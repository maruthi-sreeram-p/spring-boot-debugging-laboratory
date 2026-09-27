package com.hirestack.portal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "companies")
@Getter
@Setter
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 140)
    private String name;

    @Column(name = "slug", nullable = false, length = 140, unique = true)
    private String slug;

    @Column(name = "industry", nullable = false, length = 80)
    private String industry;

    @Column(name = "city", nullable = false, length = 80)
    private String city;

    @Column(name = "verified", nullable = false)
    private boolean verified;
}
