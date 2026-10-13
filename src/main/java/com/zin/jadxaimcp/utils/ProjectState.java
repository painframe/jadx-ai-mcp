package com.zin.jadxaimcp.utils;

import jadx.gui.ui.MainWindow;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.UUID;

/** State belongs to a GUI instance and changes whenever its decompiler is replaced. */
public final class ProjectState {
    private static final Map<MainWindow, ProjectState> STATES = new WeakHashMap<>();
    private Object decompiler;
    private String generation = UUID.randomUUID().toString();
    public static synchronized String generation(MainWindow window) {
        ProjectState state = STATES.computeIfAbsent(window, ignored -> new ProjectState());
        Object current = window.getWrapper().getDecompiler();
        if (state.decompiler != current) { state.decompiler = current; state.generation = UUID.randomUUID().toString(); }
        return state.generation;
    }
    public static synchronized void invalidate(MainWindow window) {
        ProjectState state = STATES.computeIfAbsent(window, ignored -> new ProjectState());
        state.generation = UUID.randomUUID().toString();
    }
}
