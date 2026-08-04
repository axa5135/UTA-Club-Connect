import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AlertRepository {

    public int addAlert(int clubId, String clubName, int eventId, String title,
                        String message, String createdBy) throws SQLException {
        try {
            Firestore db = FirebaseService.getDatabase();
            int id = getNextId();

            Map<String, Object> data = new HashMap<>();
            data.put("id", id);
            data.put("clubId", clubId);
            data.put("clubName", clubName == null ? "" : clubName);
            data.put("eventId", eventId);
            data.put("title", title);
            data.put("message", message);
            data.put("createdBy", createdBy);
            data.put("createdAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")));

            db.collection("club_alerts").document(String.valueOf(id)).set(data).get();
            return id;
        } catch (Exception e) {
            throw new SQLException("Could not send alert in Firebase.", e);
        }
    }

    public List<ClubAlert> getAlertsForClub(int clubId) throws SQLException {
        try {
            Firestore db = FirebaseService.getDatabase();
            List<QueryDocumentSnapshot> docs = db.collection("club_alerts")
                    .whereEqualTo("clubId", clubId)
                    .get()
                    .get()
                    .getDocuments();

            List<ClubAlert> alerts = new ArrayList<>();
            for (QueryDocumentSnapshot doc : docs) {
                alerts.add(buildAlert(doc));
            }

            alerts.sort((a, b) -> b.getId().compareTo(a.getId()));
            return alerts;
        } catch (Exception e) {
            throw new SQLException("Could not get club alerts from Firebase.", e);
        }
    }

    public List<ClubAlert> getAlertsForMember(String username, ClubRepository clubRepository) throws SQLException {
        List<Club> joinedClubs = clubRepository.getClubsJoinedBy(username);
        List<ClubAlert> allAlerts = new ArrayList<>();

        for (Club club : joinedClubs) {
            allAlerts.addAll(getAlertsForClub(club.getId()));
        }

        allAlerts.sort((a, b) -> b.getId().compareTo(a.getId()));
        return allAlerts;
    }

    public List<ClubAlert> getAllAlerts() throws SQLException {
        try {
            Firestore db = FirebaseService.getDatabase();
            List<QueryDocumentSnapshot> docs = db.collection("club_alerts").get().get().getDocuments();

            List<ClubAlert> alerts = new ArrayList<>();
            for (QueryDocumentSnapshot doc : docs) {
                alerts.add(buildAlert(doc));
            }

            alerts.sort((a, b) -> b.getId().compareTo(a.getId()));
            return alerts;
        } catch (Exception e) {
            throw new SQLException("Could not get alerts from Firebase.", e);
        }
    }

    public boolean deleteAlert(String alertId, String requestingUsername, boolean isStaff,
                               ClubRepository clubRepository) throws SQLException {
        try {
            Firestore db = FirebaseService.getDatabase();
            DocumentSnapshot doc = db.collection("club_alerts").document(alertId).get().get();

            if (!doc.exists()) return false;

            int clubId = getInt(doc, "clubId");
            Club club = clubRepository.getClubById(clubId);

            if (!isStaff && (club == null || !club.isOwnedBy(requestingUsername))) {
                return false;
            }

            db.collection("club_alerts").document(alertId).delete().get();
            return true;
        } catch (Exception e) {
            throw new SQLException("Could not delete alert from Firebase.", e);
        }
    }

    private ClubAlert buildAlert(DocumentSnapshot doc) {
        return new ClubAlert(
                doc.getId(),
                getInt(doc, "clubId"),
                safe(doc.getString("clubName")),
                getInt(doc, "eventId"),
                safe(doc.getString("title")),
                safe(doc.getString("message")),
                safe(doc.getString("createdBy")),
                safe(doc.getString("createdAt"))
        );
    }

    private int getNextId() throws Exception {
        Firestore db = FirebaseService.getDatabase();
        List<QueryDocumentSnapshot> docs = db.collection("club_alerts").get().get().getDocuments();

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

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
