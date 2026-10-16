package com.smartlibrary.dsa;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compares two documents (DSA Module 2). Structures are built once and reused:
 * <ul>
 *   <li>Suffix array of "first # second $" (SA-IS) + LCP array (Kasai)</li>
 *   <li>Suffix trees of each document (Ukkonen)</li>
 * </ul>
 * Both methods compute the same <i>matching statistics</i> of the shorter document against the
 * longer one (for every position: the longest text starting there that also occurs in the other
 * document), so they always produce the same common phrases and similarity.
 */
public final class ComparisonSession {

    /** Largest combined size accepted, to keep memory use small. */
    public static final int MAX_TOTAL_LENGTH = 1_000_000;
    /** Naive suffix sorting is only timed up to this size (it is slow on big inputs). */
    public static final int NAIVE_LIMIT = 200_000;

    private static final int SEPARATOR = 1;

    private final NormalizedText first;
    private final NormalizedText second;
    private final boolean firstIsShorter;
    private final String shorter;
    private final String longer;
    private final Map<Character, Integer> codes = new HashMap<>();
    private final int alphabetSize;
    private final int[] shorterCodes;
    private final int[] longerCodes;

    // suffix array structures (built on demand)
    private int[] combined;
    private int[] sa;
    private int[] lcp;
    private long saisNanos;
    private long kasaiNanos;
    private long naiveNanos = -1;

    // suffix trees (built on demand)
    private SuffixTree longerTree;
    private SuffixTree shorterTree;
    private long longerTreeNanos;

    public ComparisonSession(NormalizedText first, NormalizedText second) {
        if ((long) first.length() + second.length() > MAX_TOTAL_LENGTH) {
            throw new IllegalArgumentException("These books are too large to compare (limit "
                    + MAX_TOTAL_LENGTH + " characters together).");
        }
        this.first = first;
        this.second = second;
        this.firstIsShorter = first.length() <= second.length();
        this.shorter = firstIsShorter ? first.text() : second.text();
        this.longer = firstIsShorter ? second.text() : first.text();

        // alphabet: 0 = end sentinel, 1 = separator, letters from 2
        int next = 2;
        for (String s : new String[] { shorter, longer }) {
            for (int i = 0; i < s.length(); i++) {
                if (!codes.containsKey(s.charAt(i))) {
                    codes.put(s.charAt(i), next++);
                }
            }
        }
        this.alphabetSize = next;
        this.shorterCodes = encode(shorter);
        this.longerCodes = encode(longer);
    }

    public int firstLength() {
        return first.length();
    }

    public int secondLength() {
        return second.length();
    }

    public NormalizedText first() {
        return first;
    }

    public NormalizedText second() {
        return second;
    }

    // ================================================================ similarity

    public SimilarityResult compare(SimilarityMethod method, int minLength) {
        int[] ms;
        long build;
        long lcpTime = -1;
        long naive = -1;
        long start;
        if (method == SimilarityMethod.SUFFIX_ARRAY) {
            ensureSuffixArray();
            build = saisNanos;
            lcpTime = kasaiNanos;
            naive = naiveNanos;
            start = System.nanoTime();
            ms = matchingStatisticsFromSuffixArray();
        } else {
            ensureLongerTree();
            build = longerTreeNanos;
            start = System.nanoTime();
            ms = longerTree.matchingStatistics(shorterCodes);
        }
        long matchTime = System.nanoTime() - start;
        return buildResult(method, ms, minLength, build, lcpTime, naive, matchTime);
    }

    /** For each suffix of the shorter text: max LCP with any suffix of the longer text (nearest neighbours in SA). */
    private int[] matchingStatisticsFromSuffixArray() {
        int la = shorter.length();
        int total = combined.length;
        int[] ms = new int[la];
        // top-down: LCP with the nearest longer-text suffix above in the suffix array
        int run = 0;
        boolean seen = false;
        for (int r = 0; r < total; r++) {
            if (r > 0) {
                run = seen ? Math.min(run, lcp[r]) : 0;
            }
            int p = sa[r];
            if (p < la) {
                ms[p] = seen ? run : 0;
            } else if (p > la && p < total - 1) {
                run = Integer.MAX_VALUE;
                seen = true;
            }
        }
        // bottom-up: nearest longer-text suffix below
        run = 0;
        seen = false;
        for (int r = total - 1; r >= 0; r--) {
            if (r < total - 1) {
                run = seen ? Math.min(run, lcp[r + 1]) : 0;
            }
            int p = sa[r];
            if (p < la) {
                if (seen) {
                    ms[p] = Math.max(ms[p], run);
                }
            } else if (p > la && p < total - 1) {
                run = Integer.MAX_VALUE;
                seen = true;
            }
        }
        return ms;
    }

