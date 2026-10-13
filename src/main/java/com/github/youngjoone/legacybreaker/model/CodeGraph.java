package com.github.youngjoone.legacybreaker.model;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Single-owner mutable graph. The session and view access it only on the EDT. */
public final class CodeGraph {
    public static final int MAX_NODES = 400;
    public static final int MAX_EDGES = 2000;
    private final Map<String, CodeNode> nodes = new LinkedHashMap<String, CodeNode>();
    private final Set<CodeEdge> edges = new LinkedHashSet<CodeEdge>();
    private String rootId;
    private long resetVersion;

    public void reset(CodeNode root) {
        resetVersion++;
        nodes.clear();
        edges.clear();
        rootId = root.id;
        nodes.put(root.id, root);
    }

    /** Adds a neighbor and edge atomically; no dangling or duplicate edges. */
    public boolean connect(String center, CodeNode neighbor, Direction direction) {
        if (!nodes.containsKey(center)) throw new IllegalArgumentException("Unknown center");
        CodeEdge edge = direction.edge(center, neighbor.id);
        if ((!nodes.containsKey(neighbor.id) && nodes.size() >= MAX_NODES)
                || (!edges.contains(edge) && edges.size() >= MAX_EDGES)) return false;
        nodes.put(neighbor.id, neighbor);
        edges.add(edge);
        return true;
    }

    public CodeNode node(String id) { return nodes.get(id); }
    public String rootId() { return rootId; }
    public long resetVersion() { return resetVersion; }
    public Collection<CodeNode> nodes() { return Collections.unmodifiableCollection(nodes.values()); }
    public Set<CodeEdge> edges() { return Collections.unmodifiableSet(edges); }
}
