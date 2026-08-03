import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClubRepository {

    public static class JoinRequest {
        private String requestId;
        private int clubId;
        private String clubName;
        private String username;
        private String status;

        public JoinRequest(String requestId, int clubId, String clubName, String username, String status) {
            this.requestId = requestId;
            this.clubId = clubId;
            this.clubName = clubName;
            this.username = username;
            this.status = status;
        }

        public String getRequestId() { return requestId; }
        public int getClubId() { return clubId; }
        public String getClubName() { return clubName; }
        public String getUsername() { return username; }
        public String getStatus() { return status; }
    }

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
            return doc.exists() ? buildClub(doc) : null;
        } catch (Exception e) {
            throw new SQLException("Could not get club from Firebase.", e);
        }
    }

    public List<Club> getClubsOwnedBy(String username) throws SQLException {
        try {
            Firestore db = FirebaseService.getDatabase();
            List<QueryDocumentSnapshot> docs = db.collection("clubs")
                    .whereEqualTo("ownerUsername", username)
                    .get().get().getDocuments();

            List<Club> clubs = new ArrayList<>();
            for (QueryDocumentSnapshot doc : docs) clubs.add(buildClub(doc));

            clubs.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            return clubs;
        } catch (Exception e) {
            throw new SQLException("Could not get owned clubs from Firebase.", e);
        }
    }

    public List<Club> getClubsJoinedBy(String username) throws SQLException {
        try {
            Firestore db = FirebaseService.getDatabase();
            List<QueryDocumentSnapshot> docs = db.collection("club_members")
                    .whereEqualTo("username", username)
                    .get().get().getDocuments();

            List<Club> clubs = new ArrayList<>();
            for (QueryDocumentSnapshot doc : docs) {
                int clubId = getInt(doc, "clubId");
                Club club = getClubById(clubId);
                if (club != null) clubs.add(club);
            }

            clubs.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            return clubs;
        } catch (Exception e) {
            throw new SQLException("Could not get joined clubs from Firebase.", e);
        }
    }

    public int addClub(String name, List<String> tags, int members, String description,
                       String contactEmail, String meetingTime, String ownerUsername,
                       boolean autoApproveMembers) throws SQLException {
        return addClub(name, tags, members, description, contactEmail, meetingTime, ownerUsername, autoApproveMembers, "");
    }

    public int addClub(String name, List<String> tags, int members, String description,
                       String contactEmail, String meetingTime, String ownerUsername,
                       boolean autoApproveMembers, String imageUrl) throws SQLException {
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
            data.put("imageUrl", imageUrl == null ? "" : imageUrl);

            db.collection("clubs").document(String.valueOf(id)).set(data).get();
            return id;
        } catch (Exception e) {
            throw new SQLException("Could not add club to Firebase.", e);
        }
    }

    public boolean updateClub(int clubId, String name, List<String> tags, int members, String description,
                              String contactEmail, String meetingTime, boolean autoApproveMembers,
                              String requestingUsername, boolean isStaff) throws SQLException {
        Club existing = getClubById(clubId);
        String imageUrl = existing == null ? "" : existing.getImageUrl();
        return updateClub(clubId, name, tags, members, description, contactEmail, meetingTime,
                autoApproveMembers, imageUrl, requestingUsername, isStaff);
    }

    public boolean updateClub(int clubId, String name, List<String> tags, int members, String description,
                              String contactEmail, String meetingTime, boolean autoApproveMembers,
                              String imageUrl, String requestingUsername, boolean isStaff) throws SQLException {
        try {
            Club existing = getClubById(clubId);
            if (existing == null) return false;
            if (!isStaff && !existing.isOwnedBy(requestingUsername)) return false;

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
            data.put("imageUrl", imageUrl == null ? "" : imageUrl);

            db.collection("clubs").document(String.valueOf(clubId)).set(data).get();
            return true;
        } catch (Exception e) {
            throw new SQLException("Could not update club in Firebase.", e);
        }
    }

    public boolean assignClubOwner(int clubId, String newOwnerUsername) throws SQLException {
        try {
            Club club = getClubById(clubId);
            if (club == null || newOwnerUsername == null || newOwnerUsername.isBlank()) return false;
            Firestore db = FirebaseService.getDatabase();
            db.collection("clubs").document(String.valueOf(clubId)).update("ownerUsername", newOwnerUsername).get();
            return true;
        } catch (Exception e) {
            throw new SQLException("Could not assign club owner in Firebase.", e);
        }
    }

    public boolean hasUserJoined(int clubId, String username) throws SQLException {
        try {
            Firestore db = FirebaseService.getDatabase();
            String docId = clubId + "_" + username;
            return db.collection("club_members").document(docId).get().get().exists();
        } catch (Exception e) {
            throw new SQLException("Could not check club membership in Firebase.", e);
        }
    }

    public String getJoinRequestStatus(int clubId, String username) throws SQLException {
        try {
            Firestore db = FirebaseService.getDatabase();
            String docId = clubId + "_" + username;
            DocumentSnapshot doc = db.collection("join_requests").document(docId).get().get();
            if (!doc.exists()) return "none";
            String status = doc.getString("status");
            return status == null ? "none" : status;
        } catch (Exception e) {
            throw new SQLException("Could not check join request in Firebase.", e);
        }
    }

    public String requestToJoin(int clubId, String username) throws SQLException {
        try {
            Club club = getClubById(clubId);
            if (club == null) return "not_found";
            if (hasUserJoined(clubId, username)) return "already_member";

            Firestore db = FirebaseService.getDatabase();
            String docId = clubId + "_" + username;

            if (club.isAutoApproveMembers()) {
                Map<String, Object> memberData = new HashMap<>();
                memberData.put("clubId", clubId);
                memberData.put("username", username);
                db.collection("club_members").document(docId).set(memberData).get();
                db.collection("clubs").document(String.valueOf(clubId)).update("members", club.getMembers() + 1).get();
                return "joined";
            }

            DocumentSnapshot existingRequest = db.collection("join_requests").document(docId).get().get();
            if (existingRequest.exists() && "pending".equalsIgnoreCase(existingRequest.getString("status"))) {
                return "pending";
            }

            Map<String, Object> requestData = new HashMap<>();
            requestData.put("requestId", docId);
            requestData.put("clubId", clubId);
            requestData.put("clubName", club.getName());
            requestData.put("username", username);
            requestData.put("status", "pending");
            db.collection("join_requests").document(docId).set(requestData).get();
            return "requested";
        } catch (Exception e) {
            throw new SQLException("Could not request to join club in Firebase.", e);
        }
    }

    public boolean joinClub(int clubId, String username) throws SQLException {
        String result = requestToJoin(clubId, username);
        return result.equals("joined") || result.equals("requested") || result.equals("pending");
    }

    public boolean leaveClub(int clubId, String username) throws SQLException {
        try {
            if (!hasUserJoined(clubId, username)) return false;
            Club club = getClubById(clubId);
            Firestore db = FirebaseService.getDatabase();
            String docId = clubId + "_" + username;
            db.collection("club_members").document(docId).delete().get();
            if (club != null && club.getMembers() > 0) {
                db.collection("clubs").document(String.valueOf(clubId)).update("members", club.getMembers() - 1).get();
            }
            return true;
        } catch (Exception e) {
            throw new SQLException("Could not leave club in Firebase.", e);
        }
    }

    public List<JoinRequest> getJoinRequestsForUser(String username, boolean isStaff) throws SQLException {
        try {
            Firestore db = FirebaseService.getDatabase();
            List<QueryDocumentSnapshot> docs = db.collection("join_requests")
                    .whereEqualTo("status", "pending")
                    .get().get().getDocuments();

            List<Club> allowedClubs = isStaff ? getAllClubs() : getClubsOwnedBy(username);
            List<Integer> allowedClubIds = new ArrayList<>();
            for (Club club : allowedClubs) allowedClubIds.add(club.getId());

            List<JoinRequest> requests = new ArrayList<>();
            for (QueryDocumentSnapshot doc : docs) {
                int clubId = getInt(doc, "clubId");
                if (allowedClubIds.contains(clubId)) {
                    requests.add(new JoinRequest(
                            doc.getId(),
                            clubId,
                            safe(doc.getString("clubName")),
                            safe(doc.getString("username")),
                            safe(doc.getString("status"))
                    ));
                }
            }
            return requests;
        } catch (Exception e) {
            throw new SQLException("Could not get join requests from Firebase.", e);
        }
    }

    public boolean approveJoinRequest(String requestId, String requestingUsername, boolean isStaff) throws SQLException {
        try {
            Firestore db = FirebaseService.getDatabase();
            DocumentSnapshot request = db.collection("join_requests").document(requestId).get().get();
            if (!request.exists()) return false;

            int clubId = getInt(request, "clubId");
            String username = request.getString("username");
            Club club = getClubById(clubId);
            if (club == null) return false;
            if (!isStaff && !club.isOwnedBy(requestingUsername)) return false;

            String memberDocId = clubId + "_" + username;
            Map<String, Object> memberData = new HashMap<>();
            memberData.put("clubId", clubId);
            memberData.put("username", username);
            db.collection("club_members").document(memberDocId).set(memberData).get();
            db.collection("join_requests").document(requestId).update("status", "approved").get();
            db.collection("clubs").document(String.valueOf(clubId)).update("members", club.getMembers() + 1).get();
            return true;
        } catch (Exception e) {
            throw new SQLException("Could not approve join request in Firebase.", e);
        }
    }

    public boolean denyJoinRequest(String requestId, String requestingUsername, boolean isStaff) throws SQLException {
        try {
            Firestore db = FirebaseService.getDatabase();
            DocumentSnapshot request = db.collection("join_requests").document(requestId).get().get();
            if (!request.exists()) return false;

            int clubId = getInt(request, "clubId");
            Club club = getClubById(clubId);
            if (club == null) return false;
            if (!isStaff && !club.isOwnedBy(requestingUsername)) return false;

            db.collection("join_requests").document(requestId).update("status", "denied").get();
            return true;
        } catch (Exception e) {
            throw new SQLException("Could not deny join request in Firebase.", e);
        }
    }

    public boolean deleteClub(int clubId, String requestingUsername, boolean isStaff) throws SQLException {
        try {
            if (!isStaff) return false;
            Club existing = getClubById(clubId);
            if (existing == null) return false;

            Firestore db = FirebaseService.getDatabase();

            for (QueryDocumentSnapshot event : db.collection("events").whereEqualTo("clubId", clubId).get().get().getDocuments()) {
                db.collection("events").document(event.getId()).delete().get();
            }
            for (QueryDocumentSnapshot member : db.collection("club_members").whereEqualTo("clubId", clubId).get().get().getDocuments()) {
                db.collection("club_members").document(member.getId()).delete().get();
            }
            for (QueryDocumentSnapshot request : db.collection("join_requests").whereEqualTo("clubId", clubId).get().get().getDocuments()) {
                db.collection("join_requests").document(request.getId()).delete().get();
            }
            for (QueryDocumentSnapshot alert : db.collection("club_alerts").whereEqualTo("clubId", clubId).get().get().getDocuments()) {
                db.collection("club_alerts").document(alert.getId()).delete().get();
            }

            db.collection("clubs").document(String.valueOf(clubId)).delete().get();
            return true;
        } catch (Exception e) {
            throw new SQLException("Could not delete club from Firebase.", e);
        }
    }

    private Club buildClub(DocumentSnapshot doc) {
        int id = getInt(doc, "id");
        String name = safe(doc.getString("name"));
        String description = safe(doc.getString("description"));
        String contactEmail = safe(doc.getString("contactEmail"));
        String meetingTime = safe(doc.getString("meetingTime"));
        int members = getInt(doc, "members");
        String ownerUsername = safe(doc.getString("ownerUsername"));
        boolean autoApproveMembers = getBoolean(doc, "autoApproveMembers");
        String imageUrl = safe(doc.getString("imageUrl"));

        List<String> categories = new ArrayList<>();
        Object rawCategories = doc.get("categories");
        if (rawCategories instanceof List<?>) {
            for (Object item : (List<?>) rawCategories) categories.add(String.valueOf(item));
        }

        return new Club(id, name, categories, members, description, contactEmail, meetingTime,
                ownerUsername, autoApproveMembers, imageUrl);
    }

    private int getNextId(String collectionName) throws Exception {
        Firestore db = FirebaseService.getDatabase();
        List<QueryDocumentSnapshot> docs = db.collection(collectionName).get().get().getDocuments();
        int highest = 0;
        for (QueryDocumentSnapshot doc : docs) {
            int id = getInt(doc, "id");
            if (id > highest) highest = id;
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

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
