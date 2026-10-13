package com.github.youngjoone.legacybreaker.analysis;

import com.intellij.psi.*;

public final class CalleeFinder {
    public AnalysisResult find(PsiMethod source) {
        SearchBudget budget = new SearchBudget();
        if (source.getBody() == null) return budget.result;
        try {
            source.getBody().accept(new JavaRecursiveElementWalkingVisitor() {
                @Override public void visitElement(PsiElement element) {
                    if (!budget.step()) throw new LimitReached();
                    super.visitElement(element);
                }
                @Override public void visitClass(PsiClass aClass) { }
                @Override public void visitLambdaExpression(PsiLambdaExpression expression) { }
                @Override public void visitMethodCallExpression(PsiMethodCallExpression call) {
                    if (!budget.step()) throw new LimitReached();
                    PsiMethod target = call.resolveMethod();
                    if (target == null) budget.result.unresolved++;
                    else if (!target.isConstructor() && !budget.add(target)) throw new LimitReached();
                    super.visitMethodCallExpression(call);
                }
            });
        } catch (LimitReached ignored) {
            // A local traversal limit, never IntelliJ's ProcessCanceledException.
        }
        return budget.result;
    }

    private static final class LimitReached extends RuntimeException {
        LimitReached() { super(null, null, false, false); }
    }
}
