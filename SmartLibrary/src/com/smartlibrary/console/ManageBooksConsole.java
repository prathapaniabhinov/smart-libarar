package com.smartlibrary.console;

import java.util.ArrayList;
import java.util.List;

import com.smartlibrary.model.Book;
import com.smartlibrary.service.BookService;
import com.smartlibrary.service.ServiceException;

/** Librarian / Admin: list books and import new books from .txt files. */
public class ManageBooksConsole {

    private final ConsoleIO io;
    private final BookService books;

    public ManageBooksConsole(ConsoleIO io, BookService books) {
        this.io = io;
        this.books = books;
    }

    public void run() throws ServiceException {
        List<String> options = List.of("List all books", "Import a book (.txt file)");
        while (true) {
            io.header("Manage Books");
            io.menu(options, "Back to dashboard");
            int choice = io.readChoice("\nChoose an option: ", 0, options.size());
            if (choice == 0) {
                return;
            }
            try {
                switch (choice) {
                    case 1: listBooks(); break;
                    case 2: importBook(); break;
                    default: break;
                }
            } catch (ServiceException e) {
                io.error(e.getMessage());
                if (e.getKind() == ServiceException.Kind.ACCESS_DENIED) {
                    io.pause();
                    return;
                }
                io.pause();
            }
        }
    }

    private void listBooks() throws ServiceException {
        List<Book> all = books.listBooks();
        io.header("All Books");
        if (all.isEmpty()) {
            io.info("No books yet. Use 'Import a book' to add .txt files.");
        } else {
            List<String[]> rows = new ArrayList<>();
            for (Book b : all) {
                rows.add(new String[] { Long.toString(b.getBookId()), b.getTitle(), b.getAuthor(), b.getCategory(),
                        b.getFileName() });
            }
            io.table(new String[] { "ID", "Title", "Author", "Category", "File" }, rows);
            io.info(all.size() + " book(s).");
        }
        io.pause();
    }

    private void importBook() throws ServiceException {
        io.header("Import a Book");
        io.info("Give the full path of a .txt file, e.g. D:\\Books\\data_structures.txt");
        io.info("Tip: in Windows Explorer, Shift + right-click the file -> 'Copy as path', then paste here.");
        String path = io.readLine("File path : ");
        String title = io.readLine("Title     : ");
        String author = io.readLine("Author    : ");
        String category = io.readLine("Category  : ");
        Book b = books.importBook(path, title, author, category);
        io.success("Imported '" + b.getTitle() + "' as book ID " + b.getBookId() + " (saved as data/books/"
                + b.getFileName() + ").");
        io.pause();
    }
}
