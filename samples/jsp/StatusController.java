import javax.servlet.http.HttpServletRequest;

// Illustrative legacy MVC method. Framework routing annotations are omitted.
// Requires the application's existing Servlet API; the plugin does not bundle one.
class StatusController {
    String show(HttpServletRequest request) {
        String userStatus = loadStatus();
        request.setAttribute("userStatus", userStatus);
        return "status";
    }

    String loadStatus() { return "ACTIVE"; }
}
