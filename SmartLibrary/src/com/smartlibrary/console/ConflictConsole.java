package com.smartlibrary.console;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.smartlibrary.dsa.ConflictGraph;
import com.smartlibrary.service.AnalyticsService;
import com.smartlibrary.service.AnalyticsService.ConflictAnalysis;
import com.smartlibrary.service.ServiceException;

/**
 * Document Conflict Analysis (DSA Module 5 - NP-Completeness and Approximation).
 * Two books "conflict" when their text overlaps (Module 2 similarity at or above a threshold).
 * Shows the reductions CLIQUE -> INDEPENDENT-SET -> VERTEX-COVER on this graph, the exact minimum
 * vertex cover (exponential search) and the polynomial 2-approximation.
 */
public class ConflictConsole {

    private final ConsoleIO io;
    private final AnalyticsService analytics;

    public ConflictConsole(ConsoleIO io, AnalyticsService analytics) {
        this.io = io;
        this.analytics = analytics;
    }

    public void run() throws ServiceException {
        io.header("Document Conflict Analysis (NP-Completeness)");
        io.info("Two books conflict when their text overlaps (Compare Documents similarity >= threshold).");
        double threshold = readThreshold();
        io.info("Comparing every pair of books (suffix tree, Module 2)...");
        ConflictAnalysis a = analytics.conflictGraph(threshold);
        ConflictGraph g = a.graph;
        int n = g.size();

        io.header("Conflict Graph (threshold " + fmt(threshold) + " %)");
        List<String[]> rows = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            rows.add(new String[] { "B" + (i + 1), a.books.get(i).getTitle(), a.books.get(i).getCategory() });
        }
        io.table(new String[] { "Vertex", "Book", "Category" }, rows);
        List<int[]> edges = g.edges();
        io.println("  Edges (conflicts): " + edges.size() + "   - built in " + ms(a.millis()) + " ms");
        for (int[] e : edges) {
            io.println("    B" + (e[0] + 1) + " -- B" + (e[1] + 1) + "   similarity " + fmt(a.similarity[e[0]][e[1]]) + " %");
        }
        if (edges.isEmpty()) {
            io.info("No conflicts at this threshold. Try a lower threshold, e.g. 0.5.");
            io.pause();
            return;
        }

        // ---- exact (exponential) and approximate (polynomial) vertex cover
        ConflictGraph.Exact exact = g.exactMinVertexCover();
        long approx = g.vertexCover2Approx();
        int opt = Long.bitCount(exact.mask);
        int apx = Long.bitCount(approx);

        io.header("Vertex Cover - fewest books to check so that every conflict is checked");
        io.println("  Exact minimum   : " + names(a, exact.mask) + "  (size " + opt + ")");
        io.println("                    checked all 2^" + n + " = " + exact.subsetsChecked + " subsets in "
                + ms(exact.millis()) + " ms   O(2^n x n) - VERTEX-COVER is NP-complete");
        io.println("  2-approximation : " + names(a, approx) + "  (size " + apx + ")");
        io.println("                    take any unchecked conflict, add BOTH books, repeat   O(V + E)");
        io.println("  Ratio           : " + apx + " / " + opt + " = " + fmt(opt == 0 ? 1 : (double) apx / opt)
                + "  (guaranteed <= 2)");

        // ---- reductions
        long is = g.complementOf(exact.mask);
        ConflictGraph comp = g.complement();
        io.header("Reductions: CLIQUE -> INDEPENDENT-SET -> VERTEX-COVER");
        io.println("  1. VERTEX-COVER <-> INDEPENDENT-SET: S is an independent set exactly when V - S is a vertex cover.");
        io.println("     Maximum independent set = all books - minimum cover = " + names(a, is)
                + "  (size " + Long.bitCount(is) + ")");
        io.println("     = the largest group of books with NO overlap between any two (a diverse reading list).");
        check(g.isIndependentSet(is), "independent set check");
        io.println("  2. CLIQUE <-> INDEPENDENT-SET: S is a clique in G exactly when S is independent in the");
        io.println("     complement graph (edges where G has none).");
        io.println("     Complement graph has " + comp.edges().size() + " edges. The same set "
                + names(a, is) + " is a clique there:");
        io.println("     every pair of these books does NOT overlap.");
        check(comp.isClique(is), "clique in complement check");
        long biggestClique = largestClique(g);
        io.println("  3. Largest clique in the conflict graph = " + names(a, biggestClique) + "  (size "
                + Long.bitCount(biggestClique) + ")");
        io.println("     = a group of books that ALL overlap with each other.");
        io.blank();
        io.success("All three sets were verified: vertex cover, independent set, clique.");
        io.pause();
    }

    /** Largest clique of G = maximum independent set of the complement = n - min VC of the complement. */
    private static long largestClique(ConflictGraph g) {
        ConflictGraph comp = g.complement();
        long coverOfComplement = comp.exactMinVertexCover().mask;
        return comp.complementOf(coverOfComplement);
    }

    private void check(boolean ok, String what) {
        if (!ok) {
            io.error("Verification failed: " + what);
        }
    }

    private double readThreshold() {
        while (true) {
            String s = io.readLine("Similarity threshold in % (Enter = 1.0): ");
            if (s.isEmpty()) {
                return 1.0;
            }
            try {
                double v = Double.parseDouble(s);
                if (v > 0 && v <= 100) {
                    return v;
                }
            } catch (NumberFormatException ignored) {
                // ask again
            }
            io.error("Please enter a number above 0 and at most 100.");
        }
    }

    private static String names(ConflictAnalysis a, long mask) {
        List<String> out = new ArrayList<>();
        for (int i : ConflictGraph.toList(mask)) {
            out.add("B" + (i + 1));
        }
        return out.isEmpty() ? "{ }" : "{" + String.join(", ", out) + "}";
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static String ms(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }
}
