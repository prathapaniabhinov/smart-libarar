package com.smartlibrary.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Counts how often each keyword is searched, in {@code <dataDir>/search_log.csv}
 * ({@code keyword,count}). Used to build the Optimal Binary Search Tree (DSA Module 3).
 */
public class SearchLogCSV {

    public static final String FILE_NAME = "search_log.csv";
    private static final String HEADER = "keyword,count";

    private final Path dataDir;
    private final Path file;

    public SearchLogCSV(Path dataDir) {
        this.dataDir = dataDir.toAbsolutePath().normalize();
        this.file = this.dataDir.resolve(FILE_NAME);
    }

    /** Keyword to number of searches. Malformed rows are skipped. */
    public synchronized Map<String, Integer> load() throws IOException {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (!Files.exists(file)) {
            return counts;
        }
        boolean first = true;
        for (UserCSV.CsvRecord rec : UserCSV.parse(Files.readString(file, StandardCharsets.UTF_8))) {
            if (first) {
                first = false;
                if (!rec.fields.isEmpty() && rec.fields.get(0).trim().equalsIgnoreCase("keyword")) {
                    continue;
                }
            }
            if (rec.quoteError || rec.fields.size() < 2) {
                continue;
            }
            String keyword = rec.fields.get(0).trim().toLowerCase(Locale.ROOT);
            try {
                int count = Integer.parseInt(rec.fields.get(1).trim());
                if (!keyword.isEmpty() && count > 0) {
                    counts.merge(keyword, count, Integer::sum);
                }
            } catch (NumberFormatException ignored) {
                // skip malformed row
            }
        }
        return counts;
    }

    /** Adds one search for each keyword. */
    public synchronized void record(Collection<String> keywords) throws IOException {
        Map<String, Integer> counts = load();
        for (String k : keywords) {
            String key = k.trim().toLowerCase(Locale.ROOT);
            if (!key.isEmpty()) {
                counts.merge(key, 1, Integer::sum);
            }
        }
        StringBuilder sb = new StringBuilder(HEADER).append(System.lineSeparator());
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            sb.append(UserCSV.escape(e.getKey())).append(',').append(e.getValue()).append(System.lineSeparator());
        }
        Files.createDirectories(dataDir);
        Path tmp = Files.createTempFile(dataDir, "search-log-", ".tmp");
        try {
            Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
