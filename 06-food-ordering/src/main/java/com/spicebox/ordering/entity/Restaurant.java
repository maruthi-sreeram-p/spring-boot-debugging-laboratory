package com.spicebox.ordering.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "restaurants")
@Getter
@Setter
public class Restaurant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "cuisine", nullable = false, length = 60)
    private String cuisine;

    @Column(name = "city", nullable = false, length = 60)
    private String city;

    @Column(name = "prep_minutes", nullable = false)
    private Integer prepMinutes = 20;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
