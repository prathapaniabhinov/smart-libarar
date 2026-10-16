package com.smartlibrary.console;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.smartlibrary.data.ReviewerCSV.Profile;
import com.smartlibrary.dsa.ReviewerAssignment;
import com.smartlibrary.model.Book;
import com.smartlibrary.model.User;
import com.smartlibrary.service.ReviewService;
import com.smartlibrary.service.ReviewService.Network;
import com.smartlibrary.service.ServiceException;

/**
 * Reviewer Assignment (DSA Module 4 - Network Flow). Librarian and Admin.
 * <ul>
 *   <li>Edmonds-Karp max flow assigns papers (books) to reviewers (Teachers), each reviewer taking at
 *       most c papers - a bipartite matching when c = 1.</li>
 *   <li>König's theorem: size of the maximum matching = size of the minimum vertex cover; the cover
 *       shows the bottleneck reviewers / papers.</li>
 * </ul>
 */
public class ReviewerConsole {

    private final ConsoleIO io;
    private final ReviewService reviews;

    public ReviewerConsole(ConsoleIO io, ReviewService reviews) {
        this.io = io;
        this.reviews = reviews;
    }

    public void run() throws ServiceException {
        List<String> options = List.of(
                "View reviewers and eligibility",
                "Assign reviewers (Edmonds-Karp max flow)",
                "Conflict analysis (Bipartite matching + Konig's theorem)",
                "Set a reviewer's expertise and conflicts");
        while (true) {
            io.header("Reviewer Assignment (Network Flow)");
            io.menu(options, "Back to dashboard");
            int c = io.readChoice("\nChoose an option: ", 0, options.size());
            if (c == 0) {
                return;
            }
            try {
                switch (c) {
                    case 1: showNetwork(reviews.network()); io.pause(); break;
                    case 2: assign(); break;
                    case 3: konig(); break;
                    case 4: editProfile(); break;
                    default: break;
                }
            } catch (ServiceException e) {
                io.error(e.getMessage());
                if (e.getKind() == ServiceException.Kind.ACCESS_DENIED) {
                    return;
                }
                io.pause();
            }
        }
    }

    // ------------------------------------------------------------------ views

    private void showNetwork(Network net) {
        io.header("Reviewers and Eligibility");
        List<String[]> rows = new ArrayList<>();
        for (int r = 0; r < net.reviewers.size(); r++) {
            Profile p = net.profiles.get(r);
            List<String> ids = new ArrayList<>();
            for (Long id : p.getConflicts()) {
                ids.add("#" + id);
            }
            rows.add(new String[] { net.reviewers.get(r).getUsername(), net.reviewers.get(r).getName(),
                    String.join(", ", p.getExpertise()), ids.isEmpty() ? "-" : String.join(", ", ids) });
        }
        if (rows.isEmpty()) {
            io.info("No reviewers yet. Use option 4 to give a Teacher an expertise.");
        } else {
            io.table(new String[] { "Reviewer", "Name", "Expertise", "Conflicts (book IDs)" }, rows);
        }
        for (String note : net.notes) {
            io.info(note);
        }
        if (net.reviewers.isEmpty() || net.papers.isEmpty()) {
            return;
        }

        io.blank();
        io.println("  Eligibility (Y = may review: category matches expertise and no conflict):");
        String[] header = new String[net.reviewers.size() + 2];
        header[0] = "ID";
        header[1] = "Paper (category)";
        for (int r = 0; r < net.reviewers.size(); r++) {
            header[r + 2] = net.reviewers.get(r).getUsername();
        }
        List<String[]> grid = new ArrayList<>();
        for (int p = 0; p < net.papers.size(); p++) {
            Book b = net.papers.get(p);
            String[] row = new String[header.length];
            row[0] = Long.toString(b.getBookId());
            row[1] = b.getTitle() + " (" + b.getCategory() + ")";
            for (int r = 0; r < net.reviewers.size(); r++) {
                row[r + 2] = net.eligible[r][p] ? "Y" : (net.profiles.get(r).getConflicts().contains(b.getBookId())
                        ? "conflict" : ".");
            }
            grid.add(row);
        }
        io.table(header, grid);
        io.info(net.eligiblePairs() + " eligible reviewer-paper pairs.");
    }

