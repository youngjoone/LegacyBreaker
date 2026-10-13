package com.github.youngjoone.legacybreaker.analysis;

import com.github.youngjoone.legacybreaker.model.CodeNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;

public final class MethodNodeMapper {
    private MethodNodeMapper() { }

    public static CodeNode map(PsiMethod method) {
        PsiFile file = method.getContainingFile();
        VirtualFile virtualFile = file == null ? null : file.getVirtualFile();
        String path = virtualFile == null ? "<no source>" : virtualFile.getUrl();
        PsiClass owner = method.getContainingClass();
        String className = owner == null ? null : owner.getQualifiedName();
        if (className == null) {
            className = (owner != null && owner.getName() != null ? owner.getName() : "<anonymous>")
                    + "@" + (owner == null ? method.getTextOffset() : owner.getTextOffset());
        }
        StringBuilder signature = new StringBuilder(method.getName()).append('(');
        PsiParameter[] parameters = method.getParameterList().getParameters();
        for (int i = 0; i < parameters.length; i++) {
            if (i > 0) signature.append(", ");
            signature.append(parameters[i].getType().getCanonicalText());
        }
        signature.append(')');
        Document document = file == null ? null : PsiDocumentManager.getInstance(method.getProject()).getDocument(file);
        int line = document == null ? -1 : document.getLineNumber(
                Math.min(method.getTextOffset(), document.getTextLength())) + 1;
        boolean external = virtualFile == null || !ProjectFileIndex.getInstance(method.getProject()).isInSourceContent(virtualFile);
        return new CodeNode(path + "#" + className + "#" + signature, "METHOD", signature.toString(),
                className, path, line, external);
    }

    /** Executable method owner, excluding deferred lambda bodies and class initializers. */
    public static PsiMethod enclosingMethod(PsiElement element) {
        for (PsiElement current = element; current != null; current = current.getParent()) {
            if (current instanceof PsiLambdaExpression || current instanceof PsiClass) return null;
            if (current instanceof PsiMethod) {
                PsiMethod method = (PsiMethod) current;
                return method.isConstructor() ? null : method;
            }
        }
        return null;
    }
}
