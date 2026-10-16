package com.smartlibrary.console;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.smartlibrary.dsa.Algorithm;
import com.smartlibrary.dsa.Match;
import com.smartlibrary.dsa.MatchResult;
import com.smartlibrary.dsa.SpellingCorrector;
import com.smartlibrary.model.Book;
import com.smartlibrary.service.BookService;
import com.smartlibrary.service.BookService.BookHit;
import com.smartlibrary.service.BookService.BookSearch;
import com.smartlibrary.service.BookService.BookText;
import com.smartlibrary.service.ServiceException;

/**
 * Two-step search, both steps using the string-matching algorithm the user picks:
 * <ol>
 *   <li>Find a book by title / author / category (similar books from the same category are listed too).</li>
 *   <li>Search for a pattern inside the selected book's text.</li>
 * </ol>
 * When nothing is found, "Did you mean?" suggests corrections using edit distance (Module 3).
 * Successful searches are counted for the Optimal BST search statistics (Module 3).
 */
public class SearchConsole {

    private static final int RUN_ALL = 5;
    private static final int MAX_MATCHES_SHOWN = 15;

    private final ConsoleIO io;
    private final BookService books;

    public SearchConsole(ConsoleIO io, BookService books) {
        this.io = io;
        this.books = books;
    }

    public void run() throws ServiceException {
        while (true) {
            io.header("Search Books - Step 1: Find a book");
            List<Book> all = books.listBooks();
            if (all.isEmpty()) {
                io.info("No books in the library yet. A Librarian can import .txt books from Manage Books.");
                io.pause();
                return;
            }
            io.info(all.size() + " book(s) in the library. Separate several words with commas, e.g. data, algorithms");
            String query = io.readLine("Search title / author / category (Enter = list all, 0 = back): ");
            if (query.equals("0")) {
                return;
            }

            List<Book> choices = new ArrayList<>();
            if (query.isEmpty()) {
                choices.addAll(all);
                printBookList("All books", choices, null);
            } else {
                int choice = chooseAlgorithm("find the book");
                if (choice == 0) {
                    continue;
                }
                BookSearch search = runFindBooks(query, choice);
                if (search.getHits().isEmpty()) {
                    io.info("No book matches '" + query + "'.");
                    String fixed = offerCorrection(books.titleCorrector(), query);
                    if (fixed != null) {
                        query = fixed;
                        search = runFindBooks(query, choice);
                    }
                    if (search.getHits().isEmpty()) {
                        if (fixed != null) {
                            io.info("No book matches '" + query + "' either.");
                        }
                        io.pause();
                        continue;
                    }
                }
                books.recordSearch(search.getKeywords());
                List<Integer> counts = new ArrayList<>();
                for (BookHit h : search.getHits()) {
                    choices.add(h.getBook());
                    counts.add(h.getMatchCount());
                }
                printBookList("Books found", choices, counts);
                if (!search.getSimilar().isEmpty()) {
                    io.blank();
                    io.println("  Similar books (same category):");
                    for (Book b : search.getSimilar()) {
                        choices.add(b);
                        io.println(String.format("  %2d. %-38s %-20s %s", choices.size(), shorten(b.getTitle(), 38),
                                shorten(b.getAuthor(), 20), b.getCategory()));
                    }
                }
            }

            io.blank();
            int selected = io.readChoice("Select book number (0 = new search): ", 0, choices.size());
            if (selected == 0) {
                continue;
            }
            searchInsideBook(choices.get(selected - 1));
        }
    }

    // ------------------------------------------------------------------ browse

