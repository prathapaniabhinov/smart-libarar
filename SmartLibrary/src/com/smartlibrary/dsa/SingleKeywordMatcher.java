package com.smartlibrary.dsa;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Base class for algorithms that look for one pattern at a time (KMP, Rabin-Karp, Z).
 * With several patterns the text is scanned once per pattern, so the cost is O(k * (n + m)).
 */
public abstract class SingleKeywordMatcher implements StringMatcher {

    @Override
    public MatchResult search(String text, List<String> patterns) {
        long[] comparisons = new long[1];
        List<Match> all = new ArrayList<>();
        for (String pattern : patterns) {
            if (pattern == null || pattern.isEmpty()) {
                continue;
            }
            for (int position : findAll(text, pattern, comparisons)) {
                all.add(new Match(position, pattern));
            }
        }
        all.sort(Comparator.comparingInt(Match::getPosition).thenComparing(Match::getPattern));
        return new MatchResult(algorithm(), patterns, all, comparisons[0]);
    }

    /**
     * Finds every start index of {@code pattern} in {@code text}, in increasing order.
     *
     * @param comparisons a one-element counter; add 1 for every comparison made
     */
    protected abstract List<Integer> findAll(String text, String pattern, long[] comparisons);
}
