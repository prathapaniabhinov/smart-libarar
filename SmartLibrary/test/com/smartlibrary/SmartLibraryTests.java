package com.smartlibrary;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import com.smartlibrary.auth.AuthException;
import com.smartlibrary.auth.AuthService;
import com.smartlibrary.auth.InitialSetup;
import com.smartlibrary.auth.PasswordUtil;
import com.smartlibrary.auth.Role;
import com.smartlibrary.data.UserCSV;
import com.smartlibrary.model.AccountStatus;
import com.smartlibrary.model.User;
import com.smartlibrary.service.ServiceException;
import com.smartlibrary.service.UserService;

/**
 * Self-contained test suite (no JUnit needed). Run it as a Java Application in Eclipse.
 * Every test uses its own temporary data folder, so your real data/users.csv is never touched.
 */
public final class SmartLibraryTests {

    private static final String ADMIN_PW = "AdminPass123";
    private static final String STUDENT_PW = "TempPass123";

    @FunctionalInterface
    private interface TestBody {
        void run(Env env) throws Exception;
    }

    /** A fresh, isolated application instance on a temporary data folder. */
    private static final class Env {
        final Path dir;
        final UserCSV store;
        final AuthService auth;
        final InitialSetup setup;
        final UserService users;

        Env(Path dir) {
            this.dir = dir;
            this.store = new UserCSV(dir);
            this.auth = new AuthService(store);
            this.setup = new InitialSetup(dir, null);
            this.users = new UserService(store, auth, setup);
        }

        String setupCode() throws IOException {
            return Files.readString(setup.getCodeFile(), StandardCharsets.UTF_8).trim();
        }

        /** Runs first-time setup and logs in as the Admin. */
        void bootstrapAdmin() throws Exception {
            users.prepareInitialSetup();
            users.createInitialAdmin(setupCode(), "Admin", "admin01", "admin@example.com",
                    ADMIN_PW.toCharArray(), ADMIN_PW.toCharArray());
            auth.login("admin01", ADMIN_PW.toCharArray());
        }

        User createStudent(String username, String email) throws Exception {
            return users.createUser("Ravi Kumar", username, email, STUDENT_PW.toCharArray(),
                    Role.STUDENT, AccountStatus.ACTIVE);
        }
    }

    private static int passed;
    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        test("Missing CSV file is created with a header", env -> {
            Path file = env.dir.resolve("users.csv");
            check(!Files.exists(file), "precondition: file absent");
            check(env.store.loadAll().isEmpty(), "no users loaded");
            check(Files.exists(file), "users.csv created");
            check(Files.readAllLines(file).get(0).startsWith("userId,name,username,email,passwordHash,role,status"),
                    "header written");
            check(!env.users.adminExists(), "no admin yet");
        });

        test("Missing data folder is created", env -> {
            Path nested = env.dir.resolve("a/b/c");
            UserCSV store = new UserCSV(nested);
            check(store.loadAll().isEmpty(), "empty");
            check(Files.exists(nested.resolve("users.csv")), "file created in nested folder");
        });

        test("Initial setup requires the correct one-time code", env -> {
            env.users.prepareInitialSetup();
            expectService(ServiceException.Kind.ACCESS_DENIED, () -> env.users.createInitialAdmin("WRONG-CODE",
                    "Admin", "admin01", "admin@example.com", ADMIN_PW.toCharArray(), ADMIN_PW.toCharArray()));
            check(!env.users.adminExists(), "no admin after wrong code");
            env.users.createInitialAdmin(env.setupCode(), "Admin", "admin01", "admin@example.com",
                    ADMIN_PW.toCharArray(), ADMIN_PW.toCharArray());
            check(env.users.adminExists(), "admin created");
            check(!Files.exists(env.setup.getCodeFile()), "setup code file deleted");
        });

        test("Initial setup is disabled once an Admin exists", env -> {
            env.bootstrapAdmin();
            expectService(ServiceException.Kind.ACCESS_DENIED, () -> env.users.prepareInitialSetup());
            expectService(ServiceException.Kind.ACCESS_DENIED, () -> env.users.createInitialAdmin("anything",
                    "Evil", "evil01", "evil@example.com", "EvilPass123".toCharArray(), "EvilPass123".toCharArray()));
        });

