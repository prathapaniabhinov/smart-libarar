package com.smartlibrary;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import com.smartlibrary.auth.AuthService;
import com.smartlibrary.auth.InitialSetup;
import com.smartlibrary.auth.Role;
import com.smartlibrary.data.BookCSV;
import com.smartlibrary.data.UserCSV;
import com.smartlibrary.dsa.Algorithm;
import com.smartlibrary.dsa.Match;
import com.smartlibrary.dsa.MatchResult;
import com.smartlibrary.dsa.TextUtil;
import com.smartlibrary.model.AccountStatus;
import com.smartlibrary.model.Book;
import com.smartlibrary.service.BookService;
import com.smartlibrary.service.BookService.BookSearch;
import com.smartlibrary.service.BookService.BookText;
import com.smartlibrary.service.ServiceException;
import com.smartlibrary.service.UserService;

/**
 * Tests for DSA Module 1 (string matching) and the book import / two-step search.
 * Run as a Java Application. Uses temporary folders only.
 */
public final class StringMatchingTests {

    @FunctionalInterface
    private interface TestBody {
        void run() throws Exception;
    }

    @FunctionalInterface
    private interface Action {
        void run() throws Exception;
    }

    private static int passed;
    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        test("All algorithms agree with String.indexOf on 20,000 random texts", () -> {
            Random r = new Random(7);
            for (int it = 0; it < 20_000; it++) {
                String alphabet = it % 2 == 0 ? "ab" : "abc";
                String text = random(r, alphabet, r.nextInt(80));
                List<String> patterns = new ArrayList<>();
                int k = 1 + r.nextInt(3);
                for (int j = 0; j < k; j++) {
                    patterns.add(random(r, alphabet, 1 + r.nextInt(5)));
                }
                patterns = new ArrayList<>(new LinkedHashSet<>(patterns));
                List<String> expected = naive(text, patterns);
                for (Algorithm a : Algorithm.values()) {
                    List<String> got = asStrings(a.newMatcher().search(text, patterns));
                    check(got.equals(expected), a + " text=" + text + " patterns=" + patterns
                            + " expected=" + expected + " got=" + got);
                }
            }
        });

        test("Overlapping matches are all found", () -> {
            for (Algorithm a : Algorithm.values()) {
                MatchResult res = a.newMatcher().search("aaaa", List.of("aa"));
                check(res.getMatchCount() == 3, a + " found " + res.getMatchCount());
            }
        });

        test("Edge cases: empty pattern, pattern longer than text, empty text", () -> {
            for (Algorithm a : Algorithm.values()) {
                check(a.newMatcher().search("abc", List.of("")).getMatchCount() == 0, a + " empty pattern");
                check(a.newMatcher().search("ab", List.of("abc")).getMatchCount() == 0, a + " long pattern");
                check(a.newMatcher().search("", List.of("a")).getMatchCount() == 0, a + " empty text");
                check(a.newMatcher().search("abc", List.of("abc")).getMatchCount() == 1, a + " whole text");
            }
        });

        test("Worked example from the design: 'tree' found at 2 and 27", () -> {
            String text = "a tree has nodes.\na binary tree has two children.";
            for (Algorithm a : Algorithm.values()) {
                MatchResult res = a.newMatcher().search(text, List.of("tree"));
                check(asStrings(res).equals(List.of("tree@2", "tree@27")), a + " got " + asStrings(res));
                check(res.getComparisons() > 0, a + " counted comparisons");
            }
        });

        test("KMP LPS table", () -> {
            int[] lps = com.smartlibrary.dsa.KmpMatcher.buildLps("ababc", new long[1]);
            check(java.util.Arrays.equals(lps, new int[] { 0, 0, 1, 2, 0 }), java.util.Arrays.toString(lps));
        });

        test("Menu switch maps 1-4 to the four algorithms", () -> {
            check(Algorithm.fromMenuChoice(1) == Algorithm.KMP, "1");
            check(Algorithm.fromMenuChoice(2) == Algorithm.RABIN_KARP, "2");
            check(Algorithm.fromMenuChoice(3) == Algorithm.Z_ALGORITHM, "3");
            check(Algorithm.fromMenuChoice(4) == Algorithm.AHO_CORASICK, "4");
            try {
                Algorithm.fromMenuChoice(9);
                throw new AssertionError("9 accepted");
            } catch (IllegalArgumentException expected) {
                // ok
            }
        });

