import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WebsiteServer {
    private HttpServer server;
    private ClubRepository clubRepository;
    private EventRepository eventRepository;
    private UserRepository userRepository;
    private SessionManager sessionManager;

    public WebsiteServer(int port, ClubRepository clubRepository, EventRepository eventRepository,
                          UserRepository userRepository, SessionManager sessionManager) throws IOException {
        this.clubRepository = clubRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.sessionManager = sessionManager;

        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this::handleHomePage);
        server.createContext("/clubs", this::handleClubsPage);
        server.createContext("/events", this::handleEventsPage);
        server.createContext("/search", this::handleSearchPage);
        server.createContext("/categories", this::handleCategoriesPage);
        server.createContext("/club", this::handleClubDetailsPage);
        server.createContext("/add-club", this::handleAddClubPage);
        server.createContext("/edit-club", this::handleEditClubPage);
        server.createContext("/delete-club", this::handleDeleteClub);
        server.createContext("/join-club", this::handleJoinClub);
        server.createContext("/add-event", this::handleAddEventPage);
        server.createContext("/rsvp", this::handleRsvp);
        server.createContext("/login", this::handleLoginPage);
        server.createContext("/register", this::handleRegisterPage);
        server.createContext("/logout", this::handleLogout);
        server.createContext("/promote", this::handlePromotePage);
    }

    public void start() {
        server.start();
        System.out.println("Website running at http://localhost:8080");
    }

    // ===================== AUTH HELPERS =====================

    private String getCookieValue(HttpExchange exchange, String name) {
        List<String> cookieHeaders = exchange.getRequestHeaders().get("Cookie");
        if (cookieHeaders == null) {
            return null;
        }
        for (String header : cookieHeaders) {
            for (String part : header.split(";")) {
                String[] pieces = part.trim().split("=", 2);
                if (pieces.length == 2 && pieces[0].equals(name)) {
                    return pieces[1];
                }
            }
        }
        return null;
    }

    private User getCurrentUser(HttpExchange exchange) throws SQLException {
        String token = getCookieValue(exchange, "session");
        String username = sessionManager.getUsername(token);
        if (username == null) {
            return null;
        }
        return userRepository.findByUsername(username);
    }

    private void sendRedirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.getResponseBody().close();
    }

    // ===================== LOGIN / REGISTER / LOGOUT =====================

    private void handleLoginPage(HttpExchange exchange) throws IOException {
        if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            Map<String, List<String>> formData = parseFormData(readRequestBody(exchange));
            String username = getFirstValue(formData, "username");
            String password = getFirstValue(formData, "password");

            try {
                User user = userRepository.checkLogin(username, password);
                if (user != null) {
                    String token = sessionManager.createSession(user.getUsername());
                    exchange.getResponseHeaders().add("Set-Cookie", "session=" + token + "; Path=/");
                    sendRedirect(exchange, "/");
                    return;
                }
            } catch (SQLException e) {
                throw new IOException(e);
            }

            String html = wrapPage("Login", null, """
                <h1>Login</h1>
                <div class='card'>
                    <p style='color:#b91c1c;'>Incorrect username or password.</p>
                    """ + loginForm() + """
                </div>
                """);
            sendHtml(exchange, html);
            return;
        }

        String html = wrapPage("Login", null, "<h1>Login</h1>" + loginForm());
        sendHtml(exchange, html);
    }

    private String loginForm() {
        return "<div class='card'>"
                + "<form action='/login' method='post'>"
                + "<label>Username:</label><br><input type='text' name='username' required><br><br>"
                + "<label>Password:</label><br><input type='password' name='password' required><br><br>"
                + "<button type='submit'>Login</button>"
                + "</form>"
                + "<p>No account? <a href=\"/register\">Register here</a></p>"
                + "</div>";
    }

    private void handleRegisterPage(HttpExchange exchange) throws IOException {
        if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            Map<String, List<String>> formData = parseFormData(readRequestBody(exchange));
            String username = getFirstValue(formData, "username");
            String password = getFirstValue(formData, "password");

            try {
                if (username.isEmpty() || password.isEmpty()) {
                    sendHtml(exchange, wrapPage("Register", null,
                            "<h1>Register</h1><div class='card'><p style='color:#b91c1c;'>" +
                                    "Username and password are required.</p>" + registerForm() + "</div>"));
                    return;
                }
                if (userRepository.usernameExists(username)) {
                    sendHtml(exchange, wrapPage("Register", null,
                            "<h1>Register</h1><div class='card'><p style='color:#b91c1c;'>" +
                                    "That username is already taken.</p>" + registerForm() + "</div>"));
                    return;
                }

                // New accounts always start as Level 1 (Student)
                userRepository.createUser(username, password, User.LEVEL_STUDENT);
                String token = sessionManager.createSession(username);
                exchange.getResponseHeaders().add("Set-Cookie", "session=" + token + "; Path=/");
                sendRedirect(exchange, "/");
            } catch (SQLException e) {
                throw new IOException(e);
            }
            return;
        }

        String html = wrapPage("Register", null, "<h1>Create an Account</h1>" + registerForm());
        sendHtml(exchange, html);
    }

    private String registerForm() {
        return "<div class='card'>"
                + "<form action='/register' method='post'>"
                + "<label>Choose a username:</label><br><input type='text' name='username' required><br><br>"
                + "<label>Choose a password:</label><br><input type='password' name='password' required><br><br>"
                + "<button type='submit'>Create Account</button>"
                + "</form>"
                + "</div>";
    }

    private void handleLogout(HttpExchange exchange) throws IOException {
        String token = getCookieValue(exchange, "session");
        sessionManager.invalidate(token);
        exchange.getResponseHeaders().add("Set-Cookie", "session=; Path=/; Max-Age=0");
        sendRedirect(exchange, "/");
    }

    // ===================== PROMOTE (LEVEL 3 ONLY) =====================

    private void handlePromotePage(HttpExchange exchange) throws IOException {
        try {
            User currentUser = getCurrentUser(exchange);
            if (currentUser == null) {
                sendRedirect(exchange, "/login");
                return;
            }
            if (!currentUser.isAtLeast(User.LEVEL_UTA_STAFF)) {
                sendHtml(exchange, forbiddenPage(currentUser));
                return;
            }

            if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                Map<String, List<String>> formData = parseFormData(readRequestBody(exchange));
                String username = getFirstValue(formData, "username");
                userRepository.promoteToClubPresident(username);
                sendRedirect(exchange, "/promote");
                return;
            }

            List<User> students = userRepository.findAllStudents();
            StringBuilder rows = new StringBuilder();
            if (students.isEmpty()) {
                rows.append("<p>No Level 1 students to promote right now.</p>");
            }
            for (User student : students) {
                rows.append("<div class='card'>")
                        .append("<p>").append(student.getUsername()).append("</p>")
                        .append("<form action='/promote' method='post'>")
                        .append("<input type='hidden' name='username' value='").append(student.getUsername()).append("'>")
                        .append("<button type='submit'>Promote to Club President</button>")
                        .append("</form>")
                        .append("</div>");
            }

            String html = wrapPage("Promote Users", currentUser,
                    "<h1>Promote Students to Club President</h1>" + rows);
            sendHtml(exchange, html);
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    // ===================== CLUB DETAILS / ADD / EDIT / DELETE =====================

    private void handleClubDetailsPage(HttpExchange exchange) throws IOException {
        try {
            User currentUser = getCurrentUser(exchange);
            int id = getIntParam(exchange, "id", -1);
            Club club = clubRepository.getClubById(id);

            if (club == null) {
                sendHtml(exchange, wrapPage("Club Not Found", currentUser,
                        "<div class='card'><p>That club doesn't exist.</p><a href=\"/clubs\">Back to Clubs</a></div>"));
                return;
            }

            String html = wrapPage(club.getName(), currentUser,
                    "<h1>" + club.getName() + "</h1>" + buildClubCard(club, currentUser));
            sendHtml(exchange, html);
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    private void handleAddClubPage(HttpExchange exchange) throws IOException {
        try {
            User currentUser = getCurrentUser(exchange);
            if (currentUser == null) {
                sendRedirect(exchange, "/login");
                return;
            }
            if (!currentUser.isAtLeast(User.LEVEL_CLUB_PRESIDENT)) {
                sendHtml(exchange, forbiddenPage(currentUser));
                return;
            }

            if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                Map<String, List<String>> formData = parseFormData(readRequestBody(exchange));
                String name = getFirstValue(formData, "name");
                String description = getFirstValue(formData, "description");
                String contactEmail = getFirstValue(formData, "contactEmail");
                String meetingTime = getFirstValue(formData, "meetingTime");
                int members = parseIntOrZero(getFirstValue(formData, "members"));
                List<String> selectedTags = formData.getOrDefault("tags", new ArrayList<>());
                boolean autoApprove = formData.containsKey("autoApprove");

                int newId = clubRepository.addClub(name, selectedTags, members, description,
                        contactEmail, meetingTime, currentUser.getUsername(), autoApprove);

                sendRedirect(exchange, "/club?id=" + newId);
                return;
            }

            String form = clubForm("/add-club", "Add Club", null, new ArrayList<>());
            String html = wrapPage("Add a Club", currentUser, "<h1>Add a New Club</h1>" + form);
            sendHtml(exchange, html);
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    private void handleEditClubPage(HttpExchange exchange) throws IOException {
        try {
            User currentUser = getCurrentUser(exchange);
            if (currentUser == null) {
                sendRedirect(exchange, "/login");
                return;
            }

            boolean isStaff = currentUser.isAtLeast(User.LEVEL_UTA_STAFF);

            if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                Map<String, List<String>> formData = parseFormData(readRequestBody(exchange));
                int id = parseIntOrZero(getFirstValue(formData, "id"));
                String name = getFirstValue(formData, "name");
                String description = getFirstValue(formData, "description");
                String contactEmail = getFirstValue(formData, "contactEmail");
                String meetingTime = getFirstValue(formData, "meetingTime");
                int members = parseIntOrZero(getFirstValue(formData, "members"));
                List<String> selectedTags = formData.getOrDefault("tags", new ArrayList<>());
                boolean autoApprove = formData.containsKey("autoApprove");

                boolean updated = clubRepository.updateClub(id, name, selectedTags, members, description,
                        contactEmail, meetingTime, autoApprove, currentUser.getUsername(), isStaff);

                if (!updated) {
                    sendHtml(exchange, forbiddenPage(currentUser));
                    return;
                }
                sendRedirect(exchange, "/club?id=" + id);
                return;
            }

            int id = getIntParam(exchange, "id", -1);
            Club club = clubRepository.getClubById(id);
            if (club == null) {
                sendHtml(exchange, wrapPage("Club Not Found", currentUser, "<div class='card'><p>That club doesn't exist.</p></div>"));
                return;
            }
            if (!isStaff && !club.isOwnedBy(currentUser.getUsername())) {
                sendHtml(exchange, forbiddenPage(currentUser));
                return;
            }

            String form = clubForm("/edit-club", "Save Changes", club, club.getCategories());
            String html = wrapPage("Edit Club", currentUser, "<h1>Edit " + club.getName() + "</h1>" + form);
            sendHtml(exchange, html);
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    private void handleDeleteClub(HttpExchange exchange) throws IOException {
        try {
            User currentUser = getCurrentUser(exchange);
            if (currentUser == null) {
                sendRedirect(exchange, "/login");
                return;
            }
            Map<String, List<String>> formData = parseFormData(readRequestBody(exchange));
            int id = parseIntOrZero(getFirstValue(formData, "id"));
            boolean isStaff = currentUser.isAtLeast(User.LEVEL_UTA_STAFF);

            boolean deleted = clubRepository.deleteClub(id, currentUser.getUsername(), isStaff);
            if (!deleted) {
                sendHtml(exchange, forbiddenPage(currentUser));
                return;
            }
            sendRedirect(exchange, "/clubs");
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    private void handleJoinClub(HttpExchange exchange) throws IOException {
        try {
            User currentUser = getCurrentUser(exchange);
            if (currentUser == null) {
                sendRedirect(exchange, "/login");
                return;
            }
            Map<String, List<String>> formData = parseFormData(readRequestBody(exchange));
            int clubId = parseIntOrZero(getFirstValue(formData, "clubId"));
            // joinClub() itself checks that the club has auto-approve turned on before doing anything
            clubRepository.joinClub(clubId, currentUser.getUsername());
            sendRedirect(exchange, "/club?id=" + clubId);
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    // Shared form for both /add-club and /edit-club
    private String clubForm(String action, String buttonLabel, Club existingClub, List<String> selectedTags) {
        StringBuilder checkboxes = new StringBuilder();
        for (String tag : Tags.ALL_TAGS) {
            String checked = selectedTags.contains(tag) ? " checked" : "";
            checkboxes.append("<label><input type='checkbox' name='tags' value='").append(tag).append("'")
                    .append(checked).append("> ").append(tag).append("</label><br>");
        }

        String idField = existingClub != null
                ? "<input type='hidden' name='id' value='" + existingClub.getId() + "'>"
                : "";
        String name = existingClub != null ? existingClub.getName() : "";
        String description = existingClub != null ? existingClub.getDescription() : "";
        String contactEmail = existingClub != null ? existingClub.getContactEmail() : "";
        String meetingTime = existingClub != null ? existingClub.getMeetingTime() : "";
        int members = existingClub != null ? existingClub.getMembers() : 0;
        String autoApproveChecked = existingClub != null && existingClub.isAutoApproveMembers() ? " checked" : "";

        return "<div class='card'>"
                + "<form action='" + action + "' method='post'>"
                + idField
                + "<label>Club name:</label><br><input type='text' name='name' value='" + name + "' required><br><br>"
                + "<label>Category (select all that fit):</label><br>" + checkboxes + "<br>"
                + "<label>Description:</label><br><textarea name='description' rows='3' required>" + description + "</textarea><br><br>"
                + "<label>Meeting time (optional):</label><br><input type='text' name='meetingTime' value='" + meetingTime + "'><br><br>"
                + "<label>Number of members (optional):</label><br><input type='number' name='members' value='" + members + "' min='0'><br><br>"
                + "<label>Contact email:</label><br><input type='email' name='contactEmail' value='" + contactEmail + "' required><br><br>"
                + "<label><input type='checkbox' name='autoApprove'" + autoApproveChecked + "> "
                + "Auto-approve join requests (members join instantly through the site instead of contacting you)</label><br><br>"
                + "<p style='color:#6b7280; font-size: 0.9em;'>Picture upload is coming soon.</p>"
                + "<button type='submit'>" + buttonLabel + "</button>"
                + "</form>"
                + "</div>";
    }

    // ===================== EVENTS / RSVP =====================

    private void handleAddEventPage(HttpExchange exchange) throws IOException {
        try {
            User currentUser = getCurrentUser(exchange);
            if (currentUser == null) {
                sendRedirect(exchange, "/login");
                return;
            }
            if (!currentUser.isAtLeast(User.LEVEL_CLUB_PRESIDENT)) {
                sendHtml(exchange, forbiddenPage(currentUser));
                return;
            }

            boolean isStaff = currentUser.isAtLeast(User.LEVEL_UTA_STAFF);
            List<Club> ownedClubs = isStaff ? clubRepository.getAllClubs() : clubRepository.getClubsOwnedBy(currentUser.getUsername());

            if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                Map<String, List<String>> formData = parseFormData(readRequestBody(exchange));
                int clubId = parseIntOrZero(getFirstValue(formData, "clubId"));

                boolean allowed = isStaff || ownedClubs.stream().anyMatch(c -> c.getId() == clubId);
                if (!allowed) {
                    sendHtml(exchange, forbiddenPage(currentUser));
                    return;
                }

                String title = getFirstValue(formData, "title");
                String date = getFirstValue(formData, "date");
                String location = getFirstValue(formData, "location");
                String description = getFirstValue(formData, "description");
                String contactEmail = getFirstValue(formData, "contactEmail");
                String capacityText = getFirstValue(formData, "rsvpCapacity");
                Integer rsvpCapacity = capacityText.isBlank() ? null : parseIntOrZero(capacityText);

                eventRepository.addEvent(title, clubId, date, location, description, contactEmail, rsvpCapacity);
                sendRedirect(exchange, "/events");
                return;
            }

            if (ownedClubs.isEmpty()) {
                sendHtml(exchange, wrapPage("Add an Event", currentUser,
                        "<h1>Add an Event</h1><div class='card'><p>You need to create a club first before adding an event.</p>" +
                                "<a href=\"/add-club\">Add a Club</a></div>"));
                return;
            }

            StringBuilder options = new StringBuilder();
            for (Club club : ownedClubs) {
                options.append("<option value='").append(club.getId()).append("'>").append(club.getName()).append("</option>");
            }

            String form = "<div class='card'>"
                    + "<form action='/add-event' method='post'>"
                    + "<label>Club:</label><br><select name='clubId'>" + options + "</select><br><br>"
                    + "<label>Event title:</label><br><input type='text' name='title' required><br><br>"
                    + "<label>Date:</label><br><input type='text' name='date' placeholder='e.g. August 10, 2026' required><br><br>"
                    + "<label>Location:</label><br><input type='text' name='location' required><br><br>"
                    + "<label>Description:</label><br><textarea name='description' rows='3' required></textarea><br><br>"
                    + "<label>Contact email:</label><br><input type='email' name='contactEmail' required><br><br>"
                    + "<label>RSVP capacity (optional - leave blank for no limit):</label><br>"
                    + "<input type='number' name='rsvpCapacity' min='1'><br><br>"
                    + "<p style='color:#6b7280; font-size: 0.9em;'>Picture upload is coming soon.</p>"
                    + "<button type='submit'>Add Event</button>"
                    + "</form>"
                    + "</div>";

            String html = wrapPage("Add an Event", currentUser, "<h1>Add an Event</h1>" + form);
            sendHtml(exchange, html);
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    private void handleRsvp(HttpExchange exchange) throws IOException {
        try {
            User currentUser = getCurrentUser(exchange);
            if (currentUser == null) {
                sendRedirect(exchange, "/login");
                return;
            }
            Map<String, List<String>> formData = parseFormData(readRequestBody(exchange));
            int eventId = parseIntOrZero(getFirstValue(formData, "eventId"));
            // The result isn't shown as a separate message page - the event card on /events
            // already reflects whatever happened (You're going / Event Full / RSVP button).
            eventRepository.rsvp(eventId, currentUser.getUsername());
            sendRedirect(exchange, "/events");
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    private void handleEventsPage(HttpExchange exchange) throws IOException {
        try {
            User currentUser = getCurrentUser(exchange);
            List<Event> events = eventRepository.getAllEvents();

            StringBuilder html = new StringBuilder();
            for (Event event : events) {
                html.append(buildEventCard(event, currentUser));
            }

            String page = wrapPage("Events", currentUser, "<h1>Events</h1>" + html);
            sendHtml(exchange, page);
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    private String buildEventCard(Event event, User currentUser) throws SQLException {
        int rsvpCount = eventRepository.getRsvpCount(event.getId());
        boolean isFull = event.hasCapacity() && rsvpCount >= event.getRsvpCapacity();

        StringBuilder card = new StringBuilder();
        card.append("<div class='card'>")
                .append("<h2>").append(event.getTitle()).append("</h2>")
                .append("<p>Club: ").append(event.getClubName() == null ? "Unknown" : event.getClubName()).append("</p>")
                .append("<p>Date: ").append(event.getDate()).append("</p>")
                .append("<p>Location: ").append(event.getLocation()).append("</p>")
                .append("<p>Description: ").append(event.getDescription()).append("</p>");

        if (event.getContactEmail() != null && !event.getContactEmail().isBlank()) {
            card.append("<p>Contact: ").append(event.getContactEmail()).append("</p>");
        }

        if (event.hasCapacity()) {
            card.append("<p>RSVPs: ").append(rsvpCount).append(" / ").append(event.getRsvpCapacity()).append("</p>");
        } else {
            card.append("<p>RSVP Count: ").append(rsvpCount).append("</p>");
        }

        if (currentUser == null) {
            card.append("<p><a href=\"/login\">Log in to RSVP</a></p>");
        } else if (eventRepository.hasUserRsvped(event.getId(), currentUser.getUsername())) {
            card.append("<p>You're going!</p>");
        } else if (isFull) {
            card.append("<p>This event is full.</p>");
        } else {
            card.append("<form action='/rsvp' method='post' onsubmit=\"return confirm('RSVP to this event?');\">")
                    .append("<input type='hidden' name='eventId' value='").append(event.getId()).append("'>")
                    .append("<button type='submit'>RSVP</button>")
                    .append("</form>");
        }

        card.append("</div>");
        return card.toString();
    }

    // ===================== HOME / CLUBS / SEARCH / CATEGORIES =====================

    private void handleHomePage(HttpExchange exchange) throws IOException {
        try {
            User currentUser = getCurrentUser(exchange);
            int clubCount = clubRepository.getAllClubs().size();
            int eventCount = eventRepository.getAllEvents().size();

            String body = "<div class=\"card\">"
                    + "<h1>UTA Club Connect</h1>"
                    + "<p>Find clubs, view events, and connect with student organizations at UTA.</p>"
                    + "<p>Total Clubs: " + clubCount + "</p>"
                    + "<p>Total Events: " + eventCount + "</p>"
                    + "<a href=\"/clubs\">View Clubs</a>"
                    + "<a href=\"/events\">View Events</a>"
                    + "</div>";

            sendHtml(exchange, wrapPage("UTA Club Connect", currentUser, body));
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    private void handleClubsPage(HttpExchange exchange) throws IOException {
        try {
            User currentUser = getCurrentUser(exchange);
            List<Club> clubs = clubRepository.getAllClubs();

            StringBuilder html = new StringBuilder();
            for (Club club : clubs) {
                html.append(buildClubCard(club, currentUser));
            }

            sendHtml(exchange, wrapPage("Clubs", currentUser, "<h1>Clubs</h1>" + html));
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    private void handleCategoriesPage(HttpExchange exchange) throws IOException {
        try {
            User currentUser = getCurrentUser(exchange);
            String query = exchange.getRequestURI().getQuery();
            String selectedTag = "";

            if (query != null) {
                for (String param : query.split("&")) {
                    if (param.startsWith("category=")) {
                        selectedTag = URLDecoder.decode(param.substring(9), StandardCharsets.UTF_8);
                    }
                }
            }

            StringBuilder options = new StringBuilder();
            options.append("<option value=''>-- Select a category --</option>");
            for (String tag : Tags.ALL_TAGS) {
                String selectedAttr = tag.equals(selectedTag) ? " selected" : "";
                options.append("<option value='").append(tag).append("'").append(selectedAttr)
                        .append(">").append(tag).append("</option>");
            }

            String categoryForm = "<div class='card'>"
                    + "<form action='/categories' method='get'>"
                    + "<label>Choose a category: </label>"
                    + "<select name='category'>" + options + "</select>"
                    + "<button type='submit'>Filter</button>"
                    + "</form>"
                    + "</div>";

            String resultsHtml = "";
            if (!selectedTag.isEmpty()) {
                boolean found = false;
                StringBuilder results = new StringBuilder();
                for (Club club : clubRepository.getAllClubs()) {
                    if (club.hasCategory(selectedTag)) {
                        results.append(buildClubCard(club, currentUser));
                        found = true;
                    }
                }
                resultsHtml = found ? results.toString() : "<p>No clubs found in that category.</p>";
            }

            sendHtml(exchange, wrapPage("Filter Clubs", currentUser,
                    "<h1>Filter Clubs by Category</h1>" + categoryForm + resultsHtml));
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    private void handleSearchPage(HttpExchange exchange) throws IOException {
        try {
            User currentUser = getCurrentUser(exchange);
            String query = exchange.getRequestURI().getQuery();
            String searchText = "";
            if (query != null) {
                for (String param : query.split("&")) {
                    if (param.startsWith("clubName=")) {
                        searchText = URLDecoder.decode(param.substring(9), StandardCharsets.UTF_8);
                    }
                }
            }

            String resultsHtml = "";
            if (!searchText.isEmpty()) {
                boolean found = false;
                StringBuilder results = new StringBuilder();
                for (Club club : clubRepository.getAllClubs()) {
                    if (club.getName().toLowerCase().contains(searchText.toLowerCase())) {
                        results.append(buildClubCard(club, currentUser));
                        found = true;
                    }
                }
                resultsHtml = found ? results.toString() : "<p>No club found with that name.</p>";
            }

            String searchForm = "<div class='card'>"
                    + "<form action='/search' method='get'>"
                    + "<label>Enter club name: </label>"
                    + "<input type='text' name='clubName' value='" + searchText + "'>"
                    + "<button type='submit'>Search</button>"
                    + "</form>"
                    + "</div>";

            sendHtml(exchange, wrapPage("Search Clubs", currentUser, "<h1>Search Clubs</h1>" + searchForm + resultsHtml));
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    // Builds one club "card", with Edit/Delete links shown only if allowed
    private String buildClubCard(Club club, User currentUser) throws SQLException {
        boolean canManage = currentUser != null &&
                (currentUser.isAtLeast(User.LEVEL_UTA_STAFF) || club.isOwnedBy(currentUser.getUsername()));
        boolean isOwner = currentUser != null && club.isOwnedBy(currentUser.getUsername());

        StringBuilder card = new StringBuilder();
        card.append("<div class='card'>")
                .append("<h2><a href=\"/club?id=").append(club.getId()).append("\">").append(club.getName()).append("</a></h2>")
                .append("<p>Category: ").append(club.getCategoriesText()).append("</p>")
                .append("<p>Description: ").append(club.getDescription()).append("</p>");

        if (club.getMeetingTime() != null && !club.getMeetingTime().isBlank()) {
            card.append("<p>Meeting time: ").append(club.getMeetingTime()).append("</p>");
        }
        card.append("<p>Members: ").append(club.getMembers()).append("</p>");

        // How to join depends on whether the president turned on auto-approve
        if (club.isAutoApproveMembers()) {
            if (currentUser == null) {
                card.append("<p><a href=\"/login\">Log in to join</a></p>");
            } else if (isOwner) {
                // owners don't need to join their own club
            } else if (clubRepository.hasUserJoined(club.getId(), currentUser.getUsername())) {
                card.append("<p>You're a member!</p>");
            } else {
                card.append("<form action='/join-club' method='post'>")
                        .append("<input type='hidden' name='clubId' value='").append(club.getId()).append("'>")
                        .append("<button type='submit'>Join</button>")
                        .append("</form>");
            }
        } else {
            card.append("<p>To join, contact: ").append(club.getContactEmail()).append("</p>");
        }

        if (canManage) {
            card.append("<a href=\"/edit-club?id=").append(club.getId()).append("\">Edit</a> ");
            card.append("<form style='display:inline' action='/delete-club' method='post' " +
                            "onsubmit=\"return confirm('Delete this club?');\">")
                    .append("<input type='hidden' name='id' value='").append(club.getId()).append("'>")
                    .append("<button type='submit'>Delete</button>")
                    .append("</form>");
        }

        card.append("</div>");
        return card.toString();
    }

    // ===================== SHARED HELPERS =====================

    private int getIntParam(HttpExchange exchange, String name, int defaultValue) {
        String query = exchange.getRequestURI().getQuery();
        if (query == null) {
            return defaultValue;
        }
        for (String param : query.split("&")) {
            if (param.startsWith(name + "=")) {
                try {
                    return Integer.parseInt(URLDecoder.decode(param.substring(name.length() + 1), StandardCharsets.UTF_8));
                } catch (NumberFormatException e) {
                    return defaultValue;
                }
            }
        }
        return defaultValue;
    }

    private int parseIntOrZero(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        InputStream requestStream = exchange.getRequestBody();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[1024];
        int bytesRead;
        while ((bytesRead = requestStream.read(chunk)) != -1) {
            buffer.write(chunk, 0, bytesRead);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private Map<String, List<String>> parseFormData(String body) {
        Map<String, List<String>> result = new HashMap<>();
        if (body == null || body.isEmpty()) {
            return result;
        }
        for (String pair : body.split("&")) {
            String[] keyValue = pair.split("=", 2);
            String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
            String value = keyValue.length > 1 ? URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8) : "";
            result.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
        return result;
    }

    private String getFirstValue(Map<String, List<String>> formData, String key) {
        List<String> values = formData.get(key);
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.get(0);
    }

    private void sendHtml(HttpExchange exchange, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        OutputStream output = exchange.getResponseBody();
        output.write(bytes);
        output.close();
    }

    private String forbiddenPage(User currentUser) {
        return wrapPage("Not Allowed", currentUser,
                "<div class='card'><p>You don't have permission to do that.</p></div>");
    }

    // Wraps page content with the shared styles + navigation bar
    private String wrapPage(String title, User currentUser, String bodyContent) {
        return "<html><head><title>" + title + "</title>" + getStyles() + "</head><body>"
                + getNavigation(currentUser) + bodyContent + "</body></html>";
    }

    private String getStyles() {
        return """
                <style>
                    body {
                        font-family: Arial, sans-serif;
                        background-color: #f4f6f8;
                        color: #1f2937;
                        margin: 40px;
                    }
                
                    a {
                        display: inline-block;
                        margin: 8px 0;
                        color: white;
                        background-color: #0064b1;
                        padding: 10px 14px;
                        text-decoration: none;
                        border-radius: 6px;
                    }
                
                    .card {
                        background-color: white;
                        padding: 16px;
                        margin: 16px 0;
                        border-radius: 8px;
                        border: 1px solid #d5dce5;
                    }
                    
                    nav {
                        background-color: #0f2f57;
                        padding: 12px 16px;
                        border-radius: 8px;
                        margin-bottom: 24px;
                    }
                
                    nav a {
                        background-color: transparent;
                        padding: 8px 10px;
                        margin-right: 8px;
                    }

                    button {
                        background-color: #0064b1;
                        color: white;
                        border: none;
                        padding: 8px 12px;
                        border-radius: 6px;
                        cursor: pointer;
                    }
                </style>
                """;
    }

    private String getNavigation(User currentUser) {
        StringBuilder nav = new StringBuilder("<nav>");
        nav.append("<a href=\"/\">Home</a>");
        nav.append("<a href=\"/clubs\">Clubs</a>");
        nav.append("<a href=\"/events\">Events</a>");
        nav.append("<a href=\"/search\">Search</a>");
        nav.append("<a href=\"/categories\">Categories</a>");

        if (currentUser != null && currentUser.isAtLeast(User.LEVEL_CLUB_PRESIDENT)) {
            nav.append("<a href=\"/add-club\">Add a Club</a>");
            nav.append("<a href=\"/add-event\">Add an Event</a>");
        }
        if (currentUser != null && currentUser.isAtLeast(User.LEVEL_UTA_STAFF)) {
            nav.append("<a href=\"/promote\">Promote Users</a>");
        }

        if (currentUser == null) {
            nav.append("<a href=\"/login\">Login</a>");
            nav.append("<a href=\"/register\">Register</a>");
        } else {
            nav.append("<a href=\"/logout\">Logout (").append(currentUser.getUsername())
                    .append(" - ").append(currentUser.getRoleName()).append(")</a>");
        }

        nav.append("</nav>");
        return nav.toString();
    }
}
