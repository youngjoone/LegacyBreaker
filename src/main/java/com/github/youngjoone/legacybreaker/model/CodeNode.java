package com.github.youngjoone.legacybreaker.model;

/** A source snapshot; intentionally contains no PSI or UI objects. */
public final class CodeNode {
    public final String id;
    public final String type;
    public final String name;
    public final String className;
    public final String filePath;
    public final int line;
    public final boolean external;
    public final String description;

    public CodeNode(String id, String type, String name, String className,
                    String filePath, int line, boolean external) {
        this(id, type, name, className, filePath, line, external, "");
    }

    public CodeNode(String id, String type, String name, String className,
                    String filePath, int line, boolean external, String description) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.className = className;
        this.filePath = filePath;
        this.line = line;
        this.external = external;
        this.description = description;
    }
}
