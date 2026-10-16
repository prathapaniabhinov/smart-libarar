package com.smartlibrary.dsa;

import java.util.ArrayList;
import java.util.List;

/**
 * Knuth-Morris-Pratt. Time O(n + m), extra space O(m).
 * <p>
 * The LPS (longest proper prefix that is also a suffix) table tells the search how much of the
 * pattern is already matched after a mismatch, so the text pointer never moves backwards.
 */
public class KmpMatcher extends SingleKeywordMatcher {

    @Override
    public Algorithm algorithm() {
        return Algorithm.KMP;
    }

    @Override
    protected List<Integer> findAll(String text, String pattern, long[] comparisons) {
        List<Integer> result = new ArrayList<>();
        int n = text.length();
        int m = pattern.length();
        if (m == 0 || m > n) {
            return result;
        }
        int[] lps = buildLps(pattern, comparisons);

        int j = 0; // number of pattern characters matched so far
        for (int i = 0; i < n; i++) {
            while (true) {
                comparisons[0]++;
                if (text.charAt(i) == pattern.charAt(j)) {
                    j++;
                    break;
                }
                if (j == 0) {
                    break;
                }
                j = lps[j - 1]; // fall back inside the pattern, not in the text
            }
            if (j == m) {
                result.add(i - m + 1);
                j = lps[m - 1]; // keep going to find overlapping matches
            }
        }
        return result;
    }

    /** Builds the LPS table: lps[i] = length of the longest proper prefix of p[0..i] that is also its suffix. */
    public static int[] buildLps(String pattern, long[] comparisons) {
        int m = pattern.length();
        int[] lps = new int[m];
        int len = 0;
        int i = 1;
        while (i < m) {
            comparisons[0]++;
            if (pattern.charAt(i) == pattern.charAt(len)) {
                len++;
                lps[i] = len;
                i++;
            } else if (len > 0) {
                len = lps[len - 1];
            } else {
                lps[i] = 0;
                i++;
            }
        }
        return lps;
    }
}
