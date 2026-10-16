package com.smartlibrary.dsa;

/**
 * Edit distances with dynamic programming. Both run in O(n * m) time.
 * <ul>
 *   <li><b>Levenshtein</b>: minimum insertions, deletions and substitutions to turn a into b.</li>
 *   <li><b>Damerau-Levenshtein</b> (optimal string alignment form): also allows swapping two
 *       neighbouring letters as one edit, so "garph" -> "graph" costs 1 instead of 2.</li>
 * </ul>
 * dp[i][j] = distance between the first i letters of a and the first j letters of b.
 */
public final class EditDistance {

    private EditDistance() {
    }

    /** Levenshtein distance using two rows of the DP table: O(n * m) time, O(m) space. */
    public static int levenshtein(String a, String b) {
        int n = a.length();
        int m = b.length();
        int[] prev = new int[m + 1];
        int[] cur = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            prev[j] = j; // j insertions
        }
        for (int i = 1; i <= n; i++) {
            cur[0] = i; // i deletions
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(
                        prev[j] + 1,          // delete a[i-1]
                        cur[j - 1] + 1),      // insert b[j-1]
                        prev[j - 1] + cost);  // keep or substitute
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[m];
    }

    /** Damerau-Levenshtein (optimal string alignment) using three rows: O(n * m) time, O(m) space. */
    public static int damerauLevenshtein(String a, String b) {
        int n = a.length();
        int m = b.length();
        int[] prev2 = new int[m + 1];
        int[] prev = new int[m + 1];
        int[] cur = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                int best = Math.min(Math.min(prev[j] + 1, cur[j - 1] + 1), prev[j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    best = Math.min(best, prev2[j - 2] + 1); // swap two neighbouring letters
                }
                cur[j] = best;
            }
            int[] t = prev2;
            prev2 = prev;
            prev = cur;
            cur = t;
        }
        return prev[m];
    }
}
