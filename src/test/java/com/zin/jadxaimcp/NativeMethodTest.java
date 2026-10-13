package com.zin.jadxaimcp;
import com.zin.jadxaimcp.server.routes.AnalysisRoutes;
import jadx.api.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class NativeMethodTest {
    @Test void nativeFilterHonorsPackageBoundary() {
        JavaClass cls = mock(JavaClass.class);
        JavaMethod nativeMethod = mock(JavaMethod.class, RETURNS_DEEP_STUBS);
        JavaMethod plainMethod = mock(JavaMethod.class, RETURNS_DEEP_STUBS);
        when(cls.getPackage()).thenReturn("sample.inner");
        when(cls.getMethods()).thenReturn(List.of(plainMethod, nativeMethod));
        when(nativeMethod.getAccessFlags().isNative()).thenReturn(true);
        assertEquals(List.of(nativeMethod), AnalysisRoutes.nativeMethods(List.of(cls), "sample"));
        assertTrue(AnalysisRoutes.nativeMethods(List.of(cls), "sam").isEmpty());
    }
}
