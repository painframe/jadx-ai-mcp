package com.zin.jadxaimcp.server;

import com.zin.jadxaimcp.utils.JadxAIMCPPluginError;
import io.javalin.Javalin;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;

/** Shared GUI/headless bind policy and rotating bearer-token validation. */
public final class TransportSecurity {
    private final String host;
    private final String token;
    private final Path tokenFile;
    public TransportSecurity(String host, String token, Path tokenFile) {
        this.host = host == null || host.isBlank() ? "127.0.0.1" : host;
        this.token = token == null ? null : validate(token);
        this.tokenFile = tokenFile;
        if (this.token != null && tokenFile != null) throw new IllegalArgumentException("Configure only one token source");
        try {
            String configured = currentToken();
            if (!InetAddress.getByName(this.host).isLoopbackAddress() && configured == null)
                throw new IllegalArgumentException("Non-loopback bind requires a bearer token");
        } catch (IOException e) { throw new IllegalArgumentException("Cannot read transport configuration", e); }
    }
    public static TransportSecurity fromEnvironment() {
        String path = setting("jadx.mcp.tokenFile", "JADX_MCP_TOKEN_FILE", null);
        return new TransportSecurity(setting("jadx.mcp.host", "JADX_MCP_HOST", "127.0.0.1"), System.getenv("JADX_MCP_TOKEN"), path == null ? null : Path.of(path));
    }
    private static String setting(String property, String env, String fallback) {
        return System.getProperty(property, System.getenv(env) == null ? fallback : System.getenv(env));
    }
    public String host() { return host; }
    public boolean authenticated() { return token != null || tokenFile != null; }
    private static String validate(String value) {
        if (value.length() < 16 || value.length() > 4096 || !value.chars().allMatch(c -> c >= 33 && c <= 126))
            throw new IllegalArgumentException("Bearer token must contain 16..4096 printable non-space ASCII characters");
        return value;
    }
    private String currentToken() throws IOException {
        if (tokenFile == null) return token;
        if (!Files.isRegularFile(tokenFile)) throw new IOException("Token file unavailable");
        byte[] bytes;
        try (InputStream in = Files.newInputStream(tokenFile)) { bytes = in.readNBytes(4098); }
        if (bytes.length > 4097) throw new IOException("Token file too large");
        return validate(new String(bytes, StandardCharsets.UTF_8).strip());
    }
    public void install(Javalin app) {
        app.before(ctx -> {
            try {
                String expected = currentToken();
                if (expected == null) return;
                String header = ctx.header("Authorization");
                String supplied = header != null && header.startsWith("Bearer ") ? header.substring(7) : "";
                if (supplied.length() <= 4096 && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) return;
                ctx.header("WWW-Authenticate", "Bearer");
                ctx.status(401).json(JadxAIMCPPluginError.response(401, "Bearer authentication required"));
            } catch (IOException | IllegalArgumentException e) {
                // A failed token rotation must fail closed, including health/control routes.
                ctx.status(503).json(JadxAIMCPPluginError.response(503, "Authentication configuration unavailable"));
            }
            ctx.skipRemainingHandlers();
        });
    }
}
