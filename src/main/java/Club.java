import java.util.List;

public class Club {
    private int id;
    private String name;
    private String description;
    private List<String> categories;
    private String contactEmail;
    private String meetingTime;
    private int members;
    private String ownerUsername;
    private boolean autoApproveMembers;

    public Club(int id, String name, List<String> categories, int members,
                String description, String contactEmail, String meetingTime, String ownerUsername,
                boolean autoApproveMembers)
    {
        this.id = id;
        this.name = name;
        this.categories = categories;
        this.members = members;
        this.description = description;
        this.contactEmail = contactEmail;
        this.meetingTime = meetingTime;
        this.ownerUsername = ownerUsername;
        this.autoApproveMembers = autoApproveMembers;
    }

    void printInfo(){
        System.out.println("Club Name: " + name);
        System.out.println("Categories: " + getCategoriesText());
        System.out.println("Members: " + members);
        System.out.println("Description: " + description);
        System.out.println("Contact Email: " + contactEmail);
        System.out.println("Meeting Time: " + meetingTime);
        System.out.println("Owner: " + ownerUsername);
    }

    public int getId()
    {
        return id;
    }

    public String getName()
    {
        return name;
    }

    public List<String> getCategories()
    {
        return categories;
    }

    public String getCategoriesText()
    {
        return String.join(", ", categories);
    }

    public boolean hasCategory(String tag)
    {
        for (String category : categories)
        {
            if (category.equalsIgnoreCase(tag))
            {
                return true;
            }
        }
        return false;
    }

    public String getContactEmail()
    {
        return contactEmail;
    }

    public String getDescription()
    {
        return description;
    }

    public String getMeetingTime()
    {
        return meetingTime;
    }

    public int getMembers()
    {
        return members;
    }

    public String getOwnerUsername()
    {
        return ownerUsername;
    }

    public boolean isAutoApproveMembers()
    {
        return autoApproveMembers;
    }

    public boolean isOwnedBy(String username)
    {
        return ownerUsername != null && username != null && ownerUsername.equalsIgnoreCase(username);
    }
}
