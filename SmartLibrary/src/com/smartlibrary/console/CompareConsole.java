package com.smartlibrary.console;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.smartlibrary.dsa.ComparisonSession;
import com.smartlibrary.dsa.ComparisonSession.PhraseLookup;
import com.smartlibrary.dsa.NormalizedText;
import com.smartlibrary.dsa.SequenceAlignment;
import com.smartlibrary.dsa.SimilarityMethod;
import com.smartlibrary.dsa.SimilarityResult;
import com.smartlibrary.dsa.SimilarityResult.CommonPhrase;
import com.smartlibrary.model.Book;
import com.smartlibrary.service.BookService;
import com.smartlibrary.service.BookService.BookText;
import com.smartlibrary.service.ServiceException;

/**
 * DSA Module 2 - Document Similarity. Compares two books and shows their common text,
 * the longest common phrase and a similarity percentage, then lets the user look up a phrase
 * in both books.
 * <ul>
 *   <li>Suffix Array built with SA-IS (document indexing, phrase lookup by binary search)</li>
 *   <li>LCP array with Kasai (common phrases)</li>
 *   <li>Suffix Tree built with Ukkonen's algorithm (common phrases, substring retrieval)</li>
 * </ul>
 * Module 3 adds word-level alignment: Needleman-Wunsch (global) and Smith-Waterman (local).
 */
public class CompareConsole {

    private static final int RUN_BOTH = 3;
    private static final int NEEDLEMAN_WUNSCH = 4;
    private static final int SMITH_WATERMAN = 5;
    private static final int DEFAULT_MIN_LENGTH = 10;
    private static final int MAX_PHRASES_SHOWN = 15;

    private final ConsoleIO io;
    private final BookService books;

    public CompareConsole(ConsoleIO io, BookService books) {
        this.io = io;
        this.books = books;
    }

    public void run() throws ServiceException {
        while (true) {
            io.header("Compare Documents - Step 1: Choose two books");
            List<Book> all = new ArrayList<>(books.listBooks());
            if (all.size() < 2) {
                io.info("At least two books are needed. A Librarian can import .txt books from Manage Books.");
                io.pause();
                return;
            }
            all.sort((a, b) -> {
                int c = a.getCategory().compareToIgnoreCase(b.getCategory());
                return c != 0 ? c : a.getTitle().compareToIgnoreCase(b.getTitle());
            });
            List<String[]> rows = new ArrayList<>();
            for (int i = 0; i < all.size(); i++) {
                Book b = all.get(i);
                rows.add(new String[] { Integer.toString(i + 1), b.getTitle(), b.getAuthor(), b.getCategory() });
            }
            io.table(new String[] { "#", "Title", "Author", "Category" }, rows);

            int first = io.readChoice("Select first book  (0 = back): ", 0, all.size());
            if (first == 0) {
                return;
            }
            int second;
            while (true) {
                second = io.readChoice("Select second book (0 = back): ", 0, all.size());
                if (second != first) {
                    break;
                }
                io.error("Please choose a different book from the first one.");
            }
            if (second == 0) {
                return;
            }

            BookText textA = books.openBook(all.get(first - 1));
            BookText textB = books.openBook(all.get(second - 1));
            ComparisonSession session = books.startComparison(textA, textB);

            int method = chooseMethod();
            if (method == 0) {
                continue;
            }
            int minLength = method <= RUN_BOTH ? readMinLength() : DEFAULT_MIN_LENGTH;
            List<SimilarityMethod> used = new ArrayList<>();
            if (method == RUN_BOTH) {
                used.add(SimilarityMethod.SUFFIX_ARRAY);
                used.add(SimilarityMethod.SUFFIX_TREE);
                runBoth(session, textA, textB, minLength);
            } else if (method == NEEDLEMAN_WUNSCH || method == SMITH_WATERMAN) {
                printAlignment(books.alignBooks(textA, textB, method == SMITH_WATERMAN), textA, textB);
            } else {
                SimilarityMethod m = SimilarityMethod.fromMenuChoice(method);
                used.add(m);
                printResult(books.compareBooks(session, m, minLength), textA, textB);
            }

            if (!used.isEmpty()) {
                findPhrases(session, textA, textB, used);
            }
            if (!io.confirm("\nCompare two other books?")) {
                return;
            }
        }
    }

    // ------------------------------------------------------------------ input

