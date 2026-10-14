//4차수정: 신규 파일 전체 — 선언 또는 사용 위치의 변수 이름을 정확히 해석한다.
package com.github.youngjoone.legacybreaker.variable;

import com.intellij.psi.*;

public final class VariableAtCaret {
    private VariableAtCaret() { }

    public static PsiVariable find(PsiFile file, int offset) {
        if (!(file instanceof PsiJavaFile) || offset < 0 || offset > file.getTextLength()) return null;
        PsiElement leaf = offset < file.getTextLength() ? file.findElementAt(offset) : null;
        if (!(leaf instanceof PsiIdentifier) && offset > 0) {
            PsiElement previous = file.findElementAt(offset - 1);
            if (previous instanceof PsiIdentifier && previous.getTextRange().getEndOffset() == offset) leaf = previous;
        }
        if (!(leaf instanceof PsiIdentifier)) return null;
        PsiElement parent = leaf.getParent();
        if (parent instanceof PsiVariable && ((PsiVariable) parent).getNameIdentifier() == leaf)
            return (PsiVariable) parent;
        if (parent instanceof PsiReferenceExpression && ((PsiReferenceExpression) parent).getReferenceNameElement() == leaf) {
            PsiElement resolved = ((PsiReferenceExpression) parent).resolve();
            return resolved instanceof PsiVariable ? (PsiVariable) resolved : null;
        }
        return null;
    }
}
