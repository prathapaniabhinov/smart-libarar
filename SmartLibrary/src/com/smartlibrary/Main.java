package com.smartlibrary;

import java.nio.file.Path;

import com.smartlibrary.auth.AuthService;
import com.smartlibrary.auth.InitialSetup;
import com.smartlibrary.console.BaseDashboard;
import com.smartlibrary.console.ConsoleApp;
import com.smartlibrary.console.ConsoleIO;
import com.smartlibrary.data.BookCSV;
import com.smartlibrary.data.ReviewerCSV;
import com.smartlibrary.data.UsageLogCSV;
import com.smartlibrary.data.UserCSV;
import com.smartlibrary.service.AnalyticsService;
import com.smartlibrary.service.BookService;
import com.smartlibrary.service.ReviewService;
import com.smartlibrary.service.UserService;

/**
 * Entry point for SmartLibrary - Intelligent Digital Library Platform (console version).
 * Run it in Eclipse with Run As -> Java Application and use the Console view.
 * <p>
 * Data folder: {@code -Dsmartlibrary.dataDir=...}, else the {@code SMARTLIBRARY_DATA_DIR}
 * environment variable, else {@code data/} in the working directory (the project folder in Eclipse).
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        Path dataDir = UserCSV.resolveDefaultDataDir();
        UserCSV store = new UserCSV(dataDir);
        BookCSV bookStore = new BookCSV(dataDir);
        AuthService auth = new AuthService(store);
        UserService users = new UserService(store, auth, new InitialSetup(dataDir));
        BookService books = new BookService(bookStore, auth);
        ReviewService reviews = new ReviewService(store, bookStore, new ReviewerCSV(dataDir), auth);
        AnalyticsService analytics = new AnalyticsService(books, new UsageLogCSV(dataDir), auth);

        System.out.println("Data folder: " + dataDir);
        BaseDashboard.Services services = new BaseDashboard.Services(auth, users, books, reviews, analytics);
        new ConsoleApp(new ConsoleIO(System.in, System.out), auth, users, services).run();
    }
}
