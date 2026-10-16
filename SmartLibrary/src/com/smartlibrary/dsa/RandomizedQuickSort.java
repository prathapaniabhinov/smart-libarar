package com.smartlibrary.dsa;

import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Randomized QuickSort (DSA Module 6). A random pivot makes the expected number of comparisons
 * about 2 n ln n for EVERY input, so already-sorted lists are no longer a worst case.
 * Uses three-way partitioning, so many equal keys are handled in one pass.
 * <p>
 * Expected time O(n log n); worst case O(n^2) only with very unlucky random choices.
 */
public final class RandomizedQuickSort {

    /** Statistics of one sort. */
    public static final class Stats {
        public long comparisons;
        public int maxDepth;
        public long nanos;

        /** 2 n ln n: expected comparisons of randomized quicksort. */
        public static double expected(int n) {
            return n < 2 ? 0 : 2.0 * n * Math.log(n);
        }

        public double millis() {
            return nanos / 1e6;
        }
    }

    private RandomizedQuickSort() {
    }

    /** Sorts the list in place with random pivots. */
    public static <T> Stats sort(List<T> list, Comparator<? super T> cmp, Random random) {
        Stats st = new Stats();
        long start = System.nanoTime();
        sort(list, cmp, random, 0, list.size() - 1, 1, st);
        st.nanos = System.nanoTime() - start;
        return st;
    }

    /**
     * QuickSort that always uses the FIRST element as pivot, for comparison.
     * On an already-sorted list it makes about n^2 / 2 comparisons.
     */
    public static <T> Stats sortFirstPivot(List<T> list, Comparator<? super T> cmp) {
        Stats st = new Stats();
        long start = System.nanoTime();
        int lo = 0;
        int hi = list.size() - 1;
        int depth = 1;
        // classic textbook QuickSort (Lomuto partition, first element as pivot); loops on the larger part
        java.util.ArrayDeque<int[]> pending = new java.util.ArrayDeque<>();
        pending.push(new int[] { lo, hi, depth });
        while (!pending.isEmpty()) {
            int[] range = pending.pop();
            lo = range[0];
            hi = range[1];
            depth = range[2];
            while (lo < hi) {
                st.maxDepth = Math.max(st.maxDepth, depth);
                T pivot = list.get(lo);
                int i = lo;
                for (int j = lo + 1; j <= hi; j++) {
                    st.comparisons++;
                    if (cmp.compare(list.get(j), pivot) < 0) {
                        swap(list, ++i, j);
                    }
                }
                swap(list, lo, i);
                if (i - lo < hi - i) {
                    pending.push(new int[] { lo, i - 1, depth + 1 });
                    lo = i + 1;
                } else {
                    pending.push(new int[] { i + 1, hi, depth + 1 });
                    hi = i - 1;
                }
                depth++;
            }
        }
        st.nanos = System.nanoTime() - start;
        return st;
    }

    private static <T> void sort(List<T> a, Comparator<? super T> cmp, Random random, int lo, int hi,
                                 int depth, Stats st) {
        while (lo < hi) {
            st.maxDepth = Math.max(st.maxDepth, depth);
            int p = lo + random.nextInt(hi - lo + 1);
            T pivot = a.get(p);
            // three-way partition: [lo, lt) < pivot, [lt, i) == pivot, (gt, hi] > pivot
            int lt = lo;
            int gt = hi;
            int i = lo;
            while (i <= gt) {
                st.comparisons++;
                int c = cmp.compare(a.get(i), pivot);
                if (c < 0) {
                    swap(a, lt++, i++);
                } else if (c > 0) {
                    swap(a, i, gt--);
                } else {
                    i++;
                }
            }
            // recurse into the smaller part, loop on the larger one (keeps the stack small)
            if (lt - lo < hi - gt) {
                sort(a, cmp, random, lo, lt - 1, depth + 1, st);
                lo = gt + 1;
            } else {
                sort(a, cmp, random, gt + 1, hi, depth + 1, st);
                hi = lt - 1;
            }
            depth++;
        }
    }

    private static <T> void swap(List<T> a, int i, int j) {
        if (i != j) {
            T t = a.get(i);
            a.set(i, a.get(j));
            a.set(j, t);
        }
    }
}
