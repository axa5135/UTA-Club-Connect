package main.java;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class EventRepository {

    private static final String SELECT_EVENT_FIELDS = """
            SELECT events.id, events.title, events.club_id, clubs.name AS club_name,
                   events.date, events.location, events.description,
                   events.contact_email, events.rsvp_capacity
            FROM events
            LEFT JOIN clubs ON events.club_id = clubs.id
            """;

    public List<Event> getAllEvents() throws SQLException {
        List<Event> events = new ArrayList<>();
        String sql = SELECT_EVENT_FIELDS + " ORDER BY events.date";
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                events.add(buildEvent(rs));
            }
        }
        return events;
    }

    public Event getEventById(int id) throws SQLException {
        String sql = SELECT_EVENT_FIELDS + " WHERE events.id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return buildEvent(rs);
                }
            }
        }
        return null;
    }

    private Event buildEvent(ResultSet rs) throws SQLException {
        int capacity = rs.getInt("rsvp_capacity");
        Integer rsvpCapacity = rs.wasNull() ? null : capacity;

        return new Event(
                rs.getInt("id"),
                rs.getString("title"),
                rs.getInt("club_id"),
                rs.getString("club_name"),
                rs.getString("date"),
                rs.getString("location"),
                rs.getString("description"),
                rs.getString("contact_email"),
                rsvpCapacity
        );
    }

    public int addEvent(String title, int clubId, String date, String location, String description,
                         String contactEmail, Integer rsvpCapacity) throws SQLException {
        String sql = "INSERT INTO events (title, club_id, date, location, description, contact_email, rsvp_capacity) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, title);
            stmt.setInt(2, clubId);
            stmt.setString(3, date);
            stmt.setString(4, location);
            stmt.setString(5, description);
            stmt.setString(6, contactEmail);
            if (rsvpCapacity == null) {
                stmt.setNull(7, java.sql.Types.INTEGER);
            } else {
                stmt.setInt(7, rsvpCapacity);
            }
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    public int getRsvpCount(int eventId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM rsvps WHERE event_id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, eventId);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public boolean hasUserRsvped(int eventId, String username) throws SQLException {
        String sql = "SELECT 1 FROM rsvps WHERE event_id = ? AND username = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, eventId);
            stmt.setString(2, username);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    public enum RsvpResult { SUCCESS, ALREADY_RSVPED, EVENT_FULL, NOT_FOUND }

    // Handles duplicate-RSVP prevention AND capacity limits server-side (not just in the UI),
    // so someone can't bypass either by submitting the form directly.
    public RsvpResult rsvp(int eventId, String username) throws SQLException {
        Event event = getEventById(eventId);
        if (event == null) {
            return RsvpResult.NOT_FOUND;
        }
        if (hasUserRsvped(eventId, username)) {
            return RsvpResult.ALREADY_RSVPED;
        }
        if (event.hasCapacity() && getRsvpCount(eventId) >= event.getRsvpCapacity()) {
            return RsvpResult.EVENT_FULL;
        }

        String sql = "INSERT INTO rsvps (event_id, username) VALUES (?, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, eventId);
            stmt.setString(2, username);
            stmt.executeUpdate();
        }
        return RsvpResult.SUCCESS;
    }
}
