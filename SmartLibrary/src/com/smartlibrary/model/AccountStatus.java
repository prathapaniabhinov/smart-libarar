package com.smartlibrary.model;

import java.util.Locale;

/** Whether an account is allowed to log in. */
public enum AccountStatus {
    ACTIVE("Active"),
    INACTIVE("Inactive");

    private final String displayName;

    AccountStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Parses a status as stored in the CSV file (case-insensitive).
     *
     * @throws IllegalArgumentException if the value is not a known status
     */
    public static AccountStatus parse(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Status is empty");
        }
        try {
            return AccountStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown status: " + value);
        }
    }

    @Override
    public String toString() {
        return displayName;
    }
}
