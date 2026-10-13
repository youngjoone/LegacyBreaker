package com.github.youngjoone.legacybreaker.analysis;

import com.github.youngjoone.legacybreaker.model.Direction;
import com.intellij.psi.PsiMethod;

public final class MethodAnalyzer {
    public AnalysisResult analyze(PsiMethod method, Direction direction) {
        if (!method.isValid()) throw new IllegalStateException("Method no longer exists. Explore again.");
        return direction == Direction.CALLERS ? new CallerFinder().find(method) : new CalleeFinder().find(method);
    }
}
