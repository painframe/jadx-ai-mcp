package com.zin.jadxaimcp.headless;

import com.zin.jadxaimcp.utils.SearchPattern;
import com.zin.jadxaimcp.utils.SearchProgressTracker;
import jadx.api.JavaClass;
import java.util.*;

/** API search has no GUI selection/comment state and checks its budget between classes. */
public final class ApiSearch {
    private ApiSearch() { }
    public static Set<JavaClass> collect(List<JavaClass> classes, SearchPattern pattern, String packageName,
            Set<String> locations, long deadline, List<String> warnings, SearchProgressTracker progress) {
        if (locations.contains("comment")) throw new IllegalArgumentException("Comment search requires a GUI project");
        Set<JavaClass> result = new LinkedHashSet<>();
        for (JavaClass cls : classes) {
            if (Thread.currentThread().isInterrupted() || System.nanoTime() >= deadline) {
                warnings.add("Search stopped at its deadline or cancellation boundary; results are partial"); break;
            }
            if (packageName != null && !packageName.isBlank()
                    && !cls.getPackage().equals(packageName) && !cls.getPackage().startsWith(packageName + ".")) continue;
            boolean match = locations.contains("class") && pattern.matches(cls.getFullName());
            if (!match && locations.contains("method")) match = cls.getMethods().stream().anyMatch(m -> pattern.matches(m.getName()));
            if (!match && locations.contains("field")) match = cls.getFields().stream().anyMatch(f -> pattern.matches(f.getName()));
            if (!match && locations.contains("code")) match = pattern.matches(cls.getCode());
            for (int i = 0; i < locations.size(); i++) progress.incrementScanned();
            if (match) { result.add(cls); progress.incrementMatches(); }
        }
        return result;
    }
}
