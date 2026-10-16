package com.smartlibrary.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import com.smartlibrary.auth.AuthException;
import com.smartlibrary.auth.AuthService;
import com.smartlibrary.auth.InitialSetup;
import com.smartlibrary.auth.PasswordUtil;
import com.smartlibrary.auth.Role;
import com.smartlibrary.data.DuplicateUserException;
import com.smartlibrary.data.UserCSV;
import com.smartlibrary.model.AccountStatus;
import com.smartlibrary.model.User;

/**
 * User management and permission checks.
 * <p>
 * Every admin operation re-reads the logged-in user from users.csv and checks the stored role,
 * so hiding buttons in the UI is never the only protection.
 * Users returned from this service never contain password hashes.
 */
public class UserService {

    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9._-]{3,30}$");
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final int MAX_NAME = 100;
    private static final int MAX_EMAIL = 254;

    private final UserCSV store;
    private final AuthService auth;
    private final InitialSetup setup;

    public UserService(UserCSV store, AuthService auth, InitialSetup setup) {
        this.store = store;
        this.auth = auth;
        this.setup = setup;
    }

    // ================================================================ first-run setup

    /** True if at least one ADMIN record exists in users.csv (active or not). */
    public boolean adminExists() throws ServiceException {
        try {
            for (User u : store.loadAll()) {
                if (u.getRole() == Role.ADMIN) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            throw storage(e);
        }
    }

    /** Generates the one-time setup code if no Admin exists yet. */
    public void prepareInitialSetup() throws ServiceException {
        if (adminExists()) {
            throw new ServiceException(ServiceException.Kind.ACCESS_DENIED, "Initial setup has already been completed.");
        }
        try {
            setup.prepare();
            setup.printCodeToConsole();
        } catch (IOException e) {
            throw storage(e);
        }
    }

    /** Where the setup code can be found, for display on the setup screen. */
    public String describeSetupCodeLocation() {
        return setup.usesEnvironmentSecret()
                ? "the " + InitialSetup.SECRET_ENV + " environment variable"
                : setup.getCodeFile().toString();
    }

    /**
     * Creates the single initial Admin account. Requires the one-time setup code and only works
     * while no Admin exists.
     */
    public User createInitialAdmin(String setupCode, String name, String username, String email,
                                   char[] password, char[] confirmPassword) throws ServiceException {
        synchronized (store) {
            if (adminExists()) {
                throw new ServiceException(ServiceException.Kind.ACCESS_DENIED,
                        "Initial setup has already been completed.");
            }
            try {
                if (!setup.verify(setupCode)) {
                    throw new ServiceException(ServiceException.Kind.ACCESS_DENIED, "The setup code is incorrect.");
                }
            } catch (IOException e) {
                throw storage(e);
            }
            String cleanName = validateName(name);
            String cleanUsername = validateUsername(username);
            String cleanEmail = validateEmail(email);
            validateNewPassword(password, confirmPassword);

            User admin = new User(0, cleanName, cleanUsername, cleanEmail, PasswordUtil.hash(password),
                    Role.ADMIN, AccountStatus.ACTIVE, false);
            User stored = insert(admin);
            try {
                setup.consume();
            } catch (IOException e) {
                // The Admin exists, so setup is disabled anyway; the stale file is harmless.
            }
            return stored.withoutSecrets();
        }
    }

    // ================================================================ admin operations

    public List<User> listUsers() throws ServiceException {
        requireAdmin();
        try {
            List<User> result = new ArrayList<>();
            for (User u : store.loadAll()) {
                result.add(u.withoutSecrets());
            }
            return result;
        } catch (IOException e) {
            throw storage(e);
        }
    }

    /** Case-insensitive search on name or username. A blank query returns all users. */
    public List<User> searchUsers(String query) throws ServiceException {
        List<User> all = listUsers();
        if (query == null || query.trim().isEmpty()) {
            return all;
        }
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<User> result = new ArrayList<>();
        for (User u : all) {
            if (u.getName().toLowerCase(Locale.ROOT).contains(q)
                    || u.getUsername().toLowerCase(Locale.ROOT).contains(q)) {
                result.add(u);
            }
        }
        return result;
    }

    /**
     * Creates a Student, Teacher or Librarian account with a temporary password.
     * The new user must change the password on first login.
     */
    public User createUser(String name, String username, String email, char[] temporaryPassword,
                           Role role, AccountStatus status) throws ServiceException {
        requireAdmin();
        requireAssignableRole(role);
        String cleanName = validateName(name);
        String cleanUsername = validateUsername(username);
        String cleanEmail = validateEmail(email);
        String pwError = PasswordUtil.validateStrength(temporaryPassword);
        if (pwError != null) {
            throw validation("Temporary password: " + pwError);
        }
        User user = new User(0, cleanName, cleanUsername, cleanEmail, PasswordUtil.hash(temporaryPassword),
                role, status == null ? AccountStatus.ACTIVE : status, true);
        return insert(user).withoutSecrets();
    }

    /**
     * Updates a non-Admin account. If {@code newTemporaryPassword} is non-empty the password is reset
     * and the user must change it at next login; otherwise the password is unchanged.
     */
    public User updateUser(long userId, String name, String username, String email, Role role,
                           AccountStatus status, char[] newTemporaryPassword) throws ServiceException {
        requireAdmin();
        User target = loadTarget(userId);
        if (target.getRole() == Role.ADMIN) {
            throw new ServiceException(ServiceException.Kind.ACCESS_DENIED,
                    "Admin accounts cannot be edited from the user-management form.");
        }
        requireAssignableRole(role);
        if (status == null) {
            throw validation("Please choose an account status.");
        }
        target.setName(validateName(name));
        target.setUsername(validateUsername(username));
        target.setEmail(validateEmail(email));
        target.setRole(role);
        target.setStatus(status);
        if (newTemporaryPassword != null && newTemporaryPassword.length > 0) {
            String pwError = PasswordUtil.validateStrength(newTemporaryPassword);
            if (pwError != null) {
                throw validation("Temporary password: " + pwError);
            }
            target.setPasswordHash(PasswordUtil.hash(newTemporaryPassword));
            target.setMustChangePassword(true);
        }
        save(target);
        return target.withoutSecrets();
    }

    /** Activates or deactivates a non-Admin account. */
    public User setStatus(long userId, AccountStatus status) throws ServiceException {
        requireAdmin();
        if (status == null) {
            throw validation("Please choose an account status.");
        }
        User target = loadTarget(userId);
        if (target.getRole() == Role.ADMIN) {
            throw new ServiceException(ServiceException.Kind.ACCESS_DENIED,
                    "Admin accounts cannot be activated or deactivated here.");
        }
        target.setStatus(status);
        save(target);
        return target.withoutSecrets();
    }

    /** Account counts for the Admin dashboard, computed from users.csv. */
    public UserStatistics getStatistics() throws ServiceException {
        List<User> users = listUsers();
        Map<Role, Integer> byRole = new EnumMap<>(Role.class);
        for (Role r : Role.values()) {
            byRole.put(r, 0);
        }
        int active = 0;
        int pending = 0;
        for (User u : users) {
            byRole.merge(u.getRole(), 1, Integer::sum);
            if (u.isActive()) {
                active++;
            }
            if (u.isMustChangePassword()) {
                pending++;
            }
        }
        return new UserStatistics(users.size(), active, users.size() - active, pending, byRole,
                store.getLastWarnings().size());
    }

    // ================================================================ self-service

    /** Changes the logged-in user's own password. Clears the "must change password" flag. */
    public void changeOwnPassword(char[] currentPassword, char[] newPassword, char[] confirmPassword)
            throws ServiceException {
        User actor = requireLoggedIn();
        User stored = loadTarget(actor.getUserId());
        if (currentPassword == null || currentPassword.length == 0) {
            throw validation("Please enter your current password.");
        }
        if (!PasswordUtil.verify(currentPassword, stored.getPasswordHash())) {
            throw validation("Your current password is incorrect.");
        }
        validateNewPassword(newPassword, confirmPassword);
        if (PasswordUtil.verify(newPassword, stored.getPasswordHash())) {
            throw validation("The new password must be different from the current password.");
        }
        stored.setPasswordHash(PasswordUtil.hash(newPassword));
        stored.setMustChangePassword(false);
        save(stored);
    }

    // ================================================================ permission helpers

    private User requireLoggedIn() throws ServiceException {
        try {
            return auth.requireCurrentUser();
        } catch (AuthException e) {
            ServiceException.Kind kind = e.getReason() == AuthException.Reason.STORAGE_ERROR
                    ? ServiceException.Kind.STORAGE : ServiceException.Kind.ACCESS_DENIED;
            throw new ServiceException(kind, e.getMessage());
        }
    }

    private User requireAdmin() throws ServiceException {
        User actor = requireLoggedIn();
        if (actor.getRole() != Role.ADMIN) {
            throw new ServiceException(ServiceException.Kind.ACCESS_DENIED,
                    "Access denied: only an Admin can manage user accounts.");
        }
        return actor;
    }

    private static void requireAssignableRole(Role role) throws ServiceException {
        if (role == null) {
            throw validation("Please choose a role.");
        }
        if (role == Role.ADMIN) {
            throw new ServiceException(ServiceException.Kind.ACCESS_DENIED,
                    "Admin accounts cannot be created or assigned from this form.");
        }
    }

    // ================================================================ storage helpers

    private User loadTarget(long userId) throws ServiceException {
        try {
            Optional<User> u = store.findById(userId);
            if (u.isEmpty()) {
                throw new ServiceException(ServiceException.Kind.NOT_FOUND, "User #" + userId + " was not found.");
            }
            return u.get();
        } catch (IOException e) {
            throw storage(e);
        }
    }

    private User insert(User user) throws ServiceException {
        try {
            return store.insert(user);
        } catch (DuplicateUserException e) {
            throw new ServiceException(ServiceException.Kind.DUPLICATE, e.getMessage());
        } catch (IOException e) {
            throw storage(e);
        }
    }

    private void save(User user) throws ServiceException {
        try {
            store.update(user);
        } catch (DuplicateUserException e) {
            throw new ServiceException(ServiceException.Kind.DUPLICATE, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ServiceException(ServiceException.Kind.NOT_FOUND, e.getMessage());
        } catch (IOException e) {
            throw storage(e);
        }
    }

    // ================================================================ validation

    private static String validateName(String name) throws ServiceException {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) {
            throw validation("Full name is required.");
        }
        if (n.length() > MAX_NAME) {
            throw validation("Full name must be at most " + MAX_NAME + " characters.");
        }
        return n;
    }

    private static String validateUsername(String username) throws ServiceException {
        String u = username == null ? "" : username.trim();
        if (!USERNAME.matcher(u).matches()) {
            throw validation("Username must be 3-30 characters: letters, digits, '.', '_' or '-'.");
        }
        return u;
    }

    private static String validateEmail(String email) throws ServiceException {
        String e = email == null ? "" : email.trim();
        if (e.length() > MAX_EMAIL || !EMAIL.matcher(e).matches()) {
            throw validation("Please enter a valid email address.");
        }
        return e;
    }

    private static void validateNewPassword(char[] password, char[] confirm) throws ServiceException {
        String error = PasswordUtil.validateStrength(password);
        if (error != null) {
            throw validation(error);
        }
        if (confirm == null || !java.util.Arrays.equals(password, confirm)) {
            throw validation("The passwords do not match.");
        }
    }

    private static ServiceException validation(String message) {
        return new ServiceException(ServiceException.Kind.VALIDATION, message);
    }

    private static ServiceException storage(IOException e) {
        return new ServiceException(ServiceException.Kind.STORAGE, "Could not access user data: " + e.getMessage());
    }

    // ================================================================ statistics value

    /** Immutable account statistics. */
    public static final class UserStatistics {
        private final int total;
        private final int active;
        private final int inactive;
        private final int pendingPasswordChange;
        private final Map<Role, Integer> byRole;
        private final int skippedRecords;

        UserStatistics(int total, int active, int inactive, int pendingPasswordChange,
                       Map<Role, Integer> byRole, int skippedRecords) {
            this.total = total;
            this.active = active;
            this.inactive = inactive;
            this.pendingPasswordChange = pendingPasswordChange;
            this.byRole = new EnumMap<>(byRole);
            this.skippedRecords = skippedRecords;
        }

        public int getTotal() { return total; }
        public int getActive() { return active; }
        public int getInactive() { return inactive; }
        public int getPendingPasswordChange() { return pendingPasswordChange; }
        public int getCount(Role role) { return byRole.getOrDefault(role, 0); }
        public int getSkippedRecords() { return skippedRecords; }
    }
}
