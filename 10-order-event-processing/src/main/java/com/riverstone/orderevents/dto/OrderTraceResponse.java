package com.riverstone.orderevents.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderTraceResponse {

    private OrderResponse order;
    private List<EventLogResponse> events;
    private List<String> reservations;
    private List<String> notifications;
}
