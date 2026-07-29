package main.java;

public class Event {
    private int id;
    private String title;
    private int clubId;
    private String clubName;
    private String date;
    private String location;
    private String description;
    private String contactEmail;
    private Integer rsvpCapacity; // null means no capacity limit

    public Event(int id, String title, int clubId, String clubName,
                  String date, String location, String description,
                  String contactEmail, Integer rsvpCapacity)
    {
        this.id = id;
        this.title = title;
        this.clubId = clubId;
        this.clubName = clubName;
        this.date = date;
        this.location = location;
        this.description = description;
        this.contactEmail = contactEmail;
        this.rsvpCapacity = rsvpCapacity;
    }

    void printInfo()
    {
        System.out.println("Event: " + title);
        System.out.println("Club name: " + clubName);
        System.out.println("Date: " + date);
        System.out.println("Location: " + location);
        System.out.println("Description: " + description);
    }

    public int getId()
    {
        return id;
    }

    public String getTitle()
    {
        return title;
    }

    public int getClubId()
    {
        return clubId;
    }

    public String getClubName()
    {
        return clubName;
    }

    public String getDate()
    {
        return date;
    }

    public String getLocation()
    {
        return location;
    }

    public String getDescription()
    {
        return description;
    }

    public String getContactEmail()
    {
        return contactEmail;
    }

    // Null means no capacity limit was set
    public Integer getRsvpCapacity()
    {
        return rsvpCapacity;
    }

    public boolean hasCapacity()
    {
        return rsvpCapacity != null && rsvpCapacity > 0;
    }
}
