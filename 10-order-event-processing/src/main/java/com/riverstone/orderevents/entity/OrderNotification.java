package com.riverstone.orderevents.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "order_notifications")
@Getter
@Setter
public class OrderNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_ref", nullable = false, length = 32)
    private String orderRef;

    @Column(name = "channel", nullable = false, length = 16)
    private String channel;

    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
