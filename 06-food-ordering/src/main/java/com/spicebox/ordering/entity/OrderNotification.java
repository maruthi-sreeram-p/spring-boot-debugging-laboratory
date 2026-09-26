package com.spicebox.ordering.entity;

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

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    @Column(name = "recipient", nullable = false, length = 180)
    private String recipient;

    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt = Instant.now();
}
