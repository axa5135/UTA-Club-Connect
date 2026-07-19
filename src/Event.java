public class Event {
    private String title;
    private String clubName;
    private String date;
    private String location;
    private String description;
    private int rsvpCount;

    Event (String eventTitle, String eventClubName, String eventDate,
           String eventLocation, String eventDescription, int eventRsvpCount)
    {
        title = eventTitle;
        clubName = eventClubName;
        date = eventDate;
        location = eventLocation;
        description = eventDescription;
        rsvpCount = eventRsvpCount;
    }

    void printInfo()
    {
        System.out.println("Event: " + title);
        System.out.println("Club name: " + clubName);
        System.out.println("Date: " + date);
        System.out.println("Location: " + location);
        System.out.println("Description: " + description);
        System.out.println("RSVP Count: " + rsvpCount);
    }

    String getTitle()
    {
        return title;
    }

    String getClubName()
    {
        return clubName;
    }

    int getRsvpCount()
    {
        return rsvpCount;
    }

    void addRsvp()
    {
        rsvpCount = rsvpCount + 1;
    }
}
