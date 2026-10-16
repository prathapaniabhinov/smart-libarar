package com.smartlibrary.dsa;

import java.util.ArrayList;
import java.util.List;

/**
 * Rabin-Karp. Average time O(n + m), worst case O(n * m) when many hashes collide.
 * <p>
 * The pattern and every text window of the same length are turned into numbers (hashes).
 * The window hash is "rolled" in O(1) per step; characters are compared only when hashes are equal.
 */
public class RabinKarpMatcher extends SingleKeywordMatcher {

    private static final long BASE = 256;
    private static final long MOD = 1_000_000_007L;

    @Override
    public Algorithm algorithm() {
        return Algorithm.RABIN_KARP;
    }

    @Override
    protected List<Integer> findAll(String text, String pattern, long[] comparisons) {
        List<Integer> result = new ArrayList<>();
        int n = text.length();
        int m = pattern.length();
        if (m == 0 || m > n) {
            return result;
        }

        // high = BASE^(m-1) % MOD: weight of the character that leaves the window
        long high = 1;
        for (int i = 0; i < m - 1; i++) {
            high = (high * BASE) % MOD;
        }

        long patternHash = 0;
        long windowHash = 0;
        for (int i = 0; i < m; i++) {
            patternHash = (patternHash * BASE + pattern.charAt(i)) % MOD;
            windowHash = (windowHash * BASE + text.charAt(i)) % MOD;
        }

        for (int s = 0; s <= n - m; s++) {
            comparisons[0]++; // hash comparison
            if (patternHash == windowHash) {
                int k = 0;
                while (k < m) { // verify, because different strings can have the same hash
                    comparisons[0]++;
                    if (text.charAt(s + k) != pattern.charAt(k)) {
                        break;
                    }
                    k++;
                }
                if (k == m) {
                    result.add(s);
                }
            }
            if (s < n - m) {
                // roll: remove text[s], shift, add text[s + m]
                windowHash = (windowHash - (text.charAt(s) * high) % MOD + MOD) % MOD;
                windowHash = (windowHash * BASE + text.charAt(s + m)) % MOD;
            }
        }
        return result;
    }
}
