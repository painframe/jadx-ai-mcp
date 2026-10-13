package com.zin.jadxaimcp.server.routes;

import com.google.gson.*;
import com.zin.jadxaimcp.utils.JadxAIMCPPluginError;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import java.lang.reflect.Proxy;
import java.util.*;

/** Runs the existing read handlers with captured per-item responses. */
public final class BatchRoutes {
    private final Map<String, Handler> handlers;
    private final Gson gson = new Gson();
    private final jadx.gui.ui.MainWindow window;
    public BatchRoutes(Map<String, Handler> handlers, jadx.gui.ui.MainWindow window) {
        this.handlers = Map.copyOf(handlers); this.window = window;
    }
    public void handle(Context ctx) {
        try {
            String body = ctx.body();
            if (body.length() > 1048576) throw new IllegalArgumentException("Batch request exceeds 1 Mi characters");
            JsonObject request = JsonParser.parseString(body).getAsJsonObject();
            String operation = request.get("operation").getAsString();
            Handler handler = handlers.get(operation);
            if (handler == null) throw new IllegalArgumentException("Unsupported batch operation");
            JsonArray items = request.getAsJsonArray("items");
            if (items == null || items.size() < 1 || items.size() > 50) throw new IllegalArgumentException("Batch requires 1..50 items");
            int maximum = request.has("max_bytes") ? Integer.parseInt(request.get("max_bytes").getAsString()) : 65536;
            if (maximum < 1 || maximum > 1048576) throw new IllegalArgumentException("max_bytes must be 1..1048576");
            List<Map<String, Object>> results = new ArrayList<>();
            long responseBytes = 0;
            for (int index = 0; index < items.size(); index++) {
                Map<String, Object> result = new LinkedHashMap<>(); result.put("index", index);
                try {
                    JsonObject item = items.get(index).getAsJsonObject();
                    Map<String, String> parameters = new HashMap<>();
                    for (Map.Entry<String, JsonElement> entry : item.entrySet()) {
                        if (!entry.getValue().isJsonPrimitive()) throw new IllegalArgumentException("Batch parameters must be scalar");
                        parameters.put(entry.getKey(), entry.getValue().getAsString());
                    }
                    for (String pageSize : List.of("limit", "count")) {
                        if (parameters.containsKey(pageSize)) {
                            int value = Integer.parseInt(parameters.get(pageSize));
                            if (value < 1 || value > 1000) throw new IllegalArgumentException("Batch page size must be 1..1000");
                        }
                    }
                    if (operation.equals("class-source")) parameters.put("max_bytes", Integer.toString(maximum));
                    Object[] response = {null}; int[] status = {200};
                    Context itemContext = (Context) Proxy.newProxyInstance(Context.class.getClassLoader(), new Class<?>[]{Context.class},
                            (proxy, method, args) -> {
                                switch (method.getName()) {
                                    case "queryParam": return parameters.get((String) args[0]);
                                    case "status":
                                        if (args == null || args.length == 0) return status[0];
                                        status[0] = (Integer) args[0]; return proxy;
                                    case "json": case "result":
                                        if (args == null || args.length == 0) return response[0];
                                        response[0] = args[0]; return proxy;
                                    case "toString": return "BatchItemContext";
                                    default: throw new UnsupportedOperationException("Unsupported batch context method: " + method.getName());
                                }
                            });
                    handler.handle(itemContext);
                    if (response[0] == null) throw new IllegalStateException("Read handler returned no response");
                    if (status[0] < 400 && operation.equals("method-by-name") && response[0] instanceof Map) {
                        Map<String, Object> method = new LinkedHashMap<>((Map<String, Object>) response[0]);
                        Object code = method.remove("code");
                        if (code instanceof String) {
                            String identity = "method:" + method.get("class_name") + ":" + method.get("method_name") + ":" + method.get("decl");
                            method.putAll(com.zin.jadxaimcp.utils.ResponseContinuations.chunk((String) code, identity,
                                    com.zin.jadxaimcp.utils.ProjectState.generation(window), maximum, parameters.get("cursor")));
                            response[0] = method;
                        }
                    }
                    int itemBytes = gson.toJson(response[0]).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                    if ((!operation.equals("class-source") && !operation.equals("method-by-name") && itemBytes > maximum + 4096) || responseBytes + itemBytes > 8 * 1024 * 1024)
                        throw new IllegalArgumentException("Batch response budget exceeded; use smaller pagination or fewer items");
                    responseBytes += itemBytes;
                    result.put("ok", status[0] < 400);
                    if (status[0] < 400) result.put("data", response[0]);
                    else {
                        result.put("status", status[0]);
                        if (response[0] instanceof Map) result.putAll((Map<String, Object>) response[0]);
                        else result.put("error", response[0].toString());
                    }
                } catch (Exception error) {
                    result.put("ok", false); result.put("error", error.getMessage() == null ? "Invalid batch item" : error.getMessage());
                    result.put("code", error instanceof IllegalArgumentException ? "invalid_request" : "internal_error");
                }
                results.add(result);
            }
            ctx.json(Map.of("operation", operation, "results", results));
        } catch (Exception error) {
            ctx.status(400).json(JadxAIMCPPluginError.response(400, "Invalid batch request: " + error.getMessage()));
        }
    }
}
