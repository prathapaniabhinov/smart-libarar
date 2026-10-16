package com.smartlibrary.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.smartlibrary.auth.Role;
import com.smartlibrary.model.AccountStatus;
import com.smartlibrary.model.User;

/**
 * Reads and writes user accounts in {@code <dataDir>/users.csv}.
 * <p>
 * Features:
 * <ul>
 *   <li>RFC 4180 style CSV: fields containing commas, quotes or line breaks are quoted.</li>
 *   <li>Missing file or folder is created automatically with just the header.</li>
 *   <li>Malformed records are skipped when reading (reported in {@link #getLastWarnings()}) but are
 *       written back unchanged on every save, so no data is silently lost.</li>
 *   <li>Every change is a read-modify-write done under one lock and saved through a temp file plus
 *       atomic rename, so a crash cannot leave a half-written users.csv.</li>
 * </ul>
 */
public class UserCSV {

    public static final String FILE_NAME = "users.csv";

    /** System property that overrides the data directory, e.g. {@code -Dsmartlibrary.dataDir=C:\SmartLibrary\data}. */
    public static final String DATA_DIR_PROPERTY = "smartlibrary.dataDir";
    /** Environment variable that overrides the data directory when the system property is not set. */
    public static final String DATA_DIR_ENV = "SMARTLIBRARY_DATA_DIR";

    static final String[] HEADER = {
        "userId", "name", "username", "email", "passwordHash", "role", "status", "mustChangePassword"
    };
    private static final int REQUIRED_FIELDS = 7;

    private final Path dataDir;
    private final Path file;
    private final List<String> lastWarnings = new ArrayList<>();

    public UserCSV(Path dataDir) {
        this.dataDir = dataDir.toAbsolutePath().normalize();
        this.file = this.dataDir.resolve(FILE_NAME);
    }

