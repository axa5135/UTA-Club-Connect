package main.java;

import java.sql.SQLException;
import java.util.List;

public class Main
{
    public static void main(String[] args)
    {
        try
        {
            Database.initializeSchema();
        }
        catch (SQLException e)
        {
            System.out.println("Could not set up the database: " + e.getMessage());
            return;
        }

        UserRepository userRepository = new UserRepository();
        ClubRepository clubRepository = new ClubRepository();
        EventRepository eventRepository = new EventRepository();
        SessionManager sessionManager = new SessionManager();

        try
        {
            seedDataIfEmpty(userRepository, clubRepository, eventRepository);
        }
        catch (SQLException e)
        {
            System.out.println("Could not seed starter data: " + e.getMessage());
        }

        try
        {
            WebsiteServer website = new WebsiteServer(8080, clubRepository, eventRepository, userRepository, sessionManager);
            website.start();
            System.out.println();
            System.out.println("Test accounts (all use the password 'password123'):");
            System.out.println("  staff_admin    - Level 3, UTA Staff");
            System.out.println("  robotics_pres  - Level 2, Club President (owns Robotics)");
            System.out.println("  student1       - Level 1, Student");
            System.out.println();
            System.out.println("Press Ctrl+C to stop the server.");
        }
        catch (Exception error)
        {
            System.out.println("Website could not start: " + error.getMessage());
        }
    }

    // Only runs the very first time - if the database already has data, this does nothing,
    // so restarting the app never wipes out anything the team has added.
    public static void seedDataIfEmpty(UserRepository userRepository, ClubRepository clubRepository,
                                       EventRepository eventRepository) throws SQLException
    {
        if (userRepository.usernameExists("staff_admin"))
        {
            return; // already seeded on a previous run
        }

        userRepository.createUser("staff_admin", "password123", User.LEVEL_UTA_STAFF);
        userRepository.createUser("robotics_pres", "password123", User.LEVEL_CLUB_PRESIDENT);
        userRepository.createUser("student1", "password123", User.LEVEL_STUDENT);

        int roboticsId = clubRepository.addClub(
                "Robotics",
                List.of("Technology"),
                35,
                "A club for students interested in building and programming robots.",
                "robotics@mavs.uta.edu",
                "Wednesdays at 5:00 PM",
                "robotics_pres",
                true // auto-approve: joining is instant through the site
        );

        int danceId = clubRepository.addClub(
                "Dance",
                List.of("Dance", "Arts & Culture"),
                20,
                "A club for students interested in dancing and performances.",
                "dance@mavs.uta.edu",
                "Saturday at 3:00 PM",
                "staff_admin",
                false // not auto-approved: joining requires emailing the contact
        );

        int chessId = clubRepository.addClub(
                "Chess",
                List.of("Academic", "Gaming"),
                50,
                "A club for students who enjoy chess and strategy.",
                "chess@mavs.uta.edu",
                "Friday at 5:00 PM",
                "staff_admin",
                true
        );

        eventRepository.addEvent(
                "Robotics Build Night", roboticsId, "August 1, 2026", "ERB 200",
                "Students will work together to build and test simple robot designs.",
                "robotics@mavs.uta.edu", null
        );
        eventRepository.addEvent(
                "Dance Showcase", danceId, "August 3, 2026", "University Center",
                "Students will have a good time dancing.",
                "dance@mavs.uta.edu", 40 // capped at 40 RSVPs
        );
        eventRepository.addEvent(
                "Chess Tournament", chessId, "August 5, 2026", "Central Library",
                "Good opportunity for chess players to compete with other students.",
                "chess@mavs.uta.edu", null
        );
    }
}