    /**
     * Lists every book in the library, grouped by category and sorted by title, with the total count.
     * Display only: no searching from this screen.
     */
    public void browseAll() throws ServiceException {
        List<Book> all = new ArrayList<>(books.listBooks());
        io.header("Books Available in the Library");
        if (all.isEmpty()) {
            io.info("No books in the library yet. A Librarian can import .txt books from Manage Books.");
            io.pause();
            return;
        }
        all.sort((a, b) -> {
            int c = a.getCategory().compareToIgnoreCase(b.getCategory());
            return c != 0 ? c : a.getTitle().compareToIgnoreCase(b.getTitle());
        });

        List<String[]> rows = new ArrayList<>();
        List<String> categories = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            Book b = all.get(i);
            rows.add(new String[] { Integer.toString(i + 1), b.getTitle(), b.getAuthor(), b.getCategory() });
            if (categories.stream().noneMatch(c -> c.equalsIgnoreCase(b.getCategory()))) {
                categories.add(b.getCategory());
            }
        }
        io.table(new String[] { "#", "Title", "Author", "Category" }, rows);
        io.info("Total books available: " + all.size());
        io.info("Categories (" + categories.size() + "): " + String.join(", ", categories));
        io.pause();
    }

    // ------------------------------------------------------------------ step 1

    private BookSearch runFindBooks(String query, int choice) throws ServiceException {
        if (choice == RUN_ALL) {
            List<BookSearch> results = books.findBooksWithAll(query);
            io.header("Step 1 - All algorithms on book titles / authors / categories");
            List<String[]> rows = new ArrayList<>();
            for (BookSearch s : results) {
                rows.add(new String[] { s.getAlgorithm().getDisplayName(), Integer.toString(s.getTotalMatches()),
                        Integer.toString(s.getHits().size()), ms(s.getElapsedMillis()),
                        Long.toString(s.getComparisons()), s.getAlgorithm().getComplexity() });
            }
            io.table(new String[] { "Algorithm", "Matches", "Books", "Time (ms)", "Comparisons", "Complexity" }, rows);
            checkSameCount(results.stream().map(BookSearch::getTotalMatches).toList());
            return results.get(0);
        }
        BookSearch s = books.findBooks(query, Algorithm.fromMenuChoice(choice));
        io.header("Step 1 - Results using " + s.getAlgorithm().getDisplayName());
        io.println("  Linear search over " + s.getBooksScanned() + " book(s): O(B) books x "
                + s.getAlgorithm().getComplexity() + " per book");
        io.println("  Characters searched : " + s.getCharactersSearched());
        io.println("  Time taken          : " + ms(s.getElapsedMillis()) + " ms");
        io.println("  Comparisons         : " + s.getComparisons());
        return s;
    }

    private void printBookList(String title, List<Book> list, List<Integer> counts) {
        io.blank();
        io.println("  " + title + ":");
        for (int i = 0; i < list.size(); i++) {
            Book b = list.get(i);
            String suffix = counts == null ? "" : "  (" + counts.get(i) + " match" + (counts.get(i) == 1 ? "" : "es") + ")";
            io.println(String.format("  %2d. %-38s %-20s %s%s", i + 1, shorten(b.getTitle(), 38),
                    shorten(b.getAuthor(), 20), b.getCategory(), suffix));
        }
    }

    // ------------------------------------------------------------------ step 2

    private void searchInsideBook(Book book) throws ServiceException {
        BookText text = books.openBook(book);
        while (true) {
            io.header("Step 2: Search inside \"" + book.getTitle() + "\"");
            io.info("Book size: " + text.getLength() + " characters, " + text.getLineCount() + " lines.");
            io.info("Search is case-insensitive. Separate several patterns with commas, e.g. tree, graph");
            String pattern = io.readLine("Pattern to find (0 = back): ");
            if (pattern.equals("0")) {
                return;
            }
            if (pattern.isBlank()) {
                io.error("Please enter a pattern.");
                continue;
            }
            int choice = chooseAlgorithm("search inside the book");
            if (choice == 0) {
                continue;
            }

            MatchResult shown = runInside(text, pattern, choice);
            printMatches(text, shown);
            if (anyPatternMissing(shown)) {
                String fixed = offerCorrection(books.bookCorrector(text), pattern);
                if (fixed != null) {
                    shown = runInside(text, fixed, choice);
                    printMatches(text, shown);
                }
            }
            if (shown.getMatchCount() > 0) {
                books.recordSearch(shown.getPatterns());
            }
            if (!io.confirm("\nSearch again in this book?")) {
                return;
            }
        }
    }

    /** Runs the chosen algorithm (or all of them) on the book and prints the summary. */
    private MatchResult runInside(BookText text, String pattern, int choice) throws ServiceException {
        Book book = text.getBook();
        if (choice == RUN_ALL) {
            List<MatchResult> results = books.searchInBookWithAll(text, pattern);
            io.header("Step 2 - All algorithms on \"" + book.getTitle() + "\"");
            List<String[]> rows = new ArrayList<>();
            for (MatchResult r : results) {
                rows.add(new String[] { r.getAlgorithm().getDisplayName(), Integer.toString(r.getMatchCount()),
                        ms(r.getElapsedMillis()), Long.toString(r.getComparisons()), complexityFor(r) });
            }
            io.table(new String[] { "Algorithm", "Matches", "Time (ms)", "Comparisons", "Complexity" }, rows);
            checkSameCount(results.stream().map(MatchResult::getMatchCount).toList());
            return results.get(0);
        }
        MatchResult r = books.searchInBook(text, pattern, Algorithm.fromMenuChoice(choice));
        printSummary(text, r);
        return r;
    }

    private static boolean anyPatternMissing(MatchResult r) {
        for (String p : r.getPatterns()) {
            if (r.countFor(p) == 0) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ Module 3: did you mean?

    /**
     * Shows spelling suggestions (Damerau-Levenshtein / Levenshtein edit distance) and returns the
     * chosen corrected query, or null.
     */
    private String offerCorrection(SpellingCorrector corrector, String query) {
        List<SpellingCorrector.Suggestion> list = corrector.suggest(query, 3);
        if (list.isEmpty()) {
            return null;
        }
        io.blank();
        io.println("  Did you mean?   (edit distance by dynamic programming, O(n x m) per word pair)");
        List<String[]> rows = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            SpellingCorrector.Suggestion s = list.get(i);
            rows.add(new String[] { Integer.toString(i + 1), s.getText(), Integer.toString(s.getDamerau()),
                    Integer.toString(s.getLevenshtein()) });
        }
        io.table(new String[] { "#", "Suggestion", "Damerau-Levenshtein", "Levenshtein" }, rows);
        io.info("Compared with " + corrector.vocabularySize() + " known words.");
        int c = io.readChoice("Use suggestion number (0 = no): ", 0, list.size());
        return c == 0 ? null : list.get(c - 1).getText();
    }

    private void printSummary(BookText text, MatchResult r) {
        int m = 0;
        for (String p : r.getPatterns()) {
            m += p.length();
        }
        io.header("Step 2 - Results using " + r.getAlgorithm().getDisplayName());
        io.println("  How it works    : " + r.getAlgorithm().getIdea());
        io.println("  Time complexity : " + complexityFor(r));
        io.println("                    n = " + text.getLength() + " (text length), m = " + m
                + " (pattern length" + (r.getPatterns().size() > 1 ? "s, total" : "") + ")");
        if (r.getPatterns().size() > 1 && !r.getAlgorithm().isMultiPattern()) {
            io.println("                    k = " + r.getPatterns().size()
                    + " patterns: this algorithm scans the text once per pattern");
        }
        io.println("  Time taken      : " + ms(r.getElapsedMillis()) + " ms");
        io.println("  Comparisons     : " + r.getComparisons());
    }

    private void printMatches(BookText text, MatchResult r) {
        io.blank();
        for (String p : r.getPatterns()) {
            io.println("  Pattern \"" + p + "\" found " + r.countFor(p) + " time(s) in \"" + text.getBook().getTitle() + "\"");
        }
        if (r.getMatchCount() == 0) {
            return;
        }
        io.blank();
        int shown = 0;
        for (Match match : r.getMatches()) {
            if (shown == MAX_MATCHES_SHOWN) {
                io.println("  ... and " + (r.getMatchCount() - shown) + " more");
                break;
            }
            int line = text.lineOf(match.getPosition());
            int col = text.columnOf(match.getPosition());
            String where = String.format("line %d, col %d", line, col);
            String tag = r.getPatterns().size() > 1 ? "[" + match.getPattern() + "] " : "";
            io.println(String.format("  %-18s: %s\"%s\"", where, tag, snippet(text.lineText(line), col - 1,
                    match.getPattern().length())));
            shown++;
        }
    }

    // ------------------------------------------------------------------ shared

    /** Algorithm menu used in both steps. The switch on the number is in {@link Algorithm#fromMenuChoice}. */
    private int chooseAlgorithm(String purpose) {
        io.blank();
        io.println("Choose algorithm to " + purpose + ":");
        int i = 1;
        for (Algorithm a : Algorithm.values()) {
            io.println(String.format("  %d. %-26s %s", i++, a.getDisplayName(), a.getComplexity()));
        }
        io.println("  5. Run ALL and compare");
        io.println("  0. Back");
        return io.readChoice("Choose: ", 0, RUN_ALL);
    }

    private static String complexityFor(MatchResult r) {
        int k = r.getPatterns().size();
        if (k > 1 && !r.getAlgorithm().isMultiPattern()) {
            return "O(k*(n + m)), k = " + k; // one pass over the text per pattern
        }
        return r.getAlgorithm().getComplexity();
    }

    private void checkSameCount(List<Integer> counts) {
        boolean same = counts.stream().distinct().count() == 1;
        if (same) {
            io.success("All algorithms found the same number of matches (" + counts.get(0) + ").");
        } else {
            io.error("Algorithms disagree on the number of matches: " + counts);
        }
    }

    /** Part of the line around the match, at most 70 characters. */
    private static String snippet(String line, int start, int length) {
        int width = 70;
        if (line.length() <= width) {
            return line.trim();
        }
        int from = Math.max(0, Math.min(start - 25, line.length() - width));
        int to = Math.min(line.length(), from + width);
        return (from > 0 ? "..." : "") + line.substring(from, to).trim() + (to < line.length() ? "..." : "");
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    private static String ms(double millis) {
        return String.format(Locale.ROOT, "%.3f", millis);
    }
}
