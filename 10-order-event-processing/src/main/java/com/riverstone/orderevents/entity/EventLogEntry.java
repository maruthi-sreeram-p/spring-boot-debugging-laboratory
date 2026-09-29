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

/**
 * Every event a consumer takes off a topic is recorded here with the coordinates it came
 * from. It is the only way to see, after the fact, which partition carried a message and
 * which consumer picked it up.
 */
@Entity
@Table(name = "event_log")
@Getter
@Setter
public class EventLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "topic", nullable = false, length = 64)
    private String topic;

    @Column(name = "partition_no", nullable = false)
    private Integer partitionNo;

    @Column(name = "offset_no", nullable = false)
    private Long offsetNo;

    @Column(name = "message_key", length = 64)
    private String messageKey;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "order_ref", nullable = false, length = 32)
    private String orderRef;

    @Column(name = "consumer_group", nullable = false, length = 64)
    private String consumerGroup;

    @Column(name = "handled_by", nullable = false, length = 64)
    private String handledBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
