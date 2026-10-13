package com.zin.jadxaimcp.server;

import io.javalin.Javalin;
import jadx.gui.ui.MainWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zin.jadxaimcp.utils.JadxAIMCPBanner;
import com.zin.jadxaimcp.utils.PaginationUtils;
import com.zin.jadxaimcp.server.routes.*; // MCP tool call's request handlers

public class PluginServer {
    private static final Logger logger = LoggerFactory.getLogger(PluginServer.class);
    // JVM-wide key to store the ServerSocketChannel for cross-classloader shutdown
    private static final String JVM_SERVER_KEY = "jadx-ai-mcp-server-channel";
    private final MainWindow mainWindow;
    private final int port;
    private Javalin app;
    private final RequestScheduler scheduler = new RequestScheduler();
    private ProjectRoutes projectRoutes;
    private final PaginationUtils paginationUtils;
    private volatile boolean isRunning = false;

    /**
     * @param mainWindows - The main Jadx window context
     * @param port        - The port to listen on
     */
    public PluginServer(MainWindow mainWindow, int port) {
        this.mainWindow = mainWindow;
        this.port = port;
        this.paginationUtils = new PaginationUtils();
    }

    /**
     * Starts the Javalin HTTP server for the MCP plugin.
     * Before starting, it closes any existing server socket from a previous
     * classloader to prevent port conflicts.
     */
    public void start() {
        try {
            // This solves github issue -> #81
            // Close any existing server socket from a previous classloader
            // (e.g. after Reset Code Cache)
            closeExistingServerSocket();

            // Configure and start Javalin
            app = Javalin.create(config -> {
                config.showJavalinBanner = false;
            }).start(port);

            // Extract and store the underlying ServerSocketChannel (JDK class) JVM-wide
            // so future classloaders can close it even if the old classloader is broken
            storeServerSocketChannel();

            // Register all route handlers
            registerRoutes();

            isRunning = true;

            // Log startup success and banner
            logger.info(JadxAIMCPBanner.banner);
            logger.info("// -------------------- JADX AI MCP PLUGIN -------------------- //");
            logger.info("JADX AI MCP Plugin HTTP Server Started at http://127.0.0.1:" + port + "/");

        } catch (Exception e) {
            logger.error("JADX-AI-MCP Plugin Error: Could not start HTTP Server. Exception: " + e.getMessage(), e);
            isRunning = false;
            // Re-throw to let the main plugin know startup failed
            throw new RuntimeException("Failed to start Javalin Server", e);
        }
    }

    /**
     * Performs graceful shutdown of the Javalin server.
     */
    public void stop() {
        if (app != null) {
            try {
                scheduler.stopAdmission();
                app.stop();
                System.getProperties().remove(JVM_SERVER_KEY);
                logger.info("JADX-AI-MCP Plugin: HTTP Server Stopped");
            } catch (Exception e) {
                logger.error("JADX-AI-MCP Plugin Error: Error during shutdown: " + e.getMessage(), e);
            } finally {
                app = null;
                isRunning = false;
            }
        }
    }

    /**
     * Extracts the underlying ServerSocketChannel from Javalin/Jetty via reflection
     * and stores it in JVM-wide System properties. This is done at start time when
     * the classloader is valid. The ServerSocketChannel is a JDK class and can be
     * closed later without any dependency on the plugin's classloader.
     *
     * Javalin 6 / Jetty 11 reflection chain:
     * Javalin -> jettyServer() -> server() -> getConnectors()[0] -> getTransport()
     */
    private void storeServerSocketChannel() {
        try {
            Object jettyServer = app.getClass().getMethod("jettyServer").invoke(app);
            Object server = jettyServer.getClass().getMethod("server").invoke(jettyServer);
            Object[] connectors = (Object[]) server.getClass().getMethod("getConnectors").invoke(server);
            if (connectors != null && connectors.length > 0) {
                Object transport = connectors[0].getClass().getMethod("getTransport").invoke(connectors[0]);
                if (transport instanceof java.nio.channels.ServerSocketChannel) {
                    System.getProperties().put(JVM_SERVER_KEY, transport);
                    logger.debug("JADX-AI-MCP Plugin: Stored ServerSocketChannel for cross-classloader cleanup");
                }
            }
        } catch (Exception e) {
            logger.warn("JADX-AI-MCP Plugin: Could not store server socket channel: " + e.getMessage());
        }
    }

    /**
     * Closes any existing ServerSocketChannel stored by a previous plugin instance.
     * This directly releases the port at the OS level without needing to call any
     * methods on the broken old classloader's Javalin/Jetty objects.
     */
    private void closeExistingServerSocket() {
        Object stored = System.getProperties().get(JVM_SERVER_KEY);
        if (stored instanceof java.nio.channels.ServerSocketChannel) {
            try {
                java.nio.channels.ServerSocketChannel channel = (java.nio.channels.ServerSocketChannel) stored;
                if (channel.isOpen()) {
                    logger.info("JADX-AI-MCP Plugin: Closing existing server socket from previous classloader...");
                    channel.close();
                    // Wait for the OS to fully release the port
                    Thread.sleep(1000);
                    logger.info("JADX-AI-MCP Plugin: Previous server socket closed, port released.");
                }
            } catch (Exception e) {
                logger.warn("JADX-AI-MCP Plugin: Error closing old server socket: " + e.getMessage());
            } finally {
                System.getProperties().remove(JVM_SERVER_KEY);
            }
        }
    }

