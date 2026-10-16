package com.smartlibrary;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import com.smartlibrary.auth.AuthService;
import com.smartlibrary.auth.InitialSetup;
import com.smartlibrary.auth.Role;
import com.smartlibrary.data.BookCSV;
import com.smartlibrary.data.ReviewerCSV;
import com.smartlibrary.data.UsageLogCSV;
import com.smartlibrary.data.UserCSV;
import com.smartlibrary.dsa.ConflictGraph;
import com.smartlibrary.dsa.MaxFlow;
import com.smartlibrary.dsa.ParallelStats;
import com.smartlibrary.dsa.RandomizedQuickSort;
import com.smartlibrary.dsa.ReservoirSampling;
import com.smartlibrary.dsa.ReviewerAssignment;
import com.smartlibrary.model.AccountStatus;
import com.smartlibrary.service.ReviewService;
import com.smartlibrary.service.ServiceException;
import com.smartlibrary.service.UserService;

/**
 * Tests for DSA Module 4 (network flow), Module 5 (NP-completeness and approximation) and
 * Module 6 (randomized and parallel algorithms). Run as a Java Application.
 */
public final class AdvancedAlgorithmsTests {

    @FunctionalInterface
    private interface TestBody {
        void run() throws Exception;
    }

    private static int passed;
    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        // ---------------------------------------------------------------- Module 4
        test("Edmonds-Karp: classic example has max flow 23", () -> {
            MaxFlow f = new MaxFlow(6);
            int[][] e = { { 0, 1, 16 }, { 0, 2, 13 }, { 1, 2, 10 }, { 2, 1, 4 }, { 1, 3, 12 }, { 3, 2, 9 },
                    { 2, 4, 14 }, { 4, 3, 7 }, { 3, 5, 20 }, { 4, 5, 4 } };
            for (int[] x : e) {
                f.addEdge(x[0], x[1], x[2]);
            }
            check(f.edmondsKarp(0, 5) == 23, "flow");
        });

        test("Max flow = min cut on 2,000 random graphs (brute-force cuts)", () -> {
            Random r = new Random(31);
            for (int it = 0; it < 2_000; it++) {
                int n = 2 + r.nextInt(6);
                int[][] cap = new int[n][n];
                MaxFlow f = new MaxFlow(n);
                for (int u = 0; u < n; u++) {
                    for (int v = 0; v < n; v++) {
                        if (u != v && r.nextInt(3) == 0) {
                            cap[u][v] = 1 + r.nextInt(9);
                            f.addEdge(u, v, cap[u][v]);
                        }
                    }
                }
                long flow = f.edmondsKarp(0, n - 1);
                long minCut = Long.MAX_VALUE;
                for (int mask = 0; mask < (1 << n); mask++) {
                    if ((mask & 1) == 0 || (mask >> (n - 1) & 1) == 1) {
                        continue; // source inside, sink outside
                    }
                    long c = 0;
                    for (int u = 0; u < n; u++) {
                        for (int v = 0; v < n; v++) {
                            if ((mask >> u & 1) == 1 && (mask >> v & 1) == 0) {
                                c += cap[u][v];
                            }
                        }
                    }
                    minCut = Math.min(minCut, c);
                }
                check(flow == minCut, "flow " + flow + " cut " + minCut);
            }
        });

        test("Assignment respects capacity and eligibility; matching = brute force", () -> {
            Random r = new Random(32);
            for (int it = 0; it < 1_000; it++) {
                int rv = 1 + r.nextInt(4);
                int pp = 1 + r.nextInt(5);
                boolean[][] el = new boolean[rv][pp];
                for (int i = 0; i < rv; i++) {
                    for (int j = 0; j < pp; j++) {
                        el[i][j] = r.nextBoolean();
                    }
                }
                int cap = 1 + r.nextInt(3);
                ReviewerAssignment.Assignment a = ReviewerAssignment.assign(el, pp, cap);
                int[] load = new int[rv];
                int count = 0;
                for (int p = 0; p < pp; p++) {
                    if (a.reviewerOf[p] >= 0) {
                        check(el[a.reviewerOf[p]][p], "assigned to an ineligible reviewer");
                        load[a.reviewerOf[p]]++;
                        count++;
                    }
                }
                for (int l : load) {
                    check(l <= cap, "capacity exceeded");
                }
                check(count == a.assigned, "count");
                check(count == bruteAssign(el, cap, 0, new int[rv]), "not maximum");
            }
        });

