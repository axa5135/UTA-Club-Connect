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

            if (!doc.exists()) return null;

            String passwordHash = doc.getString("passwordHash");
            int role = getInt(doc, "role");
            String fullName = doc.getString("fullName");

            if (fullName == null || fullName.isBlank()) {
                fullName = User.guessFullName(username);
                db.collection("users").document(username).update("fullName", fullName).get();
            }

            return new User(username, fullName, passwordHash, role);
        } catch (Exception e) {
            throw new SQLException("Could not find user in Firebase.", e);
        }
    }

    public boolean usernameExists(String username) throws SQLException {
        return findByUsername(username) != null;
    }

    public void createUser(String username, String password, int role) throws SQLException {
        createUser(username, User.guessFullName(username), password, role);
    }

    public void createUser(String username, String fullName, String password, int role) throws SQLException {
        try {
            Firestore db = FirebaseService.getDatabase();

            Map<String, Object> data = new HashMap<>();
            data.put("username", username);
            data.put("fullName", (fullName == null || fullName.isBlank()) ? User.guessFullName(username) : fullName);
            data.put("passwordHash", PasswordUtil.hash(password));
            data.put("role", role);

            db.collection("users").document(username).set(data).get();
        } catch (Exception e) {
            throw new SQLException("Could not create user in Firebase.", e);
        }
    }

    public User checkLogin(String username, String password) throws SQLException {
        User user = findByUsername(username);
        if (user == null) return null;

        if (user.getPasswordHash().equals(PasswordUtil.hash(password))) {
            return user;
        }

        return null;
    }

    public boolean promoteToClubPresident(String username) throws SQLException {
        try {
            User user = findByUsername(username);
            if (user == null || user.getRole() != User.LEVEL_STUDENT) return false;

            Firestore db = FirebaseService.getDatabase();
            db.collection("users").document(username).update("role", User.LEVEL_CLUB_PRESIDENT).get();
            return true;
        } catch (Exception e) {
            throw new SQLException("Could not promote user in Firebase.", e);
        }
    }

    public boolean demoteToMember(String username, String currentAdminUsername) throws SQLException {
        try {
            if (username == null || username.isBlank()) return false;
            if (username.equalsIgnoreCase(currentAdminUsername)) return false;

            User user = findByUsername(username);
            if (user == null || user.getRole() != User.LEVEL_CLUB_PRESIDENT) return false;

            Firestore db = FirebaseService.getDatabase();
            db.collection("users").document(username).update("role", User.LEVEL_STUDENT).get();
            return true;
        } catch (Exception e) {
            throw new SQLException("Could not demote user in Firebase.", e);
        }
    }

    public List<User> findAllStudents() throws SQLException {
        return findUsersByRole(User.LEVEL_STUDENT);
    }

    public List<User> findAllClubLeaders() throws SQLException {
        return findUsersByRole(User.LEVEL_CLUB_PRESIDENT);
    }

    public List<User> findAllUsers() throws SQLException {
        try {
            Firestore db = FirebaseService.getDatabase();
            List<QueryDocumentSnapshot> docs = db.collection("users").get().get().getDocuments();

            List<User> results = new ArrayList<>();

            for (QueryDocumentSnapshot doc : docs) {
                results.add(buildUser(doc));
            }

            results.sort((a, b) -> a.getFullName().compareToIgnoreCase(b.getFullName()));
            return results;
        } catch (Exception e) {
            throw new SQLException("Could not get users from Firebase.", e);
        }
    }

    private List<User> findUsersByRole(int roleToFind) throws SQLException {
        try {
            Firestore db = FirebaseService.getDatabase();
            List<QueryDocumentSnapshot> docs = db.collection("users")
                    .whereEqualTo("role", roleToFind)
                    .get()
                    .get()
                    .getDocuments();

            List<User> results = new ArrayList<>();

            for (QueryDocumentSnapshot doc : docs) {
                results.add(buildUser(doc));
            }

            results.sort((a, b) -> a.getFullName().compareToIgnoreCase(b.getFullName()));
            return results;
        } catch (Exception e) {
            throw new SQLException("Could not get users from Firebase.", e);
        }
    }

    private User buildUser(DocumentSnapshot doc) throws Exception {
        String username = doc.getString("username");
        if (username == null || username.isBlank()) username = doc.getId();

        String passwordHash = doc.getString("passwordHash");
        int role = getInt(doc, "role");
        String fullName = doc.getString("fullName");

        if (fullName == null || fullName.isBlank()) {
            fullName = User.guessFullName(username);
            FirebaseService.getDatabase().collection("users").document(username).update("fullName", fullName).get();
        }

        return new User(username, fullName, passwordHash, role);
    }

    private int getInt(DocumentSnapshot doc, String field) {
        Long value = doc.getLong(field);
        return value == null ? 0 : value.intValue();
    }
}
