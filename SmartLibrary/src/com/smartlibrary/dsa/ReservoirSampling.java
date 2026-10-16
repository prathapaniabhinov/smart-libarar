package com.smartlibrary.dsa;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Reservoir Sampling, Algorithm R (DSA Module 6).
 * Picks k items uniformly at random from a stream of unknown length in ONE pass with O(k) memory:
 * keep the first k items; for item number i (i > k), replace a random reservoir slot with
 * probability k / i. Every item ends up in the sample with probability k / N.
 */
public final class ReservoirSampling {

    /** The sample and how many stream items were seen. */
    public static final class Sample<T> {
        public final List<T> items;
        public final long seen;
        public final long replacements;

        Sample(List<T> items, long seen, long replacements) {
            this.items = Collections.unmodifiableList(items);
            this.seen = seen;
            this.replacements = replacements;
        }
    }

    private ReservoirSampling() {
    }

    public static <T> Sample<T> sample(Iterator<T> stream, int k, Random random) {
        if (k <= 0) {
            throw new IllegalArgumentException("Sample size must be positive");
        }
        List<T> reservoir = new ArrayList<>(k);
        long seen = 0;
        long replacements = 0;
        while (stream.hasNext()) {
            T item = stream.next();
            seen++;
            if (reservoir.size() < k) {
                reservoir.add(item);
            } else {
                long j = (long) (random.nextDouble() * seen); // 0 .. seen-1
                if (j < k) {
                    reservoir.set((int) j, item);
                    replacements++;
                }
            }
        }
        return new Sample<>(new ArrayList<>(reservoir), seen, replacements);
    }
}