    private void assign() throws ServiceException {
        Network net = reviews.network();
        showNetwork(net);
        if (net.reviewers.isEmpty() || net.papers.isEmpty()) {
            io.pause();
            return;
        }
        int capacity = readInt("\nMaximum papers per reviewer (Enter = 2): ", 2, 1, 100);
        ReviewerAssignment.Assignment a = reviews.assign(net, capacity);

        io.header("Assignment (Edmonds-Karp maximum flow)");
        io.println("  Network         : source -> " + net.reviewers.size() + " reviewers (capacity " + capacity
                + ") -> " + net.papers.size() + " papers (capacity 1) -> sink");
        io.println("  Graph size      : V = " + a.vertices + " vertices, E = " + a.edges + " edges");
        io.println("  How it works    : BFS finds the shortest augmenting path in the residual graph,");
        io.println("                    pushes flow along it, and repeats until no path is left.");
        io.println("  Augmenting paths: " + a.augmentingPaths);
        io.println("  Time            : " + ms(a.millis()) + " ms   O(V x E^2)");
        io.println("  Maximum flow    : " + a.assigned + " of " + net.papers.size() + " papers assigned");
        io.blank();
        List<String[]> rows = new ArrayList<>();
        int[] load = new int[net.reviewers.size()];
        for (int p = 0; p < net.papers.size(); p++) {
            Book b = net.papers.get(p);
            int r = a.reviewerOf[p];
            if (r >= 0) {
                load[r]++;
            }
            rows.add(new String[] { Long.toString(b.getBookId()), b.getTitle(), b.getCategory(),
                    r >= 0 ? net.reviewers.get(r).getUsername() : "NOT ASSIGNED" });
        }
        io.table(new String[] { "ID", "Paper", "Category", "Reviewer" }, rows);
        for (int r = 0; r < net.reviewers.size(); r++) {
            io.println("  " + net.reviewers.get(r).getUsername() + ": " + load[r] + " of " + capacity + " papers");
        }
        if (a.assigned < net.papers.size()) {
            io.info((net.papers.size() - a.assigned) + " paper(s) could not be assigned: no eligible reviewer "
                    + "with free capacity. Try a higher limit or option 3 to see the bottleneck.");
        }
        if (a.assigned > 0 && io.confirm("\nSave this assignment so Teachers can see it?")) {
            reviews.save(net, a);
            io.success("Assignment saved.");
        }
        io.pause();
    }

    private void konig() throws ServiceException {
        Network net = reviews.network();
        if (net.reviewers.isEmpty() || net.papers.isEmpty()) {
            io.info("Reviewers and papers are needed. Use option 4 first.");
            io.pause();
            return;
        }
        ReviewerAssignment.KonigResult k = reviews.konig(net);
        io.header("Conflict Analysis (Bipartite Matching + Konig's theorem)");
        io.println("  Question: if every reviewer takes only ONE paper, how many papers can be covered?");
        io.blank();
        io.println("  Maximum bipartite matching (" + k.matchingSize + " pairs):");
        for (int r = 0; r < net.reviewers.size(); r++) {
            int p = k.matchedPaper[r];
            io.println("    " + pad(net.reviewers.get(r).getUsername(), 14) + " -> "
                    + (p >= 0 ? net.papers.get(p).getTitle() : "(no paper)"));
        }
        io.blank();
        io.println("  Minimum vertex cover (" + k.coverSize() + " vertices) - every eligible pair touches one of them:");
        List<String> names = new ArrayList<>();
        for (int r : k.coverReviewers) {
            names.add("reviewer " + net.reviewers.get(r).getUsername());
        }
        for (int p : k.coverPapers) {
            names.add("paper \"" + net.papers.get(p).getTitle() + "\"");
        }
        for (String s : names) {
            io.println("    - " + s);
        }
        io.blank();
        io.success("Konig's theorem: maximum matching = minimum vertex cover = " + k.matchingSize + ".");
        if (k.matchingSize < net.papers.size()) {
            io.println("  So at most " + k.matchingSize + " of " + net.papers.size()
                    + " papers can each get their own reviewer.");
            io.println("  Why: every eligible reviewer-paper pair touches one of the " + k.coverSize()
                    + " cover vertices above,");
            io.println("  and each of them can be used by only one pair. That cover is the bottleneck:");
            io.println("  add reviewers with the needed expertise, remove conflicts, or allow more papers");
            io.println("  per reviewer (option 2) to assign the rest.");
        } else {
            io.println("  Every paper can get its own distinct reviewer.");
        }
        io.pause();
    }

    private void editProfile() throws ServiceException {
        List<User> teachers = reviews.teachers();
        io.header("Set Reviewer Expertise");
        if (teachers.isEmpty()) {
            io.info("There are no Teacher accounts.");
            io.pause();
            return;
        }
        for (int i = 0; i < teachers.size(); i++) {
            io.println(String.format("  %d. %-14s %s", i + 1, teachers.get(i).getUsername(), teachers.get(i).getName()));
        }
        int c = io.readChoice("Choose Teacher (0 = back): ", 0, teachers.size());
        if (c == 0) {
            return;
        }
        String exp = io.readLine("Expertise categories, comma separated (e.g. Computer Science, Mathematics): ");
        String conf = io.readLine("Conflict book IDs, comma separated (Enter = none): ");
        List<String> expertise = new ArrayList<>(List.of(exp.split(",")));
        List<Long> conflicts = new ArrayList<>();
        for (String s : conf.split(",")) {
            if (!s.trim().isEmpty()) {
                try {
                    conflicts.add(Long.parseLong(s.trim()));
                } catch (NumberFormatException e) {
                    io.error("'" + s.trim() + "' is not a book ID - ignored.");
                }
            }
        }
        reviews.saveProfile(teachers.get(c - 1).getUsername(), expertise, conflicts);
        io.success("Saved reviewer profile for " + teachers.get(c - 1).getUsername() + ".");
        io.pause();
    }

    // ------------------------------------------------------------------ helpers

    private int readInt(String prompt, int def, int min, int max) {
        while (true) {
            String s = io.readLine(prompt);
            if (s.isEmpty()) {
                return def;
            }
            try {
                int v = Integer.parseInt(s);
                if (v >= min && v <= max) {
                    return v;
                }
            } catch (NumberFormatException ignored) {
                // ask again
            }
            io.error("Please enter a number from " + min + " to " + max + ".");
        }
    }

    private static String pad(String s, int w) {
        return s.length() >= w ? s : s + " ".repeat(w - s.length());
    }

    private static String ms(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }
}
