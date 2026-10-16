package com.smartlibrary.dsa;

import java.util.ArrayList;
import java.util.List;

/**
 * Z Algorithm. Time O(n + m), extra space O(m).
 * <p>
 * Works on the virtual string S = pattern + '$' + text, where '$' matches nothing.
 * Z[k] = length of the longest substring starting at k that equals a prefix of S.
 * Wherever Z[k] == m, the pattern occurs in the text at k - m - 1.
 * <p>
 * Because '$' never matches, every Z value is at most m, so only the first m + 1 Z values
 * ever need to be stored; the joined string is never actually built.
 */
public class ZAlgorithmMatcher extends SingleKeywordMatcher {

    @Override
    public Algorithm algorithm() {
        return Algorithm.Z_ALGORITHM;
    }

    @Override
    protected List<Integer> findAll(String text, String pattern, long[] comparisons) {
        List<Integer> result = new ArrayList<>();
        int n = text.length();
        int m = pattern.length();
        if (m == 0 || m > n) {
            return result;
        }
        int total = m + 1 + n;
        int[] zPrefix = new int[m + 1]; // Z values for the pattern part (and the '$')
        int left = 0;
        int right = 0; // [left, right) is the rightmost Z-box found so far

        for (int k = 1; k < total; k++) {
            int z = 0;
            if (k < right) {
                z = Math.min(right - k, zPrefix[k - left]); // reuse a value computed earlier
            }
            while (k + z < total && same(pattern, text, m, z, k + z, comparisons)) {
                z++;
            }
            if (k + z > right) {
                left = k;
                right = k + z;
            }
            if (k <= m) {
                zPrefix[k] = z;
            } else if (z == m) {
                result.add(k - m - 1);
            }
        }
        return result;
    }

    /** Compares S[a] and S[b] of the virtual string pattern + '$' + text. '$' (index m) matches nothing. */
    private static boolean same(String pattern, String text, int m, int a, int b, long[] comparisons) {
        if (a == m || b == m) {
            return false;
        }
        comparisons[0]++;
        return charAt(pattern, text, m, a) == charAt(pattern, text, m, b);
    }

    private static char charAt(String pattern, String text, int m, int index) {
        return index < m ? pattern.charAt(index) : text.charAt(index - m - 1);
    }
}
