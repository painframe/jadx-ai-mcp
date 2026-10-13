package com.zin.jadxaimcp.server.search;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jadx.api.JavaClass;
import com.zin.jadxaimcp.utils.SearchPattern;
import com.zin.jadxaimcp.utils.SearchProgressTracker;
import jadx.gui.search.ISearchMethod;
import jadx.api.JavaNode;
import jadx.core.utils.ListUtils;
import jadx.gui.jobs.Cancelable;
import jadx.gui.search.ISearchProvider;
import jadx.gui.search.SearchSettings;
import jadx.gui.search.providers.ClassSearchProvider;
import jadx.gui.search.providers.CodeSearchProvider;
import jadx.gui.search.providers.CommentSearchProvider;
import jadx.gui.search.providers.FieldSearchProvider;
import jadx.gui.search.providers.MethodSearchProvider;
import jadx.gui.treemodel.CodeNode;
import jadx.gui.treemodel.JClass;
import jadx.gui.treemodel.JNode;
import jadx.gui.ui.MainWindow;

/**
 * Delegates keyword search to the same {@link jadx.gui.search.providers} used by
 * JADX-GUI ({@link jadx.gui.ui.dialog.SearchDialog}): {@link CodeSearchProvider}
 * reads {@link jadx.api.ICodeCache} before forcing decompilation, and uses
 * {@link jadx.gui.JadxWrapper#buildDecompileBatches} for multi-class code search.
 *
 * <p>This replaces the previous parallelStream + {@link jadx.api.JavaClass#getCode()} scan
 * in {@code ClassRoutes}, which was slower and could stress the decompiler differently
 * from the UI.
 */
public final class GuiAlignedSearch {
    private static final Logger LOG = LoggerFactory.getLogger(GuiAlignedSearch.class);

    private GuiAlignedSearch() {
    }

    /**
     * @param locationKeys subset of {@code class}, {@code method}, {@code field}, {@code code}, {@code comment}
     */
    public static LinkedHashSet<JavaClass> collectMatchingClasses(
            MainWindow mainWindow,
            List<JavaClass> allClasses,
            String searchTerm,
            SearchPattern pattern,
            String packageFilter,
            boolean applyPackageFilter,
            Set<String> locationKeys) {

        return collectMatchingClasses(mainWindow, allClasses, searchTerm, pattern, packageFilter,
                applyPackageFilter, locationKeys, Long.MAX_VALUE, new java.util.ArrayList<>());
    }
    public static LinkedHashSet<JavaClass> collectMatchingClasses(MainWindow mainWindow, List<JavaClass> allClasses,
            String searchTerm, SearchPattern pattern, String packageFilter, boolean applyPackageFilter,
            Set<String> locationKeys, long deadline, List<String> warnings) {
        SearchSettings ss = new SearchSettings(searchTerm) {
            @Override public boolean isMatch(String value) { return pattern.matches(value); }
            @Override public ISearchMethod getSearchMethod() { return (text, ignored, offset) -> pattern.find(text, offset); }
        };
        ss.setIgnoreCase(true);
        ss.setUseRegex(false);
        // GUI providers require explicit empty package/resource filters.
        if (applyPackageFilter && packageFilter != null && !packageFilter.trim().isEmpty()) {
            ss.setSearchPkgStr(packageFilter.trim());
        } else {
            ss.setSearchPkgStr("");
        }
        ss.setResFilterStr("");

        String err = ss.prepare(mainWindow);
        if (err != null) {
            throw new IllegalArgumentException(err);
        }

        List<JavaClass> searchClasses = allClasses;
        if (ss.getSearchPackage() != null) {
            searchClasses = allClasses.stream()
                    .filter(ss::isInSearchPkg)
                    .collect(Collectors.toList());
        }

        LinkedHashSet<JavaClass> out = new LinkedHashSet<>();
        if (searchClasses.isEmpty()) {
            return out;
        }

        Cancelable neverCancel = new Cancelable() {
            private volatile boolean canceled;

            @Override
            public boolean isCanceled() {
                return canceled || Thread.currentThread().isInterrupted() || System.nanoTime() >= deadline;
            }

            @Override
            public void cancel() {
                this.canceled = true;
            }
        };

        try {
        for (String key : locationKeys) {
            switch (key) {
                case "class":
                    drainUnique(new ClassSearchProvider(mainWindow, ss, searchClasses), out, neverCancel);
                    break;
                case "method":
                    drainUnique(new MethodSearchProvider(mainWindow, ss, searchClasses), out, neverCancel);
                    break;
                case "field":
                    drainUnique(new FieldSearchProvider(mainWindow, ss, searchClasses), out, neverCancel);
                    break;
                case "code":
                    addCodeMatches(mainWindow, ss, pattern, searchClasses, out, neverCancel);
                    break;
                case "comment":
                    drainUnique(new CommentSearchProvider(mainWindow, ss, searchClasses), out, neverCancel);
                    break;
                default:
                    LOG.warn("JADX AI MCP: unknown search location key '{}', skipping", key);
            }
        }
        } catch (SearchDeadline stopped) { warnings.add("Search stopped at its deadline or cancellation boundary; results are partial"); }
        return out;
    }

