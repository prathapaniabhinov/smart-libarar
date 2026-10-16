package com.smartlibrary.dsa;

import java.util.List;

/**
 * Common interface for the Module 1 string-matching algorithms.
 * Matching is exact; callers lower-case text and patterns first for case-insensitive search.
 */
public interface StringMatcher {

    /** Which algorithm this matcher implements. */
    Algorithm algorithm();

    /**
     * Finds every occurrence (overlapping occurrences included) of every pattern in the text.
     * Empty patterns are ignored.
     *
     * @return matches sorted by position, plus the number of character/hash comparisons made
     */
    MatchResult search(String text, List<String> patterns);
}
