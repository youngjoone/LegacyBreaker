package com.github.youngjoone.legacybreaker.navigation;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.pom.Navigatable;

public final class SourceNavigator {
    private SourceNavigator() { }

    public static void navigate(Project project, SmartPsiElementPointer<PsiMethod> pointer) {
        // Commit before dereferencing so edits do not leave navigation at an old offset.
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        PsiMethod method = pointer == null ? null : pointer.getElement();
        if (method != null && method.isValid() && method.getNavigationElement() instanceof Navigatable) {
            Navigatable target = (Navigatable) method.getNavigationElement();
            if (target.canNavigate()) {
                target.navigate(true);
                return;
            }
        }
        Messages.showInfoMessage(project, "Source is no longer available. Refresh or explore another method.",
                "Code Relations");
    }
}
