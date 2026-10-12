package com.github.youngjoone.legacybreaker.action;

import com.github.youngjoone.legacybreaker.analysis.MethodAtCaret;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;

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

        PsiClass owner = method.getContainingClass();
        String ownerName = owner == null ? null : owner.getQualifiedName();
        if (ownerName == null) {
            ownerName = owner == null ? "<unknown>" : owner.getName();
        }
        if (ownerName == null) {
            ownerName = "<anonymous class>";
        }

        StringBuilder signature = new StringBuilder(ownerName);
        signature.append('#').append(method.getName()).append('(');
        PsiParameter[] parameters = method.getParameterList().getParameters();
        for (int i = 0; i < parameters.length; i++) {
            if (i > 0) {
                signature.append(", ");
            }
            signature.append(parameters[i].getType().getPresentableText());
        }
        signature.append(')');

        Messages.showInfoMessage(project,
                "Selected method: " + signature
                        + "\n\nInitial milestone: caller/callee graph is not implemented yet.",
                "Explore Code Relations");
    }
}
