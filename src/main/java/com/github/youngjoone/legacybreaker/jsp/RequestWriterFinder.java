package com.github.youngjoone.legacybreaker.jsp;

import com.github.youngjoone.legacybreaker.model.CodeNode;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.*;
import java.util.regex.*;

/** Uses the IDE word index to visit only Java files containing the attribute key. */
public final class RequestWriterFinder {
    public void find(Project project, VirtualFile jsp, ValueTrace result) {
        Matcher words = Pattern.compile("[\\p{L}\\p{N}_$]+").matcher(result.attribute);
        String word = "";
        while (words.find()) if (words.group().length() > word.length()) word = words.group();
        if (word.isEmpty()) { result.notes.add("Attribute key has no indexable word; writer search skipped."); return; }
        long deadline = System.nanoTime() + 8_000_000_000L;
        Set<String> files = new HashSet<>();
        PsiSearchHelper.getInstance(project).processAllFilesWithWordInLiterals(word, GlobalSearchScope.projectScope(project), file -> {
            synchronized (result) {
                ProgressManager.checkCanceled();
                if (files.size() >= 100 || result.writers >= 30 || System.nanoTime() > deadline) { result.partial = true; return false; }
                if (!(file instanceof PsiJavaFile) || file.getVirtualFile() == null || !files.add(file.getVirtualFile().getUrl())) return true;
                file.accept(new JavaRecursiveElementWalkingVisitor() {
                    private int visits;
                    @Override public void visitElement(PsiElement element) {
                        ProgressManager.checkCanceled();
                        if (++visits > 50000 || System.nanoTime() > deadline || result.writers >= 30) {
                            result.partial = true; stopWalking(); return;
                        }
                        super.visitElement(element);
                    }
                    @Override public void visitMethodCallExpression(PsiMethodCallExpression call) {
                        PsiExpression[] args = call.getArgumentList().getExpressions();
                        if ("setAttribute".equals(call.getMethodExpression().getReferenceName()) && args.length == 2
                                && args[0] instanceof PsiLiteralExpression
                                && result.attribute.equals(((PsiLiteralExpression) args[0]).getValue())
                                && isRequest(call.getMethodExpression().getQualifierExpression()) && result.writers < 30) {
                            PsiMethod method = PsiTreeUtil.getParentOfType(call, PsiMethod.class);
                            if (method != null) addWriter(jsp, result, call, method, args[1]);
                        }
                        super.visitMethodCallExpression(call);
                    }
                });
                return !result.partial;
            }
        });
        result.notes.add(result.writers == 0
                ? "No supported Java request writer found. Model attributes, helper wrappers, includes or unresolved servlet types may need manual tracing."
                : "Dashed links are writer candidates, not proof that the same request rendered this JSP.");
    }

    private static boolean isRequest(PsiExpression receiver) {
        if (receiver == null || !(receiver.getType() instanceof PsiClassType)) return false;
        PsiClassType type = (PsiClassType) receiver.getType();
        String name = type.getCanonicalText();
        if (name.equals("javax.servlet.ServletRequest") || name.equals("javax.servlet.http.HttpServletRequest")
                || name.equals("jakarta.servlet.ServletRequest") || name.equals("jakarta.servlet.http.HttpServletRequest")) return true;
        PsiClass resolved = type.resolve();
        return resolved != null && (InheritanceUtil.isInheritor(resolved, "javax.servlet.ServletRequest")
                || InheritanceUtil.isInheritor(resolved, "jakarta.servlet.ServletRequest"));
    }

    private static void addWriter(VirtualFile jsp, ValueTrace result, PsiMethodCallExpression call,
                                  PsiMethod method, PsiExpression value) {
        String evidence = viewEvidence(jsp, method);
        if (evidence == null) return; // All recognized literal view names point elsewhere.
        VirtualFile file = call.getContainingFile().getVirtualFile();
        PsiClass owner = method.getContainingClass();
        String context = (owner == null ? "Java" : owner.getName()) + "." + method.getName() + "()";
        CodeNode writer = result.node(file, call.getTextOffset(), "ATTRIBUTE_WRITER", "setAttribute(\"" + result.attribute + "\", ...)",
                context, "CANDIDATE — " + evidence + " Branch execution, receiver identity and intervening writes are not proven.", call);
        if (!result.upstream(result.attributeNode, writer, "CANDIDATE_ATTRIBUTE")) return;
        result.writers++;
        traceJava(result, file, value, writer.id, new HashSet<>(), 0);
    }

