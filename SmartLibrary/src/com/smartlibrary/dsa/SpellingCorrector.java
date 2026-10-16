package com.smartlibrary.dsa;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "Did you mean?" for search queries. Every word of the query that is not in the vocabulary is
 * replaced by the closest vocabulary words by Damerau-Levenshtein distance (ties broken by
 * Levenshtein distance, then by how often the word appears).
 * <p>
 * Cost: O(V * n * m) for each unknown word, V = vocabulary size, n and m = word lengths.
 */
public final class SpellingCorrector {

    /** One suggested query. Distances are summed over the corrected words. */
    public static final class Suggestion {
        private final String text;
        private final int damerau;
        private final int levenshtein;

        Suggestion(String text, int damerau, int levenshtein) {
            this.text = text;
            this.damerau = damerau;
            this.levenshtein = levenshtein;
        }

        public String getText() { return text; }
        public int getDamerau() { return damerau; }
        public int getLevenshtein() { return levenshtein; }
    }

    private static final class Candidate {
        final String word;
        final int damerau;
        final int levenshtein;

        Candidate(String word, int damerau, int levenshtein) {
            this.word = word;
            this.damerau = damerau;
            this.levenshtein = levenshtein;
        }
    }

    private final Map<String, Integer> vocabulary;

    /** @param vocabulary word to number of occurrences (lower case) */
    public SpellingCorrector(Map<String, Integer> vocabulary) {
        this.vocabulary = Collections.unmodifiableMap(new HashMap<>(vocabulary));
    }

    /** Counts the words (letters/digits, lower case, at least 2 characters) of a text. */
    public static Map<String, Integer> vocabularyOf(String text, Map<String, Integer> into) {
        StringBuilder word = new StringBuilder();
        for (int i = 0; i <= text.length(); i++) {
            char c = i < text.length() ? text.charAt(i) : ' ';
            if (Character.isLetterOrDigit(c)) {
                word.append(Character.toLowerCase(c));
            } else if (word.length() > 0) {
                if (word.length() >= 2) {
                    into.merge(word.toString(), 1, Integer::sum);
                }
                word.setLength(0);
            }
        }
        return into;
    }

    public int vocabularySize() {
        return vocabulary.size();
    }

    /** Largest distance accepted for a word of this length. */
    static int maxDistance(int length) {
        return length <= 4 ? 1 : length <= 8 ? 2 : 3;
    }

    /**
     * Suggests corrected versions of a query. Keywords separated by commas are corrected separately.
     *
     * @return up to {@code max} suggestions, best first; empty if every word is already known
     *         or nothing close enough exists
     */
    public List<Suggestion> suggest(String query, int max) {
        List<String> keywords = TextUtil.parseKeywords(query);
        // per word: candidate list (a known word is its own only candidate)
        List<List<List<Candidate>>> perKeyword = new ArrayList<>();
        boolean anyUnknown = false;
        for (String keyword : keywords) {
            List<List<Candidate>> perWord = new ArrayList<>();
            for (String w : keyword.split("\\s+")) {
                if (w.isEmpty()) {
                    continue;
                }
                List<Candidate> cands = candidates(w, max);
                if (!(cands.size() == 1 && cands.get(0).damerau == 0)) {
                    anyUnknown = true;
                }
                if (cands.isEmpty()) {
                    cands = List.of(new Candidate(w, 0, 0)); // nothing close: keep the word as typed
                }
                perWord.add(cands);
            }
            perKeyword.add(perWord);
        }
        if (!anyUnknown) {
            return List.of();
        }

        Set<String> seen = new LinkedHashSet<>();
        List<Suggestion> out = new ArrayList<>();
        for (int rank = 0; rank < max; rank++) {
            List<String> fixedKeywords = new ArrayList<>();
            int dam = 0;
            int lev = 0;
            for (List<List<Candidate>> perWord : perKeyword) {
                List<String> words = new ArrayList<>();
                for (List<Candidate> cands : perWord) {
                    Candidate c = cands.get(Math.min(rank, cands.size() - 1));
                    words.add(c.word);
                    dam += c.damerau;
                    lev += c.levenshtein;
                }
                fixedKeywords.add(String.join(" ", words));
            }
            String text = String.join(", ", fixedKeywords);
            if (dam > 0 && seen.add(text)) {
                out.add(new Suggestion(text, dam, lev));
            }
        }
        return out;
    }

    private List<Candidate> candidates(String word, int max) {
        if (vocabulary.containsKey(word)) {
            return List.of(new Candidate(word, 0, 0));
        }
        int limit = maxDistance(word.length());
        List<Candidate> found = new ArrayList<>();
        for (String v : vocabulary.keySet()) {
            if (Math.abs(v.length() - word.length()) > limit) {
                continue; // cannot be within the limit
            }
            int d = EditDistance.damerauLevenshtein(word, v);
            if (d <= limit) {
                found.add(new Candidate(v, d, EditDistance.levenshtein(word, v)));
            }
        }
        found.sort((a, b) -> {
            if (a.damerau != b.damerau) {
                return Integer.compare(a.damerau, b.damerau);
            }
            if (a.levenshtein != b.levenshtein) {
                return Integer.compare(a.levenshtein, b.levenshtein);
            }
            int fa = vocabulary.get(a.word);
            int fb = vocabulary.get(b.word);
            return fa != fb ? Integer.compare(fb, fa) : a.word.compareTo(b.word);
        });
        return found.size() > max ? new ArrayList<>(found.subList(0, max)) : found;
    }
}
