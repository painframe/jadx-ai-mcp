package com.zin.jadxaimcp;

import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FixtureAnalysisTest {
    @Test void archivesContainNativeFormatPayloads() throws Exception {
        // Assert the format-specific payloads, not merely a ZIP extension or nonempty class list.
        try (java.util.zip.ZipFile jar = new java.util.zip.ZipFile("tests/fixtures/jadx-test-library-1.0.0.jar");
             java.util.zip.ZipFile apk = new java.util.zip.ZipFile("tests/fixtures/jadx-test-app-1.0.0.apk")) {
            assertNotNull(jar.getEntry("META-INF/MANIFEST.MF"));
            assertNotNull(apk.getEntry("AndroidManifest.xml"));
            try (java.io.InputStream classFile = jar.getInputStream(jar.getEntry("com/jadxtest/library/Calculator.class"));
                 java.io.InputStream dex = apk.getInputStream(apk.getEntry("classes.dex"))) {
                assertArrayEquals(new byte[]{(byte) 0xca, (byte) 0xfe, (byte) 0xba, (byte) 0xbe}, classFile.readNBytes(4));
                assertArrayEquals(new byte[]{'d', 'e', 'x', '\n'}, dex.readNBytes(4));
            }
        }
    }
    @Test void loadsJarAndApk() throws Exception {
        for (String fixture : List.of("jadx-test-library-1.0.0.jar", "jadx-test-app-1.0.0.apk")) {
            JadxArgs args = new JadxArgs();
            args.setInputFiles(List.of(new File("tests/fixtures/" + fixture)));
            try (JadxDecompiler jadx = new JadxDecompiler(args)) {
                jadx.load();
                assertFalse(jadx.getClasses().isEmpty(), fixture);
                if (fixture.endsWith(".apk")) {
                    jadx.api.ResourceFile manifest = jadx.getResources().stream()
                            .filter(resource -> resource.getOriginalName().endsWith("AndroidManifest.xml")).findFirst().orElseThrow();
                    // Loading classes alone can pass for an incorrectly packaged ZIP. Decode the actual APK manifest too.
                    assertTrue(manifest.loadContent().getText().getCodeStr().contains("com.jadxtest.app"));
                }
                assertTrue(jadx.getClasses().stream().anyMatch(cls -> !cls.getCode().isBlank()), fixture);
            }
        }
    }
}
