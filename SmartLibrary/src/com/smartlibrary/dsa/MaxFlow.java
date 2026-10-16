package com.smartlibrary.dsa;

import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * Maximum flow with the Edmonds-Karp algorithm (Ford-Fulkerson using BFS shortest augmenting paths).
 * Time O(V * E^2).
 * <p>
 * Each edge is stored together with its reverse edge; the residual capacity of the reverse edge
 * grows when flow is pushed, so flow can later be "undone" along another path.
 */
public final class MaxFlow {

    private final int n;
    private int[] head;
    private int[] next = new int[16];
    private int[] to = new int[16];
    private long[] cap = new long[16];
    private long[] original = new long[16];
    private int edges;
    private int augmentingPaths;

    public MaxFlow(int vertices) {
        this.n = vertices;
        this.head = new int[vertices];
        Arrays.fill(head, -1);
    }

    /**
     * Adds a directed edge u -> v with the given capacity (and its reverse edge with capacity 0).
     *
     * @return the id of the forward edge, for {@link #flowOn(int)}
     */
    public int addEdge(int u, int v, long capacity) {
        int e = add(u, v, capacity);
        add(v, u, 0);
        return e;
    }

    private int add(int u, int v, long c) {
        if (edges == to.length) {
            int size = edges * 2;
            next = Arrays.copyOf(next, size);
            to = Arrays.copyOf(to, size);
            cap = Arrays.copyOf(cap, size);
            original = Arrays.copyOf(original, size);
        }
        to[edges] = v;
        cap[edges] = c;
        original[edges] = c;
        next[edges] = head[u];
        head[u] = edges;
        return edges++;
    }

    /** Runs Edmonds-Karp and returns the maximum flow from s to t. */
    public long edmondsKarp(int s, int t) {
        long flow = 0;
        int[] parentEdge = new int[n];
        while (true) {
            // BFS in the residual graph: finds a shortest augmenting path
            Arrays.fill(parentEdge, -1);
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(s);
            boolean[] seen = new boolean[n];
            seen[s] = true;
            while (!queue.isEmpty() && !seen[t]) {
                int u = queue.poll();
                for (int e = head[u]; e != -1; e = next[e]) {
                    int v = to[e];
                    if (!seen[v] && cap[e] > 0) {
                        seen[v] = true;
                        parentEdge[v] = e;
                        queue.add(v);
                    }
                }
            }
            if (!seen[t]) {
                return flow; // no augmenting path left: flow is maximum
            }
            // bottleneck = smallest residual capacity on the path
            long bottleneck = Long.MAX_VALUE;
            for (int v = t; v != s; v = to[parentEdge[v] ^ 1]) {
                bottleneck = Math.min(bottleneck, cap[parentEdge[v]]);
            }
            for (int v = t; v != s; v = to[parentEdge[v] ^ 1]) {
                cap[parentEdge[v]] -= bottleneck;
                cap[parentEdge[v] ^ 1] += bottleneck;
            }
            flow += bottleneck;
            augmentingPaths++;
        }
    }

    /** Flow currently on a forward edge returned by {@link #addEdge}. */
    public long flowOn(int edge) {
        return original[edge] - cap[edge];
    }

    /** Number of augmenting paths used by the last run. */
    public int augmentingPaths() {
        return augmentingPaths;
    }

    public int vertexCount() {
        return n;
    }

    /** Forward edges only (reverse edges are not counted). */
    public int edgeCount() {
        return edges / 2;
    }

    /** Vertices reachable from s in the residual graph (the source side of a minimum cut). */
    public boolean[] reachableFrom(int s) {
        boolean[] seen = new boolean[n];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(s);
        seen[s] = true;
        while (!queue.isEmpty()) {
            int u = queue.poll();
            for (int e = head[u]; e != -1; e = next[e]) {
                if (!seen[to[e]] && cap[e] > 0) {
                    seen[to[e]] = true;
                    queue.add(to[e]);
                }
            }
        }
        return seen;
    }
}
