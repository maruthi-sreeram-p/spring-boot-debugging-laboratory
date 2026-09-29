package com.riverstone.orderevents.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EventLogResponse {

    private Long id;
    private String topic;
    private Integer partition;
    private Long offset;
    private String messageKey;
    private String eventType;
    private String orderRef;
    private String consumerGroup;
    private String handledBy;
    private Instant createdAt;
}
