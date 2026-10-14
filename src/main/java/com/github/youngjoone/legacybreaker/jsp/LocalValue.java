package com.github.youngjoone.legacybreaker.jsp;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.*;

/** Conservative reaching-value rule: initializer without writes, or one straight-line assignment. */
public final class LocalValue {
    private LocalValue() { }
    public static PsiExpression unwrap(PsiExpression expression) {
        while (expression instanceof PsiParenthesizedExpression || expression instanceof PsiTypeCastExpression) {
            expression = expression instanceof PsiParenthesizedExpression
                    ? ((PsiParenthesizedExpression) expression).getExpression() : ((PsiTypeCastExpression) expression).getOperand();
        }
        return expression;
    }

    public static PsiExpression origin(PsiLocalVariable variable, PsiElement use) {
        PsiMethod method = PsiTreeUtil.getParentOfType(variable, PsiMethod.class);
        if (method == null || method.getBody() == null) return null;
        //4차수정: 람다 내부는 재대입 검사가 생략되므로 초기값으로 연결하지 않고 경계에서 중단한다.
        for (PsiElement parent = use; parent != null && parent != method; parent = parent.getParent())
            if (parent instanceof PsiLoopStatement || parent instanceof PsiLambdaExpression) return null;
        List<PsiElement> writes = new ArrayList<>();
        method.getBody().accept(new JavaRecursiveElementWalkingVisitor() {
            @Override public void visitReferenceExpression(PsiReferenceExpression reference) {
                com.intellij.openapi.progress.ProgressManager.checkCanceled();
                if (reference.getTextOffset() < use.getTextOffset() && reference.resolve() == variable) {
                    PsiElement operand = reference;
                    while (operand.getParent() instanceof PsiParenthesizedExpression) operand = operand.getParent();
                    PsiElement parent = operand.getParent();
                    boolean increment = parent instanceof PsiPrefixExpression
                            && (((PsiPrefixExpression) parent).getOperationTokenType() == JavaTokenType.PLUSPLUS
                            || ((PsiPrefixExpression) parent).getOperationTokenType() == JavaTokenType.MINUSMINUS);
                    if ((parent instanceof PsiAssignmentExpression && ((PsiAssignmentExpression) parent).getLExpression() == operand)
                            || parent instanceof PsiPostfixExpression || increment) writes.add(parent);
                }
                super.visitReferenceExpression(reference);
            }
            @Override public void visitClass(PsiClass aClass) { }
            @Override public void visitLambdaExpression(PsiLambdaExpression expression) { }
        });
        if (writes.isEmpty()) return variable.getInitializer();
        if (variable.getInitializer() != null || writes.size() != 1 || !(writes.get(0) instanceof PsiAssignmentExpression)) return null;
        PsiAssignmentExpression assignment = (PsiAssignmentExpression) writes.get(0);
        if (assignment.getOperationTokenType() != JavaTokenType.EQ) return null;
        if (!(assignment.getParent() instanceof PsiExpressionStatement)) return null;
        PsiCodeBlock block = PsiTreeUtil.getParentOfType(use, PsiCodeBlock.class);
        return assignment.getParent().getParent() == block ? assignment.getRExpression() : null;
    }
}
