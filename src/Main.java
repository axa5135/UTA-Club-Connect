import java.util.ArrayList;
import java.util.Scanner;

public class Main
{
    public static void main(String[] args)
    {
        ArrayList<Club> clubs = new ArrayList<>();
        ArrayList<Event> events = new ArrayList<>();
        Scanner input = new Scanner (System.in);
        boolean running = true;

        // entering the available clubs
        loadClubs(clubs);

        // entering available events
        loadEvents(events);

        while (running)
        {

            // menu
            showMenu();

            // reads the user's input
            int choice = input.nextInt();
            input.nextLine();

            //printing all clubs
            if (choice == 1)
            {
               showAllClubs(clubs);
            }

            // searching club by name
            else if (choice == 2)
            {
                searchClubsByName(clubs, input);
            }

            // filtering by category
            else if (choice == 3)
            {
                filterClubByCategory(clubs, input);
            }

            //show events
            else if (choice == 4)
            {
                showAllEvents(events);
            }

            //search events by club name
            else if (choice == 5)
            {
                searchEventsByClubName(events, input);
            }

            else if (choice == 6)
            {
                rsvpToEvent(events, input);
            }

            // request joining to club
            else if (choice == 7)
            {
                requestToJoinClub(clubs, input);
            }

            else if(choice == 8)
            {
                addNewClub(clubs, input);
            }


            else if(choice == 9)
            {
                addNewEvent(events, input);
            }

            //exit
            else if (choice == 10)
            {
                System.out.println("Goodbye!");
                running = false;
            }
            else
            {
                System.out.println("Invalid option. Please choose 1-10.");
            }
        }
    }

    public static void showAllClubs(ArrayList<Club> clubs)
    {
        for (Club club : clubs)
        {
            club.printInfo();
            System.out.println("---------------------");
        }
    }

    public static void searchClubsByName(ArrayList<Club> clubs, Scanner input)
    {
        System.out.println("Enter club name you are looking for:\n");
        String searchName = input.nextLine();

        System.out.println("Search result:");
        boolean found = false;
        for (Club club : clubs)
        {
            if (club.getName().toLowerCase().contains(searchName.toLowerCase()))
            {
                club.printInfo();
                System.out.println("--------------------");
                found = true;
            }
        }

        if (!found)
        {
            System.out.println("No club found with that name.");
        }
    }

    public static void filterClubByCategory(ArrayList<Club> clubs, Scanner input)
    {
        System.out.println();
        System.out.print("Enter category to filter: ");
        String searchCategory = input.nextLine();
        boolean categoryFound = false;
        for (Club club : clubs)
        {
            if (club.getCategory().toLowerCase().contains(searchCategory.toLowerCase()))
            {
                club.printInfo();
                System.out.println("--------------------");
                categoryFound = true;
            }
        }
        if (!categoryFound)
        {
            System.out.println("Category not found.");
        }
    }

    public static void showAllEvents(ArrayList<Event> events)
    {
        for (Event event : events)
        {
            event.printInfo();
            System.out.println("-------------------");
        }
    }

    public static void searchEventsByClubName (ArrayList<Event> events, Scanner input)
    {
        System.out.println("Enter club name you are looking for:\n");
        String searchName = input.nextLine();

        System.out.println("Search result:");
        boolean found = false;
        for (Event event : events) {
            if (event.getClubName().toLowerCase().contains(searchName.toLowerCase()))
            {
                event.printInfo();
                System.out.println("--------------------");
                found = true;
            }
        }
        if (!found)
        {
            System.out.println("No events found for that club.");
        }
    }

    public static void loadClubs(ArrayList<Club> clubs)
    {
        Club club1 = new Club(
                "Robotics",
                "Technology",
                35,
                "A club for students interested in building and" +
                        " programming robots.",
                "robotics@mavs.uta.edu",
                "Wednesdays at 5:00 PM"
        );
        clubs.add(club1);

        Club club2 = new Club(
                "Dance",
                "Entertainment",
                20,
                "A club for students interested in dancing" +
                        " and performances.",
                "dance@mavs.uta.edu",
                "Saturday at 3:00 PM"
        );
        clubs.add(club2);

        Club club3 = new Club(
                "Chess",
                "Sport",
                50,
                "A club for students who enjoy chess and strategy.",
                "chess@mavs.uta.edu",
                "Friday at 5:00 PM"
        );
        clubs.add(club3);
    }

