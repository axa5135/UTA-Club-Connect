import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserRepository {

    public User findByUsername(String username) throws SQLException {
        try {
            Firestore db = FirebaseService.getDatabase();
            DocumentSnapshot doc = db.collection("users").document(username).get().get();

            if (!doc.exists()) {
                return null;
            }

            String passwordHash = doc.getString("passwordHash");
            int role = getInt(doc, "role");

            return new User(username, passwordHash, role);
        } catch (Exception e) {
            throw new SQLException("Could not find user in Firebase.", e);
        }
    }

    public boolean usernameExists(String username) throws SQLException {
        return findByUsername(username) != null;
    }

    public void createUser(String username, String password, int role) throws SQLException {
        try {
            Firestore db = FirebaseService.getDatabase();

            Map<String, Object> data = new HashMap<>();
            data.put("username", username);
            data.put("passwordHash", PasswordUtil.hash(password));
            data.put("role", role);

            db.collection("users").document(username).set(data).get();
        } catch (Exception e) {
            throw new SQLException("Could not create user in Firebase.", e);
        }
    }

    public User checkLogin(String username, String password) throws SQLException {
        User user = findByUsername(username);

        if (user == null) {
            return null;
        }

        if (user.getPasswordHash().equals(PasswordUtil.hash(password))) {
            return user;
        }

        return null;
    }

    public boolean promoteToClubPresident(String username) throws SQLException {
        try {
            User user = findByUsername(username);

            if (user == null) {
                return false;
            }

            if (user.getRole() != User.LEVEL_STUDENT) {
                return false;
            }

            Firestore db = FirebaseService.getDatabase();
            db.collection("users").document(username).update("role", User.LEVEL_CLUB_PRESIDENT).get();

            return true;
        } catch (Exception e) {
            throw new SQLException("Could not promote user in Firebase.", e);
        }
    }

    public List<User> findAllStudents() throws SQLException {
        try {
            Firestore db = FirebaseService.getDatabase();
            List<QueryDocumentSnapshot> docs = db.collection("users")
                    .whereEqualTo("role", User.LEVEL_STUDENT)
                    .get()
                    .get()
                    .getDocuments();

            List<User> results = new ArrayList<>();

            for (QueryDocumentSnapshot doc : docs) {
                String username = doc.getString("username");
                String passwordHash = doc.getString("passwordHash");
                int role = getInt(doc, "role");

                results.add(new User(username, passwordHash, role));
            }

            return results;
        } catch (Exception e) {
            throw new SQLException("Could not get students from Firebase.", e);
        }
    }

    private int getInt(DocumentSnapshot doc, String field) {
        Long value = doc.getLong(field);
        return value == null ? 0 : value.intValue();
    }
}