        test("Konig: min vertex cover is a cover and equals the max matching", () -> {
            Random r = new Random(33);
            for (int it = 0; it < 1_000; it++) {
                int rv = 1 + r.nextInt(5);
                int pp = 1 + r.nextInt(5);
                boolean[][] el = new boolean[rv][pp];
                for (int i = 0; i < rv; i++) {
                    for (int j = 0; j < pp; j++) {
                        el[i][j] = r.nextInt(3) == 0;
                    }
                }
                ReviewerAssignment.KonigResult k = ReviewerAssignment.konig(el, pp);
                check(k.coverSize() == k.matchingSize, "Konig sizes");
                for (int i = 0; i < rv; i++) {
                    for (int j = 0; j < pp; j++) {
                        if (el[i][j]) {
                            check(k.coverReviewers.contains(i) || k.coverPapers.contains(j), "edge not covered");
                        }
                    }
                }
                check(k.matchingSize == bruteAssign(el, 1, 0, new int[rv]), "matching not maximum");
            }
        });

        test("Reviewer service: sample reviewers, conflicts and permissions", () -> withLibrary((dir, users, auth) -> {
            Path data = dir.resolve("data");
            Files.writeString(data.resolve("books.csv"), "bookId,title,author,category,fileName\n"
                    + "1,DS,A,Computer Science,a.txt\n2,Algo,B,Computer Science,b.txt\n3,Forest,C,Biology,c.txt\n");
            users.createUser("Sita", "tch.sita", "s@example.com", "Password123".toCharArray(), Role.TEACHER, AccountStatus.ACTIVE);
            users.createUser("Ravi", "stu.ravi", "r@example.com", "Password123".toCharArray(), Role.STUDENT, AccountStatus.ACTIVE);
            Files.writeString(data.resolve("reviewers.csv"),
                    "username,expertise,conflictBookIds\ntch.sita,Computer Science,1\nstu.ravi,Biology,\n");
            ReviewService rs = new ReviewService(new UserCSV(data), new BookCSV(data), new ReviewerCSV(data), auth);
            ReviewService.Network net = rs.network();
            check(net.reviewers.size() == 1, "students are not reviewers");
            check(!net.eligible[0][0] && net.eligible[0][1] && !net.eligible[0][2], "eligibility");
            ReviewerAssignment.Assignment a = rs.assign(net, 5);
            check(a.assigned == 1, "only paper 2");
            rs.save(net, a);
            auth.logout();
            auth.login("tch.sita", "Password123".toCharArray());
            check(rs.myAssignments().size() == 1, "teacher sees own assignment");
            expect(ServiceException.Kind.ACCESS_DENIED, () -> rs.network());
        }));

        // ---------------------------------------------------------------- Module 5
        test("Exact vertex cover equals brute force; 2-approx is a cover within 2x", () -> {
            Random r = new Random(34);
            for (int it = 0; it < 2_000; it++) {
                int n = 1 + r.nextInt(9);
                ConflictGraph g = randomGraph(r, n);
                long exact = g.exactMinVertexCover().mask;
                int best = Integer.MAX_VALUE;
                for (long m = 0; m < (1L << n); m++) {
                    if (coversAll(g, m)) {
                        best = Math.min(best, Long.bitCount(m));
                    }
                }
                check(Long.bitCount(exact) == best && coversAll(g, exact), "exact");
                long apx = g.vertexCover2Approx();
                check(coversAll(g, apx), "approx is a cover");
                check(Long.bitCount(apx) <= 2 * best, "approx within 2x");
            }
        });

        test("2-approximation can be exactly 2x worse (star graph)", () -> {
            ConflictGraph star = new ConflictGraph(5);
            for (int v = 1; v < 5; v++) {
                star.addEdge(0, v); // centre 0: optimum cover = {0}
            }
            check(Long.bitCount(star.exactMinVertexCover().mask) == 1, "opt 1");
            check(Long.bitCount(star.vertexCover2Approx()) == 2, "approx 2");
        });

