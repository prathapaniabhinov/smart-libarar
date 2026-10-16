package com.smartlibrary.dsa;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * Aho-Corasick. Time O(n + M + z): M = total pattern length, z = number of matches.
 * <p>
 * 1. Build a trie of all patterns.
 * 2. Add failure links with a BFS (like KMP's LPS table, but for many patterns at once).
 * 3. Scan the text once; at each character follow trie edges or failure links and report every
 *    pattern that ends at the current state.
 */
public class AhoCorasickMatcher implements StringMatcher {

    @Override
    public Algorithm algorithm() {
        return Algorithm.AHO_CORASICK;
    }

    @Override
    public MatchResult search(String text, List<String> patterns) {
        long[] comparisons = new long[1];

        List<String> keywords = new ArrayList<>(new LinkedHashSet<>(patterns));
        keywords.removeIf(p -> p == null || p.isEmpty());

        // ---- 1. trie: node 0 is the root
        List<Map<Character, Integer>> next = new ArrayList<>();
        List<Integer> fail = new ArrayList<>();
        List<List<Integer>> output = new ArrayList<>(); // keyword indexes ending at each node
        addNode(next, fail, output);

        for (int k = 0; k < keywords.size(); k++) {
            int node = 0;
            for (char c : keywords.get(k).toCharArray()) {
                Integer child = next.get(node).get(c);
                if (child == null) {
                    child = addNode(next, fail, output);
                    next.get(node).put(c, child);
                }
                node = child;
            }
            output.get(node).add(k);
        }

        // ---- 2. failure links (BFS, so shorter states are finished first)
        Queue<Integer> queue = new ArrayDeque<>();
        for (int child : next.get(0).values()) {
            fail.set(child, 0);
            queue.add(child);
        }
        while (!queue.isEmpty()) {
            int u = queue.poll();
            for (Map.Entry<Character, Integer> edge : next.get(u).entrySet()) {
                char c = edge.getKey();
                int v = edge.getValue();
                int f = fail.get(u);
                while (f != 0 && !next.get(f).containsKey(c)) {
                    comparisons[0]++;
                    f = fail.get(f);
                }
                Integer target = next.get(f).get(c);
                fail.set(v, (target != null && target != v) ? target : 0);
                output.get(v).addAll(output.get(fail.get(v))); // inherit patterns that end here too
                queue.add(v);
            }
        }

        // ---- 3. single scan of the text
        List<Match> matches = new ArrayList<>();
        int state = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            while (state != 0 && !next.get(state).containsKey(c)) {
                comparisons[0]++;
                state = fail.get(state);
            }
            comparisons[0]++;
            Integer to = next.get(state).get(c);
            state = to == null ? 0 : to;
            for (int k : output.get(state)) {
                String keyword = keywords.get(k);
                matches.add(new Match(i - keyword.length() + 1, keyword));
            }
        }
        matches.sort(Comparator.comparingInt(Match::getPosition).thenComparing(Match::getPattern));
        return new MatchResult(algorithm(), patterns, matches, comparisons[0]);
    }

    private static int addNode(List<Map<Character, Integer>> next, List<Integer> fail, List<List<Integer>> output) {
        next.add(new HashMap<>());
        fail.add(0);
        output.add(new ArrayList<>());
        return next.size() - 1;
    }
}
