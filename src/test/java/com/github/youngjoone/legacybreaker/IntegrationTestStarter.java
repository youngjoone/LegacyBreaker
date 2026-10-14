package com.github.youngjoone.legacybreaker;

import com.github.youngjoone.legacybreaker.analysis.*;
import com.github.youngjoone.legacybreaker.model.*;
import com.github.youngjoone.legacybreaker.session.ExplorationSessionService;
import com.github.youngjoone.legacybreaker.ui.CodeRelationsPanel;
import com.intellij.openapi.application.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.*;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.*;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.actionSystem.ActionManager;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;

import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

/** Runs against the real 2018.3 platform and indexes, in a separate headless sandbox. */
public final class IntegrationTestStarter extends ApplicationStarterEx {
    private Project project;
    private PsiJavaFile file;
    private PsiMethod root;

    @Override public String getCommandName() { return "legacybreaker-test"; }
    @Override public boolean isHeadless() { return true; }
    @Override public void premain(String[] args) { }
    @Override public void main(String[] args) {
        Thread watchdog = new Thread(() -> {
            try { Thread.sleep(180000); } catch (InterruptedException ignored) { return; }
            System.err.println("FAIL: integration suite exceeded three minutes");
            System.exit(1);
        }, "legacybreaker-test-timeout");
        watchdog.setDaemon(true);
        watchdog.start();
        new Thread(() -> {
            try {
                setup();
                DumbService.getInstance(project).waitForSmartMode();
                ProgressManager.getInstance().runProcess(
                        () -> ApplicationManager.getApplication().runReadAction(this::analysisTests),
                        new EmptyProgressIndicator());
                JspIntegrationTest.run(project);
                //4차수정: 변수 탐색 기능을 같은 실제 IDE 테스트 프로젝트에서 검증한다.
                VariableIntegrationTest.run(project);
                sessionTests();
                System.out.println("PASS: real PSI, reference index, lazy expansion, navigation, stale refresh and cancellation");
                System.exit(0);
            } catch (Throwable failure) {
                failure.printStackTrace();
                System.exit(1);
            }
        }, "legacybreaker-integration-test").start();
    }

    private void setup() throws Exception {
        Path folder = Paths.get(System.getProperty("legacybreaker.test.project")).toAbsolutePath();
        Files.createDirectories(folder.resolve("src"));
        Files.copy(Paths.get(System.getProperty("legacybreaker.test.sample")), folder.resolve("src/Example.java"),
                StandardCopyOption.REPLACE_EXISTING);
        JspIntegrationTest.fixtures(folder);
        //4차수정: 변수 탐색용 Java 샘플과 대량 참조 fixture를 인덱싱 전에 추가한다.
        VariableIntegrationTest.fixtures(folder);
        edt(() -> {
            project = ProjectManagerEx.getInstanceEx().newProject("LegacyBreaker Test", folder.toString(), false, false);
            check(project != null, "Project created");
            VirtualFile source = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(folder.resolve("src").toFile());
            ApplicationManager.getApplication().runWriteAction(() -> {
                Module module = ModuleManager.getInstance(project).newModule(folder.resolve("test.iml").toString(), "JAVA_MODULE");
                ModuleRootModificationUtil.updateModel(module, model -> {
                    model.addContentEntry(source).addSourceFolder(source, false);
                    model.setSdk(JavaSdk.getInstance().createJdk("Test JDK 8", System.getProperty("legacybreaker.test.jdk"), false));
                });
                LanguageLevelProjectExtension.getInstance(project).setLanguageLevel(LanguageLevel.JDK_1_8);
            });
            check(ProjectManagerEx.getInstanceEx().openProject(project), "Project opened");
            file = (PsiJavaFile) PsiManager.getInstance(project).findFile(source.findChild("Example.java"));
            root = method("save", "int");
            return null;
        });
    }

    private PsiMethod method(String name, String parameter) {
        for (PsiMethod method : file.getClasses()[0].findMethodsByName(name, false)) {
            PsiParameter[] parameters = method.getParameterList().getParameters();
            if (parameter == null && parameters.length == 0) return method;
            if (parameters.length == 1 && parameters[0].getType().getCanonicalText().equals(parameter)) return method;
        }
        throw new AssertionError("Missing method " + name);
    }

    private static Set<String> names(AnalysisResult result) {
        Set<String> values = new HashSet<>();
        for (CodeNode node : result.nodes.values()) values.add(node.name);
        return values;
    }

