package com.github.youngjoone.legacybreaker.model;

import java.util.Objects;

public final class CodeEdge {
    public static final String CALLS = "CALLS";
    public final String source;
    public final String target;
    public final String type;

    public CodeEdge(String source, String target, String type) {
        this.source = source;
        this.target = target;
        this.type = type;
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof CodeEdge)) return false;
        CodeEdge edge = (CodeEdge) other;
        return source.equals(edge.source) && target.equals(edge.target) && type.equals(edge.type);
    }

    @Override public int hashCode() { return Objects.hash(source, target, type); }
}
