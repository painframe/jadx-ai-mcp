package com.zin.jadxaimcp.utils;

import jadx.api.JavaClass;
import jadx.api.JavaNode;
import jadx.api.data.ICodeRename;
import jadx.api.data.impl.JadxCodeData;
import jadx.api.plugins.events.types.NodeRenamedByUser;
import jadx.gui.treemodel.JClass;
import jadx.gui.treemodel.JNode;
import jadx.gui.treemodel.JRenameNode;
import jadx.gui.ui.MainWindow;
import java.util.*;

/** Mirrors JADX RenameService's code-data update without asynchronous mutation dispatch. */
public final class SynchronousRename {
    private SynchronousRename() { }
    public static void apply(MainWindow window, NodeRenamedByUser event) {
        JavaNode javaNode = window.getWrapper().getDecompiler().getJavaNodeByRef(event.getNode());
        JNode node = window.getCacheObject().getNodeCache().makeFrom(javaNode);
        if (!(node instanceof JRenameNode)) throw new IllegalArgumentException("Node cannot be renamed");
        JRenameNode renameNode = (JRenameNode) node;
        JadxCodeData data = window.getProject().getCodeData();
        if (data == null) data = new JadxCodeData();
        Set<ICodeRename> renames = new HashSet<>(data.getRenames());
        ICodeRename rename = renameNode.buildCodeRename(event.getNewName(), renames);
        renames.remove(rename);
        if (event.isResetName() || event.getNewName().isEmpty()) renameNode.removeAlias();
        else renames.add(rename);
        List<ICodeRename> sorted = new ArrayList<>(renames); Collections.sort(sorted);
        data.setRenames(sorted); window.getProject().setCodeData(data);
        // Complete authoritative alias updates and source invalidation before returning.
        window.getWrapper().reloadCodeData();
        ProjectState.invalidate(window);
        Set<JavaClass> roots = new HashSet<>();
        for (JavaClass cls : window.getWrapper().getIncludedClassesWithInners()) roots.add(cls.getTopParentClass());
        for (JavaClass cls : roots) {
            JClass guiClass = window.getCacheObject().getNodeCache().makeFrom(cls);
            if (guiClass == null) cls.unload(); else guiClass.unload(window.getCacheObject());
        }
        // Refresh visible editor buffers and the package tree before releasing admission.
        // CodeArea.refreshClass reads the updated class; no rename event/background mutation is dispatched.
        jadx.gui.utils.UiUtils.uiRunAndWait(() -> {
            for (jadx.gui.ui.panel.ContentPanel tab : window.getTabbedPane().getTabs()) {
                if (tab instanceof jadx.gui.ui.codearea.ClassCodeContentPanel) {
                    jadx.gui.ui.codearea.AbstractCodeArea area = ((jadx.gui.ui.codearea.ClassCodeContentPanel) tab).getJavaCodePanel().getCodeArea();
                    if (area instanceof jadx.gui.ui.codearea.CodeArea) ((jadx.gui.ui.codearea.CodeArea) area).refreshClass();
                }
            }
            renameNode.reload(window);
        });
    }
}
