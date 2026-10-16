package com.smartlibrary;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import com.smartlibrary.dsa.ComparisonSession;
import com.smartlibrary.dsa.LcpKasai;
import com.smartlibrary.dsa.NormalizedText;
import com.smartlibrary.dsa.SimilarityMethod;
import com.smartlibrary.dsa.SimilarityResult;
import com.smartlibrary.dsa.SuffixArraySAIS;
import com.smartlibrary.dsa.SuffixTree;

/**
 * Tests for DSA Module 2 (document similarity): SA-IS, Kasai LCP, Ukkonen suffix tree,
 * and the comparison of two documents. Run as a Java Application.
 */
public final class DocumentSimilarityTests {

    @FunctionalInterface
    private interface TestBody {
        void run() throws Exception;
    }

    private static int passed;
    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        test("SA-IS equals naive suffix sorting on 20,000 random texts", () -> {
            Random r = new Random(11);
            for (int it = 0; it < 20_000; it++) {
                int[] s = randomText(r, 1 + r.nextInt(60), it % 3 == 0 ? 2 : 2 + r.nextInt(5));
                int k = max(s) + 1;
                check(Arrays.equals(SuffixArraySAIS.build(s, k), SuffixArraySAIS.buildNaive(s)),
                        "text " + Arrays.toString(s));
            }
        });

        test("SA-IS on a highly repetitive text (aaaa...a)", () -> {
            int[] s = new int[5001];
            Arrays.fill(s, 0, 5000, 1);
            int[] sa = SuffixArraySAIS.build(s, 2);
            for (int i = 0; i < s.length; i++) {
                check(sa[i] == s.length - 1 - i, "rank " + i);
            }
        });

        test("Kasai LCP equals direct comparison", () -> {
            Random r = new Random(12);
            for (int it = 0; it < 5_000; it++) {
                int[] s = randomText(r, 1 + r.nextInt(60), 2 + r.nextInt(4));
                int[] sa = SuffixArraySAIS.build(s, max(s) + 1);
                int[] lcp = LcpKasai.build(s, sa);
                for (int i = 1; i < s.length; i++) {
                    int h = 0;
                    while (sa[i] + h < s.length && sa[i - 1] + h < s.length && s[sa[i] + h] == s[sa[i - 1] + h]) {
                        h++;
                    }
                    check(lcp[i] == h, "lcp mismatch");
                }
            }
        });

        test("Suffix tree: substring retrieval counts every occurrence", () -> {
            Random r = new Random(13);
            for (int it = 0; it < 5_000; it++) {
                int[] s = randomText(r, 1 + r.nextInt(60), 2 + r.nextInt(4));
                SuffixTree t = new SuffixTree(s);
                int[] p = new int[1 + r.nextInt(4)];
                for (int i = 0; i < p.length; i++) {
                    p[i] = 1 + r.nextInt(max(s) == 0 ? 1 : max(s));
                }
                int count = 0;
                int first = -1;
                for (int st = 0; st + p.length < s.length; st++) {
                    if (Arrays.equals(Arrays.copyOfRange(s, st, st + p.length), p)) {
                        count++;
                        if (first < 0) {
                            first = st;
                        }
                    }
                }
                SuffixTree.Occurrences o = t.find(p);
                check(o.count == count && o.firstPosition == first,
                        "find " + Arrays.toString(p) + " in " + Arrays.toString(s));
            }
        });

        test("Suffix tree: matching statistics equal brute force", () -> {
            Random r = new Random(14);
            for (int it = 0; it < 5_000; it++) {
                int k = 2 + r.nextInt(4);
                int[] s = randomText(r, 1 + r.nextInt(50), k);
                int[] other = new int[1 + r.nextInt(40)];
                for (int i = 0; i < other.length; i++) {
                    other[i] = 1 + r.nextInt(k - 1);
                }
                int[] ms = new SuffixTree(s).matchingStatistics(other);
                for (int i = 0; i < other.length; i++) {
                    int best = 0;
                    for (int st = 0; st < s.length - 1; st++) {
                        int h = 0;
                        while (i + h < other.length && st + h < s.length - 1 && other[i + h] == s[st + h]) {
                            h++;
                        }
                        best = Math.max(best, h);
                    }
                    check(ms[i] == best, "ms[" + i + "]");
                }
            }
        });

        test("Worked example: 'tree has' vs 'the tree' share 'tree'", () -> {
            ComparisonSession s = new ComparisonSession(new NormalizedText("tree has"), new NormalizedText("the tree"));
            for (SimilarityMethod m : SimilarityMethod.values()) {
                SimilarityResult r = s.compare(m, 4);
                check(r.getPhrases().isEmpty(), m + ": a single word is not a phrase");
            }
            ComparisonSession s2 = new ComparisonSession(new NormalizedText("a binary tree has two children"),
                    new NormalizedText("every binary tree has a root"));
            SimilarityResult r = s2.compare(SimilarityMethod.SUFFIX_ARRAY, 6);
            check(r.getLongest() != null && r.getLongest().getText().equals("binary tree has"),
                    "longest = " + (r.getLongest() == null ? null : r.getLongest().getText()));
        });

