package com.zin.jadxaimcp.server.routes;

import com.google.gson.*;
import com.zin.jadxaimcp.utils.*;
import io.javalin.http.Context;
import jadx.gui.ui.MainWindow;
import javax.swing.SwingUtilities;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ProjectRoutes {
    private final MainWindow window;
    private final Map<String, Object> operation;
    @SuppressWarnings("unchecked")
    public ProjectRoutes(MainWindow window) {
        this.window = window;
        // JDK-only values retain operation status when JADX recreates the plugin classloader.
        synchronized (System.getProperties()) {
            operation = (Map<String, Object>) System.getProperties().computeIfAbsent(
                    "jadx-ai-mcp.operation." + System.identityHashCode(window), ignored -> new ConcurrentHashMap<String, Object>());
        }
    }
    public void open(Context ctx) {
        try {
            JsonObject body = JsonParser.parseString(ctx.body()).getAsJsonObject();
            if (!body.has("path") || !body.get("path").isJsonPrimitive() || !body.get("path").getAsJsonPrimitive().isString())
                throw new IllegalArgumentException("path must be an absolute path string");
            Path requested = Path.of(body.get("path").getAsString());
            if (!requested.isAbsolute()) throw new IllegalArgumentException("path must be absolute");
            Path path = requested.toRealPath();
            if (!Files.isRegularFile(path) || !Files.isReadable(path)) throw new IllegalArgumentException("Path is not a readable file");
            if (body.has("replace") && (!body.get("replace").isJsonPrimitive() || !body.get("replace").getAsJsonPrimitive().isBoolean()))
                throw new IllegalArgumentException("replace must be a boolean");
            boolean replace = body.has("replace") && body.get("replace").getAsBoolean();
            String id = UUID.randomUUID().toString();
            Map<String, Object> accepted;
            synchronized (operation) {
                if (Boolean.TRUE.equals(operation.get("pending"))) {
                    ctx.status(409).json(JadxAIMCPPluginError.response(409, "A project load is already active")); return;
                }
                operation.clear(); operation.put("operation_id", id); operation.put("path", path.toString());
                operation.put("state", "loading"); operation.put("pending", true); operation.put("started_at_ms", System.currentTimeMillis());
                accepted = new LinkedHashMap<>(operation);
            }
            SwingUtilities.invokeLater(() -> begin(path, replace, id));
            ctx.status(202).json(accepted);
        } catch (Exception error) {
            ctx.status(400).json(JadxAIMCPPluginError.response(400, "Cannot open project: " + error.getMessage()));
        }
    }
    private void begin(Path path, boolean replace, String id) {
        try {
            if (!id.equals(operation.get("operation_id"))) return;
            if (!window.getProject().isInitial() && !window.getProject().isSaved())
                throw new IllegalArgumentException("Save unsaved project changes in JADX before opening another file");
            if (!replace && !window.getProject().getFilePaths().isEmpty())
                throw new IllegalArgumentException("A project is open; set replace=true after saving it");
            Object previous = window.getWrapper().getDecompiler();
            ProjectState.invalidate(window);
            window.addLoadListener(loaded -> {
                if (!id.equals(operation.get("operation_id")) || !Boolean.TRUE.equals(operation.get("pending"))) return true;
                if (loaded && window.getWrapper().getDecompiler() != null && window.getWrapper().getDecompiler() != previous
                        && window.getProject().getFilePaths().contains(path)) {
                    ProjectState.invalidate(window);
                    String generation = ProjectState.generation(window);
                    synchronized (operation) {
                        if (id.equals(operation.get("operation_id"))) {
                            operation.put("state", "ready"); operation.put("generation", generation); operation.put("pending", false);
                        }
                    }
                    return true;
                }
                if (loaded && window.getWrapper().getDecompiler() != null && window.getWrapper().getDecompiler() != previous) {
                    fail(id, "JADX loaded a different project"); return true;
                }
                return false;
            });
            window.open(path);
        } catch (Exception error) { fail(id, error.getMessage()); }
    }
    private void fail(String id, String error) {
        synchronized (operation) {
            if (!id.equals(operation.get("operation_id"))) return;
            operation.put("state", "failed"); operation.put("error", error == null ? "Project load failed" : error);
            operation.put("pending", false);
        }
    }
    public boolean isLoading() { return Boolean.TRUE.equals(operation.get("pending")); }
    public void status(Context ctx) {
        Map<String, Object> snapshot;
        synchronized (operation) {
            if (!Objects.equals(ctx.queryParam("operation_id"), operation.get("operation_id"))) {
                ctx.status(404).json(JadxAIMCPPluginError.response(404, "Unknown project operation")); return;
            }
            if ("loading".equals(operation.get("state"))
                    && System.currentTimeMillis() - ((Number) operation.get("started_at_ms")).longValue() > 300000) {
                operation.put("state", "failed");
                operation.put("error", "JADX did not signal completion within five minutes; analysis stays blocked until the GUI finishes");
            }
            snapshot = new LinkedHashMap<>(operation);
        }
        ctx.json(snapshot);
    }
}
