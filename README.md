UTA Club Connect

UTA Club Connect is a web application that helps students at the University of Texas at Arlington discover student clubs and events, join clubs, RSVP to events, and stay updated through club alerts. It also gives club leaders and staff tools to manage clubs, events, membership requests, and announcements.

This project was built for CSE 3310 – Fundamentals of Software Engineering at UTA, taught by Dr. Marika Apostolova.

Team

- Aden Elwell
- Purnima Nagwag
- Ayush Adhikari
- Ramzeddine Khalifa

Features

- Browse and search clubs and events, including filtering by category
- User accounts with three permission levels:
    - Student – browse clubs/events, join clubs, RSVP to events, view alerts
    - Club President – everything a student can do, plus manage their own club(s), post events, send alerts, and review join requests
    - UTA Staff – full admin access: create/edit/delete any club, assign club leaders, promote/demote users, moderate alerts, and change site settings
- Club membership via direct join or an approval-based join request flow (clubs can be configured to auto-approve members)
- Event RSVPs, including optional RSVP capacity limits
- Club alerts/announcements that club leaders and staff can send to members
- Custom images for club pages, event pages, and the homepage hero banner
- Simple session-based login using cookies (no third-party auth)

Tech Stack

- Language: Java
- Web server: Built directly on Java's built-in `com.sun.net.httpserver.HttpServer` — no external web framework. HTML is generated server-side and returned directly in the HTTP response.
- Database: [Firebase Firestore](https://firebase.google.com/docs/firestore) via the Firebase Admin SDK for Java
- Password storage: Passwords are hashed with SHA-256 before being stored. This is a lightweight, approach and is not intended for production use.
- Sessions: Stored in memory and mapped to a random token in a cookie. Sessions reset if the server restarts — acceptable for the scope of this project.

Project Structure

File(s) - Purpose
`Main.java` - Application entry point; sets up the database, seeds starter data, and starts the web server
`WebsiteServer.java` - Defines all HTTP routes and renders the site's HTML pages |
`Database.java` - Verifies the Firebase connection on startup |
`FirebaseService.java` - Initializes and provides access to the Firestore client |
`User.java` / `UserRepository.java` - User model and data access (accounts, roles, login) |
`Club.java` / `ClubRepository.java` - Club model and data access (clubs, membership, join requests) |
`Event.java` / `EventRepository.java` - Event model and data access (events, RSVPs) |
`ClubAlert.java` / `AlertRepository.java` - Club alert/announcement model and data access |
`SessionManager.java` - Tracks logged-in sessions via tokens |
`PasswordUtil.java` - Hashes passwords with SHA-256 |
`Tags.java` - The fixed list of club category tags used throughout the site |

Getting Started

This project doesn't use a build tool like Maven or Gradle — it was developed and run directly through IntelliJ IDEA by running `Main.java`. You'll need the Firebase Admin SDK for Java on your classpath in order to build/run it (either by adding it as a project library in IntelliJ or with `javac`/`java` and the relevant JARs on your classpath).

Firebase Setup

The app connects to a Firebase Firestore project for storage. You'll need a Firebase service account key saved as `firebase-key.json` in the project root before running the app — without it, `Database.initializeSchema()` will fail on startup.

Running the App

1. Make sure `firebase-key.json` is in place.
2. Run `Main.java`.
3. The site will be available at `http://localhost:8080`.

On first run, if no users exist yet, the app seeds a few starter accounts and sample clubs/events automatically.

Test Accounts

All seeded accounts use the password `password123`:

Username - Name - Role
`staff_admin` - Staff Admin - UTA Staff (Admin)
`robotics_pres` - Robotics President - Club President
`student1` - Student One - Student

Notes and Limitations
- This project was built for a class assignment, and some design choices (in-memory sessions, unsalted password hashing, seeding starter data on first run) reflect that scope rather than production-readiness.
- There is no automated database migration system — Firestore collections are created implicitly as data is written.