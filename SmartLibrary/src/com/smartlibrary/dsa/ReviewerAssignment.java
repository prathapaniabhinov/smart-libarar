package com.smartlibrary.dsa;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Assigning papers (books) to reviewers as a flow network (DSA Module 4).
 * <pre>
 *   source --(capacity c)--> reviewer --(1, if eligible)--> paper --(1)--> sink
 * </pre>
 * The maximum flow (Edmonds-Karp) is the largest number of papers that can be assigned when every
 * reviewer takes at most c papers. With c = 1 this is a maximum bipartite matching, and König's
 * theorem gives a minimum vertex cover of the same size from the final residual graph.
 */
public final class ReviewerAssignment {

    private ReviewerAssignment() {
    }

    /** Result of {@link #assign}. */
    public static final class Assignment {
        /** reviewerOf[p] = reviewer index assigned to paper p, or -1. */
        public final int[] reviewerOf;
        public final int assigned;
        public final int augmentingPaths;
        public final int vertices;
        public final int edges;
        public final long nanos;

        Assignment(int[] reviewerOf, int assigned, int augmentingPaths, int vertices, int edges, long nanos) {
            this.reviewerOf = reviewerOf;
            this.assigned = assigned;
            this.augmentingPaths = augmentingPaths;
            this.vertices = vertices;
            this.edges = edges;
            this.nanos = nanos;
        }

        public double millis() {
            return nanos / 1e6;
        }
    }

    /** Result of {@link #konig}. */
    public static final class KonigResult {
        public final int matchingSize;
        /** matchedPaper[r] = paper matched to reviewer r, or -1. */
        public final int[] matchedPaper;
        public final List<Integer> coverReviewers;
        public final List<Integer> coverPapers;

        KonigResult(int matchingSize, int[] matchedPaper, List<Integer> coverReviewers, List<Integer> coverPapers) {
            this.matchingSize = matchingSize;
            this.matchedPaper = matchedPaper;
            this.coverReviewers = Collections.unmodifiableList(coverReviewers);
            this.coverPapers = Collections.unmodifiableList(coverPapers);
        }

        public int coverSize() {
            return coverReviewers.size() + coverPapers.size();
        }
    }

    /**
     * Assigns papers to reviewers with Edmonds-Karp.
     *
     * @param eligible eligible[r][p] = reviewer r may review paper p
     * @param capacity maximum papers per reviewer
     */
    public static Assignment assign(boolean[][] eligible, int papers, int capacity) {
        long start = System.nanoTime();
        int reviewers = eligible.length;
        int source = 0;
        int sink = reviewers + papers + 1;
        MaxFlow flow = new MaxFlow(reviewers + papers + 2);
        int[][] edge = new int[reviewers][papers];
        for (int r = 0; r < reviewers; r++) {
            flow.addEdge(source, 1 + r, capacity);
            for (int p = 0; p < papers; p++) {
                edge[r][p] = eligible[r][p] ? flow.addEdge(1 + r, 1 + reviewers + p, 1) : -1;
            }
        }
        for (int p = 0; p < papers; p++) {
            flow.addEdge(1 + reviewers + p, sink, 1);
        }
        int total = (int) flow.edmondsKarp(source, sink);
        int[] reviewerOf = new int[papers];
        java.util.Arrays.fill(reviewerOf, -1);
        for (int r = 0; r < reviewers; r++) {
            for (int p = 0; p < papers; p++) {
                if (edge[r][p] >= 0 && flow.flowOn(edge[r][p]) == 1) {
                    reviewerOf[p] = r;
                }
            }
        }
        return new Assignment(reviewerOf, total, flow.augmentingPaths(), flow.vertexCount(), flow.edgeCount(),
                System.nanoTime() - start);
    }

    /**
     * Maximum bipartite matching (capacity 1) and a minimum vertex cover by König's theorem:
     * after the maximum flow, let Z be the vertices reachable from the source in the residual graph.
     * Cover = (reviewers NOT in Z) + (papers in Z). Its size equals the matching size.
     */
    public static KonigResult konig(boolean[][] eligible, int papers) {
        int reviewers = eligible.length;
        int source = 0;
        int sink = reviewers + papers + 1;
        MaxFlow flow = new MaxFlow(reviewers + papers + 2);
        int[][] edge = new int[reviewers][papers];
        for (int r = 0; r < reviewers; r++) {
            flow.addEdge(source, 1 + r, 1);
            for (int p = 0; p < papers; p++) {
                edge[r][p] = eligible[r][p] ? flow.addEdge(1 + r, 1 + reviewers + p, 1) : -1;
            }
        }
        for (int p = 0; p < papers; p++) {
            flow.addEdge(1 + reviewers + p, sink, 1);
        }
        int size = (int) flow.edmondsKarp(source, sink);
        int[] matchedPaper = new int[reviewers];
        java.util.Arrays.fill(matchedPaper, -1);
        for (int r = 0; r < reviewers; r++) {
            for (int p = 0; p < papers; p++) {
                if (edge[r][p] >= 0 && flow.flowOn(edge[r][p]) == 1) {
                    matchedPaper[r] = p;
                }
            }
        }
        boolean[] z = flow.reachableFrom(source);
        List<Integer> coverR = new ArrayList<>();
        List<Integer> coverP = new ArrayList<>();
        for (int r = 0; r < reviewers; r++) {
            if (!z[1 + r]) {
                coverR.add(r);
            }
        }
        for (int p = 0; p < papers; p++) {
            if (z[1 + reviewers + p]) {
                coverP.add(p);
            }
        }
        return new KonigResult(size, matchedPaper, coverR, coverP);
    }
}
