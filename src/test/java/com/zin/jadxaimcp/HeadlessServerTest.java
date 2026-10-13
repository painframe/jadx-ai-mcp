package com.zin.jadxaimcp;

import com.google.gson.*;
import com.zin.jadxaimcp.headless.HeadlessServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HeadlessServerTest {
    private static final String TOKEN = "synthetic-headless-test-token-only";
    private static final String CALC = "com.jadxtest.library.Calculator";
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private HttpResponse<String> request(HeadlessServer server, String path, String body, boolean auth) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path)).timeout(Duration.ofSeconds(30));
        if (auth) builder.header("Authorization", "Bearer " + TOKEN);
        if (body != null) builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    private JsonObject get(HeadlessServer server, String path) throws Exception {
        HttpResponse<String> response = request(server, path, null, true);
        assertEquals(200, response.statusCode(), path + " " + response.body()); return JsonParser.parseString(response.body()).getAsJsonObject();
    }
    private Path fixture(String suffix) { return Path.of("tests/fixtures/jadx-test-" + suffix).toAbsolutePath(); }
    @Test void realHeadlessHttpRoutesAuthAndChunks() throws Exception {
        System.setProperty("java.awt.headless", "true");
        try (HeadlessServer server = new HeadlessServer(fixture("library-1.0.0.jar"), "127.0.0.1", 0, TOKEN, null)) {
            assertEquals(401, request(server, "/health", null, false).statusCode());
            assertEquals(401, request(server, "/cache-clear", "{}", false).statusCode());
            assertEquals("headless", get(server, "/health").get("mode").getAsString());
            assertFalse(get(server, "/health").getAsJsonArray("capabilities").toString().contains("current-class"));
            assertEquals(501, request(server, "/current-class", null, true).statusCode());
            assertEquals(501, request(server, "/search-classes-by-keyword?search_term=a&search_in=comment", null, true).statusCode());
            get(server, "/all-classes"); get(server, "/class-info?class_name=" + CALC);
            get(server, "/method-signature?class_name=" + CALC + "&method_name=add");
            get(server, "/method-by-name?class_name=" + CALC + "&method_name=add&method_signature=" + URLEncoder.encode("add(int, int):int", java.nio.charset.StandardCharsets.UTF_8));
            HttpResponse<String> missingOverload = request(server, "/method-by-name?class_name=" + CALC + "&method_name=add&method_signature=" + URLEncoder.encode("add(boolean):int", java.nio.charset.StandardCharsets.UTF_8), null, true);
            assertEquals(404, missingOverload.statusCode());
            assertTrue(JsonParser.parseString(missingOverload.body()).getAsJsonObject().has("candidates"), missingOverload.body());
            get(server, "/search-native-methods"); get(server, "/xrefs-to-class?class_name=" + CALC);
            get(server, "/jvm-info?section=packages"); get(server, "/list-all-resource-files-names");
            get(server, "/runtime-diagnostics");
            JsonObject search = get(server, "/search-classes-by-keyword?search_term=Calculator&search_in=class&case_sensitive=true");
            assertTrue(search.get("classes").toString().contains("Calculator")); get(server, "/search-progress");
            HttpResponse<String> source = request(server, "/class-source?class_name=" + CALC, null, true);
            assertEquals(200, source.statusCode()); String full = source.body();
            StringBuilder chunks = new StringBuilder(); String cursor = null; int count = 0;
            do {
                JsonObject chunk = get(server, "/class-source?class_name=" + CALC + "&max_bytes=128" + (cursor == null ? "" : "&cursor=" + cursor));
                chunks.append(chunk.get("content").getAsString()); JsonElement next = chunk.getAsJsonObject("continuation").get("next_cursor");
                cursor = next.isJsonNull() ? null : next.getAsString(); assertTrue(++count < 1000);
            } while (cursor != null);
            assertEquals(full, chunks.toString());
            HttpResponse<String> batch = request(server, "/batch-analysis", new Gson().toJson(Map.of("operation", "class-source", "items", List.of(Map.of("class_name", CALC), Map.of("class_name", "missing")))), true);
            assertEquals(200, batch.statusCode()); JsonArray results = JsonParser.parseString(batch.body()).getAsJsonObject().getAsJsonArray("results");
            assertTrue(results.get(0).getAsJsonObject().get("ok").getAsBoolean()); assertFalse(results.get(1).getAsJsonObject().get("ok").getAsBoolean());
        }
    }
    @Test void discoversActualNativeDeclarationsAndKeepsInstanceProgressSeparate() throws Exception {
        try (HeadlessServer nativeServer = new HeadlessServer(Path.of("target/test-classes/fixtures/NativeMethods.class").toAbsolutePath(), "127.0.0.1", 0, TOKEN, null);
             HeadlessServer jarServer = new HeadlessServer(fixture("library-1.0.0.jar"), "127.0.0.1", 0, TOKEN, null)) {
            JsonObject natives = get(nativeServer, "/search-native-methods?package=fixtures&limit=1");
            assertEquals(1, natives.getAsJsonArray("methods").size());
            assertEquals(2, natives.getAsJsonObject("pagination").get("total").getAsInt());
            assertEquals(0, get(nativeServer, "/search-native-methods?package=fixture").getAsJsonArray("methods").size());
            String before = get(jarServer, "/search-progress").toString();
            get(nativeServer, "/search-classes-by-keyword?search_term=Native&search_in=class");
            assertEquals(before, get(jarServer, "/search-progress").toString());
        }
    }
    @TempDir Path temporary;
    @Test void failedReplacementPreservesProjectAndSuccessfulSwapInvalidatesCursor() throws Exception {
        try (HeadlessServer server = new HeadlessServer(fixture("library-1.0.0.jar"), "127.0.0.1", 0, TOKEN, null)) {
            String generation = get(server, "/health").get("generation").getAsString();
            JsonObject chunk = get(server, "/class-source?class_name=" + CALC + "&max_bytes=128");
            String cursor = chunk.getAsJsonObject("continuation").get("next_cursor").getAsString();
            String valid = new Gson().toJson(Map.of("path", fixture("app-1.0.0.apk").toString()));
            assertEquals(409, request(server, "/open-apk", valid, true).statusCode());
            Path invalid = temporary.resolve("broken.jar"); Files.writeString(invalid, "not an archive");
            JsonObject failed = openAndAwait(server, invalid);
            assertEquals("failed", failed.get("state").getAsString());
            assertEquals(generation, get(server, "/health").get("generation").getAsString());
            assertEquals(200, request(server, "/class-source?class_name=" + CALC, null, true).statusCode());
            assertEquals("ready", openAndAwait(server, fixture("app-1.0.0.apk")).get("state").getAsString());
            assertNotEquals(generation, get(server, "/health").get("generation").getAsString());
            assertEquals(200, request(server, "/manifest", null, true).statusCode()); get(server, "/main-activity"); get(server, "/strings");
            assertEquals("ready", openAndAwait(server, fixture("library-1.0.0.jar")).get("state").getAsString());
            assertEquals(400, request(server, "/class-source?class_name=" + CALC + "&cursor=" + cursor, null, true).statusCode());
        }
    }
    private JsonObject openAndAwait(HeadlessServer server, Path path) throws Exception {
        HttpResponse<String> accepted = request(server, "/open-apk", new Gson().toJson(Map.of("path", path.toString(), "replace", true)), true);
        assertEquals(202, accepted.statusCode(), accepted.body());
        String id = JsonParser.parseString(accepted.body()).getAsJsonObject().get("operation_id").getAsString();
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            JsonObject state = get(server, "/project-operation?operation_id=" + id);
            if (!state.get("pending").getAsBoolean()) return state;
            Thread.sleep(20);
        }
        fail("Project operation did not finish within 30 seconds"); return null;
    }
}
