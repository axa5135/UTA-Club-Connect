public class Event {
    private int id;
    private String title;
    private int clubId;
    private String clubName;
    private String date;
    private String location;
    private String description;
    private String contactEmail;
    private Integer rsvpCapacity;
    private String imageUrl;

    public Event(int id, String title, int clubId, String clubName,
                 String date, String location, String description,
                 String contactEmail, Integer rsvpCapacity) {
        this(id, title, clubId, clubName, date, location, description, contactEmail, rsvpCapacity, "");
    }

    public Event(int id, String title, int clubId, String clubName,
                 String date, String location, String description,
                 String contactEmail, Integer rsvpCapacity, String imageUrl) {
        this.id = id;
        this.title = title;
        this.clubId = clubId;
        this.clubName = clubName;
        this.date = date;
        this.location = location;
        this.description = description;
        this.contactEmail = contactEmail;
        this.rsvpCapacity = rsvpCapacity;
        this.imageUrl = imageUrl == null ? "" : imageUrl;
    }

    void printInfo() {
        System.out.println("Event: " + title);
        System.out.println("Club name: " + clubName);
        System.out.println("Date: " + date);
        System.out.println("Location: " + location);
        System.out.println("Description: " + description);
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public int getClubId() { return clubId; }
    public String getClubName() { return clubName; }
    public String getDate() { return date; }
    public String getLocation() { return location; }
    public String getDescription() { return description; }
    public String getContactEmail() { return contactEmail; }
    public Integer getRsvpCapacity() { return rsvpCapacity; }
    public String getImageUrl() { return imageUrl; }

    public boolean hasCapacity() {
        return rsvpCapacity != null && rsvpCapacity > 0;
    }
}
