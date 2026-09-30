package com.athenaeum.lending.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BookResponse {

    private final Long id;
    private final String isbn;
    private final String title;
    private final String author;
    private final String publisher;
    private final int publishedYear;
    private final String shelfMark;
    private final long totalCopies;
    private final long availableCopies;
    private final long holdsWaiting;
}