    /**
     * Data directory used by the application: system property, then environment variable,
     * then {@code data} relative to the working directory (the project folder when run from Eclipse).
     */
    public static Path resolveDefaultDataDir() {
        String fromProperty = System.getProperty(DATA_DIR_PROPERTY);
        if (fromProperty != null && !fromProperty.isBlank()) {
            return Paths.get(fromProperty.trim()).toAbsolutePath().normalize();
        }
        String fromEnv = System.getenv(DATA_DIR_ENV);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return Paths.get(fromEnv.trim()).toAbsolutePath().normalize();
        }
        return Paths.get("data").toAbsolutePath().normalize();
    }

    public Path getDataDir() {
        return dataDir;
    }

    public Path getFile() {
        return file;
    }

    /** Warnings (skipped malformed lines, duplicates) from the most recent read. */
    public synchronized List<String> getLastWarnings() {
        return Collections.unmodifiableList(new ArrayList<>(lastWarnings));
    }

    /** Creates the data folder and a header-only users.csv if they do not exist yet. */
    public synchronized void ensureFileExists() throws IOException {
        Files.createDirectories(dataDir);
        if (!Files.exists(file)) {
            writeAtomically(String.join(",", HEADER) + System.lineSeparator());
        }
    }

    /** Returns copies of all valid user records, in file order. */
    public synchronized List<User> loadAll() throws IOException {
        List<User> result = new ArrayList<>();
        for (User u : read().users) {
            result.add(u.copy());
        }
        return result;
    }

    public synchronized Optional<User> findById(long userId) throws IOException {
        for (User u : read().users) {
            if (u.getUserId() == userId) {
                return Optional.of(u.copy());
            }
        }
        return Optional.empty();
    }

    /** Finds a user whose username or email matches (case-insensitive). */
    public synchronized Optional<User> findByUsernameOrEmail(String identifier) throws IOException {
        if (identifier == null) {
            return Optional.empty();
        }
        String key = identifier.trim();
        for (User u : read().users) {
            if (u.getUsername().equalsIgnoreCase(key) || u.getEmail().equalsIgnoreCase(key)) {
                return Optional.of(u.copy());
            }
        }
        return Optional.empty();
    }

    /**
     * Adds a new user. A unique userId (highest existing id + 1) is assigned, ignoring the id on the
     * argument. Username and email must not already be used.
     *
     * @return the stored user, with its new id
     */
    public synchronized User insert(User newUser) throws IOException, DuplicateUserException {
        Snapshot snap = read();
        checkUnique(snap.users, newUser, -1);
        long nextId = 1;
        for (User u : snap.users) {
            nextId = Math.max(nextId, u.getUserId() + 1);
        }
        for (List<String> raw : snap.invalidRows) {
            nextId = Math.max(nextId, parseIdLoosely(raw) + 1);
        }
        User stored = newUser.copy();
        stored.setUserId(nextId);
        snap.users.add(stored);
        write(snap);
        return stored.copy();
    }

    /**
     * Replaces the record with the same userId. All other records (including malformed ones) are kept.
     *
     * @throws IllegalArgumentException if no user has that id
     */
    public synchronized void update(User updated) throws IOException, DuplicateUserException {
        Snapshot snap = read();
        int index = -1;
        for (int i = 0; i < snap.users.size(); i++) {
            if (snap.users.get(i).getUserId() == updated.getUserId()) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            throw new IllegalArgumentException("No user with id " + updated.getUserId());
        }
        checkUnique(snap.users, updated, updated.getUserId());
        snap.users.set(index, updated.copy());
        write(snap);
    }

    // ------------------------------------------------------------------ internals

    private static final class Snapshot {
        final List<User> users = new ArrayList<>();
        final List<List<String>> invalidRows = new ArrayList<>();
    }

    private static void checkUnique(List<User> users, User candidate, long ignoreId) throws DuplicateUserException {
        for (User u : users) {
            if (u.getUserId() == ignoreId) {
                continue;
            }
            if (u.getUsername().equalsIgnoreCase(candidate.getUsername())) {
                throw new DuplicateUserException("Username '" + candidate.getUsername() + "' is already taken.");
            }
            if (u.getEmail().equalsIgnoreCase(candidate.getEmail())) {
                throw new DuplicateUserException("Email '" + candidate.getEmail() + "' is already registered.");
            }
        }
    }

    private Snapshot read() throws IOException {
        lastWarnings.clear();
        Snapshot snap = new Snapshot();
        if (!Files.exists(file)) {
            ensureFileExists();
            return snap;
        }
        String text = Files.readString(file, StandardCharsets.UTF_8);
        List<CsvRecord> records = parse(text);
        boolean first = true;
        for (CsvRecord rec : records) {
            if (first) {
                first = false;
                if (!rec.fields.isEmpty() && rec.fields.get(0).trim().equalsIgnoreCase(HEADER[0])) {
                    continue; // header
                }
            }
            if (rec.quoteError) {
                lastWarnings.add("Line " + rec.line + ": broken quotes, record skipped.");
                snap.invalidRows.add(rec.fields);
                continue;
            }
            try {
                User u = toUser(rec.fields);
                for (User existing : snap.users) {
                    if (existing.getUserId() == u.getUserId()
                            || existing.getUsername().equalsIgnoreCase(u.getUsername())
                            || existing.getEmail().equalsIgnoreCase(u.getEmail())) {
                        throw new IllegalArgumentException("duplicate id, username or email of an earlier record");
                    }
                }
                snap.users.add(u);
            } catch (IllegalArgumentException e) {
                lastWarnings.add("Line " + rec.line + ": " + e.getMessage() + ", record skipped.");
                snap.invalidRows.add(rec.fields);
            }
        }
        return snap;
    }

    private void write(Snapshot snap) throws IOException {
        StringBuilder sb = new StringBuilder();
        String nl = System.lineSeparator();
        appendRow(sb, List.of(HEADER));
        sb.append(nl);
        for (User u : snap.users) {
            appendRow(sb, toFields(u));
            sb.append(nl);
        }
        for (List<String> raw : snap.invalidRows) {
            appendRow(sb, raw);
            sb.append(nl);
        }
        writeAtomically(sb.toString());
    }

    private void writeAtomically(String content) throws IOException {
        Files.createDirectories(dataDir);
        Path tmp = Files.createTempFile(dataDir, "users-", ".csv.tmp");
        try {
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static User toUser(List<String> f) {
        if (f.size() < REQUIRED_FIELDS) {
            throw new IllegalArgumentException("expected at least " + REQUIRED_FIELDS + " fields but found " + f.size());
        }
        long id;
        try {
            id = Long.parseLong(f.get(0).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid userId '" + f.get(0) + "'");
        }
        if (id <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        String name = f.get(1).trim();
        String username = f.get(2).trim();
        String email = f.get(3).trim();
        String hash = f.get(4).trim();
        if (username.isEmpty()) {
            throw new IllegalArgumentException("username is empty");
        }
        if (email.isEmpty()) {
            throw new IllegalArgumentException("email is empty");
        }
        if (hash.isEmpty()) {
            throw new IllegalArgumentException("passwordHash is empty");
        }
        Role role = Role.parse(f.get(5));
        AccountStatus status = AccountStatus.parse(f.get(6));
        boolean mustChange = f.size() > 7 && "true".equalsIgnoreCase(f.get(7).trim());
        return new User(id, name, username, email, hash, role, status, mustChange);
    }

    private static List<String> toFields(User u) {
        List<String> f = new ArrayList<>();
        f.add(Long.toString(u.getUserId()));
        f.add(nullToEmpty(u.getName()));
        f.add(nullToEmpty(u.getUsername()));
        f.add(nullToEmpty(u.getEmail()));
        f.add(nullToEmpty(u.getPasswordHash()));
        f.add(u.getRole().name());
        f.add(u.getStatus().name());
        f.add(Boolean.toString(u.isMustChangePassword()));
        return f;
    }

    private static long parseIdLoosely(List<String> raw) {
        if (raw.isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(raw.get(0).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    // ------------------------------------------------------------------ CSV parsing / escaping

    static final class CsvRecord {
        final int line;
        final List<String> fields;
        final boolean quoteError;

        CsvRecord(int line, List<String> fields, boolean quoteError) {
            this.line = line;
            this.fields = fields;
            this.quoteError = quoteError;
        }
    }

    /** Parses CSV text into records. Quoted fields may contain commas, doubled quotes and line breaks. */
    static List<CsvRecord> parse(String text) {
        List<CsvRecord> out = new ArrayList<>();
        int i = 0;
        int n = text.length();
        if (n > 0 && text.charAt(0) == '\uFEFF') {
            i = 1; // UTF-8 BOM written by Excel
        }
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean fieldQuoted = false;
        boolean quoteError = false;
        int line = 1;
        int recordLine = 1;

        while (i < n) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < n && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i += 2;
                        continue;
                    }
                    inQuotes = false;
                } else {
                    if (c == '\n') {
                        line++;
                    }
                    field.append(c);
                }
                i++;
                continue;
            }
            switch (c) {
                case '"':
                    if (field.length() == 0 && !fieldQuoted) {
                        inQuotes = true;
                        fieldQuoted = true;
                    } else {
                        quoteError = true;
                        field.append(c);
                    }
                    break;
                case ',':
                    fields.add(field.toString());
                    field.setLength(0);
                    fieldQuoted = false;
                    break;
                case '\r':
                    break;
                case '\n':
                    fields.add(field.toString());
                    addIfNotBlank(out, recordLine, fields, fieldQuoted, quoteError);
                    fields = new ArrayList<>();
                    field.setLength(0);
                    fieldQuoted = false;
                    quoteError = false;
                    line++;
                    recordLine = line;
                    break;
                default:
                    if (fieldQuoted) {
                        quoteError = true; // text after a closing quote, e.g. "abc"def
                    }
                    field.append(c);
                    break;
            }
            i++;
        }
        if (inQuotes) {
            quoteError = true;
        }
        fields.add(field.toString());
        addIfNotBlank(out, recordLine, fields, fieldQuoted, quoteError);
        return out;
    }

    private static void addIfNotBlank(List<CsvRecord> out, int line, List<String> fields,
                                      boolean lastFieldQuoted, boolean quoteError) {
        boolean blank = fields.size() == 1 && fields.get(0).trim().isEmpty() && !lastFieldQuoted;
        if (!blank) {
            out.add(new CsvRecord(line, fields, quoteError));
        }
    }

    private static void appendRow(StringBuilder sb, List<String> fields) {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(fields.get(i)));
        }
    }

    /** Quotes a field when it contains a comma, quote, line break or leading/trailing space. */
    static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuotes = value.indexOf(',') >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0
                || (!value.isEmpty() && (Character.isWhitespace(value.charAt(0))
                        || Character.isWhitespace(value.charAt(value.length() - 1))));
        if (!needsQuotes) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
