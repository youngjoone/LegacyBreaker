package com.github.youngjoone.legacybreaker.jsp;

import com.github.youngjoone.legacybreaker.model.*;
import com.github.youngjoone.legacybreaker.session.ExplorationSessionService;
import com.github.youngjoone.legacybreaker.ui.CodeRelationsToolWindowFactory;
import com.intellij.ProjectTopics;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.*;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.concurrency.CancellablePromise;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Separate project session: JSP tracing never destroys the existing Java call graph. */
public final class JspTraceService implements Disposable {
    private final Project project;
    private final CodeGraph graph = new CodeGraph();
    private final List<Runnable> listeners = new ArrayList<>();
    private final Map<String, RangeMarker> markers = new HashMap<>();
    private final AtomicBoolean freshnessQueued = new AtomicBoolean();
    private ValueTrace trace;
    private CancellablePromise<ValueTrace> pending;
    private VirtualFile jsp;
    private RangeMarker caret;
    private String selected;
    private String status = "Select a JSP output or a direct JS declaration, then Trace JSP Value Origin.";
    private long psiStamp, rootsStamp;
    private volatile long generation;
    private volatile boolean disposed;
    private boolean stale;

    public JspTraceService(Project project) {
        this.project = project;
        MessageBusConnection connection = project.getMessageBus().connect(this);
        connection.subscribe(PsiModificationTracker.TOPIC, this::queueFreshness);
        connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
            @Override public void rootsChanged(ModuleRootEvent event) { queueFreshness(); }
        });
    }
    public static JspTraceService getInstance(Project project) { return ServiceManager.getService(project, JspTraceService.class); }

    public void start(VirtualFile file, int offset) {
        ApplicationManager.getApplication().assertIsDispatchThread();
        if (disposed || !file.isValid()) return;
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        Document document = FileDocumentManager.getInstance().getDocument(file);
        if (document == null) return;
        stopWork(); clearLocations(); graph.clear(); trace = null; selected = null;
        if (caret != null) caret.dispose();
        jsp = file;
        final int traceOffset = Math.max(0, Math.min(offset, document.getTextLength()));
        caret = document.createRangeMarker(traceOffset, traceOffset);
        psiStamp = psiVersion(); rootsStamp = rootsVersion(); stale = false;
        final long stamp = generation;
        final long documentStamp = document.getModificationStamp();
        status = "Tracing JSP values and searching indexed Java writers...";
        pending = ReadAction.nonBlocking(() -> new JspValueAnalyzer().analyze(project, file, document.getText(), traceOffset))
                .inSmartMode(project).expireWhen(() -> disposed || project.isDisposed() || stamp != generation || !file.isValid())
                .finishOnUiThread(ModalityState.NON_MODAL, result -> {
                    if (stamp != generation || disposed) return;
                    fresh();
                    if (stale || stamp != generation) return;
                    if (document.getModificationStamp() != documentStamp) { markStale(); return; }
                    pending = null;
                    trace = result; graph.replaceWith(result.graph); selected = graph.rootId();
                    for (Map.Entry<String, ValueTrace.Location> entry : result.locations.entrySet()) {
                        ValueTrace.Location location = entry.getValue();
                        if (location.pointer == null && location.offset >= 0 && location.offset <= document.getTextLength())
                            markers.put(entry.getKey(), document.createRangeMarker(location.offset, location.offset));
                    }
                    status = result.writers + " Java writer candidate(s). " + (result.partial ? "PARTIAL: search limit reached. " : "")
                            + String.join(" ", result.notes);
                    changed();
                }).submit(AppExecutorUtil.getAppExecutorService());
        pending.onError(error -> ApplicationManager.getApplication().invokeLater(() -> {
            if (stamp != generation || disposed || project.isDisposed()) return;
            pending = null;
            status = error instanceof IllegalArgumentException ? error.getMessage()
                    : "Trace stopped. Refresh to retry. " + error.getClass().getSimpleName();
            if (!(error instanceof IllegalArgumentException) && !(error instanceof java.util.concurrent.CancellationException)
                    && !(error instanceof com.intellij.openapi.progress.ProcessCanceledException))
                com.intellij.openapi.diagnostic.Logger.getInstance(JspTraceService.class).warn("JSP trace failed", error);
            changed();
        }, ModalityState.NON_MODAL));
        changed();
    }
    public void refresh() {
        if (jsp != null && jsp.isValid() && caret != null && caret.isValid()) start(jsp, caret.getStartOffset());
        else { status = "JSP location no longer exists. Select a new expression."; changed(); }
    }
    public void cancel() { stopWork(); status = "Trace cancelled. Refresh to retry."; changed(); }
    private void stopWork() { generation++; if (pending != null) pending.cancel(); pending = null; }
    private long psiVersion() { return PsiModificationTracker.SERVICE.getInstance(project).getModificationCount(); }
    private long rootsVersion() { return ProjectRootModificationTracker.getInstance(project).getModificationCount(); }
    private void queueFreshness() {
        if (disposed || !freshnessQueued.compareAndSet(false, true)) return;
        ApplicationManager.getApplication().invokeLater(() -> {
            freshnessQueued.set(false);
            if (!disposed && !project.isDisposed()) fresh();
        }, ModalityState.NON_MODAL);
    }
    private void fresh() {
        if (jsp != null && !stale && (psiVersion() != psiStamp || rootsVersion() != rootsStamp)) markStale();
    }
    private void markStale() {
        stale = true; stopWork(); status = "Code changed. This trace is a snapshot; Refresh Trace to recompute."; changed();
    }
    public void navigate() {
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        ValueTrace.Location location = trace == null ? null : trace.locations.get(selected);
        if (location == null) return;
        if (location.pointer != null) {
            PsiElement element = location.pointer.getElement();
            if (element != null && element.getNavigationElement() instanceof Navigatable) {
                Navigatable target = (Navigatable) element.getNavigationElement();
                if (target.canNavigate()) { target.navigate(true); return; }
            }
            if (element != null && element.getContainingFile() != null && element.getContainingFile().getVirtualFile() != null) {
                new OpenFileDescriptor(project, element.getContainingFile().getVirtualFile(), element.getTextOffset()).navigate(true); return;
            }
        } else {
            RangeMarker marker = markers.get(selected);
            if (location.file.isValid() && marker != null && marker.isValid()) {
                new OpenFileDescriptor(project, location.file, marker.getStartOffset()).navigate(true); return;
            }
        }
        status = "Source no longer exists. Refresh Trace or select another expression."; changed();
    }
    public boolean canExploreMethod() {
        ValueTrace.Location location = trace == null ? null : trace.locations.get(selected);
        return location != null && location.method != null && location.method.getElement() != null;
    }
    public void exploreMethod() {
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        ValueTrace.Location location = trace == null ? null : trace.locations.get(selected);
        PsiMethod method = location == null || location.method == null ? null : location.method.getElement();
        if (method == null) return;
        ExplorationSessionService.getInstance(project).explore(method);
        ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(CodeRelationsToolWindowFactory.ID);
        if (window != null) window.activate(null);
    }
    public CodeGraph graph() { return graph; }
    public String selectedId() { return selected; }
    public void select(String id) { selected = id; changed(); }
    public String status() { return status; }
    public boolean isLoading() { return pending != null; }
    public boolean isStale() { return stale; }
    public boolean canRefresh() { return jsp != null; }
    public void addListener(Runnable listener) { listeners.add(listener); }
    public void removeListener(Runnable listener) { listeners.remove(listener); }
    private void changed() { for (Runnable listener : new ArrayList<>(listeners)) listener.run(); }
    private void clearLocations() { for (RangeMarker marker : markers.values()) marker.dispose(); markers.clear(); }
    @Override public void dispose() {
        disposed = true; stopWork(); clearLocations();
        if (caret != null) caret.dispose();
        listeners.clear(); trace = null;
    }
}
