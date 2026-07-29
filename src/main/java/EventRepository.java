import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EventRepository {

    public List<Event> getAllEvents() throws SQLException {
        try {
            Firestore db = FirebaseService.getDatabase();
            List<QueryDocumentSnapshot> docs = db.collection("events").get().get().getDocuments();

            List<Event> events = new ArrayList<>();
            for (QueryDocumentSnapshot doc : docs) {
                events.add(buildEvent(doc));
            }

            return events;
        } catch (Exception e) {
            throw new SQLException("Could not get events from Firebase.", e);
        }
    }

    public Event getEventById(int id) throws SQLException {
        try {
            Firestore db = FirebaseService.getDatabase();
            DocumentSnapshot doc = db.collection("events").document(String.valueOf(id)).get().get();

            if (!doc.exists()) {
                return null;
            }

            return buildEvent(doc);
        } catch (Exception e) {
            throw new SQLException("Could not get event from Firebase.", e);
        }
    }

    public int addEvent(String title, int clubId, String date, String location, String description,
                        String contactEmail, Integer rsvpCapacity) throws SQLException {
        try {
            Firestore db = FirebaseService.getDatabase();
            int id = getNextId("events");

            ClubRepository clubRepository = new ClubRepository();
            Club club = clubRepository.getClubById(clubId);
            String clubName = club == null ? "Unknown" : club.getName();

            Map<String, Object> data = new HashMap<>();
            data.put("id", id);
            data.put("title", title);
            data.put("clubId", clubId);
            data.put("clubName", clubName);
            data.put("date", date);
            data.put("location", location);
            data.put("description", description);
            data.put("contactEmail", contactEmail);
            data.put("rsvpCapacity", rsvpCapacity);

            db.collection("events").document(String.valueOf(id)).set(data).get();
            return id;
        } catch (Exception e) {
            throw new SQLException("Could not add event to Firebase.", e);
        }
    }

    public int getRsvpCount(int eventId) throws SQLException {
        try {
            Firestore db = FirebaseService.getDatabase();
            List<QueryDocumentSnapshot> docs = db.collection("rsvps")
                    .whereEqualTo("eventId", eventId)
                    .get()
                    .get()
                    .getDocuments();

            return docs.size();
        } catch (Exception e) {
            throw new SQLException("Could not get RSVP count from Firebase.", e);
        }
    }

    public boolean hasUserRsvped(int eventId, String username) throws SQLException {
        try {
            Firestore db = FirebaseService.getDatabase();
            String docId = eventId + "_" + username;

            DocumentSnapshot doc = db.collection("rsvps").document(docId).get().get();
            return doc.exists();
        } catch (Exception e) {
            throw new SQLException("Could not check RSVP in Firebase.", e);
        }
    }

    public enum RsvpResult {
        SUCCESS,
        ALREADY_RSVPED,
        EVENT_FULL,
        NOT_FOUND
    }

    public RsvpResult rsvp(int eventId, String username) throws SQLException {
        try {
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

            Firestore db = FirebaseService.getDatabase();
            String docId = eventId + "_" + username;

            Map<String, Object> data = new HashMap<>();
            data.put("eventId", eventId);
            data.put("username", username);

            db.collection("rsvps").document(docId).set(data).get();
            return RsvpResult.SUCCESS;
        } catch (Exception e) {
            throw new SQLException("Could not RSVP in Firebase.", e);
        }
    }

    private Event buildEvent(DocumentSnapshot doc) {
        int id = getInt(doc, "id");
        String title = doc.getString("title");
        int clubId = getInt(doc, "clubId");
        String clubName = doc.getString("clubName");
        String date = doc.getString("date");
        String location = doc.getString("location");
        String description = doc.getString("description");
        String contactEmail = doc.getString("contactEmail");

        Long capacityLong = doc.getLong("rsvpCapacity");
        Integer rsvpCapacity = capacityLong == null ? null : capacityLong.intValue();

        return new Event(id, title, clubId, clubName, date, location, description, contactEmail, rsvpCapacity);
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
}