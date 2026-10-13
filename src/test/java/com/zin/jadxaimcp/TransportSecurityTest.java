package com.zin.jadxaimcp;
import com.zin.jadxaimcp.server.TransportSecurity;
import io.javalin.Javalin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.net.URI;
import java.net.http.*;
import static org.junit.jupiter.api.Assertions.*;
class TransportSecurityTest {
    @TempDir Path temp;
    @Test void remoteBindRequiresSecretAndLoopbackRemainsCompatible() {
        assertThrows(IllegalArgumentException.class, () -> new TransportSecurity("0.0.0.0", null, null));
        assertThrows(IllegalArgumentException.class, () -> new TransportSecurity("127.0.0.1", "", null));
        assertThrows(IllegalArgumentException.class, () -> new TransportSecurity("127.0.0.1", "   ", null));
        assertFalse(new TransportSecurity("127.0.0.1", null, null).authenticated());
        assertThrows(IllegalArgumentException.class, () -> new TransportSecurity("127.0.0.1", "short", null));
    }
    @Test void everyRouteUsesRotatingTokenAndFailureClosesAccess() throws Exception {
        Path token = temp.resolve("token"); Files.writeString(token, "first-fixture-token-123\n");
        Javalin app = Javalin.create(c -> c.showJavalinBanner = false);
        new TransportSecurity("127.0.0.1", null, token).install(app);
        app.get("/health", c -> c.result("ready")); app.post("/mutation", c -> c.result("changed"));
        app.start("127.0.0.1", 0);
        try {
            assertEquals(401, request(app, "/health", "", false));
            assertEquals(200, request(app, "/health", "first-fixture-token-123", false));
            assertEquals(401, request(app, "/mutation", "wrong-fixture-token", true));
            Files.writeString(token, "second-fixture-token-456\n");
            assertEquals(401, request(app, "/health", "first-fixture-token-123", false));
            assertEquals(200, request(app, "/mutation", "second-fixture-token-456", true));
            Files.delete(token);
            assertEquals(503, request(app, "/health", "second-fixture-token-456", false));
        } finally { app.stop(); }
    }
    private int request(Javalin app, String path, String token, boolean post) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + path));
        if (!token.isEmpty()) request.header("Authorization", "Bearer " + token);
        if (post) request.POST(HttpRequest.BodyPublishers.noBody());
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