        test("Identical documents are 100% similar; unrelated ones 0%", () -> {
            String t = "Breadth first search uses a queue. Depth first search uses a stack.\n"
                    + "A heap is a complete binary tree stored inside an array.";
            ComparisonSession same = new ComparisonSession(new NormalizedText(t), new NormalizedText(t));
            ComparisonSession diff = new ComparisonSession(new NormalizedText(t),
                    new NormalizedText("Forests store carbon and protect the soil from erosion."));
            for (SimilarityMethod m : SimilarityMethod.values()) {
                check(Math.abs(same.compare(m, 10).getSimilarityPercent() - 100.0) < 1e-9, m + " identical");
                check(diff.compare(m, 10).getSimilarityPercent() == 0.0, m + " unrelated");
            }
        });

        test("Phrases do not cross sentence ends", () -> {
            ComparisonSession s = new ComparisonSession(
                    new NormalizedText("edges are weighted or unweighted. graphs are stored as lists"),
                    new NormalizedText("bfs works on an unweighted graphs are stored somewhere"));
            for (SimilarityMethod m : SimilarityMethod.values()) {
                for (SimilarityResult.CommonPhrase p : s.compare(m, 6).getPhrases()) {
                    check(!p.getText().contains("unweighted graphs"), m + " crossed a sentence: " + p.getText());
                }
            }
        });

        test("Both methods agree on random documents", () -> {
            Random r = new Random(15);
            String[] words = { "tree", "graph", "heap", "queue", "stack", "binary", "search", "sort", "the", "a" };
            for (int it = 0; it < 300; it++) {
                String a = randomSentence(r, words, 5 + r.nextInt(60));
                String b = randomSentence(r, words, 5 + r.nextInt(60));
                ComparisonSession s = new ComparisonSession(new NormalizedText(a), new NormalizedText(b));
                SimilarityResult x = s.compare(SimilarityMethod.SUFFIX_ARRAY, 8);
                SimilarityResult y = s.compare(SimilarityMethod.SUFFIX_TREE, 8);
                check(x.getCoveredCharacters() == y.getCoveredCharacters(), "covered differs");
                check(x.getPhrases().size() == y.getPhrases().size(), "phrase count differs");
                for (int i = 0; i < x.getPhrases().size(); i++) {
                    SimilarityResult.CommonPhrase p = x.getPhrases().get(i);
                    SimilarityResult.CommonPhrase q = y.getPhrases().get(i);
                    check(p.getText().equals(q.getText()) && p.getPositionInFirst() == q.getPositionInFirst()
                            && p.getPositionInSecond() == q.getPositionInSecond(), "phrase differs");
                    String na = new NormalizedText(a).text();
                    String nb = new NormalizedText(b).text();
                    check(na.startsWith(p.getText(), p.getPositionInFirst()), "position in first");
                    check(nb.startsWith(p.getText(), p.getPositionInSecond()), "position in second");
                }
            }
        });

        test("Phrase lookup: suffix array and suffix tree give the same counts", () -> {
            ComparisonSession s = new ComparisonSession(
                    new NormalizedText("Binary search is fast. A binary tree is not binary search."),
                    new NormalizedText("We use BINARY SEARCH on sorted arrays."));
            ComparisonSession.PhraseLookup a = s.findWithSuffixArray("binary search");
            ComparisonSession.PhraseLookup b = s.findWithSuffixTree("Binary   Search!");
            check(a.countInFirst == 2 && a.countInSecond == 1, "SA counts " + a.countInFirst + "/" + a.countInSecond);
            check(b.countInFirst == 2 && b.countInSecond == 1, "ST counts " + b.countInFirst + "/" + b.countInSecond);
            check(s.findWithSuffixTree("zebra").countInFirst == 0, "missing phrase");
        });

        test("Chapter headings are ignored and line numbers map back", () -> {
            String t = "Chapter 1: Trees\nA tree has nodes.\nChapter 2: Graphs\nA graph has edges.";
            NormalizedText n = new NormalizedText(t);
            check(!n.text().contains("chapter"), "heading removed: " + n.text());
            int pos = n.text().indexOf("graph");
            check(t.startsWith("graph", n.originalIndex(pos)), "maps back to the original text");
        });

        test("Sample books: Data Structures vs Algorithms share 'breadth first search'", () -> {
            Path books = Path.of("data", "books");
            Path a = books.resolve("1_introduction_to_data_structures.txt");
            Path b = books.resolve("2_algorithms_explained.txt");
            if (!Files.exists(a) || !Files.exists(b)) {
                System.out.println("      (skipped: run from the project folder to include this test)");
                return;
            }
            ComparisonSession s = new ComparisonSession(new NormalizedText(Files.readString(a)),
                    new NormalizedText(Files.readString(b)));
            SimilarityResult r = s.compare(SimilarityMethod.SUFFIX_TREE, 10);
            check(r.getLongest().getText().equals("breadth first search"), "longest = " + r.getLongest().getText());
        });

        System.out.println();
        System.out.println("Passed: " + passed + ", Failed: " + failures.size());
        for (String f : failures) {
            System.out.println("  FAILED " + f);
        }
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    // ------------------------------------------------------------------ helpers

    /** Random text over 1..k-1, ending with the unique 0 terminal. */
    private static int[] randomText(Random r, int n, int k) {
        int[] s = new int[n + 1];
        for (int i = 0; i < n; i++) {
            s[i] = 1 + r.nextInt(Math.max(1, k - 1));
        }
        s[n] = 0;
        return s;
    }

    private static String randomSentence(Random r, String[] words, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(words[r.nextInt(words.length)]);
            sb.append(r.nextInt(8) == 0 ? ". " : " ");
        }
        return sb.toString();
    }

    private static int max(int[] s) {
        int m = 0;
        for (int v : s) {
            m = Math.max(m, v);
        }
        return m;
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
