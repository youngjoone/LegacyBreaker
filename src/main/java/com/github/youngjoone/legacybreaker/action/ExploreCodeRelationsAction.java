package com.github.youngjoone.legacybreaker.action;

import com.github.youngjoone.legacybreaker.analysis.MethodAtCaret;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.github.youngjoone.legacybreaker.session.ExplorationSessionService;
import com.github.youngjoone.legacybreaker.ui.CodeRelationsToolWindowFactory;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;

public final class ExploreCodeRelationsAction extends AnAction {
    @Override
    public void update(AnActionEvent event) {
        // Menu updates must not resolve references or search the project.
        boolean available = event.getProject() != null
                && event.getData(CommonDataKeys.EDITOR) != null
                && event.getData(CommonDataKeys.PSI_FILE) instanceof PsiJavaFile;
        event.getPresentation().setEnabledAndVisible(available);
    }

    @Override
    public void actionPerformed(AnActionEvent event) {
        Project project = event.getProject();
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        if (project == null || editor == null) {
            return;
        }

        // Match PSI to unsaved editor changes before reading the caret position.
        PsiDocumentManager documents = PsiDocumentManager.getInstance(project);
        documents.commitDocument(editor.getDocument());
        PsiFile file = documents.getPsiFile(editor.getDocument());
        PsiMethod method = MethodAtCaret.find(file, editor.getCaretModel().getOffset());
        if (method == null) {
            Messages.showInfoMessage(project,
                    "Place the caret inside a Java method declaration or body.",
                    "Explore Code Relations");
            return;
        }

        ExplorationSessionService.getInstance(project).explore(method);
        ToolWindow window = ToolWindowManager.getInstance(project)
                .getToolWindow(CodeRelationsToolWindowFactory.ID);
        if (window != null) window.activate(null);
    }
}
