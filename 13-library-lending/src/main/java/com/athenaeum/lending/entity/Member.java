package com.athenaeum.lending.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "members")
@Getter
@Setter
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "membership_number", nullable = false, unique = true, length = 20)
    private String membershipNumber;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(nullable = false, unique = true, length = 190)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberTier tier = MemberTier.STANDARD;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberStatus status = MemberStatus.ACTIVE;

    /** Kept in step with the loans table so the borrowing limit can be checked cheaply. */
    @Column(name = "active_loan_count", nullable = false)
    private int activeLoanCount;

    @Column(name = "joined_on", nullable = false)
    private LocalDate joinedOn;
}
