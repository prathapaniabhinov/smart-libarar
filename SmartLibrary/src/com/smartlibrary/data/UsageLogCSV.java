package com.smartlibrary.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Append-only library usage stream in {@code <dataDir>/usage_log.csv}
 * ({@code time,username,action,detail}). Read back one line at a time, so it can be sampled with
 * Reservoir Sampling without loading the whole file (DSA Module 6).
 */
public class UsageLogCSV {

    public static final String FILE_NAME = "usage_log.csv";
    private static final String HEADER = "time,username,action,detail";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** One usage event. */
    public static final class Event {
        public final String time;
        public final String username;
        public final String action;
        public final String detail;

        public Event(String time, String username, String action, String detail) {
            this.time = time;
            this.username = username;
            this.action = action;
            this.detail = detail;
        }
    }

    private final Path dataDir;
    private final Path file;

    public UsageLogCSV(Path dataDir) {
        this.dataDir = dataDir.toAbsolutePath().normalize();
        this.file = this.dataDir.resolve(FILE_NAME);
    }

    public Path getFile() {
        return file;
    }

    /** Appends one event. */
    public synchronized void append(String username, String action, String detail) throws IOException {
        Files.createDirectories(dataDir);
        StringBuilder sb = new StringBuilder();
        if (!Files.exists(file)) {
            sb.append(HEADER).append(System.lineSeparator());
        }
        sb.append(LocalDateTime.now().format(TIME)).append(',')
          .append(UserCSV.escape(username)).append(',')
          .append(UserCSV.escape(action)).append(',')
          .append(UserCSV.escape(detail.replace('\n', ' ').replace('\r', ' ')))
          .append(System.lineSeparator());
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public boolean isEmpty() throws IOException {
        if (!Files.exists(file)) {
            return true;
        }
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            r.readLine(); // header
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.isBlank()) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Streams the events one line at a time. The caller must close the returned stream.
     * Malformed lines are skipped.
     */
    public EventStream open() throws IOException {
        return new EventStream(Files.newBufferedReader(file, StandardCharsets.UTF_8));
    }

    /** Iterator over the log that reads lazily from disk. */
    public static final class EventStream implements Iterator<Event>, AutoCloseable {
        private final BufferedReader reader;
        private Event nextEvent;

        EventStream(BufferedReader reader) throws IOException {
            this.reader = reader;
            reader.readLine(); // header
            advance();
        }

        private void advance() {
            try {
                nextEvent = null;
                String line;
                while (nextEvent == null && (line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    var recs = UserCSV.parse(line);
                    if (!recs.isEmpty() && recs.get(0).fields.size() >= 4 && !recs.get(0).quoteError) {
                        var f = recs.get(0).fields;
                        nextEvent = new Event(f.get(0), f.get(1), f.get(2), f.get(3));
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public boolean hasNext() {
            return nextEvent != null;
        }

        @Override
        public Event next() {
            if (nextEvent == null) {
                throw new NoSuchElementException();
            }
            Event e = nextEvent;
            advance();
            return e;
        }

        @Override
        public void close() throws IOException {
            reader.close();
        }
    }
}
