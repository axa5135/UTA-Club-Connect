import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

// Handles the connection to the shared SQLite database file and creates
// the tables the first time the app is run. Since the .db file lives in
// the project folder, everyone on the team sees the same data.
public class Database {
    private static final String DB_URL = "jdbc:sqlite:club_connect.db";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    public static void initializeSchema() throws SQLException {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    username TEXT PRIMARY KEY,
                    password_hash TEXT NOT NULL,
                    role INTEGER NOT NULL DEFAULT 1
                )
                """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS clubs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    description TEXT,
                    contact_email TEXT,
                    meeting_time TEXT,
                    members INTEGER DEFAULT 0,
                    owner_username TEXT,
                    FOREIGN KEY (owner_username) REFERENCES users(username)
                )
                """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS club_tags (
                    club_id INTEGER NOT NULL,
                    tag TEXT NOT NULL,
                    FOREIGN KEY (club_id) REFERENCES clubs(id)
                )
                """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    club_id INTEGER,
                    date TEXT,
                    location TEXT,
                    description TEXT,
                    FOREIGN KEY (club_id) REFERENCES clubs(id)
                )
                """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS rsvps (
                    event_id INTEGER NOT NULL,
                    username TEXT NOT NULL,
                    PRIMARY KEY (event_id, username),
                    FOREIGN KEY (event_id) REFERENCES events(id),
                    FOREIGN KEY (username) REFERENCES users(username)
                )
                """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS club_members (
                    club_id INTEGER NOT NULL,
                    username TEXT NOT NULL,
                    PRIMARY KEY (club_id, username),
                    FOREIGN KEY (club_id) REFERENCES clubs(id),
                    FOREIGN KEY (username) REFERENCES users(username)
                )
                """);

            // These columns were added after the tables above already existed for some of
            // the team - this makes sure everyone's existing club_connect.db file gets
            // upgraded instead of breaking.
            addColumnIfMissing(conn, "clubs", "auto_approve_members", "INTEGER DEFAULT 0");
            addColumnIfMissing(conn, "events", "contact_email", "TEXT");
            addColumnIfMissing(conn, "events", "rsvp_capacity", "INTEGER");
        }
    }

    private static void addColumnIfMissing(Connection conn, String table, String column, String definition) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (rs.getString("name").equalsIgnoreCase(column)) {
                    return; // column already exists, nothing to do
                }
            }
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }
}