    private SimilarityResult buildResult(SimilarityMethod method, int[] ms, int minLength,
                                         long build, long lcpTime, long naive, long matchTime) {
        int la = shorter.length();
        // common phrases: maximal matches, trimmed to whole words.
        // Similarity counts the characters of the shorter text covered by these phrases.
        int covered = 0;
        int coveredReach = 0;
        Map<String, int[]> found = new LinkedHashMap<>();
        int lastEnd = -1;
        for (int j = 0; j < la; j++) {
            if (ms[j] < minLength || j + ms[j] <= lastEnd) {
                continue;
            }
            int matchEnd = j + ms[j];
            lastEnd = matchEnd;
            // a match may cross sentence breaks: split it into sentence pieces
            int pieceStart = j;
            for (int k = j; k <= matchEnd; k++) {
                if (k == matchEnd || shorter.charAt(k) == NormalizedText.BREAK) {
                    int[] piece = wholeWords(pieceStart, k);
                    pieceStart = k + 1;
                    if (piece == null) {
                        continue;
                    }
                    int s = piece[0];
                    int e = piece[1];
                    String phrase = shorter.substring(s, e);
                    if (phrase.length() < minLength || phrase.indexOf(' ') < 0) {
                        continue; // too short, or a single word
                    }
                    for (int c = Math.max(s, coveredReach); c < e; c++) {
                        if (!NormalizedText.isBoundary(shorter.charAt(c))) {
                            covered++; // count letters and digits only
                        }
                    }
                    coveredReach = Math.max(coveredReach, e);
                    if (!found.containsKey(phrase)) {
                        found.put(phrase, new int[] { s, firstOccurrenceInLonger(method, phrase) });
                    }
                }
            }
        }

        // longest first; drop phrases that are part of a longer phrase already listed
        List<Map.Entry<String, int[]>> entries = new ArrayList<>(found.entrySet());
        entries.sort((x, y) -> Integer.compare(y.getKey().length(), x.getKey().length()));
        List<Map.Entry<String, int[]>> kept = new ArrayList<>();
        for (Map.Entry<String, int[]> en : entries) {
            boolean inside = false;
            for (Map.Entry<String, int[]> k : kept) {
                if (k.getKey().contains(en.getKey())) {
                    inside = true;
                    break;
                }
            }
            if (!inside) {
                kept.add(en);
            }
        }

        List<SimilarityResult.CommonPhrase> phrases = new ArrayList<>();
        for (Map.Entry<String, int[]> en : kept) {
            int posShorter = en.getValue()[0];
            int posLonger = en.getValue()[1];
            phrases.add(firstIsShorter
                    ? new SimilarityResult.CommonPhrase(en.getKey(), posShorter, posLonger)
                    : new SimilarityResult.CommonPhrase(en.getKey(), posLonger, posShorter));
        }
        int countable = 0; // similarity is measured on letters and digits only
        for (int i = 0; i < la; i++) {
            if (!NormalizedText.isBoundary(shorter.charAt(i))) {
                countable++;
            }
        }
        return new SimilarityResult(method, minLength, first.length() + second.length(), countable, covered,
                phrases, build, lcpTime, naive, matchTime);
    }

    /** Trims [s, e) of the shorter text to whole words. Returns {s, e} or null if nothing is left. */
    private int[] wholeWords(int s, int e) {
        int la = shorter.length();
        if (s > 0 && !NormalizedText.isBoundary(shorter.charAt(s - 1)) && s < e
                && !NormalizedText.isBoundary(shorter.charAt(s))) {
            while (s < e && !NormalizedText.isBoundary(shorter.charAt(s))) {
                s++; // skip the partial first word
            }
        }
        if (e < la && e > s && !NormalizedText.isBoundary(shorter.charAt(e))
                && !NormalizedText.isBoundary(shorter.charAt(e - 1))) {
            while (e > s && !NormalizedText.isBoundary(shorter.charAt(e - 1))) {
                e--; // drop the partial last word
            }
        }
        while (s < e && NormalizedText.isBoundary(shorter.charAt(s))) {
            s++;
        }
        while (e > s && NormalizedText.isBoundary(shorter.charAt(e - 1))) {
            e--;
        }
        return e > s ? new int[] { s, e } : null;
    }

    private int firstOccurrenceInLonger(SimilarityMethod method, String phrase) {
        int[] p = encode(phrase);
        if (method == SimilarityMethod.SUFFIX_ARRAY) {
            return lookupSuffixArray(p)[3];
        }
        return longerTree.find(p).firstPosition;
    }

    // ================================================================ phrase lookup (substring retrieval)

    /** Occurrences of a phrase in each document. */
    public static final class PhraseLookup {
        public final String phrase;
        public final int countInFirst;
        public final int countInSecond;
        public final int firstPositionInFirst;
        public final int firstPositionInSecond;
        public final long nanos;

        PhraseLookup(String phrase, int countInFirst, int countInSecond, int firstPositionInFirst,
                     int firstPositionInSecond, long nanos) {
            this.phrase = phrase;
            this.countInFirst = countInFirst;
            this.countInSecond = countInSecond;
            this.firstPositionInFirst = firstPositionInFirst;
            this.firstPositionInSecond = firstPositionInSecond;
            this.nanos = nanos;
        }

