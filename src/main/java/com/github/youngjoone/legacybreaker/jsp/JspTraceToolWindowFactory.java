package com.github.youngjoone.legacybreaker.jsp;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.*;
import com.intellij.ui.content.*;

public final class JspTraceToolWindowFactory implements ToolWindowFactory {
    public static final String ID = "JSP Value Origins";
    @Override public void createToolWindowContent(Project project, ToolWindow window) {
        JspTracePanel panel = new JspTracePanel(JspTraceService.getInstance(project));
        Content content = ContentFactory.SERVICE.getInstance().createContent(panel, "", false);
        content.setDisposer(panel); window.getContentManager().addContent(content);
    }
}
