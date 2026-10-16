package com.smartlibrary.auth;

/** Login or session failure. The message is safe to show to the user. */
public class AuthException extends Exception {

    private static final long serialVersionUID = 1L;

    public enum Reason {
        MISSING_INPUT,
        INVALID_CREDENTIALS,
        ACCOUNT_INACTIVE,
        NOT_LOGGED_IN,
        STORAGE_ERROR
    }

    private final Reason reason;

    public AuthException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
