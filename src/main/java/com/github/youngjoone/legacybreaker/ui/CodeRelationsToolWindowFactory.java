package com.github.youngjoone.legacybreaker.ui;

import com.github.youngjoone.legacybreaker.session.ExplorationSessionService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;

public final class CodeRelationsToolWindowFactory implements ToolWindowFactory {
    public static final String ID = "Code Relations";

    @Override public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        CodeRelationsPanel panel = new CodeRelationsPanel(ExplorationSessionService.getInstance(project));
        Content content = ContentFactory.SERVICE.getInstance().createContent(panel, "", false);
        content.setDisposer(panel);
        toolWindow.getContentManager().addContent(content);
    }
}
