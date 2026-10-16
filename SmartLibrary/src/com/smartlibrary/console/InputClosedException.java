package com.smartlibrary.console;

/** Thrown when console input ends (for example the Eclipse Console is terminated). */
public class InputClosedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InputClosedException() {
        super("Console input closed");
    }
}
