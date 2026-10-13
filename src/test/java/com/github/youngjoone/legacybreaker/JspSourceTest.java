package com.github.youngjoone.legacybreaker;

import com.github.youngjoone.legacybreaker.jsp.JspSource;

public final class JspSourceTest {
    public static void main(String[] args) {
        String prefix = "<% String value = (String) request.getAttribute(\"key\"); %>\n";
        String text = prefix + "<script>var status = \"<%= value %>\";</script>";
        JspSource source = new JspSource(text);
        JspSource.Part output = source.outputAt(text.indexOf("status"));
        check("status".equals(source.jsName(output)), "JS declaration maps to output");
        check(source.sourceOffset(source.javaOffset(output)) == output.bodyStart, "Offsets survive extraction");
        check(source.outputAt(text.indexOf("value %>")).output, "Direct JSP output selection");
        String commented = "<%-- <%= fake %> --%>" + text;
        check(new JspSource(commented).parts.size() == 2, "JSP comments skipped");
        rejectAt(prefix + "<script>// var fake = \"<%= value %>\";\n</script>", "fake");
        rejectAt(prefix + "<script>/* var fake = \"<%= value %>\"; */</script>", "fake");
        rejectAt(prefix + "<script>var fake = \"prefix<%= value %>\";</script>", "fake");
        rejectAt(prefix + "<script>var fake = <%= value %> + suffix;</script>", "fake");
        rejectAt(prefix + "<script>var fake = \" <%= value %>\";</script>", "fake");
        rejectAt(prefix + "<script>var fake = \"<%= value %> \";</script>", "fake");
        String plain = prefix + "<script>let status = <%= value %> ;</script>";
        JspSource unquoted = new JspSource(plain);
        check("status".equals(unquoted.jsName(unquoted.outputAt(plain.indexOf("status")))), "Unquoted direct initializer");
        rejectAt(prefix + "<div>var fake = <%= value %>;</div>", "fake");
        rejectAt(text + "<script>use(status);</script>", "use(status)");
        check(new JspSource("<%@ include file=\"x.jsp\" %>" + text).includes, "Includes explicitly marked");
        try { new JspSource("<%! int value; %>"); throw new AssertionError("Declaration accepted"); }
        catch (IllegalArgumentException expected) { }
        System.out.println("PASS: JSP extraction, comments, direct JS binding, offsets and unsupported syntax");
    }
    private static void rejectAt(String text, String target) {
        try { new JspSource(text).outputAt(text.indexOf(target)); throw new AssertionError("Unexpected binding: " + target); }
        catch (IllegalArgumentException expected) { }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
