import java.sql.SQLException;

public class Database {
    public static void initializeSchema() throws SQLException {
        try {
            FirebaseService.getDatabase();
            System.out.println("Firebase connected.");
        } catch (Exception e) {
            throw new SQLException("Could not connect to Firebase. Make sure firebase-key.json is in the project root.", e);
        }
    }
}
