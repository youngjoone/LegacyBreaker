package com.github.youngjoone.legacybreaker.jsp;

import com.github.youngjoone.legacybreaker.model.CodeNode;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.*;

public final class JspValueAnalyzer {
    public ValueTrace analyze(Project project, VirtualFile file, String text, int caret) {
        JspSource source = new JspSource(text, com.intellij.openapi.progress.ProgressManager::checkCanceled);
        JspSource.Part output = source.outputAt(caret);
        PsiJavaFile parsed = (PsiJavaFile) PsiFileFactory.getInstance(project).createFileFromText(
                "__JspTrace.java", JavaFileType.INSTANCE, source.javaSource);
        if (PsiTreeUtil.findChildOfType(parsed, PsiErrorElement.class) != null)
            throw new IllegalArgumentException("Scriptlets could not be parsed as Java. Includes, declarations or incomplete code may need manual tracing.");
        PsiExpression value = null;
        for (PsiMethodCallExpression call : PsiTreeUtil.findChildrenOfType(parsed, PsiMethodCallExpression.class)) {
            if (!"__output".equals(call.getMethodExpression().getReferenceName())) continue;
            PsiExpression[] args = call.getArgumentList().getExpressions();
            if (args.length == 1 && args[0].getTextOffset() >= source.javaOffset(output)
                    && args[0].getTextOffset() < source.javaOffset(output) + output.end - 2 - output.bodyStart) {
                value = args[0]; break;
            }
        }
        if (value == null) throw new IllegalArgumentException("No Java value expression at the selected JSP output.");
        ValueTrace result = new ValueTrace();
        String jsName = source.jsName(output);
        CodeNode print = result.node(file, output.start, "JSP_OUTPUT", "<%= " + value.getText() + " %>", file.getName(),
                "Server-side JSP output expression. This shows static source, not an observed runtime value.", null);
        if (jsName != null) {
            CodeNode js = result.node(file, source.jsOffset(output), "JS_VARIABLE", jsName, "JavaScript declaration",
                    "Direct initializer from a JSP output. Subsequent JavaScript mutations are not analyzed.", null);
            result.graph.reset(js);
            result.upstream(js.id, print, "OUTPUTS");
        } else result.graph.reset(print);
        traceLocal(result, file, source, value, print.id, new HashSet<>(), 0);
        if (source.includes) result.notes.add("JSP includes are not followed; this is a partial local-file trace.");
        if (result.attribute != null) new RequestWriterFinder().find(project, file, result);
        return result;
    }

    private void traceLocal(ValueTrace result, VirtualFile file, JspSource source, PsiExpression expression,
                            String downstream, Set<PsiElement> visited, int depth) {
        com.intellij.openapi.progress.ProgressManager.checkCanceled();
        expression = LocalValue.unwrap(expression);
        if (expression == null) return;
        if (depth >= 12 || !visited.add(expression)) { result.notes.add("Local value trace depth/cycle limit reached."); return; }
        int offset = source.sourceOffset(expression.getTextOffset());
        if (offset < 0) { result.notes.add("Value is outside the local JSP scriptlets."); return; }
        if (expression instanceof PsiReferenceExpression) {
            PsiElement resolved = ((PsiReferenceExpression) expression).resolve();
            if (resolved instanceof PsiLocalVariable) {
                PsiLocalVariable variable = (PsiLocalVariable) resolved;
                int declaration = source.sourceOffset(variable.getTextOffset());
                if (declaration < 0) return;
                PsiExpression origin = LocalValue.origin(variable, expression);
                //4차수정: 루프/람다 경계에서 중단되는 경우도 노드 설명에 표시한다.
                CodeNode node = result.node(file, declaration, "JSP_VARIABLE", variable.getName(), "JSP Java variable",
                        origin == null ? "Reassigned, conditional, loop/lambda or missing initializer: reaching value is not determined."
                                : "Direct local value source; casts and parentheses are unwrapped.", null);
                result.upstream(downstream, node, "ASSIGNS");
                if (origin != null) traceLocal(result, file, source, origin, node.id, visited, depth + 1);
                else result.notes.add("Stopped at ambiguous local variable " + variable.getName() + ".");
                return;
            }
        }
        if (expression instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression call = (PsiMethodCallExpression) expression;
            PsiExpression qualifier = LocalValue.unwrap(call.getMethodExpression().getQualifierExpression());
            PsiExpression[] args = call.getArgumentList().getExpressions();
            PsiElement receiver = qualifier instanceof PsiReferenceExpression ? ((PsiReferenceExpression) qualifier).resolve() : null;
            if ("getAttribute".equals(call.getMethodExpression().getReferenceName())
                    && receiver instanceof PsiParameter && "request".equals(((PsiParameter) receiver).getName())
                    && args.length == 1 && args[0] instanceof PsiLiteralExpression
                    && ((PsiLiteralExpression) args[0]).getValue() instanceof String) {
                result.attribute = (String) ((PsiLiteralExpression) args[0]).getValue();
                CodeNode attribute = result.node(file, offset, "REQUEST_ATTRIBUTE", "request[\"" + result.attribute + "\"]",
                        "Request attribute read", "Java writers are candidates. Request identity, dispatch paths and overwrites are not proven.", null);
                result.upstream(downstream, attribute, "READS_ATTRIBUTE");
                result.attributeNode = attribute.id;
                return;
            }
        }
        CodeNode stop = result.node(file, offset, "VALUE_EXPRESSION", expression.getText(), "JSP expression",
                expression instanceof PsiLiteralExpression ? "Literal value in this JSP."
                        : "Boundary: only direct locals and request.getAttribute with a literal string key are supported. Session, parameters, fields and dynamic keys are not treated as request attributes.", null);
        result.upstream(downstream, stop, "VALUE_FROM");
        if (!(expression instanceof PsiLiteralExpression)) result.notes.add("Stopped at unsupported JSP expression; no request-writer guess was made.");
    }
}
