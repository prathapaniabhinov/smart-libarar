# DSA Modules 1–6 (implemented)

# Module 1 – String Matching

All four algorithms are implemented and used by the two-step book search.

| Algorithm    | Class                | Time complexity             | Idea |
|--------------|----------------------|-----------------------------|------|
| KMP          | `KmpMatcher`         | O(n + m)                    | LPS table; never moves backwards in the text |
| Rabin–Karp   | `RabinKarpMatcher`   | O(n + m) avg, O(n·m) worst  | Rolling hash of each window; letters compared only when hashes match |
| Z Algorithm  | `ZAlgorithmMatcher`  | O(n + m)                    | Z array of pattern + `$` + text; Z = m means a match |
| Aho–Corasick | `AhoCorasickMatcher` | O(n + M + z)                | Trie + failure links; many patterns in one pass |

n = text length, m = pattern length, M = total length of all patterns, z = number of matches.
With k patterns, KMP / Rabin–Karp / Z scan the text once per pattern: O(k·(n + m)).

## Classes

- `StringMatcher` – common interface: `MatchResult search(String text, List<String> patterns)`
- `SingleKeywordMatcher` – base class for KMP, Rabin–Karp and Z (one pattern at a time)
- `Algorithm` – enum with name, complexity and idea; `fromMenuChoice(int)` is the switch case
  (1 = KMP, 2 = Rabin–Karp, 3 = Z, 4 = Aho–Corasick)
- `Match`, `MatchResult` – positions found, comparisons counted, time taken
- `TextUtil` – lower-casing that keeps positions, keyword parsing (`tree, graph`),
  line index and binary search from a position to its line number

## Where they are used

1. **Find a book** (`BookService.findBooks`): linear search over all books; the chosen algorithm
   searches each book's "title | author | category". Books in the same category are listed as similar.
2. **Search inside a book** (`BookService.searchInBook`): the chosen algorithm searches the book's full text.
   Results show line and column of each match.

Option 5 in the menu runs all four algorithms on the same input and checks they find the same matches.

## Comparisons counted

- KMP, Z: character comparisons (including building the LPS table).
- Rabin–Karp: one per window hash comparison plus character checks when hashes are equal.
- Aho–Corasick: one per trie transition or failure-link step.

Timing uses `System.nanoTime()` after one untimed warm-up run, so Java's JIT start-up does not distort it.

## Tests

`test/com/smartlibrary/StringMatchingTests.java` checks every algorithm against `String.indexOf`
on 20,000 random texts, plus edge cases, overlapping matches, the two-step search, book import
validation and permissions.

# Module 2 – Suffix Structures for Document Similarity

Used by **Compare Documents** (Student, Teacher, Librarian): two books → common text,
longest common phrase, similarity %, and phrase lookup in both books.

| Structure | Class | Time | Job |
|---|---|---|---|
| Suffix Array | `SuffixArraySAIS` | — | Index of "book1 # book2 $"; phrase lookup by binary search, O(m log n) |
| SA-IS | `SuffixArraySAIS.build` | O(n) | Builds the suffix array by induced sorting (naive sort shown for comparison) |
| LCP / Kasai | `LcpKasai` | O(n) | LCP of neighbouring suffixes; neighbours from different books = common text |
| Suffix Tree | `SuffixTree` (Ukkonen) | O(n) build, O(m) lookup | Substring retrieval (occurrences, first position); matching statistics via suffix links |

`ComparisonSession` computes the *matching statistics* of the shorter book against the longer one
(for every position, the longest text starting there that also occurs in the other book), either from
the suffix array + LCP (nearest suffix of the other book above/below) or from the suffix tree
(walk with suffix links). Both give identical results. Matches of at least the minimum length are
cut at sentence ends, trimmed to whole words and de-duplicated. `NormalizedText` lower-cases the
text, removes punctuation and chapter headings, and keeps a map back to original line numbers.

Similarity % = letters/digits of the shorter book inside common phrases ÷ letters/digits of the
shorter book × 100.

Tests: `test/com/smartlibrary/DocumentSimilarityTests.java`.

# Module 3 – Advanced Dynamic Programming

| Algorithm | Class | Time | Used for |
|---|---|---|---|
| Levenshtein | `EditDistance.levenshtein` | O(n·m), O(m) space | "Did you mean?" ranking (insert / delete / replace) |
| Damerau-Levenshtein (optimal string alignment) | `EditDistance.damerauLevenshtein` | O(n·m), O(m) space | "Did you mean?" – also counts a swap of neighbouring letters as one edit |
| Needleman-Wunsch | `SequenceAlignment.needlemanWunsch` | O(n·m) | Global word-level alignment of two books |
| Smith-Waterman | `SequenceAlignment.smithWaterman` | O(n·m) | Best-matching passage (local alignment), catches reworded copying |
| Optimal BST | `OptimalBST` | O(n³) time, O(n²) space | Search Statistics: most searched keywords near the root |

- `SpellingCorrector` suggests, for every unknown word, the vocabulary words within 1–3 edits
  (by word length), ranked by Damerau-Levenshtein, then Levenshtein, then frequency.
- Alignment scoring: match +2, mismatch −1, gap −1; first 3,000 words of each book.
- Optimal BST recurrence: `cost[i][j] = min over r ( cost[i][r] + cost[r+1][j] ) + sum(freq[i..j])`,
  compared with a balanced BST of the same keys.

Tests: `test/com/smartlibrary/DynamicProgrammingTests.java`.

# Module 4 – Network Flow

| Algorithm | Class | Time | Used for |
|---|---|---|---|
| Ford-Fulkerson / Edmonds-Karp | `MaxFlow` | O(V·E²) | Reviewer Assignment: most papers assigned, each reviewer at most c papers |
| Bipartite matching | `ReviewerAssignment.konig` | O(V·E²) | Same network with capacity 1 |
| König's theorem | `ReviewerAssignment.konig` | O(V + E) after the flow | Minimum vertex cover = (reviewers not reachable in the residual graph) + (papers reachable); size = matching |

# Module 5 – NP-Completeness and Approximation

| Concept | Class | Time | Used for |
|---|---|---|---|
| Vertex Cover (exact) | `ConflictGraph.exactMinVertexCover` | O(2^n · n) | Fewest books to check so every overlap is checked |
| Vertex Cover 2-approximation | `ConflictGraph.vertexCover2Approx` | O(V + E) | Same, polynomial, at most 2× optimum |
| CLIQUE → IS → VC | `ConflictGraph.complement`, `isClique`, `isIndependentSet` | — | IS = V − VC; IS in G = clique in the complement |

Edges come from Module 2 similarity (suffix tree) between every pair of books.

# Module 6 – Randomized and Parallel Algorithms

| Algorithm | Class | Time | Used for |
|---|---|---|---|
| Randomized QuickSort | `RandomizedQuickSort` | O(n log n) expected (≈ 2n ln n comparisons) | Ranking books and search results; compared with first-element pivot (n²/2 on sorted input) |
| Reservoir Sampling | `ReservoirSampling` | O(N) time, O(k) memory | k random events from the usage stream in one pass |
| Parallel Reduce | `ParallelStats.countWords` | work O(n), span O(log n) | Word count per book with fork/join |
| Blelloch Scan | `ParallelStats.blellochScan` | work O(n), span O(log n) | Cumulative word counts (prefix sums) |
| Brent's theorem | `ParallelStats.brentBound` | — | T_p ≤ W/p + S |

Tests: `test/com/smartlibrary/AdvancedAlgorithmsTests.java`.
