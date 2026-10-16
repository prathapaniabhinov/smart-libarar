package com.smartlibrary;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.stream.Stream;

import com.smartlibrary.data.SearchLogCSV;
import com.smartlibrary.dsa.EditDistance;
import com.smartlibrary.dsa.NormalizedText;
import com.smartlibrary.dsa.OptimalBST;
import com.smartlibrary.dsa.SequenceAlignment;
import com.smartlibrary.dsa.SpellingCorrector;

/**
 * Tests for DSA Module 3 (advanced dynamic programming): edit distances and spelling correction,
 * Needleman-Wunsch / Smith-Waterman alignment, and the Optimal BST. Run as a Java Application.
 */
public final class DynamicProgrammingTests {

    @FunctionalInterface
    private interface TestBody {
        void run() throws Exception;
    }

    private static int passed;
    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        test("Levenshtein: known values", () -> {
            check(EditDistance.levenshtein("kitten", "sitting") == 3, "kitten/sitting");
            check(EditDistance.levenshtein("", "abc") == 3, "empty");
            check(EditDistance.levenshtein("graph", "graph") == 0, "same");
            check(EditDistance.levenshtein("garph", "graph") == 2, "swap costs 2");
        });

        test("Damerau-Levenshtein counts a swap of neighbouring letters as 1", () -> {
            check(EditDistance.damerauLevenshtein("garph", "graph") == 1, "garph");
            check(EditDistance.damerauLevenshtein("binray", "binary") == 1, "binray");
            check(EditDistance.damerauLevenshtein("kitten", "sitting") == 3, "kitten/sitting");
        });

        test("Edit distances equal the full DP table on 20,000 random pairs", () -> {
            Random r = new Random(21);
            for (int it = 0; it < 20_000; it++) {
                String a = random(r, r.nextInt(9));
                String b = random(r, r.nextInt(9));
                check(EditDistance.levenshtein(a, b) == fullTable(a, b, false), "lev " + a + "/" + b);
                check(EditDistance.damerauLevenshtein(a, b) == fullTable(a, b, true), "dam " + a + "/" + b);
                check(EditDistance.levenshtein(a, b) == EditDistance.levenshtein(b, a), "symmetric");
            }
        });

        test("Did you mean? corrects words from the vocabulary", () -> {
            Map<String, Integer> vocab = SpellingCorrector.vocabularyOf(
                    "binary search tree graph algorithm algorithms process deadlock queue", new HashMap<>());
            SpellingCorrector sc = new SpellingCorrector(vocab);
            check(sc.suggest("algoritm", 3).get(0).getText().equals("algorithm"), "algoritm");
            check(sc.suggest("garph", 3).get(0).getText().equals("graph"), "garph");
            check(sc.suggest("binray serach", 3).get(0).getText().equals("binary search"), "two words");
            check(sc.suggest("procces, deadlok", 3).get(0).getText().equals("process, deadlock"), "two keywords");
            check(sc.suggest("graph", 3).isEmpty(), "known word: nothing to correct");
            check(sc.suggest("zzzzzzzz", 3).isEmpty(), "nothing close");
        });

        test("Smith-Waterman finds a shared passage with a changed word", () -> {
            List<SequenceAlignment.Word> a = words("we know that breadth first search uses a queue for levels");
            List<SequenceAlignment.Word> b = words("totally different start breadth first search uses a stack for levels end");
            SequenceAlignment.Result r = SequenceAlignment.smithWaterman(a, b);
            // "breadth first search uses a [queue|stack] for levels": 7 same words, 1 different
            check(r.matches == 7 && r.mismatches == 1 && r.gaps == 0,
                    "matches " + r.matches + " mismatches " + r.mismatches + " gaps " + r.gaps);
            check(r.score == 7 * SequenceAlignment.MATCH + SequenceAlignment.MISMATCH, "score " + r.score);
            check(a.get(r.columns.get(0).first).text.equals("breadth"), "starts at 'breadth'");
        });

        test("Needleman-Wunsch: identical texts align perfectly, one deletion costs one gap", () -> {
            List<SequenceAlignment.Word> a = words("a tree has nodes and edges");
            SequenceAlignment.Result same = SequenceAlignment.needlemanWunsch(a, a);
            check(same.identityPercent() == 100.0 && same.score == 6 * SequenceAlignment.MATCH, "identical");
            SequenceAlignment.Result del = SequenceAlignment.needlemanWunsch(a, words("a tree has nodes edges"));
            check(del.gaps == 1 && del.matches == 5 && del.score == 5 * SequenceAlignment.MATCH + SequenceAlignment.GAP,
                    "gaps " + del.gaps + " score " + del.score);
        });

        test("Alignment scores equal a brute-force full table on random word lists", () -> {
            Random r = new Random(22);
            String[] vocab = { "tree", "graph", "heap", "queue" };
            for (int it = 0; it < 2_000; it++) {
                List<SequenceAlignment.Word> a = words(randomWords(r, vocab, r.nextInt(8)));
                List<SequenceAlignment.Word> b = words(randomWords(r, vocab, r.nextInt(8)));
                check(SequenceAlignment.needlemanWunsch(a, b).score == fullAlign(a, b, false), "NW");
                check(SequenceAlignment.smithWaterman(a, b).score == fullAlign(a, b, true), "SW");
            }
        });

