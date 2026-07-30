public class User {
    public static final int LEVEL_STUDENT = 1;
    public static final int LEVEL_CLUB_PRESIDENT = 2;
    public static final int LEVEL_UTA_STAFF = 3;

    private String username;
    private String passwordHash;
    private int role;

    public User(String username, String passwordHash, int role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public String getUsername() { return username; }
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
}
