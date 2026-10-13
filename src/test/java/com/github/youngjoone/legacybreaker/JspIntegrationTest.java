package com.github.youngjoone.legacybreaker;

import com.github.youngjoone.legacybreaker.jsp.*;
import com.github.youngjoone.legacybreaker.model.*;
import com.intellij.openapi.application.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.PsiDocumentManager;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.io.File;

public final class JspIntegrationTest {
    public static void fixtures(Path projectFolder) throws Exception {
        Path source = projectFolder.resolve("src");
        Path samples = Paths.get(System.getProperty("legacybreaker.test.sample")).getParent().resolve("jsp");
        Files.createDirectories(source.resolve("views"));
        Files.copy(samples.resolve("status.jsp"), source.resolve("views/status.jsp"), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(samples.resolve("StatusController.java"), source.resolve("StatusController.java"), StandardCopyOption.REPLACE_EXISTING);
        // Test-only Servlet API stubs: never packaged in the plugin or required at runtime.
        write(source, "javax/servlet/http/HttpServletRequest.java", "package javax.servlet.http; public interface HttpServletRequest { Object getAttribute(String key); void setAttribute(String key, Object value); Object getRequestDispatcher(String path); }");
        write(source, "javax/servlet/http/HttpSession.java", "package javax.servlet.http; public interface HttpSession { void setAttribute(String key, Object value); }");
        write(source, "OtherController.java", "import javax.servlet.http.*; class OtherController { String other(HttpServletRequest request) { request.setAttribute(\"userStatus\", \"OTHER\"); return \"other\"; } void session(HttpSession session) { session.setAttribute(\"userStatus\", \"SESSION\"); } }");
        write(source, "UnknownWriter.java", "import javax.servlet.http.*; class UnknownWriter { void helper(HttpServletRequest request) { request.setAttribute(\"userStatus\", \"UNKNOWN\"); } }");
        write(source, "BagWriter.java", "class BagWriter { void setAttribute(String key, Object value) {} void write() { setAttribute(\"userStatus\", \"BAG\"); } }");
    }
    private static void write(Path root, String file, String text) throws Exception {
        Path path = root.resolve(file); Files.createDirectories(path.getParent()); Files.write(path, text.getBytes(StandardCharsets.UTF_8));
    }

    public static void run(Project project) throws Exception {
        // Test PSI analysis/navigation, without unrelated background editor inspections.
        // The 2018 SDK's template-header inspection invokes an obsolete macOS JNA framework loader.
        edt(() -> { com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.getInstance(project)
                .setUpdateByTimerEnabled(false); return null; });
        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(System.getProperty("legacybreaker.test.project") + "/src/views/status.jsp");
        check(file != null, "JSP file in VFS");
        Document document = edt(() -> FileDocumentManager.getInstance().getDocument(file));
        check(document != null, "Community SDK can read JSP without an Ultimate JSP plugin");
        String text = document.getText();
        ProgressManager.getInstance().runProcess(() -> ApplicationManager.getApplication().runReadAction(() -> {
            ValueTrace trace = new JspValueAnalyzer().analyze(project, file, text, text.indexOf("var status") + 5);
            check("userStatus".equals(trace.attribute), "Extract literal request attribute");
            check(trace.writers == 2, "Matching and unknown-view writer only: " + trace.writers);
            check(trace.graph.nodes().stream().anyMatch(n -> n.type.equals("METHOD") && n.name.equals("loadStatus()")), "Trace Java initializer into resolved method");
            check(trace.graph.nodes().stream().noneMatch(n -> n.className.startsWith("OtherController") || n.className.startsWith("BagWriter")), "Reject unrelated view and wrong receiver type");
            check(trace.graph.edges().stream().filter(e -> e.type.equals("CANDIDATE_ATTRIBUTE")).count() == 2, "Candidate links explicit");
            check(trace.graph.nodes().stream().filter(n -> n.type.equals("ATTRIBUTE_WRITER")).allMatch(n -> n.description.startsWith("CANDIDATE")), "No false runtime proof");
            check(trace.graph.node(trace.graph.rootId()).type.equals("JS_VARIABLE"), "JS declaration is the root");
            assertNoAttribute(project, file, "<% String v = (String) session.getAttribute(\"userStatus\"); %><%= v %>", "session excluded");
            assertNoAttribute(project, file, "<% String k=\"userStatus\"; String v=(String)request.getAttribute(k); %><%= v %>", "dynamic key excluded");
            assertNoAttribute(project, file, "<% String v=(String)request.getAttribute(\"userStatus\"); if (true) v=\"changed\"; %><%= v %>", "conditional reassignment stops");
            assertNoAttribute(project, file, "<% String v=(String)request.getAttribute(\"userStatus\"); for(int i=0;i<2;i++){ %><%= v %><% v=\"next\"; } %>", "loop reaching values stop");
            assertNoAttribute(project, file, "<% String v=(String)request.getAttribute(\"userStatus\"); (v)=\"changed\"; %><%= v %>", "parenthesized assignment stops");
            String direct = "<% String v; v=(String)request.getAttribute(\"userStatus\"); %><%= v %>";
            check("userStatus".equals(new JspValueAnalyzer().analyze(project, file, direct, direct.indexOf("<%=") + 3).attribute), "Straight-line assignment supported");
            String shadow = "<% String v=(String)request.getAttribute(\"userStatus\"); { String x=\"literal\"; %><%= x %><% } %>";
            assertNoAttribute(project, file, shadow, "Unrelated local literal does not match outer attribute");
        }), new EmptyProgressIndicator());

        check(edt(() -> com.intellij.openapi.actionSystem.ActionManager.getInstance()
                .getAction("LegacyBreaker.TraceJspValue")) != null, "JSP action registered from descriptor");
        JspTraceService service = edt(() -> JspTraceService.getInstance(project));
        edt(() -> { service.start(file, text.indexOf("var status") + 5); return null; });
        idle(service);
        edt(() -> {
            check(service.graph().nodes().stream().anyMatch(n -> n.type.equals("ATTRIBUTE_WRITER")), "Asynchronous trace published: " + service.status());
            JspTracePanel panel = new JspTracePanel(service);
            panel.setSize(1400, 600); layout(panel);
            showRoot(panel);
            BufferedImage image = new BufferedImage(1400, 600, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics(); panel.paint(graphics); graphics.dispose();
            javax.imageio.ImageIO.write(image, "png", new File(System.getProperty("legacybreaker.test.jsp.preview")));
            panel.dispose();
            int count = service.graph().nodes().size();
            service.select(service.graph().nodes().stream().filter(n -> n.type.equals("ATTRIBUTE_WRITER")).findFirst().get().id);
            service.navigate();
            com.intellij.openapi.editor.Editor javaEditor = FileEditorManager.getInstance(project).getSelectedTextEditor();
            check(javaEditor != null && FileDocumentManager.getInstance().getFile(javaEditor.getDocument()).getName().endsWith(".java"),
                    "Java writer source opened");
            check(service.canExploreMethod(), "Can continue into Java call graph");
            service.exploreMethod();
            check(service.graph().nodes().size() == count, "Java exploration keeps JSP trace");
            service.select(service.graph().rootId());
            WriteCommandAction.runWriteCommandAction(project, () -> document.insertString(0, "\n"));
            service.navigate();
            check(FileEditorManager.getInstance(project).getSelectedTextEditor().getCaretModel().getOffset()
                    == text.indexOf("status =") + 1, "JSP marker follows inserted text");
            return null;
        });
        edt(() -> null);
        edt(() -> { check(service.isStale(), "JSP edit invalidates trace"); service.refresh(); return null; });
        idle(service);
        edt(() -> {
            check(!service.isStale() && !service.graph().nodes().isEmpty(), "Refresh after edit");
            service.start(file, 0); service.cancel();
            check(!service.isLoading(), "Cancel clears pending trace");
            service.start(file, document.getText().indexOf("<%=") + 3);
            return null;
        });
        idle(service);
        edt(() -> { check(!service.graph().nodes().isEmpty(), "Old cancelled trace cannot overwrite new selection"); return null; });
        System.out.println("PASS: JSP/JS origins, request writer candidates, negative cases, navigation, refresh and cancellation");
    }
    private static void assertNoAttribute(Project project, VirtualFile file, String text, String message) {
        ValueTrace result = new JspValueAnalyzer().analyze(project, file, text, text.indexOf("<%=") + 3);
        check(result.attribute == null && result.writers == 0, message);
    }
    private static void idle(JspTraceService service) throws Exception {
        long deadline = System.currentTimeMillis() + 30000;
        while (edt(service::isLoading)) {
            if (System.currentTimeMillis() > deadline) throw new AssertionError("JSP search timed out");
            Thread.sleep(50);
        }
    }
    private static <T> T edt(Callable<T> callable) throws Exception {
        FutureTask<T> task = new FutureTask<>(callable);
        ApplicationManager.getApplication().invokeAndWait(task, ModalityState.NON_MODAL); return task.get();
    }
    private static void showRoot(java.awt.Container container) {
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof javax.swing.JButton && ((javax.swing.JButton) child).getText().equals("Show Root"))
                ((javax.swing.JButton) child).doClick();
            else if (child instanceof java.awt.Container) showRoot((java.awt.Container) child);
        }
    }
    private static void layout(java.awt.Container container) {
        container.doLayout();
        for (java.awt.Component child : container.getComponents()) if (child instanceof java.awt.Container) layout((java.awt.Container) child);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
