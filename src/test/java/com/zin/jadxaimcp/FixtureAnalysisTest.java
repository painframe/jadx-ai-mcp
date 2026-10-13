package com.zin.jadxaimcp;

import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FixtureAnalysisTest {
    @Test void loadsJarAndApk() throws Exception {
        for (String fixture : List.of("jadx-test-library-1.0.0.jar", "jadx-test-app-1.0.0.apk")) {
            JadxArgs args = new JadxArgs();
            args.setInputFiles(List.of(new File("tests/fixtures/" + fixture)));
            try (JadxDecompiler jadx = new JadxDecompiler(args)) {
                jadx.load();
                assertFalse(jadx.getClasses().isEmpty(), fixture);
                assertTrue(jadx.getClasses().stream().anyMatch(cls -> !cls.getCode().isBlank()), fixture);
            }
        }
    }
}
