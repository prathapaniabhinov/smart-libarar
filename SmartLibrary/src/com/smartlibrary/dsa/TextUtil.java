package com.smartlibrary.dsa;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Text helpers used by the search: case folding, keyword parsing and line lookup. */
public final class TextUtil {

    private TextUtil() {
    }

    /**
     * Lower-cases character by character, so the result has exactly the same length as the input
     * and match positions map straight back to the original text.
     */
    public static String toLowerSameLength(String s) {
        char[] chars = s.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            chars[i] = Character.toLowerCase(chars[i]);
        }
        return new String(chars);
    }

    /**
     * Splits user input on commas into lower-case keywords: "Tree, Graph" -> [tree, graph].
     * Blank and duplicate keywords are removed. Spaces inside a keyword are kept ("binary tree").
     */
    public static List<String> parseKeywords(String input) {
        Set<String> result = new LinkedHashSet<>();
        if (input != null) {
            for (String part : input.split(",")) {
                String k = toLowerSameLength(part.trim());
                if (!k.isEmpty()) {
                    result.add(k);
                }
            }
        }
        return new ArrayList<>(result);
    }

    /** Index of the first character of every line. Handles \n, \r\n and \r line endings. */
    public static int[] lineStarts(String text) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || (c == '\r' && (i + 1 >= text.length() || text.charAt(i + 1) != '\n'))) {
                starts.add(i + 1);
            }
        }
        int[] arr = new int[starts.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = starts.get(i);
        }
        return arr;
    }

    /** Binary search: 0-based line index containing text position {@code pos}. O(log lines). */
    public static int lineIndexOf(int[] lineStarts, int pos) {
        int lo = 0;
        int hi = lineStarts.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (lineStarts[mid] <= pos) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }
}
