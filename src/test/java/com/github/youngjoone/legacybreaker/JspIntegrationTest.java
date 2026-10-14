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
        //4차수정: 재대입, 람다, 일반 문자열 반환 및 목적지 힌트의 회귀 검증용 소스.
        write(source, "RevisionFourWriters.java", "import javax.servlet.http.*; class RevisionFourWriters { "
                + "void duplicate(HttpServletRequest request) { String value=\"FIRST\"; request.setAttribute(\"mergeKey\",value); value=\"SECOND\"; request.setAttribute(\"mergeKey\",value); } "
                + "void lambda(HttpServletRequest request) { Runnable r=()->{ String value=\"FIRST\"; value=\"SECOND\"; request.setAttribute(\"lambdaKey\",value); }; } "
                + "String helper(HttpServletRequest request) { request.setAttribute(\"helperKey\",\"DATA\"); return \"OK\"; } "
                + "String mixed(HttpServletRequest request, boolean b, String view) { request.setAttribute(\"mixedKey\",\"DATA\"); if(b) return \"other\"; return view; } "
                + "void dispatch(HttpServletRequest request) { request.setAttribute(\"dispatchKey\",\"DATA\"); request.getRequestDispatcher(\"/other.jsp\"); } }");
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
            //4차수정: 화면 힌트가 일치하지 않는 작성자도 미확인 후보로 유지한다.
            check(trace.writers == 3, "Matching, unknown and unmatched-view writers: " + trace.writers);
            check(trace.graph.nodes().stream().anyMatch(n -> n.type.equals("METHOD") && n.name.equals("loadStatus()")), "Trace Java initializer into resolved method");
            //4차수정: 화면명 힌트와 수신 타입 검증을 구분하고, 실제로 제외해야 하는 session/일반 객체를 계속 검사한다.
            check(trace.graph.nodes().stream().noneMatch(n -> n.className.startsWith("OtherController.session") || n.className.startsWith("BagWriter")), "Reject wrong receiver type");
            check(trace.graph.nodes().stream().anyMatch(n -> n.type.equals("ATTRIBUTE_WRITER") && n.className.equals("OtherController.other()")
                    && n.description.contains("destination unconfirmed")), "Unmatched hint is explicitly unconfirmed");
            check(trace.graph.nodes().stream().anyMatch(n -> n.type.equals("ATTRIBUTE_WRITER") && n.description.contains("hint matches status")), "Matching hint retained");
            check(trace.graph.edges().stream().filter(e -> e.type.equals("CANDIDATE_ATTRIBUTE")).count() == 3, "Candidate links explicit");
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
            //4차수정: 실제 IDEA PSI/인덱스에서 세 가지 오류의 회귀 검증을 실행한다.
            revisionFourTests(project, file);
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
    //4차수정: 메서드 전체 추가. 재대입 시점별 경로와 소스 위치, 람다 경계, 후보 누락을 검증한다.
    private static void revisionFourTests(Project project, VirtualFile file) {
        ValueTrace merged = attributeTrace(project, file, "mergeKey");
        check(merged.writers == 2, "Both assignments found");
        CodeNode[] writers = merged.graph.nodes().stream().filter(n -> n.type.equals("ATTRIBUTE_WRITER"))
                .sorted((a, b) -> Integer.compare(merged.locations.get(a.id).offset, merged.locations.get(b.id).offset))
                .toArray(CodeNode[]::new);
        CodeNode first = sourceOf(merged, writers[0]);
        CodeNode second = sourceOf(merged, writers[1]);
        check(first.type.equals("JAVA_VARIABLE") && second.type.equals("JAVA_VARIABLE") && !first.id.equals(second.id),
                "Separate local nodes for distinct uses of one declaration");
        check(sourceOf(merged, first).name.equals("\"FIRST\""), "First use keeps original literal");
        check(merged.graph.edges().stream().noneMatch(e -> e.target.equals(second.id)), "Reassigned use has no false initial-value edge");
        check(second.description.contains("stopped here"), "Ambiguous second use is explained");
        com.intellij.psi.PsiElement source = merged.locations.get(second.id).pointer.getElement();
        check(source instanceof com.intellij.psi.PsiReferenceExpression && source.getTextOffset() == merged.locations.get(second.id).offset,
                "Variable source pointer targets the specific use");
        check(source.getParent().getParent().getTextOffset() == merged.locations.get(writers[1].id).offset,
                "Second variable use belongs to second writer, not declaration or first writer");

        String lambda = "<% Runnable r=()->{ String value=(String)request.getAttribute(\"lambdaKey\"); value=\"CHANGED\"; %><%= value %><% }; %>";
        assertNoAttribute(project, file, lambda, "JSP lambda reassignment cannot resolve to original request read");
        String nested = "<% Runnable r=()->{ Runnable inner=()->{ String value=(String)request.getAttribute(\"lambdaKey\"); value=\"CHANGED\"; %><%= value %><% }; }; %>";
        assertNoAttribute(project, file, nested, "Nested JSP lambda also stops");
        ValueTrace lambdaWriter = attributeTrace(project, file, "lambdaKey");
        check(lambdaWriter.writers == 1, "Writer in lambda is still a candidate");
        CodeNode boundary = lambdaWriter.graph.nodes().stream().filter(n -> n.type.equals("JAVA_VARIABLE")).findFirst().get();
        check(boundary.description.contains("loop/lambda") && lambdaWriter.graph.edges().stream().noneMatch(e -> e.target.equals(boundary.id)),
                "Java lambda local stops without a false FIRST source");

        for (String key : new String[]{"helperKey", "mixedKey", "dispatchKey"}) {
            ValueTrace candidate = attributeTrace(project, file, key);
            check(candidate.writers == 1, "Do not discard unconfirmed destination: " + key);
            check(candidate.graph.nodes().stream().anyMatch(n -> n.type.equals("ATTRIBUTE_WRITER")
                    && n.description.contains("destination unconfirmed")), "Explicit unconfirmed label: " + key);
        }
        System.out.println("PASS: revision 4 separate variable uses, lambda boundaries and unconfirmed writer retention");
    }
    //4차수정: 회귀 검증용 request 출력식 생성 헬퍼 추가.
    private static ValueTrace attributeTrace(Project project, VirtualFile file, String key) {
        return new JspValueAnalyzer().analyze(project, file, "<%= request.getAttribute(\"" + key + "\") %>", 4);
    }
    //4차수정: 특정 사용처의 유일한 직접 출처를 검사하는 헬퍼 추가.
    private static CodeNode sourceOf(ValueTrace trace, CodeNode consumer) {
        CodeEdge[] incoming = trace.graph.edges().stream().filter(e -> e.target.equals(consumer.id)).toArray(CodeEdge[]::new);
        check(incoming.length == 1, "Exactly one direct source for " + consumer.name);
        return trace.graph.node(incoming[0].source);
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
