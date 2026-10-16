package com.smartlibrary.console;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.smartlibrary.dsa.OptimalBST;
import com.smartlibrary.service.BookService;
import com.smartlibrary.service.ServiceException;

/**
 * Search Statistics (DSA Module 3): builds an Optimal Binary Search Tree from how often each
 * keyword has been searched, so frequently searched keywords are found with fewer comparisons.
 * Librarian and Admin only.
 */
public class SearchStatsConsole {

    /** Largest number of keywords put in the tree (most searched first). O(n^3) DP. */
    private static final int MAX_KEYS = 50;
    private static final int MAX_DRAW_DEPTH = 5;

    private final ConsoleIO io;
    private final BookService books;

    public SearchStatsConsole(ConsoleIO io, BookService books) {
        this.io = io;
        this.books = books;
    }

    public void run() throws ServiceException {
        io.header("Search Statistics - Optimal Binary Search Tree");
        Map<String, Integer> freq = books.searchFrequencies();
        boolean demo = false;
        if (freq.isEmpty()) {
            io.info("No searches recorded yet. Every successful search in Search Books is counted.");
            if (!io.confirm("Use DEMO frequencies to see how it works?")) {
                return;
            }
            demo = true;
            freq = new LinkedHashMap<>();
            freq.put("tree", 12);
            freq.put("graph", 7);
            freq.put("process", 5);
            freq.put("heap", 4);
            freq.put("queue", 3);
            freq.put("kmp", 2);
            freq.put("algebra", 1);
        }

        // most searched keywords first
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(freq.entrySet());
        entries.sort((a, b) -> {
            int c = Integer.compare(b.getValue(), a.getValue());
            return c != 0 ? c : a.getKey().compareTo(b.getKey());
        });
        Map<String, Integer> used = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : entries) {
            if (used.size() == MAX_KEYS) {
                break;
            }
            used.put(e.getKey(), e.getValue());
        }

        if (demo) {
            io.println("  *** DEMO DATA - not real searches ***");
        }
        List<String[]> rows = new ArrayList<>();
        int rank = 1;
        for (Map.Entry<String, Integer> e : used.entrySet()) {
            if (rank > 15) {
                break;
            }
            rows.add(new String[] { Integer.toString(rank++), e.getKey(), Integer.toString(e.getValue()) });
        }
        io.table(new String[] { "#", "Keyword", "Searches" }, rows);
        if (freq.size() > used.size()) {
            io.info("Using the " + used.size() + " most searched of " + freq.size() + " keywords.");
        }

        OptimalBST tree = new OptimalBST(used);
        double optimal = tree.expectedComparisons();
        double balanced = tree.balancedExpectedComparisons();
        io.blank();
        io.println("  Keys in tree                : " + tree.size() + "  (" + tree.totalFrequency() + " searches)");
        io.println("  DP built in                 : " + ms(tree.buildMillis()) + " ms   O(n^3) time, O(n^2) space");
        io.println("  Avg comparisons, Optimal BST: " + fmt(optimal));
        io.println("  Avg comparisons, balanced   : " + fmt(balanced) + "  (same keys, ignoring frequency)");
        if (balanced > 0) {
            io.println("  Saving                      : " + fmt(100.0 * (balanced - optimal) / balanced) + " %");
        }
        io.blank();
        io.println("  Optimal BST (keyword (searches), L = left child, R = right child):");
        for (String line : tree.draw(MAX_DRAW_DEPTH)) {
            io.println("    " + line);
        }

        while (true) {
            io.blank();
            String key = io.readLine("Look up a keyword in the tree (Enter = back): ").trim().toLowerCase(Locale.ROOT);
            if (key.isEmpty()) {
                return;
            }
            int o = tree.comparisonsInOptimal(key);
            int b = tree.comparisonsInBalanced(key);
            if (o > 0) {
                io.println("  Found \"" + key + "\": Optimal BST " + o + " comparison(s), balanced BST " + b + ".");
            } else {
                io.println("  \"" + key + "\" is not in the tree (Optimal BST checked " + (-o)
                        + " node(s), balanced BST " + (-b) + ").");
            }
        }
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static String ms(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }
}
