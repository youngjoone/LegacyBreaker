package com.github.youngjoone.legacybreaker.analysis;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.SmartPointerManager;
import com.github.youngjoone.legacybreaker.model.CodeNode;

final class SearchBudget {
    private final long deadline = System.nanoTime() + 5_000_000_000L;
    private int visited;
    final AnalysisResult result = new AnalysisResult();

    boolean step() {
        ProgressManager.checkCanceled();
        if (++visited > 20000 || System.nanoTime() > deadline) {
            result.truncated = true;
            return false;
        }
        return true;
    }

    boolean add(PsiMethod method) {
        CodeNode node = MethodNodeMapper.map(method);
        if (!result.nodes.containsKey(node.id) && result.nodes.size() >= 100) {
            result.truncated = true;
            return false;
        }
        result.nodes.put(node.id, node);
        result.pointers.put(node.id, SmartPointerManager.getInstance(method.getProject())
                .createSmartPsiElementPointer(method));
        return true;
    }
}
