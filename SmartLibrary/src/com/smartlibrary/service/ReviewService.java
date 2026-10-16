package com.smartlibrary.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.smartlibrary.auth.AuthException;
import com.smartlibrary.auth.AuthService;
import com.smartlibrary.auth.Role;
import com.smartlibrary.data.BookCSV;
import com.smartlibrary.data.ReviewerCSV;
import com.smartlibrary.data.ReviewerCSV.Profile;
import com.smartlibrary.data.UserCSV;
import com.smartlibrary.dsa.ReviewerAssignment;
import com.smartlibrary.model.Book;
import com.smartlibrary.model.User;

/**
 * Reviewer assignment (DSA Module 4). Reviewers are active Teachers with a profile in reviewers.csv.
 * A reviewer is eligible for a paper (book) when the book's category is one of the reviewer's
 * expertise areas and the book is not in the reviewer's conflict list.
 * Librarians and Admins plan and save assignments; Teachers see their own.
 */
public class ReviewService {

    /** Reviewers, papers and who may review what. */
    public static final class Network {
        public final List<User> reviewers;
        public final List<Profile> profiles;
        public final List<Book> papers;
        public final boolean[][] eligible;
        public final List<String> notes;

        Network(List<User> reviewers, List<Profile> profiles, List<Book> papers, boolean[][] eligible,
                List<String> notes) {
            this.reviewers = Collections.unmodifiableList(reviewers);
            this.profiles = Collections.unmodifiableList(profiles);
            this.papers = Collections.unmodifiableList(papers);
            this.eligible = eligible;
            this.notes = Collections.unmodifiableList(notes);
        }

        public int eligiblePairs() {
            int c = 0;
            for (boolean[] row : eligible) {
                for (boolean b : row) {
                    if (b) {
                        c++;
                    }
                }
            }
            return c;
        }
    }

    private final UserCSV users;
    private final BookCSV books;
    private final ReviewerCSV reviewers;
    private final AuthService auth;

    public ReviewService(UserCSV users, BookCSV books, ReviewerCSV reviewers, AuthService auth) {
        this.users = users;
        this.books = books;
        this.reviewers = reviewers;
        this.auth = auth;
    }

    /** Builds the bipartite reviewer / paper graph. Librarian or Admin only. */
    public Network network() throws ServiceException {
        requireManager();
        try {
            List<String> notes = new ArrayList<>();
            List<User> teachers = new ArrayList<>();
            for (User u : users.loadAll()) {
                if (u.getRole() == Role.TEACHER && u.isActive()) {
                    teachers.add(u.withoutSecrets());
                }
            }
            List<User> rs = new ArrayList<>();
            List<Profile> ps = new ArrayList<>();
            for (Profile p : reviewers.loadProfiles()) {
                User match = null;
                for (User t : teachers) {
                    if (t.getUsername().equalsIgnoreCase(p.getUsername())) {
                        match = t;
                    }
                }
                if (match == null) {
                    notes.add("Skipped reviewer '" + p.getUsername() + "': not an active Teacher.");
                } else if (p.getExpertise().isEmpty()) {
                    notes.add("Skipped reviewer '" + p.getUsername() + "': no expertise set.");
                } else {
                    rs.add(match);
                    ps.add(p);
                }
            }
            for (User t : teachers) {
                boolean has = false;
                for (User r : rs) {
                    has |= r.getUserId() == t.getUserId();
                }
                if (!has && notes.stream().noneMatch(n -> n.contains("'" + t.getUsername() + "'"))) {
                    notes.add("Teacher '" + t.getUsername() + "' has no reviewer profile (set one in option 4).");
                }
            }
            List<Book> papers = books.loadAll();
            boolean[][] eligible = new boolean[rs.size()][papers.size()];
            for (int r = 0; r < rs.size(); r++) {
                for (int p = 0; p < papers.size(); p++) {
                    Book b = papers.get(p);
                    eligible[r][p] = ps.get(r).knows(b.getCategory()) && !ps.get(r).getConflicts().contains(b.getBookId());
                }
            }
            return new Network(rs, ps, papers, eligible, notes);
        } catch (IOException e) {
            throw storage(e);
        }
    }

