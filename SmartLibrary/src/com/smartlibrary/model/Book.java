package com.smartlibrary.model;

/** A book in the library. Its full text is a .txt file in data/books/. */
public class Book {

    private final long bookId;
    private final String title;
    private final String author;
    private final String category;
    private final String fileName;

    public Book(long bookId, String title, String author, String category, String fileName) {
        this.bookId = bookId;
        this.title = title;
        this.author = author;
        this.category = category;
        this.fileName = fileName;
    }

    public long getBookId() { return bookId; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getCategory() { return category; }
    /** Name of the text file inside data/books/. */
    public String getFileName() { return fileName; }

    @Override
    public String toString() {
        return "Book{id=" + bookId + ", title=" + title + "}";
    }
}