    public static void loadEvents(ArrayList<Event> events)
    {
        Event event1 = new Event(
                "Robotics Build Night",
                "Robotics",
                "August 1, 2026",
                "ERB 200",
                "Students will work together to build" +
                        " and test simple robot designs.",
                12
        );
        events.add(event1);

        Event event2 = new Event(
                "Dance Showcase",
                "Dance",
                "August 3, 2026",
                "University Center",
                "Students will have good time dancing.",
                32
        );
        events.add(event2);

        Event event3 = new Event(
                "Chess Tournament",
                "Chess",
                "August 5, 2026",
                "Central Library",
                "Good opportunity for chess players to" +
                        " compete with other students.",
                16
        );
        events.add(event3);
    }

    public static void showMenu()
    {
        System.out.println("Welcome to UTA Club Connect");
        System.out.println("1. Show all clubs");
        System.out.println("2. Search club by name");
        System.out.println("3. Filter clubs by category");
        System.out.println("4. Show all events");
        System.out.println("5. Search events by club name");
        System.out.println("6. RSVP to an event");
        System.out.println("7. Request to join a club");
        System.out.println("8. Add a new club");
        System.out.println("9. Add a new event");
        System.out.println("10. Exit");
        System.out.print("Choose an option: ");
    }

    public static void rsvpToEvent(ArrayList<Event> events, Scanner input)
    {
        System.out.println("Available events:");
        showAllEvents(events);
        System.out.print("Enter event title to RSVP: ");
        String eventTitle = input.nextLine();
        boolean found = false;
        for (Event event : events)
        {
            if (event.getTitle().toLowerCase().contains(eventTitle.toLowerCase())) {
                System.out.println("Event found:");
                event.addRsvp();
                event.printInfo();
                System.out.print("---------------\n");
                found = true;
            }
        }
        if (!found)
        {
            System.out.println("No event found with that title.");
        }
    }

    public static void requestToJoinClub(ArrayList<Club> clubs, Scanner input)
    {
        System.out.println("Available clubs:");
        showAllClubs(clubs);
        System.out.print("Enter club name to request joining: ");
        String clubName = input.nextLine();
        boolean found = false;

        for (Club club : clubs)
        {
            if (club.getName().toLowerCase().contains(clubName.toLowerCase()))
            {
                System.out.println("Join request sent for " + club.getName() + ".");
                System.out.println("Club contact: " + club.getContactEmail());
                found = true;
            }
        }
        if (!found)
        {
            System.out.println("No club found with that name.");
        }
    }

    public static void addNewClub(ArrayList<Club> clubs, Scanner input)
    {
        System.out.print("Enter club name: ");
        String name = input.nextLine();
        System.out.print("Enter club category: ");
        String category = input.nextLine();
        System.out.print("Enter number of members: ");
        int members = input.nextInt();
        input.nextLine();
        System.out.print("Enter club description: ");
        String description = input.nextLine();
        System.out.print("Enter contact email: ");
        String contactEmail = input.nextLine();
        System.out.print("Enter meeting time: ");
        String meetingTime = input.nextLine();
        Club newClub = new Club(
                name,
                category,
                members,
                description,
                contactEmail,
                meetingTime
        );
        clubs.add(newClub);
        System.out.println("New club added successfully.");
    }

    public static void addNewEvent(ArrayList<Event> events, Scanner input) {
        System.out.print("Enter event title: ");
        String title = input.nextLine();
        System.out.print("Enter event club name: ");
        String clubName = input.nextLine();
        System.out.print("Enter event date: ");
        String date = input.nextLine();
        System.out.print("Enter event location: ");
        String location = input.nextLine();
        System.out.print("Enter event description: ");
        String description = input.nextLine();
        System.out.print("Enter rsvp: ");
        int rsvpCount = input.nextInt();
        input.nextLine();
        Event newEvent = new Event(
                title,
                clubName,
                date,
                location,
                description,
                rsvpCount
        );
        events.add(newEvent);
        System.out.println("New event added successfully.");

    }
}