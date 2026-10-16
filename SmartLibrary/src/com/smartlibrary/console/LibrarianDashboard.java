package com.smartlibrary.console;

/** Menu for LIBRARIAN users. */
public class LibrarianDashboard extends BaseDashboard {

    public LibrarianDashboard(ConsoleIO io, Services services) {
        super(io, services, "Librarian");
    }

    @Override
    protected void buildMenu() {
        addItem("View All Books", () -> new SearchConsole(io, books).browseAll());
        addItem("Manage Books (list / import .txt)", () -> new ManageBooksConsole(io, books).run());
        addItem("Search Documents", () -> new SearchConsole(io, books).run());
        addItem("Compare Documents", () -> new CompareConsole(io, books).run());
        addItem("Search Statistics (Optimal BST)", () -> new SearchStatsConsole(io, books).run());
        addItem("Reviewer Assignment (Network Flow)", () -> new ReviewerConsole(io, reviews).run());
        addItem("Document Conflict Analysis (NP-Completeness)", () -> new ConflictConsole(io, analytics).run());
        addItem("Library Analytics (Randomized + Parallel)", () -> new AnalyticsConsole(io, analytics).run());
        addItem("Issue and Return Books", () -> planned("Issue and Return Books",
                "Issue books to students and teachers and record returns.",
                "Issue a copy to a user with a due date",
                "Record returns and update availability"));
        addItem("Borrowing Records", () -> planned("Borrowing Records",
                "View all borrowing records and track overdue books.",
                "Full borrowing history per book and per user",
                "Overdue list sorted by days overdue"));
    }
}