    /**
     * @return boolean True if server is running, false otherwise
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * @return int The port number the server is configured to listen on
     */
    public int getPort() {
        return port;
    }

    public RequestScheduler scheduler() { return scheduler; }
    private void get(String path, io.javalin.http.Handler handler) {
        app.get(path, ctx -> scheduler.handle(ctx, handler, projectRoutes::isLoading));
    }
    private void post(String path, io.javalin.http.Handler handler) {
        app.post(path, ctx -> scheduler.handle(ctx, handler, projectRoutes::isLoading));
    }

    /**
     * Registers all HTTP API endpoints with their route handlers.
     */
    private void registerRoutes() {
        // Instantiate Route Controllers
        // Passing 'mainWindow' and 'paginationUtils' to them so they can do their work
        projectRoutes = new ProjectRoutes(mainWindow);
        post("/open-apk", projectRoutes::open);
        get("/project-operation", projectRoutes::status);
        AnalysisRoutes analysisRoutes = new AnalysisRoutes(mainWindow, paginationUtils);
        GeneralRoutes generalRoutes = new GeneralRoutes(mainWindow, port, this);
        ClassRoutes classRoutes = new ClassRoutes(mainWindow, paginationUtils);
        MethodRoutes methodRoutes = new MethodRoutes(mainWindow, paginationUtils);
        ResourceRoutes resourceRoutes = new ResourceRoutes(mainWindow);
        RefactoringRoutes refactoringRoutes = new RefactoringRoutes(mainWindow);
        DebugRoutes debugRoutes = new DebugRoutes(mainWindow);
        XrefsRoutes xrefsRoutes = new XrefsRoutes(mainWindow);
        CommentRoutes commentRoutes = new CommentRoutes(mainWindow);

        BatchRoutes batchRoutes = new BatchRoutes(java.util.Map.of(
                "class-source", classRoutes::handleClassSource,
                "method-by-name", methodRoutes::handleMethodByName,
                "xrefs-to-class", xrefsRoutes::handleXrefsToClass,
                "xrefs-to-method", xrefsRoutes::handleXrefsToMethod,
                "xrefs-to-field", xrefsRoutes::handleXrefsToField), mainWindow);
        post("/batch-analysis", batchRoutes::handle);

        get("/jvm-info", new JvmRoutes(mainWindow)::handle);

        // --- General & Health ---
        get("/health", generalRoutes::handleHealth);
        get("/class-info", analysisRoutes::classInfo);
        get("/method-signature", analysisRoutes::methodSignature);
        get("/search-native-methods", analysisRoutes::nativeMethods);

        // --- Class & Code Navigation ---
        get("/current-class", classRoutes::handleCurrentClass);
        get("/all-classes", classRoutes::handleAllClasses);
        get("/selected-text", classRoutes::handleSelectedText);
        get("/class-source", classRoutes::handleClassSource);
        get("/smali-of-class", classRoutes::handleSmaliOfClass);
        get("/methods-of-class", classRoutes::handleMethodsOfClass);
        get("/fields-of-class", classRoutes::handleFieldsOfClass);
        get("/main-application-classes-code", classRoutes::handleMainApplicationClassesCode);
        get("/main-application-classes-names", classRoutes::handleMainApplicationClassesNames);
        get("/main-activity", classRoutes::handleMainActivity);
        get("/search-classes-by-keyword", classRoutes::handleSearchClassesByKeyword);
        get("/search-progress", classRoutes::handleSearchProgress);
        get("/package-tree", classRoutes::handleGetPackageTree);
        get("/cache-stats", classRoutes::handleCacheStats);
        post("/cache-clear", classRoutes::handleCacheClear);

        // --- Methods ---
        get("/method-by-name", methodRoutes::handleMethodByName);
        get("/search-method", methodRoutes::handleSearchMethod);

        // --- Xrefs ---
        get("/xrefs-to-class", xrefsRoutes::handleXrefsToClass);
        get("/xrefs-to-method", xrefsRoutes::handleXrefsToMethod);
        get("/xrefs-to-field", xrefsRoutes::handleXrefsToField);

        // --- Resources & Manifest ---
        get("/manifest", resourceRoutes::handleManifest);
        get("/strings", resourceRoutes::handleStrings);
        get("/list-all-resource-files-names", resourceRoutes::handleListAllResourceFilesNames);
        get("/get-resource-file", resourceRoutes::handleGetResourceFile);

        // --- Renaming ---
        get("/rename-class", refactoringRoutes::handleRenameClass);
        get("/rename-method", refactoringRoutes::handleRenameMethod);
        get("/rename-field", refactoringRoutes::handleRenameField);
        get("/rename-package", refactoringRoutes::handleRenamePackage);
        get("/rename-variable", refactoringRoutes::handleRenameVariable);

        // --- Comments ---
        get("/add-comment", commentRoutes::handleAddComment);
        get("/list-comments", commentRoutes::handleListComments);

        // --- Debugging ---
        get("/debug/stack-frames", debugRoutes::handleGetStackFrames);
        get("/debug/variables", debugRoutes::handleGetVariables);
        get("/debug/threads", debugRoutes::handleGetThreads);
    }

}