        test("Keyword parsing and case folding keep positions", () -> {
            check(TextUtil.parseKeywords(" Tree, GRAPH ,tree,, ").equals(List.of("tree", "graph")), "parse");
            check(TextUtil.parseKeywords("binary tree").equals(List.of("binary tree")), "space kept");
            String s = "İstanbul ABC";
            check(TextUtil.toLowerSameLength(s).length() == s.length(), "same length");
            int[] starts = TextUtil.lineStarts("ab\ncd\r\nef\rgh");
            check(java.util.Arrays.equals(starts, new int[] { 0, 3, 7, 10 }), java.util.Arrays.toString(starts));
            check(TextUtil.lineIndexOf(starts, 8) == 2, "binary search line");
        });

        test("Two-step search on imported books", () -> withLibrary((dir, users, books) -> {
            Path a = writeTxt(dir, "ds.txt", "a tree has nodes.\na binary tree has two children.");
            Path b = writeTxt(dir, "nature.txt", "the tree grows tall.");
            Path c = writeTxt(dir, "math.txt", "x plus y equals z.");
            books.importBook(a.toString(), "Data Structures", "Weiss", "Computer Science");
            books.importBook("\"" + b + "\"", "Forests", "Green", "Biology"); // quoted path works
            books.importBook(c.toString(), "Algebra", "Gupta", "Mathematics");
            books.importBook(writeTxt(dir, "ads.txt", "segment tree and tree heaps").toString(),
                    "Advanced Topics", "Brass", "Computer Science");

            for (Algorithm alg : Algorithm.values()) {
                BookSearch s = books.findBooks("data struct", alg);
                check(s.getHits().size() == 1 && s.getHits().get(0).getBook().getTitle().equals("Data Structures"),
                        alg + " step 1 hits");
                check(s.getSimilar().size() == 1 && s.getSimilar().get(0).getTitle().equals("Advanced Topics"),
                        alg + " similar books");
            }
            BookSearch multi = books.findBooks("algebra, forests", Algorithm.AHO_CORASICK);
            check(multi.getHits().size() == 2, "multi keyword title search");

            Book ds = books.findBooks("data", Algorithm.KMP).getHits().get(0).getBook();
            BookText text = books.openBook(ds);
            List<MatchResult> all = books.searchInBookWithAll(text, "Tree");
            for (MatchResult r : all) {
                check(r.getMatchCount() == 2, r.getAlgorithm() + " found " + r.getMatchCount());
            }
            Match second = all.get(0).getMatches().get(1);
            check(text.lineOf(second.getPosition()) == 2 && text.columnOf(second.getPosition()) == 10, "line/col");
            check(text.lineText(2).equals("a binary tree has two children."), "line text");

            MatchResult two = books.searchInBook(text, "tree, children", Algorithm.AHO_CORASICK);
            check(two.countFor("tree") == 2 && two.countFor("children") == 1, "multi pattern in book");
        }));

        test("Import validation: missing file, non-.txt, empty, duplicate", () -> withLibrary((dir, users, books) -> {
            expect(ServiceException.Kind.VALIDATION, () -> books.importBook(dir.resolve("nope.txt").toString(),
                    "T", "A", "C"));
            Path pdf = Files.writeString(dir.resolve("x.pdf"), "abc");
            expect(ServiceException.Kind.VALIDATION, () -> books.importBook(pdf.toString(), "T", "A", "C"));
            Path empty = Files.writeString(dir.resolve("empty.txt"), "");
            expect(ServiceException.Kind.VALIDATION, () -> books.importBook(empty.toString(), "T", "A", "C"));
            Path ok = writeTxt(dir, "ok.txt", "hello");
            expect(ServiceException.Kind.VALIDATION, () -> books.importBook(ok.toString(), "", "A", "C"));
            books.importBook(ok.toString(), "Hello, \"World\"", "A", "C");
            expect(ServiceException.Kind.DUPLICATE, () -> books.importBook(ok.toString(), "hello, \"world\"", "a", "C"));
            check(new BookCSV(dir.resolve("data")).loadAll().get(0).getTitle().equals("Hello, \"World\""),
                    "CSV round trip with comma and quotes");
        }));

        test("Only Librarian/Admin can import; logged-out users cannot search", () -> withLibrary((dir, users, books) -> {
            Path ok = writeTxt(dir, "ok.txt", "hello");
            users.createUser("Stu", "stu01", "stu@example.com", "Password123".toCharArray(), Role.STUDENT,
                    AccountStatus.ACTIVE);
            users.createUser("Lib", "lib01", "lib@example.com", "Password123".toCharArray(), Role.LIBRARIAN,
                    AccountStatus.ACTIVE);
            AuthService auth = AUTH.get();
            auth.logout();
            auth.login("stu01", "Password123".toCharArray());
            expect(ServiceException.Kind.ACCESS_DENIED, () -> books.importBook(ok.toString(), "T", "A", "C"));
            auth.logout();
            auth.login("lib01", "Password123".toCharArray());
            books.importBook(ok.toString(), "T", "A", "C");
            auth.logout();
            expect(ServiceException.Kind.ACCESS_DENIED, () -> books.findBooks("t", Algorithm.KMP));
            expect(ServiceException.Kind.ACCESS_DENIED, () -> books.listBooks());
        }));

