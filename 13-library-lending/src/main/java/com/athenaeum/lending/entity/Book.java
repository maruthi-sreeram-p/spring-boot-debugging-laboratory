package com.athenaeum.lending.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "books")
@Getter
@Setter
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String isbn;

    @Column(nullable = false, length = 250)
    private String title;

    @Column(nullable = false, length = 180)
    private String author;

    @Column(nullable = false, length = 180)
    private String publisher;

    @Column(name = "published_year", nullable = false)
    private int publishedYear;

    @Column(name = "shelf_mark", nullable = false, length = 40)
    private String shelfMark;
}
