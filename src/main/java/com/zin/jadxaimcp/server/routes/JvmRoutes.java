package com.zin.jadxaimcp.server.routes;

import com.zin.jadxaimcp.utils.JadxAIMCPPluginError;
import com.zin.jadxaimcp.utils.PaginationUtils;
import io.javalin.http.Context;
import jadx.api.JavaClass;
import jadx.api.JavaMethod;
import jadx.gui.ui.MainWindow;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.zip.*;

/** Read-only JVM metadata. Archive entries are never extracted or executed. */
public final class JvmRoutes {
    private static final int ENTRY_BYTES = 65536, TOTAL_BYTES = 2097152, MAX_ENTRIES = 10000;
    private static final Set<String> SECTIONS = Set.of("manifest", "services", "entry-points", "dependencies", "properties", "packages");
    private final Supplier<List<File>> inputs;
    private final Supplier<List<JavaClass>> classes;
    public JvmRoutes(MainWindow window) { this(() -> window.getWrapper().getDecompiler().getArgs().getInputFiles(), () -> window.getWrapper().getIncludedClassesWithInners()); }
    public JvmRoutes(Supplier<List<File>> inputs, Supplier<List<JavaClass>> classes) { this.inputs = inputs; this.classes = classes; }
    public void handle(Context ctx) {
        String section = ctx.queryParam("section");
        if (!SECTIONS.contains(Objects.toString(section, ""))) { ctx.status(400).json(JadxAIMCPPluginError.response(400, "Unknown JVM section")); return; }
        try {
            List<Map<String, Object>> rows = analyze(section);
            Map<String, Object> result = new LinkedHashMap<>(new PaginationUtils().handlePagination(ctx, rows, "jvm-info", "items", row -> row));
            result.put("section", section); result.put("entry_byte_limit", ENTRY_BYTES); result.put("total_byte_limit", TOTAL_BYTES);
            ctx.json(result);
        } catch (LimitException e) { ctx.status(413).json(JadxAIMCPPluginError.response(413, e.getMessage())); }
        catch (PaginationUtils.PaginationException e) { ctx.status(400).json(JadxAIMCPPluginError.response(400, e.getMessage())); }
        catch (IllegalArgumentException e) { ctx.status(400).json(JadxAIMCPPluginError.response(400, e.getMessage())); }
        catch (UnsupportedOperationException e) { ctx.status(501).json(JadxAIMCPPluginError.response(501, e.getMessage())); }
        catch (Exception e) { ctx.status(500).json(JadxAIMCPPluginError.response(500, "Cannot read JVM metadata: " + e.getMessage())); }
    }
    public List<Map<String, Object>> analyze(String section) throws IOException {
        List<Map<String, Object>> result = new ArrayList<>();
        if (Set.of("manifest", "services", "properties").contains(section)) {
            List<File> jars = inputs.get().stream().filter(f -> f.getName().toLowerCase(Locale.ROOT).endsWith(".jar")).collect(Collectors.toList());
            if (jars.isEmpty()) throw new UnsupportedOperationException("This section requires a loaded JAR input");
            int total = 0, inspected = 0;
            for (File file : jars) try (ZipFile zip = new ZipFile(file)) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (++inspected > MAX_ENTRIES) throw new LimitException("Archive entry scan exceeds " + MAX_ENTRIES);
                    String name = entry.getName();
                    boolean include = section.equals("manifest") ? name.equalsIgnoreCase("META-INF/MANIFEST.MF")
                            : section.equals("services") ? name.startsWith("META-INF/services/") : name.endsWith(".properties");
                    if (!include || entry.isDirectory()) continue;
                    byte[] bytes;
                    try (InputStream in = zip.getInputStream(entry)) { bytes = in.readNBytes(ENTRY_BYTES + 1); }
                    if (bytes.length > ENTRY_BYTES || (total += bytes.length) > TOTAL_BYTES) throw new LimitException("JVM metadata exceeds bounded response budget");
                    String text = new String(bytes, StandardCharsets.UTF_8);
                    Map<String, Object> row = new LinkedHashMap<>(); row.put("archive", file.getName()); row.put("name", name);
                    if (section.equals("manifest")) {
                        java.util.jar.Manifest manifest = new java.util.jar.Manifest(new ByteArrayInputStream(bytes));
                        Map<String, String> attributes = new TreeMap<>();
                        manifest.getMainAttributes().forEach((k,v) -> attributes.put(k.toString(), v.toString())); row.put("attributes", attributes);
                    } else if (section.equals("services")) {
                        row.put("service", name.substring("META-INF/services/".length()));
                        row.put("providers", text.lines().map(line -> line.split("#", 2)[0].strip()).filter(line -> !line.isEmpty()).collect(Collectors.toList()));
                    } else {
                        Properties properties = new Properties(); properties.load(new ByteArrayInputStream(bytes));
                        row.put("properties", new TreeMap<>(properties));
                    }
                    result.add(row);
                }
            }
        } else if (section.equals("packages")) {
            Map<String, Long> counts = classes.get().stream().collect(Collectors.groupingBy(JavaClass::getPackage, TreeMap::new, Collectors.counting()));
            counts.forEach((name,count) -> result.add(Map.of("package", name, "class_count", count)));
        } else for (JavaClass cls : classes.get()) {
            if (section.equals("dependencies")) result.add(Map.of("class_name", cls.getFullName(), "dependencies", cls.getDependencies().stream().map(JavaClass::getFullName).sorted().collect(Collectors.toList())));
            else for (JavaMethod method : cls.getMethods()) if (method.getName().equals("main") && method.getAccessFlags().isPublic() && method.getAccessFlags().isStatic()
                    && method.getReturnType().toString().equals("void") && method.getArguments().size() == 1 && method.getArguments().get(0).toString().equals("java.lang.String[]")) result.add(AnalysisRoutes.methodInfo(method));
        }
        return result;
    }
    private static final class LimitException extends IOException { LimitException(String message) { super(message); } }
}
