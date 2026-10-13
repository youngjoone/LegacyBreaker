package com.github.youngjoone.legacybreaker.model;

public enum Direction {
    CALLERS, CALLEES;

    public CodeEdge edge(String center, String neighbor) {
        return this == CALLERS ? new CodeEdge(neighbor, center, CodeEdge.CALLS)
                : new CodeEdge(center, neighbor, CodeEdge.CALLS);
    }
}
