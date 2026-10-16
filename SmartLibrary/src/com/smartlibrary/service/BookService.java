package com.smartlibrary.service;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.smartlibrary.auth.AuthException;
import com.smartlibrary.auth.AuthService;
import com.smartlibrary.auth.Role;
import com.smartlibrary.data.BookCSV;
import com.smartlibrary.data.SearchLogCSV;
import com.smartlibrary.data.UsageLogCSV;
import com.smartlibrary.dsa.Algorithm;
import com.smartlibrary.dsa.ComparisonSession;
import com.smartlibrary.dsa.MatchResult;
import com.smartlibrary.dsa.NormalizedText;
import com.smartlibrary.dsa.RandomizedQuickSort;
import com.smartlibrary.dsa.SequenceAlignment;
import com.smartlibrary.dsa.SimilarityMethod;
import com.smartlibrary.dsa.SimilarityResult;
import com.smartlibrary.dsa.SpellingCorrector;
import com.smartlibrary.dsa.StringMatcher;
import com.smartlibrary.dsa.TextUtil;
import com.smartlibrary.model.Book;
import com.smartlibrary.model.User;

/**
 * Book import, the two-step search (Module 1), document comparison (Module 2),
 * spelling correction, word alignment and search statistics (Module 3):
 * <ol>
 *   <li>{@link #findBooks}: linear scan over the catalogue; each book's title, author and category
 *       are searched with the chosen string-matching algorithm.</li>
 *   <li>{@link #searchInBook}: the chosen algorithm searches the full text of one selected book.</li>
 * </ol>
 * Any logged-in user may search; only Librarians and Admins may import books.
 */
public class BookService {

    private static final int MAX_FIELD = 150;

    private final BookCSV store;
    private final AuthService auth;
    private final SearchLogCSV searchLog;
    private final UsageLogCSV usageLog;
    private final Random random = new Random();

    public BookService(BookCSV store, AuthService auth) {
        this(store, auth, new SearchLogCSV(store.getDataDir()), new UsageLogCSV(store.getDataDir()));
    }

    public BookService(BookCSV store, AuthService auth, SearchLogCSV searchLog, UsageLogCSV usageLog) {
        this.store = store;
        this.auth = auth;
        this.searchLog = searchLog;
        this.usageLog = usageLog;
    }

    /** Adds an event to the usage stream (Module 6). Failures are ignored: logging is optional. */
    private void logUsage(String action, String detail) {
        try {
            usageLog.append(requireLoggedIn().getUsername(), action, detail);
        } catch (IOException | ServiceException ignored) {
            // never let logging break the feature
        }
    }

    // ================================================================ catalogue

    public List<Book> listBooks() throws ServiceException {
        requireLoggedIn();
        try {
            return store.loadAll();
        } catch (IOException e) {
            throw storage(e);
        }
    }

    /**
     * Imports a .txt file as a new book. Librarian or Admin only.
     *
     * @param sourcePath path typed by the user; surrounding quotes (from Windows "Copy as path") are removed
     */
    public Book importBook(String sourcePath, String title, String author, String category) throws ServiceException {
        User actor = requireLoggedIn();
        if (actor.getRole() != Role.LIBRARIAN && actor.getRole() != Role.ADMIN) {
            throw new ServiceException(ServiceException.Kind.ACCESS_DENIED,
                    "Access denied: only a Librarian or Admin can import books.");
        }
        String cleanTitle = requireField(title, "Title");
        String cleanAuthor = requireField(author, "Author");
        String cleanCategory = requireField(category, "Category");
        Path source = toPath(sourcePath);
        synchronized (store) {
            for (Book b : listBooks()) {
                if (b.getTitle().equalsIgnoreCase(cleanTitle) && b.getAuthor().equalsIgnoreCase(cleanAuthor)) {
                    throw new ServiceException(ServiceException.Kind.DUPLICATE,
                            "'" + cleanTitle + "' by " + cleanAuthor + " is already in the library (ID "
                                    + b.getBookId() + ").");
                }
            }
            try {
                return store.importText(source, cleanTitle, cleanAuthor, cleanCategory);
            } catch (IllegalArgumentException e) {
                throw new ServiceException(ServiceException.Kind.VALIDATION, e.getMessage());
            } catch (IOException e) {
                throw storage(e);
            }
        }
    }

    // ================================================================ step 1: find a book

    /**
     * Linear search over all books: the algorithm searches "title | author | category" of each book.
     * Books are ranked by number of keyword matches (most first).
     */
    public BookSearch findBooks(String query, Algorithm algorithm) throws ServiceException {
        scanBooks(query, algorithm); // warm-up run, so the timing below is not dominated by JIT start-up
        return scanBooks(query, algorithm);
    }

