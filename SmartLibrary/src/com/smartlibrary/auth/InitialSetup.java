package com.smartlibrary.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * One-time setup code that protects creation of the first Admin account.
 * <p>
 * On first run (no Admin in users.csv) a random code is generated and written to
 * {@code <dataDir>/setup-code.txt}. Only someone who can open that file on this machine can
 * create the Admin. After the Admin is created the file is deleted and setup is disabled.
 * <p>
 * Alternatively the code can be supplied through the {@code SMARTLIBRARY_SETUP_SECRET}
 * environment variable, in which case no file is written.
 */
public class InitialSetup {

    public static final String CODE_FILE_NAME = "setup-code.txt";
    public static final String SECRET_ENV = "SMARTLIBRARY_SETUP_SECRET";

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O/1/I
    private static final int CODE_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Path codeFile;
    private final String envSecret;

    public InitialSetup(Path dataDir) {
        this(dataDir, System.getenv(SECRET_ENV));
    }

    /** Constructor that allows tests to control the environment secret. */
    public InitialSetup(Path dataDir, String envSecret) {
        this.codeFile = dataDir.toAbsolutePath().normalize().resolve(CODE_FILE_NAME);
        this.envSecret = (envSecret == null || envSecret.isBlank()) ? null : envSecret.trim();
    }

    public boolean usesEnvironmentSecret() {
        return envSecret != null;
    }

    public Path getCodeFile() {
        return codeFile;
    }

    /** Makes sure a setup code exists (creates the code file if needed). */
    public synchronized void prepare() throws IOException {
        if (envSecret != null) {
            return;
        }
        if (!Files.exists(codeFile) || readCodeFile().isEmpty()) {
            Files.createDirectories(codeFile.getParent());
            Files.writeString(codeFile, generateCode() + System.lineSeparator(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Prints the setup code to the console (the Eclipse Console view when run from Eclipse).
     * Only called while no Admin exists; the code stops working once the Admin is created.
     */
    public synchronized void printCodeToConsole() throws IOException {
        System.out.println();
        System.out.println("==================================================");
        System.out.println(" SmartLibrary - FIRST RUN SETUP");
        if (envSecret != null) {
            System.out.println(" Setup code: the value of " + SECRET_ENV);
        } else {
            System.out.println(" Setup code: " + readCodeFile());
            System.out.println(" (also saved in " + codeFile + ")");
        }
        System.out.println(" Type it below when asked for the 'Setup code'.");
        System.out.println("==================================================");
        System.out.println();
    }

    /** Checks an entered code in constant time. */
    public synchronized boolean verify(String enteredCode) throws IOException {
        if (enteredCode == null || enteredCode.trim().isEmpty()) {
            return false;
        }
        String expected = envSecret != null ? envSecret : (Files.exists(codeFile) ? readCodeFile() : "");
        if (expected.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                enteredCode.trim().getBytes(StandardCharsets.UTF_8));
    }

    /** Deletes the code file once the Admin has been created. */
    public synchronized void consume() throws IOException {
        Files.deleteIfExists(codeFile);
    }

    private String readCodeFile() throws IOException {
        return Files.readString(codeFile, StandardCharsets.UTF_8).trim();
    }

    private static String generateCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            if (i > 0 && i % 4 == 0) {
                sb.append('-');
            }
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