    private int readMinLength() {
        while (true) {
            String s = io.readLine("Minimum phrase length in characters (Enter = " + DEFAULT_MIN_LENGTH + "): ");
            if (s.isEmpty()) {
                return DEFAULT_MIN_LENGTH;
            }
            try {
                int v = Integer.parseInt(s);
                if (v >= 4 && v <= 200) {
                    return v;
                }
            } catch (NumberFormatException ignored) {
                // ask again
            }
            io.error("Please enter a number from 4 to 200, or press Enter for " + DEFAULT_MIN_LENGTH + ".");
        }
    }

    /** Method menu. The switch on the number is in {@link SimilarityMethod#fromMenuChoice}. */
    private int chooseMethod() {
        io.blank();
        io.println("Choose method:");
        int i = 1;
        for (SimilarityMethod m : SimilarityMethod.values()) {
            io.println(String.format("  %d. %-36s %s", i++, m.getDisplayName(), m.getComplexity()));
        }
        io.println("  3. Run BOTH and compare");
        io.println("  4. Needleman-Wunsch (global word alignment)   O(n x m)");
        io.println("  5. Smith-Waterman (best matching passage)     O(n x m)");
        io.println("  0. Back");
        return io.readChoice("Choose: ", 0, SMITH_WATERMAN);
    }

    // ------------------------------------------------------------------ Module 3: alignment

    private void printAlignment(SequenceAlignment.Result r, BookText a, BookText b) {
        NormalizedText na = new NormalizedText(a.getOriginalText());
        NormalizedText nb = new NormalizedText(b.getOriginalText());
        List<SequenceAlignment.Word> wa = SequenceAlignment.words(na);
        List<SequenceAlignment.Word> wb = SequenceAlignment.words(nb);

        io.header("Results: \"" + a.getBook().getTitle() + "\" vs \"" + b.getBook().getTitle() + "\"");
        io.println("  Method              : " + (r.local ? "Smith-Waterman (local alignment)"
                : "Needleman-Wunsch (global alignment)"));
        io.println("  How it works        : " + (r.local
                ? "DP over words; scores below 0 restart at 0, so the best-scoring passage is found."
                : "DP over words; aligns both texts from start to end, matching words where possible."));
        io.println("  Words               : n = " + r.firstWords + " (Book 1), m = " + r.secondWords + " (Book 2)"
                + (r.truncated ? "  - first " + SequenceAlignment.MAX_WORDS + " words of each book used" : ""));
        io.println("  DP table            : " + (r.firstWords + 1) + " x " + (r.secondWords + 1) + " = " + r.cells
                + " cells, " + ms(r.millis()) + " ms   O(n x m)");
        io.println("  Scoring             : match +" + SequenceAlignment.MATCH + ", mismatch "
                + SequenceAlignment.MISMATCH + ", gap " + SequenceAlignment.GAP);
        io.println("  Alignment score     : " + r.score);
        io.println("  Aligned words       : " + r.matches + " same, " + r.mismatches + " different, "
                + r.gaps + " gaps");

        if (!r.local) {
            io.println("  Identity            : " + pct(r.identityPercent())
                    + " of aligned positions have the same word");
            io.info("Global alignment shows how closely the two books follow the same wording from start to end.");
            return;
        }
        if (r.columns.isEmpty() || r.matches == 0) {
            io.info("No matching passage was found.");
            return;
        }
        int firstA = -1;
        int lastA = -1;
        int firstB = -1;
        int lastB = -1;
        for (SequenceAlignment.Column c : r.columns) {
            if (c.first >= 0) {
                firstA = firstA < 0 ? c.first : firstA;
                lastA = c.first;
            }
            if (c.second >= 0) {
                firstB = firstB < 0 ? c.second : firstB;
                lastB = c.second;
            }
        }
        io.println("  Best matching passage: " + r.columns.size() + " aligned words, "
                + pct(r.identityPercent()) + " identical");
        io.println("     Book 1: lines " + lineOfWord(wa, firstA, na, a) + "-" + lineOfWord(wa, lastA, na, a)
                + "    Book 2: lines " + lineOfWord(wb, firstB, nb, b) + "-" + lineOfWord(wb, lastB, nb, b));
        io.blank();
        printAlignedColumns(r, wa, wb);
        io.info("| = same word, x = different word, - = gap (word added or removed)");
    }

