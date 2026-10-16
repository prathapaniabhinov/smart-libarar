package com.smartlibrary.console;

import java.util.ArrayList;
import java.util.List;

import com.smartlibrary.auth.PasswordUtil;
import com.smartlibrary.auth.Role;
import com.smartlibrary.model.AccountStatus;
import com.smartlibrary.model.User;
import com.smartlibrary.service.ServiceException;
import com.smartlibrary.service.UserService;

/**
 * Admin-only user management: list, search, create, update, activate and deactivate accounts.
 * UserService re-checks the Admin role on every call, so this menu is not the only guard.
 */
public class ManageUsersConsole {

    private static final String[] HEADERS = { "ID", "Name", "Username", "Email", "Role", "Status", "Temp Password" };

    private final ConsoleIO io;
    private final UserService users;

    public ManageUsersConsole(ConsoleIO io, UserService users) {
        this.io = io;
        this.users = users;
    }

    public void run() throws ServiceException {
        List<String> options = List.of(
                "List all users",
                "Search users (name or username)",
                "Create user",
                "Update user",
                "Activate / Deactivate user");
        while (true) {
            io.header("Manage Users");
            io.menu(options, "Back to dashboard");
            int choice = io.readChoice("\nChoose an option: ", 0, options.size());
            if (choice == 0) {
                return;
            }
            try {
                switch (choice) {
                    case 1: listUsers(""); break;
                    case 2: listUsers(io.readLine("Search text: ")); break;
                    case 3: createUser(); break;
                    case 4: updateUser(); break;
                    case 5: toggleStatus(); break;
                    default: break;
                }
            } catch (ServiceException e) {
                io.error(e.getMessage());
                if (e.getKind() == ServiceException.Kind.ACCESS_DENIED) {
                    return; // session ended or not an Admin any more
                }
            }
        }
    }

    // ------------------------------------------------------------------ actions

    private void listUsers(String query) throws ServiceException {
        List<User> found = users.searchUsers(query);
        io.header(query.isEmpty() ? "All Users" : "Search results for '" + query + "'");
        if (found.isEmpty()) {
            io.info("No users found.");
        } else {
            List<String[]> rows = new ArrayList<>();
            for (User u : found) {
                rows.add(new String[] { Long.toString(u.getUserId()), u.getName(), u.getUsername(), u.getEmail(),
                        u.getRole().getDisplayName(), u.getStatus().getDisplayName(),
                        u.isMustChangePassword() ? "Must change" : "" });
            }
            io.table(HEADERS, rows);
            io.info(found.size() + " user(s).");
        }
        io.pause();
    }

    private void createUser() throws ServiceException {
        io.header("Create User");
        io.info("Admin accounts cannot be created here. The user must change the temporary password at first login.");
        String name = io.readLine("Full name          : ");
        String username = io.readLine("Username           : ");
        String email = io.readLine("Email              : ");
        char[] password = io.readPassword("Temporary password : ");
        try {
            Role role = chooseRole(null);
            AccountStatus status = chooseStatus(AccountStatus.ACTIVE);
            User created = users.createUser(name, username, email, password, role, status);
            io.success("Created " + created.getRole().getDisplayName() + " '" + created.getUsername()
                    + "' with ID " + created.getUserId() + ".");
        } finally {
            PasswordUtil.wipe(password);
        }
        io.pause();
    }

    private void updateUser() throws ServiceException {
        io.header("Update User");
        User target = findUser(io.readId("User ID to update: "));
        if (target == null) {
            return;
        }
        if (target.getRole() == Role.ADMIN) {
            io.error("Admin accounts cannot be edited from user management.");
            io.pause();
            return;
        }
        io.info("Press Enter to keep the value shown in [brackets].");
        String name = keep(io.readLine("Full name [" + target.getName() + "] : "), target.getName());
        String username = keep(io.readLine("Username [" + target.getUsername() + "] : "), target.getUsername());
        String email = keep(io.readLine("Email [" + target.getEmail() + "] : "), target.getEmail());
        char[] password = io.readPassword("New temporary password [keep current] : ");
        try {
            Role role = chooseRole(target.getRole());
            AccountStatus status = chooseStatus(target.getStatus());
            User updated = users.updateUser(target.getUserId(), name, username, email, role, status, password);
            io.success("Updated '" + updated.getUsername() + "'"
                    + (password.length > 0 ? " and reset the temporary password." : "."));
        } finally {
            PasswordUtil.wipe(password);
        }
        io.pause();
    }

    private void toggleStatus() throws ServiceException {
        io.header("Activate / Deactivate User");
        User target = findUser(io.readId("User ID: "));
        if (target == null) {
            return;
        }
        if (target.getRole() == Role.ADMIN) {
            io.error("Admin accounts cannot be activated or deactivated here.");
            io.pause();
            return;
        }
        AccountStatus next = target.isActive() ? AccountStatus.INACTIVE : AccountStatus.ACTIVE;
        String verb = next == AccountStatus.ACTIVE ? "Activate" : "Deactivate";
        if (io.confirm(verb + " '" + target.getUsername() + "' (" + target.getName() + ")?")) {
            User updated = users.setStatus(target.getUserId(), next);
            io.success("'" + updated.getUsername() + "' is now " + updated.getStatus().getDisplayName() + ".");
        } else {
            io.info("No changes made.");
        }
        io.pause();
    }

    // ------------------------------------------------------------------ helpers

    private User findUser(long id) throws ServiceException {
        if (id <= 0) {
            io.error("Please enter a valid user ID (see 'List all users').");
            io.pause();
            return null;
        }
        for (User u : users.listUsers()) {
            if (u.getUserId() == id) {
                return u;
            }
        }
        io.error("User #" + id + " was not found.");
        io.pause();
        return null;
    }

    /** Lets the Admin pick Student, Teacher or Librarian (never Admin). */
    private Role chooseRole(Role current) {
        Role[] roles = Role.assignableByAdmin();
        io.println("Role:");
        for (int i = 0; i < roles.length; i++) {
            io.println("  " + (i + 1) + ". " + roles[i].getDisplayName() + (roles[i] == current ? "  (current)" : ""));
        }
        if (current != null) {
            io.println("  0. Keep current");
            int c = io.readChoice("Choose role: ", 0, roles.length);
            return c == 0 ? current : roles[c - 1];
        }
        return roles[io.readChoice("Choose role: ", 1, roles.length) - 1];
    }

    private AccountStatus chooseStatus(AccountStatus current) {
        io.println("Account status:");
        io.println("  1. Active" + (current == AccountStatus.ACTIVE ? "  (current)" : ""));
        io.println("  2. Inactive" + (current == AccountStatus.INACTIVE ? "  (current)" : ""));
        io.println("  0. Keep " + current.getDisplayName());
        int c = io.readChoice("Choose status: ", 0, 2);
        return c == 0 ? current : (c == 1 ? AccountStatus.ACTIVE : AccountStatus.INACTIVE);
    }

    private static String keep(String typed, String current) {
        return typed.isEmpty() ? current : typed;
    }
}
