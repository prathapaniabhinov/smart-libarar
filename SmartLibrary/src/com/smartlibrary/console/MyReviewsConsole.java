package com.smartlibrary.console;

import java.util.ArrayList;
import java.util.List;

import com.smartlibrary.model.Book;
import com.smartlibrary.service.ReviewService;
import com.smartlibrary.service.ServiceException;

/** Teacher view of the papers assigned to them by the Reviewer Assignment (Module 4). */
public class MyReviewsConsole {

    private final ConsoleIO io;
    private final ReviewService reviews;

    public MyReviewsConsole(ConsoleIO io, ReviewService reviews) {
        this.io = io;
        this.reviews = reviews;
    }

    public void run() throws ServiceException {
        io.header("My Review Assignments");
        List<Book> mine = reviews.myAssignments();
        if (mine.isEmpty()) {
            io.info("No papers are assigned to you yet. A Librarian assigns them with Reviewer Assignment.");
        } else {
            List<String[]> rows = new ArrayList<>();
            for (Book b : mine) {
                rows.add(new String[] { Long.toString(b.getBookId()), b.getTitle(), b.getAuthor(), b.getCategory() });
            }
            io.table(new String[] { "ID", "Paper", "Author", "Category" }, rows);
            io.info(mine.size() + " paper(s) to review. Use Search Academic Resources to read inside them.");
        }
        io.pause();
    }
}
