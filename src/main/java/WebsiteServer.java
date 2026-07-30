import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

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
        server.createContext("/dashboard", this::handleDashboardPage);
        server.createContext("/add-club", this::handleAddClubPage);
        server.createContext("/edit-club", this::handleEditClubPage);
        server.createContext("/delete-club", this::handleDeleteClub);
        server.createContext("/join-club", this::handleJoinClub);
        server.createContext("/leave-club", this::handleLeaveClub);
        server.createContext("/join-requests", this::handleJoinRequestsPage);
        server.createContext("/approve-request", this::handleApproveRequest);
        server.createContext("/deny-request", this::handleDenyRequest);
        server.createContext("/add-event", this::handleAddEventPage);
        server.createContext("/event-image", this::handleEventImagePage);
        server.createContext("/rsvp", this::handleRsvp);
        server.createContext("/login", this::handleLoginPage);
        server.createContext("/register", this::handleRegisterPage);
        server.createContext("/logout", this::handleLogout);
        server.createContext("/promote", this::handlePromotePage);
        server.createContext("/assign-leader", this::handleAssignLeaderPage);
        server.createContext("/site-settings", this::handleSiteSettingsPage);
    }

    public void start() {
        server.start();
        System.out.println("Website running at http://localhost:8080");
    }

    private String getCookieValue(HttpExchange exchange, String name) {
        List<String> cookieHeaders = exchange.getRequestHeaders().get("Cookie");
        if (cookieHeaders == null) return null;
        for (String header : cookieHeaders) {
            for (String part : header.split(";")) {
                String[] pieces = part.trim().split("=", 2);
                if (pieces.length == 2 && pieces[0].equals(name)) return pieces[1];
            }
        }
        return null;
    }

    private User getCurrentUser(HttpExchange exchange) throws SQLException {
        String token = getCookieValue(exchange, "session");
        String username = sessionManager.getUsername(token);
        if (username == null) return null;
        return userRepository.findByUsername(username);
    }

    private void sendRedirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.getResponseBody().close();
    }

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
                    sendRedirect(exchange, "/dashboard");
                    return;
                }
            } catch (SQLException e) {
                throw new IOException(e);
            }
            sendHtml(exchange, wrapPage("Login", null, "<h1>Login</h1><div class='card'><p class='error'>Incorrect username or password.</p>" + loginForm() + "</div>"));
            return;
        }
        sendHtml(exchange, wrapPage("Login", null, "<h1>Login</h1>" + loginForm()));
    }

    private String loginForm() {
        return "<div class='card auth-card'>"
                + "<form action='/login' method='post'>"
                + "<label>Username</label><input type='text' name='username' required>"
                + "<label>Password</label><input type='password' name='password' required>"
                + "<button type='submit'>Login</button>"
                + "</form><p>No account? <a href='/register'>Register here</a></p></div>";
    }

    private void handleRegisterPage(HttpExchange exchange) throws IOException {
        if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            Map<String, List<String>> formData = parseFormData(readRequestBody(exchange));
            String username = getFirstValue(formData, "username");
            String password = getFirstValue(formData, "password");
            try {
                if (username.isEmpty() || password.isEmpty()) {
                    sendHtml(exchange, wrapPage("Register", null, "<h1>Register</h1><p class='error'>Username and password are required.</p>" + registerForm()));
                    return;
                }
                if (userRepository.usernameExists(username)) {
                    sendHtml(exchange, wrapPage("Register", null, "<h1>Register</h1><p class='error'>That username is already taken.</p>" + registerForm()));
                    return;
                }
                userRepository.createUser(username, password, User.LEVEL_STUDENT);
                String token = sessionManager.createSession(username);
                exchange.getResponseHeaders().add("Set-Cookie", "session=" + token + "; Path=/");
                sendRedirect(exchange, "/dashboard");
            } catch (SQLException e) {
                throw new IOException(e);
            }
            return;
        }
        sendHtml(exchange, wrapPage("Register", null, "<h1>Create Account</h1>" + registerForm()));
    }

    private String registerForm() {
        return "<div class='card auth-card'>"
                + "<form action='/register' method='post'>"
                + "<label>Choose a username</label><input type='text' name='username' required>"
                + "<label>Choose a password</label><input type='password' name='password' required>"
                + "<button type='submit'>Create Account</button>"
                + "</form></div>";
    }

    private void handleLogout(HttpExchange exchange) throws IOException {
        String token = getCookieValue(exchange, "session");
        sessionManager.invalidate(token);
        exchange.getResponseHeaders().add("Set-Cookie", "session=; Path=/; Max-Age=0");
        sendRedirect(exchange, "/");
    }

    private void handleDashboardPage(HttpExchange exchange) throws IOException {
        try {
            User currentUser = getCurrentUser(exchange);
            if (currentUser == null) {
                sendRedirect(exchange, "/login");
                return;
            }

            StringBuilder html = new StringBuilder();
            html.append("<h1>Dashboard</h1>");
            html.append("<div class='card'><h2>Welcome, ").append(escape(currentUser.getUsername())).append("</h2>")
                    .append("<p>Role: ").append(currentUser.getRoleName()).append("</p></div>");

            html.append("<div class='action-grid'>");
            html.append(actionCard("Explore Clubs", "View clubs, meeting dates, and contacts.", "/clubs"));
            html.append(actionCard("Explore Events", "Find upcoming events and RSVP.", "/events"));

            if (currentUser.isAtLeast(User.LEVEL_CLUB_PRESIDENT)) {
                html.append(actionCard("Add Event", "Create an event for a club you lead.", "/add-event"));
                html.append(actionCard("Join Requests", "Approve or deny pending member requests.", "/join-requests"));
            }
            if (currentUser.isAtLeast(User.LEVEL_UTA_STAFF)) {
                html.append(actionCard("Add Club", "Create a new club and assign an owner.", "/add-club"));
                html.append(actionCard("Assign Leader", "Appoint a club owner or leader.", "/assign-leader"));
                html.append(actionCard("Promote Users", "Promote members to club leaders.", "/promote"));
                html.append(actionCard("Home Image", "Change the homepage background image.", "/site-settings"));
            }
            html.append("</div>");

            sendHtml(exchange, wrapPage("Dashboard", currentUser, html.toString()));
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    private String actionCard(String title, String text, String link) {
        return "<a class='action-card' href='" + link + "'><h3>" + title + "</h3><p>" + text + "</p></a>";
    }

    private void handleHomePage(HttpExchange exchange) throws IOException {
        try {
            User currentUser = getCurrentUser(exchange);
            List<Club> clubs = clubRepository.getAllClubs();
            List<Event> events = eventRepository.getAllEvents();
            String heroImage = getHomeHeroImage();

            String heroStyle = heroImage.isBlank()
                    ? ""
                    : " style=\"background-image: linear-gradient(rgba(15, 48, 80, .70), rgba(15, 48, 80, .70)), url('" + escapeAttribute(heroImage) + "');\"";

            StringBuilder body = new StringBuilder();
            body.append("<section class='hero'" + heroStyle + ">")
                    .append("<div class='hero-box'>")
                    .append("<h1>Discover clubs, events, and student life at UTA</h1>")
                    .append("<form class='hero-search' action='/search' method='get'>")
                    .append("<input type='text' name='q' placeholder='Search clubs, events, categories, or meeting dates'>")
                    .append("<button type='submit'>Search</button>")
                    .append("</form>")
                    .append("<p class='tiny-counts'>").append(clubs.size()).append(" clubs • ").append(events.size()).append(" events</p>")
                    .append("</div></section>");

            body.append("<section class='home-section'><div class='section-header'><h2>Explore Clubs</h2><a href='/clubs'>View all</a></div><div class='scroll-row'>");
            for (Club club : clubs) body.append(homeClubCard(club));
            body.append("</div></section>");

            body.append("<section class='home-section'><div class='section-header'><h2>Upcoming Events</h2><a href='/events'>View all</a></div><div class='scroll-row'>");
            for (Event event : events) body.append(homeEventCard(event));
            body.append("</div></section>");

            sendHtml(exchange, wrapPage("UTA Club Connect", currentUser, body.toString()));
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    private String homeClubCard(Club club) {
        String image = club.getImageUrl().isBlank() ? "https://images.unsplash.com/photo-1523580494863-6f3031224c94?auto=format&fit=crop&w=900&q=80" : club.getImageUrl();
        return "<a class='media-card' href='/club?id=" + club.getId() + "'>"
                + "<img src='" + escapeAttribute(image) + "' alt='Club image'>"
                + "<div><h3>" + escape(club.getName()) + "</h3>"
                + "<p>" + escape(club.getCategoriesText()) + "</p>"
                + "<p>Meeting: " + escape(club.getMeetingTime()) + "</p></div></a>";
    }

    private String homeEventCard(Event event) {
        String image = event.getImageUrl().isBlank() ? "https://images.unsplash.com/photo-1517486808906-6ca8b3f04846?auto=format&fit=crop&w=900&q=80" : event.getImageUrl();
        return "<div class='media-card'>"
                + "<img src='" + escapeAttribute(image) + "' alt='Event image'>"
                + "<div><h3>" + escape(event.getTitle()) + "</h3>"
                + "<p>" + escape(event.getClubName()) + "</p>"
                + "<p>" + escape(event.getDate()) + " • " + escape(event.getLocation()) + "</p></div></div>";
    }

    private void handleClubsPage(HttpExchange exchange) throws IOException {
        try {
            User currentUser = getCurrentUser(exchange);
            StringBuilder html = new StringBuilder("<h1>Clubs</h1><div class='grid'>");
            for (Club club : clubRepository.getAllClubs()) html.append(buildClubCard(club, currentUser));
            html.append("</div>");
            sendHtml(exchange, wrapPage("Clubs", currentUser, html.toString()));
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    private void handleClubDetailsPage(HttpExchange exchange) throws IOException {
        try {
            User currentUser = getCurrentUser(exchange);
            int id = getIntParam(exchange, "id", -1);
            Club club = clubRepository.getClubById(id);
            if (club == null) {
                sendHtml(exchange, wrapPage("Club Not Found", currentUser, "<div class='card'><p>That club does not exist.</p></div>"));
                return;
            }
            sendHtml(exchange, wrapPage(club.getName(), currentUser, "<h1>" + escape(club.getName()) + "</h1>" + buildClubCard(club, currentUser)));
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    private String buildClubCard(Club club, User currentUser) throws SQLException {
        boolean isStaff = currentUser != null && currentUser.isAtLeast(User.LEVEL_UTA_STAFF);
        boolean isOwner = currentUser != null && club.isOwnedBy(currentUser.getUsername());
        boolean canManage = isStaff || isOwner;
        String image = club.getImageUrl().isBlank() ? "https://images.unsplash.com/photo-1523580494863-6f3031224c94?auto=format&fit=crop&w=900&q=80" : club.getImageUrl();

        StringBuilder card = new StringBuilder();
        card.append("<div class='card club-card'>")
                .append("<img class='card-img' src='").append(escapeAttribute(image)).append("' alt='Club image'>")
                .append("<h2><a href='/club?id=").append(club.getId()).append("'>").append(escape(club.getName())).append("</a></h2>")
                .append("<p><b>Category:</b> ").append(escape(club.getCategoriesText())).append("</p>")
                .append("<p><b>Meeting:</b> ").append(escape(club.getMeetingTime())).append("</p>")
                .append("<p><b>Contact:</b> ").append(escape(club.getContactEmail())).append("</p>")
                .append("<p>").append(escape(club.getDescription())).append("</p>")
                .append("<p><b>Members:</b> ").append(club.getMembers()).append("</p>");

        if (currentUser == null) {
            card.append("<p><a href='/login'>Log in to join this club</a></p>");
        } else if (isOwner) {
            card.append("<p class='status'>You are the club leader.</p>");
        } else if (clubRepository.hasUserJoined(club.getId(), currentUser.getUsername())) {
            card.append("<p class='status'>You are a member.</p>")
                    .append("<form action='/leave-club' method='post'>")
                    .append("<input type='hidden' name='clubId' value='").append(club.getId()).append("'>")
                    .append("<button type='submit' class='secondary'>Leave Club</button></form>");
        } else {
            String requestStatus = clubRepository.getJoinRequestStatus(club.getId(), currentUser.getUsername());
            if ("pending".equalsIgnoreCase(requestStatus)) {
                card.append("<p class='status'>Join request pending.</p>");
            } else {
                String buttonText = club.isAutoApproveMembers() ? "Join Club" : "Request to Join";
                card.append("<form action='/join-club' method='post'>")
                        .append("<input type='hidden' name='clubId' value='").append(club.getId()).append("'>")
                        .append("<button type='submit'>").append(buttonText).append("</button></form>");
            }
        }

        if (canManage) {
            card.append("<div class='tools'><a href='/edit-club?id=").append(club.getId()).append("'>Edit Club / Picture</a>");
            if (isStaff) {
                card.append("<form style='display:inline' action='/delete-club' method='post' onsubmit=\"return confirm('Delete this club?');\">")
                        .append("<input type='hidden' name='id' value='").append(club.getId()).append("'>")
                        .append("<button class='danger' type='submit'>Delete</button></form>");
            }
            card.append("</div>");
        }
        card.append("</div>");
        return card.toString();
    }

    private void handleEventsPage(HttpExchange exchange) throws IOException {
        try {
            User currentUser = getCurrentUser(exchange);
            StringBuilder html = new StringBuilder("<h1>Events</h1><div class='grid'>");
            for (Event event : eventRepository.getAllEvents()) html.append(buildEventCard(event, currentUser));
            html.append("</div>");
            sendHtml(exchange, wrapPage("Events", currentUser, html.toString()));
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    private String buildEventCard(Event event, User currentUser) throws SQLException {
        int rsvpCount = eventRepository.getRsvpCount(event.getId());
        boolean isFull = event.hasCapacity() && rsvpCount >= event.getRsvpCapacity();
        boolean canEditImage = false;
        if (currentUser != null) {
            Club club = clubRepository.getClubById(event.getClubId());
            canEditImage = currentUser.isAtLeast(User.LEVEL_UTA_STAFF) || (club != null && club.isOwnedBy(currentUser.getUsername()));
        }
        String image = event.getImageUrl().isBlank() ? "https://images.unsplash.com/photo-1517486808906-6ca8b3f04846?auto=format&fit=crop&w=900&q=80" : event.getImageUrl();

        StringBuilder card = new StringBuilder();
        card.append("<div class='card event-card'>")
                .append("<img class='card-img' src='").append(escapeAttribute(image)).append("' alt='Event image'>")
                .append("<h2>").append(escape(event.getTitle())).append("</h2>")
                .append("<p><b>Club:</b> ").append(escape(event.getClubName())).append("</p>")
                .append("<p><b>Date:</b> ").append(escape(event.getDate())).append("</p>")
                .append("<p><b>Location:</b> ").append(escape(event.getLocation())).append("</p>")
                .append("<p>").append(escape(event.getDescription())).append("</p>");

        if (!event.getContactEmail().isBlank()) card.append("<p><b>Contact:</b> ").append(escape(event.getContactEmail())).append("</p>");
        card.append(event.hasCapacity() ? "<p><b>RSVPs:</b> " + rsvpCount + " / " + event.getRsvpCapacity() + "</p>" : "<p><b>RSVP Count:</b> " + rsvpCount + "</p>");

        if (currentUser == null) {
            card.append("<p><a href='/login'>Log in to RSVP</a></p>");
        } else if (eventRepository.hasUserRsvped(event.getId(), currentUser.getUsername())) {
            card.append("<p class='status'>You are going!</p>");
        } else if (isFull) {
            card.append("<p class='status'>This event is full.</p>");
        } else {
            card.append("<form action='/rsvp' method='post'>")
                    .append("<input type='hidden' name='eventId' value='").append(event.getId()).append("'>")
                    .append("<button type='submit'>RSVP</button></form>");
        }

        if (canEditImage) {
            card.append("<div class='tools'><a href='/event-image?id=").append(event.getId()).append("'>Change Event Picture</a></div>");
        }
        card.append("</div>");
        return card.toString();
    }

    private void handleSearchPage(HttpExchange exchange) throws IOException {
        try {
            User currentUser = getCurrentUser(exchange);
            String searchText = getQueryParam(exchange, "q");
            if (searchText.isBlank()) searchText = getQueryParam(exchange, "clubName");

            StringBuilder html = new StringBuilder();
            html.append("<h1>Search</h1><div class='card'><form action='/search' method='get'>")
                    .append("<input type='text' name='q' value='").append(escapeAttribute(searchText)).append("' placeholder='Search clubs, events, categories, or dates'>")
                    .append("<button type='submit'>Search</button></form></div>");

            if (!searchText.isBlank()) {
                String lower = searchText.toLowerCase();
                html.append("<h2>Clubs</h2><div class='grid'>");
                boolean foundClub = false;
                for (Club club : clubRepository.getAllClubs()) {
                    if (club.getName().toLowerCase().contains(lower)
                            || club.getCategoriesText().toLowerCase().contains(lower)
                            || club.getMeetingTime().toLowerCase().contains(lower)
                            || club.getDescription().toLowerCase().contains(lower)) {
                        html.append(buildClubCard(club, currentUser));
                        foundClub = true;
                    }
                }
                if (!foundClub) html.append("<p>No clubs found.</p>");
                html.append("</div><h2>Events</h2><div class='grid'>");
                boolean foundEvent = false;
                for (Event event : eventRepository.getAllEvents()) {
                    if (event.getTitle().toLowerCase().contains(lower)
                            || event.getClubName().toLowerCase().contains(lower)
                            || event.getDate().toLowerCase().contains(lower)
                            || event.getLocation().toLowerCase().contains(lower)
                            || event.getDescription().toLowerCase().contains(lower)) {
                        html.append(buildEventCard(event, currentUser));
                        foundEvent = true;
                    }
                }
                if (!foundEvent) html.append("<p>No events found.</p>");
                html.append("</div>");
            }
            sendHtml(exchange, wrapPage("Search", currentUser, html.toString()));
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    private void handleCategoriesPage(HttpExchange exchange) throws IOException {
        try {
            User currentUser = getCurrentUser(exchange);
            String selectedTag = getQueryParam(exchange, "category");
            StringBuilder options = new StringBuilder("<option value=''>Select category</option>");
            for (String tag : Tags.ALL_TAGS) {
                String selected = tag.equals(selectedTag) ? " selected" : "";
                options.append("<option value='").append(escapeAttribute(tag)).append("'").append(selected).append(">").append(escape(tag)).append("</option>");
            }

            StringBuilder html = new StringBuilder("<h1>Categories</h1><div class='card'><form action='/categories' method='get'>")
                    .append("<select name='category'>").append(options).append("</select><button type='submit'>Filter</button></form></div><div class='grid'>");
            if (!selectedTag.isBlank()) {
                for (Club club : clubRepository.getAllClubs()) {
                    if (club.hasCategory(selectedTag)) html.append(buildClubCard(club, currentUser));
                }
            }
            html.append("</div>");
            sendHtml(exchange, wrapPage("Categories", currentUser, html.toString()));
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    private void handleAddClubPage(HttpExchange exchange) throws IOException {
        try {
            User currentUser = getCurrentUser(exchange);
            if (currentUser == null) { sendRedirect(exchange, "/login"); return; }
            if (!currentUser.isAtLeast(User.LEVEL_UTA_STAFF)) { sendHtml(exchange, forbiddenPage(currentUser)); return; }

            if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                Map<String, List<String>> formData = parseFormData(readRequestBody(exchange));
                int newId = clubRepository.addClub(
                        getFirstValue(formData, "name"),
                        formData.getOrDefault("tags", new ArrayList<>()),
                        parseIntOrZero(getFirstValue(formData, "members")),
                        getFirstValue(formData, "description"),
                        getFirstValue(formData, "contactEmail"),
                        getFirstValue(formData, "meetingTime"),
                        getFirstValue(formData, "ownerUsername"),
                        formData.containsKey("autoApprove"),
                        getFirstValue(formData, "imageUrl")
                );
                String owner = getFirstValue(formData, "ownerUsername");
                if (!owner.isBlank()) userRepository.promoteToClubPresident(owner);
                sendRedirect(exchange, "/club?id=" + newId);
                return;
            }
            sendHtml(exchange, wrapPage("Add Club", currentUser, "<h1>Add Club</h1>" + clubForm("/add-club", "Add Club", null, new ArrayList<>(), true)));
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    private void handleEditClubPage(HttpExchange exchange) throws IOException {
        try {
            User currentUser = getCurrentUser(exchange);
            if (currentUser == null) { sendRedirect(exchange, "/login"); return; }
            boolean isStaff = currentUser.isAtLeast(User.LEVEL_UTA_STAFF);

            if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                Map<String, List<String>> formData = parseFormData(readRequestBody(exchange));
                int id = parseIntOrZero(getFirstValue(formData, "id"));
                boolean updated = clubRepository.updateClub(
                        id,
                        getFirstValue(formData, "name"),
                        formData.getOrDefault("tags", new ArrayList<>()),
                        parseIntOrZero(getFirstValue(formData, "members")),
                        getFirstValue(formData, "description"),
                        getFirstValue(formData, "contactEmail"),
                        getFirstValue(formData, "meetingTime"),
                        formData.containsKey("autoApprove"),
                        getFirstValue(formData, "imageUrl"),
                        currentUser.getUsername(),
                        isStaff
                );
                if (!updated) { sendHtml(exchange, forbiddenPage(currentUser)); return; }
                sendRedirect(exchange, "/club?id=" + id);
                return;
            }

            int id = getIntParam(exchange, "id", -1);
            Club club = clubRepository.getClubById(id);
            if (club == null) { sendHtml(exchange, wrapPage("Not Found", currentUser, "<p>Club not found.</p>")); return; }
            if (!isStaff && !club.isOwnedBy(currentUser.getUsername())) { sendHtml(exchange, forbiddenPage(currentUser)); return; }
            sendHtml(exchange, wrapPage("Edit Club", currentUser, "<h1>Edit Club</h1>" + clubForm("/edit-club", "Save Changes", club, club.getCategories(), false)));
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    private String clubForm(String action, String buttonLabel, Club existingClub, List<String> selectedTags, boolean includeOwner) {
        StringBuilder checkboxes = new StringBuilder();
        for (String tag : Tags.ALL_TAGS) {
            String checked = selectedTags.contains(tag) ? " checked" : "";
            checkboxes.append("<label class='check'><input type='checkbox' name='tags' value='").append(escapeAttribute(tag)).append("'").append(checked).append("> ").append(escape(tag)).append("</label>");
        }
        String idField = existingClub != null ? "<input type='hidden' name='id' value='" + existingClub.getId() + "'>" : "";
        String name = existingClub != null ? existingClub.getName() : "";
        String description = existingClub != null ? existingClub.getDescription() : "";
        String contactEmail = existingClub != null ? existingClub.getContactEmail() : "";
        String meetingTime = existingClub != null ? existingClub.getMeetingTime() : "";
        int members = existingClub != null ? existingClub.getMembers() : 0;
        String imageUrl = existingClub != null ? existingClub.getImageUrl() : "";
        String autoApproveChecked = existingClub != null && existingClub.isAutoApproveMembers() ? " checked" : "";
        String ownerField = includeOwner ? "<label>Club leader username</label><input type='text' name='ownerUsername' placeholder='username'>" : "";

        return "<div class='card form-card'><form action='" + action + "' method='post'>"
                + idField
                + "<label>Club name</label><input type='text' name='name' value='" + escapeAttribute(name) + "' required>"
                + ownerField
                + "<label>Categories</label><div class='checks'>" + checkboxes + "</div>"
                + "<label>Description</label><textarea name='description' rows='3' required>" + escape(description) + "</textarea>"
                + "<label>Meeting time</label><input type='text' name='meetingTime' value='" + escapeAttribute(meetingTime) + "'>"
                + "<label>Members</label><input type='number' name='members' value='" + members + "' min='0'>"
                + "<label>Contact email</label><input type='email' name='contactEmail' value='" + escapeAttribute(contactEmail) + "' required>"
                + "<label>Club picture URL</label><input type='url' name='imageUrl' value='" + escapeAttribute(imageUrl) + "' placeholder='Paste image link here'>"
                + "<label class='check'><input type='checkbox' name='autoApprove'" + autoApproveChecked + "> Auto-approve join requests</label>"
                + "<button type='submit'>" + buttonLabel + "</button></form></div>";
    }

    private void handleDeleteClub(HttpExchange exchange) throws IOException {
        try {
            User currentUser = getCurrentUser(exchange);
            if (currentUser == null) { sendRedirect(exchange, "/login"); return; }
            int id = parseIntOrZero(getFirstValue(parseFormData(readRequestBody(exchange)), "id"));
            boolean deleted = clubRepository.deleteClub(id, currentUser.getUsername(), currentUser.isAtLeast(User.LEVEL_UTA_STAFF));
            if (!deleted) { sendHtml(exchange, forbiddenPage(currentUser)); return; }
            sendRedirect(exchange, "/clubs");
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    private void handleJoinClub(HttpExchange exchange) throws IOException {
        try {
            User currentUser = getCurrentUser(exchange);
            if (currentUser == null) { sendRedirect(exchange, "/login"); return; }
            int clubId = parseIntOrZero(getFirstValue(parseFormData(readRequestBody(exchange)), "clubId"));
            clubRepository.requestToJoin(clubId, currentUser.getUsername());
            sendRedirect(exchange, "/club?id=" + clubId);
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    private void handleLeaveClub(HttpExchange exchange) throws IOException {
        try {
            User currentUser = getCurrentUser(exchange);
            if (currentUser == null) { sendRedirect(exchange, "/login"); return; }
            int clubId = parseIntOrZero(getFirstValue(parseFormData(readRequestBody(exchange)), "clubId"));
            clubRepository.leaveClub(clubId, currentUser.getUsername());
            sendRedirect(exchange, "/club?id=" + clubId);
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    private void handleJoinRequestsPage(HttpExchange exchange) throws IOException {
        try {
            User currentUser = getCurrentUser(exchange);
            if (currentUser == null) { sendRedirect(exchange, "/login"); return; }
            if (!currentUser.isAtLeast(User.LEVEL_CLUB_PRESIDENT)) { sendHtml(exchange, forbiddenPage(currentUser)); return; }

            List<ClubRepository.JoinRequest> requests = clubRepository.getJoinRequestsForUser(currentUser.getUsername(), currentUser.isAtLeast(User.LEVEL_UTA_STAFF));
            StringBuilder html = new StringBuilder("<h1>Join Requests</h1>");
            if (requests.isEmpty()) html.append("<div class='card'><p>No pending requests.</p></div>");
            for (ClubRepository.JoinRequest request : requests) {
                html.append("<div class='card'><h2>").append(escape(request.getClubName())).append("</h2>")
                        .append("<p>User: ").append(escape(request.getUsername())).append("</p>")
                        .append("<form style='display:inline' action='/approve-request' method='post'><input type='hidden' name='requestId' value='").append(escapeAttribute(request.getRequestId())).append("'><button type='submit'>Approve</button></form> ")
                        .append("<form style='display:inline' action='/deny-request' method='post'><input type='hidden' name='requestId' value='").append(escapeAttribute(request.getRequestId())).append("'><button class='danger' type='submit'>Deny</button></form></div>");
            }
            sendHtml(exchange, wrapPage("Join Requests", currentUser, html.toString()));
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    private void handleApproveRequest(HttpExchange exchange) throws IOException {
        handleRequestDecision(exchange, true);
    }

    private void handleDenyRequest(HttpExchange exchange) throws IOException {
        handleRequestDecision(exchange, false);
    }

    private void handleRequestDecision(HttpExchange exchange, boolean approve) throws IOException {
        try {
            User currentUser = getCurrentUser(exchange);
            if (currentUser == null) { sendRedirect(exchange, "/login"); return; }
            String requestId = getFirstValue(parseFormData(readRequestBody(exchange)), "requestId");
            if (approve) clubRepository.approveJoinRequest(requestId, currentUser.getUsername(), currentUser.isAtLeast(User.LEVEL_UTA_STAFF));
            else clubRepository.denyJoinRequest(requestId, currentUser.getUsername(), currentUser.isAtLeast(User.LEVEL_UTA_STAFF));
            sendRedirect(exchange, "/join-requests");
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    private void handleAddEventPage(HttpExchange exchange) throws IOException {
        try {
            User currentUser = getCurrentUser(exchange);
            if (currentUser == null) { sendRedirect(exchange, "/login"); return; }
            if (!currentUser.isAtLeast(User.LEVEL_CLUB_PRESIDENT)) { sendHtml(exchange, forbiddenPage(currentUser)); return; }

            boolean isStaff = currentUser.isAtLeast(User.LEVEL_UTA_STAFF);
            List<Club> ownedClubs = isStaff ? clubRepository.getAllClubs() : clubRepository.getClubsOwnedBy(currentUser.getUsername());

            if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                Map<String, List<String>> formData = parseFormData(readRequestBody(exchange));
                int clubId = parseIntOrZero(getFirstValue(formData, "clubId"));
                boolean allowed = isStaff || ownedClubs.stream().anyMatch(c -> c.getId() == clubId);
                if (!allowed) { sendHtml(exchange, forbiddenPage(currentUser)); return; }

                eventRepository.addEvent(
                        getFirstValue(formData, "title"),
                        clubId,
                        getFirstValue(formData, "date"),
                        getFirstValue(formData, "location"),
                        getFirstValue(formData, "description"),
                        getFirstValue(formData, "contactEmail"),
                        getFirstValue(formData, "rsvpCapacity").isBlank() ? null : parseIntOrZero(getFirstValue(formData, "rsvpCapacity")),
                        getFirstValue(formData, "imageUrl")
                );
                sendRedirect(exchange, "/events");
                return;
            }

            if (ownedClubs.isEmpty()) {
                sendHtml(exchange, wrapPage("Add Event", currentUser, "<h1>Add Event</h1><div class='card'><p>You do not lead any clubs yet.</p></div>"));
                return;
            }

            StringBuilder options = new StringBuilder();
            for (Club club : ownedClubs) options.append("<option value='").append(club.getId()).append("'>").append(escape(club.getName())).append("</option>");
            String form = "<div class='card form-card'><form action='/add-event' method='post'>"
                    + "<label>Club</label><select name='clubId'>" + options + "</select>"
                    + "<label>Event title</label><input type='text' name='title' required>"
                    + "<label>Date</label><input type='text' name='date' placeholder='August 10, 2026' required>"
                    + "<label>Location</label><input type='text' name='location' required>"
                    + "<label>Description</label><textarea name='description' rows='3' required></textarea>"
                    + "<label>Contact email</label><input type='email' name='contactEmail' required>"
                    + "<label>RSVP capacity</label><input type='number' name='rsvpCapacity' min='1' placeholder='optional'>"
                    + "<label>Event picture URL</label><input type='url' name='imageUrl' placeholder='Paste image link here'>"
                    + "<button type='submit'>Add Event</button></form></div>";
            sendHtml(exchange, wrapPage("Add Event", currentUser, "<h1>Add Event</h1>" + form));
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    private void handleEventImagePage(HttpExchange exchange) throws IOException {
        try {
            User currentUser = getCurrentUser(exchange);
            if (currentUser == null) { sendRedirect(exchange, "/login"); return; }
            boolean isStaff = currentUser.isAtLeast(User.LEVEL_UTA_STAFF);
            if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                Map<String, List<String>> formData = parseFormData(readRequestBody(exchange));
                int id = parseIntOrZero(getFirstValue(formData, "id"));
                boolean updated = eventRepository.updateEventImage(id, getFirstValue(formData, "imageUrl"), currentUser.getUsername(), isStaff);
                if (!updated) { sendHtml(exchange, forbiddenPage(currentUser)); return; }
                sendRedirect(exchange, "/events");
                return;
            }
            int id = getIntParam(exchange, "id", -1);
            Event event = eventRepository.getEventById(id);
            if (event == null) { sendHtml(exchange, wrapPage("Not Found", currentUser, "<p>Event not found.</p>")); return; }
            String form = "<div class='card form-card'><form action='/event-image' method='post'>"
                    + "<input type='hidden' name='id' value='" + event.getId() + "'>"
                    + "<label>Event picture URL</label><input type='url' name='imageUrl' value='" + escapeAttribute(event.getImageUrl()) + "'>"
                    + "<button type='submit'>Save Picture</button></form></div>";
            sendHtml(exchange, wrapPage("Change Event Picture", currentUser, "<h1>Change Event Picture</h1>" + form));
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    private void handleRsvp(HttpExchange exchange) throws IOException {
        try {
            User currentUser = getCurrentUser(exchange);
            if (currentUser == null) { sendRedirect(exchange, "/login"); return; }
            int eventId = parseIntOrZero(getFirstValue(parseFormData(readRequestBody(exchange)), "eventId"));
            eventRepository.rsvp(eventId, currentUser.getUsername());
            sendRedirect(exchange, "/events");
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    private void handlePromotePage(HttpExchange exchange) throws IOException {
        try {
            User currentUser = getCurrentUser(exchange);
            if (currentUser == null) { sendRedirect(exchange, "/login"); return; }
            if (!currentUser.isAtLeast(User.LEVEL_UTA_STAFF)) { sendHtml(exchange, forbiddenPage(currentUser)); return; }
            if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                String username = getFirstValue(parseFormData(readRequestBody(exchange)), "username");
                userRepository.promoteToClubPresident(username);
                sendRedirect(exchange, "/promote");
                return;
            }
            StringBuilder html = new StringBuilder("<h1>Promote Members</h1>");
            for (User student : userRepository.findAllStudents()) {
                html.append("<div class='card'><p>").append(escape(student.getUsername())).append("</p>")
                        .append("<form action='/promote' method='post'><input type='hidden' name='username' value='").append(escapeAttribute(student.getUsername())).append("'>")
                        .append("<button type='submit'>Promote to Club Leader</button></form></div>");
            }
            sendHtml(exchange, wrapPage("Promote", currentUser, html.toString()));
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    private void handleAssignLeaderPage(HttpExchange exchange) throws IOException {
        try {
            User currentUser = getCurrentUser(exchange);
            if (currentUser == null) { sendRedirect(exchange, "/login"); return; }
            if (!currentUser.isAtLeast(User.LEVEL_UTA_STAFF)) { sendHtml(exchange, forbiddenPage(currentUser)); return; }
            if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                Map<String, List<String>> formData = parseFormData(readRequestBody(exchange));
                int clubId = parseIntOrZero(getFirstValue(formData, "clubId"));
                String username = getFirstValue(formData, "username");
                clubRepository.assignClubOwner(clubId, username);
                userRepository.promoteToClubPresident(username);
                sendRedirect(exchange, "/assign-leader");
                return;
            }
            StringBuilder options = new StringBuilder();
            for (Club club : clubRepository.getAllClubs()) options.append("<option value='").append(club.getId()).append("'>").append(escape(club.getName())).append("</option>");
            String form = "<h1>Assign Club Leader</h1><div class='card form-card'><form action='/assign-leader' method='post'>"
                    + "<label>Club</label><select name='clubId'>" + options + "</select>"
                    + "<label>Leader username</label><input type='text' name='username' required>"
                    + "<button type='submit'>Assign Leader</button></form></div>";
            sendHtml(exchange, wrapPage("Assign Leader", currentUser, form));
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    private void handleSiteSettingsPage(HttpExchange exchange) throws IOException {
        try {
            User currentUser = getCurrentUser(exchange);
            if (currentUser == null) { sendRedirect(exchange, "/login"); return; }
            if (!currentUser.isAtLeast(User.LEVEL_UTA_STAFF)) { sendHtml(exchange, forbiddenPage(currentUser)); return; }
            if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                String imageUrl = getFirstValue(parseFormData(readRequestBody(exchange)), "heroImageUrl");
                saveHomeHeroImage(imageUrl);
                sendRedirect(exchange, "/");
                return;
            }
            String form = "<h1>Home Page Background</h1><div class='card form-card'><form action='/site-settings' method='post'>"
                    + "<label>Background image URL</label><input type='url' name='heroImageUrl' value='" + escapeAttribute(getHomeHeroImage()) + "' placeholder='Paste image link here'>"
                    + "<button type='submit'>Save Image</button></form></div>";
            sendHtml(exchange, wrapPage("Site Settings", currentUser, form));
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    private String getHomeHeroImage() {
        try {
            Firestore db = FirebaseService.getDatabase();
            DocumentSnapshot doc = db.collection("site_settings").document("home").get().get();
            if (!doc.exists()) return "";
            String image = doc.getString("heroImageUrl");
            return image == null ? "" : image;
        } catch (Exception e) {
            return "";
        }
    }

    private void saveHomeHeroImage(String imageUrl) throws SQLException {
        try {
            Firestore db = FirebaseService.getDatabase();
            Map<String, Object> data = new HashMap<>();
            data.put("heroImageUrl", imageUrl == null ? "" : imageUrl);
            db.collection("site_settings").document("home").set(data).get();
        } catch (Exception e) {
            throw new SQLException("Could not save site settings.", e);
        }
    }

    private String forbiddenPage(User user) {
        return wrapPage("Forbidden", user, "<div class='card'><h1>Access Denied</h1><p>You do not have permission to do this.</p></div>");
    }

    private String wrapPage(String title, User currentUser, String body) {
        String authLinks = currentUser == null
                ? "<a href='/login'>Login</a><a class='btn-small' href='/register'>Register</a>"
                : "<a href='/dashboard'>" + escape(currentUser.getUsername()) + " (" + currentUser.getRoleName() + ")</a><a class='btn-small' href='/logout'>Logout</a>";

        return "<!DOCTYPE html><html><head><meta charset='UTF-8'><title>" + escape(title) + "</title>" + styles() + "</head><body>"
                + "<nav class='topbar'><a class='brand' href='/'>UTA Club Connect</a><div class='navlinks'>"
                + "<a href='/clubs'>Clubs</a><a href='/events'>Events</a><a href='/search'>Search</a><a href='/categories'>Categories</a>"
                + "</div><div class='authlinks'>" + authLinks + "</div></nav>"
                + "<main>" + body + "</main></body></html>";
    }

    private String styles() {
        return "<style>"
                + "*{box-sizing:border-box}body{margin:0;font-family:Arial,Helvetica,sans-serif;background:#f4f7fb;color:#102033}a{color:#0067b1;text-decoration:none}"
                + ".topbar{height:64px;background:white;display:flex;align-items:center;justify-content:space-between;padding:0 38px;box-shadow:0 1px 8px rgba(0,0,0,.08);position:sticky;top:0;z-index:10}.brand{font-weight:800;font-size:24px;color:#0b4f86}.navlinks a,.authlinks a{margin:0 10px;color:#102033}.btn-small{background:#0067b1;color:white!important;padding:10px 16px;border-radius:8px}"
                + "main{max-width:1280px;margin:0 auto;padding:28px}.hero{margin:-28px calc(50% - 50vw) 28px;min-height:390px;background:linear-gradient(135deg,#0072bc,#23415f);background-size:cover;background-position:center;display:flex;align-items:center;justify-content:center}.hero-box{width:min(900px,90%);background:rgba(8,31,52,.86);border-radius:10px;padding:44px;text-align:center;color:white}.hero h1{font-size:40px;margin:0 0 28px}.hero-search{display:flex;background:white;border-radius:8px;padding:14px;gap:10px}.hero-search input{flex:1;border:0;font-size:18px;padding:14px;outline:0}.hero-search button,button{background:#0067b1;color:white;border:0;padding:12px 18px;border-radius:7px;cursor:pointer}.tiny-counts{font-size:14px;margin:12px 0 0;color:#dbeafe}"
                + ".home-section{margin:34px 0}.section-header{display:flex;justify-content:space-between;align-items:center}.scroll-row{display:flex;gap:18px;overflow-x:auto;padding-bottom:14px}.media-card{min-width:280px;max-width:280px;background:white;border-radius:12px;box-shadow:0 1px 8px rgba(0,0,0,.08);overflow:hidden;color:#102033}.media-card img{width:100%;height:150px;object-fit:cover}.media-card div{padding:14px}.media-card h3{margin:0 0 8px}"
                + ".grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:18px}.card{background:white;border:1px solid #d7e0ea;border-radius:12px;padding:20px;margin:16px 0;box-shadow:0 1px 6px rgba(0,0,0,.04)}.card-img{width:100%;height:170px;object-fit:cover;border-radius:10px;margin-bottom:12px}.status{color:#047857;font-weight:bold}.error{color:#b91c1c}.danger{background:#b91c1c}.secondary{background:#e8eef5;color:#102033}.tools{margin-top:14px;border-top:1px solid #e5e7eb;padding-top:12px}.tools a{margin-right:12px}"
                + ".form-card form,.auth-card form{display:flex;flex-direction:column;gap:10px}.form-card input,.form-card textarea,.form-card select,.auth-card input,.card input,.card select{padding:12px;border:1px solid #cbd5e1;border-radius:8px;font-size:15px}.checks{display:grid;grid-template-columns:repeat(auto-fit,minmax(160px,1fr));gap:8px}.check{font-size:14px}.action-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(230px,1fr));gap:18px}.action-card{display:block;background:white;border:1px solid #d7e0ea;border-radius:12px;padding:22px;color:#102033}.action-card:hover{box-shadow:0 4px 14px rgba(0,0,0,.12)}"
                + "</style>";
    }

    private void sendHtml(HttpExchange exchange, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        InputStream inputStream = exchange.getRequestBody();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) != -1) outputStream.write(buffer, 0, length);
        return outputStream.toString(StandardCharsets.UTF_8);
    }

    private Map<String, List<String>> parseFormData(String body) {
        Map<String, List<String>> result = new HashMap<>();
        if (body == null || body.isBlank()) return result;
        for (String pair : body.split("&")) {
            String[] pieces = pair.split("=", 2);
            String key = urlDecode(pieces[0]);
            String value = pieces.length > 1 ? urlDecode(pieces[1]) : "";
            result.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
        return result;
    }

    private String getFirstValue(Map<String, List<String>> map, String key) {
        List<String> values = map.get(key);
        return values == null || values.isEmpty() ? "" : values.get(0);
    }

    private int getIntParam(HttpExchange exchange, String key, int defaultValue) {
        try {
            String value = getQueryParam(exchange, key);
            return value.isBlank() ? defaultValue : Integer.parseInt(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String getQueryParam(HttpExchange exchange, String key) {
        String query = exchange.getRequestURI().getQuery();
        if (query == null) return "";
        for (String param : query.split("&")) {
            String[] pieces = param.split("=", 2);
            if (urlDecode(pieces[0]).equals(key)) return pieces.length > 1 ? urlDecode(pieces[1]) : "";
        }
        return "";
    }

    private int parseIntOrZero(String text) {
        try { return Integer.parseInt(text); } catch (Exception e) { return 0; }
    }

    private String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private String escape(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String escapeAttribute(String text) {
        return escape(text);
    }
}
