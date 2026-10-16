package com.smartlibrary.dsa;

import java.util.Arrays;

/**
 * Suffix tree built with Ukkonen's algorithm. Time O(n) for a fixed alphabet.
 * <p>
 * Every path from the root spells a substring of the text; every suffix ends at a leaf.
 * Edges store (start, end) indexes into the text instead of copies of the characters.
 * <p>
 * Uses in SmartLibrary:
 * <ul>
 *   <li>{@link #find} - substring retrieval: does a phrase occur, how many times, where first. O(m).</li>
 *   <li>{@link #matchingStatistics} - for each position of another text, the longest prefix that
 *       occurs in this text; used to find the common text of two books. O(|other text|).</li>
 * </ul>
 * The text must end with a unique terminal value 0 that appears nowhere else.
 */
public final class SuffixTree {

    private static final int LEAF = Integer.MAX_VALUE;
    private static final int ROOT = 0;

    private final int[] text;
    private final int n;

    // node arrays (children kept as a linked list: firstChild / nextSibling)
    private final int[] start;
    private final int[] end;
    private final int[] link;
    private final int[] firstChild;
    private final int[] nextSibling;
    private int nodeCount;
    private int leafEnd;

    // filled after construction
    private final int[] leafCount;
    private final int[] firstStart;

    public SuffixTree(int[] text) {
        if (text.length == 0 || text[text.length - 1] != 0) {
            throw new IllegalArgumentException("Text must end with a unique 0 terminal");
        }
        this.text = text;
        this.n = text.length;
        int capacity = 2 * n + 2;
        start = new int[capacity];
        end = new int[capacity];
        link = new int[capacity];
        firstChild = new int[capacity];
        nextSibling = new int[capacity];
        build();
        leafCount = new int[nodeCount];
        firstStart = new int[nodeCount];
        computeLeafInfo();
    }

    public int nodeCount() {
        return nodeCount;
    }

    // ================================================================ Ukkonen's algorithm

    private void build() {
        newNode(-1, -1); // root
        int activeNode = ROOT;
        int activeEdge = 0;
        int activeLength = 0;
        int remainder = 0;

        for (int pos = 0; pos < n; pos++) {
            leafEnd = pos;          // rule 1: every leaf grows by one character automatically
            remainder++;
            int lastNewInternal = -1;

            while (remainder > 0) {
                if (activeLength == 0) {
                    activeEdge = pos;
                }
                int next = child(activeNode, text[activeEdge]);
                if (next == -1) {
                    // rule 2: new leaf straight from the active node
                    addChild(activeNode, newNode(pos, LEAF));
                    if (lastNewInternal != -1) {
                        link[lastNewInternal] = activeNode;
                        lastNewInternal = -1;
                    }
                } else {
                    int edgeLength = edgeLength(next);
                    if (activeLength >= edgeLength) { // walk down (skip / count trick)
                        activeEdge += edgeLength;
                        activeLength -= edgeLength;
                        activeNode = next;
                        continue;
                    }
                    if (text[start[next] + activeLength] == text[pos]) {
                        // rule 3: character already present - stop this phase
                        if (lastNewInternal != -1 && activeNode != ROOT) {
                            link[lastNewInternal] = activeNode;
                            lastNewInternal = -1;
                        }
                        activeLength++;
                        break;
                    }
                    // rule 2 with split: new internal node in the middle of the edge
                    int split = newNode(start[next], start[next] + activeLength - 1);
                    replaceChild(activeNode, next, split);
                    addChild(split, newNode(pos, LEAF));
                    start[next] += activeLength;
                    addChild(split, next);
                    if (lastNewInternal != -1) {
                        link[lastNewInternal] = split;
                    }
                    lastNewInternal = split;
                }
                remainder--;
                if (activeNode == ROOT && activeLength > 0) {
                    activeLength--;
                    activeEdge = pos - remainder + 1;
                } else if (activeNode != ROOT) {
                    activeNode = link[activeNode]; // suffix link (root by default)
                }
            }
        }
        leafEnd = n - 1;
    }

    private int newNode(int s, int e) {
        int v = nodeCount++;
        start[v] = s;
        end[v] = e;
        link[v] = ROOT;
        firstChild[v] = -1;
        nextSibling[v] = -1;
        return v;
    }

    private int edgeLength(int v) {
        return (end[v] == LEAF ? leafEnd : end[v]) - start[v] + 1;
    }

    private int child(int v, int c) {
        for (int u = firstChild[v]; u != -1; u = nextSibling[u]) {
            if (text[start[u]] == c) {
                return u;
            }
        }
        return -1;
    }

