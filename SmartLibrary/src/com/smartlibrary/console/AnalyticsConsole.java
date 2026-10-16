package com.smartlibrary.console;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.smartlibrary.data.UsageLogCSV;
import com.smartlibrary.dsa.ParallelStats;
import com.smartlibrary.dsa.RandomizedQuickSort;
import com.smartlibrary.service.AnalyticsService;
import com.smartlibrary.service.AnalyticsService.BookStat;
import com.smartlibrary.service.AnalyticsService.ParallelReport;
import com.smartlibrary.service.AnalyticsService.Ranking;
import com.smartlibrary.service.AnalyticsService.UsageSample;
import com.smartlibrary.service.ServiceException;

/**
 * Library Analytics (DSA Module 6 - Randomized and Parallel Algorithms). Librarian and Admin.
 */
public class AnalyticsConsole {

    private static final int DEMO_STREAM = 100_000;

    private final ConsoleIO io;
    private final AnalyticsService analytics;

    public AnalyticsConsole(ConsoleIO io, AnalyticsService analytics) {
        this.io = io;
        this.analytics = analytics;
    }

    public void run() throws ServiceException {
        List<String> options = List.of(
                "Rank books by size (Randomized QuickSort)",
                "Sample the library usage stream (Reservoir Sampling)",
                "Parallel document statistics (Parallel Reduce + Blelloch Scan + Brent's theorem)");
        while (true) {
            io.header("Library Analytics (Randomized and Parallel Algorithms)");
            io.menu(options, "Back to dashboard");
            int c = io.readChoice("\nChoose an option: ", 0, options.size());
            if (c == 0) {
                return;
            }
            try {
                switch (c) {
                    case 1: ranking(); break;
                    case 2: sampling(); break;
                    case 3: parallel(); break;
                    default: break;
                }
            } catch (ServiceException e) {
                io.error(e.getMessage());
                if (e.getKind() == ServiceException.Kind.ACCESS_DENIED) {
                    return;
                }
            }
            io.pause();
        }
    }

    // ------------------------------------------------------------------ Randomized QuickSort

    private void ranking() throws ServiceException {
        Ranking r = analytics.rankBooksByWords();
        io.header("Books Ranked by Size (Randomized QuickSort)");
        List<String[]> rows = new ArrayList<>();
        int rank = 1;
        for (BookStat s : r.ranked) {
            rows.add(new String[] { Integer.toString(rank++), s.book.getTitle(), s.book.getCategory(),
                    Long.toString(s.words), Long.toString(s.characters) });
        }
        io.table(new String[] { "Rank", "Book", "Category", "Words", "Characters" }, rows);
        io.println("  Pivot           : chosen at random each time, three-way partition");
        io.println("  Comparisons     : " + r.stats.comparisons + "   (expected about 2 n ln n = "
                + fmt(RandomizedQuickSort.Stats.expected(r.ranked.size())) + ")");
        io.println("  Recursion depth : " + r.stats.maxDepth);
        io.blank();
        io.println("  Why randomize? Sorting an already-sorted list of " + r.demoSize + " numbers:");
        List<String[]> demo = new ArrayList<>();
        demo.add(new String[] { "Randomized pivot", Long.toString(r.demoRandom.comparisons),
                Integer.toString(r.demoRandom.maxDepth), "O(n log n) expected" });
        demo.add(new String[] { "First element as pivot", Long.toString(r.demoFirstPivot.comparisons),
                Integer.toString(r.demoFirstPivot.maxDepth), "O(n^2) on sorted input" });
        io.table(new String[] { "QuickSort", "Comparisons", "Depth", "Complexity" }, demo);
        io.info("Search Books also ranks its results with Randomized QuickSort.");
    }

    // ------------------------------------------------------------------ Reservoir Sampling

