package com.github.youngjoone.legacybreaker.jsp;

import java.util.*;
import java.util.regex.*;

/** Bounded lexical extraction, not a full JSP or JavaScript parser. Offsets are UTF-16. */
public final class JspSource {
    public static final int MAX_LENGTH = 300000;
    public static final class Part {
        public final int start, bodyStart, end;
        public final boolean output;
        int javaStart;
        Part(int start, int bodyStart, int end, boolean output) {
            this.start = start; this.bodyStart = bodyStart; this.end = end; this.output = output;
        }
    }
    public final String text;
    public final List<Part> parts = new ArrayList<>();
    public final String javaSource;
    public final boolean includes;
    private final String visible;
    private final Map<Part, int[]> bindings = new HashMap<>();
    private final Runnable checkCancelled;

    public JspSource(String text) { this(text, () -> { }); }

    public JspSource(String text, Runnable checkCancelled) {
        this.checkCancelled = checkCancelled;
        if (text.length() > MAX_LENGTH) throw new IllegalArgumentException("JSP exceeds the 300,000 character limit.");
        this.text = text;
        StringBuilder mask = new StringBuilder(text);
        StringBuilder java = new StringBuilder("class __JspTrace { void render(Object request, Object session, Object response, Object out, Object pageContext) {\n");
        boolean hasIncludes = false;
        int position = 0;
        while ((position = text.indexOf("<%", position)) >= 0) {
            checkCancelled.run();
            if (text.startsWith("<%--", position)) {
                int end = text.indexOf("--%>", position + 4);
                if (end < 0) throw new IllegalArgumentException("Unclosed JSP comment.");
                blank(mask, position, end + 4); position = end + 4; continue;
            }
            int end = text.indexOf("%>", position + 2);
            if (end < 0) throw new IllegalArgumentException("Unclosed JSP scriptlet.");
            char kind = position + 2 < text.length() ? text.charAt(position + 2) : ' ';
            if (kind == '!') throw new IllegalArgumentException("JSP declarations (<%!) are not supported in this trace stage.");
            if (kind == '@') {
                if (text.substring(position, end).matches("(?s).*\\binclude\\b.*")) hasIncludes = true;
                blank(mask, position, end + 2);
            } else {
                if (parts.size() >= 1000) throw new IllegalArgumentException("Too many JSP scriptlet blocks.");
                Part part = new Part(position, position + (kind == '=' ? 3 : 2), end + 2, kind == '=');
                if (part.output) java.append("__output(");
                part.javaStart = java.length();
                java.append(text, part.bodyStart, end);
                java.append(part.output ? ");\n" : "\n");
                parts.add(part);
            }
            position = end + 2;
        }
        java.append("} void __output(Object value) {} }");
        this.javaSource = java.toString();
        this.visible = mask.toString();
        this.includes = hasIncludes || Pattern.compile("<jsp:include\\b", Pattern.CASE_INSENSITIVE).matcher(visible).find();
    }

    public Part outputAt(int caret) {
        checkCancelled.run();
        for (Part part : parts) if (part.output && caret >= part.start && caret < part.end) return part;
        // Only direct var/let/const initializers; do not guess scopes at arbitrary JS usages.
        for (Part part : parts) {
            if (!part.output) continue;
            int[] binding = binding(part);
            if (binding != null && caret >= binding[0] && caret <= binding[1]) return part;
        }
        throw new IllegalArgumentException("Select a <%= expression %> or a JS declaration directly initialized from it.");
    }

    public String jsName(Part part) {
        int[] binding = binding(part);
        return binding == null ? null : text.substring(binding[2], binding[3]);
    }
    public int jsOffset(Part part) { int[] b = binding(part); return b == null ? part.start : b[2]; }
    public int javaOffset(Part part) { return part.javaStart; }

    public int sourceOffset(int javaOffset) {
        for (Part part : parts) {
            int length = part.end - 2 - part.bodyStart;
            if (javaOffset >= part.javaStart && javaOffset < part.javaStart + length)
                return part.bodyStart + javaOffset - part.javaStart;
        }
        return -1;
    }

    private boolean bindingsBuilt;
    private int[] binding(Part output) {
        if (!bindingsBuilt) { buildBindings(); bindingsBuilt = true; }
        return bindings.get(output);
    }

    private void buildBindings() {
        Map<Integer, Part> outputs = new HashMap<>();
        for (Part part : parts) if (part.output) outputs.put(part.start, part);
        Matcher scripts = Pattern.compile("(?is)<script\\b[^>]*>(.*?)</script\\s*>").matcher(visible);
        while (scripts.find()) {
            checkCancelled.run();
            StringBuilder masked = new StringBuilder(visible.substring(scripts.start(1), scripts.end(1)));
            for (Part part : parts) if (part.start >= scripts.start(1) && part.end <= scripts.end(1))
                blank(masked, part.start - scripts.start(1), part.end - scripts.start(1));
            hideJsStringsAndComments(masked);
            Matcher declarations = Pattern.compile("\\b(?:var|let|const)\\s+([A-Za-z_$][\\w$]*)\\s*=").matcher(masked);
            while (declarations.find()) {
                checkCancelled.run();
                int position = scripts.start(1) + declarations.end();
                while (position < scripts.end(1) && Character.isWhitespace(text.charAt(position))) position++;
                char quote = position < scripts.end(1) ? text.charAt(position) : 0;
                if (quote == '\'' || quote == '"') position++;
                else quote = 0;
                Part output = outputs.get(position);
                if (output == null || output.end > scripts.end(1)) continue;
                int end = output.end;
                if (quote != 0) {
                    if (end >= scripts.end(1) || text.charAt(end) != quote) continue;
                    end++;
                }
                int after = end;
                while (after < scripts.end(1) && Character.isWhitespace(text.charAt(after))) after++;
                if (after < scripts.end(1) && text.charAt(after) != ';') continue;
                bindings.put(output, new int[]{scripts.start(1) + declarations.start(), end,
                        scripts.start(1) + declarations.start(1), scripts.start(1) + declarations.end(1)});
            }
        }
    }

    private static void hideJsStringsAndComments(StringBuilder text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'' || c == '"' || c == '`') {
                int start = i++;
                while (i < text.length()) {
                    if (text.charAt(i) == '\\') { i += 2; continue; }
                    if (text.charAt(i++) == c) break;
                }
                blank(text, start, Math.min(i, text.length())); i--;
            } else if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '/') {
                int start = i;
                while (i < text.length() && text.charAt(i) != '\n') i++;
                blank(text, start, i); i--;
            } else if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '*') {
                int start = i;
                i += 2;
                while (i + 1 < text.length() && !(text.charAt(i) == '*' && text.charAt(i + 1) == '/')) i++;
                i = Math.min(text.length(), i + 2);
                blank(text, start, i); i--;
            }
        }
    }
    private static void blank(StringBuilder text, int start, int end) {
        for (int i = start; i < end; i++) if (text.charAt(i) != '\n' && text.charAt(i) != '\r') text.setCharAt(i, ' ');
    }
}
