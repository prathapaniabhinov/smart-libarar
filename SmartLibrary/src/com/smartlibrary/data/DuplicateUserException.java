package com.smartlibrary.data;

/** Thrown when a username or email already belongs to another account. */
public class DuplicateUserException extends Exception {

    private static final long serialVersionUID = 1L;

    public DuplicateUserException(String message) {
        super(message);
    }
}
