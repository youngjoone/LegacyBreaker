package com.github.youngjoone.legacybreaker.analysis;

import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;

public final class CallerFinder {
    public AnalysisResult find(PsiMethod target) {
        SearchBudget budget = new SearchBudget();
        ReferencesSearch.search(target, GlobalSearchScope.projectScope(target.getProject())).forEach(reference -> {
            if (!budget.step()) return false;
            PsiElement element = reference.getElement();
            if (!(element instanceof PsiReferenceExpression)) return true;
            PsiElement parent = element.getParent();
            if (!(parent instanceof PsiMethodCallExpression)) return true;
            PsiMethodCallExpression call = (PsiMethodCallExpression) parent;
            if (call.getMethodExpression() != element) return true;
            PsiMethod resolved = call.resolveMethod();
            // Exact static target; do not silently combine an override with its base declaration.
            if (resolved == null || !target.getManager().areElementsEquivalent(target, resolved)) return true;
            PsiMethod caller = MethodNodeMapper.enclosingMethod(call);
            return caller == null || budget.add(caller);
        });
        return budget.result;
    }
}
