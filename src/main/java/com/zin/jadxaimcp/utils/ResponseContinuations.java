package com.zin.jadxaimcp.utils;

import io.javalin.http.Context;
import jadx.gui.ui.MainWindow;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public final class ResponseContinuations {
    private ResponseContinuations() { }
    public static boolean send(Context ctx, MainWindow window, String identity, String content) {
        return send(ctx, window, identity, content, Map.of());
    }
    public static boolean send(Context ctx, MainWindow window, String identity, String content, Map<String, Object> metadata) {
        if (ctx.queryParam("max_bytes") == null && ctx.queryParam("cursor") == null) return false;
        try {
            int maximum = ctx.queryParam("max_bytes") == null ? 65536 : Integer.parseInt(ctx.queryParam("max_bytes"));
            Map<String, Object> result = new LinkedHashMap<>(chunk(content, identity, ProjectState.generation(window), maximum, ctx.queryParam("cursor")));
            result.putAll(metadata);
            ctx.json(result);
        } catch (IllegalArgumentException error) {
            ctx.status(400).json(JadxAIMCPPluginError.response(400, error.getMessage()));
        }
        return true;
    }
    public static Map<String, Object> chunk(String content, String identity, String generation, int maximum, String cursor) {
        if (maximum < 1 || maximum > 1048576) throw new IllegalArgumentException("max_bytes must be 1..1048576");
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        String digest;
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            hash.update(identity.getBytes(StandardCharsets.UTF_8)); hash.update((byte) 0);
            digest = Base64.getUrlEncoder().withoutPadding().encodeToString(hash.digest(bytes));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        int start = 0;
        if (cursor != null) {
            if (cursor.isEmpty() || cursor.length() > 4096) throw new IllegalArgumentException("Invalid cursor");
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split(":", -1);
            if (parts.length != 3 || !parts[0].equals(generation) || !parts[1].equals(digest))
                throw new IllegalArgumentException("Stale cursor or different content; restart without cursor");
            start = Integer.parseInt(parts[2]);
        }
        if (start < 0 || start > bytes.length || (start < bytes.length && (bytes[start] & 0xc0) == 0x80))
            throw new IllegalArgumentException("Invalid cursor boundary");
        int end = Math.min(bytes.length, start + maximum);
        while (end > start && end < bytes.length && (bytes[end] & 0xc0) == 0x80) end--;
        if (end == start && start < bytes.length) throw new IllegalArgumentException("max_bytes is smaller than the next UTF-8 character");
        Map<String, Object> continuation = new LinkedHashMap<>();
        continuation.put("generation", generation); continuation.put("max_bytes", maximum);
        continuation.put("offset_bytes", start); continuation.put("total_bytes", bytes.length);
        continuation.put("next_cursor", end == bytes.length ? null : Base64.getUrlEncoder().withoutPadding()
                .encodeToString((generation + ":" + digest + ":" + end).getBytes(StandardCharsets.UTF_8)));
        return Map.of("content", new String(bytes, start, end - start, StandardCharsets.UTF_8), "continuation", continuation);
    }
}
