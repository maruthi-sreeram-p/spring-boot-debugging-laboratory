package com.hirestack.portal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "candidates")
@Getter
@Setter
public class Candidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "email", nullable = false, length = 180, unique = true)
    private String email;

    @Column(name = "phone", length = 24)
    private String phone;

    @Column(name = "headline", nullable = false, length = 200)
    private String headline;

    @Column(name = "location", nullable = false, length = 80)
    private String location;

    @Column(name = "years_experience", nullable = false)
    private Integer yearsExperience = 0;

    @Column(name = "skills", nullable = false, length = 400)
    private String skills = "";

    @Column(name = "expected_salary", precision = 12, scale = 2)
    private BigDecimal expectedSalary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