    private void addChild(int parent, int c) {
        nextSibling[c] = firstChild[parent];
        firstChild[parent] = c;
    }

    private void replaceChild(int parent, int oldChild, int newChild) {
        if (firstChild[parent] == oldChild) {
            nextSibling[newChild] = nextSibling[oldChild];
            firstChild[parent] = newChild;
            return;
        }
        for (int u = firstChild[parent]; u != -1; u = nextSibling[u]) {
            if (nextSibling[u] == oldChild) {
                nextSibling[newChild] = nextSibling[oldChild];
                nextSibling[u] = newChild;
                return;
            }
        }
        throw new IllegalStateException("child not found");
    }

    /** Number of leaves (= occurrences) under every node, and the smallest suffix start below it. */
    private void computeLeafInfo() {
        int[] depth = new int[nodeCount];
        int[] order = new int[nodeCount];
        int[] stack = new int[nodeCount];
        int top = 0;
        int count = 0;
        stack[top++] = ROOT;
        while (top > 0) { // pre-order, parents before children
            int v = stack[--top];
            order[count++] = v;
            for (int u = firstChild[v]; u != -1; u = nextSibling[u]) {
                depth[u] = depth[v] + edgeLength(u);
                stack[top++] = u;
            }
        }
        Arrays.fill(firstStart, Integer.MAX_VALUE);
        for (int i = count - 1; i >= 0; i--) { // children before parents
            int v = order[i];
            if (firstChild[v] == -1) {
                leafCount[v] = 1;
                firstStart[v] = n - depth[v];
            }
            for (int u = firstChild[v]; u != -1; u = nextSibling[u]) {
                leafCount[v] += leafCount[u];
                firstStart[v] = Math.min(firstStart[v], firstStart[u]);
            }
        }
    }

    // ================================================================ queries

    /** Result of {@link #find}: number of occurrences and the first start index (-1 if none). */
    public static final class Occurrences {
        public final int count;
        public final int firstPosition;

        Occurrences(int count, int firstPosition) {
            this.count = count;
            this.firstPosition = firstPosition;
        }
    }

    /** Substring retrieval: walks the pattern down from the root. O(m) plus child lookups. */
    public Occurrences find(int[] pattern) {
        if (pattern.length == 0) {
            return new Occurrences(0, -1);
        }
        int node = ROOT;
        int i = 0;
        while (i < pattern.length) {
            int c = child(node, pattern[i]);
            if (c == -1) {
                return new Occurrences(0, -1);
            }
            int len = edgeLength(c);
            for (int k = 0; k < len && i < pattern.length; k++, i++) {
                if (text[start[c] + k] != pattern[i]) {
                    return new Occurrences(0, -1);
                }
            }
            node = c;
        }
        return new Occurrences(leafCount[node], firstStart[node]);
    }

    /**
     * Matching statistics of {@code other} against this tree: ms[i] = length of the longest prefix of
     * other[i..] that occurs somewhere in this text. Uses suffix links so the total work is O(|other|).
     * {@code other} must not contain the terminal value 0.
     */
    public int[] matchingStatistics(int[] other) {
        int m = other.length;
        int[] ms = new int[m];
        int node = ROOT;   // deepest node on the matched path
        int edge = -1;     // edge below node we are part-way along
        int onEdge = 0;    // characters matched along that edge
        int matched = 0;   // total length matched = string depth

        for (int i = 0; i < m; i++) {
            // 1. extend the match as far as possible
            while (i + matched < m) {
                int c = other[i + matched];
                if (onEdge == 0) {
                    int ch = child(node, c);
                    if (ch == -1) {
                        break;
                    }
                    edge = ch;
                } else if (text[start[edge] + onEdge] != c) {
                    break;
                }
                onEdge++;
                matched++;
                if (onEdge == edgeLength(edge)) {
                    node = edge;
                    onEdge = 0;
                }
            }
            ms[i] = matched;
            if (matched == 0) {
                continue;
            }

            // 2. move to the match for position i + 1 (drop the first character)
            int restStart;
            int restLength;
            if (node == ROOT) {
                restStart = i + 1;
                restLength = matched - 1;
            } else {
                restStart = i + matched - onEdge;
                restLength = onEdge;
                node = link[node];
            }
            matched--;
            onEdge = 0;
            // skip / count: re-walk restLength characters from node
            while (restLength > 0) {
                int ch = child(node, other[restStart]);
                int len = edgeLength(ch);
                if (restLength >= len) {
                    node = ch;
                    restStart += len;
                    restLength -= len;
                } else {
                    edge = ch;
                    onEdge = restLength;
                    restLength = 0;
                }
            }
        }
        return ms;
    }
}
