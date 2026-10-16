package com.smartlibrary.dsa;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Optimal Binary Search Tree for keywords with known search frequencies.
 * Dynamic programming, O(n^3) time and O(n^2) space.
 * <p>
 * cost[i][j] = smallest total (frequency x depth) of a BST built from keys i..j:
 * <pre>  cost[i][j] = min over r in i..j of ( cost[i][r-1] + cost[r+1][j] ) + (sum of freq[i..j])</pre>
 * Frequently searched keys end up near the root, so on average fewer comparisons are needed
 * than in a balanced BST that ignores frequencies.
 */
public final class OptimalBST {

    /** A tree node. */
    public static final class Node {
        public final String key;
        public final int frequency;
        public Node left;
        public Node right;

        Node(String key, int frequency) {
            this.key = key;
            this.frequency = frequency;
        }
    }

    private final String[] keys;
    private final int[] freq;
    private final long totalFrequency;
    private final long optimalCost;
    private final Node root;
    private final Node balancedRoot;
    private final long buildNanos;

    /** @param keyFrequencies keyword to number of searches (frequencies must be positive) */
    public OptimalBST(Map<String, Integer> keyFrequencies) {
        TreeMap<String, Integer> sorted = new TreeMap<>(keyFrequencies);
        int n = sorted.size();
        keys = new String[n];
        freq = new int[n];
        int k = 0;
        long total = 0;
        for (Map.Entry<String, Integer> e : sorted.entrySet()) {
            keys[k] = e.getKey();
            freq[k] = e.getValue();
            total += e.getValue();
            k++;
        }
        totalFrequency = total;

        long start = System.nanoTime();
        long[][] cost = new long[n + 1][n + 1]; // cost[i][j] for keys i..j-1 (half-open), cost[i][i] = 0
        int[][] rootIdx = new int[n + 1][n + 1];
        long[] prefix = new long[n + 1];
        for (int i = 0; i < n; i++) {
            prefix[i + 1] = prefix[i] + freq[i];
        }
        for (int len = 1; len <= n; len++) {
            for (int i = 0; i + len <= n; i++) {
                int j = i + len;
                long best = Long.MAX_VALUE;
                int bestR = i;
                for (int r = i; r < j; r++) {
                    long c = cost[i][r] + cost[r + 1][j];
                    if (c < best) {
                        best = c;
                        bestR = r;
                    }
                }
                cost[i][j] = best + (prefix[j] - prefix[i]);
                rootIdx[i][j] = bestR;
            }
        }
        optimalCost = n == 0 ? 0 : cost[0][n];
        root = build(rootIdx, 0, n);
        buildNanos = System.nanoTime() - start;
        balancedRoot = buildBalanced(0, n);
    }

    private Node build(int[][] rootIdx, int i, int j) {
        if (i >= j) {
            return null;
        }
        int r = rootIdx[i][j];
        Node node = new Node(keys[r], freq[r]);
        node.left = build(rootIdx, i, r);
        node.right = build(rootIdx, r + 1, j);
        return node;
    }

    /** Balanced BST from the sorted keys (middle key as root), ignoring frequencies. */
    private Node buildBalanced(int i, int j) {
        if (i >= j) {
            return null;
        }
        int mid = (i + j) >>> 1;
        Node node = new Node(keys[mid], freq[mid]);
        node.left = buildBalanced(i, mid);
        node.right = buildBalanced(mid + 1, j);
        return node;
    }

    public Node root() {
        return root;
    }

    public int size() {
        return keys.length;
    }

    public long totalFrequency() {
        return totalFrequency;
    }

    public double buildMillis() {
        return buildNanos / 1e6;
    }

    /** Average comparisons per search in the optimal tree (root = 1 comparison). */
    public double expectedComparisons() {
        return totalFrequency == 0 ? 0 : (double) optimalCost / totalFrequency;
    }

    /** Average comparisons per search in a balanced BST of the same keys. */
    public double balancedExpectedComparisons() {
        return totalFrequency == 0 ? 0 : (double) weightedDepth(balancedRoot, 1) / totalFrequency;
    }

    private static long weightedDepth(Node node, int depth) {
        if (node == null) {
            return 0;
        }
        return (long) node.frequency * depth + weightedDepth(node.left, depth + 1)
                + weightedDepth(node.right, depth + 1);
    }

    /** Comparisons needed to find a key in the optimal tree (negative if not found: -comparisons made). */
    public int comparisonsInOptimal(String key) {
        return comparisons(root, key);
    }

    /** Comparisons needed to find a key in the balanced tree (negative if not found). */
    public int comparisonsInBalanced(String key) {
        return comparisons(balancedRoot, key);
    }

    private static int comparisons(Node node, String key) {
        int count = 0;
        while (node != null) {
            count++;
            int c = key.compareTo(node.key);
            if (c == 0) {
                return count;
            }
            node = c < 0 ? node.left : node.right;
        }
        return -count;
    }

    /** Text drawing of the tree, one node per line, children indented (L = left, R = right). */
    public List<String> draw(int maxDepth) {
        List<String> lines = new ArrayList<>();
        draw(root, "", "", 1, maxDepth, lines);
        return lines;
    }

    private static void draw(Node node, String prefix, String label, int depth, int maxDepth, List<String> out) {
        if (node == null) {
            return;
        }
        out.add(prefix + label + node.key + " (" + node.frequency + ")");
        if (depth == maxDepth) {
            if (node.left != null || node.right != null) {
                out.add(prefix + "    ...");
            }
            return;
        }
        String childPrefix = prefix + "    ";
        draw(node.left, childPrefix, "L: ", depth + 1, maxDepth, out);
        draw(node.right, childPrefix, "R: ", depth + 1, maxDepth, out);
    }
}
