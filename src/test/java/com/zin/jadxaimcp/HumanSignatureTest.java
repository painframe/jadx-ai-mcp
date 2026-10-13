package com.zin.jadxaimcp;
import com.zin.jadxaimcp.utils.MethodSignatures;
import jadx.api.*;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class HumanSignatureTest {
    @Test void rejectsAmbiguousSimpleTypesBeforeSelectingMethod() throws Exception {
        JadxArgs args = new JadxArgs(); args.setInputFiles(List.of(new File("target/test-classes/fixtures/Overloads.class")));
        try (JadxDecompiler jadx = new JadxDecompiler(args)) {
            jadx.load();
            assertThrows(MethodSignatures.AmbiguousSignatureException.class, () ->
                    MethodSignatures.ensureUnambiguous(jadx.getClasses(), "fixtures.Overloads", "run", "run(Foo):void"));
            assertDoesNotThrow(() -> MethodSignatures.ensureUnambiguous(jadx.getClasses(), "fixtures.Overloads", "run", "run(fixtures.a.Foo):void"));
        }
    }
    @Test void matchesExactHumanAndLegacyDescriptors() throws Exception {
        JadxArgs args = new JadxArgs(); args.setInputFiles(List.of(new File("tests/fixtures/jadx-test-library-1.0.0.jar")));
        try (JadxDecompiler jadx = new JadxDecompiler(args)) {
            jadx.load();
            JavaClass cls = jadx.getClasses().stream().filter(c -> c.getName().equals("Calculator")).findFirst().orElseThrow();
            JavaMethod method = cls.getMethods().stream().filter(m -> m.getName().equals("add")).findFirst().orElseThrow();
            assertTrue(MethodSignatures.matches(method, "add(int, int):int"));
            assertTrue(MethodSignatures.matches(method, "(int, int):int"));
            assertTrue(MethodSignatures.matches(method, "(II)I"));
            assertFalse(MethodSignatures.matches(method, "add(int):int"));
            assertFalse(MethodSignatures.matches(method, "add(int, int):void"));
            assertTrue(MethodSignatures.available(cls).contains("add(int, int):int"));
        }
    }
}
