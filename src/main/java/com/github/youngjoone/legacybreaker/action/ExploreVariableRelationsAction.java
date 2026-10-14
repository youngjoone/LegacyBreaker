//4차수정: 신규 파일 전체 — Java 변수 선언/사용 위치에서 변수 탐색을 시작한다.
package com.github.youngjoone.legacybreaker.action;

import com.github.youngjoone.legacybreaker.variable.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.*;
import com.intellij.psi.PsiJavaFile;

public final class ExploreVariableRelationsAction extends AnAction {
    @Override public void update(AnActionEvent event) {
        event.getPresentation().setEnabledAndVisible(event.getProject() != null
                && event.getData(CommonDataKeys.EDITOR) != null && event.getData(CommonDataKeys.PSI_FILE) instanceof PsiJavaFile);
    }
    @Override public void actionPerformed(AnActionEvent event) {
        Project project = event.getProject(); Editor editor = event.getData(CommonDataKeys.EDITOR);
        VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);
        if (project == null || editor == null || file == null) return;
        VariableSession.getInstance(project).start(file, editor.getCaretModel().getOffset());
        ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(VariableToolWindowFactory.ID);
        if (window != null) window.activate(null);
    }
}