        test("Optimal BST equals brute force over every tree shape", () -> {
            Random r = new Random(23);
            for (int it = 0; it < 2_000; it++) {
                int n = 1 + r.nextInt(7);
                Map<String, Integer> m = new TreeMap<>();
                for (int i = 0; i < n; i++) {
                    m.put("k" + (char) ('a' + i), 1 + r.nextInt(20));
                }
                int[] f = m.values().stream().mapToInt(Integer::intValue).toArray();
                long total = 0;
                for (int v : f) {
                    total += v;
                }
                OptimalBST t = new OptimalBST(m);
                check(Math.abs(t.expectedComparisons() * total - bruteForce(f, 0, n, 1)) < 1e-6, "cost");
                check(t.expectedComparisons() <= t.balancedExpectedComparisons() + 1e-9, "never worse than balanced");
            }
        });

        test("Optimal BST puts the most searched key near the root", () -> {
            Map<String, Integer> m = new LinkedHashMap<>();
            m.put("algebra", 1);
            m.put("graph", 2);
            m.put("heap", 1);
            m.put("process", 1);
            m.put("tree", 50);
            OptimalBST t = new OptimalBST(m);
            check(t.root().key.equals("tree"), "root = " + t.root().key);
            check(t.comparisonsInOptimal("tree") == 1, "1 comparison");
            check(t.comparisonsInBalanced("tree") > 1, "balanced needs more");
            check(t.comparisonsInOptimal("zebra") < 0, "missing key");
        });

        test("Search log counts keywords and survives a reload", () -> {
            Path dir = Files.createTempDirectory("sl-log-");
            try {
                SearchLogCSV log = new SearchLogCSV(dir);
                check(log.load().isEmpty(), "empty at start");
                log.record(List.of("tree", "Graph"));
                log.record(List.of("tree"));
                Map<String, Integer> m = new SearchLogCSV(dir).load();
                check(m.get("tree") == 2 && m.get("graph") == 1, "counts " + m);
            } finally {
                try (Stream<Path> w = Files.walk(dir)) {
                    w.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
                }
            }
        });

        test("Sample books: Smith-Waterman detects a reworded copy", () -> {
            Path p = Path.of("data", "books", "1_introduction_to_data_structures.txt");
            if (!Files.exists(p)) {
                System.out.println("      (skipped: run from the project folder to include this test)");
                return;
            }
            String original = Files.readString(p);
            String reworded = original.replace("uses a queue", "relies on a queue")
                    .replace("Stacks are used for", "Stacks help with");
            SequenceAlignment.Result r = SequenceAlignment.smithWaterman(
                    words(reworded), SequenceAlignment.words(new NormalizedText(original)));
            check(r.matches > 400 && r.identityPercent() > 95, "matches " + r.matches);
        });

        System.out.println();
        System.out.println("Passed: " + passed + ", Failed: " + failures.size());
        for (String f : failures) {
            System.out.println("  FAILED " + f);
        }
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    // ------------------------------------------------------------------ reference implementations

    /** Full (n+1) x (m+1) DP table, written as plainly as possible. */
    private static int fullTable(String a, String b, boolean damerau) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (damerau && i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2)
                        && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[a.length()][b.length()];
    }

    private static int fullAlign(List<SequenceAlignment.Word> a, List<SequenceAlignment.Word> b, boolean local) {
        int n = a.size();
        int m = b.size();
        int[][] s = new int[n + 1][m + 1];
        int best = 0;
        for (int i = 1; i <= n; i++) {
            s[i][0] = local ? 0 : i * SequenceAlignment.GAP;
        }
        for (int j = 1; j <= m; j++) {
            s[0][j] = local ? 0 : j * SequenceAlignment.GAP;
        }
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int v = Math.max(s[i - 1][j - 1] + (a.get(i - 1).text.equals(b.get(j - 1).text)
                        ? SequenceAlignment.MATCH : SequenceAlignment.MISMATCH),
                        Math.max(s[i - 1][j], s[i][j - 1]) + SequenceAlignment.GAP);
                s[i][j] = local ? Math.max(0, v) : v;
                best = Math.max(best, s[i][j]);
            }
        }
        return local ? best : s[n][m];
    }

    /** Smallest sum of frequency x depth over every BST shape of keys i..j-1. */
    private static long bruteForce(int[] f, int i, int j, int depth) {
        if (i >= j) {
            return 0;
        }
        long best = Long.MAX_VALUE;
        for (int r = i; r < j; r++) {
            best = Math.min(best, (long) f[r] * depth + bruteForce(f, i, r, depth + 1)
                    + bruteForce(f, r + 1, j, depth + 1));
        }
        return best;
    }

    // ------------------------------------------------------------------ helpers

    private static List<SequenceAlignment.Word> words(String s) {
        return SequenceAlignment.words(new NormalizedText(s));
    }

    private static String random(Random r, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append("abc".charAt(r.nextInt(3)));
        }
        return sb.toString();
    }

    private static String randomWords(Random r, String[] vocab, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(vocab[r.nextInt(vocab.length)]).append(' ');
        }
        return sb.toString();
    }

    private static void test(String name, TestBody body) {
        try {
            body.run();
            passed++;
            System.out.println("PASS  " + name);
        } catch (Throwable t) {
            failures.add(name + " -> " + t);
            System.out.println("FAIL  " + name + " -> " + t);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
