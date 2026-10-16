package com.smartlibrary.dsa;

import java.util.Arrays;
import java.util.Locale;

/**
 * A book's text prepared for comparison:
 * lower case, letters and digits kept, sentence ends (. ! ? ; :) turned into a '.' break,
 * everything else turned into a single space, chapter heading lines ("Chapter 3: ...") left out.
 * <p>
 * Keeps a map from every normalized position back to the original text, so results can be
 * reported with line numbers in the real book.
 */
public final class NormalizedText {

    private final String text;
    private final int[] originalIndex;

    public NormalizedText(String original) {
        StringBuilder sb = new StringBuilder(original.length());
        int[] map = new int[original.length() + 1];
        int count = 0;
        int lineStart = 0;
        int len = original.length();
        while (lineStart <= len) {
            int lineEnd = original.indexOf('\n', lineStart);
            if (lineEnd < 0) {
                lineEnd = len;
            }
            if (!isHeading(original.substring(lineStart, lineEnd))) {
                for (int i = lineStart; i < lineEnd; i++) {
                    char c = original.charAt(i);
                    if (Character.isLetterOrDigit(c)) {
                        sb.append(Character.toLowerCase(c));
                        map[count++] = i;
                    } else if (isSentenceEnd(c)) {
                        // sentence end: a '.' break that common phrases are cut at
                        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == ' ') {
                            sb.setLength(sb.length() - 1);
                            count--;
                        }
                        if (sb.length() > 0 && sb.charAt(sb.length() - 1) != BREAK) {
                            sb.append(BREAK);
                            map[count++] = i;
                        }
                    } else if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ') {
                        sb.append(' ');
                        map[count++] = i;
                    }
                }
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ') {
                    sb.append(' '); // line break counts as a space
                    map[count++] = Math.min(lineEnd, Math.max(0, len - 1));
                }
            }
            lineStart = lineEnd + 1;
        }
        while (sb.length() > 0 && sb.charAt(sb.length() - 1) == ' ') {
            sb.setLength(sb.length() - 1);
            count--;
        }
        this.text = sb.toString();
        this.originalIndex = Arrays.copyOf(map, Math.max(count, 0));
    }

    /** Normalizes a phrase typed by the user the same way (no heading removal). */
    public static String normalizePhrase(String phrase) {
        StringBuilder sb = new StringBuilder();
        for (char c : phrase.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            } else if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ') {
                sb.append(' ');
            }
        }
        return sb.toString().trim();
    }

    /** Marks the end of a sentence in the normalized text. Common phrases never contain it. */
    public static final char BREAK = '.';

    private static boolean isSentenceEnd(char c) {
        return c == '.' || c == '!' || c == '?' || c == ';' || c == ':';
    }

    /** True for characters that separate words: space or sentence break. */
    public static boolean isBoundary(char c) {
        return c == ' ' || c == BREAK;
    }

    private static boolean isHeading(String line) {
        return line.trim().toLowerCase(Locale.ROOT).matches("chapter\\s+\\d.*");
    }

    public String text() {
        return text;
    }

    public int length() {
        return text.length();
    }

    /** Index in the original book text of normalized position {@code pos}. */
    public int originalIndex(int pos) {
        return originalIndex[pos];
    }
}
