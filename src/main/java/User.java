public class User {
    public static final int LEVEL_STUDENT = 1;
    public static final int LEVEL_CLUB_PRESIDENT = 2;
    public static final int LEVEL_UTA_STAFF = 3;

    private String username;
    private String fullName;
    private String passwordHash;
    private int role;

    public User(String username, String passwordHash, int role) {
        this(username, guessFullName(username), passwordHash, role);
    }

    public User(String username, String fullName, String passwordHash, int role) {
        this.username = username;
        this.fullName = (fullName == null || fullName.isBlank()) ? guessFullName(username) : fullName;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public String getUsername() { return username; }
    public String getFullName() { return fullName; }
    public String getPasswordHash() { return passwordHash; }
    public int getRole() { return role; }

    public boolean isAtLeast(int minimumRole) {
        return role >= minimumRole;
    }

    public String getRoleName() {
        if (role == LEVEL_UTA_STAFF) return "Admin";
        if (role == LEVEL_CLUB_PRESIDENT) return "Club Leader";
        return "Member";
    }

    public static String guessFullName(String username) {
        if (username == null || username.isBlank()) return "Unknown User";
        if (username.equalsIgnoreCase("staff_admin")) return "Staff Admin";
        if (username.equalsIgnoreCase("robotics_pres")) return "Robotics President";
        if (username.equalsIgnoreCase("student1")) return "Student One";
        if (username.equalsIgnoreCase("ayush_adhikari")) return "Ayush Adhikari";

        String cleaned = username.replace("_", " ").replace(".", " ").replace("-", " ");
        StringBuilder result = new StringBuilder();

        for (String part : cleaned.split(" ")) {
            if (!part.isBlank()) {
                result.append(part.substring(0, 1).toUpperCase());
                if (part.length() > 1) {
                    result.append(part.substring(1).toLowerCase());
                }
                result.append(" ");
            }
        }

        return result.toString().trim().isEmpty() ? username : result.toString().trim();
    }
}
