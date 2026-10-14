//4차수정: 신규 파일 전체 — 변수 탐색 결과와 소스 위치를 그래프 화면과 분리한다.
package com.github.youngjoone.legacybreaker.variable;

import com.github.youngjoone.legacybreaker.model.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.*;

public final class VariableAnalysis {
    public final CodeGraph graph = new CodeGraph();
    public final Map<String, SmartPsiElementPointer<PsiElement>> pointers = new HashMap<>();
    public SmartPsiElementPointer<PsiVariable> variable;
    public int reads, writes, updates, initializers;
    public boolean partial;

    public CodeNode node(PsiElement element, String kind, String label, String description) {
        PsiFile file = element.getContainingFile();
        VirtualFile virtualFile = file.getVirtualFile();
        int offset = element.getTextOffset();
        Document document = PsiDocumentManager.getInstance(element.getProject()).getDocument(file);
        int line = document == null ? -1 : document.getLineNumber(offset) + 1;
        String id = virtualFile.getUrl() + ":" + offset + ":" + kind;
        pointers.put(id, SmartPointerManager.getInstance(element.getProject()).createSmartPsiElementPointer(element));
        PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
        String context = file.getName() + (line > 0 ? ":" + line : "") + (method == null ? "" : " | " + method.getName() + "()");
        return new CodeNode(id, kind, label, context, virtualFile.getUrl(), line, false, description);
    }

    public String summary() {
        return "Read-only: " + reads + "; write-only: " + writes + "; read + write: " + updates
                + "; initializer: " + initializers + (partial ? ". PARTIAL: search/graph limit reached." : ".")
                + " Static source locations; no execution order or runtime values are inferred.";
    }
}
