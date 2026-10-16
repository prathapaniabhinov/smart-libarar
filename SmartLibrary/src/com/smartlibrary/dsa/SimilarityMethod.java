package com.smartlibrary.dsa;

/** The two ways DSA Module 2 finds the common text of two documents. */
public enum SimilarityMethod {

    SUFFIX_ARRAY("Suffix Array (SA-IS) + LCP (Kasai)",
            "O(n)",
            "Sorts all suffixes of both books (SA-IS); neighbours from different books with a long LCP are common text."),
    SUFFIX_TREE("Suffix Tree (Ukkonen)",
            "O(n)",
            "Builds a suffix tree of one book and walks the other book through it using suffix links.");

    private final String displayName;
    private final String complexity;
    private final String idea;

    SimilarityMethod(String displayName, String complexity, String idea) {
        this.displayName = displayName;
        this.complexity = complexity;
        this.idea = idea;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getComplexity() {
        return complexity;
    }

    public String getIdea() {
        return idea;
    }

    /** Menu number to method: 1 = Suffix Array, 2 = Suffix Tree. */
    public static SimilarityMethod fromMenuChoice(int choice) {
        switch (choice) {
            case 1:
                return SUFFIX_ARRAY;
            case 2:
                return SUFFIX_TREE;
            default:
                throw new IllegalArgumentException("No method for menu choice " + choice);
        }
    }

    @Override
    public String toString() {
        return displayName;
    }
}
