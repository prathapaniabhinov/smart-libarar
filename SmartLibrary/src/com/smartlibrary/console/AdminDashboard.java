package com.smartlibrary.console;

import com.smartlibrary.auth.Role;
import com.smartlibrary.service.ServiceException;
import com.smartlibrary.service.UserService.UserStatistics;

/** Menu for ADMIN users. */
public class AdminDashboard extends BaseDashboard {

    public AdminDashboard(ConsoleIO io, Services services) {
        super(io, services, "Admin");
    }

    @Override
    protected void buildMenu() {
        addItem("Manage Users", () -> new ManageUsersConsole(io, users).run());
        addItem("Manage Library Resources", () -> new ManageBooksConsole(io, books).run());
        addItem("System Statistics", this::showStatistics);
        addItem("Search Statistics (Optimal BST)", () -> new SearchStatsConsole(io, books).run());
        addItem("Reviewer Assignment (Network Flow)", () -> new ReviewerConsole(io, reviews).run());
        addItem("Document Conflict Analysis (NP-Completeness)", () -> new ConflictConsole(io, analytics).run());
        addItem("Library Analytics (Randomized + Parallel)", () -> new AnalyticsConsole(io, analytics).run());
    }

    private void showStatistics() throws ServiceException {
        UserStatistics s = users.getStatistics();
        io.header("System Statistics (live from users.csv and books.csv)");
        io.println("  Total accounts            : " + s.getTotal());
        io.println("  Active                    : " + s.getActive());
        io.println("  Inactive                  : " + s.getInactive());
        io.println("  Awaiting password change  : " + s.getPendingPasswordChange());
        io.blank();
        io.println("  Admins                    : " + s.getCount(Role.ADMIN));
        io.println("  Librarians                : " + s.getCount(Role.LIBRARIAN));
        io.println("  Teachers                  : " + s.getCount(Role.TEACHER));
        io.println("  Students                  : " + s.getCount(Role.STUDENT));
        io.blank();
        io.println("  Books in library          : " + books.listBooks().size());
        if (s.getSkippedRecords() > 0) {
            io.blank();
            io.error(s.getSkippedRecords() + " malformed record(s) in users.csv were skipped.");
        }
        io.pause();
    }
}