        test("Reductions: IS = complement of VC; IS is a clique in the complement graph", () -> {
            Random r = new Random(35);
            for (int it = 0; it < 1_000; it++) {
                int n = 1 + r.nextInt(9);
                ConflictGraph g = randomGraph(r, n);
                long vc = g.exactMinVertexCover().mask;
                long is = g.complementOf(vc);
                check(g.isIndependentSet(is), "independent");
                check(g.complement().isClique(is), "clique in complement");
                int bestIs = 0;
                for (long m = 0; m < (1L << n); m++) {
                    if (g.isIndependentSet(m)) {
                        bestIs = Math.max(bestIs, Long.bitCount(m));
                    }
                }
                check(Long.bitCount(is) == bestIs, "maximum independent set");
            }
        });

        // ---------------------------------------------------------------- Module 6
        test("Randomized QuickSort sorts correctly (duplicates, sorted, reversed, random)", () -> {
            Random r = new Random(36);
            for (int it = 0; it < 2_000; it++) {
                int n = r.nextInt(60);
                List<Integer> a = new ArrayList<>();
                int kind = it % 4;
                for (int i = 0; i < n; i++) {
                    a.add(kind == 0 ? r.nextInt(5) : kind == 1 ? i : kind == 2 ? n - i : r.nextInt(1000));
                }
                List<Integer> expected = new ArrayList<>(a);
                Collections.sort(expected);
                RandomizedQuickSort.sort(a, Integer::compare, r);
                check(a.equals(expected), "sorted");
            }
        });

