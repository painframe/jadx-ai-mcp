package com.zin.jadxaimcp;
import com.zin.jadxaimcp.utils.SearchPattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GuiSearchTest {
    @Test void literalCaseFoldingPreservesOriginalOffsets() {
        assertEquals(1, new SearchPattern("z", false, false).find("İZ", 0));
    }
    @Test void literalSearchKeepsOriginalExpression() {
        assertTrue(new SearchPattern("İ", false, false).matches("İ"));
    }
    @Test void realCodeProviderTerminatesZeroWidthSearch() throws Exception {
        jadx.api.JadxArgs args = new jadx.api.JadxArgs();
        args.setInputFiles(java.util.List.of(new java.io.File("tests/fixtures/jadx-test-library-1.0.0.jar")));
        try (jadx.api.JadxDecompiler decompiler = new jadx.api.JadxDecompiler(args)) {
            decompiler.load();
            jadx.gui.ui.MainWindow window = org.mockito.Mockito.mock(jadx.gui.ui.MainWindow.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
            org.mockito.Mockito.when(window.getWrapper().getDecompiler()).thenReturn(decompiler);
            org.mockito.Mockito.when(window.getWrapper().getArgs()).thenReturn(args);
            jadx.gui.utils.JNodeCache nodes = new jadx.gui.utils.JNodeCache(window.getWrapper());
            org.mockito.Mockito.when(window.getCacheObject().getNodeCache()).thenReturn(nodes);
            jadx.api.JavaClass cls = decompiler.getClasses().get(0);
            for (String expression : java.util.List.of("$", "(?=\\n)")) {
                try (SearchPattern pattern = new SearchPattern(expression, true, true)) {
                    assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () -> {
                        assertEquals(1, com.zin.jadxaimcp.server.search.GuiAlignedSearch.collectMatchingClasses(
                                window, java.util.List.of(cls), expression, pattern, null, false, java.util.Set.of("code")).size());
                    });
                }
            }
        }
    }
    @Test void decompilationFailureIsNotSilentlyPartial() {
        jadx.gui.ui.MainWindow window = org.mockito.Mockito.mock(jadx.gui.ui.MainWindow.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        jadx.api.JavaClass cls = org.mockito.Mockito.mock(jadx.api.JavaClass.class);
        org.mockito.Mockito.when(cls.getCodeInfo()).thenThrow(new IllegalStateException("fixture failure"));
        try (SearchPattern pattern = new SearchPattern("x", false, true)) {
            assertThrows(IllegalStateException.class, () -> com.zin.jadxaimcp.server.search.GuiAlignedSearch.collectMatchingClasses(
                    window, java.util.List.of(cls), "x", pattern, null, false, java.util.Set.of("code")));
        }
    }
    @Test void realGuiClassProviderSearchesFixture() throws Exception {
        jadx.api.JadxArgs args = new jadx.api.JadxArgs();
        args.setInputFiles(java.util.List.of(new java.io.File("tests/fixtures/jadx-test-library-1.0.0.jar")));
        try (jadx.api.JadxDecompiler decompiler = new jadx.api.JadxDecompiler(args);
             SearchPattern pattern = new SearchPattern("Calculator", false, true)) {
            decompiler.load();
            jadx.gui.ui.MainWindow window = org.mockito.Mockito.mock(jadx.gui.ui.MainWindow.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
            org.mockito.Mockito.when(window.getWrapper().getDecompiler()).thenReturn(decompiler);
            jadx.gui.utils.JNodeCache nodes = new jadx.gui.utils.JNodeCache(window.getWrapper());
            org.mockito.Mockito.when(window.getCacheObject().getNodeCache()).thenReturn(nodes);
            java.util.Set<jadx.api.JavaClass> result = com.zin.jadxaimcp.server.search.GuiAlignedSearch.collectMatchingClasses(
                    window, decompiler.getClasses(), "Calculator", pattern, null, false, java.util.Set.of("class"));
            assertEquals(1, result.size());
            assertEquals("Calculator", result.iterator().next().getName());
        }
    }
    @Test void isolatedMatcherSuppliesProviderOffsets() {
        try (SearchPattern pattern = new SearchPattern("[A-Z]+", true, true)) {
            assertEquals(2, pattern.find("a AB c CD", 0));
            assertEquals(7, pattern.find("a AB c CD", 4));
            assertEquals(-1, pattern.find("a AB c CD", 9));
        }
    }
}
