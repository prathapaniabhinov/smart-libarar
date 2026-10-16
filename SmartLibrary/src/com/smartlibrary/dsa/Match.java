package com.smartlibrary.dsa;

/** One occurrence of a pattern: the index in the text where it starts. */
public final class Match {

    private final int position;
    private final String pattern;

    public Match(int position, String pattern) {
        this.position = position;
        this.pattern = pattern;
    }

    /** Index (0-based) in the text where the pattern starts. */
    public int getPosition() {
        return position;
    }

    public String getPattern() {
        return pattern;
    }

    @Override
    public String toString() {
        return pattern + "@" + position;
    }
}
