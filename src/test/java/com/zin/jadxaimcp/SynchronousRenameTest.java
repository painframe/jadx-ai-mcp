package com.zin.jadxaimcp;
import com.zin.jadxaimcp.utils.SynchronousRename;
import jadx.api.*;
import jadx.api.plugins.events.types.NodeRenamedByUser;
import jadx.gui.ui.MainWindow;
import jadx.gui.utils.JNodeCache;
import jadx.gui.settings.JadxProject;
import org.junit.jupiter.api.Test;
import java.io.File;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class SynchronousRenameTest {
    @Test void aliasAndSourceChangeBeforeReturnWithoutEventDispatch() throws Exception {
        JadxArgs args = new JadxArgs(); args.setInputFile(new File("tests/fixtures/jadx-test-library-1.0.0.jar"));
        try (JadxDecompiler decompiler = new JadxDecompiler(args)) {
            decompiler.load(); JavaClass cls = decompiler.getClasses().get(0); cls.getCode();
            MainWindow window = mock(MainWindow.class, RETURNS_DEEP_STUBS);
            JadxProject project = new JadxProject(window); when(window.getProject()).thenReturn(project);
            when(window.getWrapper().getDecompiler()).thenReturn(decompiler);
            when(window.getWrapper().getIncludedClassesWithInners()).thenReturn(decompiler.getClassesWithInners());
            JNodeCache cache = new JNodeCache(window.getWrapper());
            when(window.getCacheObject().getNodeCache()).thenReturn(cache);
            jadx.gui.JadxWrapper wrapper = window.getWrapper();
            doAnswer(invocation -> { args.setCodeData(project.getCodeData()); decompiler.reloadCodeData(); return null; }).when(wrapper).reloadCodeData();
            NodeRenamedByUser event = new NodeRenamedByUser(cls.getCodeNodeRef(), cls.getName(), "RenamedFixture");
            SynchronousRename.apply(window, event);
            assertEquals("RenamedFixture", cls.getName());
            assertTrue(cls.getCode().contains("RenamedFixture"));
            verify(window.events(), never()).send(any());
        }
    }
}
