package com.smartlibrary.dsa;

import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.stream.IntStream;

/**
 * Parallel algorithms for library statistics (DSA Module 6).
 * <ul>
 *   <li><b>Parallel Reduce</b>: split the work in halves on a fork/join pool, combine the partial
 *       results. Work O(n), span O(log n).</li>
 *   <li><b>Blelloch Scan</b>: exclusive prefix sums with an up-sweep and a down-sweep over a
 *       balanced tree. Work O(n), span O(log n). Each tree level runs in parallel.</li>
 *   <li><b>Brent's theorem</b>: with p processors, T_p &lt;= W / p + S
 *       (W = total work, S = span / critical path).</li>
 * </ul>
 */
public final class ParallelStats {

    /** Chunks smaller than this are handled sequentially by one task. */
    public static final int CUTOFF = 2048;

    private ParallelStats() {
    }

    public static int processors() {
        return Runtime.getRuntime().availableProcessors();
    }

    // ================================================================ parallel reduce

    /** Result of a parallel word count. */
    public static final class Reduction {
        public final long words;
        public final long work;
        public final int span;
        public final int tasks;
        public final long nanos;

        Reduction(long words, long work, int span, int tasks, long nanos) {
            this.words = words;
            this.work = work;
            this.span = span;
            this.tasks = tasks;
            this.nanos = nanos;
        }

        public double millis() {
            return nanos / 1e6;
        }
    }

    /**
     * Counts the words of a text with a parallel reduce. A word starts at position i when text[i] is a
     * letter or digit and text[i-1] is not, so every chunk can be counted independently and the
     * counts are simply added.
     */
    public static Reduction countWords(String text) {
        char[] c = text.toCharArray();
        long start = System.nanoTime();
        WordTask root = new WordTask(c, 0, c.length);
        long words = ForkJoinPool.commonPool().invoke(root);
        long nanos = System.nanoTime() - start;
        int levels = 0;
        for (long chunks = (c.length + CUTOFF - 1) / CUTOFF; chunks > 1; chunks = (chunks + 1) / 2) {
            levels++;
        }
        return new Reduction(words, c.length, levels + Math.min(c.length, CUTOFF), root.tasks(), nanos);
    }

    /** Sequential word count with the same rule, for checking. */
    public static long countWordsSequential(String text) {
        long count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (isWordStart(text, i)) {
                count++;
            }
        }
        return count;
    }

    private static boolean isWordStart(String s, int i) {
        return Character.isLetterOrDigit(s.charAt(i)) && (i == 0 || !Character.isLetterOrDigit(s.charAt(i - 1)));
    }

    private static final class WordTask extends RecursiveTask<Long> {
        private static final long serialVersionUID = 1L;
        private final char[] c;
        private final int from;
        private final int to;
        private int taskCount = 1;

        WordTask(char[] c, int from, int to) {
            this.c = c;
            this.from = from;
            this.to = to;
        }

        @Override
        protected Long compute() {
            if (to - from <= CUTOFF) {
                long count = 0;
                for (int i = from; i < to; i++) {
                    if (Character.isLetterOrDigit(c[i]) && (i == 0 || !Character.isLetterOrDigit(c[i - 1]))) {
                        count++;
                    }
                }
                return count;
            }
            int mid = (from + to) >>> 1;
            WordTask left = new WordTask(c, from, mid);
            WordTask right = new WordTask(c, mid, to);
            left.fork();                       // left half runs in parallel
            long r = right.compute();          // this thread does the right half
            long l = left.join();
            taskCount = 1 + left.taskCount + right.taskCount;
            return l + r;                      // combine (the "reduce" step)
        }

        int tasks() {
            return taskCount;
        }
    }

    // ================================================================ Blelloch scan

    /** Result of an exclusive scan. */
    public static final class Scan {
        public final long[] exclusive;
        public final long total;
        public final long work;
        public final int span;
        public final long nanos;

        Scan(long[] exclusive, long total, long work, int span, long nanos) {
            this.exclusive = exclusive;
            this.total = total;
            this.work = work;
            this.span = span;
            this.nanos = nanos;
        }

        /** Inclusive prefix sum at i = exclusive[i] + value[i]. */
        public long inclusive(long[] values, int i) {
            return exclusive[i] + values[i];
        }

        public double millis() {
            return nanos / 1e6;
        }
    }

    /**
     * Blelloch exclusive scan: out[i] = values[0] + ... + values[i-1].
     * The array is padded to a power of two. Up-sweep builds partial sums up the tree;
     * the root is set to 0; down-sweep pushes prefix sums back down. Every level is a parallel loop.
     */
    public static Scan blellochScan(long[] values) {
        int n = values.length;
        long start = System.nanoTime();
        int size = 1;
        while (size < n) {
            size <<= 1;
        }
        long[] a = Arrays.copyOf(values, Math.max(size, 1));
        long work = 0;
        int levels = 0;

        // up-sweep (reduce)
        for (int d = 1; d < size; d <<= 1) {
            final int step = d * 2;
            final int stride = d;
            int count = size / step;
            loop(count, k -> {
                int i = k * step;
                a[i + step - 1] += a[i + stride - 1];
            });
            work += count;
            levels++;
        }
        long total = size == 0 ? 0 : a[size - 1];
        if (size > 0) {
            a[size - 1] = 0;
        }
        // down-sweep
        for (int d = size >> 1; d >= 1; d >>= 1) {
            final int step = d * 2;
            final int stride = d;
            int count = size / step;
            loop(count, k -> {
                int i = k * step;
                long t = a[i + stride - 1];
                a[i + stride - 1] = a[i + step - 1];
                a[i + step - 1] += t;
            });
            work += count;
            levels++;
        }
        long nanos = System.nanoTime() - start;
        return new Scan(Arrays.copyOf(a, n), total, work, levels, nanos);
    }

    /** Runs body(0..count-1): in parallel for big levels, sequentially for small ones. */
    private static void loop(int count, java.util.function.IntConsumer body) {
        if (count >= 1024) {
            IntStream.range(0, count).parallel().forEach(body);
        } else {
            for (int k = 0; k < count; k++) {
                body.accept(k);
            }
        }
    }

    // ================================================================ Brent's theorem

    /** Brent's bound on running time with p processors: W / p + S. */
    public static double brentBound(long work, long span, int p) {
        return (double) work / p + span;
    }
}