        test("Randomized pivot avoids the O(n^2) worst case on sorted input", () -> {
            int n = 2000;
            List<Integer> a = new ArrayList<>();
            List<Integer> b = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                a.add(i);
                b.add(i);
            }
            RandomizedQuickSort.Stats rs = RandomizedQuickSort.sort(a, Integer::compare, new Random(1));
            RandomizedQuickSort.Stats fs = RandomizedQuickSort.sortFirstPivot(b, Integer::compare);
            check(fs.comparisons == (long) n * (n - 1) / 2, "first pivot = n(n-1)/2, got " + fs.comparisons);
            check(rs.comparisons < 3 * RandomizedQuickSort.Stats.expected(n), "randomized near 2n ln n");
            List<Integer> sorted = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                sorted.add(i);
            }
            check(a.equals(sorted) && b.equals(sorted), "both sorted");
        });

        test("Reservoir sampling is uniform (chi-square over 20,000 runs)", () -> {
            int n = 20;
            int k = 5;
            int runs = 20_000;
            int[] hits = new int[n];
            Random r = new Random(37);
            for (int run = 0; run < runs; run++) {
                List<Integer> stream = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    stream.add(i);
                }
                ReservoirSampling.Sample<Integer> s = ReservoirSampling.sample(stream.iterator(), k, r);
                check(s.items.size() == k && s.seen == n, "size");
                for (int x : s.items) {
                    hits[x]++;
                }
            }
            double expected = (double) runs * k / n;
            double chi = 0;
            for (int h : hits) {
                chi += (h - expected) * (h - expected) / expected;
            }
            check(chi < 43.8, "chi-square " + chi + " (19 d.f., p = 0.001 limit 43.8)");
        });

        test("Usage log streams events back one at a time", () -> {
            Path dir = Files.createTempDirectory("sl-usage-");
            try {
                UsageLogCSV log = new UsageLogCSV(dir);
                check(log.isEmpty(), "empty");
                log.append("stu.ravi", "SEARCH", "tree, graph");
                log.append("tch.sita", "OPEN_BOOK", "Basic Algebra");
                try (UsageLogCSV.EventStream s = log.open()) {
                    ReservoirSampling.Sample<UsageLogCSV.Event> sm = ReservoirSampling.sample(s, 5, new Random(1));
                    check(sm.seen == 2 && sm.items.get(0).detail.equals("tree, graph"), "read back");
                }
            } finally {
                deleteRecursively(dir);
            }
        });

        test("Parallel reduce word count equals sequential count", () -> {
            Random r = new Random(38);
            for (int it = 0; it < 200; it++) {
                StringBuilder sb = new StringBuilder();
                int len = r.nextInt(20_000);
                for (int i = 0; i < len; i++) {
                    sb.append(" ab.,\nxyz".charAt(r.nextInt(9)));
                }
                String t = sb.toString();
                check(ParallelStats.countWords(t).words == ParallelStats.countWordsSequential(t), "word count");
            }
        });

        test("Blelloch scan equals sequential prefix sums", () -> {
            Random r = new Random(39);
            for (int it = 0; it < 500; it++) {
                int n = r.nextInt(5000);
                long[] v = new long[n];
                for (int i = 0; i < n; i++) {
                    v[i] = r.nextInt(100);
                }
                ParallelStats.Scan s = ParallelStats.blellochScan(v);
                long sum = 0;
                for (int i = 0; i < n; i++) {
                    check(s.exclusive[i] == sum, "prefix at " + i);
                    sum += v[i];
                }
                check(s.total == sum, "total");
            }
        });

        test("Brent's bound: W/p + S", () -> {
            check(ParallelStats.brentBound(1000, 10, 1) == 1010.0, "p=1");
            check(ParallelStats.brentBound(1000, 10, 4) == 260.0, "p=4");
        });

        System.out.println();
        System.out.println("Passed: " + passed + ", Failed: " + failures.size());
        for (String f : failures) {
            System.out.println("  FAILED " + f);
        }
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    // ------------------------------------------------------------------ helpers

    /** Brute force: most papers assignable (papers from index p on) with per-reviewer capacity. */
    private static int bruteAssign(boolean[][] el, int cap, int p, int[] load) {
        int papers = el.length == 0 ? 0 : el[0].length;
        if (p == papers) {
            return 0;
        }
        int best = bruteAssign(el, cap, p + 1, load); // leave paper p unassigned
        for (int r = 0; r < el.length; r++) {
            if (el[r][p] && load[r] < cap) {
                load[r]++;
                best = Math.max(best, 1 + bruteAssign(el, cap, p + 1, load));
                load[r]--;
            }
        }
        return best;
    }

    private static ConflictGraph randomGraph(Random r, int n) {
        ConflictGraph g = new ConflictGraph(n);
        for (int u = 0; u < n; u++) {
            for (int v = u + 1; v < n; v++) {
                if (r.nextInt(3) == 0) {
                    g.addEdge(u, v);
                }
            }
        }
        return g;
    }

    private static boolean coversAll(ConflictGraph g, long mask) {
        for (int[] e : g.edges()) {
            if ((mask >> e[0] & 1) == 0 && (mask >> e[1] & 1) == 0) {
                return false;
            }
        }
        return true;
    }

    @FunctionalInterface
    private interface LibraryBody {
        void run(Path dir, UserService users, AuthService auth) throws Exception;
    }

    private static void withLibrary(LibraryBody body) throws Exception {
        Path dir = Files.createTempDirectory("sl-adv-");
        try {
            Path data = dir.resolve("data");
            UserCSV store = new UserCSV(data);
            AuthService auth = new AuthService(store);
            UserService users = new UserService(store, auth, new InitialSetup(data, "CODE"));
            users.prepareInitialSetup();
            users.createInitialAdmin("CODE", "Admin", "admin01", "admin@example.com",
                    "AdminPass123".toCharArray(), "AdminPass123".toCharArray());
            auth.login("admin01", "AdminPass123".toCharArray());
            body.run(dir, users, auth);
        } finally {
            deleteRecursively(dir);
        }
    }

    @FunctionalInterface
    private interface Action {
        void run() throws Exception;
    }

    private static void expect(ServiceException.Kind kind, Action a) throws Exception {
        try {
            a.run();
        } catch (ServiceException e) {
            check(e.getKind() == kind, "expected " + kind + " got " + e.getKind());
            return;
        }
        throw new AssertionError("expected " + kind);
    }

    private static void deleteRecursively(Path dir) {
        try (Stream<Path> w = Files.walk(dir)) {
            w.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (Exception ignored) {
            // best effort
        }
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
}