        test("Book files outside data/books are refused", () -> withLibrary((dir, users, books) -> {
            Path data = dir.resolve("data");
            Files.writeString(data.resolve("books.csv"),
                    "bookId,title,author,category,fileName\n1,Evil,X,Y,../users.csv\n");
            Book evil = books.listBooks().get(0);
            expect(ServiceException.Kind.STORAGE, () -> books.openBook(evil));
        }));

        test("Sample books in the project load and are searchable", () -> {
            Path projectData = Path.of("data");
            if (!Files.exists(projectData.resolve("books.csv"))) {
                System.out.println("      (skipped: run from the project folder to include this test)");
                return;
            }
            Path tmp = Files.createTempDirectory("sl-sample-");
            try {
                Path data = tmp.resolve("data");
                Files.createDirectories(data.resolve("books"));
                Files.copy(projectData.resolve("books.csv"), data.resolve("books.csv"));
                try (Stream<Path> s = Files.list(projectData.resolve("books"))) {
                    for (Path p : (Iterable<Path>) s::iterator) {
                        Files.copy(p, data.resolve("books").resolve(p.getFileName()));
                    }
                }
                List<Book> list = new BookCSV(data).loadAll();
                check(list.size() == 5, "5 sample books, got " + list.size());
                for (Book b : list) {
                    check(!new BookCSVReader(data).read(b).isEmpty(), "text of " + b.getTitle());
                }
            } finally {
                deleteRecursively(tmp);
            }
        });

        System.out.println();
        System.out.println("Passed: " + passed + ", Failed: " + failures.size());
        for (String f : failures) {
            System.out.println("  FAILED " + f);
        }
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    // ------------------------------------------------------------------ helpers

    /** Small adapter so the sample-book test can read text through the public API. */
    private static final class BookCSVReader {
        private final BookCSV csv;

        BookCSVReader(Path data) {
            this.csv = new BookCSV(data);
        }

        String read(Book b) throws IOException {
            return csv.readBookText(b);
        }
    }

    @FunctionalInterface
    private interface LibraryBody {
        void run(Path dir, UserService users, BookService books) throws Exception;
    }

    private static final ThreadLocal<AuthService> AUTH = new ThreadLocal<>();

    /** Fresh data folder with an Admin logged in. */
    private static void withLibrary(LibraryBody body) throws Exception {
        Path dir = Files.createTempDirectory("sl-books-");
        try {
            Path data = dir.resolve("data");
            UserCSV store = new UserCSV(data);
            AuthService auth = new AuthService(store);
            AUTH.set(auth);
            UserService users = new UserService(store, auth, new InitialSetup(data, "CODE"));
            users.prepareInitialSetup();
            users.createInitialAdmin("CODE", "Admin", "admin01", "admin@example.com",
                    "AdminPass123".toCharArray(), "AdminPass123".toCharArray());
            auth.login("admin01", "AdminPass123".toCharArray());
            body.run(dir, users, new BookService(new BookCSV(data), auth));
        } finally {
            deleteRecursively(dir);
        }
    }

    private static Path writeTxt(Path dir, String name, String content) throws IOException {
        return Files.writeString(dir.resolve(name), content, StandardCharsets.UTF_8);
    }

    private static String random(Random r, String alphabet, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append(alphabet.charAt(r.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    private static List<String> naive(String text, List<String> patterns) {
        List<String> out = new ArrayList<>();
        for (String p : patterns) {
            for (int i = text.indexOf(p); i >= 0; i = text.indexOf(p, i + 1)) {
                out.add(p + "@" + i);
            }
        }
        Collections.sort(out);
        return out;
    }

    private static List<String> asStrings(MatchResult r) {
        List<String> out = new ArrayList<>();
        for (Match m : r.getMatches()) {
            out.add(m.toString());
        }
        Collections.sort(out);
        return out;
    }

    private static void test(String name, TestBody body) {
        try {
            body.run();
            passed++;
            System.out.println("PASS  " + name);
        } catch (Throwable t) {
            failures.add(name + " -> " + t);
            System.out.println("FAIL  " + name + " -> " + t);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void expect(ServiceException.Kind kind, Action action) throws Exception {
        try {
            action.run();
        } catch (ServiceException e) {
            check(e.getKind() == kind, "expected " + kind + " but got " + e.getKind() + ": " + e.getMessage());
            return;
        }
        throw new AssertionError("expected ServiceException " + kind);
    }

    private static void deleteRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ignored) {
                    // best effort
                }
            });
        } catch (IOException ignored) {
            // best effort
        }
    }
}
