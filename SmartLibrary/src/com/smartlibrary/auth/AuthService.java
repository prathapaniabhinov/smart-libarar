package com.smartlibrary.auth;

import java.io.IOException;
import java.util.Optional;

import com.smartlibrary.data.UserCSV;
import com.smartlibrary.model.User;

/**
 * Validates credentials and keeps track of the logged-in user.
 * <p>
 * The role always comes from users.csv. There is no way to pass a role in at login time.
 */
public class AuthService {

    private static final String INVALID_MESSAGE = "Invalid username/email or password.";

    private final UserCSV store;
    private User currentUser;

    public AuthService(UserCSV store) {
        this.store = store;
    }

    /**
     * Logs a user in by username or email.
     *
     * @return the authenticated user (without password hash)
     */
    public synchronized User login(String identifier, char[] password) throws AuthException {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new AuthException(AuthException.Reason.MISSING_INPUT, "Please enter your username or email.");
        }
        if (password == null || password.length == 0) {
            throw new AuthException(AuthException.Reason.MISSING_INPUT, "Please enter your password.");
        }

        Optional<User> found;
        try {
            found = store.findByUsernameOrEmail(identifier);
        } catch (IOException e) {
            throw new AuthException(AuthException.Reason.STORAGE_ERROR,
                    "Could not read user data (" + e.getMessage() + ").");
        }

        if (found.isEmpty()) {
            PasswordUtil.dummyVerify(password); // same timing as a real check
            throw new AuthException(AuthException.Reason.INVALID_CREDENTIALS, INVALID_MESSAGE);
        }
        User user = found.get();
        if (!PasswordUtil.verify(password, user.getPasswordHash())) {
            throw new AuthException(AuthException.Reason.INVALID_CREDENTIALS, INVALID_MESSAGE);
        }
        // Status is checked only after the password, so inactive status is not revealed to guessers.
        if (!user.isActive()) {
            throw new AuthException(AuthException.Reason.ACCOUNT_INACTIVE,
                    "This account is inactive. Please contact the administrator.");
        }
        currentUser = user.withoutSecrets();
        return currentUser.copy();
    }

    /** Ends the session. After this every permission check fails until the next login. */
    public synchronized void logout() {
        currentUser = null;
    }

    public synchronized boolean isLoggedIn() {
        return currentUser != null;
    }

    /**
     * Returns the logged-in user re-read from users.csv, so role or status changes made since login
     * take effect immediately. Ends the session if the account was deactivated or removed.
     */
    public synchronized User requireCurrentUser() throws AuthException {
        if (currentUser == null) {
            throw new AuthException(AuthException.Reason.NOT_LOGGED_IN, "You are not logged in.");
        }
        Optional<User> fresh;
        try {
            fresh = store.findById(currentUser.getUserId());
        } catch (IOException e) {
            throw new AuthException(AuthException.Reason.STORAGE_ERROR,
                    "Could not read user data (" + e.getMessage() + ").");
        }
        if (fresh.isEmpty()) {
            logout();
            throw new AuthException(AuthException.Reason.NOT_LOGGED_IN, "Your account no longer exists.");
        }
        if (!fresh.get().isActive()) {
            logout();
            throw new AuthException(AuthException.Reason.ACCOUNT_INACTIVE,
                    "Your account has been deactivated. You have been logged out.");
        }
        currentUser = fresh.get().withoutSecrets();
        return currentUser.copy();
    }
}
