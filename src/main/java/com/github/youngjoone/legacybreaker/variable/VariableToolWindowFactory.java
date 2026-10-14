//4차수정: 신규 파일 전체 — 변수 탐색 창을 등록한다.
package com.github.youngjoone.legacybreaker.variable;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.*;
import com.intellij.ui.content.*;

public final class VariableToolWindowFactory implements ToolWindowFactory {
    public static final String ID = "Variable Relations";
    @Override public void createToolWindowContent(Project project, ToolWindow window) {
        VariablePanel panel = new VariablePanel(VariableSession.getInstance(project));
        Content content = ContentFactory.SERVICE.getInstance().createContent(panel, "", false);
        content.setDisposer(panel); window.getContentManager().addContent(content);
    }
}
