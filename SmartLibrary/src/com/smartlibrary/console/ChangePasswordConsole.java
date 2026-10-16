package com.smartlibrary.console;

import com.smartlibrary.auth.PasswordUtil;
import com.smartlibrary.service.ServiceException;
import com.smartlibrary.service.UserService;

/** Lets the logged-in user change their own password. */
public class ChangePasswordConsole {

    private static final int MAX_ATTEMPTS = 3;

    private final ConsoleIO io;
    private final UserService users;

    public ChangePasswordConsole(ConsoleIO io, UserService users) {
        this.io = io;
        this.users = users;
    }

    /**
     * @param forced true when the user logged in with a temporary password
     * @return true if the password was changed
     */
    public boolean run(boolean forced) {
        io.header(forced ? "Set a New Password" : "Change Password");
        if (forced) {
            io.info("You logged in with a temporary password. Choose a new one to continue.");
        }
        io.info("Rules: 8-128 characters, at least one letter and one digit, no spaces.");

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            char[] current = io.readPassword(forced ? "Temporary password   : " : "Current password     : ");
            char[] next = io.readPassword("New password         : ");
            char[] confirm = io.readPassword("Confirm new password : ");
            try {
                users.changeOwnPassword(current, next, confirm);
                io.success("Your password has been changed.");
                return true;
            } catch (ServiceException e) {
                io.error(e.getMessage());
                if (e.getKind() == ServiceException.Kind.ACCESS_DENIED
                        || e.getKind() == ServiceException.Kind.STORAGE) {
                    return false;
                }
            } finally {
                PasswordUtil.wipe(current);
                PasswordUtil.wipe(next);
                PasswordUtil.wipe(confirm);
            }
            if (attempt < MAX_ATTEMPTS && !io.confirm("Try again?")) {
                return false;
            }
        }
        io.error("Too many attempts.");
        return false;
    }
}