    private BookSearch scanBooks(String query, Algorithm algorithm) throws ServiceException {
        List<String> keywords = TextUtil.parseKeywords(query);
        if (keywords.isEmpty()) {
            throw new ServiceException(ServiceException.Kind.VALIDATION, "Please enter something to search for.");
        }
        List<Book> all = listBooks();
        StringMatcher matcher = algorithm.newMatcher();
        List<BookHit> hits = new ArrayList<>();
        long comparisons = 0;
        long nanos = 0;
        long chars = 0;
        for (Book book : all) { // linear search: O(B) books
            String searchable = TextUtil.toLowerSameLength(
                    book.getTitle() + " | " + book.getAuthor() + " | " + book.getCategory());
            long start = System.nanoTime();
            MatchResult r = matcher.search(searchable, keywords);
            nanos += System.nanoTime() - start;
            comparisons += r.getComparisons();
            chars += searchable.length();
            if (r.getMatchCount() > 0) {
                hits.add(new BookHit(book, r.getMatchCount()));
            }
        }
        // rank: most matches first (Randomized QuickSort, Module 6)
        RandomizedQuickSort.sort(hits, (a, b) -> a.getMatchCount() != b.getMatchCount()
                ? Integer.compare(b.getMatchCount(), a.getMatchCount())
                : a.getBook().getTitle().compareToIgnoreCase(b.getBook().getTitle()), random);
        return new BookSearch(algorithm, keywords, hits, similarBooks(all, hits), comparisons, nanos, chars,
                all.size());
    }

    /** Runs {@link #findBooks} with every algorithm (after one untimed warm-up round). */
    public List<BookSearch> findBooksWithAll(String query) throws ServiceException {
        for (Algorithm a : Algorithm.values()) {
            findBooks(query, a); // warm-up so the JIT does not penalise the first algorithm
        }
        List<BookSearch> results = new ArrayList<>();
        for (Algorithm a : Algorithm.values()) {
            results.add(findBooks(query, a));
        }
        return results;
    }

    /** Books in the same category as any found book, excluding the found books themselves. */
    private static List<Book> similarBooks(List<Book> all, List<BookHit> hits) {
        Set<String> categories = new LinkedHashSet<>();
        Set<Long> found = new LinkedHashSet<>();
        for (BookHit h : hits) {
            categories.add(h.getBook().getCategory().toLowerCase(Locale.ROOT));
            found.add(h.getBook().getBookId());
        }
        List<Book> similar = new ArrayList<>();
        for (Book b : all) {
            if (!found.contains(b.getBookId()) && categories.contains(b.getCategory().toLowerCase(Locale.ROOT))) {
                similar.add(b);
            }
        }
        return similar;
    }

    // ================================================================ step 2: search inside a book

    /** Loads a book's text, ready to be searched, and records the event in the usage stream. */
    public BookText openBook(Book book) throws ServiceException {
        BookText t = openBookQuietly(book);
        logUsage("OPEN_BOOK", book.getTitle());
        return t;
    }

    /** Loads a book's text without recording usage (used by analytics). */
    public BookText openBookQuietly(Book book) throws ServiceException {
        requireLoggedIn();
        try {
            return new BookText(book, store.readBookText(book));
        } catch (IOException e) {
            throw storage(e);
        }
    }

    /** Searches the selected book's full text with one algorithm (one untimed warm-up run first). */
    public MatchResult searchInBook(BookText bookText, String patternInput, Algorithm algorithm)
            throws ServiceException {
        requireLoggedIn();
        List<String> keywords = TextUtil.parseKeywords(patternInput);
        if (keywords.isEmpty()) {
            throw new ServiceException(ServiceException.Kind.VALIDATION, "Please enter a pattern to search for.");
        }
        StringMatcher matcher = algorithm.newMatcher();
        matcher.search(bookText.getSearchText(), keywords); // warm-up
        long start = System.nanoTime();
        MatchResult result = matcher.search(bookText.getSearchText(), keywords);
        result.setElapsedNanos(System.nanoTime() - start);
        return result;
    }

    /** Runs all four algorithms on the same book and pattern. */
    public List<MatchResult> searchInBookWithAll(BookText bookText, String patternInput) throws ServiceException {
        for (Algorithm a : Algorithm.values()) {
            searchInBook(bookText, patternInput, a); // warm-up round
        }
        List<MatchResult> results = new ArrayList<>();
        for (Algorithm a : Algorithm.values()) {
            results.add(searchInBook(bookText, patternInput, a));
        }
        return results;
    }