        public double millis() {
            return nanos / 1e6;
        }
    }

    /** Document indexing: binary search for the phrase in the suffix array. O(m log n + occurrences). */
    public PhraseLookup findWithSuffixArray(String rawPhrase) {
        ensureSuffixArray();
        String phrase = NormalizedText.normalizePhrase(rawPhrase);
        long start = System.nanoTime();
        int[] r = phrase.isEmpty() ? new int[] { 0, -1, 0, -1 } : lookupSuffixArray(encode(phrase));
        long t = System.nanoTime() - start;
        return firstIsShorter
                ? new PhraseLookup(phrase, r[0], r[2], r[1], r[3], t)
                : new PhraseLookup(phrase, r[2], r[0], r[3], r[1], t);
    }

    /** Substring retrieval: walks the phrase down each document's suffix tree. O(m). */
    public PhraseLookup findWithSuffixTree(String rawPhrase) {
        ensureLongerTree();
        if (shorterTree == null) {
            shorterTree = new SuffixTree(withTerminal(shorterCodes));
        }
        String phrase = NormalizedText.normalizePhrase(rawPhrase);
        int[] p = encode(phrase);
        long start = System.nanoTime();
        SuffixTree.Occurrences s = shorterTree.find(p);
        SuffixTree.Occurrences l = longerTree.find(p);
        long t = System.nanoTime() - start;
        return firstIsShorter
                ? new PhraseLookup(phrase, s.count, l.count, s.firstPosition, l.firstPosition, t)
                : new PhraseLookup(phrase, l.count, s.count, l.firstPosition, s.firstPosition, t);
    }

    /** @return {count in shorter, first pos in shorter, count in longer, first pos in longer} */
    private int[] lookupSuffixArray(int[] p) {
        int total = combined.length;
        int la = shorter.length();
        int lo = lowerBound(p);
        int countA = 0;
        int countB = 0;
        int firstA = Integer.MAX_VALUE;
        int firstB = Integer.MAX_VALUE;
        for (int r = lo; r < total && startsWith(sa[r], p); r++) {
            int pos = sa[r];
            if (pos < la) {
                countA++;
                firstA = Math.min(firstA, pos);
            } else if (pos > la) {
                countB++;
                firstB = Math.min(firstB, pos - la - 1);
            }
        }
        return new int[] { countA, countA == 0 ? -1 : firstA, countB, countB == 0 ? -1 : firstB };
    }

    /** First rank whose suffix is >= p (binary search). */
    private int lowerBound(int[] p) {
        int lo = 0;
        int hi = combined.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (compareSuffix(sa[mid], p) < 0) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    private int compareSuffix(int pos, int[] p) {
        for (int k = 0; k < p.length; k++) {
            if (pos + k >= combined.length) {
                return -1;
            }
            int c = combined[pos + k];
            if (c != p[k]) {
                return Integer.compare(c, p[k]);
            }
        }
        return 0; // suffix starts with p
    }

    private boolean startsWith(int pos, int[] p) {
        return compareSuffix(pos, p) == 0;
    }

    // ================================================================ building

    private void ensureSuffixArray() {
        if (sa != null) {
            return;
        }
        int la = shorterCodes.length;
        int lb = longerCodes.length;
        combined = new int[la + lb + 2];
        System.arraycopy(shorterCodes, 0, combined, 0, la);
        combined[la] = SEPARATOR;
        System.arraycopy(longerCodes, 0, combined, la + 1, lb);
        combined[la + lb + 1] = 0;

        SuffixArraySAIS.build(combined, alphabetSize); // warm-up so timing is not dominated by JIT start-up
        long t = System.nanoTime();
        sa = SuffixArraySAIS.build(combined, alphabetSize);
        saisNanos = System.nanoTime() - t;

        t = System.nanoTime();
        lcp = LcpKasai.build(combined, sa);
        kasaiNanos = System.nanoTime() - t;

        if (combined.length <= NAIVE_LIMIT) {
            t = System.nanoTime();
            SuffixArraySAIS.buildNaive(combined);
            naiveNanos = System.nanoTime() - t;
        }
    }

    private void ensureLongerTree() {
        if (longerTree != null) {
            return;
        }
        new SuffixTree(withTerminal(longerCodes)); // warm-up
        long t = System.nanoTime();
        longerTree = new SuffixTree(withTerminal(longerCodes));
        longerTreeNanos = System.nanoTime() - t;
    }

    private int[] encode(String s) {
        int[] out = new int[s.length()];
        for (int i = 0; i < s.length(); i++) {
            Integer c = codes.get(s.charAt(i));
            out[i] = c == null ? -1 : c; // -1 never matches anything in the texts
        }
        return out;
    }

    private static int[] withTerminal(int[] s) {
        int[] t = new int[s.length + 1];
        System.arraycopy(s, 0, t, 0, s.length);
        t[s.length] = 0;
        return t;
    }
}
