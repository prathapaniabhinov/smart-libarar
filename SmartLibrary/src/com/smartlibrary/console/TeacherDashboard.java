package com.smartlibrary.console;

/** Menu for TEACHER users. */
public class TeacherDashboard extends BaseDashboard {

    public TeacherDashboard(ConsoleIO io, Services services) {
        super(io, services, "Teacher");
    }

    @Override
    protected void buildMenu() {
        addItem("View All Books", () -> new SearchConsole(io, books).browseAll());
        addItem("Search Academic Resources", () -> new SearchConsole(io, books).run());
        addItem("My Review Assignments", () -> new MyReviewsConsole(io, reviews).run());
        addItem("Compare Documents", () -> new CompareConsole(io, books).run());
        addItem("Document Conflict Analysis (NP-Completeness)", () -> new ConflictConsole(io, analytics).run());
        addItem("Recommend Resources", () -> planned("Recommend Resources",
                "Recommend books and papers to students.",
                "Recommend a resource with a note",
                "Track recommendations per course"));
    }
}
