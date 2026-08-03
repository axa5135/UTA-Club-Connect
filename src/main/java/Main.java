import java.sql.SQLException;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        try {
            Database.initializeSchema();
        } catch (SQLException e) {
            System.out.println("Could not set up the database: " + e.getMessage());
            return;
        }

        UserRepository userRepository = new UserRepository();
        ClubRepository clubRepository = new ClubRepository();
        EventRepository eventRepository = new EventRepository();
        SessionManager sessionManager = new SessionManager();

        try {
            seedDataIfEmpty(userRepository, clubRepository, eventRepository);
        } catch (SQLException e) {
            System.out.println("Could not seed starter data: " + e.getMessage());
        }

        try {
            WebsiteServer website = new WebsiteServer(8080, clubRepository, eventRepository, userRepository, sessionManager);
            website.start();
            System.out.println();
            System.out.println("Test accounts (all use the password 'password123'):");
            System.out.println("  staff_admin    - Staff Admin, Level 3, Admin");
            System.out.println("  robotics_pres  - Robotics President, Level 2, Club Leader");
            System.out.println("  student1       - Student One, Level 1, Member");
            System.out.println();
            System.out.println("Press Ctrl+C to stop the server.");
        } catch (Exception error) {
            System.out.println("Website could not start: " + error.getMessage());
        }
    }

    public static void seedDataIfEmpty(UserRepository userRepository, ClubRepository clubRepository,
                                       EventRepository eventRepository) throws SQLException {
        if (userRepository.usernameExists("staff_admin")) {
            return;
        }

        userRepository.createUser("staff_admin", "Staff Admin", "password123", User.LEVEL_UTA_STAFF);
        userRepository.createUser("robotics_pres", "Robotics President", "password123", User.LEVEL_CLUB_PRESIDENT);
        userRepository.createUser("student1", "Student One", "password123", User.LEVEL_STUDENT);

        int roboticsId = clubRepository.addClub(
                "Robotics",
                List.of("Technology"),
                35,
                "A club for students interested in building and programming robots.",
                "robotics@mavs.uta.edu",
                "Wednesdays at 5:00 PM",
                "robotics_pres",
                false,
                "https://images.unsplash.com/photo-1518770660439-4636190af475?auto=format&fit=crop&w=900&q=80"
        );

        int danceId = clubRepository.addClub(
                "Dance",
                List.of("Dance", "Arts & Culture"),
                20,
                "A club for students interested in dancing and performances.",
                "dance@mavs.uta.edu",
                "Saturday at 3:00 PM",
                "staff_admin",
                false,
                "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?auto=format&fit=crop&w=900&q=80"
        );

        int chessId = clubRepository.addClub(
                "Chess",
                List.of("Academic", "Gaming"),
                50,
                "A club for students who enjoy chess and strategy.",
                "chess@mavs.uta.edu",
                "Friday at 5:00 PM",
                "staff_admin",
                true,
                "https://images.unsplash.com/photo-1529699211952-734e80c4d42b?auto=format&fit=crop&w=900&q=80"
        );

        eventRepository.addEvent(
                "Robotics Build Night", roboticsId, "August 1, 2026", "ERB 200",
                "Students will work together to build and test simple robot designs.",
                "robotics@mavs.uta.edu", null,
                "https://images.unsplash.com/photo-1485827404703-89b55fcc595e?auto=format&fit=crop&w=900&q=80"
        );

        eventRepository.addEvent(
                "Dance Showcase", danceId, "August 3, 2026", "University Center",
                "Students will have a good time dancing.",
                "dance@mavs.uta.edu", 40,
                "https://images.unsplash.com/photo-1547153760-18fc86324498?auto=format&fit=crop&w=900&q=80"
        );

        eventRepository.addEvent(
                "Chess Tournament", chessId, "August 5, 2026", "Library Room 101",
                "Students can compete in friendly chess matches.",
                "chess@mavs.uta.edu", 24,
                "https://images.unsplash.com/photo-1580541832626-2a7131ee809f?auto=format&fit=crop&w=900&q=80"
        );
    }
}