    private static final class SearchDeadline extends RuntimeException { }

    private static void addCodeMatches(MainWindow mainWindow, SearchSettings ss, SearchPattern pattern, List<JavaClass> searchClasses,
            LinkedHashSet<JavaClass> out, Cancelable c) {
        SearchSettings codeSettings = new SearchSettings(ss.getSearchString()) {
            @Override public jadx.api.JavaPackage getSearchPackage() { return ss.getSearchPackage(); }
            @Override public ISearchMethod getSearchMethod() {
                return new ISearchMethod() {
                    private String previousText;
                    public int find(String text, String ignored, int offset) {
                        if (text == previousText) return -1;
                        previousText = text;
                        if (!pattern.matches(text)) return -1;
                        // Only class identity is returned; one safe line position avoids GUI zero-width loops.
                        for (int i = 0; i < text.length(); i++) {
                            if (text.charAt(i) != '\n' && text.charAt(i) != '\r') return i;
                        }
                        return -1;
                    }
                };
            }
        };
        // Surface decompilation failures before the GUI provider can log and skip them.
        for (JavaClass cls : searchClasses) {
            if (c.isCanceled()) throw new SearchDeadline();
            if (!cls.isInner() && !cls.isNoCode()) {
                jadx.api.ICodeInfo code = cls.getCodeInfo();
                mainWindow.getWrapper().getArgs().getCodeCache().add(cls.getRawName(), code);
            }
        }
        if (searchClasses.size() == 1) {
            drainUnique(new CodeSearchProvider(mainWindow, codeSettings, searchClasses, null), out, c);
            return;
        }
        List<JavaClass> topClasses = ListUtils.filter(searchClasses, cls -> !cls.isInner());
        List<List<JavaClass>> batches = mainWindow.getWrapper().buildDecompileBatches(topClasses);
        Set<JavaClass> includedClasses = new HashSet<>(topClasses);
        for (List<JavaClass> batch : batches) {
            drainUnique(new CodeSearchProvider(mainWindow, codeSettings, batch, includedClasses), out, c);
        }
    }

    private static void drainUnique(ISearchProvider provider, LinkedHashSet<JavaClass> out, Cancelable c) {
        while (true) {
            JNode n;
            int before = provider.progress();
            n = provider.next(c);
            for (int i = before; i < provider.progress(); i++) SearchProgressTracker.getInstance().incrementScanned();
            if (c.isCanceled()) throw new SearchDeadline();
            if (n == null) {
                break;
            }
            JavaClass jc = javaClassFromJNode(n);
            if (jc != null) {
                if (out.add(jc)) SearchProgressTracker.getInstance().incrementMatches();
            }
        }
    }

    private static JavaClass javaClassFromJNode(JNode n) {
        if (n instanceof CodeNode) {
            return ((CodeNode) n).getRootClass().getCls();
        }
        if (n instanceof JClass) {
            return ((JClass) n).getCls();
        }
        JavaNode jn = n.getJavaNode();
        if (jn == null) {
            return null;
        }
        return jn.getTopParentClass();
    }
}