    private void sampling() throws ServiceException {
        io.header("Usage Stream Sample (Reservoir Sampling)");
        boolean demo = false;
        if (analytics.usageLogEmpty()) {
            io.info("No usage recorded yet. Searches, opened books and comparisons are recorded in usage_log.csv.");
            if (!io.confirm("Sample a simulated DEMO stream of " + DEMO_STREAM + " events instead?")) {
                return;
            }
            demo = true;
        }
        int k = readInt("Sample size k (Enter = 5): ", 5, 1, 1000);
        UsageSample s = analytics.sampleUsage(k, demo, DEMO_STREAM);
        if (demo) {
            io.println("  *** DEMO DATA - simulated events, not real usage ***");
        }
        io.println("  Events in stream : " + s.sample.seen + " (read once, one at a time)");
        io.println("  Memory used      : " + k + " slots   O(k), whatever the stream length");
        io.println("  Replacements     : " + s.sample.replacements + " (item i replaces a slot with probability k / i)");
        io.println("  Each event is in the sample with probability k / N = " + k + " / " + s.sample.seen);
        io.blank();
        List<String[]> rows = new ArrayList<>();
        int i = 1;
        for (UsageLogCSV.Event e : s.sample.items) {
            rows.add(new String[] { Integer.toString(i++), e.time, e.username, e.action, e.detail });
        }
        io.table(new String[] { "#", "Time", "User", "Action", "Detail" }, rows);
        io.blank();
        io.println("  Actions in the whole stream (counted in the same pass):");
        for (Map.Entry<String, Long> e : s.actionCounts.entrySet()) {
            io.println(String.format(Locale.ROOT, "    %-10s %8d  (%.1f %%)", e.getKey(), e.getValue(),
                    100.0 * e.getValue() / s.sample.seen));
        }
    }

    // ------------------------------------------------------------------ Parallel Reduce + Blelloch + Brent

    private void parallel() throws ServiceException {
        ParallelReport r = analytics.parallelStatistics();
        io.header("Parallel Document Statistics");
        io.println("  Processors available: p = " + r.processors);
        io.blank();
        List<String[]> rows = new ArrayList<>();
        boolean allMatch = true;
        for (int i = 0; i < r.books.size(); i++) {
            ParallelStats.Reduction red = r.reductions.get(i);
            boolean ok = red.words == r.sequentialWords[i];
            allMatch &= ok;
            rows.add(new String[] { r.books.get(i).getTitle(), Long.toString(red.words),
                    Long.toString(r.scan.exclusive[i]), Long.toString(r.scan.inclusive(r.words, i)),
                    Integer.toString(red.tasks), ms(red.millis()), ok ? "yes" : "NO" });
        }
        io.table(new String[] { "Book", "Words", "Words before", "Cumulative", "Tasks", "Time (ms)", "= sequential" },
                rows);
        io.println("  Words           : Parallel Reduce - text split in halves on a fork/join pool down to");
        io.println("                    chunks of " + ParallelStats.CUTOFF + " characters, partial counts added.");
        io.println("  Words before /  : Blelloch Scan (exclusive / inclusive prefix sums) - up-sweep then");
        io.println("  Cumulative        down-sweep, each tree level in parallel. " + r.scan.span
                + " levels, " + r.scan.work + " additions.");
        if (allMatch) {
            io.success("Parallel counts equal the sequential counts; scan total = " + r.scan.total + " words.");
        } else {
            io.error("Parallel and sequential counts differ.");
        }

        long work = r.totalCharacters;
        long span = (long) Math.ceil(Math.log(Math.max(2, work)) / Math.log(2));
        io.blank();
        io.println("  Brent's theorem: T_p <= W / p + S");
        io.println("    W (work) = " + work + " characters checked in total");
        io.println("    S (span) = log2(W) = " + span + " combine steps on the critical path");
        java.util.TreeSet<Integer> ps = new java.util.TreeSet<>(List.of(1, 2, 4, 8, r.processors));
        for (int p : ps) {
            io.println(String.format(Locale.ROOT, "    p = %-3d -> T_p <= %,.0f steps%s", p,
                    ParallelStats.brentBound(work, span, p), p == r.processors ? "   (this computer)" : ""));
        }
        io.info("More processors divide the work, but the span (" + span + ") can never be beaten.");
    }

    // ------------------------------------------------------------------ helpers

    private int readInt(String prompt, int def, int min, int max) {
        while (true) {
            String s = io.readLine(prompt);
            if (s.isEmpty()) {
                return def;
            }
            try {
                int v = Integer.parseInt(s);
                if (v >= min && v <= max) {
                    return v;
                }
            } catch (NumberFormatException ignored) {
                // ask again
            }
            io.error("Please enter a number from " + min + " to " + max + ".");
        }
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static String ms(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }
}
