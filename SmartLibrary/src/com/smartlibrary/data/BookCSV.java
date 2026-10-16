package com.smartlibrary.data;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.smartlibrary.model.Book;

/**
 * Stores the book catalogue in {@code <dataDir>/books.csv} and each book's text in
 * {@code <dataDir>/books/<file>.txt}.
 * <p>
 * Same safety rules as {@link UserCSV}: proper CSV quoting, malformed rows skipped but kept,
 * atomic saves. Book files are only ever read from inside the books folder.
 */
public class BookCSV {

    public static final String FILE_NAME = "books.csv";
    public static final String BOOKS_DIR = "books";
    /** Largest .txt file that can be imported (10 MB). */
    public static final long MAX_FILE_BYTES = 10L * 1024 * 1024;

    static final String[] HEADER = { "bookId", "title", "author", "category", "fileName" };

    private final Path dataDir;
    private final Path file;
    private final Path booksDir;
    private final List<String> lastWarnings = new ArrayList<>();

    public BookCSV(Path dataDir) {
        this.dataDir = dataDir.toAbsolutePath().normalize();
        this.file = this.dataDir.resolve(FILE_NAME);
        this.booksDir = this.dataDir.resolve(BOOKS_DIR);
    }

    public Path getFile() {
        return file;
    }

    public Path getDataDir() {
        return dataDir;
    }

    public Path getBooksDir() {
        return booksDir;
    }

    public synchronized List<String> getLastWarnings() {
        return Collections.unmodifiableList(new ArrayList<>(lastWarnings));
    }

    public synchronized List<Book> loadAll() throws IOException {
        return new ArrayList<>(read().books);
    }

    public synchronized Optional<Book> findById(long bookId) throws IOException {
        for (Book b : read().books) {
            if (b.getBookId() == bookId) {
                return Optional.of(b);
            }
        }
        return Optional.empty();
    }

    /**
     * Copies a .txt file into the books folder and adds it to books.csv.
     *
     * @throws IllegalArgumentException if the source is missing, not a .txt file, empty or too large
     */
    public synchronized Book importText(Path source, String title, String author, String category)
            throws IOException {
        Path src = source.toAbsolutePath().normalize();
        if (!Files.isRegularFile(src)) {
            throw new IllegalArgumentException("File not found: " + src);
        }
        if (!src.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".txt")) {
            throw new IllegalArgumentException("Only .txt files can be imported.");
        }
        long size = Files.size(src);
        if (size == 0) {
            throw new IllegalArgumentException("The file is empty.");
        }
        if (size > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("The file is larger than 10 MB.");
        }
        String text = readTextFile(src);

        Snapshot snap = read();
        long nextId = 1;
        for (Book b : snap.books) {
            nextId = Math.max(nextId, b.getBookId() + 1);
        }
        for (List<String> raw : snap.invalidRows) {
            nextId = Math.max(nextId, parseIdLoosely(raw) + 1);
        }
        String fileName = nextId + "_" + slug(title) + ".txt";

        Files.createDirectories(booksDir);
        Path target = booksDir.resolve(fileName);
        writeAtomically(target, text);
        Book book = new Book(nextId, title, author, category, fileName);
        snap.books.add(book);
        try {
            write(snap);
        } catch (IOException e) {
            Files.deleteIfExists(target); // keep folder and catalogue consistent
            throw e;
        }
        return book;
    }

    /** Reads a book's text. Refuses file names that point outside the books folder. */
    public String readBookText(Book book) throws IOException {
        Path p = booksDir.resolve(book.getFileName()).normalize();
        if (!p.startsWith(booksDir) || p.equals(booksDir)) {
            throw new IOException("Invalid file name for book #" + book.getBookId());
        }
        if (!Files.isRegularFile(p)) {
            throw new IOException("Text file for '" + book.getTitle() + "' is missing: " + p);
        }
        return readTextFile(p);
    }

    /** Reads UTF-8 text, falling back to ISO-8859-1 for files saved in an older encoding. Strips a BOM. */
    static String readTextFile(Path p) throws IOException {
        byte[] bytes = Files.readAllBytes(p);
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            text = new String(bytes, StandardCharsets.ISO_8859_1);
        }
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    // ------------------------------------------------------------------ internals

    private static final class Snapshot {
        final List<Book> books = new ArrayList<>();
        final List<List<String>> invalidRows = new ArrayList<>();
    }

    private Snapshot read() throws IOException {
        lastWarnings.clear();
        Snapshot snap = new Snapshot();
        if (!Files.exists(file)) {
            Files.createDirectories(dataDir);
            writeAtomically(file, String.join(",", HEADER) + System.lineSeparator());
            return snap;
        }
        List<UserCSV.CsvRecord> records = UserCSV.parse(Files.readString(file, StandardCharsets.UTF_8));
        boolean first = true;
        for (UserCSV.CsvRecord rec : records) {
            if (first) {
                first = false;
                if (!rec.fields.isEmpty() && rec.fields.get(0).trim().equalsIgnoreCase(HEADER[0])) {
                    continue;
                }
            }
            if (rec.quoteError) {
                lastWarnings.add("books.csv line " + rec.line + ": broken quotes, record skipped.");
                snap.invalidRows.add(rec.fields);
                continue;
            }
            try {
                Book b = toBook(rec.fields);
                for (Book existing : snap.books) {
                    if (existing.getBookId() == b.getBookId()) {
                        throw new IllegalArgumentException("duplicate bookId");
                    }
                }
                snap.books.add(b);
            } catch (IllegalArgumentException e) {
                lastWarnings.add("books.csv line " + rec.line + ": " + e.getMessage() + ", record skipped.");
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
        for (Book b : snap.books) {
            appendRow(sb, List.of(Long.toString(b.getBookId()), b.getTitle(), b.getAuthor(), b.getCategory(),
                    b.getFileName()));
            sb.append(nl);
        }
        for (List<String> raw : snap.invalidRows) {
            appendRow(sb, raw);
            sb.append(nl);
        }
        writeAtomically(file, sb.toString());
    }

    private static Book toBook(List<String> f) {
        if (f.size() < HEADER.length) {
            throw new IllegalArgumentException("expected " + HEADER.length + " fields but found " + f.size());
        }
        long id;
        try {
            id = Long.parseLong(f.get(0).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid bookId '" + f.get(0) + "'");
        }
        if (id <= 0) {
            throw new IllegalArgumentException("bookId must be positive");
        }
        String title = f.get(1).trim();
        String fileName = f.get(4).trim();
        if (title.isEmpty()) {
            throw new IllegalArgumentException("title is empty");
        }
        if (fileName.isEmpty()) {
            throw new IllegalArgumentException("fileName is empty");
        }
        return new Book(id, title, f.get(2).trim(), f.get(3).trim(), fileName);
    }

    private static void appendRow(StringBuilder sb, List<String> fields) {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(UserCSV.escape(fields.get(i)));
        }
    }

    private static void writeAtomically(Path target, String content) throws IOException {
        Path dir = target.getParent();
        Files.createDirectories(dir);
        Path tmp = Files.createTempFile(dir, "tmp-", ".part");
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

    private static long parseIdLoosely(List<String> raw) {
        try {
            return raw.isEmpty() ? 0 : Long.parseLong(raw.get(0).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** "Data Structures in Java!" -> "data_structures_in_java" (safe file name part). */
    private static String slug(String title) {
        String s = title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (s.length() > 40) {
            s = s.substring(0, 40);
        }
        return s.isEmpty() ? "book" : s;
    }
}
