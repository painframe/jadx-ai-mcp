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
    private Set<String> routes(String source) {
        // Preserve string/character literals while removing Java comments; URLs may contain //.
        Matcher tokens = Pattern.compile("(?s)\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|/\\*.*?\\*/|//[^\\r\\n]*").matcher(source);
        StringBuffer code = new StringBuffer();
        while (tokens.find()) {
            String token = tokens.group();
            tokens.appendReplacement(code, Matcher.quoteReplacement(token.startsWith("/") ? " " : token));
        }
        tokens.appendTail(code);
        // Routes use get/post helpers that add scheduling; the literal inventory remains authoritative.
        Matcher matcher = Pattern.compile("(?m)^\\s*(?:app\\.)?(get|post)\\s*\\(\\s*\"([^\"]+)\"").matcher(code);
        Set<String> routes = new HashSet<>();
        while (matcher.find()) assertTrue(routes.add(matcher.group(1) + " " + matcher.group(2)), "duplicate route");
        return routes;
    }
    @Test void preservesBaselineRoutes() throws Exception {
        Set<String> routes = routes(Files.readString(Path.of("src/main/java/com/zin/jadxaimcp/server/PluginServer.java")));
        for (String baseline : Files.readAllLines(Path.of("src/test/resources/baseline-routes.txt"))) {
            assertTrue(routes.contains(baseline), "Missing baseline route: " + baseline);
        }
    }
    @Test void commentedRegistrationsCannotSatisfyInventory() {
        String source = "// app.get(\"/removed\", handler);\n/*\nget(\"/also-removed\", handler);\n*/\n"
                + "get(\"/actual\", handler); // post(\"/comment\", handler)\n"
                + "String url = \"http://example.test\";\napp.post(\"/other\", handler);\n";
        assertEquals(Set.of("get /actual", "post /other"), routes(source));
    }
}
