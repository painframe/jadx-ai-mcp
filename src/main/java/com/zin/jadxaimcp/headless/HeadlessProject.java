package com.zin.jadxaimcp.headless;

import com.google.gson.*;
import com.zin.jadxaimcp.server.RequestScheduler;
import com.zin.jadxaimcp.utils.JadxAIMCPPluginError;
import io.javalin.http.Context;
import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** One persistent decompiler; replacements are loaded privately and swapped under the write lease. */
public final class HeadlessProject implements AutoCloseable {
    private final RequestScheduler scheduler;
    private final ExecutorService loader = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "jadx-headless-loader"); thread.setDaemon(true); return thread;
    });
    private final Map<String, Object> operation = new LinkedHashMap<>();
    private volatile JadxDecompiler decompiler;
    private volatile Path input;
    private volatile String generation = UUID.randomUUID().toString();
    private volatile boolean loading;
    private volatile boolean closed;
    private final java.util.concurrent.atomic.AtomicBoolean decompilerClosed = new java.util.concurrent.atomic.AtomicBoolean();
    public HeadlessProject(Path input, RequestScheduler scheduler) throws Exception {
        this.scheduler = scheduler; this.input = readable(input); this.decompiler = load(this.input);
    }
    private static Path readable(Path requested) throws Exception {
        if (!requested.isAbsolute()) throw new IllegalArgumentException("path must be absolute");
        Path path = requested.toRealPath();
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) throw new IllegalArgumentException("path must be a readable regular file");
        return path;
    }
    private static JadxDecompiler load(Path path) throws java.io.IOException {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        boolean archive = name.endsWith(".apk") || name.endsWith(".jar") || name.endsWith(".zip") || name.endsWith(".aab");
        if (!archive && !name.endsWith(".class") && !name.endsWith(".dex"))
            throw new IllegalArgumentException("Supported input types: APK, JAR, ZIP, AAB, class, DEX");
        if (archive) {
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(path.toFile())) {
                if (zip.size() == 0) throw new IllegalArgumentException("Input archive is empty");
            }
        }
        JadxArgs args = new JadxArgs(); args.setInputFile(path.toFile()); args.setThreadsCount(4);
        args.setDisabledPlugins(Set.of("jadx-ai-mcp")); // The standalone backend owns transport; never start GUI plugin discovery.
        JadxDecompiler candidate = new JadxDecompiler(args);
        try {
            candidate.load();
            if (candidate.getClasses().isEmpty() && (!archive || candidate.getResources().isEmpty()))
                throw new IllegalArgumentException("Input contains no supported classes or resources");
            return candidate;
        } catch (RuntimeException error) { candidate.close(); throw error; }
    }
    public JadxDecompiler decompiler() { return decompiler; }
    public Path input() { return input; }
    public String generation() { return generation; }
    public void invalidate() { generation = UUID.randomUUID().toString(); }
    public boolean isLoading() { return loading; }
    public void open(Context ctx) {
        try {
            if (ctx.body().length() > 16384) throw new IllegalArgumentException("Project request exceeds 16 Ki characters");
            JsonObject body = JsonParser.parseString(ctx.body()).getAsJsonObject();
            if (!body.has("path") || !body.get("path").isJsonPrimitive() || !body.get("path").getAsJsonPrimitive().isString())
                throw new IllegalArgumentException("path must be an absolute path string");
            Path path = readable(Path.of(body.get("path").getAsString()));
            if (body.has("replace") && (!body.get("replace").isJsonPrimitive() || !body.get("replace").getAsJsonPrimitive().isBoolean()))
                throw new IllegalArgumentException("replace must be a boolean");
            boolean replace = body.has("replace") && body.get("replace").getAsBoolean();
            synchronized (operation) {
                if (closed || loading || (!replace && decompiler != null)) {
                    ctx.status(409).json(JadxAIMCPPluginError.response(409, loading ? "A project load is already active" : "A project is open; set replace=true")); return;
                }
                loading = true; operation.clear();
                operation.put("operation_id", UUID.randomUUID().toString()); operation.put("state", "loading");
                operation.put("pending", true); operation.put("started_at_ms", System.currentTimeMillis());
                operation.put("path", path.toString());
                try { loader.submit(() -> replace(path)); }
                catch (RejectedExecutionException stopped) { loading = false; operation.put("pending", false); throw stopped; }
                ctx.status(202).json(new LinkedHashMap<>(operation));
            }
        } catch (Exception error) { ctx.status(400).json(JadxAIMCPPluginError.response(400, "Cannot open project: " + error.getMessage())); }
    }
    private void replace(Path path) {
        JadxDecompiler candidate = null;
        try (RequestScheduler.Lease lease = scheduler.acquire("/open-apk")) {
            candidate = load(path);
            synchronized (operation) {
                if (closed || System.currentTimeMillis() - ((Number) operation.get("started_at_ms")).longValue() > 300000 || "failed".equals(operation.get("state"))) throw new IllegalStateException("Project load cancelled or expired");
                JadxDecompiler previous = decompiler; decompiler = candidate; candidate = null; input = path; invalidate();
                operation.put("state", "ready"); operation.put("generation", generation);
                try { previous.close(); } catch (RuntimeException cleanup) {
                    operation.put("warning", "New project ready; previous project cleanup failed");
                }
            }
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            synchronized (operation) { operation.put("state", "failed"); operation.put("error", "Project load failed; previous project preserved"); }
        } finally {
            try { if (candidate != null) candidate.close(); }
            finally {
                synchronized (operation) { loading = false; operation.put("pending", false); }
                if (closed) closeDecompiler();
            }
        }
    }
    public void status(Context ctx) {
        synchronized (operation) {
            if (!Objects.equals(ctx.queryParam("operation_id"), operation.get("operation_id")) || operation.isEmpty()) {
                ctx.status(404).json(JadxAIMCPPluginError.response(404, "Unknown project operation")); return;
            }
            if (loading && System.currentTimeMillis() - ((Number) operation.get("started_at_ms")).longValue() > 300000) {
                operation.put("state", "failed"); operation.put("error", "Load exceeded five minutes; admission remains blocked until loader exits");
            }
            ctx.json(new LinkedHashMap<>(operation));
        }
    }
    public void close() {
        closed = true; loader.shutdownNow();
        try {
            if (!loader.awaitTermination(10, TimeUnit.SECONDS)) return; // Never close a decompiler still used by its loader.
        } catch (InterruptedException error) { Thread.currentThread().interrupt(); return; }
        closeDecompiler();
    }
    private void closeDecompiler() {
        if (decompilerClosed.compareAndSet(false, true)) decompiler.close();
    }
}
