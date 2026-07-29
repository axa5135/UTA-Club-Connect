import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Tracks who's logged in. Each logged-in browser gets a random token stored
// in a cookie; this class maps that token back to a username. Sessions live
// only in memory, so everyone is logged out if the server restarts - that's
// fine for a class project.
public class SessionManager {
    private final Map<String, String> tokenToUsername = new ConcurrentHashMap<>();

    public String createSession(String username) {
        String token = UUID.randomUUID().toString();
        tokenToUsername.put(token, username);
        return token;
    }

    public String getUsername(String token) {
        if (token == null) {
            return null;
        }
        return tokenToUsername.get(token);
    }

    public void invalidate(String token) {
        if (token != null) {
            tokenToUsername.remove(token);
        }
    }
}
