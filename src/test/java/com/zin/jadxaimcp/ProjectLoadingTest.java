package com.zin.jadxaimcp;
import com.zin.jadxaimcp.server.routes.ProjectRoutes;
import io.javalin.http.Context;
import jadx.gui.ui.MainWindow;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ProjectLoadingTest {
    @Test void completionRequiresNewDecompilerAndRequestedPath() throws Exception {
        MainWindow window = mock(MainWindow.class, RETURNS_DEEP_STUBS);
        when(window.getProject().isInitial()).thenReturn(true);
        when(window.getProject().getFilePaths()).thenReturn(java.util.List.of());
        java.util.List<jadx.gui.utils.ILoadListener> listeners = new java.util.ArrayList<>();
        doAnswer(call -> { jadx.gui.utils.ILoadListener listener = call.getArgument(0); listeners.add(listener); listener.update(true); return null; }).when(window).addLoadListener(any());
        ProjectRoutes routes = new ProjectRoutes(window);
        Context ctx = mock(Context.class, RETURNS_SELF);
        when(ctx.body()).thenReturn(new com.google.gson.Gson().toJson(Map.of("path", java.nio.file.Path.of("tests/fixtures/jadx-test-app-1.0.0.apk").toAbsolutePath().toString())));
        routes.open(ctx); javax.swing.SwingUtilities.invokeAndWait(() -> {});
        assertTrue(routes.isLoading());
        assertFalse(listeners.get(0).update(true));
        assertFalse(listeners.get(0).update(false));
        when(window.getWrapper().getDecompiler()).thenReturn(mock(jadx.api.JadxDecompiler.class));
        when(window.getProject().getFilePaths()).thenReturn(java.util.List.of(java.nio.file.Path.of("tests/fixtures/jadx-test-app-1.0.0.apk").toRealPath()));
        assertTrue(listeners.get(0).update(true)); assertFalse(routes.isLoading());
    }
    @Test void rejectsNonBooleanReplacementAndRelativePaths() {
        ProjectRoutes routes = new ProjectRoutes(mock(MainWindow.class, RETURNS_DEEP_STUBS));
        for (String body : java.util.List.of("{\"path\":\"relative.apk\"}", "{\"path\":\"/etc/hosts\",\"replace\":\"true\"}")) {
            Context ctx = mock(Context.class, RETURNS_SELF); when(ctx.body()).thenReturn(body);
            routes.open(ctx); verify(ctx).status(400);
        }
    }
    @Test void unsavedProjectIsNeverReplaced() throws Exception {
        MainWindow window = mock(MainWindow.class, RETURNS_DEEP_STUBS);
        when(window.getProject().isInitial()).thenReturn(false);
        when(window.getProject().isSaved()).thenReturn(false);
        ProjectRoutes routes = new ProjectRoutes(window);
        Context ctx = mock(Context.class, RETURNS_SELF);
        when(ctx.body()).thenReturn(new com.google.gson.Gson().toJson(Map.of("path", java.nio.file.Path.of("tests/fixtures/jadx-test-app-1.0.0.apk").toAbsolutePath().toString(), "replace", true)));
        routes.open(ctx);
        javax.swing.SwingUtilities.invokeAndWait(() -> {});
        verify(window, never()).open(any(java.nio.file.Path.class));
        ArgumentCaptor<Object> output = ArgumentCaptor.forClass(Object.class); verify(ctx).json(output.capture());
        Context status = mock(Context.class, RETURNS_SELF);
        when(status.queryParam("operation_id")).thenReturn((String) ((Map<?, ?>) output.getValue()).get("operation_id"));
        routes.status(status);
        ArgumentCaptor<Object> state = ArgumentCaptor.forClass(Object.class); verify(status).json(state.capture());
        assertEquals("failed", ((Map<?, ?>) state.getValue()).get("state"));
    }
}
