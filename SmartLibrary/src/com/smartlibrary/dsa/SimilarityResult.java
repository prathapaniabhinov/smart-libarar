package com.smartlibrary.dsa;

import java.util.Collections;
import java.util.List;

/** Result of comparing two documents with one method. Positions are in the normalized texts. */
public final class SimilarityResult {

    /** A phrase found in both documents, with its first position in each. */
    public static final class CommonPhrase {
        private final String text;
        private final int positionInFirst;
        private final int positionInSecond;

        public CommonPhrase(String text, int positionInFirst, int positionInSecond) {
            this.text = text;
            this.positionInFirst = positionInFirst;
            this.positionInSecond = positionInSecond;
        }

        public String getText() { return text; }
        public int getPositionInFirst() { return positionInFirst; }
        public int getPositionInSecond() { return positionInSecond; }
    }

    private final SimilarityMethod method;
    private final int minLength;
    private final int totalLength;
    private final int shorterLength;
    private final int coveredCharacters;
    private final List<CommonPhrase> phrases;
    private final long buildNanos;
    private final long lcpNanos;
    private final long naiveNanos;
    private final long matchNanos;

    public SimilarityResult(SimilarityMethod method, int minLength, int totalLength, int shorterLength,
                            int coveredCharacters, List<CommonPhrase> phrases,
                            long buildNanos, long lcpNanos, long naiveNanos, long matchNanos) {
        this.method = method;
        this.minLength = minLength;
        this.totalLength = totalLength;
        this.shorterLength = shorterLength;
        this.coveredCharacters = coveredCharacters;
        this.phrases = Collections.unmodifiableList(phrases);
        this.buildNanos = buildNanos;
        this.lcpNanos = lcpNanos;
        this.naiveNanos = naiveNanos;
        this.matchNanos = matchNanos;
    }

    public SimilarityMethod getMethod() { return method; }
    public int getMinLength() { return minLength; }
    /** n: length of both normalized texts together. */
    public int getTotalLength() { return totalLength; }
    /** Letters and digits in the shorter document. */
    public int getShorterLength() { return shorterLength; }
    /** Letters and digits of the shorter document that lie inside a common phrase. */
    public int getCoveredCharacters() { return coveredCharacters; }
    /** Common phrases, longest first. */
    public List<CommonPhrase> getPhrases() { return phrases; }

    /** covered characters / length of the shorter document x 100. */
    public double getSimilarityPercent() {
        return shorterLength == 0 ? 0 : 100.0 * coveredCharacters / shorterLength;
    }

    public CommonPhrase getLongest() {
        return phrases.isEmpty() ? null : phrases.get(0);
    }

    /** Suffix array (SA-IS) or suffix tree (Ukkonen) construction time. */
    public double getBuildMillis() { return buildNanos / 1e6; }
    /** Kasai LCP time; negative for the suffix tree method. */
    public double getLcpMillis() { return lcpNanos / 1e6; }
    /** Naive suffix sort time for comparison; negative when skipped. */
    public double getNaiveMillis() { return naiveNanos / 1e6; }
    /** Time to find the common text once the structure is built. */
    public double getMatchMillis() { return matchNanos / 1e6; }
}
