package com.github.youngjoone.legacybreaker.session;

import com.github.youngjoone.legacybreaker.analysis.*;
import com.github.youngjoone.legacybreaker.model.*;
import com.github.youngjoone.legacybreaker.navigation.SourceNavigator;
import com.intellij.ProjectTopics;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.concurrency.CancellablePromise;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/** Project lifetime state, independent of editor focus and Tool Window visibility. */
public final class ExplorationSessionService implements Disposable {
    private static final Logger LOG = Logger.getInstance(ExplorationSessionService.class);
    private final Project project;
    private final CodeGraph graph = new CodeGraph();
    private final Map<String, SmartPsiElementPointer<PsiMethod>> pointers = new HashMap<>();
    private final Map<String, CancellablePromise<AnalysisResult>> pending = new HashMap<>();
    private final Map<String, String> completed = new HashMap<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private final AtomicBoolean freshnessScheduled = new AtomicBoolean();
    private final ExecutorService executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Code Relations", 2);
    private volatile long generation;
    private volatile boolean disposed;
    private long psiStamp;
    private long rootsStamp;
    private boolean stale;
    private String selected;
    private String status = "Place the caret in a Java method and choose Explore Code Relations.";

    public ExplorationSessionService(Project project) {
        this.project = project;
        MessageBusConnection connection = project.getMessageBus().connect(this);
        connection.subscribe(PsiModificationTracker.TOPIC, () -> scheduleFreshnessCheck());
        connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
            @Override public void rootsChanged(ModuleRootEvent event) { scheduleFreshnessCheck(); }
        });
    }

    public static ExplorationSessionService getInstance(Project project) {
        return ServiceManager.getService(project, ExplorationSessionService.class);
    }

    public void explore(PsiMethod method) {
        assertEdt();
        if (disposed || !method.isValid()) return;
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        if (!method.isValid()) return;
        cancelPending();
        completed.clear();
        pointers.clear();
        CodeNode root = MethodNodeMapper.map(method);
        graph.reset(root);
        pointers.put(root.id, SmartPointerManager.getInstance(project).createSmartPsiElementPointer(method));
        selected = root.id;
        stale = false;
        psiStamp = psiVersion();
        rootsStamp = rootsVersion();
        status = root.external
                ? "Root is outside project source roots. Navigation is available; expansion requires project sources."
                : "Root selected. Loading direct callers and callees (waiting for indexing if needed).";
        fireChanged();
        expand(root.id, Direction.CALLERS);
        expand(root.id, Direction.CALLEES);
    }

    public void expand(String id, Direction direction) {
        assertEdt();
        if (disposed || project.isDisposed()) return;
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        checkFreshness();
        if (!canExpand(id, direction)) return;
        final String key = key(id, direction);
        final long requestGeneration = generation;
        final SmartPsiElementPointer<PsiMethod> pointer = pointers.get(id);
        status = "Loading " + direction.name().toLowerCase(Locale.ROOT) + " (waiting for indexing if needed)...";
        CancellablePromise<AnalysisResult> promise = ReadAction.nonBlocking(() -> {
            PsiMethod method = pointer == null ? null : pointer.getElement();
            if (method == null) throw new IllegalStateException("Method was removed. Explore again.");
            return new MethodAnalyzer().analyze(method, direction);
        }).inSmartMode(project)
                .expireWhen(() -> disposed || project.isDisposed() || generation != requestGeneration)
                .finishOnUiThread(ModalityState.NON_MODAL, result -> {
                    if (disposed || generation != requestGeneration) return;
                    checkFreshness();
                    if (stale || generation != requestGeneration) return;
                    pending.remove(key);
                    int added = 0;
                    for (CodeNode node : result.nodes.values()) {
                        if (graph.connect(id, node, direction)) {
                            pointers.put(node.id, result.pointers.get(node.id));
                            added++;
                        } else result.truncated = true;
                    }
                    String summary = added + " related methods"
                            + (result.truncated ? " (partial: search/graph limit reached)" : "")
                            + (result.unresolved > 0 ? "; " + result.unresolved + " unresolved calls" : "");
                    completed.put(key, summary);
                    status = direction.name().toLowerCase(Locale.ROOT) + ": " + summary;
                    fireChanged();
                }).submit(executor);
        pending.put(key, promise);
        promise.onError(error -> ApplicationManager.getApplication().invokeLater(() -> {
            if (disposed || project.isDisposed() || generation != requestGeneration) return;
            pending.remove(key);
            if (error instanceof ProcessCanceledException || error instanceof CancellationException) {
                status = "Search cancelled. You can expand this direction again.";
            } else {
                LOG.warn("Code relationship search failed", error);
                status = "Search failed. Refresh or retry this direction; details are in the IDE log.";
            }
            fireChanged();
        }, ModalityState.NON_MODAL));
        fireChanged();
    }

    public void refresh() {
        assertEdt();
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        SmartPsiElementPointer<PsiMethod> pointer = pointers.get(graph.rootId());
        PsiMethod method = pointer == null ? null : pointer.getElement();
        if (method == null || !method.isValid()) {
            status = "Root was removed. Choose Explore Code Relations on another method.";
            fireChanged();
        } else explore(method);
    }

    public void cancel() {
        assertEdt();
        cancelPending();
        status = "Pending searches cancelled; the existing graph is kept.";
        fireChanged();
    }

    private void cancelPending() {
        generation++;
        for (CancellablePromise<AnalysisResult> promise : pending.values()) promise.cancel();
        pending.clear();
    }

    private void scheduleFreshnessCheck() {
        if (disposed || !freshnessScheduled.compareAndSet(false, true)) return;
        ApplicationManager.getApplication().invokeLater(() -> {
            freshnessScheduled.set(false);
            if (!disposed && !project.isDisposed()) checkFreshness();
        }, ModalityState.NON_MODAL);
    }

    private void checkFreshness() {
        if (graph.rootId() != null && !stale && (psiStamp != psiVersion() || rootsStamp != rootsVersion())) {
            stale = true;
            cancelPending();
            completed.clear();
            status = "Code changed. Graph is a snapshot; Refresh Root to analyze the current code.";
            fireChanged();
        }
    }

    private long psiVersion() { return PsiModificationTracker.SERVICE.getInstance(project).getModificationCount(); }
    private long rootsVersion() { return ProjectRootModificationTracker.getInstance(project).getModificationCount(); }
    private static String key(String id, Direction direction) { return id + "\u0000" + direction.name(); }
    private static void assertEdt() { ApplicationManager.getApplication().assertIsDispatchThread(); }

    public boolean canExpand(String id, Direction direction) {
        CodeNode node = graph.node(id);
        return !disposed && !stale && node != null && !node.external
                && !pending.containsKey(key(id, direction)) && !completed.containsKey(key(id, direction));
    }
    public String directionStatus(String id, Direction direction) {
        if (stale) return "stale snapshot";
        if (pending.containsKey(key(id, direction))) return "loading / waiting for indexing";
        String summary = completed.get(key(id, direction));
        return summary == null ? "not expanded" : summary;
    }
    public void navigateSelected() { assertEdt(); SourceNavigator.navigate(project, pointers.get(selected)); }
    public void select(String id) { assertEdt(); selected = id; fireChanged(); }
    public CodeGraph graph() { return graph; }
    public String selectedId() { return selected; }
    public String status() { return status; }
    public boolean isStale() { return stale; }
    public boolean isLoading() { return !pending.isEmpty(); }
    public void addListener(Runnable listener) { listeners.add(listener); }
    public void removeListener(Runnable listener) { listeners.remove(listener); }
    private void fireChanged() { for (Runnable listener : new ArrayList<>(listeners)) listener.run(); }

    @Override public void dispose() {
        disposed = true;
        cancelPending();
        listeners.clear();
        pointers.clear();
        executor.shutdownNow();
    }
}