    /** Assigns papers with Edmonds-Karp; each reviewer takes at most {@code capacity} papers. */
    public ReviewerAssignment.Assignment assign(Network net, int capacity) throws ServiceException {
        requireManager();
        if (capacity < 1 || capacity > 100) {
            throw new ServiceException(ServiceException.Kind.VALIDATION, "Papers per reviewer must be 1 to 100.");
        }
        return ReviewerAssignment.assign(net.eligible, net.papers.size(), capacity);
    }

    /** One-paper-per-reviewer matching and König minimum vertex cover. */
    public ReviewerAssignment.KonigResult konig(Network net) throws ServiceException {
        requireManager();
        return ReviewerAssignment.konig(net.eligible, net.papers.size());
    }

    /** Saves an assignment so Teachers can see it. */
    public void save(Network net, ReviewerAssignment.Assignment a) throws ServiceException {
        requireManager();
        List<String[]> pairs = new ArrayList<>();
        for (int p = 0; p < net.papers.size(); p++) {
            if (a.reviewerOf[p] >= 0) {
                pairs.add(new String[] { Long.toString(net.papers.get(p).getBookId()),
                        net.reviewers.get(a.reviewerOf[p]).getUsername() });
            }
        }
        try {
            reviewers.saveAssignments(pairs);
        } catch (IOException e) {
            throw storage(e);
        }
    }

    /** Sets a Teacher's expertise and conflict list. Librarian or Admin only. */
    public void saveProfile(String username, List<String> expertise, List<Long> conflicts) throws ServiceException {
        requireManager();
        try {
            User teacher = users.findByUsernameOrEmail(username).orElse(null);
            if (teacher == null || teacher.getRole() != Role.TEACHER) {
                throw new ServiceException(ServiceException.Kind.VALIDATION, "'" + username + "' is not a Teacher.");
            }
            Set<String> exp = new LinkedHashSet<>();
            for (String e : expertise) {
                if (!e.trim().isEmpty()) {
                    exp.add(e.trim());
                }
            }
            if (exp.isEmpty()) {
                throw new ServiceException(ServiceException.Kind.VALIDATION, "Enter at least one expertise category.");
            }
            reviewers.saveProfile(new Profile(teacher.getUsername(), exp, new LinkedHashSet<>(conflicts)));
        } catch (IOException e) {
            throw storage(e);
        }
    }

    /** Active Teachers, for choosing whose profile to edit. */
    public List<User> teachers() throws ServiceException {
        requireManager();
        try {
            List<User> out = new ArrayList<>();
            for (User u : users.loadAll()) {
                if (u.getRole() == Role.TEACHER) {
                    out.add(u.withoutSecrets());
                }
            }
            return out;
        } catch (IOException e) {
            throw storage(e);
        }
    }

    /** The logged-in Teacher's saved assignments. */
    public List<Book> myAssignments() throws ServiceException {
        User me = requireLoggedIn();
        try {
            List<Book> all = books.loadAll();
            List<Book> mine = new ArrayList<>();
            for (String[] pair : reviewers.loadAssignments()) {
                if (pair[1].equalsIgnoreCase(me.getUsername())) {
                    for (Book b : all) {
                        if (Long.toString(b.getBookId()).equals(pair[0])) {
                            mine.add(b);
                        }
                    }
                }
            }
            return mine;
        } catch (IOException e) {
            throw storage(e);
        }
    }

    // ------------------------------------------------------------------ helpers

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
                    "Access denied: only a Librarian or Admin can assign reviewers.");
        }
        return u;
    }

    private static ServiceException storage(IOException e) {
        return new ServiceException(ServiceException.Kind.STORAGE, "Could not access reviewer data: " + e.getMessage());
    }
}