    private static int lineOfWord(List<SequenceAlignment.Word> words, int index, NormalizedText norm, BookText text) {
        return index < 0 ? 0 : text.lineOf(norm.originalIndex(words.get(index).position));
    }

    /** Prints the alignment as three rows (Book 1, markers, Book 2), wrapped at about 70 characters. */
    private void printAlignedColumns(SequenceAlignment.Result r, List<SequenceAlignment.Word> wa,
                                     List<SequenceAlignment.Word> wb) {
        int maxColumns = 60;
        StringBuilder top = new StringBuilder();
        StringBuilder mid = new StringBuilder();
        StringBuilder bot = new StringBuilder();
        int shown = 0;
        for (SequenceAlignment.Column c : r.columns) {
            if (shown == maxColumns) {
                break;
            }
            String x = c.first >= 0 ? wa.get(c.first).text : "-";
            String y = c.second >= 0 ? wb.get(c.second).text : "-";
            char mark = (c.first < 0 || c.second < 0) ? ' ' : x.equals(y) ? '|' : 'x';
            int w = Math.max(x.length(), y.length());
            if (top.length() + w > 66) {
                flush(top, mid, bot);
            }
            top.append(pad(x, w)).append(' ');
            mid.append(pad(String.valueOf(mark), w)).append(' ');
            bot.append(pad(y, w)).append(' ');
            shown++;
        }
        flush(top, mid, bot);
        if (r.columns.size() > maxColumns) {
            io.println("     ... and " + (r.columns.size() - maxColumns) + " more aligned words");
        }
    }

    private void flush(StringBuilder top, StringBuilder mid, StringBuilder bot) {
        if (top.length() == 0) {
            return;
        }
        io.println("     Book 1: " + top.toString().stripTrailing());
        io.println("             " + mid.toString().stripTrailing());
        io.println("     Book 2: " + bot.toString().stripTrailing());
        io.blank();
        top.setLength(0);
        mid.setLength(0);
        bot.setLength(0);
    }

    private static String pad(String s, int w) {
        return s + " ".repeat(Math.max(0, w - s.length()));
    }

    // ------------------------------------------------------------------ results

    private void printResult(SimilarityResult r, BookText a, BookText b) {
        io.header("Results: \"" + a.getBook().getTitle() + "\" vs \"" + b.getBook().getTitle() + "\"");
        io.println("  Method              : " + r.getMethod().getDisplayName());
        io.println("  How it works        : " + r.getMethod().getIdea());
        io.println("  Text length         : n = " + r.getTotalLength() + " (both books, normalized)");
        if (r.getMethod() == SimilarityMethod.SUFFIX_ARRAY) {
            io.println("  Suffix array (SA-IS): " + ms(r.getBuildMillis()) + " ms   O(n)");
            if (r.getNaiveMillis() >= 0) {
                io.println("  Naive suffix sort   : " + ms(r.getNaiveMillis()) + " ms   O(n^2 log n) worst case (for comparison)");
            }
            io.println("  LCP array (Kasai)   : " + ms(r.getLcpMillis()) + " ms   O(n)");
            io.println("  Common-text scan    : " + ms(r.getMatchMillis()) + " ms   O(n)");
        } else {
            io.println("  Suffix tree (Ukkonen): " + ms(r.getBuildMillis()) + " ms   O(n)");
            io.println("  Matching walk        : " + ms(r.getMatchMillis()) + " ms   O(length of shorter book)");
        }
        printCommonText(r, a, b);
    }

