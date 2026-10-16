package com.smartlibrary.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;

import com.smartlibrary.auth.AuthException;
import com.smartlibrary.auth.AuthService;
import com.smartlibrary.auth.Role;
import com.smartlibrary.data.UsageLogCSV;
import com.smartlibrary.dsa.ComparisonSession;
import com.smartlibrary.dsa.ConflictGraph;
import com.smartlibrary.dsa.ParallelStats;
import com.smartlibrary.dsa.RandomizedQuickSort;
import com.smartlibrary.dsa.ReservoirSampling;
import com.smartlibrary.dsa.SimilarityMethod;
import com.smartlibrary.model.Book;
import com.smartlibrary.model.User;
import com.smartlibrary.service.BookService.BookText;

/**
 * Module 5 (document conflict graph: vertex cover, independent set, clique) and
 * Module 6 (randomized QuickSort ranking, reservoir sampling of the usage stream,
 * parallel reduce and Blelloch scan statistics).
 */
public class AnalyticsService {

    /** Largest number of books compared pairwise for the conflict graph. */
    public static final int MAX_CONFLICT_BOOKS = 20;
    private static final int MIN_PHRASE = 10;

    private final BookService books;
    private final UsageLogCSV usage;
    private final AuthService auth;

    public AnalyticsService(BookService books, UsageLogCSV usage, AuthService auth) {
        this.books = books;
        this.usage = usage;
        this.auth = auth;
    }

    // ================================================================ Module 5: conflict graph

    /** Books, their pairwise similarity and the resulting conflict graph. */
    public static final class ConflictAnalysis {
        public final List<Book> books;
        public final double[][] similarity;
        public final double threshold;
        public final ConflictGraph graph;
        public final long nanos;

        ConflictAnalysis(List<Book> books, double[][] similarity, double threshold, ConflictGraph graph, long nanos) {
            this.books = Collections.unmodifiableList(books);
            this.similarity = similarity;
            this.threshold = threshold;
            this.graph = graph;
            this.nanos = nanos;
        }

        public double millis() {
            return nanos / 1e6;
        }
    }

