<%@ page contentType="text/html;charset=UTF-8" %>
<%
    String userStatus = (String) request.getAttribute("userStatus");
%>
<script>
    var status = "<%= userStatus %>";
</script>
<!-- Trace from status above or from the JSP output expression. -->
<!-- This example illustrates legacy interpolation, not a safe general-purpose JS encoding utility. -->