    private static String viewEvidence(VirtualFile jsp, PsiMethod method) {
        if (method.getBody() == null) return "View destination unknown.";
        List<String> destinations = new ArrayList<>();
        method.getBody().accept(new JavaRecursiveElementWalkingVisitor() {
            @Override public void visitElement(PsiElement element) {
                ProgressManager.checkCanceled();
                super.visitElement(element);
            }
            @Override public void visitClass(PsiClass aClass) { }
            @Override public void visitLambdaExpression(PsiLambdaExpression expression) { }
            @Override public void visitReturnStatement(PsiReturnStatement statement) {
                PsiExpression value = statement.getReturnValue();
                if (value instanceof PsiLiteralExpression && ((PsiLiteralExpression) value).getValue() instanceof String)
                    destinations.add((String) ((PsiLiteralExpression) value).getValue());
                super.visitReturnStatement(statement);
            }
            @Override public void visitMethodCallExpression(PsiMethodCallExpression call) {
                if ("getRequestDispatcher".equals(call.getMethodExpression().getReferenceName())
                        && isRequest(call.getMethodExpression().getQualifierExpression())) {
                    PsiExpression[] args = call.getArgumentList().getExpressions();
                    if (args.length == 1 && args[0] instanceof PsiLiteralExpression && ((PsiLiteralExpression) args[0]).getValue() instanceof String)
                        destinations.add((String) ((PsiLiteralExpression) args[0]).getValue());
                }
                super.visitMethodCallExpression(call);
            }
        });
        if (destinations.isEmpty()) return "No literal view destination in this method; may be a helper or unrelated writer.";
        String jspPath = jsp.getPath().replace('\\', '/');
        for (String destination : destinations) {
            if (destination.startsWith("redirect:")) continue;
            if (destination.startsWith("forward:")) destination = destination.substring(8);
            if (destination.contains("?") || destination.contains("..") || destination.isEmpty()) continue;
            String suffix = destination.endsWith(".jsp") ? destination : destination + ".jsp";
            if (!suffix.startsWith("/")) suffix = "/" + suffix;
            if (jspPath.endsWith(suffix)) return "Literal view/path hint matches " + destination
                    + "; ViewResolver configuration and dispatch execution are not checked.";
        }
        return null;
    }

    private static void traceJava(ValueTrace result, VirtualFile file, PsiExpression raw, String downstream,
                                   Set<PsiElement> visited, int depth) {
        ProgressManager.checkCanceled();
        PsiExpression expression = LocalValue.unwrap(raw);
        if (expression == null || depth >= 10 || !visited.add(expression)) return;
        if (expression instanceof PsiReferenceExpression) {
            PsiElement variable = ((PsiReferenceExpression) expression).resolve();
            if (variable instanceof PsiLocalVariable) {
                PsiExpression origin = LocalValue.origin((PsiLocalVariable) variable, expression);
                CodeNode node = result.node(file, variable.getTextOffset(), "JAVA_VARIABLE", ((PsiLocalVariable) variable).getName(),
                        "Java local value", origin == null ? "Conditional/reassigned or unknown origin; stopped here." : "Direct initializer or straight-line assignment.", variable);
                if (result.upstream(downstream, node, "VALUE_FROM") && origin != null) traceJava(result, file, origin, node.id, visited, depth + 1);
                return;
            }
        }
        CodeNode value = result.node(file, expression.getTextOffset(), "JAVA_VALUE", expression.getText(), "Value passed to request",
                "Static expression, not an evaluated runtime value. Parameters, fields and compound expressions stop here.", expression);
        if (!result.upstream(downstream, value, "VALUE_FROM")) return;
        if (expression instanceof PsiMethodCallExpression) {
            PsiMethod target = ((PsiMethodCallExpression) expression).resolveMethod();
            if (target != null && target.getContainingFile() != null && target.getContainingFile().getVirtualFile() != null) {
                CodeNode method = result.node(target.getContainingFile().getVirtualFile(), target.getTextOffset(), "METHOD", target.getName() + "()",
                        "Method return source", "Statically resolved return source. Use Explore Method Calls for Java call relationships; return branches are not data-flow analyzed.", target);
                result.upstream(value.id, method, "RETURN_VALUE");
            }
        }
    }
}