        test("Successful login returns the role stored in the CSV", env -> {
            env.bootstrapAdmin();
            env.createStudent("ravi01", "ravi@example.com");
            env.auth.logout();
            User byUsername = env.auth.login("ravi01", STUDENT_PW.toCharArray());
            check(byUsername.getRole() == Role.STUDENT, "role is STUDENT");
            check(byUsername.getPasswordHash() == null, "hash not exposed to caller");
            env.auth.logout();
            User byEmail = env.auth.login("RAVI@example.com", STUDENT_PW.toCharArray());
            check(byEmail.getUsername().equals("ravi01"), "login by email (case-insensitive)");
            User admin = loginFresh(env, "admin01", ADMIN_PW);
            check(admin.getRole() == Role.ADMIN, "admin role");
        });

        test("Incorrect password and unknown user are rejected with the same message", env -> {
            env.bootstrapAdmin();
            env.auth.logout();
            AuthException wrong = expectAuth(AuthException.Reason.INVALID_CREDENTIALS,
                    () -> env.auth.login("admin01", "WrongPass999".toCharArray()));
            AuthException unknown = expectAuth(AuthException.Reason.INVALID_CREDENTIALS,
                    () -> env.auth.login("nobody", "WrongPass999".toCharArray()));
            check(wrong.getMessage().equals(unknown.getMessage()), "identical error messages");
            check(!env.auth.isLoggedIn(), "not logged in");
            expectAuth(AuthException.Reason.MISSING_INPUT, () -> env.auth.login("", "x".toCharArray()));
            expectAuth(AuthException.Reason.MISSING_INPUT, () -> env.auth.login("admin01", new char[0]));
        });

        test("HASH_PLACEHOLDER records can never log in", env -> {
            Files.createDirectories(env.dir);
            Files.writeString(env.dir.resolve("users.csv"),
                    "userId,name,username,email,passwordHash,role,status\n"
                    + "1,Admin,admin01,admin@example.com,HASH_PLACEHOLDER,ADMIN,ACTIVE\n");
            expectAuth(AuthException.Reason.INVALID_CREDENTIALS,
                    () -> env.auth.login("admin01", "HASH_PLACEHOLDER".toCharArray()));
        });

        test("Students and Teachers cannot use admin functions", env -> {
            env.bootstrapAdmin();
            env.createStudent("ravi01", "ravi@example.com");
            env.users.createUser("Sita", "sita01", "sita@example.com", STUDENT_PW.toCharArray(),
                    Role.TEACHER, AccountStatus.ACTIVE);
            for (String username : new String[] { "ravi01", "sita01" }) {
                loginFresh(env, username, STUDENT_PW);
                expectService(ServiceException.Kind.ACCESS_DENIED, () -> env.users.listUsers());
                expectService(ServiceException.Kind.ACCESS_DENIED, () -> env.users.createUser("X", "x0001",
                        "x@example.com", "Password123".toCharArray(), Role.LIBRARIAN, AccountStatus.ACTIVE));
                expectService(ServiceException.Kind.ACCESS_DENIED, () -> env.users.setStatus(1, AccountStatus.INACTIVE));
                expectService(ServiceException.Kind.ACCESS_DENIED, () -> env.users.getStatistics());
            }
        });

        test("Admin form cannot create or assign the ADMIN role", env -> {
            env.bootstrapAdmin();
            expectService(ServiceException.Kind.ACCESS_DENIED, () -> env.users.createUser("Second Admin",
                    "admin02", "admin2@example.com", "Password123".toCharArray(), Role.ADMIN, AccountStatus.ACTIVE));
            User s = env.createStudent("ravi01", "ravi@example.com");
            expectService(ServiceException.Kind.ACCESS_DENIED, () -> env.users.updateUser(s.getUserId(), "Ravi",
                    "ravi01", "ravi@example.com", Role.ADMIN, AccountStatus.ACTIVE, null));
            expectService(ServiceException.Kind.ACCESS_DENIED, () -> env.users.setStatus(1, AccountStatus.INACTIVE));
        });

