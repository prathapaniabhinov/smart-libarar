package com.smartlibrary.console;

import com.smartlibrary.auth.AuthException;
import com.smartlibrary.auth.AuthService;
import com.smartlibrary.auth.PasswordUtil;
import com.smartlibrary.model.User;

/**
 * The single login prompt for all roles. There is no role choice: the role comes from users.csv.
 */
public class LoginConsole {

    private final ConsoleIO io;
    private final AuthService auth;

    public LoginConsole(ConsoleIO io, AuthService auth) {
        this.io = io;
        this.auth = auth;
    }

    /** @return the logged-in user, or null if login failed */
    public User run() {
        io.header("Login");
        String identifier = io.readLine("Username or email : ");
        char[] password = io.readPassword("Password          : ");
        try {
            User user = auth.login(identifier, password);
            io.success("Welcome, " + user.getName() + " (" + user.getRole().getDisplayName() + ").");
            return user;
        } catch (AuthException e) {
            io.error(e.getMessage());
            return null;
        } finally {
            PasswordUtil.wipe(password);
        }
    }
}
