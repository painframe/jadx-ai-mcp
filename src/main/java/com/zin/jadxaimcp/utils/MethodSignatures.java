package com.zin.jadxaimcp.utils;

import jadx.api.JavaMethod;
import jadx.core.dex.info.MethodInfo;
import jadx.core.dex.nodes.MethodNode;

public class MethodSignatures {

    private MethodSignatures() {
    }

    /**
     * @param method The method to test against the caller supplied signature
     * @param methodSignature The signature/descriptor to match, e.g. '(I)V'
     * @return boolean True when the signature identifies this method
     *
     * MethodInfo builds its shortId once from the original method name and never
     * refreshes it, while JavaMethod.getName() reports the alias. After a rename the
     * two disagree, so a signature carrying the renamed name never matches the
     * shortId and the method looks missing. Both forms are compared here so a
     * signature keeps resolving before and after a rename.
     */
    public static boolean matches(JavaMethod method, String methodSignature) {
        if (methodSignature == null || methodSignature.isEmpty()) {
            return true;
        }

        MethodNode methodNode = method.getMethodNode();
        if (methodNode == null) {
            return false;
        }

        MethodInfo methodInfo = methodNode.getMethodInfo();
        if (methodInfo == null) {
            return false;
        }

        java.util.regex.Matcher human = java.util.regex.Pattern.compile("^([^()]*)\\(([^()]*)\\)(?::([^:]+))?$")
                .matcher(methodSignature.trim());
        if (human.matches()) {
            String name = human.group(1).trim();
            if (!name.isEmpty() && !name.equals(method.getName()) && !name.equals(methodInfo.getName())) return false;
            String arguments = human.group(2).trim();
            String[] requested = arguments.isEmpty() ? new String[0] : arguments.split(",", -1);
            java.util.List<jadx.core.dex.instructions.args.ArgType> types = methodInfo.getArgumentsTypes();
            if (requested.length != types.size()) return false;
            for (int i = 0; i < requested.length; i++) {
                if (!typeMatches(types.get(i).toString(), requested[i].trim())) return false;
            }
            return human.group(3) == null || typeMatches(method.getReturnType().toString(), human.group(3).trim());
        }

        if (methodInfo.getShortId().contains(methodSignature)) {
            return true;
        }
        return methodInfo.makeSignature(true, true).contains(methodSignature);
    }
    private static boolean typeMatches(String actual, String requested) {
        if (actual.equals(requested)) return true;
        int packageEnd = actual.lastIndexOf('.');
        return packageEnd >= 0 && actual.substring(packageEnd + 1).equals(requested);
    }

    public static java.util.List<String> available(jadx.api.JavaClass cls) {
        return cls.getMethods().stream().limit(50).map(method -> method.getName() + "("
                + method.getMethodNode().getMethodInfo().getArgumentsTypes().stream()
                    .map(Object::toString).collect(java.util.stream.Collectors.joining(", "))
                + "):" + method.getReturnType()).collect(java.util.stream.Collectors.toList());
    }
    public static void ensureUnambiguous(java.util.List<jadx.api.JavaClass> classes, String className,
            String methodName, String signature) {
        if (signature == null || !(signature.contains(":") || signature.endsWith(")"))) return;
        java.util.List<String> candidates = new java.util.ArrayList<>();
        for (jadx.api.JavaClass cls : classes) {
            if (className != null && !className.isEmpty() && !className.equals(cls.getFullName()) && !className.equals(cls.getRawName())) continue;
            for (JavaMethod method : cls.getMethods()) {
                if ((method.getName().equalsIgnoreCase(methodName) || (cls.getFullName() + "." + method.getName()).equalsIgnoreCase(methodName))
                        && matches(method, signature)) {
                    candidates.add(method.getFullName() + " " + method.getMethodNode().getMethodInfo().getShortId());
                    if (candidates.size() >= 50) break;
                }
            }
            if (candidates.size() >= 50) break;
        }
        if (candidates.size() > 1) throw new AmbiguousSignatureException(candidates);
    }

    public static final class AmbiguousSignatureException extends IllegalArgumentException {
        public final java.util.List<String> candidates;
        AmbiguousSignatureException(java.util.List<String> candidates) {
            super("Ambiguous human signature; use fully qualified parameter types");
            this.candidates = java.util.List.copyOf(candidates);
        }
    }
}
