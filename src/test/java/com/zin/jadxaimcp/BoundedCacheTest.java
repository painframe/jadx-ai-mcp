package com.zin.jadxaimcp;
import com.zin.jadxaimcp.utils.DecompilationCache;
import com.zin.jadxaimcp.utils.ProjectState;
import jadx.gui.ui.MainWindow;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class BoundedCacheTest {
    @Test void boundsEntriesAndInvalidatesByProjectGeneration() {
        MainWindow a = mock(MainWindow.class, RETURNS_DEEP_STUBS);
        MainWindow b = mock(MainWindow.class, RETURNS_DEEP_STUBS);
        DecompilationCache cache = DecompilationCache.forWindow(a);
        for (int i = 0; i < 513; i++) cache.put("C" + i, "source " + i);
        assertEquals(512, cache.getStats().get("cached_classes"));
        assertNull(cache.get("C0")); assertEquals("source 512", cache.get("C512"));
        assertNull(DecompilationCache.forWindow(b).get("C512"));
        ProjectState.invalidate(a); assertNull(cache.get("C512"));
        cache.put("large", "x".repeat(4194305)); assertNull(cache.get("large"));
    }
    @Test void oldComputationCannotPopulateNewProjectGeneration() {
        MainWindow window = mock(MainWindow.class, RETURNS_DEEP_STUBS);
        DecompilationCache cache = DecompilationCache.forWindow(window);
        String captured = cache.generation();
        ProjectState.invalidate(window);
        cache.put("A", "stale old computation", captured);
        assertNull(cache.get("A"));
        cache.put("A", "fresh", cache.generation()); assertEquals("fresh", cache.get("A"));
    }
    @Test void externalGuiCommentEditsInvalidateCacheAndCursors() {
        MainWindow window = mock(MainWindow.class, RETURNS_DEEP_STUBS);
        jadx.api.data.impl.JadxCodeData data = new jadx.api.data.impl.JadxCodeData();
        when(window.getProject().getCodeData()).thenReturn(data);
        DecompilationCache cache = DecompilationCache.forWindow(window);
        cache.put("A", "old"); String before = ProjectState.generation(window);
        jadx.api.data.impl.JadxCodeComment comment = new jadx.api.data.impl.JadxCodeComment();
        comment.setComment("manually edited comment"); data.setComments(java.util.List.of(comment));
        assertNull(cache.get("A")); assertNotEquals(before, ProjectState.generation(window));
    }
    @Test void replacementAndInvalidationKeepExactAccounting() {
        DecompilationCache cache = DecompilationCache.getInstance(); cache.clear();
        cache.put("A", "old"); cache.put("A", "new content");
        assertEquals(11L, cache.getStats().get("original_bytes"));
        cache.invalidate("A"); assertEquals(0L, cache.getStats().get("original_bytes"));
    }
}
