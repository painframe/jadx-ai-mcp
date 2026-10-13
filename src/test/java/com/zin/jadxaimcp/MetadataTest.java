package com.zin.jadxaimcp;
import com.zin.jadxaimcp.utils.FridaTypeConverter;
import com.zin.jadxaimcp.server.routes.AnalysisRoutes;
import jadx.api.*;
import jadx.core.dex.instructions.args.ArgType;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class MetadataTest {
    @Test void convertsFridaArrays() {
        assertEquals("[[I", FridaTypeConverter.toFridaType(ArgType.array(ArgType.array(ArgType.INT))));
        assertEquals("[Ljava.lang.String;", FridaTypeConverter.toFridaType(ArgType.array(ArgType.STRING)));
    }
    @Test void readsActualMethodMetadata() throws Exception {
        JadxArgs args = new JadxArgs(); args.setInputFiles(List.of(new File("tests/fixtures/jadx-test-library-1.0.0.jar")));
        try (JadxDecompiler jadx = new JadxDecompiler(args)) {
            jadx.load();
            JavaMethod method = jadx.getClasses().stream().filter(cls -> cls.getName().equals("Calculator")).findFirst().orElseThrow()
                    .getMethods().stream().filter(m -> m.getName().equals("add")).findFirst().orElseThrow();
            assertEquals("add", AnalysisRoutes.methodInfo(method).get("method_name"));
            assertEquals("'int', 'int'", AnalysisRoutes.methodInfo(method).get("frida_overload"));
        }
    }
}
