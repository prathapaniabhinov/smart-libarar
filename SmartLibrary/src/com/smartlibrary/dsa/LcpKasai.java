package com.smartlibrary.dsa;

/**
 * LCP array with Kasai's algorithm. Time O(n).
 * <p>
 * lcp[r] = length of the longest common prefix of the suffixes at ranks r - 1 and r (lcp[0] = 0).
 * Key fact: if suffix i has LCP h with its neighbour, suffix i + 1 has LCP at least h - 1,
 * so the comparison never restarts from zero and the total work is linear.
 */
public final class LcpKasai {

    private LcpKasai() {
    }

    public static int[] build(int[] s, int[] sa) {
        int n = s.length;
        int[] rank = new int[n];
        for (int r = 0; r < n; r++) {
            rank[sa[r]] = r;
        }
        int[] lcp = new int[n];
        int h = 0;
        for (int i = 0; i < n; i++) {
            if (rank[i] > 0) {
                int j = sa[rank[i] - 1];
                while (i + h < n && j + h < n && s[i + h] == s[j + h]) {
                    h++;
                }
                lcp[rank[i]] = h;
                if (h > 0) {
                    h--;
                }
            } else {
                h = 0;
            }
        }
        return lcp;
    }
}
