package com.vantage.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "warehouses")
@Getter
@Setter
public class Warehouse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 12, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "city", nullable = false, length = 80)
    private String city;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
