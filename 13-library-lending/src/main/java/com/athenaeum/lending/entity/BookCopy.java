package com.athenaeum.lending.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "book_copies")
@Getter
@Setter
public class BookCopy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(nullable = false, unique = true, length = 30)
    private String barcode;

    @Column(nullable = false, length = 60)
    private String branch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CopyStatus status = CopyStatus.AVAILABLE;

    @Column(name = "acquired_on", nullable = false)
    private LocalDate acquiredOn;

    @Column(nullable = false)
    private long version;
}
