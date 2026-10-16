package com.smartlibrary.dsa;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The result of running one algorithm on one text. */
public final class MatchResult {

    private final Algorithm algorithm;
    private final List<String> patterns;
    private final List<Match> matches;
    private final long comparisons;
    private long elapsedNanos;

    public MatchResult(Algorithm algorithm, List<String> patterns, List<Match> matches, long comparisons) {
        this.algorithm = algorithm;
        this.patterns = Collections.unmodifiableList(new ArrayList<>(patterns));
        this.matches = Collections.unmodifiableList(new ArrayList<>(matches));
        this.comparisons = comparisons;
    }

    public Algorithm getAlgorithm() {
        return algorithm;
    }

    public List<String> getPatterns() {
        return patterns;
    }

    /** All matches, sorted by position. */
    public List<Match> getMatches() {
        return matches;
    }

    public int getMatchCount() {
        return matches.size();
    }

    /** Number of matches of one particular pattern. */
    public int countFor(String pattern) {
        int count = 0;
        for (Match m : matches) {
            if (m.getPattern().equals(pattern)) {
                count++;
            }
        }
        return count;
    }

    /** Character comparisons (plus hash comparisons for Rabin-Karp, transitions for Aho-Corasick). */
    public long getComparisons() {
        return comparisons;
    }

    public long getElapsedNanos() {
        return elapsedNanos;
    }

    public void setElapsedNanos(long elapsedNanos) {
        this.elapsedNanos = elapsedNanos;
    }

    public double getElapsedMillis() {
        return elapsedNanos / 1_000_000.0;
    }
}