    private void runBoth(ComparisonSession session, BookText a, BookText b, int minLength) throws ServiceException {
        SimilarityResult sa = books.compareBooks(session, SimilarityMethod.SUFFIX_ARRAY, minLength);
        SimilarityResult st = books.compareBooks(session, SimilarityMethod.SUFFIX_TREE, minLength);
        io.header("Results: \"" + a.getBook().getTitle() + "\" vs \"" + b.getBook().getTitle() + "\"");
        io.println("  Text length: n = " + sa.getTotalLength() + " (both books, normalized)");
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] { "Suffix Array (SA-IS)", ms(sa.getBuildMillis()), "O(n)" });
        if (sa.getNaiveMillis() >= 0) {
            rows.add(new String[] { "Naive suffix sort", ms(sa.getNaiveMillis()), "O(n^2 log n) worst" });
        }
        rows.add(new String[] { "LCP array (Kasai)", ms(sa.getLcpMillis()), "O(n)" });
        rows.add(new String[] { "Common text via LCP", ms(sa.getMatchMillis()), "O(n)" });
        rows.add(new String[] { "Suffix Tree (Ukkonen)", ms(st.getBuildMillis()), "O(n)" });
        rows.add(new String[] { "Common text via tree", ms(st.getMatchMillis()), "O(shorter book)" });
        io.table(new String[] { "Step", "Time (ms)", "Complexity" }, rows);

        boolean same = sa.getCoveredCharacters() == st.getCoveredCharacters()
                && sa.getPhrases().size() == st.getPhrases().size();
        for (int i = 0; same && i < sa.getPhrases().size(); i++) {
            same = sa.getPhrases().get(i).getText().equals(st.getPhrases().get(i).getText());
        }
        if (same) {
            io.success("Both methods found the same common text (" + sa.getPhrases().size()
                    + " phrases, similarity " + pct(sa.getSimilarityPercent()) + ").");
        } else {
            io.error("The methods disagree: suffix array " + pct(sa.getSimilarityPercent()) + ", suffix tree "
                    + pct(st.getSimilarityPercent()) + ".");
        }
        printCommonText(sa, a, b);
    }

    private void printCommonText(SimilarityResult r, BookText a, BookText b) {
        NormalizedText na = new NormalizedText(a.getOriginalText());
        NormalizedText nb = new NormalizedText(b.getOriginalText());
        io.blank();
        io.println("  Similarity          : " + pct(r.getSimilarityPercent()) + "  (" + r.getCoveredCharacters()
                + " of " + r.getShorterLength() + " characters of the shorter book are in common phrases)");
        CommonPhrase longest = r.getLongest();
        if (longest == null) {
            io.println("  Longest common phrase: none");
            io.info("No common phrases of at least " + r.getMinLength()
                    + " characters. Try a smaller minimum length to see shorter matches.");
            return;
        }
        io.println("  Longest common phrase: \"" + longest.getText() + "\" (" + longest.getText().length() + " chars)");
        io.blank();
        io.println("  Common text (" + r.getPhrases().size() + " phrase" + (r.getPhrases().size() == 1 ? "" : "s")
                + ", min " + r.getMinLength() + " chars):");
        int shown = 0;
        for (CommonPhrase p : r.getPhrases()) {
            if (shown == MAX_PHRASES_SHOWN) {
                io.println("   ... and " + (r.getPhrases().size() - shown) + " more");
                break;
            }
            int lineA = a.lineOf(na.originalIndex(p.getPositionInFirst()));
            int lineB = b.lineOf(nb.originalIndex(p.getPositionInSecond()));
            io.println(String.format("  %2d. %-40s Book 1: line %-4d Book 2: line %d", shown + 1,
                    "\"" + shorten(p.getText(), 38) + "\"", lineA, lineB));
            shown++;
        }
    }

    // ------------------------------------------------------------------ phrase lookup

    private void findPhrases(ComparisonSession session, BookText a, BookText b, List<SimilarityMethod> used)
            throws ServiceException {
        NormalizedText na = new NormalizedText(a.getOriginalText());
        NormalizedText nb = new NormalizedText(b.getOriginalText());
        while (true) {
            io.blank();
            String phrase = io.readLine("Find a phrase in both books (Enter = skip): ");
            if (phrase.isBlank()) {
                return;
            }
            for (SimilarityMethod m : used) {
                PhraseLookup l = books.findPhrase(session, phrase, m);
                String how = m == SimilarityMethod.SUFFIX_ARRAY
                        ? "Suffix Array (binary search)  O(m log n)"
                        : "Suffix Tree (walk from root)  O(m)";
                io.println("  " + how + " : " + ms(l.millis()) + " ms");
                io.println("     Book 1: " + describe(l.countInFirst, l.firstPositionInFirst, na, a));
                io.println("     Book 2: " + describe(l.countInSecond, l.firstPositionInSecond, nb, b));
            }
        }
    }

    private static String describe(int count, int firstPos, NormalizedText norm, BookText text) {
        if (count == 0) {
            return "not found";
        }
        return count + " time(s), first at line " + text.lineOf(norm.originalIndex(firstPos));
    }

    // ------------------------------------------------------------------ formatting

    private static String ms(double millis) {
        return String.format(Locale.ROOT, "%.3f", millis);
    }

    private static String pct(double p) {
        return String.format(Locale.ROOT, "%.1f %%", p);
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
