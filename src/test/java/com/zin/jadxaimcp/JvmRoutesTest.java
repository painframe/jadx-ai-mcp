package com.zin.jadxaimcp;
import com.zin.jadxaimcp.server.routes.JvmRoutes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;
class JvmRoutesTest {
    @TempDir Path temp;
    private Path jar(String name, String body) throws Exception {
        Path jar = temp.resolve("fixture.jar");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new ZipEntry(name)); out.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)); out.closeEntry();
        }
        return jar;
    }
    @Test void readsServicesAndManifestWithoutExtraction() throws Exception {
        Path path = jar("META-INF/services/example.Service", "# comment\nexample.Impl # provider\n");
        JvmRoutes routes = new JvmRoutes(() -> List.of(path.toFile()), List::of);
        assertEquals(List.of("example.Impl"), routes.analyze("services").get(0).get("providers"));
        jar("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nMain-Class: example.Main\n\n");
        assertEquals("example.Main", ((Map<?,?>) routes.analyze("manifest").get(0).get("attributes")).get("Main-Class"));
        jar("config.properties", "answer=42\n");
        assertEquals("42", ((Map<?,?>) routes.analyze("properties").get(0).get("properties")).get("answer"));
    }
    @Test void invalidPaginationIsClientError() throws Exception {
        io.javalin.Javalin app = io.javalin.Javalin.create(c -> c.showJavalinBanner = false);
        app.get("/jvm-info", new JvmRoutes(List::of, List::of)::handle); app.start("127.0.0.1", 0);
        try {
            for (String query : List.of("offset=-1", "count=-1", "limit=invalid")) {
                java.net.http.HttpResponse<String> response = java.net.http.HttpClient.newHttpClient().send(
                    java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + app.port() + "/jvm-info?section=packages&" + query)).build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString());
                assertEquals(400, response.statusCode()); assertTrue(response.body().contains("invalid_request"));
            }
        } finally { app.stop(); }
    }
    @Test void rejectsZipBombMetadataAndNonJarArchive() throws Exception {
        Path path = jar("config.properties", "x".repeat(65537));
        assertThrows(java.io.IOException.class, () -> new JvmRoutes(() -> List.of(path.toFile()), List::of).analyze("properties"));
        assertThrows(UnsupportedOperationException.class, () -> new JvmRoutes(() -> List.of(temp.resolve("x.apk").toFile()), List::of).analyze("manifest"));
    }
}
