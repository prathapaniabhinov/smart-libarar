package com.smartlibrary.dsa;

import java.util.Arrays;

/**
 * Suffix array construction with SA-IS (Suffix Array by Induced Sorting, Nong, Zhang and Chan 2009).
 * Time O(n), extra space O(n).
 * <p>
 * Idea: classify every suffix as S-type (smaller than the next suffix) or L-type (larger).
 * Sort only the "LMS" suffixes (leftmost S-type in each run), recursively if needed, then
 * <i>induce</i> the order of all other suffixes from them in two linear passes over buckets.
 */
public final class SuffixArraySAIS {

    private SuffixArraySAIS() {
    }

    /**
     * Builds the suffix array of {@code s}.
     *
     * @param s text as integers in [0, k); the last value must be 0 and 0 must appear nowhere else
     * @param k alphabet size
     * @return sa where sa[r] = start index of the r-th smallest suffix (sa[0] is the final 0)
     */
    public static int[] build(int[] s, int k) {
        int n = s.length;
        if (n == 0 || s[n - 1] != 0) {
            throw new IllegalArgumentException("Text must end with a unique 0 sentinel");
        }
        int[] sa = new int[n];
        if (n == 1) {
            return sa;
        }
        sais(s, sa, n, k);
        return sa;
    }

    /**
     * Simple construction for comparison: sorts all suffixes with string comparison.
     * Time O(n^2 log n) in the worst case (O(n log n) comparisons, each up to O(n)).
     */
    public static int[] buildNaive(int[] s) {
        int n = s.length;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> {
            if (a.equals(b)) {
                return 0;
            }
            int i = a;
            int j = b;
            while (i < n && j < n) {
                if (s[i] != s[j]) {
                    return Integer.compare(s[i], s[j]);
                }
                i++;
                j++;
            }
            return Integer.compare(n - a, n - b); // shorter suffix first
        });
        int[] sa = new int[n];
        for (int i = 0; i < n; i++) {
            sa[i] = order[i];
        }
        return sa;
    }

    // ------------------------------------------------------------------ SA-IS

    private static void sais(int[] s, int[] sa, int n, int k) {
        // t[i] = true if suffix i is S-type
        boolean[] t = new boolean[n];
        t[n - 1] = true;
        for (int i = n - 2; i >= 0; i--) {
            t[i] = s[i] < s[i + 1] || (s[i] == s[i + 1] && t[i + 1]);
        }
        int[] bkt = new int[k];

        // 1. place LMS suffixes at the ends of their buckets, then induce
        getBuckets(s, bkt, n, k, true);
        Arrays.fill(sa, -1);
        for (int i = 1; i < n; i++) {
            if (isLms(t, i)) {
                sa[--bkt[s[i]]] = i;
            }
        }
        induceL(t, sa, s, bkt, n, k);
        induceS(t, sa, s, bkt, n, k);

        // 2. compact the sorted LMS substrings and name them
        int n1 = 0;
        for (int i = 0; i < n; i++) {
            if (isLms(t, sa[i])) {
                sa[n1++] = sa[i];
            }
        }
        Arrays.fill(sa, n1, n, -1);
        int name = 0;
        int prev = -1;
        for (int i = 0; i < n1; i++) {
            int pos = sa[i];
            boolean diff = false;
            for (int d = 0; d < n; d++) {
                if (prev == -1 || s[pos + d] != s[prev + d] || t[pos + d] != t[prev + d]) {
                    diff = true;
                    break;
                } else if (d > 0 && (isLms(t, pos + d) || isLms(t, prev + d))) {
                    break;
                }
            }
            if (diff) {
                name++;
                prev = pos;
            }
            sa[n1 + pos / 2] = name - 1;
        }
        for (int i = n - 1, j = n - 1; i >= n1; i--) {
            if (sa[i] >= 0) {
                sa[j--] = sa[i];
            }
        }

        // 3. sort the reduced string (recursively if names are not unique)
        int[] s1 = Arrays.copyOfRange(sa, n - n1, n);
        int[] sa1 = new int[n1];
        if (name < n1) {
            sais(s1, sa1, n1, name);
        } else {
            for (int i = 0; i < n1; i++) {
                sa1[s1[i]] = i;
            }
        }

        // 4. induce the final order from the sorted LMS suffixes
        int[] lms = new int[n1];
        for (int i = 1, j = 0; i < n; i++) {
            if (isLms(t, i)) {
                lms[j++] = i;
            }
        }
        for (int i = 0; i < n1; i++) {
            sa1[i] = lms[sa1[i]];
        }
        getBuckets(s, bkt, n, k, true);
        Arrays.fill(sa, -1);
        for (int i = n1 - 1; i >= 0; i--) {
            int j = sa1[i];
            sa[--bkt[s[j]]] = j;
        }
        induceL(t, sa, s, bkt, n, k);
        induceS(t, sa, s, bkt, n, k);
    }

    private static boolean isLms(boolean[] t, int i) {
        return i > 0 && t[i] && !t[i - 1];
    }

    /** Bucket starts (end = false) or bucket ends (end = true) for every character. */
    private static void getBuckets(int[] s, int[] bkt, int n, int k, boolean end) {
        Arrays.fill(bkt, 0);
        for (int i = 0; i < n; i++) {
            bkt[s[i]]++;
        }
        int sum = 0;
        for (int i = 0; i < k; i++) {
            sum += bkt[i];
            bkt[i] = end ? sum : sum - bkt[i];
        }
    }

    /** Left-to-right pass: place L-type suffixes at the fronts of their buckets. */
    private static void induceL(boolean[] t, int[] sa, int[] s, int[] bkt, int n, int k) {
        getBuckets(s, bkt, n, k, false);
        for (int i = 0; i < n; i++) {
            int j = sa[i] - 1;
            if (sa[i] > 0 && !t[j]) {
                sa[bkt[s[j]]++] = j;
            }
        }
    }

    /** Right-to-left pass: place S-type suffixes at the ends of their buckets. */
    private static void induceS(boolean[] t, int[] sa, int[] s, int[] bkt, int n, int k) {
        getBuckets(s, bkt, n, k, true);
        for (int i = n - 1; i >= 0; i--) {
            int j = sa[i] - 1;
            if (sa[i] > 0 && t[j]) {
                sa[--bkt[s[j]]] = j;
            }
        }
    }
}