    private void analysisTests() {
        check(MethodAtCaret.find(file, root.getNameIdentifier().getTextOffset()) == root, "Caret identifies method");
        check(MethodAtCaret.find(file, file.getText().indexOf("final Store")) == null, "Field is not a method");
        check(MethodAtCaret.find(file, file.getText().indexOf("{ validate(); } // excluded") + 3) == null, "Anonymous initializer boundary");
        AnalysisResult callers = new CallerFinder().find(root);
        check(names(callers).equals(new HashSet<>(Arrays.asList("entry()", "secondCaller()", "save(int)", "run()"))),
                "Direct callers and overload/lambda ownership: " + names(callers));
        AnalysisResult callees = new CalleeFinder().find(root);
        check(names(callees).equals(new HashSet<>(Arrays.asList("validate()", "write()", "save(int)", "currentTimeMillis()"))),
                "Direct callees: " + names(callees));
        check(!MethodNodeMapper.map(root).id.equals(MethodNodeMapper.map(method("save", "java.lang.String")).id), "Overload IDs");
        check(new CalleeFinder().find(method("deferred", null)).nodes.isEmpty(), "Skip lambdas and anonymous classes");
        check(new CalleeFinder().find(method("nested", null)).nodes.isEmpty(), "Skip local classes");
        check(callees.nodes.values().stream().anyMatch(node -> node.external && node.name.equals("currentTimeMillis()")), "Library terminal node");
        StringBuilder large = new StringBuilder("class Limits { void start() { missing();");
        for (int i = 0; i < 120; i++) large.append("m").append(i).append("();");
        large.append("}");
        for (int i = 0; i < 120; i++) large.append("void m").append(i).append("() {}");
        large.append("}");
        PsiJavaFile limits = (PsiJavaFile) PsiFileFactory.getInstance(project).createFileFromText(
                "Limits.java", com.intellij.ide.highlighter.JavaFileType.INSTANCE, large.toString());
        AnalysisResult bounded = new CalleeFinder().find(limits.getClasses()[0].findMethodsByName("start", false)[0]);
        check(bounded.nodes.size() == 100 && bounded.truncated && bounded.unresolved == 1,
                "Bounded expansion and unresolved call reporting");
        System.out.println("PASS: PSI caller/callee tests");
    }

    private void sessionTests() throws Exception {
        ExplorationSessionService session = edt(() -> ExplorationSessionService.getInstance(project));
        edt(() -> { session.explore(root); return null; });
        awaitIdle(session);
        edt(() -> {
            check(!session.isStale(), "Fresh initial graph");
            check(session.graph().nodes().size() == 7, "One-hop initial graph: " + session.graph().nodes().size());
            check(session.graph().edges().size() == 7, "Initial directed edges: " + session.graph().edges().size());
            check(!session.canExpand(session.graph().rootId(), Direction.CALLERS), "Completed expansion reused");
            check(ActionManager.getInstance().getAction("LegacyBreaker.ExploreCodeRelations") != null,
                    "Editor action registered by plugin.xml");
            CodeRelationsPanel first = new CodeRelationsPanel(session);
            first.setSize(1150, 520);
            layout(first);
            BufferedImage picture = new BufferedImage(1150, 520, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = picture.createGraphics();
            first.paint(graphics); graphics.dispose();
            javax.imageio.ImageIO.write(picture, "png", new File(System.getProperty("legacybreaker.test.preview")));
            first.dispose();
            CodeRelationsPanel second = new CodeRelationsPanel(session);
            check(session.graph().nodes().size() == 7, "View recreation retains graph");
            second.dispose();
            String write = session.graph().nodes().stream().filter(n -> n.name.equals("write()")).findFirst().get().id;
            session.select(write);
            session.expand(write, Direction.CALLEES);
            return null;
        });
        awaitIdle(session);
        edt(() -> {
            check(session.graph().nodes().size() == 8, "Lazy expansion adds audit only when requested");
            int before = session.graph().nodes().size();
            session.navigateSelected();
            check(FileEditorManager.getInstance(project).getOpenFiles().length > 0, "Source editor opened");
            check(session.graph().nodes().size() == before, "Source navigation keeps graph");
            return null;
        });
        edt(() -> {
            Document document = PsiDocumentManager.getInstance(project).getDocument(file);
            WriteCommandAction.runWriteCommandAction(project, () -> document.insertString(0, "// edited\n"));
            // Expanding must commit unsaved edits before deciding whether cached results are fresh.
            session.expand(session.selectedId(), Direction.CALLERS);
            return null;
        });
        edt(() -> null); // flush the queued modification notification
        edt(() -> {
            check(session.isStale(), "Source changes invalidate cached expansions");
            check(session.graph().nodes().size() == 8, "Stale graph is retained for inspection");
            session.select(session.graph().rootId());
            session.navigateSelected();
            check(FileEditorManager.getInstance(project).getSelectedTextEditor().getCaretModel().getOffset()
                    == root.getNavigationElement().getTextOffset(), "Smart pointer navigates after inserted text");
            session.refresh();
            return null;
        });
        awaitIdle(session);
        edt(() -> {
            check(!session.isStale() && session.graph().nodes().size() == 7, "Refresh rebuilds current root");
            session.explore(root);
            session.cancel();
            check(!session.isLoading(), "Cancellation clears pending state");
            session.explore(method("validate", null));
            return null;
        });
        awaitIdle(session);
        edt(() -> {
            check(session.graph().node(session.graph().rootId()).name.equals("validate()"), "Old jobs cannot replace new root");
            WriteCommandAction.runWriteCommandAction(project, () -> method("validate", null).delete());
            session.refresh();
            check(session.status().startsWith("Root was removed"), "Deleted root refresh is handled");
            ProjectManagerEx.getInstanceEx().closeAndDispose(project);
            check(project.isDisposed(), "Project service and background workers disposed");
            return null;
        });
    }

    private static void awaitIdle(ExplorationSessionService session) throws Exception {
        long deadline = System.currentTimeMillis() + 30000;
        while (edt(session::isLoading)) {
            if (System.currentTimeMillis() > deadline) throw new AssertionError("Search timeout");
            Thread.sleep(50);
        }
    }
    private static <T> T edt(Callable<T> callable) throws Exception {
        FutureTask<T> task = new FutureTask<>(callable);
        ApplicationManager.getApplication().invokeAndWait(task, ModalityState.NON_MODAL);
        return task.get();
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void layout(java.awt.Container container) {
        container.doLayout();
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof java.awt.Container) layout((java.awt.Container) child);
        }
    }
}
