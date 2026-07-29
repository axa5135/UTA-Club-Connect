import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClubRepository {

    public List<Club> getAllClubs() throws SQLException {
        try {
            Firestore db = FirebaseService.getDatabase();
            List<QueryDocumentSnapshot> docs = db.collection("clubs").get().get().getDocuments();

            List<Club> clubs = new ArrayList<>();
            for (QueryDocumentSnapshot doc : docs) {
                clubs.add(buildClub(doc));
            }

            clubs.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            return clubs;
        } catch (Exception e) {
            throw new SQLException("Could not get clubs from Firebase.", e);
        }
    }

    public Club getClubById(int id) throws SQLException {
        try {
            Firestore db = FirebaseService.getDatabase();
            DocumentSnapshot doc = db.collection("clubs").document(String.valueOf(id)).get().get();

            if (!doc.exists()) {
                return null;
            }

            return buildClub(doc);
        } catch (Exception e) {
            throw new SQLException("Could not get club from Firebase.", e);
        }
    }

    public List<Club> getClubsOwnedBy(String username) throws SQLException {
        try {
            Firestore db = FirebaseService.getDatabase();
            List<QueryDocumentSnapshot> docs = db.collection("clubs")
                    .whereEqualTo("ownerUsername", username)
                    .get()
                    .get()
                    .getDocuments();

            List<Club> clubs = new ArrayList<>();
            for (QueryDocumentSnapshot doc : docs) {
                clubs.add(buildClub(doc));
            }

            clubs.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            return clubs;
        } catch (Exception e) {
            throw new SQLException("Could not get owned clubs from Firebase.", e);
        }
    }

    public int addClub(String name, List<String> tags, int members, String description,
                       String contactEmail, String meetingTime, String ownerUsername,
                       boolean autoApproveMembers) throws SQLException {
        try {
            Firestore db = FirebaseService.getDatabase();
            int id = getNextId("clubs");

            Map<String, Object> data = new HashMap<>();
            data.put("id", id);
            data.put("name", name);
            data.put("categories", tags);
            data.put("members", members);
            data.put("description", description);
            data.put("contactEmail", contactEmail);
            data.put("meetingTime", meetingTime);
            data.put("ownerUsername", ownerUsername);
            data.put("autoApproveMembers", autoApproveMembers);

            db.collection("clubs").document(String.valueOf(id)).set(data).get();
            return id;
        } catch (Exception e) {
            throw new SQLException("Could not add club to Firebase.", e);
        }
    }

    public boolean updateClub(int clubId, String name, List<String> tags, int members, String description,
                              String contactEmail, String meetingTime, boolean autoApproveMembers,
                              String requestingUsername, boolean isStaff) throws SQLException {
        try {
            Club existing = getClubById(clubId);

            if (existing == null) {
                return false;
            }

            if (!isStaff && !existing.isOwnedBy(requestingUsername)) {
                return false;
            }

            Firestore db = FirebaseService.getDatabase();

            Map<String, Object> data = new HashMap<>();
            data.put("id", clubId);
            data.put("name", name);
            data.put("categories", tags);
            data.put("members", members);
            data.put("description", description);
            data.put("contactEmail", contactEmail);
            data.put("meetingTime", meetingTime);
            data.put("ownerUsername", existing.getOwnerUsername());
            data.put("autoApproveMembers", autoApproveMembers);

            db.collection("clubs").document(String.valueOf(clubId)).set(data).get();
            return true;
        } catch (Exception e) {
            throw new SQLException("Could not update club in Firebase.", e);
        }
    }

    public boolean hasUserJoined(int clubId, String username) throws SQLException {
        try {
            Firestore db = FirebaseService.getDatabase();
            String docId = clubId + "_" + username;

            DocumentSnapshot doc = db.collection("club_members").document(docId).get().get();
            return doc.exists();
        } catch (Exception e) {
            throw new SQLException("Could not check club membership in Firebase.", e);
        }
    }

    public boolean joinClub(int clubId, String username) throws SQLException {
        try {
            Club club = getClubById(clubId);

            if (club == null || !club.isAutoApproveMembers()) {
                return false;
            }

            if (hasUserJoined(clubId, username)) {
                return false;
            }

            Firestore db = FirebaseService.getDatabase();
            String docId = clubId + "_" + username;

            Map<String, Object> data = new HashMap<>();
            data.put("clubId", clubId);
            data.put("username", username);

            db.collection("club_members").document(docId).set(data).get();
            return true;
        } catch (Exception e) {
            throw new SQLException("Could not join club in Firebase.", e);
        }
    }

    public boolean deleteClub(int clubId, String requestingUsername, boolean isStaff) throws SQLException {
        try {
            Club existing = getClubById(clubId);

            if (existing == null) {
                return false;
            }

            if (!isStaff && !existing.isOwnedBy(requestingUsername)) {
                return false;
            }

            Firestore db = FirebaseService.getDatabase();

            List<QueryDocumentSnapshot> events = db.collection("events")
                    .whereEqualTo("clubId", clubId)
                    .get()
                    .get()
                    .getDocuments();

            for (QueryDocumentSnapshot event : events) {
                db.collection("events").document(event.getId()).delete().get();
            }

            List<QueryDocumentSnapshot> members = db.collection("club_members")
                    .whereEqualTo("clubId", clubId)
                    .get()
                    .get()
                    .getDocuments();

            for (QueryDocumentSnapshot member : members) {
                db.collection("club_members").document(member.getId()).delete().get();
            }

            db.collection("clubs").document(String.valueOf(clubId)).delete().get();
            return true;
        } catch (Exception e) {
            throw new SQLException("Could not delete club from Firebase.", e);
        }
    }

    private Club buildClub(DocumentSnapshot doc) {
        int id = getInt(doc, "id");
        String name = doc.getString("name");
        String description = doc.getString("description");
        String contactEmail = doc.getString("contactEmail");
        String meetingTime = doc.getString("meetingTime");
        int members = getInt(doc, "members");
        String ownerUsername = doc.getString("ownerUsername");
        boolean autoApproveMembers = getBoolean(doc, "autoApproveMembers");

        List<String> categories = new ArrayList<>();
        Object rawCategories = doc.get("categories");

        if (rawCategories instanceof List<?>) {
            for (Object item : (List<?>) rawCategories) {
                categories.add(String.valueOf(item));
            }
        }

        return new Club(id, name, categories, members, description, contactEmail, meetingTime, ownerUsername, autoApproveMembers);
    }

    private int getNextId(String collectionName) throws Exception {
        Firestore db = FirebaseService.getDatabase();
        List<QueryDocumentSnapshot> docs = db.collection(collectionName).get().get().getDocuments();

        int highest = 0;
        for (QueryDocumentSnapshot doc : docs) {
            int id = getInt(doc, "id");
            if (id > highest) {
                highest = id;
            }
        }

        return highest + 1;
    }

    private int getInt(DocumentSnapshot doc, String field) {
        Long value = doc.getLong(field);
        return value == null ? 0 : value.intValue();
    }

    private boolean getBoolean(DocumentSnapshot doc, String field) {
        Boolean value = doc.getBoolean(field);
        return value != null && value;
    }
}
