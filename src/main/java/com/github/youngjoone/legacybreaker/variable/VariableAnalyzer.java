//4차수정: 신규 파일 전체 — Java 변수의 초기값, 읽기, 쓰기, 복합 대입/증감 위치를 수집한다.
package com.github.youngjoone.legacybreaker.variable;

import com.github.youngjoone.legacybreaker.model.*;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import java.util.*;

public final class VariableAnalyzer {
    public static final int MAX_REFERENCES = 200;

    public VariableAnalysis analyze(PsiVariable variable) {
        if (!(variable instanceof PsiLocalVariable || variable instanceof PsiParameter || variable instanceof PsiField)
                || variable instanceof PsiEnumConstant || !variable.isValid() || variable.getContainingFile().getVirtualFile() == null
                || !ProjectFileIndex.getInstance(variable.getProject()).isInSourceContent(variable.getContainingFile().getVirtualFile()))
            throw new IllegalArgumentException("Select a local variable, parameter or field in a project Java source root.");
        VariableAnalysis result = new VariableAnalysis();
        result.variable = SmartPointerManager.getInstance(variable.getProject()).createSmartPsiElementPointer(variable);
        String kind = variable instanceof PsiField ? "field" : variable instanceof PsiParameter ? "parameter" : "local variable";
        CodeNode root = result.node(variable, "VARIABLE", variable.getName(), variable.getType().getPresentableText() + " " + kind
                + "." + (variable instanceof PsiField ? " Field occurrences refer to the same declaration, not necessarily the same object." : "")
                + " Only direct variable accesses are listed; object contents and inter-method value flow are not traced.");
        result.graph.reset(root);
        PsiExpression initializer = variable.getInitializer();
        if (initializer != null) {
            CodeNode origin = result.node(initializer, "INITIALIZER", "[INITIALIZER] " + preview(initializer),
                    "Explicit initializer: " + snippet(initializer) + ". Expression shown as source; not evaluated or recursively traced.");
            result.graph.connect(root.id, origin, Direction.CALLERS, "INITIALIZES");
            result.initializers = 1;
        }
        long deadline = System.nanoTime() + 5_000_000_000L;
        int[] visits = {0};
        Set<String> seen = new HashSet<>();
        ReferencesSearch.search(variable, GlobalSearchScope.projectScope(variable.getProject())).forEach(reference -> {
            synchronized (result) {
                ProgressManager.checkCanceled();
                if (++visits[0] > 20000 || System.nanoTime() > deadline) { result.partial = true; return false; }
                PsiElement element = reference.getElement();
                if (!(element instanceof PsiReferenceExpression)) return true;
                PsiReferenceExpression use = (PsiReferenceExpression) element;
                if (use.getContainingFile().getVirtualFile() == null
                        || !ProjectFileIndex.getInstance(variable.getProject()).isInSourceContent(use.getContainingFile().getVirtualFile())) return true;
                PsiElement resolved = use.resolve();
                if (resolved == null || !variable.getManager().areElementsEquivalent(variable, resolved)) return true;
                String occurrence = use.getContainingFile().getVirtualFile().getUrl() + ":" + use.getTextOffset();
                if (!seen.add(occurrence)) return true;
                if (seen.size() > MAX_REFERENCES) { result.partial = true; return false; }
                Access access = classify(use);
                String nodeKind = access.read && access.write ? "READ_WRITE" : access.write ? "WRITE" : "READ";
                CodeNode node = result.node(use, nodeKind, "[" + nodeKind + "] " + preview(access.expression),
                        nodeKind + ": " + snippet(access.expression) + ". This is an occurrence, not a reaching-value link."
                                + (access.write ? " Assignment/update is in this source location; branch execution is not proven."
                                : " Passing/returning the reference or reading it as a receiver does not prove object state is unchanged."));
                boolean accepted = true;
                if (access.write) accepted = result.graph.connect(root.id, node, Direction.CALLERS, "WRITES");
                if (accepted && access.read) accepted = result.graph.connect(root.id, node, Direction.CALLEES, "READS");
                if (!accepted) { result.partial = true; return false; }
                if (access.read && access.write) result.updates++;
                else if (access.write) result.writes++;
                else result.reads++;
                return true;
            }
        });
        return result;
    }

    private static Access classify(PsiReferenceExpression use) {
        PsiElement operand = use;
        while (operand.getParent() instanceof PsiParenthesizedExpression) operand = operand.getParent();
        PsiElement parent = operand.getParent();
        if (parent instanceof PsiAssignmentExpression && ((PsiAssignmentExpression) parent).getLExpression() == operand)
            return new Access(((PsiAssignmentExpression) parent).getOperationTokenType() != JavaTokenType.EQ, true, parent);
        if (parent instanceof PsiPostfixExpression) return new Access(true, true, parent);
        if (parent instanceof PsiPrefixExpression) {
            PsiPrefixExpression prefix = (PsiPrefixExpression) parent;
            if (prefix.getOperationTokenType() == JavaTokenType.PLUSPLUS || prefix.getOperationTokenType() == JavaTokenType.MINUSMINUS)
                return new Access(true, true, parent);
        }
        // In array[i] = value, array is read; it is the element that changes. Likewise obj.field = value reads obj.
        if (parent instanceof PsiExpressionList && parent.getParent() instanceof PsiCallExpression) parent = parent.getParent();
        return new Access(true, false, parent == null ? use : parent);
    }

    private static String snippet(PsiElement element) {
        String text = element.getText();
        return text.length() > 1000 ? text.substring(0, 1000) + "..." : text;
    }
    private static String preview(PsiElement element) {
        String text = snippet(element).replaceAll("\\s+", " ");
        return text.length() > 100 ? text.substring(0, 100) + "..." : text;
    }
    private static final class Access {
        final boolean read, write;
        final PsiElement expression;
        Access(boolean read, boolean write, PsiElement expression) { this.read = read; this.write = write; this.expression = expression; }
    }
}
