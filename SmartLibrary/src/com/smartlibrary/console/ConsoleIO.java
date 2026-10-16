package com.smartlibrary.console;

import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;

/**
 * All console input and output in one place: prompts, menus, messages and tables.
 * <p>
 * Note: the Eclipse Console cannot hide typed characters, so passwords are visible while typing.
 */
public class ConsoleIO {

    private static final String LINE = "------------------------------------------------------------";
    private static final int MAX_COLUMN = 30;

    private final Scanner in;
    private final PrintStream out;

    public ConsoleIO(InputStream input, PrintStream output) {
        this.in = new Scanner(input, StandardCharsets.UTF_8);
        this.out = output;
    }

    // ------------------------------------------------------------------ input

    /** Reads one line (trimmed). */
    public String readLine(String prompt) {
        out.print(prompt);
        out.flush();
        if (!in.hasNextLine()) {
            throw new InputClosedException();
        }
        return in.nextLine().trim();
    }

    /** Reads a password. Not trimmed, so leading/trailing characters are kept exactly as typed. */
    public char[] readPassword(String prompt) {
        out.print(prompt);
        out.flush();
        if (!in.hasNextLine()) {
            throw new InputClosedException();
        }
        return in.nextLine().toCharArray();
    }

    /** Reads a number between min and max (inclusive), asking again until it is valid. */
    public int readChoice(String prompt, int min, int max) {
        while (true) {
            String s = readLine(prompt);
            try {
                int n = Integer.parseInt(s);
                if (n >= min && n <= max) {
                    return n;
                }
            } catch (NumberFormatException ignored) {
                // fall through
            }
            error("Please enter a number from " + min + " to " + max + ".");
        }
    }

    /** Reads a whole number, or returns -1 if the input is blank or not a number. */
    public long readId(String prompt) {
        String s = readLine(prompt);
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Asks a yes/no question. */
    public boolean confirm(String prompt) {
        while (true) {
            String s = readLine(prompt + " (y/n): ").toLowerCase();
            if (s.equals("y") || s.equals("yes")) {
                return true;
            }
            if (s.equals("n") || s.equals("no")) {
                return false;
            }
            error("Please type y or n.");
        }
    }

    public void pause() {
        readLine("\nPress Enter to continue...");
    }

    // ------------------------------------------------------------------ output

    public void println(String s) {
        out.println(s);
    }

    public void blank() {
        out.println();
    }

    public void header(String title) {
        out.println();
        out.println(LINE);
        out.println("  " + title);
        out.println(LINE);
    }

    public void success(String message) {
        out.println("[OK] " + message);
    }

    public void error(String message) {
        out.println("[ERROR] " + message);
    }

    public void info(String message) {
        out.println("[i] " + message);
    }

    /** Prints a numbered menu. Option 0 is printed last. */
    public void menu(List<String> options, String zeroLabel) {
        for (int i = 0; i < options.size(); i++) {
            out.println("  " + (i + 1) + ". " + options.get(i));
        }
        out.println("  0. " + zeroLabel);
    }

    /** Prints rows as an aligned text table. */
    public void table(String[] headers, List<String[]> rows) {
        int[] widths = new int[headers.length];
        for (int c = 0; c < headers.length; c++) {
            widths[c] = headers[c].length();
        }
        for (String[] row : rows) {
            for (int c = 0; c < headers.length; c++) {
                widths[c] = Math.max(widths[c], cell(row, c).length());
            }
        }
        StringBuilder sep = new StringBuilder("+");
        for (int w : widths) {
            sep.append("-".repeat(w + 2)).append('+');
        }
        out.println(sep);
        out.println(formatRow(headers, widths));
        out.println(sep);
        for (String[] row : rows) {
            out.println(formatRow(row, widths));
        }
        out.println(sep);
    }

    private static String formatRow(String[] row, int[] widths) {
        StringBuilder sb = new StringBuilder("|");
        for (int c = 0; c < widths.length; c++) {
            String v = cell(row, c);
            sb.append(' ').append(v).append(" ".repeat(widths[c] - v.length())).append(" |");
        }
        return sb.toString();
    }

    /** One table cell: line breaks flattened, long text shortened. */
    private static String cell(String[] row, int c) {
        String v = c < row.length && row[c] != null ? row[c] : "";
        v = v.replace("\r", " ").replace("\n", " ");
        return v.length() > MAX_COLUMN ? v.substring(0, MAX_COLUMN - 3) + "..." : v;
    }
}
