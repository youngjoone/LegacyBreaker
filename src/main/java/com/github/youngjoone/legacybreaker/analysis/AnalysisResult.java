package com.github.youngjoone.legacybreaker.analysis;

import com.github.youngjoone.legacybreaker.model.CodeNode;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.SmartPsiElementPointer;
import java.util.LinkedHashMap;
import java.util.Map;

/** Created inside one read action, handed to the session only after successful completion. */
public final class AnalysisResult {
    public final Map<String, CodeNode> nodes = new LinkedHashMap<String, CodeNode>();
    public final Map<String, SmartPsiElementPointer<PsiMethod>> pointers =
            new LinkedHashMap<String, SmartPsiElementPointer<PsiMethod>>();
    public boolean truncated;
    public int unresolved;
}
