package com.github.youngjoone.legacybreaker.action;

import com.github.youngjoone.legacybreaker.jsp.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.*;
import java.util.Locale;

public final class TraceJspValueAction extends AnAction {
    @Override public void update(AnActionEvent event) {
        VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);
        boolean enabled = event.getProject() != null && event.getData(CommonDataKeys.EDITOR) != null && file != null
                && file.getName().toLowerCase(Locale.ROOT).endsWith(".jsp");
        event.getPresentation().setEnabledAndVisible(enabled);
    }
    @Override public void actionPerformed(AnActionEvent event) {
        Project project = event.getProject();
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);
        if (project == null || editor == null || file == null) return;
        JspTraceService.getInstance(project).start(file, editor.getCaretModel().getOffset());
        ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(JspTraceToolWindowFactory.ID);
        if (window != null) window.activate(null);
    }
}
