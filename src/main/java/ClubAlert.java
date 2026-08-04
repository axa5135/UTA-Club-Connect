public class ClubAlert {
    private String id;
    private int clubId;
    private String clubName;
    private int eventId;
    private String title;
    private String message;
    private String createdBy;
    private String createdAt;

    public ClubAlert(String id, int clubId, String clubName, int eventId,
                     String title, String message, String createdBy, String createdAt) {
        this.id = id;
        this.clubId = clubId;
        this.clubName = clubName;
        this.eventId = eventId;
        this.title = title;
        this.message = message;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public int getClubId() { return clubId; }
    public String getClubName() { return clubName; }
    public int getEventId() { return eventId; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getCreatedBy() { return createdBy; }
    public String getCreatedAt() { return createdAt; }
}
