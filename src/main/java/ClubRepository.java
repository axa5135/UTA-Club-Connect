import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class ClubRepository {

    public List<Club> getAllClubs() throws SQLException {
        List<Club> clubs = new ArrayList<>();
        String sql = "SELECT id, name, description, contact_email, meeting_time, members, owner_username, auto_approve_members " +
                "FROM clubs ORDER BY name";
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                clubs.add(buildClub(conn, rs));
            }
        }
        return clubs;
    }

    public Club getClubById(int id) throws SQLException {
        String sql = "SELECT id, name, description, contact_email, meeting_time, members, owner_username, auto_approve_members " +
                "FROM clubs WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return buildClub(conn, rs);
                }
            }
        }
        return null;
    }

    public List<Club> getClubsOwnedBy(String username) throws SQLException {
        List<Club> clubs = new ArrayList<>();
        String sql = "SELECT id, name, description, contact_email, meeting_time, members, owner_username, auto_approve_members " +
                "FROM clubs WHERE owner_username = ? ORDER BY name";
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    clubs.add(buildClub(conn, rs));
                }
            }
        }
        return clubs;
    }

    private Club buildClub(Connection conn, ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        List<String> tags = getTagsForClub(conn, id);
        return new Club(
                id,
                rs.getString("name"),
                tags,
                rs.getInt("members"),
                rs.getString("description"),
                rs.getString("contact_email"),
                rs.getString("meeting_time"),
                rs.getString("owner_username"),
                rs.getInt("auto_approve_members") == 1
        );
    }

    private List<String> getTagsForClub(Connection conn, int clubId) throws SQLException {
        List<String> tags = new ArrayList<>();
        String sql = "SELECT tag FROM club_tags WHERE club_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, clubId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    tags.add(rs.getString("tag"));
                }
            }
        }
        return tags;
    }

    public int addClub(String name, List<String> tags, int members, String description,
                        String contactEmail, String meetingTime, String ownerUsername,
                        boolean autoApproveMembers) throws SQLException {
        String sql = "INSERT INTO clubs (name, description, contact_email, meeting_time, members, owner_username, auto_approve_members) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, name);
            stmt.setString(2, description);
            stmt.setString(3, contactEmail);
            stmt.setString(4, meetingTime);
            stmt.setInt(5, members);
            stmt.setString(6, ownerUsername);
            stmt.setInt(7, autoApproveMembers ? 1 : 0);
            stmt.executeUpdate();

            int clubId;
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                keys.next();
                clubId = keys.getInt(1);
            }

            insertTags(conn, clubId, tags);
            return clubId;
        }
    }

    private void insertTags(Connection conn, int clubId, List<String> tags) throws SQLException {
        String sql = "INSERT INTO club_tags (club_id, tag) VALUES (?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (String tag : tags) {
                stmt.setInt(1, clubId);
                stmt.setString(2, tag);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    // Only edits the club if requestingUsername owns it, or isStaff is true (Level 3)
    public boolean updateClub(int clubId, String name, List<String> tags, int members, String description,
                               String contactEmail, String meetingTime, boolean autoApproveMembers,
                               String requestingUsername, boolean isStaff) throws SQLException {
        Club existing = getClubById(clubId);
        if (existing == null) {
            return false;
        }
        if (!isStaff && !existing.isOwnedBy(requestingUsername)) {
            return false;
        }

        String sql = "UPDATE clubs SET name = ?, description = ?, contact_email = ?, meeting_time = ?, members = ?, " +
                "auto_approve_members = ? WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            stmt.setString(2, description);
            stmt.setString(3, contactEmail);
            stmt.setString(4, meetingTime);
            stmt.setInt(5, members);
            stmt.setInt(6, autoApproveMembers ? 1 : 0);
            stmt.setInt(7, clubId);
            stmt.executeUpdate();

            try (PreparedStatement deleteTags = conn.prepareStatement("DELETE FROM club_tags WHERE club_id = ?")) {
                deleteTags.setInt(1, clubId);
                deleteTags.executeUpdate();
            }
            insertTags(conn, clubId, tags);
        }
        return true;
    }

    public boolean hasUserJoined(int clubId, String username) throws SQLException {
        String sql = "SELECT 1 FROM club_members WHERE club_id = ? AND username = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, clubId);
            stmt.setString(2, username);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    // Only actually joins if the club has auto-approve turned on. Returns true if this
    // call newly added the membership, false if they were already a member (or not allowed).
    public boolean joinClub(int clubId, String username) throws SQLException {
        Club club = getClubById(clubId);
        if (club == null || !club.isAutoApproveMembers()) {
            return false;
        }
        if (hasUserJoined(clubId, username)) {
            return false;
        }
        String sql = "INSERT INTO club_members (club_id, username) VALUES (?, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, clubId);
            stmt.setString(2, username);
            stmt.executeUpdate();
        }
        return true;
    }

    // Only deletes the club if requestingUsername owns it, or isStaff is true (Level 3)
    public boolean deleteClub(int clubId, String requestingUsername, boolean isStaff) throws SQLException {
        Club existing = getClubById(clubId);
        if (existing == null) {
            return false;
        }
        if (!isStaff && !existing.isOwnedBy(requestingUsername)) {
            return false;
        }

        try (Connection conn = Database.getConnection()) {
            try (PreparedStatement deleteTags = conn.prepareStatement("DELETE FROM club_tags WHERE club_id = ?")) {
                deleteTags.setInt(1, clubId);
                deleteTags.executeUpdate();
            }
            try (PreparedStatement deleteEvents = conn.prepareStatement("DELETE FROM events WHERE club_id = ?")) {
                deleteEvents.setInt(1, clubId);
                deleteEvents.executeUpdate();
            }
            try (PreparedStatement deleteMembers = conn.prepareStatement("DELETE FROM club_members WHERE club_id = ?")) {
                deleteMembers.setInt(1, clubId);
                deleteMembers.executeUpdate();
            }
            try (PreparedStatement deleteClub = conn.prepareStatement("DELETE FROM clubs WHERE id = ?")) {
                deleteClub.setInt(1, clubId);
                deleteClub.executeUpdate();
            }
        }
        return true;
    }
}
