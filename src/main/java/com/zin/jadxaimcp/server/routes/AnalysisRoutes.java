package com.zin.jadxaimcp.server.routes;

import com.zin.jadxaimcp.utils.*;
import io.javalin.http.Context;
import jadx.api.*;
import jadx.core.dex.instructions.args.ArgType;
import jadx.gui.ui.MainWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.stream.Collectors;

public class AnalysisRoutes {
    private static final Logger LOG = LoggerFactory.getLogger(AnalysisRoutes.class);
    private final java.util.function.Supplier<List<JavaClass>> classes;
    private final PaginationUtils pagination;
    public AnalysisRoutes(MainWindow window, PaginationUtils pagination) {
        this(() -> window.getWrapper().getIncludedClassesWithInners(), pagination);
    }
    public AnalysisRoutes(java.util.function.Supplier<List<JavaClass>> classes, PaginationUtils pagination) {
        this.classes = classes; this.pagination = pagination;
    }
    private JavaClass findClass(Context ctx) {
        String name = ctx.queryParam("class_name");
        if (name == null || name.isBlank()) {
            JadxAIMCPPluginError.handleError(ctx, 400, "Missing class_name", LOG); return null;
        }
        for (JavaClass cls : classes.get()) {
            if (name.equals(cls.getFullName()) || name.equals(cls.getRawName())) return cls;
        }
        JadxAIMCPPluginError.handleError(ctx, 404, "Class not found: " + name, LOG); return null;
    }
    public void classInfo(Context ctx) {
        try {
            JavaClass cls = findClass(ctx);
            if (cls == null) return;
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("class_name", cls.getFullName()); result.put("raw_name", cls.getRawName());
            result.put("simple_name", cls.getName()); result.put("package", cls.getPackage());
            result.put("access_flags", cls.getClassNode().getAccessFlags().toString());
            result.put("is_interface", cls.getClassNode().getAccessFlags().isInterface());
            result.put("is_enum", cls.getClassNode().getAccessFlags().isEnum());
            result.put("is_abstract", cls.getClassNode().getAccessFlags().isAbstract());
            result.put("is_final", cls.getClassNode().getAccessFlags().isFinal());
            result.put("is_inner", cls.isInner());
            result.put("super_class", Objects.toString(cls.getClassNode().getSuperClass(), null));
            result.put("interfaces", cls.getClassNode().getInterfaces().stream().map(Object::toString).collect(Collectors.toList()));
            result.put("inner_classes", cls.getInnerClasses().stream().map(JavaClass::getFullName).collect(Collectors.toList()));
            result.put("methods", cls.getMethods().stream().map(AnalysisRoutes::methodInfo).collect(Collectors.toList()));
            result.put("fields", cls.getFields().stream().map(field -> Map.of("name", field.getName(),
                    "type", field.getType().toString(), "access_flags", field.getAccessFlags().toString())).collect(Collectors.toList()));
            result.put("methods_count", cls.getMethods().size()); result.put("fields_count", cls.getFields().size());
            ctx.json(result);
        } catch (Exception error) { JadxAIMCPPluginError.handleError(ctx, "Cannot read class metadata", error, LOG); }
    }
    public void methodSignature(Context ctx) {
        try {
            JavaClass cls = findClass(ctx);
            if (cls == null) return;
            String name = ctx.queryParam("method_name");
            if (name == null || name.isBlank()) { JadxAIMCPPluginError.handleError(ctx, 400, "Missing method_name", LOG); return; }
            List<Map<String, Object>> signatures = cls.getMethods().stream()
                    .filter(method -> method.getName().equals(name) || method.getMethodNode().getMethodInfo().getName().equals(name))
                    .filter(method -> MethodSignatures.matches(method, ctx.queryParam("method_signature")))
                    .map(AnalysisRoutes::methodInfo).collect(Collectors.toList());
            if (signatures.isEmpty()) { JadxAIMCPPluginError.handleError(ctx, 404, "No matching method overload", LOG); return; }
            ctx.json(Map.of("class_name", cls.getFullName(), "method_name", name, "overloads", signatures.size(), "signatures", signatures));
        } catch (Exception error) { JadxAIMCPPluginError.handleError(ctx, "Cannot read method metadata", error, LOG); }
    }
    public void nativeMethods(Context ctx) {
        try {
            String packageName = ctx.queryParam("package");
            List<JavaMethod> methods = nativeMethods(classes.get(), packageName);
            ctx.json(pagination.handlePagination(ctx, methods, "native-methods", "methods", method -> methodInfo(method)));
        } catch (Exception error) { JadxAIMCPPluginError.handleError(ctx, "Cannot search native methods", error, LOG); }
    }
    public static List<JavaMethod> nativeMethods(List<JavaClass> classes, String packageName) {
        return classes.stream()
                .filter(cls -> packageName == null || packageName.isBlank() || cls.getPackage().equals(packageName)
                        || cls.getPackage().startsWith(packageName + "."))
                .flatMap(cls -> cls.getMethods().stream()).filter(method -> method.getAccessFlags().isNative())
                .sorted(Comparator.comparing(JavaMethod::getFullName)
                        .thenComparing(method -> method.getMethodNode().getMethodInfo().getShortId()))
                .collect(Collectors.toList());
    }
    public static Map<String, Object> methodInfo(JavaMethod method) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("method_name", method.getName()); result.put("class_name", method.getDeclaringClass().getFullName());
        result.put("signature", method.getMethodNode().getMethodInfo().getShortId());
        result.put("return_type", method.getReturnType().toString()); result.put("access_flags", method.getAccessFlags().toString());
        result.put("is_constructor", method.isConstructor()); result.put("is_native", method.getAccessFlags().isNative());
        result.put("is_static", method.getAccessFlags().isStatic()); result.put("is_abstract", method.getAccessFlags().isAbstract());
        List<Map<String, String>> parameters = new ArrayList<>();
        List<ArgType> types = method.getMethodNode().getMethodInfo().getArgumentsTypes();
        for (int i = 0; i < types.size(); i++) parameters.add(Map.of("name", "arg" + i, "type", types.get(i).toString(),
                "type_frida", FridaTypeConverter.toFridaType(types.get(i))));
        result.put("parameters", parameters); result.put("frida_overload", FridaTypeConverter.toFridaOverloadString(types));
        return result;
    }
}
