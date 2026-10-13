package com.zin.jadxaimcp;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

class RouteInventoryTest {
    @Test void preservesBaselineRoutes() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/zin/jadxaimcp/server/PluginServer.java"));
        Matcher matcher = Pattern.compile("app\\.(get|post)\\(\"([^\"]+)\"").matcher(source);
        Set<String> routes = new HashSet<>();
        while (matcher.find()) assertTrue(routes.add(matcher.group(1) + " " + matcher.group(2)), "duplicate route");
        for (String baseline : Files.readAllLines(Path.of("src/test/resources/baseline-routes.txt"))) {
            assertTrue(routes.contains(baseline), "Missing baseline route: " + baseline);
        }
    }
}
