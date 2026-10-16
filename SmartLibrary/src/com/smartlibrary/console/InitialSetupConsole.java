package com.smartlibrary.console;

import com.smartlibrary.auth.PasswordUtil;
import com.smartlibrary.model.User;
import com.smartlibrary.service.ServiceException;
import com.smartlibrary.service.UserService;

/**
 * First-run setup, shown only while no Admin exists. Creating the Admin requires the one-time
 * setup code printed in the console (and saved in data/setup-code.txt).
 */
public class InitialSetupConsole {

    private static final int MAX_ATTEMPTS = 3;

    private final ConsoleIO io;
    private final UserService users;

    public InitialSetupConsole(ConsoleIO io, UserService users) {
        this.io = io;
        this.users = users;
    }

    /** @return true if the Admin was created */
    public boolean run() {
        io.header("Initial Setup - Create the Administrator");
        io.info("No Admin account exists yet. This one-time setup creates the first Admin.");
        io.info("Enter the setup code shown in the box above.");

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            io.blank();
            String code = io.readLine("Setup code       : ");
            String name = io.readLine("Full name        : ");
            String username = io.readLine("Username         : ");
            String email = io.readLine("Email            : ");
            char[] password = io.readPassword("Password         : ");
            char[] confirm = io.readPassword("Confirm password : ");
            try {
                User admin = users.createInitialAdmin(code, name, username, email, password, confirm);
                io.success("Admin account '" + admin.getUsername() + "' created. Setup is now disabled.");
                return true;
            } catch (ServiceException e) {
                io.error(e.getMessage());
            } finally {
                PasswordUtil.wipe(password);
                PasswordUtil.wipe(confirm);
            }
            if (attempt < MAX_ATTEMPTS && !io.confirm("Try again?")) {
                return false;
            }
        }
        io.error("Too many failed attempts. Restart the application to try again.");
        return false;
    }
}
