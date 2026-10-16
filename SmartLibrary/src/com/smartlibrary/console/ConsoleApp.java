package com.smartlibrary.console;

import java.util.List;

import com.smartlibrary.auth.AuthException;
import com.smartlibrary.auth.AuthService;
import com.smartlibrary.model.User;
import com.smartlibrary.service.ServiceException;
import com.smartlibrary.service.UserService;

/**
 * Console application flow: first-run setup (if needed) -> login -> forced password change
 * (if needed) -> dashboard for the role stored in users.csv -> logout -> login again.
 */
public class ConsoleApp {

    private final ConsoleIO io;
    private final AuthService auth;
    private final UserService users;
    private final BaseDashboard.Services services;

    public ConsoleApp(ConsoleIO io, AuthService auth, UserService users, BaseDashboard.Services services) {
        this.io = io;
        this.auth = auth;
        this.users = users;
        this.services = services;
    }

    public void run() {
        io.println("============================================================");
        io.println("  SmartLibrary - Intelligent Digital Library Platform");
        io.println("============================================================");
        try {
            if (!users.adminExists()) {
                users.prepareInitialSetup(); // prints the one-time setup code
                if (!new InitialSetupConsole(io, users).run()) {
                    io.println("Exiting. Run the program again to complete setup.");
                    return;
                }
            }
            mainMenu();
        } catch (ServiceException e) {
            io.error(e.getMessage());
            io.error("Check that the data folder exists and is writable, then run again.");
        } catch (InputClosedException e) {
            io.blank();
            io.println("Input closed. Exiting.");
        } finally {
            auth.logout();
        }
    }

    private void mainMenu() {
        while (true) {
            io.header("Main Menu");
            io.menu(List.of("Login"), "Exit");
            int choice = io.readChoice("\nChoose an option: ", 0, 1);
            if (choice == 0) {
                io.println("Goodbye!");
                return;
            }
            User user = new LoginConsole(io, auth).run();
            if (user == null) {
                continue;
            }
            if (user.isMustChangePassword()) {
                if (!new ChangePasswordConsole(io, users).run(true)) {
                    auth.logout();
                    io.error("You must change your temporary password before continuing. Logged out.");
                    continue;
                }
                try {
                    user = auth.requireCurrentUser();
                } catch (AuthException e) {
                    io.error(e.getMessage());
                    continue;
                }
            }
            dashboardFor(user).run();
            auth.logout();
        }
    }

    /** Picks the dashboard from the role stored in users.csv - never from user input. */
    private BaseDashboard dashboardFor(User user) {
        switch (user.getRole()) {
            case ADMIN:
                return new AdminDashboard(io, services);
            case LIBRARIAN:
                return new LibrarianDashboard(io, services);
            case TEACHER:
                return new TeacherDashboard(io, services);
            case STUDENT:
            default:
                return new StudentDashboard(io, services);
        }
    }
}