        test("Editing the CSV role is what decides access (no client-side role)", env -> {
            env.bootstrapAdmin();
            User s = env.createStudent("ravi01", "ravi@example.com");
            loginFresh(env, "ravi01", STUDENT_PW);
            expectService(ServiceException.Kind.ACCESS_DENIED, () -> env.users.listUsers());
            // Promote to LIBRARIAN directly in storage: the open session sees the change immediately.
            User stored = env.store.findById(s.getUserId()).orElseThrow();
            stored.setRole(Role.LIBRARIAN);
            env.store.update(stored);
            check(env.auth.requireCurrentUser().getRole() == Role.LIBRARIAN, "role re-read from CSV");
        });

        test("Duplicate usernames and emails are rejected (case-insensitive)", env -> {
            env.bootstrapAdmin();
            env.createStudent("ravi01", "ravi@example.com");
            expectService(ServiceException.Kind.DUPLICATE, () -> env.createStudent("RAVI01", "other@example.com"));
            expectService(ServiceException.Kind.DUPLICATE, () -> env.createStudent("ravi02", "Ravi@Example.com"));
            expectService(ServiceException.Kind.DUPLICATE, () -> env.createStudent("admin01", "x@example.com"));
            User second = env.createStudent("ravi02", "ravi2@example.com");
            expectService(ServiceException.Kind.DUPLICATE, () -> env.users.updateUser(second.getUserId(), "R",
                    "ravi01", "ravi2@example.com", Role.STUDENT, AccountStatus.ACTIVE, null));
            check(env.store.loadAll().size() == 3, "only 3 users stored");
        });

        test("Input validation", env -> {
            env.bootstrapAdmin();
            expectService(ServiceException.Kind.VALIDATION, () -> env.createStudent("ab", "a@example.com"));
            expectService(ServiceException.Kind.VALIDATION, () -> env.createStudent("bad user", "a@example.com"));
            expectService(ServiceException.Kind.VALIDATION, () -> env.createStudent("good01", "not-an-email"));
            expectService(ServiceException.Kind.VALIDATION, () -> env.users.createUser("", "good01",
                    "a@example.com", STUDENT_PW.toCharArray(), Role.STUDENT, AccountStatus.ACTIVE));
            expectService(ServiceException.Kind.VALIDATION, () -> env.users.createUser("Name", "good01",
                    "a@example.com", "short1".toCharArray(), Role.STUDENT, AccountStatus.ACTIVE));
            expectService(ServiceException.Kind.VALIDATION, () -> env.users.createUser("Name", "good01",
                    "a@example.com", "lettersonly".toCharArray(), Role.STUDENT, AccountStatus.ACTIVE));
        });

        test("Inactive users cannot log in, and an open session ends on deactivation", env -> {
            env.bootstrapAdmin();
            User s = env.createStudent("ravi01", "ravi@example.com");
            env.users.setStatus(s.getUserId(), AccountStatus.INACTIVE);
            env.auth.logout();
            expectAuth(AuthException.Reason.ACCOUNT_INACTIVE, () -> env.auth.login("ravi01", STUDENT_PW.toCharArray()));
            // Wrong password on an inactive account must not reveal the status.
            expectAuth(AuthException.Reason.INVALID_CREDENTIALS,
                    () -> env.auth.login("ravi01", "WrongPass999".toCharArray()));

            loginFresh(env, "admin01", ADMIN_PW);
            env.users.setStatus(s.getUserId(), AccountStatus.ACTIVE);
            loginFresh(env, "ravi01", STUDENT_PW);
            // Admin deactivates the student while the student is "logged in".
            User stored = env.store.findById(s.getUserId()).orElseThrow();
            stored.setStatus(AccountStatus.INACTIVE);
            env.store.update(stored);
            expectAuth(AuthException.Reason.ACCOUNT_INACTIVE, () -> env.auth.requireCurrentUser());
            check(!env.auth.isLoggedIn(), "session ended");
        });

        test("Logout blocks further access", env -> {
            env.bootstrapAdmin();
            check(!env.users.listUsers().isEmpty(), "works while logged in");
            env.auth.logout();
            check(!env.auth.isLoggedIn(), "logged out");
            expectService(ServiceException.Kind.ACCESS_DENIED, () -> env.users.listUsers());
            expectService(ServiceException.Kind.ACCESS_DENIED, () -> env.users.changeOwnPassword(
                    ADMIN_PW.toCharArray(), "NewPass456".toCharArray(), "NewPass456".toCharArray()));
            expectAuth(AuthException.Reason.NOT_LOGGED_IN, () -> env.auth.requireCurrentUser());
        });

