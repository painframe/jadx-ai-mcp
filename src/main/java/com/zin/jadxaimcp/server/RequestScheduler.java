package com.zin.jadxaimcp.server;

import com.zin.jadxaimcp.utils.JadxAIMCPPluginError;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BooleanSupplier;

/** Bounded synchronous admission: no detached tasks survive their HTTP handler. */
public final class RequestScheduler {
    private static final Set<String> CONTROL = Set.of("/health", "/search-progress", "/project-operation", "/runtime-diagnostics");
    private static final Set<String> HEAVY = Set.of("/class-source", "/smali-of-class", "/main-application-classes-code", "/method-by-name", "/batch-analysis", "/get-resource-file");
    private final Semaphore queued = new Semaphore(64, true);
    private final Semaphore searches = new Semaphore(1, true);
    private final Semaphore heavy = new Semaphore(4, true);
    private final Semaphore metadata = new Semaphore(8, true);
    private final ReentrantReadWriteLock project = new ReentrantReadWriteLock(true);
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final long waitMillis;
    public RequestScheduler() { this(5000); }
    public RequestScheduler(long waitMillis) { this.waitMillis = waitMillis; }

    public void handle(Context ctx, Handler handler, BooleanSupplier loading) throws Exception {
        if (CONTROL.contains(ctx.path())) { handler.handle(ctx); return; }
        if (loading.getAsBoolean() && !ctx.path().equals("/open-apk")) { reject(ctx, "Project loading; retry after it is ready"); return; }
        try (Lease lease = acquire(ctx.path())) {
            // A loader may have obtained the write lock while this reader was waiting.
            if (loading.getAsBoolean() && !ctx.path().equals("/open-apk")) { reject(ctx, "Project loading; retry after it is ready"); return; }
            handler.handle(ctx);
        } catch (BusyException e) { reject(ctx, e.getMessage()); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); reject(ctx, "Analysis admission interrupted"); }
    }
    private static void reject(Context ctx, String message) { ctx.status(503).json(JadxAIMCPPluginError.response(503, message)); }
    public Lease acquire(String path) throws InterruptedException, BusyException {
        if (!accepting.get() || !queued.tryAcquire()) throw new BusyException("Analysis queue full or server stopping");
        boolean mutation = path.startsWith("/rename-") || Set.of("/add-comment", "/cache-clear", "/open-apk").contains(path);
        Semaphore lane = mutation ? null : path.startsWith("/search-") ? searches : HEAVY.contains(path) ? heavy : metadata;
        Lock lock = mutation ? project.writeLock() : project.readLock();
        boolean laneHeld = false, lockHeld = false;
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(waitMillis);
        try {
            if (lane != null) {
                laneHeld = lane.tryAcquire(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                if (!laneHeld) throw new BusyException("Analysis lane busy; retry later");
            }
            lockHeld = lock.tryLock(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            if (!lockHeld || !accepting.get()) throw new BusyException("Project mutation pending or server stopping");
            return new Lease(lane, lock);
        } catch (InterruptedException | BusyException e) {
            if (lockHeld) lock.unlock();
            if (laneHeld) lane.release();
            throw e;
        } finally { queued.release(); }
    }
    public void stopAdmission() { accepting.set(false); }
    public Map<String, Object> snapshot() {
        return Map.of("accepting", accepting.get(), "waiting", 64 - queued.availablePermits(),
                "active_readers", project.getReadLockCount(), "mutation_active", project.isWriteLocked(),
                "search_slots", searches.availablePermits(), "heavy_slots", heavy.availablePermits(), "metadata_slots", metadata.availablePermits());
    }
    public static final class BusyException extends Exception { BusyException(String message) { super(message); } }
    public static final class Lease implements AutoCloseable {
        private final Semaphore lane; private final Lock lock; private boolean closed;
        Lease(Semaphore lane, Lock lock) { this.lane = lane; this.lock = lock; }
        public void close() { if (!closed) { closed = true; lock.unlock(); if (lane != null) lane.release(); } }
    }
}
