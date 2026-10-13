package com.zin.jadxaimcp.headless;

import com.zin.jadxaimcp.server.RequestScheduler;
import com.zin.jadxaimcp.server.TransportSecurity;
import com.zin.jadxaimcp.server.RuntimeDiagnostics;
import com.zin.jadxaimcp.server.routes.*;
import com.zin.jadxaimcp.utils.*;
import io.javalin.Javalin;
import io.javalin.http.Handler;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CountDownLatch;

/** Standalone JADX API backend: no MainWindow, Swing event loop, or display server. */
public final class HeadlessServer implements AutoCloseable {
    private final RequestScheduler scheduler = new RequestScheduler();
    private final HeadlessProject project;
    private final Javalin app;
    private final Map<String, Handler> reads = new LinkedHashMap<>();
    private final Map<String, Handler> writes = new LinkedHashMap<>();
    private final String instanceId = UUID.randomUUID().toString();
    private boolean closed;
    public HeadlessServer(Path input, String host, int port, String token, Path tokenFile) throws Exception {
        TransportSecurity security = new TransportSecurity(host, token, tokenFile);
        project = new HeadlessProject(input, scheduler);
        app = Javalin.create(config -> { config.showJavalinBanner = false; config.http.maxRequestSize = 1048576; });
        try {
            security.install(app);
            register();
            app.exception(Exception.class, (error, ctx) -> ctx.status(500).json(JadxAIMCPPluginError.response(500, "Internal analysis error")));
            app.error(404, ctx -> {
                if (ctx.result() == null) ctx.json(JadxAIMCPPluginError.response(404, "Unknown endpoint"));
            });
            for (Map.Entry<String, Handler> route : reads.entrySet())
                app.get(route.getKey(), ctx -> scheduler.handle(ctx, route.getValue(), project::isLoading));
            for (Map.Entry<String, Handler> route : writes.entrySet())
                app.post(route.getKey(), ctx -> scheduler.handle(ctx, route.getValue(), project::isLoading));
            for (String path : List.of("/current-class", "/selected-text", "/rename-class", "/rename-method", "/rename-field",
                    "/rename-package", "/rename-variable", "/add-comment", "/list-comments", "/debug/stack-frames", "/debug/variables", "/debug/threads"))
                app.get(path, ctx -> ctx.status(501).json(Map.of("error", "This endpoint requires a GUI session", "code", "unsupported_capability",
                        "source", "jadx", "status", 501, "retryable", false)));
            app.start(security.host(), port);
        } catch (Exception error) { app.stop(); project.close(); throw error; }
    }
    private void register() {
        PaginationUtils pagination = new PaginationUtils();
        SearchProgressTracker progress = new SearchProgressTracker();
        ClassRoutes classes = new ClassRoutes(project::decompiler, project::generation, project::invalidate, pagination, progress);
        AnalysisRoutes analysis = new AnalysisRoutes(() -> project.decompiler().getClassesWithInners(), pagination);
        MethodRoutes methods = new MethodRoutes(() -> project.decompiler().getClassesWithInners(), pagination, progress);
        XrefsRoutes xrefs = new XrefsRoutes(() -> project.decompiler().getClassesWithInners());
        ResourceRoutes resources = new ResourceRoutes(() -> project.decompiler().getResources(), project::generation);
        JvmRoutes jvm = new JvmRoutes(() -> List.of(project.input().toFile()), () -> project.decompiler().getClassesWithInners());
        reads.put("/health", ctx -> ctx.json(Map.of("status", project.isLoading() ? "loading" : "ok", "service", "jadx-ai-mcp",
                "mode", "headless", "instance_id", instanceId, "project_name", project.input().getFileName().toString(),
                "generation", project.generation(), "capabilities", capabilities())));
        reads.put("/runtime-diagnostics", ctx -> ctx.json(RuntimeDiagnostics.snapshot(scheduler, classes.cacheStats())));
        reads.put("/class-info", analysis::classInfo); reads.put("/method-signature", analysis::methodSignature);
        reads.put("/search-native-methods", analysis::nativeMethods); reads.put("/jvm-info", jvm::handle);
        reads.put("/all-classes", classes::handleAllClasses); reads.put("/class-source", classes::handleClassSource);
        reads.put("/smali-of-class", classes::handleSmaliOfClass); reads.put("/methods-of-class", classes::handleMethodsOfClass);
        reads.put("/fields-of-class", classes::handleFieldsOfClass); reads.put("/main-activity", classes::handleMainActivity);
        reads.put("/main-application-classes-code", classes::handleMainApplicationClassesCode);
        reads.put("/main-application-classes-names", classes::handleMainApplicationClassesNames);
        reads.put("/package-tree", classes::handleGetPackageTree); reads.put("/search-classes-by-keyword", classes::handleSearchClassesByKeyword);
        reads.put("/search-progress", classes::handleSearchProgress); reads.put("/cache-stats", classes::handleCacheStats);
        reads.put("/method-by-name", methods::handleMethodByName); reads.put("/search-method", methods::handleSearchMethod);
        reads.put("/xrefs-to-class", xrefs::handleXrefsToClass); reads.put("/xrefs-to-method", xrefs::handleXrefsToMethod);
        reads.put("/xrefs-to-field", xrefs::handleXrefsToField); reads.put("/manifest", resources::handleManifest);
        reads.put("/strings", resources::handleStrings); reads.put("/list-all-resource-files-names", resources::handleListAllResourceFilesNames);
        reads.put("/get-resource-file", resources::handleGetResourceFile); reads.put("/project-operation", project::status);
        writes.put("/cache-clear", classes::handleCacheClear); writes.put("/open-apk", project::open);
        BatchRoutes batch = BatchRoutes.withGeneration(Map.of("class-source", classes::handleClassSource, "method-by-name", methods::handleMethodByName,
                "xrefs-to-class", xrefs::handleXrefsToClass, "xrefs-to-method", xrefs::handleXrefsToMethod,
                "xrefs-to-field", xrefs::handleXrefsToField), project::generation);
        writes.put("/batch-analysis", batch::handle);
    }
    private List<String> capabilities() {
        List<String> result = new ArrayList<>();
        reads.keySet().forEach(path -> result.add(path.substring(1))); writes.keySet().forEach(path -> result.add(path.substring(1)));
        return List.copyOf(result);
    }
    public int port() { return app.port(); }
    public synchronized void close() {
        if (closed) return;
        closed = true; scheduler.stopAdmission();
        try { app.stop(); } finally { project.close(); }
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        Map<String, String> options = new HashMap<>();
        Set<String> known = Set.of("--input", "--host", "--port", "--token-file");
        for (int i = 0; i < args.length; i += 2) {
            if (!known.contains(args[i]) || i + 1 >= args.length || options.put(args[i], args[i + 1]) != null)
                throw new IllegalArgumentException("Usage: --input ABSOLUTE_PATH [--host HOST] [--port PORT] [--token-file PATH]");
        }
        if (!options.containsKey("--input")) throw new IllegalArgumentException("--input is required");
        String host = options.getOrDefault("--host", System.getenv().getOrDefault("JADX_MCP_HOST", "127.0.0.1"));
        int port = Integer.parseInt(options.getOrDefault("--port", "8650"));
        if (port < 1 || port > 65535) throw new IllegalArgumentException("port must be 1..65535");
        String tokenPath = options.getOrDefault("--token-file", System.getenv("JADX_MCP_TOKEN_FILE"));
        HeadlessServer server = new HeadlessServer(Path.of(options.get("--input")), host, port,
                System.getenv("JADX_MCP_TOKEN"), tokenPath == null ? null : Path.of(tokenPath));
        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "jadx-headless-shutdown"));
        try { new CountDownLatch(1).await(); } finally { server.close(); }
    }
}
