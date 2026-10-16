package com.smartlibrary.dsa;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Word-level sequence alignment of two documents, the way DNA sequences are aligned.
 * Scores: match +2, mismatch -1, gap -1. Both algorithms are O(n * m) time, n and m = word counts.
 * <ul>
 *   <li><b>Needleman-Wunsch</b> (global): best alignment of the whole of both texts.</li>
 *   <li><b>Smith-Waterman</b> (local): the best-matching passage, even if some words were changed,
 *       added or removed - useful for spotting reworded copying.</li>
 * </ul>
 * Only the first {@link #MAX_WORDS} words of each document are aligned, to keep memory small.
 */
public final class SequenceAlignment {

    public static final int MATCH = 2;
    public static final int MISMATCH = -1;
    public static final int GAP = -1;
    public static final int MAX_WORDS = 3000;

    private static final byte STOP = 0;
    private static final byte DIAG = 1;
    private static final byte UP = 2;   // word from the first text against a gap
    private static final byte LEFT = 3; // word from the second text against a gap

    private SequenceAlignment() {
    }

    /** A word and where it starts in the normalized text. */
    public static final class Word {
        public final String text;
        public final int position;

        Word(String text, int position) {
            this.text = text;
            this.position = position;
        }
    }

    /** One column of the alignment: indexes into the two word lists, -1 for a gap. */
    public static final class Column {
        public final int first;
        public final int second;

        Column(int first, int second) {
            this.first = first;
            this.second = second;
        }
    }

    /** Result of an alignment. */
    public static final class Result {
        public final boolean local;
        public final int score;
        public final List<Column> columns;
        public final int matches;
        public final int mismatches;
        public final int gaps;
        public final int firstWords;
        public final int secondWords;
        public final boolean truncated;
        public final long cells;
        public final long nanos;

        Result(boolean local, int score, List<Column> columns, int firstWords, int secondWords,
               boolean truncated, long nanos, List<Word> a, List<Word> b) {
            this.local = local;
            this.score = score;
            this.columns = Collections.unmodifiableList(columns);
            int mt = 0;
            int mm = 0;
            int g = 0;
            for (Column c : columns) {
                if (c.first < 0 || c.second < 0) {
                    g++;
                } else if (a.get(c.first).text.equals(b.get(c.second).text)) {
                    mt++;
                } else {
                    mm++;
                }
            }
            this.matches = mt;
            this.mismatches = mm;
            this.gaps = g;
            this.firstWords = firstWords;
            this.secondWords = secondWords;
            this.truncated = truncated;
            this.cells = (long) (firstWords + 1) * (secondWords + 1);
            this.nanos = nanos;
        }

        /** Matching columns / all columns x 100. */
        public double identityPercent() {
            return columns.isEmpty() ? 0 : 100.0 * matches / columns.size();
        }

        public double millis() {
            return nanos / 1e6;
        }
    }

    /** Splits a normalized text into words (sentence breaks are not words). */
    public static List<Word> words(NormalizedText t) {
        List<Word> out = new ArrayList<>();
        String s = t.text();
        int i = 0;
        while (i < s.length()) {
            while (i < s.length() && NormalizedText.isBoundary(s.charAt(i))) {
                i++;
            }
            int start = i;
            while (i < s.length() && !NormalizedText.isBoundary(s.charAt(i))) {
                i++;
            }
            if (i > start) {
                out.add(new Word(s.substring(start, i), start));
            }
        }
        return out;
    }

    public static Result needlemanWunsch(List<Word> a, List<Word> b) {
        return align(a, b, false);
    }

    public static Result smithWaterman(List<Word> a, List<Word> b) {
        return align(a, b, true);
    }

    private static Result align(List<Word> fullA, List<Word> fullB, boolean local) {
        boolean truncated = fullA.size() > MAX_WORDS || fullB.size() > MAX_WORDS;
        List<Word> a = fullA.size() > MAX_WORDS ? fullA.subList(0, MAX_WORDS) : fullA;
        List<Word> b = fullB.size() > MAX_WORDS ? fullB.subList(0, MAX_WORDS) : fullB;
        int n = a.size();
        int m = b.size();
        long start = System.nanoTime();

        byte[] trace = new byte[(n + 1) * (m + 1)];
        int[] prev = new int[m + 1];
        int[] cur = new int[m + 1];
        int bestScore = 0;
        int bestI = 0;
        int bestJ = 0;

        // first row: only gaps (global) or zero (local)
        for (int j = 1; j <= m; j++) {
            prev[j] = local ? 0 : j * GAP;
            trace[j] = local ? STOP : LEFT;
        }
        for (int i = 1; i <= n; i++) {
            cur[0] = local ? 0 : i * GAP;
            trace[i * (m + 1)] = local ? STOP : UP;
            String wa = a.get(i - 1).text;
            for (int j = 1; j <= m; j++) {
                int diag = prev[j - 1] + (wa.equals(b.get(j - 1).text) ? MATCH : MISMATCH);
                int up = prev[j] + GAP;
                int left = cur[j - 1] + GAP;
                int best = diag;
                byte dir = DIAG;
                if (up > best) {
                    best = up;
                    dir = UP;
                }
                if (left > best) {
                    best = left;
                    dir = LEFT;
                }
                if (local && best <= 0) {
                    best = 0;
                    dir = STOP; // Smith-Waterman: start a new local alignment here
                }
                cur[j] = best;
                trace[i * (m + 1) + j] = dir;
                if (local && best > bestScore) {
                    bestScore = best;
                    bestI = i;
                    bestJ = j;
                }
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }

        int score = local ? bestScore : prev[m];
        int i = local ? bestI : n;
        int j = local ? bestJ : m;
        List<Column> cols = new ArrayList<>();
        while (i > 0 || j > 0) { // trace back the best path
            byte dir = trace[i * (m + 1) + j];
            if (local && dir == STOP) {
                break;
            }
            if (dir == DIAG) {
                cols.add(new Column(i - 1, j - 1));
                i--;
                j--;
            } else if (dir == UP) {
                cols.add(new Column(i - 1, -1));
                i--;
            } else {
                cols.add(new Column(-1, j - 1));
                j--;
            }
        }
        Collections.reverse(cols);
        long nanos = System.nanoTime() - start;
        return new Result(local, score, cols, n, m, truncated, nanos, a, b);
    }
}
