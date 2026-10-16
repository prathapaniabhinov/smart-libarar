package com.smartlibrary.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reviewer profiles in {@code <dataDir>/reviewers.csv} (DSA Module 4):
 * {@code username,expertise,conflictBookIds} where expertise is a list of categories and
 * conflictBookIds is a list of book IDs the reviewer must not review, both separated by ';'.
 * <p>
 * Also stores the saved assignments in {@code <dataDir>/review_assignments.csv}
 * ({@code bookId,reviewer}).
 */
public class ReviewerCSV {

    public static final String FILE_NAME = "reviewers.csv";
    public static final String ASSIGNMENTS_FILE = "review_assignments.csv";

    /** One reviewer profile. */
    public static final class Profile {
        private final String username;
        private final Set<String> expertise;
        private final Set<Long> conflicts;

        public Profile(String username, Set<String> expertise, Set<Long> conflicts) {
            this.username = username;
            this.expertise = Collections.unmodifiableSet(new LinkedHashSet<>(expertise));
            this.conflicts = Collections.unmodifiableSet(new LinkedHashSet<>(conflicts));
        }

        public String getUsername() { return username; }
        public Set<String> getExpertise() { return expertise; }
        public Set<Long> getConflicts() { return conflicts; }

        /** True if this reviewer knows the category (case-insensitive). */
        public boolean knows(String category) {
            for (String e : expertise) {
                if (e.equalsIgnoreCase(category.trim())) {
                    return true;
                }
            }
            return false;
        }
    }

    private final Path dataDir;
    private final Path file;
    private final Path assignments;

    public ReviewerCSV(Path dataDir) {
        this.dataDir = dataDir.toAbsolutePath().normalize();
        this.file = this.dataDir.resolve(FILE_NAME);
        this.assignments = this.dataDir.resolve(ASSIGNMENTS_FILE);
    }

    public synchronized List<Profile> loadProfiles() throws IOException {
        List<Profile> out = new ArrayList<>();
        if (!Files.exists(file)) {
            return out;
        }
        boolean first = true;
        Set<String> seen = new LinkedHashSet<>();
        for (UserCSV.CsvRecord rec : UserCSV.parse(Files.readString(file, StandardCharsets.UTF_8))) {
            if (first) {
                first = false;
                if (!rec.fields.isEmpty() && rec.fields.get(0).trim().equalsIgnoreCase("username")) {
                    continue;
                }
            }
            if (rec.quoteError || rec.fields.isEmpty() || rec.fields.get(0).trim().isEmpty()) {
                continue;
            }
            String user = rec.fields.get(0).trim();
            if (!seen.add(user.toLowerCase())) {
                continue; // first profile for a user wins
            }
            Set<String> exp = new LinkedHashSet<>();
            if (rec.fields.size() > 1) {
                for (String s : rec.fields.get(1).split(";")) {
                    if (!s.trim().isEmpty()) {
                        exp.add(s.trim());
                    }
                }
            }
            Set<Long> conf = new LinkedHashSet<>();
            if (rec.fields.size() > 2) {
                for (String s : rec.fields.get(2).split(";")) {
                    try {
                        if (!s.trim().isEmpty()) {
                            conf.add(Long.parseLong(s.trim()));
                        }
                    } catch (NumberFormatException ignored) {
                        // skip a bad id
                    }
                }
            }
            out.add(new Profile(user, exp, conf));
        }
        return out;
    }

    /** Replaces (or adds) one reviewer's profile, keeping the others. */
    public synchronized void saveProfile(Profile profile) throws IOException {
        List<Profile> all = loadProfiles();
        all.removeIf(p -> p.getUsername().equalsIgnoreCase(profile.getUsername()));
        all.add(profile);
        StringBuilder sb = new StringBuilder("username,expertise,conflictBookIds").append(System.lineSeparator());
        for (Profile p : all) {
            List<String> ids = new ArrayList<>();
            for (Long id : p.getConflicts()) {
                ids.add(Long.toString(id));
            }
            sb.append(UserCSV.escape(p.getUsername())).append(',')
              .append(UserCSV.escape(String.join(";", p.getExpertise()))).append(',')
              .append(UserCSV.escape(String.join(";", ids))).append(System.lineSeparator());
        }
        write(file, sb.toString());
    }

    /** Saved assignments as {bookId, reviewer} pairs. */
    public synchronized List<String[]> loadAssignments() throws IOException {
        List<String[]> out = new ArrayList<>();
        if (!Files.exists(assignments)) {
            return out;
        }
        boolean first = true;
        for (UserCSV.CsvRecord rec : UserCSV.parse(Files.readString(assignments, StandardCharsets.UTF_8))) {
            if (first) {
                first = false;
                if (!rec.fields.isEmpty() && rec.fields.get(0).trim().equalsIgnoreCase("bookId")) {
                    continue;
                }
            }
            if (!rec.quoteError && rec.fields.size() >= 2) {
                out.add(new String[] { rec.fields.get(0).trim(), rec.fields.get(1).trim() });
            }
        }
        return out;
    }

    /** Replaces all saved assignments. */
    public synchronized void saveAssignments(List<String[]> pairs) throws IOException {
        StringBuilder sb = new StringBuilder("bookId,reviewer").append(System.lineSeparator());
        for (String[] p : pairs) {
            sb.append(UserCSV.escape(p[0])).append(',').append(UserCSV.escape(p[1])).append(System.lineSeparator());
        }
        write(assignments, sb.toString());
    }

    private void write(Path target, String content) throws IOException {
        Files.createDirectories(dataDir);
        Path tmp = Files.createTempFile(dataDir, "reviewers-", ".tmp");
        try {
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