        test("New users must change their temporary password; change works", env -> {
            env.bootstrapAdmin();
            env.createStudent("ravi01", "ravi@example.com");
            User u = loginFresh(env, "ravi01", STUDENT_PW);
            check(u.isMustChangePassword(), "must change on first login");
            expectService(ServiceException.Kind.VALIDATION, () -> env.users.changeOwnPassword(
                    "wrongCurrent1".toCharArray(), "NewPass456".toCharArray(), "NewPass456".toCharArray()));
            expectService(ServiceException.Kind.VALIDATION, () -> env.users.changeOwnPassword(
                    STUDENT_PW.toCharArray(), "NewPass456".toCharArray(), "Different789".toCharArray()));
            expectService(ServiceException.Kind.VALIDATION, () -> env.users.changeOwnPassword(
                    STUDENT_PW.toCharArray(), STUDENT_PW.toCharArray(), STUDENT_PW.toCharArray()));
            env.users.changeOwnPassword(STUDENT_PW.toCharArray(), "NewPass456".toCharArray(),
                    "NewPass456".toCharArray());
            env.auth.logout();
            expectAuth(AuthException.Reason.INVALID_CREDENTIALS,
                    () -> env.auth.login("ravi01", STUDENT_PW.toCharArray()));
            User after = env.auth.login("ravi01", "NewPass456".toCharArray());
            check(!after.isMustChangePassword(), "flag cleared");
        });

        test("Admin password reset forces another change", env -> {
            env.bootstrapAdmin();
            User s = env.createStudent("ravi01", "ravi@example.com");
            env.users.updateUser(s.getUserId(), "Ravi K", "ravi01", "ravi@example.com", Role.TEACHER,
                    AccountStatus.ACTIVE, "Reset1234".toCharArray());
            User u = loginFresh(env, "ravi01", "Reset1234");
            check(u.isMustChangePassword(), "must change after reset");
            check(u.getRole() == Role.TEACHER && u.getName().equals("Ravi K"), "details updated");
        });

        test("Passwords are stored only as salted hashes", env -> {
            env.bootstrapAdmin();
            env.createStudent("ravi01", "ravi@example.com");
            String csv = Files.readString(env.dir.resolve("users.csv"));
            check(!csv.contains(ADMIN_PW) && !csv.contains(STUDENT_PW), "no plaintext passwords in CSV");
            String h1 = PasswordUtil.hash("Same1234".toCharArray());
            String h2 = PasswordUtil.hash("Same1234".toCharArray());
            check(!h1.equals(h2), "random salt gives different hashes");
            check(PasswordUtil.verify("Same1234".toCharArray(), h1), "verify ok");
            check(!PasswordUtil.verify("Same1235".toCharArray(), h1), "verify rejects wrong password");
            check(!PasswordUtil.verify("x".toCharArray(), "pbkdf2$abc$$"), "malformed hash rejected");
            check(!env.users.listUsers().get(0).toString().contains("pbkdf2"), "toString hides hash");
            for (User u : env.users.listUsers()) {
                check(u.getPasswordHash() == null, "service never returns hashes");
            }
        });

        test("Commas, quotes and line breaks survive a CSV round trip", env -> {
            env.bootstrapAdmin();
            String tricky = "Kumar, Ravi \"RK\"\nJunior";
            User s = env.users.createUser(tricky, "ravi01", "ravi@example.com", STUDENT_PW.toCharArray(),
                    Role.STUDENT, AccountStatus.ACTIVE);
            UserCSV reopened = new UserCSV(env.dir);
            User loaded = reopened.findById(s.getUserId()).orElseThrow();
            check(loaded.getName().equals(tricky), "name preserved: " + loaded.getName());
            check(reopened.getLastWarnings().isEmpty(), "no warnings");
        });

