public class Club {
    private String name;
    private String description;
    private String category;
    private String contactEmail;
    private String meetingTime;
    private int members;


    Club(String clubName, String clubCategory, int clubMembers,
         String clubDescription, String clubContactEmail, String clubMeetingTime)
    {
       name = clubName;
       category = clubCategory;
       members = clubMembers;
       description = clubDescription;
       contactEmail = clubContactEmail;
       meetingTime = clubMeetingTime;
    }


    void printInfo(){
        System.out.println("Club Name: " + name);
        System.out.println("Category: " + category);
        System.out.println("Members: " + members);
        System.out.println("Description: " + description);
        System.out.println("Contact Email: " + contactEmail);
        System.out.println("Meeting Time: " + meetingTime);
    }

    String getName()
    {
        return name;
    }

    String getCategory()
    {
        return category;
    }

    String getContactEmail()
    {
        return contactEmail;
    }
}
