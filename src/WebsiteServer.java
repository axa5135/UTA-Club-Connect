import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import java.util.ArrayList;

public class WebsiteServer {
    private HttpServer server;
    private ArrayList<Club> clubs;
    private ArrayList<Event> events;

    public WebsiteServer(int port, ArrayList<Club> clubs, ArrayList<Event> events) throws IOException {
        this.clubs = clubs;
        this.events = events;
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this::handleHomePage);
        server.createContext("/clubs", this::handleClubsPage);
        server.createContext("/events", this::handleEventsPage);
        server.createContext("/search", this::handleSearchPage);
        server.createContext("/categories", this::handleCategoriesPage);
        server.createContext("/club", this::handleClubDetailsPage);
    }

    public void start() {
        server.start();
        System.out.println("Website running at http://localhost:8080");
    }

    private void handleClubDetailsPage(HttpExchange exchange) throws IOException
    {

    }

    private void handleCategoriesPage(HttpExchange exchange) throws IOException
    {
        String query = exchange.getRequestURI().getQuery();
        String categoryText = "";

        if (query != null && query.startsWith("category=")) {
            categoryText = query.substring(9);
        }

        String categoryForm = "<div class='card'>"
                + "<form action='/categories' method='get'>"
                + "<label>Enter category: </label>"
                + "<input type='text' name='category' value='" + categoryText + "'>"
                + "<button type='submit'>Filter</button>"
                + "</form>"
                + "</div>";

        String resultsHtml = "";

        if (!categoryText.isEmpty()) {
            boolean found = false;

            for (Club club : clubs) {
                if (club.getCategory().toLowerCase().contains(categoryText.toLowerCase())) {
                    resultsHtml = resultsHtml + "<div class='card'>";
                    resultsHtml = resultsHtml + "<h2>" + club.getName() + "</h2>";
                    resultsHtml = resultsHtml + "<p>Category: " + club.getCategory() + "</p>";
                    resultsHtml = resultsHtml + "<p>Description: " + club.getDescription() + "</p>";
                    resultsHtml = resultsHtml + "<p>Meeting time: " + club.getMeetingTime() + "</p>";
                    resultsHtml = resultsHtml + "<p>Members: " + club.getMembers() + "</p>";
                    resultsHtml = resultsHtml + "<p>Contact: " + club.getContactEmail() + "</p>";
                    resultsHtml = resultsHtml + "</div>";
                    found = true;
                }
            }

            if (!found) {
                resultsHtml = "<p>No clubs found in that category.</p>";
            }
        }

        String html = """
            <html>
                <head>
                    <title>Filter Clubs</title>
            """ + getStyles() + """
                </head>
                <body>
            """ + getNavigation() + """
                    <h1>Filter Clubs by Category</h1>
            """ + categoryForm + resultsHtml + """
                </body>
            </html>
            """;

        sendHtml(exchange, html);
    }

    private void handleSearchPage(HttpExchange exchange) throws IOException
    {
        String query = exchange.getRequestURI().getQuery();
        String searchText = "";
        if (query != null && query.startsWith("clubName=")) {
            searchText = query.substring(9);
        }
        String resultsHtml = "";

        if (!searchText.isEmpty()) {
            boolean found = false;

            for (Club club : clubs) {
                if (club.getName().toLowerCase().contains(searchText.toLowerCase())) {
                    resultsHtml = resultsHtml + "<div class='card'>";
                    resultsHtml = resultsHtml + "<h2>" + club.getName() + "</h2>";
                    resultsHtml = resultsHtml + "<p>Category: " + club.getCategory() + "</p>";
                    resultsHtml = resultsHtml + "<p>Description: " + club.getDescription() + "</p>";
                    resultsHtml = resultsHtml + "<p>Meeting time: " + club.getMeetingTime() + "</p>";
                    resultsHtml = resultsHtml + "<p>Members: " + club.getMembers() + "</p>";
                    resultsHtml = resultsHtml + "<p>Contact: " + club.getContactEmail() + "</p>";
                    resultsHtml = resultsHtml + "</div>";
                    found = true;
                }
            }

            if (!found) {
                resultsHtml = "<p>No club found with that name.</p>";
            }
        }

        String searchForm = "<div class='card'>"
                + "<form action='/search' method='get'>"
                + "<label>Enter club name: </label>"
                + "<input type='text' name='clubName' value='" + searchText + "'>"
                + "<button type='submit'>Search</button>"
                + "</form>"
                + "</div>";

        String html = """
                <html>
                    <head>
                        <title>Search Clubs</title>
                    """ + getStyles() + """
                    </head>
                    <body>
                    """ + getNavigation() + """
                        <h1>Search Clubs</h1>
                        """ + searchForm + resultsHtml + """
                    </body>
                </html>
                        
                """;
        sendHtml(exchange, html);
    }

    private void handleHomePage(HttpExchange exchange) throws IOException
    {
        String html = """
                <html>
                    <head>
                        <title>UTA Club Connect</title>
                """ + getStyles() + """
                    </head>
                    <body>
                    """ + getNavigation() + """
                        <div class="card">
                            <h1>UTA Club Connect</h1>
                            <p>Find clubs, view events, and connect with student organizations at UTA.</p>
                            """ + "<p>Total Clubs: " + clubs.size() + "</p>" + """
                            """ + "<p>Total Events: " + events.size() + "</p>" + """
                
                            <a href="/clubs">View Clubs</a>
                            <a href="/events">View Events</a>
                            </div>
                    </body>
                </html>
                """;
        sendHtml(exchange, html);
    }

    private void handleClubsPage(HttpExchange exchange) throws IOException {
        String html = """
                <html>
                    <head>
                        <title>Clubs</title>
                """ + getStyles() + """
                    </head>
                    <body>
                    """ + getNavigation() + """
                        <h1>Clubs</h1>
                """;

        for (Club club : clubs) {
            html = html + "<div class='card'>";
            html = html + "<h2>" + club.getName() + "</h2>";
            html = html + "<p>Category: " + club.getCategory() + "</p>";
            html = html + "<p>Description: " + club.getDescription() + "</p>";
            html = html + "<p>Meeting time: " + club.getMeetingTime() + "</p>";
            html = html + "<p>Members: " + club.getMembers() + "</p>";
            html = html + "<p>Contact: " + club.getContactEmail() + "</p>";
            html = html + "</div>";
        }

        html = html + """
                    </body>
                </html>
                """;

        sendHtml(exchange, html);
    }

    private void handleEventsPage(HttpExchange exchange) throws IOException {
        String html = """
                <html>
                    <head>
                        <title>Events</title>
                """ + getStyles() + """
                    </head>
                    <body>
                    """ + getNavigation() + """
                        <h1>Events</h1>
                """;

        for (Event event : events) {
            html = html + "<div class='card'>";
            html = html + "<h2>" + event.getTitle() + "</h2>";
            html = html + "<p>Club name: " + event.getClubName() + "</p>";
            html = html + "<p>Date: " + event.getDate() + "</p>";
            html = html + "<p>Location: " + event.getLocation() + "</p>";
            html = html + "<p>Description: " + event.getDescription() + "</p>";
            html = html + "<p>RSVP Count: " + event.getRsvpCount() + "</p>";
            html = html + "</div>";
        }

        html = html + """
                    </body>
                </html>
                """;

        sendHtml(exchange, html);
    }

    private void sendHtml(HttpExchange exchange, String html) throws IOException {
        exchange.sendResponseHeaders(200, html.length());

        OutputStream output = exchange.getResponseBody();
        output.write(html.getBytes());
        output.close();
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
                </style>
                """;
    }
    private String getNavigation()
    {
        return """
                <nav>
                    <a href="/">Home</a>
                    <a href="/clubs">Clubs</a>
                    <a href="/events">Events</a>
                    <a href="/search">Search</a>
                    <a href="/categories">Categories</a>
                </nav>
                """;
    }

}
