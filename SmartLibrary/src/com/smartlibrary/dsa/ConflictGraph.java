package com.smartlibrary.dsa;

import java.util.ArrayList;
import java.util.List;

/**
 * Undirected graph used for the NP-completeness module (DSA Module 5).
 * Vertices are books; an edge joins two books whose text overlaps (a "conflict").
 * <ul>
 *   <li><b>Vertex Cover</b>: smallest set of books touching every conflict (NP-hard; exact by
 *       brute force over all 2^n subsets, plus the classic 2-approximation).</li>
 *   <li><b>Independent Set</b>: books with no conflict among them. S is independent exactly when
 *       V \ S is a vertex cover, so maximum IS = n - minimum VC.</li>
 *   <li><b>Clique</b>: books that all conflict with each other. S is a clique in G exactly when S is
 *       an independent set in the complement graph.</li>
 * </ul>
 * These two facts are the reductions CLIQUE -> INDEPENDENT-SET -> VERTEX-COVER.
 */
public final class ConflictGraph {

    /** Largest graph solved exactly (2^n subsets). */
    public static final int EXACT_LIMIT = 20;

    private final int n;
    private final long[] adj; // adjacency as bit masks

    public ConflictGraph(int n) {
        if (n > 62) {
            throw new IllegalArgumentException("At most 62 vertices");
        }
        this.n = n;
        this.adj = new long[n];
    }

    public int size() {
        return n;
    }

    public void addEdge(int u, int v) {
        if (u != v) {
            adj[u] |= 1L << v;
            adj[v] |= 1L << u;
        }
    }

    public boolean hasEdge(int u, int v) {
        return (adj[u] >> v & 1) == 1;
    }

    /** Edges as {u, v} with u < v, in order. */
    public List<int[]> edges() {
        List<int[]> out = new ArrayList<>();
        for (int u = 0; u < n; u++) {
            for (int v = u + 1; v < n; v++) {
                if (hasEdge(u, v)) {
                    out.add(new int[] { u, v });
                }
            }
        }
        return out;
    }

    /** Complement graph: edge exactly where this graph has none. */
    public ConflictGraph complement() {
        ConflictGraph c = new ConflictGraph(n);
        for (int u = 0; u < n; u++) {
            for (int v = u + 1; v < n; v++) {
                if (!hasEdge(u, v)) {
                    c.addEdge(u, v);
                }
            }
        }
        return c;
    }

    // ================================================================ exact (exponential)

    /** Result of an exact search, with the number of subsets checked. */
    public static final class Exact {
        public final long mask;
        public final long subsetsChecked;
        public final long nanos;

        Exact(long mask, long subsetsChecked, long nanos) {
            this.mask = mask;
            this.subsetsChecked = subsetsChecked;
            this.nanos = nanos;
        }

        public List<Integer> vertices() {
            return toList(mask);
        }

        public double millis() {
            return nanos / 1e6;
        }
    }

    /** Minimum vertex cover by checking all 2^n subsets. O(2^n * n). */
    public Exact exactMinVertexCover() {
        if (n > EXACT_LIMIT) {
            throw new IllegalStateException("Too many vertices for the exact search (limit " + EXACT_LIMIT + ")");
        }
        long start = System.nanoTime();
        long best = (1L << n) - 1;
        long checked = 0;
        for (long mask = 0; mask < (1L << n); mask++) {
            checked++;
            if (Long.bitCount(mask) < Long.bitCount(best) && isVertexCover(mask)) {
                best = mask;
            }
        }
        return new Exact(best, checked, System.nanoTime() - start);
    }

    /** Every edge has at least one end in the set: each vertex outside it has all neighbours inside. */
    public boolean isVertexCover(long mask) {
        for (int u = 0; u < n; u++) {
            if ((mask >> u & 1) == 0 && (adj[u] & ~mask) != 0) {
                return false;
            }
        }
        return true;
    }

    public boolean isIndependentSet(long mask) {
        for (int u = 0; u < n; u++) {
            if ((mask >> u & 1) == 1 && (adj[u] & mask) != 0) {
                return false;
            }
        }
        return true;
    }

    public boolean isClique(long mask) {
        for (int u = 0; u < n; u++) {
            if ((mask >> u & 1) == 1 && (adj[u] | 1L << u) != ((adj[u] | 1L << u) | mask)) {
                return false;
            }
        }
        return true;
    }

    // ================================================================ approximation (polynomial)

    /**
     * Vertex Cover 2-approximation: take any uncovered edge, add BOTH its ends, repeat.
     * The chosen edges form a matching, and any cover needs one end of each, so the result is at most
     * twice the optimum. O(V + E).
     */
    public long vertexCover2Approx() {
        long cover = 0;
        for (int[] e : edges()) {
            if ((cover >> e[0] & 1) == 0 && (cover >> e[1] & 1) == 0) {
                cover |= 1L << e[0];
                cover |= 1L << e[1];
            }
        }
        return cover;
    }

    /** All vertices not in the mask. */
    public long complementOf(long mask) {
        return ~mask & ((1L << n) - 1);
    }

    public static List<Integer> toList(long mask) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < 63; i++) {
            if ((mask >> i & 1) == 1) {
                out.add(i);
            }
        }
        return out;
    }
}
