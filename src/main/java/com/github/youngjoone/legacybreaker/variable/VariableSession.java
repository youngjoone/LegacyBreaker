//4차수정: 신규 파일 전체 — 변수 탐색의 비동기 검색, 필터, 소스 이동, 새로고침과 취소를 관리한다.
package com.github.youngjoone.legacybreaker.variable;

import com.github.youngjoone.legacybreaker.model.*;
import com.intellij.ProjectTopics;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.concurrency.CancellablePromise;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public final class VariableSession implements Disposable {
    public enum Filter { ALL, READS, CHANGES }
    private final Project project;
    private final CodeGraph graph = new CodeGraph();
    private final List<Runnable> listeners = new ArrayList<>();
    private final AtomicBoolean queued = new AtomicBoolean();
    private final ExecutorService executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Variable Relations", 1);
    private VariableAnalysis result;
    private SmartPsiElementPointer<PsiVariable> target;
    private CancellablePromise<VariableAnalysis> pending;
    private VirtualFile file;
    private RangeMarker caret;
    private Filter filter = Filter.ALL;
    private String selected, status = "Select a Java variable name and choose Explore Variable Relations.";
    private long psiStamp, rootsStamp;
    private volatile long generation;
    private volatile boolean disposed;
    private boolean stale;

    public VariableSession(Project project) {
        this.project = project;
        MessageBusConnection connection = project.getMessageBus().connect(this);
        connection.subscribe(PsiModificationTracker.TOPIC, this::queueFreshness);
        connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
            @Override public void rootsChanged(ModuleRootEvent event) { queueFreshness(); }
        });
    }
    public static VariableSession getInstance(Project project) { return ServiceManager.getService(project, VariableSession.class); }

    public void start(VirtualFile source, int offset) {
        edt();
        if (disposed || project.isDisposed() || !source.isValid()) return;
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        Document document = FileDocumentManager.getInstance().getDocument(source);
        if (document == null) return;
        stop();
        if (caret != null) caret.dispose();
        final int position = Math.max(0, Math.min(offset, document.getTextLength()));
        file = source; caret = document.createRangeMarker(position, position);
        result = null; target = null; graph.clear(); selected = null; filter = Filter.ALL;
        stale = false; psiStamp = psiVersion(); rootsStamp = rootsVersion();
        long stamp = generation, documentStamp = document.getModificationStamp();
        status = "Searching variable reads and changes (waiting for indexing if needed)...";
        pending = ReadAction.nonBlocking(() -> {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(source);
            PsiVariable variable = VariableAtCaret.find(psiFile, position);
            if (variable == null) throw new IllegalArgumentException("Place the caret on a Java variable name in its declaration or use.");
            return new VariableAnalyzer().analyze(variable);
        }).inSmartMode(project).expireWhen(() -> disposed || project.isDisposed() || stamp != generation || !source.isValid())
                .finishOnUiThread(ModalityState.NON_MODAL, analysis -> {
                    if (disposed || stamp != generation) return;
                    fresh();
                    if (stale || stamp != generation) return;
                    if (document.getModificationStamp() != documentStamp) { markStale(); return; }
                    pending = null; result = analysis; target = analysis.variable;
                    status = analysis.summary(); rebuild(); changed();
                }).submit(executor);
        pending.onError(error -> ApplicationManager.getApplication().invokeLater(() -> {
            if (disposed || project.isDisposed() || stamp != generation) return;
            pending = null;
            status = error instanceof IllegalArgumentException ? error.getMessage() : "Variable search stopped. Refresh to retry.";
            if (!(error instanceof IllegalArgumentException) && !(error instanceof java.util.concurrent.CancellationException)
                    && !(error instanceof com.intellij.openapi.progress.ProcessCanceledException))
                com.intellij.openapi.diagnostic.Logger.getInstance(VariableSession.class).warn("Variable search failed", error);
            changed();
        }, ModalityState.NON_MODAL));
        changed();
    }

    public void refresh() {
        edt();
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        if (target != null) {
            PsiVariable variable = target.getElement();
            if (variable == null || variable.getNameIdentifier() == null) {
                status = "Variable was removed. Select another variable."; changed(); return;
            }
            start(variable.getContainingFile().getVirtualFile(), variable.getNameIdentifier().getTextOffset());
        } else if (file != null && file.isValid() && caret != null && caret.isValid()) start(file, caret.getStartOffset());
        else { status = "Source location no longer exists. Select another variable."; changed(); }
    }
    public void setFilter(Filter value) { edt(); filter = value; rebuild(); changed(); }
    private void rebuild() {
        graph.clear();
        if (result == null) return;
        CodeGraph all = result.graph;
        graph.reset(all.node(all.rootId()));
        for (CodeEdge edge : all.edges()) {
            String other = edge.source.equals(all.rootId()) ? edge.target : edge.source;
            CodeNode node = all.node(other);
            boolean include = filter == Filter.ALL || node.type.equals("READ_WRITE")
                    || (filter == Filter.READS && node.type.equals("READ"))
                    || (filter == Filter.CHANGES && (node.type.equals("WRITE") || node.type.equals("INITIALIZER")));
            if (include) graph.connect(all.rootId(), node, edge.source.equals(all.rootId()) ? Direction.CALLEES : Direction.CALLERS, edge.type);
        }
        if (graph.node(selected) == null) selected = graph.rootId();
    }
    public void navigate() {
        edt();
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        SmartPsiElementPointer<PsiElement> pointer = result == null ? null : result.pointers.get(selected);
        PsiElement element = pointer == null ? null : pointer.getElement();
        if (element != null && element.getContainingFile().getVirtualFile() != null) {
            new OpenFileDescriptor(project, element.getContainingFile().getVirtualFile(), element.getTextOffset()).navigate(true);
        } else { status = "Source no longer exists. Refresh or select another variable."; changed(); }
    }
    public void cancel() { edt(); stop(); status = "Search cancelled. Refresh to retry."; changed(); }
    private void stop() { generation++; if (pending != null) pending.cancel(); pending = null; }
    private void queueFreshness() {
        if (disposed || !queued.compareAndSet(false, true)) return;
        ApplicationManager.getApplication().invokeLater(() -> {
            queued.set(false);
            if (!disposed && !project.isDisposed()) fresh();
        }, ModalityState.NON_MODAL);
    }
    private void fresh() {
        if (file != null && !stale && (psiVersion() != psiStamp || rootsVersion() != rootsStamp)) markStale();
    }
    private void markStale() { stale = true; stop(); status = "Code changed. This is a snapshot; Refresh to update variable locations."; changed(); }
    private long psiVersion() { return PsiModificationTracker.SERVICE.getInstance(project).getModificationCount(); }
    private long rootsVersion() { return ProjectRootModificationTracker.getInstance(project).getModificationCount(); }
    private static void edt() { ApplicationManager.getApplication().assertIsDispatchThread(); }
    public CodeGraph graph() { return graph; }
    public String status() { return status; }
    public String selectedId() { return selected; }
    public Filter filter() { return filter; }
    public void select(String id) { edt(); selected = id; changed(); }
    public boolean isLoading() { return pending != null; }
    public boolean isStale() { return stale; }
    public boolean canRefresh() { return file != null; }
    public void addListener(Runnable listener) { listeners.add(listener); }
    public void removeListener(Runnable listener) { listeners.remove(listener); }
    private void changed() { for (Runnable listener : new ArrayList<>(listeners)) listener.run(); }
    @Override public void dispose() {
        disposed = true; stop(); executor.shutdownNow();
        if (caret != null) caret.dispose();
        listeners.clear(); result = null; target = null;
    }
}