    /**
     * Compares every pair of books with the Module 2 suffix tree method and joins two books with an
     * edge when their similarity is at least {@code thresholdPercent}.
     */
    public ConflictAnalysis conflictGraph(double thresholdPercent) throws ServiceException {
        requireLoggedIn();
        if (thresholdPercent <= 0 || thresholdPercent > 100) {
            throw new ServiceException(ServiceException.Kind.VALIDATION, "Threshold must be above 0 and at most 100.");
        }
        List<Book> list = new ArrayList<>(books.listBooks());
        if (list.size() < 2) {
            throw new ServiceException(ServiceException.Kind.VALIDATION, "At least two books are needed.");
        }
        if (list.size() > MAX_CONFLICT_BOOKS) {
            list = new ArrayList<>(list.subList(0, MAX_CONFLICT_BOOKS));
        }
        long start = System.nanoTime();
        List<BookText> texts = new ArrayList<>();
        for (Book b : list) {
            texts.add(books.openBookQuietly(b));
        }
        int n = list.size();
        double[][] sim = new double[n][n];
        ConflictGraph g = new ConflictGraph(n);
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                ComparisonSession s = books.startComparisonQuietly(texts.get(i), texts.get(j));
                double pct = s.compare(SimilarityMethod.SUFFIX_TREE, MIN_PHRASE).getSimilarityPercent();
                sim[i][j] = pct;
                sim[j][i] = pct;
                if (pct >= thresholdPercent) {
                    g.addEdge(i, j);
                }
            }
        }
        return new ConflictAnalysis(list, sim, thresholdPercent, g, System.nanoTime() - start);
    }

    // ================================================================ Module 6: ranking

    /** A book with its word count. */
    public static final class BookStat {
        public final Book book;
        public final long words;
        public final long characters;

        BookStat(Book book, long words, long characters) {
            this.book = book;
            this.words = words;
            this.characters = characters;
        }
    }

    /** Ranking result plus a quicksort comparison on a sorted list. */
    public static final class Ranking {
        public final List<BookStat> ranked;
        public final RandomizedQuickSort.Stats stats;
        public final int demoSize;
        public final RandomizedQuickSort.Stats demoRandom;
        public final RandomizedQuickSort.Stats demoFirstPivot;

        Ranking(List<BookStat> ranked, RandomizedQuickSort.Stats stats, int demoSize,
                RandomizedQuickSort.Stats demoRandom, RandomizedQuickSort.Stats demoFirstPivot) {
            this.ranked = Collections.unmodifiableList(ranked);
            this.stats = stats;
            this.demoSize = demoSize;
            this.demoRandom = demoRandom;
            this.demoFirstPivot = demoFirstPivot;
        }
    }

    /** Ranks books by word count (largest first) with Randomized QuickSort. */
    public Ranking rankBooksByWords() throws ServiceException {
        requireLoggedIn();
        List<BookStat> stats = new ArrayList<>();
        for (Book b : books.listBooks()) {
            String text = books.openBookQuietly(b).getOriginalText();
            stats.add(new BookStat(b, ParallelStats.countWordsSequential(text), text.length()));
        }
        RandomizedQuickSort.Stats st = RandomizedQuickSort.sort(stats,
                (x, y) -> x.words != y.words ? Long.compare(y.words, x.words)
                        : x.book.getTitle().compareToIgnoreCase(y.book.getTitle()),
                new Random());

        // why randomize: an already-sorted list is the worst case for "first element" pivots
        int demo = 2000;
        List<Integer> a = new ArrayList<>();
        List<Integer> b = new ArrayList<>();
        for (int i = 0; i < demo; i++) {
            a.add(i);
            b.add(i);
        }
        RandomizedQuickSort.Stats r = RandomizedQuickSort.sort(a, Integer::compare, new Random());
        RandomizedQuickSort.Stats f = RandomizedQuickSort.sortFirstPivot(b, Integer::compare);
        return new Ranking(stats, st, demo, r, f);
    }

    // ================================================================ Module 6: reservoir sampling

    /** A reservoir sample of the usage stream plus exact counts per action from the same pass. */
    public static final class UsageSample {
        public final ReservoirSampling.Sample<UsageLogCSV.Event> sample;
        public final Map<String, Long> actionCounts;
        public final boolean demo;

        UsageSample(ReservoirSampling.Sample<UsageLogCSV.Event> sample, Map<String, Long> actionCounts, boolean demo) {
            this.sample = sample;
            this.actionCounts = Collections.unmodifiableMap(actionCounts);
            this.demo = demo;
        }
    }

    public boolean usageLogEmpty() throws ServiceException {
        requireManager();
        try {
            return usage.isEmpty();
        } catch (IOException e) {
            throw storage(e);
        }
    }

    /**
     * Samples k events from the usage log in one pass (O(k) memory). With {@code demo} = true a
     * simulated stream of {@code demoSize} events is generated on the fly instead.
     */
    public UsageSample sampleUsage(int k, boolean demo, int demoSize) throws ServiceException {
        requireManager();
        if (k < 1 || k > 1000) {
            throw new ServiceException(ServiceException.Kind.VALIDATION, "Sample size must be 1 to 1000.");
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        Random random = new Random();
        if (demo) {
            Iterator<UsageLogCSV.Event> stream = counting(simulatedStream(demoSize, new Random(7)), counts);
            return new UsageSample(ReservoirSampling.sample(stream, k, random), counts, true);
        }
        try (UsageLogCSV.EventStream s = usage.open()) {
            return new UsageSample(ReservoirSampling.sample(counting(s, counts), k, random), counts, false);
        } catch (IOException e) {
            throw storage(e);
        }
    }

    private static Iterator<UsageLogCSV.Event> counting(Iterator<UsageLogCSV.Event> in, Map<String, Long> counts) {
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return in.hasNext();
            }

            @Override
            public UsageLogCSV.Event next() {
                UsageLogCSV.Event e = in.next();
                counts.merge(e.action, 1L, Long::sum);
                return e;
            }
        };
    }

    /** Simulated events (clearly marked DEMO in the UI), generated one at a time. */
    private static Iterator<UsageLogCSV.Event> simulatedStream(int size, Random r) {
        String[] actions = { "SEARCH", "SEARCH", "SEARCH", "OPEN_BOOK", "OPEN_BOOK", "COMPARE" };
        String[] users = { "stu.ravi", "stu.priya", "stu.kiran", "tch.sita", "lib.ramesh" };
        String[] details = { "tree", "process", "graph", "Algorithms Explained", "Basic Algebra", "heap" };
        return new Iterator<>() {
            int i;

            @Override
            public boolean hasNext() {
                return i < size;
            }

            @Override
            public UsageLogCSV.Event next() {
                if (i >= size) {
                    throw new NoSuchElementException();
                }
                i++;
                return new UsageLogCSV.Event("demo #" + i, users[r.nextInt(users.length)],
                        actions[r.nextInt(actions.length)], details[r.nextInt(details.length)]);
            }
        };
    }

    // ================================================================ Module 6: parallel statistics

    /** Parallel word counts per book and their cumulative totals. */
    public static final class ParallelReport {
        public final List<Book> books;
        public final List<ParallelStats.Reduction> reductions;
        public final long[] sequentialWords;
        public final long[] words;
        public final ParallelStats.Scan scan;
        public final long totalCharacters;
        public final int processors;

        ParallelReport(List<Book> books, List<ParallelStats.Reduction> reductions, long[] sequentialWords,
                       long[] words, ParallelStats.Scan scan, long totalCharacters, int processors) {
            this.books = Collections.unmodifiableList(books);
            this.reductions = Collections.unmodifiableList(reductions);
            this.sequentialWords = sequentialWords;
            this.words = words;
            this.scan = scan;
            this.totalCharacters = totalCharacters;
            this.processors = processors;
        }
    }

    public ParallelReport parallelStatistics() throws ServiceException {
        requireManager();
        List<Book> list = books.listBooks();
        List<ParallelStats.Reduction> reds = new ArrayList<>();
        long[] seq = new long[list.size()];
        long[] words = new long[list.size()];
        long chars = 0;
        for (int i = 0; i < list.size(); i++) {
            String text = books.openBookQuietly(list.get(i)).getOriginalText();
            ParallelStats.countWords(text); // warm-up
            ParallelStats.Reduction r = ParallelStats.countWords(text);
            reds.add(r);
            words[i] = r.words;
            seq[i] = ParallelStats.countWordsSequential(text);
            chars += text.length();
        }
        ParallelStats.Scan scan = ParallelStats.blellochScan(words);
        return new ParallelReport(list, reds, seq, words, scan, chars, ParallelStats.processors());
    }

    // ================================================================ helpers

    private User requireLoggedIn() throws ServiceException {
        try {
            return auth.requireCurrentUser();
        } catch (AuthException e) {
            throw new ServiceException(ServiceException.Kind.ACCESS_DENIED, e.getMessage());
        }
    }

    private User requireManager() throws ServiceException {
        User u = requireLoggedIn();
        if (u.getRole() != Role.LIBRARIAN && u.getRole() != Role.ADMIN) {
            throw new ServiceException(ServiceException.Kind.ACCESS_DENIED,
                    "Access denied: only a Librarian or Admin can view library analytics.");
        }
        return u;
    }

    private static ServiceException storage(IOException e) {
        return new ServiceException(ServiceException.Kind.STORAGE, "Could not access usage data: " + e.getMessage());
    }
}