    // ================================================================ Module 2: document similarity

    /** Prepares two books for comparison (normalizes their text) and records the event. */
    public ComparisonSession startComparison(BookText first, BookText second) throws ServiceException {
        ComparisonSession s = startComparisonQuietly(first, second);
        logUsage("COMPARE", first.getBook().getTitle() + " vs " + second.getBook().getTitle());
        return s;
    }

    /** Prepares two books for comparison without recording usage (used by analytics). */
    public ComparisonSession startComparisonQuietly(BookText first, BookText second) throws ServiceException {
        requireLoggedIn();
        if (first.getBook().getBookId() == second.getBook().getBookId()) {
            throw new ServiceException(ServiceException.Kind.VALIDATION, "Please choose two different books.");
        }
        try {
            return new ComparisonSession(new NormalizedText(first.getOriginalText()),
                    new NormalizedText(second.getOriginalText()));
        } catch (IllegalArgumentException e) {
            throw new ServiceException(ServiceException.Kind.VALIDATION, e.getMessage());
        }
    }

    /** Finds the common text of the two books with the chosen method. */
    public SimilarityResult compareBooks(ComparisonSession session, SimilarityMethod method, int minLength)
            throws ServiceException {
        requireLoggedIn();
        if (minLength < 4 || minLength > 200) {
            throw new ServiceException(ServiceException.Kind.VALIDATION,
                    "Minimum phrase length must be between 4 and 200 characters.");
        }
        return session.compare(method, minLength);
    }

    /** Looks up a phrase in both books with the suffix array or the suffix trees. */
    public ComparisonSession.PhraseLookup findPhrase(ComparisonSession session, String phrase,
                                                     SimilarityMethod method) throws ServiceException {
        requireLoggedIn();
        if (NormalizedText.normalizePhrase(phrase == null ? "" : phrase).isEmpty()) {
            throw new ServiceException(ServiceException.Kind.VALIDATION, "Please enter a phrase.");
        }
        return method == SimilarityMethod.SUFFIX_ARRAY ? session.findWithSuffixArray(phrase)
                : session.findWithSuffixTree(phrase);
    }

    // ================================================================ Module 3: dynamic programming

    /** "Did you mean?" for Step 1: vocabulary = words of all titles, authors and categories. */
    public SpellingCorrector titleCorrector() throws ServiceException {
        Map<String, Integer> vocab = new HashMap<>();
        for (Book b : listBooks()) {
            SpellingCorrector.vocabularyOf(b.getTitle() + " " + b.getAuthor() + " " + b.getCategory(), vocab);
        }
        return new SpellingCorrector(vocab);
    }

    /** "Did you mean?" for Step 2: vocabulary = words of the selected book. */
    public SpellingCorrector bookCorrector(BookText text) throws ServiceException {
        requireLoggedIn();
        return new SpellingCorrector(SpellingCorrector.vocabularyOf(text.getOriginalText(), new HashMap<>()));
    }

    /** Word-level alignment of two books: Smith-Waterman (local) or Needleman-Wunsch (global). */
    public SequenceAlignment.Result alignBooks(BookText first, BookText second, boolean local)
            throws ServiceException {
        requireLoggedIn();
        List<SequenceAlignment.Word> a = SequenceAlignment.words(new NormalizedText(first.getOriginalText()));
        List<SequenceAlignment.Word> b = SequenceAlignment.words(new NormalizedText(second.getOriginalText()));
        return local ? SequenceAlignment.smithWaterman(a, b) : SequenceAlignment.needlemanWunsch(a, b);
    }

    /** Counts a successful search, for the Optimal BST statistics. Failures to save are ignored. */
    public void recordSearch(List<String> keywords) throws ServiceException {
        requireLoggedIn();
        try {
            searchLog.record(keywords);
        } catch (IOException e) {
            // statistics are optional: never let them break a search
        }
        logUsage("SEARCH", String.join(", ", keywords));
    }

    /** Keyword search counts. Librarian or Admin only. */
    public Map<String, Integer> searchFrequencies() throws ServiceException {
        User actor = requireLoggedIn();
        if (actor.getRole() != Role.LIBRARIAN && actor.getRole() != Role.ADMIN) {
            throw new ServiceException(ServiceException.Kind.ACCESS_DENIED,
                    "Access denied: only a Librarian or Admin can view search statistics.");
        }
        try {
            return searchLog.load();
        } catch (IOException e) {
            throw storage(e);
        }
    }

