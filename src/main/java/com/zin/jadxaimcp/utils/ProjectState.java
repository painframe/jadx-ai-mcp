package com.zin.jadxaimcp.utils;

import jadx.gui.ui.MainWindow;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.UUID;

/** State belongs to a GUI instance and changes whenever its decompiler is replaced. */
public final class ProjectState {
    private static final Map<MainWindow, ProjectState> STATES = new WeakHashMap<>();
    private Object decompiler;
    private Object codeCache;
    private byte[] annotations = new byte[0];
    private String generation = UUID.randomUUID().toString();
    public static synchronized String generation(MainWindow window) {
        ProjectState state = STATES.computeIfAbsent(window, ignored -> new ProjectState());
        Object current = window.getWrapper().getDecompiler();
        Object currentCache = window.getWrapper().getArgs().getCodeCache();
        byte[] currentAnnotations = annotationVersion(window);
        if (state.decompiler != current || state.codeCache != currentCache || !java.util.Arrays.equals(state.annotations, currentAnnotations)) {
            state.decompiler = current; state.codeCache = currentCache; state.annotations = currentAnnotations;
            state.generation = UUID.randomUUID().toString();
        }
        return state.generation;
    }
    public static synchronized void invalidate(MainWindow window) {
        ProjectState state = STATES.computeIfAbsent(window, ignored -> new ProjectState());
        state.generation = UUID.randomUUID().toString();
    }
    private static byte[] annotationVersion(MainWindow window) {
        try {
            java.security.MessageDigest hash = java.security.MessageDigest.getInstance("SHA-256");
            jadx.api.data.ICodeData data = window.getProject().getCodeData();
            if (data != null) {
                for (jadx.api.data.ICodeComment comment : data.getComments()) {
                    hashValue(hash, comment.getNodeRef()); hashValue(hash, comment.getCodeRef());
                    hashValue(hash, comment.getComment()); hashValue(hash, comment.getStyle());
                }
                for (jadx.api.data.ICodeRename rename : data.getRenames()) {
                    hashValue(hash, rename.getNodeRef()); hashValue(hash, rename.getCodeRef()); hashValue(hash, rename.getNewName());
                }
            }
            return hash.digest();
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static void hashValue(java.security.MessageDigest hash, Object value) {
        byte[] bytes = String.valueOf(value).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        hash.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array()); hash.update(bytes);
    }
}
