package com.smartlibrary.dsa;

/**
 * The four string-matching algorithms of DSA Module 1, with their time complexity.
 * n = text length, m = pattern length, M = total length of all patterns, z = number of matches.
 */
public enum Algorithm {

    KMP("KMP (Knuth-Morris-Pratt)",
            "O(n + m)",
            "Builds an LPS table from the pattern, so it never goes back in the text."),
    RABIN_KARP("Rabin-Karp",
            "O(n + m) average, O(n * m) worst case",
            "Compares a rolling hash of each text window with the pattern's hash."),
    Z_ALGORITHM("Z Algorithm",
            "O(n + m)",
            "Builds the Z array of pattern + '$' + text; Z = m means a match."),
    AHO_CORASICK("Aho-Corasick",
            "O(n + M + z)",
            "Builds a trie of all patterns with failure links and scans the text once.");

    private final String displayName;
    private final String complexity;
    private final String idea;

    Algorithm(String displayName, String complexity, String idea) {
        this.displayName = displayName;
        this.complexity = complexity;
        this.idea = idea;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Big-O time complexity for searching one text. */
    public String getComplexity() {
        return complexity;
    }

    /** One-line explanation of how the algorithm works. */
    public String getIdea() {
        return idea;
    }

    /** True if the algorithm finds several patterns in a single pass over the text. */
    public boolean isMultiPattern() {
        return this == AHO_CORASICK;
    }

    /** Creates a new matcher for this algorithm. */
    public StringMatcher newMatcher() {
        switch (this) {
            case KMP:
                return new KmpMatcher();
            case RABIN_KARP:
                return new RabinKarpMatcher();
            case Z_ALGORITHM:
                return new ZAlgorithmMatcher();
            case AHO_CORASICK:
                return new AhoCorasickMatcher();
            default:
                throw new IllegalStateException("Unknown algorithm " + this);
        }
    }

    /**
     * Maps a menu number to an algorithm: 1 = KMP, 2 = Rabin-Karp, 3 = Z, 4 = Aho-Corasick.
     *
     * @throws IllegalArgumentException for any other number
     */
    public static Algorithm fromMenuChoice(int choice) {
        switch (choice) {
            case 1:
                return KMP;
            case 2:
                return RABIN_KARP;
            case 3:
                return Z_ALGORITHM;
            case 4:
                return AHO_CORASICK;
            default:
                throw new IllegalArgumentException("No algorithm for menu choice " + choice);
        }
    }

    @Override
    public String toString() {
        return displayName;
    }
}
