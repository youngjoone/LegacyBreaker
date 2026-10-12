package com.github.youngjoone.legacybreaker.analysis;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;

public final class MethodAtCaret {
    private MethodAtCaret() {
    }

    // Call under IntelliJ read access (the editor action runs on the EDT).
    public static PsiMethod find(PsiFile file, int offset) {
        if (!(file instanceof PsiJavaFile) || offset < 0 || offset >= file.getTextLength()) {
            return null;
        }
        PsiElement element = file.findElementAt(offset);
        while (element != null) {
            if (element instanceof PsiMethod) {
                PsiMethod method = (PsiMethod) element;
                return method.isConstructor() ? null : method;
            }
            // Do not treat an anonymous/local class initializer as its outer method.
            if (element instanceof PsiClass) {
                return null;
            }
            element = element.getParent();
        }
        return null;
    }
}
