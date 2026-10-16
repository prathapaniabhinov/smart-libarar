package com.smartlibrary.console;

/** Menu for STUDENT users. */
public class StudentDashboard extends BaseDashboard {

    public StudentDashboard(ConsoleIO io, Services services) {
        super(io, services, "Student");
    }

    @Override
    protected void buildMenu() {
        addItem("View All Books", () -> new SearchConsole(io, books).browseAll());
        addItem("Search Books", () -> new SearchConsole(io, books).run());
        addItem("Compare Documents", () -> new CompareConsole(io, books).run());
        addItem("Document Conflict Analysis (NP-Completeness)", () -> new ConflictConsole(io, analytics).run());
        addItem("Search Research Papers", () -> planned("Search Research Papers",
                "Search research-paper titles and abstracts, and compare papers.",
                "Abstract and full-text keyword search",
                "Compare two research papers"));
        addItem("My Borrowing History", () -> planned("My Borrowing History",
                "See the books you have borrowed and returned. Borrowing records are not stored yet.",
                "List of borrowed and returned books",
                "Borrow and return through the librarian workflow"));
        addItem("Due Dates", () -> planned("Due Dates",
                "See upcoming due dates and overdue books.",
                "Upcoming due dates sorted by date",
                "Overdue warnings"));
    }
}
