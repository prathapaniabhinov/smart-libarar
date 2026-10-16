package com.smartlibrary.console;

import java.util.ArrayList;
import java.util.List;

import com.smartlibrary.auth.AuthException;
import com.smartlibrary.auth.AuthService;
import com.smartlibrary.model.User;
import com.smartlibrary.service.AnalyticsService;
import com.smartlibrary.service.BookService;
import com.smartlibrary.service.ReviewService;
import com.smartlibrary.service.ServiceException;
import com.smartlibrary.service.UserService;

/**
 * Common menu loop for all four dashboards. Shows the user's name and role, the role's menu,
 * Account Settings, and Logout.
 * <p>
 * Before every action the session is re-checked against users.csv, so a logged-out or
 * deactivated user cannot keep using the menu.
 */
public abstract class BaseDashboard {

    /** A menu action that may fail with a service error. */
    @FunctionalInterface
    protected interface MenuAction {
        void run() throws ServiceException;
    }

    private static final class MenuItem {
        final String label;
        final MenuAction action;

        MenuItem(String label, MenuAction action) {
            this.label = label;
            this.action = action;
        }
    }

    protected final ConsoleIO io;
    protected final AuthService auth;
    protected final UserService users;
    protected final BookService books;
    protected final ReviewService reviews;
    protected final AnalyticsService analytics;
    private final String roleTitle;
    private final List<MenuItem> items = new ArrayList<>();

    protected BaseDashboard(ConsoleIO io, Services s, String roleTitle) {
        this.io = io;
        this.auth = s.auth;
        this.users = s.users;
        this.books = s.books;
        this.reviews = s.reviews;
        this.analytics = s.analytics;
        this.roleTitle = roleTitle;
    }

    /** All services a dashboard can use. */
    public static final class Services {
        final AuthService auth;
        final UserService users;
        final BookService books;
        final ReviewService reviews;
        final AnalyticsService analytics;

        public Services(AuthService auth, UserService users, BookService books, ReviewService reviews,
                        AnalyticsService analytics) {
            this.auth = auth;
            this.users = users;
            this.books = books;
            this.reviews = reviews;
            this.analytics = analytics;
        }
    }

    /** Subclasses add their menu entries with {@link #addItem(String, MenuAction)}. */
    protected abstract void buildMenu();

    protected void addItem(String label, MenuAction action) {
        items.add(new MenuItem(label, action));
    }

    /** Runs the dashboard until the user logs out or the session ends. */
    public void run() {
        buildMenu();
        addItem("Account Settings", this::accountSettings);
        List<String> labels = new ArrayList<>();
        for (MenuItem item : items) {
            labels.add(item.label);
        }

        while (true) {
            User current = currentUserOrNull();
            if (current == null) {
                return;
            }
            io.header("SmartLibrary - " + roleTitle + " Dashboard");
            io.println("  Logged in as: " + current.getName() + "  |  " + current.getRole().getDisplayName()
                    + "  (" + current.getUsername() + ")");
            io.blank();
            io.menu(labels, "Logout");
            int choice = io.readChoice("\nChoose an option: ", 0, items.size());
            if (choice == 0) {
                if (io.confirm("Log out of SmartLibrary?")) {
                    auth.logout();
                    io.success("You have been logged out.");
                    return;
                }
                continue;
            }
            if (currentUserOrNull() == null) {
                return;
            }
            try {
                items.get(choice - 1).action.run();
            } catch (ServiceException e) {
                io.error(e.getMessage());
            }
        }
    }

    /** Fresh user from users.csv, or null (with a message) if the session has ended. */
    private User currentUserOrNull() {
        try {
            return auth.requireCurrentUser();
        } catch (AuthException e) {
            io.error(e.getMessage());
            auth.logout();
            return null;
        }
    }

    private void accountSettings() {
        User u = currentUserOrNull();
        if (u == null) {
            return;
        }
        io.header("Account Settings");
        io.println("  Name     : " + u.getName());
        io.println("  Username : " + u.getUsername());
        io.println("  Email    : " + u.getEmail());
        io.println("  Role     : " + u.getRole().getDisplayName());
        io.println("  Status   : " + u.getStatus().getDisplayName());
        io.blank();
        if (io.confirm("Change your password now?")) {
            new ChangePasswordConsole(io, users).run(false);
        }
    }

    /**
     * Shows a feature that is not built yet. It is clearly labelled and shows no fake data.
     */
    protected void planned(String title, String description, String... plannedItems) {
        io.header(title);
        io.println("  *** PLANNED - NOT YET IMPLEMENTED ***");
        io.blank();
        io.println("  " + description);
        if (plannedItems.length > 0) {
            io.blank();
            io.println("  Planned capabilities:");
            for (String item : plannedItems) {
                io.println("   - " + item);
            }
        }
        io.pause();
    }
}