        test("Malformed records are skipped but preserved; updates keep other records", env -> {
            Files.createDirectories(env.dir);
            Files.writeString(env.dir.resolve("users.csv"),
                    "userId,name,username,email,passwordHash,role,status,mustChangePassword\n"
                    + "abc,Broken,broken01,b@example.com,HASH,STUDENT,ACTIVE,false\n"
                    + "7,Bad Role,bad01,bad@example.com,HASH,WIZARD,ACTIVE,false\n"
                    + "8,Too,few\n"
                    + "\n");
            env.bootstrapAdmin();
            check(env.store.getLastWarnings().size() == 3, "3 warnings: " + env.store.getLastWarnings());
            User s = env.createStudent("ravi01", "ravi@example.com");
            // Admin got id 9 (ids 7 and 8 are held by malformed rows), so the student gets 10.
            check(s.getUserId() == 10, "new id avoids ids of malformed rows, got " + s.getUserId());
            User other = env.createStudent("ravi02", "ravi2@example.com");
            env.users.updateUser(s.getUserId(), "Renamed", "ravi01", "ravi@example.com", Role.STUDENT,
                    AccountStatus.ACTIVE, null);
            String csv = Files.readString(env.dir.resolve("users.csv"));
            check(csv.contains("broken01") && csv.contains("WIZARD") && csv.contains("8,Too,few"),
                    "malformed rows kept");
            check(env.store.findById(other.getUserId()).orElseThrow().getName().equals("Ravi Kumar"),
                    "other record untouched");
            check(env.store.findById(s.getUserId()).orElseThrow().getName().equals("Renamed"), "update saved");
            check(env.store.loadAll().size() == 3, "3 valid users");
        });

        test("Legacy 7-column CSV (without mustChangePassword) is read", env -> {
            Files.createDirectories(env.dir);
            String hash = PasswordUtil.hash("Legacy1234".toCharArray());
            Files.writeString(env.dir.resolve("users.csv"),
                    "userId,name,username,email,passwordHash,role,status\n"
                    + "1,Ramesh,ramesh01,ramesh@example.com," + hash + ",LIBRARIAN,ACTIVE\n");
            User u = env.auth.login("ramesh01", "Legacy1234".toCharArray());
            check(u.getRole() == Role.LIBRARIAN && !u.isMustChangePassword(), "legacy row usable");
        });

        test("User search by name or username", env -> {
            env.bootstrapAdmin();
            env.createStudent("ravi01", "ravi@example.com");
            env.users.createUser("Sita Devi", "sita01", "sita@example.com", STUDENT_PW.toCharArray(),
                    Role.TEACHER, AccountStatus.ACTIVE);
            check(env.users.searchUsers("SITA").size() == 1, "by name, case-insensitive");
            check(env.users.searchUsers("ravi0").size() == 1, "by username");
            check(env.users.searchUsers("").size() == 3, "blank returns all");
            check(env.users.searchUsers("zzz").isEmpty(), "no match");
        });

        System.out.println();
        System.out.println("Passed: " + passed + ", Failed: " + failures.size());
        for (String f : failures) {
            System.out.println("  FAILED " + f);
        }
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    // ------------------------------------------------------------------ helpers

    private static User loginFresh(Env env, String username, String password) throws AuthException {
        env.auth.logout();
        return env.auth.login(username, password.toCharArray());
    }

    private static void test(String name, TestBody body) {
        Path dir = null;
        try {
            dir = Files.createTempDirectory("smartlibrary-test-");
            body.run(new Env(dir.resolve("data")));
            passed++;
            System.out.println("PASS  " + name);
        } catch (Throwable t) {
            failures.add(name + " -> " + t);
            System.out.println("FAIL  " + name + " -> " + t);
        } finally {
            deleteRecursively(dir);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    private interface Action {
        void run() throws Exception;
    }

    private static ServiceException expectService(ServiceException.Kind kind, Action action) throws Exception {
        try {
            action.run();
        } catch (ServiceException e) {
            check(e.getKind() == kind, "expected " + kind + " but got " + e.getKind() + ": " + e.getMessage());
            return e;
        }
        throw new AssertionError("expected ServiceException " + kind);
    }

    private static AuthException expectAuth(AuthException.Reason reason, Action action) throws Exception {
        try {
            action.run();
        } catch (AuthException e) {
            check(e.getReason() == reason, "expected " + reason + " but got " + e.getReason() + ": " + e.getMessage());
            return e;
        }
        throw new AssertionError("expected AuthException " + reason);
    }

    private static void deleteRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ignored) {
                    // best effort
                }
            });
        } catch (IOException ignored) {
            // best effort
        }
    }
}
