package com.zin.jadxaimcp.utils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/** One bounded, disposable regex JVM per search, shared across its inputs. */
public final class SearchPattern implements AutoCloseable {
    private static final int MAX_PATTERN_CHARS = 1024;
    private static final int MAX_INPUT_CHARS = 4 * 1024 * 1024;
    private static final Semaphore WORKERS = new Semaphore(4);
    private final String literal;
    private final boolean caseSensitive;
    private final long timeoutNanos;
    private Process process;
    private ExecutorService io;
    private DataInputStream output;
    private DataOutputStream input;
    private boolean permit;
    private boolean closed;

    public SearchPattern(String expression, boolean regex, boolean caseSensitive) {
        this(expression, regex, caseSensitive, TimeUnit.SECONDS.toNanos(2));
    }

    public SearchPattern(String expression, boolean regex, boolean caseSensitive, long timeoutNanos) {
        if (expression == null || expression.isEmpty() || expression.length() > MAX_PATTERN_CHARS)
            throw new IllegalArgumentException("Search expression must contain 1..1024 characters");
        this.caseSensitive = caseSensitive;
        this.timeoutNanos = timeoutNanos;
        literal = caseSensitive ? expression : expression.toLowerCase(Locale.ROOT);
        if (!regex) return;
        if (!(permit = WORKERS.tryAcquire())) throw new IllegalArgumentException("Regex worker capacity exhausted");
        try {
            String classes = Path.of(SearchPattern.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
            process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-Xmx64m", "-Xss512k", "-cp", classes, Worker.class.getName())
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            input = new DataOutputStream(process.getOutputStream());
            output = new DataInputStream(process.getInputStream());
            io = Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "jadx-regex-io"); thread.setDaemon(true); return thread;
            });
            exchange(() -> {
                input.writeUTF(expression); input.writeBoolean(caseSensitive); input.flush();
                checkReply(); return false;
            }, TimeUnit.SECONDS.toNanos(5));
        } catch (Exception error) {
            close();
            throw new IllegalArgumentException("Cannot initialize regex: " + error.getMessage(), error);
        }
    }

    public synchronized boolean matches(String text) {
        if (closed) throw new IllegalArgumentException("Regex request already closed");
        if (text == null) return false;
        if (io == null) return (caseSensitive ? text : text.toLowerCase(Locale.ROOT)).contains(literal);
        if (text.length() > MAX_INPUT_CHARS) throw new IllegalArgumentException("Regex input exceeds 4194304 characters");
        return exchange(() -> {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            input.writeInt(bytes.length); input.write(bytes); input.flush();
            checkReply(); return output.readBoolean();
        }, timeoutNanos);
    }

    private void checkReply() throws IOException {
        if (!output.readBoolean()) throw new IOException(output.readUTF());
    }

    private boolean exchange(Callable<Boolean> work, long deadlineNanos) {
        Future<Boolean> result = io.submit(work);
        try {
            return result.get(deadlineNanos, TimeUnit.NANOSECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt(); close();
            throw new IllegalArgumentException("Regex request cancelled", error);
        } catch (ExecutionException | TimeoutException error) {
            close();
            throw new IllegalArgumentException("Regex failed or exceeded its deadline", error);
        }
    }

    public static boolean flag(String value) {
        if (value == null || value.equalsIgnoreCase("false")) return false;
        if (value.equalsIgnoreCase("true")) return true;
        throw new IllegalArgumentException("Search flags must be true or false");
    }

    @Override public synchronized void close() {
        closed = true;
        if (process != null) {
            process.destroyForcibly();
            boolean interrupted = Thread.interrupted();
            try { process.waitFor(2, TimeUnit.SECONDS); }
            catch (InterruptedException error) { interrupted = true; }
            if (interrupted) Thread.currentThread().interrupt();
        }
        if (io != null) io.shutdownNow();
        if (permit) { WORKERS.release(); permit = false; }
    }

    public static final class Worker {
        public static void main(String[] args) throws IOException {
            DataInputStream input = new DataInputStream(System.in);
            DataOutputStream output = new DataOutputStream(System.out);
            try {
                String expression = input.readUTF();
                boolean sensitive = input.readBoolean();
                Pattern pattern = Pattern.compile(expression, sensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                output.writeBoolean(true); output.flush();
                while (true) {
                    int size;
                    try { size = input.readInt(); } catch (EOFException done) { return; }
                    if (size < 0 || size > MAX_INPUT_CHARS * 4) throw new IOException("Regex input too large");
                    byte[] bytes = new byte[size]; input.readFully(bytes);
                    boolean matched = pattern.matcher(new String(bytes, StandardCharsets.UTF_8)).find();
                    output.writeBoolean(true); output.writeBoolean(matched); output.flush();
                }
            } catch (Exception | StackOverflowError error) {
                output.writeBoolean(false); output.writeUTF("Invalid or excessively complex regex"); output.flush();
            }
        }
    }
}
