package com.github.youngjoone.legacybreaker.jsp;

import com.github.youngjoone.legacybreaker.model.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.*;

public final class ValueTrace {
    public static final class Location {
        public final VirtualFile file;
        public final int offset;
        public final SmartPsiElementPointer<PsiElement> pointer;
        public final SmartPsiElementPointer<PsiMethod> method;
        Location(VirtualFile file, int offset, PsiElement element) {
            this.file = file; this.offset = offset;
            SmartPointerManager manager = element == null ? null : SmartPointerManager.getInstance(element.getProject());
            pointer = manager == null ? null : manager.createSmartPsiElementPointer(element);
            PsiMethod owner = element == null ? null : element instanceof PsiMethod ? (PsiMethod) element : PsiTreeUtil.getParentOfType(element, PsiMethod.class);
            method = owner == null ? null : manager.createSmartPsiElementPointer(owner);
        }
    }
    public final CodeGraph graph = new CodeGraph();
    public final Map<String, Location> locations = new HashMap<>();
    public final List<String> notes = new ArrayList<>();
    public String attribute;
    public String attributeNode;
    public int writers;
    public boolean partial;

    public CodeNode node(VirtualFile file, int offset, String type, String name, String context,
                         String description, PsiElement element) {
        String id = file.getUrl() + ":" + offset + ":" + type;
        locations.put(id, new Location(file, offset, element));
        return new CodeNode(id, type, name, context, file.getUrl(), -1, false, description);
    }
    public boolean upstream(String downstream, CodeNode source, String relation) {
        boolean accepted = graph.connect(downstream, source, Direction.CALLERS, relation);
        if (!accepted) partial = true;
        return accepted;
    }
}
