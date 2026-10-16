package com.smartlibrary.auth;

import java.util.Locale;

/**
 * The four roles supported by SmartLibrary.
 * The role stored in users.csv is the single source of truth for what a user may do.
 */
public enum Role {
    ADMIN("Admin"),
    LIBRARIAN("Librarian"),
    STUDENT("Student"),
    TEACHER("Teacher");

    private final String displayName;

    Role(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Roles an Admin may assign through the user-management screen (never ADMIN). */
    public static Role[] assignableByAdmin() {
        return new Role[] { STUDENT, TEACHER, LIBRARIAN };
    }

    /**
     * Parses a role name as stored in the CSV file (case-insensitive).
     *
     * @throws IllegalArgumentException if the value is not a known role
     */
    public static Role parse(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Role is empty");
        }
        try {
            return Role.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown role: " + value);
        }
    }

    @Override
    public String toString() {
        return displayName;
    }
}
