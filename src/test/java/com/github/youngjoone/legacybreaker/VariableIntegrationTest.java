//4차수정: 신규 파일 전체 — 실제 IDEA 2018.3의 변수 PSI/인덱스와 탐색 세션 회귀 검증.
package com.github.youngjoone.legacybreaker;

import com.github.youngjoone.legacybreaker.variable.*;
import com.github.youngjoone.legacybreaker.model.*;
import com.intellij.openapi.application.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;

public final class VariableIntegrationTest {
    public static void fixtures(Path folder) throws Exception {
        Path source = folder.resolve("src");
        Files.copy(Paths.get(System.getProperty("legacybreaker.test.sample")).getParent().resolve("VariableExample.java"),
                source.resolve("VariableExample.java"), StandardCopyOption.REPLACE_EXISTING);
        Files.write(source.resolve("VariableExternal.java"), "class VariableExternal { void touch(VariableExample other) { other.field++; } }".getBytes(StandardCharsets.UTF_8));
        StringBuilder large = new StringBuilder("class VariableLarge { void many() { int large=0;");
        for (int i = 0; i < VariableAnalyzer.MAX_REFERENCES + 1; i++) large.append("large++;");
        large.append("} }");
        Files.write(source.resolve("VariableLarge.java"), large.toString().getBytes(StandardCharsets.UTF_8));
    }
    public static void run(Project project) throws Exception {
        VirtualFile virtual = LocalFileSystem.getInstance().findFileByPath(System.getProperty("legacybreaker.test.project") + "/src/VariableExample.java");
        PsiJavaFile file = (PsiJavaFile) edt(() -> PsiManager.getInstance(project).findFile(virtual));
        Document document = edt(() -> PsiDocumentManager.getInstance(project).getDocument(file));
        String original = document.getText();
        ProgressManager.getInstance().runProcess(() -> ApplicationManager.getApplication().runReadAction(() -> {
            PsiVariable amount = at(file, "amount = input");
            check(VariableAtCaret.find(file, original.indexOf("consume(amount)") + 8) == amount, "Usage resolves to local declaration");
            check(VariableAtCaret.find(file, original.indexOf("amount = input") + "amount".length()) == amount, "Caret at name end supported");
            check(VariableAtCaret.find(file, original.indexOf("void run")) == null, "Keyword is not a variable");
            check(VariableAtCaret.find(file, original.indexOf("consume(amount)")) == null, "Method name is not a variable");
            check(VariableAtCaret.find(file, original.indexOf("amount > 0") + "amount ".length()) == null, "Operator does not choose nearby variable");
            counts(new VariableAnalyzer().analyze(amount), 5, 2, 3, 1, "local reads and writes");
            VariableAnalysis local = new VariableAnalyzer().analyze(amount);
            check(local.graph.nodes().stream().filter(n -> !n.type.equals("VARIABLE") && !n.type.equals("INITIALIZER"))
                    .allMatch(n -> PsiTreeUtil.getParentOfType(local.pointers.get(n.id).getElement(), PsiMethod.class).getName().equals("run")),
                    "Same-name variable in another method excluded");
            counts(new VariableAnalyzer().analyze(at(file, "input)")), 2, 1, 0, 0, "parameter reads and reassignment");
            VariableAnalysis field = new VariableAnalyzer().analyze(at(file, "field = 1"));
            counts(field, 1, 1, 1, 1, "field includes external file and excludes shadow parameter");
            check(field.graph.nodes().stream().anyMatch(n -> n.filePath.endsWith("VariableExternal.java")), "Field search crosses Java files");
            check(field.graph.node(field.graph.rootId()).description.contains("not necessarily the same object"), "Field identity limitation visible");
            counts(new VariableAnalyzer().analyze(at(file, "values = {")), 3, 1, 0, 1, "Array element writes only read array variable");
            counts(new VariableAnalyzer().analyze(at(file, "item :")), 1, 0, 0, 0, "Foreach implicit input is not an explicit initializer");
            counts(new VariableAnalyzer().analyze(at(file, "flag = true")), 1, 1, 0, 1, "Logical negation is a read");
            counts(new VariableAnalyzer().analyze(at(file, "captured = 1")), 1, 0, 0, 1, "Captured lambda reference included");
            counts(new VariableAnalyzer().analyze(at(file, "inner = 1")), 1, 1, 0, 1, "Lambda reads and writes are listed without guessing reaching values");
            PsiJavaFile big = (PsiJavaFile) PsiManager.getInstance(project).findFile(virtual.getParent().findChild("VariableLarge.java"));
            VariableAnalysis bounded = new VariableAnalyzer().analyze(at(big, "large=0"));
            check(bounded.updates == VariableAnalyzer.MAX_REFERENCES && bounded.partial, "Reference limit reports partial");
            check(local.graph.nodes().stream().anyMatch(n -> n.type.equals("READ") && n.name.contains("consume(amount)")), "Argument use shows consuming call name");
            check(local.graph.edges().stream().filter(e -> e.type.equals("READS")).count() == 8, "Read edges include compound updates");
            check(local.graph.edges().stream().filter(e -> e.type.equals("WRITES")).count() == 5, "Write edges include compound updates");
            check(local.graph.edges().stream().filter(e -> e.type.equals("INITIALIZES")).count() == 1, "Explicit initializer edge");
        }), new EmptyProgressIndicator());

        VariableSession session = edt(() -> VariableSession.getInstance(project));
        edt(() -> {
            check(com.intellij.openapi.actionSystem.ActionManager.getInstance().getAction("LegacyBreaker.ExploreVariableRelations") != null, "Variable editor action registered");
            session.start(virtual, original.indexOf("consume(amount)") + 8); return null;
        });
        idle(session);
        edt(() -> {
            check(session.graph().nodes().size() == 12 && !session.isStale(), "Async analysis published: " + session.status());
            session.setFilter(VariableSession.Filter.READS);
            check(session.graph().nodes().size() == 9, "Read filter includes read-write sites");
            session.setFilter(VariableSession.Filter.CHANGES);
            check(session.graph().nodes().size() == 7, "Changes filter includes initializer and read-write sites");
            session.setFilter(VariableSession.Filter.ALL);
            VariablePanel panel = new VariablePanel(session); panel.setSize(1250, 680); layout(panel); showRoot(panel);
            BufferedImage preview = new BufferedImage(1250, 680, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = preview.createGraphics(); panel.paint(graphics); graphics.dispose();
            javax.imageio.ImageIO.write(preview, "png", Paths.get(System.getProperty("legacybreaker.test.preview")).resolveSibling("variable-preview.png").toFile());
            panel.dispose();
            VariablePanel restored = new VariablePanel(session);
            check(session.graph().nodes().size() == 12, "Closing/reopening UI preserves graph"); restored.dispose();
            CodeNode write = session.graph().nodes().stream().filter(n -> n.type.equals("WRITE") && n.name.contains("amount = 2")).findFirst().get();
            session.select(write.id); session.navigate();
            check(FileEditorManager.getInstance(project).getSelectedTextEditor().getCaretModel().getOffset() == original.indexOf("amount = 2"), "Navigation goes to use rather than declaration");
            WriteCommandAction.runWriteCommandAction(project, () -> document.insertString(0, "// moved\n"));
            session.navigate();
            check(FileEditorManager.getInstance(project).getSelectedTextEditor().getCaretModel().getOffset() == original.indexOf("amount = 2") + 9, "Smart pointer follows edited occurrence");
            return null;
        });
        edt(() -> null);
        edt(() -> { check(session.isStale(), "Edits invalidate variable result"); session.refresh(); return null; });
        idle(session);
        edt(() -> {
            check(!session.isStale() && session.graph().nodes().size() == 12, "Refresh resolves declaration after edit");
            session.start(virtual, 0); session.cancel();
            check(!session.isLoading(), "Cancel clears pending state");
            session.start(virtual, document.getText().indexOf("field = 1")); return null;
        });
        idle(session);
        edt(() -> {
            check(session.graph().node(session.graph().rootId()).name.equals("field"), "Cancelled result cannot replace newer root");
            PsiVariable removed = at(file, "field = 1");
            WriteCommandAction.runWriteCommandAction(project, removed::delete);
            session.refresh();
            check(session.status().startsWith("Variable was removed"), "Deleted declaration refresh handled");
            session.start(virtual, document.getText().indexOf("void run")); return null;
        });
        idle(session);
        edt(() -> { check(session.graph().nodes().isEmpty() && session.status().startsWith("Place the caret"), "Unsupported caret gets actionable message"); return null; });
        System.out.println("PASS: variable identity, read/write classification, bounds, filters, navigation, refresh and cancellation");
    }
    private static PsiVariable at(PsiFile file, String marker) {
        int offset = file.getText().indexOf(marker);
        check(offset >= 0, "Fixture marker: " + marker);
        PsiVariable variable = VariableAtCaret.find(file, offset);
        check(variable != null, "Variable at " + marker); return variable;
    }
    private static void counts(VariableAnalysis result, int reads, int writes, int updates, int initializers, String message) {
        check(result.reads == reads && result.writes == writes && result.updates == updates && result.initializers == initializers && !result.partial,
                message + ": " + result.summary());
    }
    private static void idle(VariableSession session) throws Exception {
        long end = System.currentTimeMillis() + 30000;
        while (edt(session::isLoading)) { if (System.currentTimeMillis() > end) throw new AssertionError("Variable search timed out"); Thread.sleep(50); }
    }
    private static <T> T edt(Callable<T> callable) throws Exception {
        FutureTask<T> task = new FutureTask<>(callable);
        ApplicationManager.getApplication().invokeAndWait(task, ModalityState.NON_MODAL); return task.get();
    }
    private static void layout(java.awt.Container container) {
        container.doLayout();
        for (java.awt.Component child : container.getComponents()) if (child instanceof java.awt.Container) layout((java.awt.Container) child);
    }
    private static void showRoot(java.awt.Container container) {
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof javax.swing.JButton && ((javax.swing.JButton) child).getText().equals("Show Root")) ((javax.swing.JButton) child).doClick();
            else if (child instanceof java.awt.Container) showRoot((java.awt.Container) child);
        }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