    // ================================================================ helpers

    private User requireLoggedIn() throws ServiceException {
        try {
            return auth.requireCurrentUser();
        } catch (AuthException e) {
            ServiceException.Kind kind = e.getReason() == AuthException.Reason.STORAGE_ERROR
                    ? ServiceException.Kind.STORAGE : ServiceException.Kind.ACCESS_DENIED;
            throw new ServiceException(kind, e.getMessage());
        }
    }

    private static String requireField(String value, String label) throws ServiceException {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty()) {
            throw new ServiceException(ServiceException.Kind.VALIDATION, label + " is required.");
        }
        if (v.length() > MAX_FIELD) {
            throw new ServiceException(ServiceException.Kind.VALIDATION,
                    label + " must be at most " + MAX_FIELD + " characters.");
        }
        return v;
    }

    private static Path toPath(String input) throws ServiceException {
        String s = input == null ? "" : input.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1).trim();
        }
        if (s.isEmpty()) {
            throw new ServiceException(ServiceException.Kind.VALIDATION, "File path is required.");
        }
        try {
            return Paths.get(s);
        } catch (InvalidPathException e) {
            throw new ServiceException(ServiceException.Kind.VALIDATION, "Invalid file path: " + s);
        }
    }

    private static ServiceException storage(IOException e) {
        return new ServiceException(ServiceException.Kind.STORAGE, "Could not access library data: " + e.getMessage());
    }

    // ================================================================ result types

    /** A book found in step 1 with its number of keyword matches. */
    public static final class BookHit {
        private final Book book;
        private final int matchCount;

        BookHit(Book book, int matchCount) {
            this.book = book;
            this.matchCount = matchCount;
        }

        public Book getBook() { return book; }
        public int getMatchCount() { return matchCount; }
    }

    /** Result of step 1 for one algorithm. */
    public static final class BookSearch {
        private final Algorithm algorithm;
        private final List<String> keywords;
        private final List<BookHit> hits;
        private final List<Book> similar;
        private final long comparisons;
        private final long nanos;
        private final long charactersSearched;
        private final int booksScanned;

        BookSearch(Algorithm algorithm, List<String> keywords, List<BookHit> hits, List<Book> similar,
                   long comparisons, long nanos, long charactersSearched, int booksScanned) {
            this.algorithm = algorithm;
            this.keywords = Collections.unmodifiableList(keywords);
            this.hits = Collections.unmodifiableList(hits);
            this.similar = Collections.unmodifiableList(similar);
            this.comparisons = comparisons;
            this.nanos = nanos;
            this.charactersSearched = charactersSearched;
            this.booksScanned = booksScanned;
        }

        public Algorithm getAlgorithm() { return algorithm; }
        public List<String> getKeywords() { return keywords; }
        public List<BookHit> getHits() { return hits; }
        public List<Book> getSimilar() { return similar; }
        public long getComparisons() { return comparisons; }
        public double getElapsedMillis() { return nanos / 1_000_000.0; }
        public long getCharactersSearched() { return charactersSearched; }
        public int getBooksScanned() { return booksScanned; }

        public int getTotalMatches() {
            int total = 0;
            for (BookHit h : hits) {
                total += h.getMatchCount();
            }
            return total;
        }
    }

    /** A loaded book: original text for display, lower-case text for searching, and a line index. */
    public static final class BookText {
        private final Book book;
        private final String original;
        private final String searchText;
        private final int[] lineStarts;

        BookText(Book book, String original) {
            this.book = book;
            this.original = original;
            this.searchText = TextUtil.toLowerSameLength(original);
            this.lineStarts = TextUtil.lineStarts(original);
        }

        public Book getBook() { return book; }
        public String getOriginalText() { return original; }
        public String getSearchText() { return searchText; }
        public int getLength() { return original.length(); }
        public int getLineCount() { return lineStarts.length; }

        /** 1-based line number of a text position (binary search). */
        public int lineOf(int position) {
            return TextUtil.lineIndexOf(lineStarts, position) + 1;
        }

        /** 1-based column of a text position. */
        public int columnOf(int position) {
            return position - lineStarts[TextUtil.lineIndexOf(lineStarts, position)] + 1;
        }

        /** The original text of a 1-based line, without its line ending. */
        public String lineText(int lineNumber) {
            int start = lineStarts[lineNumber - 1];
            int end = lineNumber < lineStarts.length ? lineStarts[lineNumber] : original.length();
            String line = original.substring(start, end);
            return line.replace("\r", "").replace("\n", "");
        }
    }
}
