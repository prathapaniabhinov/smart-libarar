package com.smartlibrary.service;

/** Failure of a user-management operation. The message is safe to show to the user. */
public class ServiceException extends Exception {

    private static final long serialVersionUID = 1L;

    public enum Kind {
        VALIDATION,
        DUPLICATE,
        ACCESS_DENIED,
        NOT_FOUND,
        STORAGE
    }

    private final Kind kind;

    public ServiceException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }
}
