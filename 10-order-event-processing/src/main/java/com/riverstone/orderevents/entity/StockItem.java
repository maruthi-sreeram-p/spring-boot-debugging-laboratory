package com.riverstone.orderevents.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "stock_items")
@Getter
@Setter
public class StockItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sku", nullable = false, length = 32, unique = true)
    private String sku;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "available", nullable = false)
    private Integer available = 0;

    @Column(name = "reserved", nullable = false)
    private Integer reserved = 0;
}
