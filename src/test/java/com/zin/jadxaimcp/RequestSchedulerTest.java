package com.zin.jadxaimcp;

import com.zin.jadxaimcp.server.RequestScheduler;
import io.javalin.Javalin;
import java.net.URI;
import java.net.http.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RequestSchedulerTest {
    @Test void mutationsExcludeReadersAndReleaseOnFailure() throws Exception {
        RequestScheduler scheduler = new RequestScheduler(50);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (RequestScheduler.Lease read = scheduler.acquire("/class-source")) {
            assertTrue(pool.submit(() -> {
                try (RequestScheduler.Lease ignored = scheduler.acquire("/rename-class")) { return false; }
                catch (RequestScheduler.BusyException e) { return true; }
            }).get());
        } finally { pool.shutdownNow(); }
        try (RequestScheduler.Lease mutation = scheduler.acquire("/rename-class")) {
            assertEquals(true, scheduler.snapshot().get("mutation_active"));
        }
        assertEquals(false, scheduler.snapshot().get("mutation_active"));
    }
    @Test void searchLaneIsIndependentFromMetadataAndBounded() throws Exception {
        RequestScheduler scheduler = new RequestScheduler(10);
        try (RequestScheduler.Lease first = scheduler.acquire("/search-classes-by-keyword")) {
            assertThrows(RequestScheduler.BusyException.class, () -> scheduler.acquire("/search-method"));
            try (RequestScheduler.Lease metadata = scheduler.acquire("/class-info")) {
                assertEquals(2, scheduler.snapshot().get("active_readers"));
            }
        }
        scheduler.stopAdmission();
        assertThrows(RequestScheduler.BusyException.class, () -> scheduler.acquire("/class-info"));
    }
    @Test void httpLoadingGuardControlAndExceptionCleanup() throws Exception {
        RequestScheduler scheduler = new RequestScheduler(50);
        AtomicBoolean loading = new AtomicBoolean(true);
        Javalin app = Javalin.create(c -> c.showJavalinBanner = false);
        app.get("/class-source", ctx -> scheduler.handle(ctx, c -> c.result("source"), loading::get));
        app.get("/health", ctx -> scheduler.handle(ctx, c -> c.result("ready"), loading::get));
        app.get("/rename-class", ctx -> scheduler.handle(ctx, c -> { throw new IllegalStateException("fixture"); }, loading::get));
        app.start("127.0.0.1", 0);
        try {
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:" + app.port();
            assertEquals(503, client.send(HttpRequest.newBuilder(URI.create(base + "/class-source")).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(200, client.send(HttpRequest.newBuilder(URI.create(base + "/health")).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
            loading.set(false);
            assertEquals(500, client.send(HttpRequest.newBuilder(URI.create(base + "/rename-class")).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(200, client.send(HttpRequest.newBuilder(URI.create(base + "/class-source")).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(false, scheduler.snapshot().get("mutation_active"));
        } finally { app.stop(); }
    }
}
