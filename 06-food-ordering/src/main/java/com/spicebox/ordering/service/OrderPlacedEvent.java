package com.spicebox.ordering.service;

/**
 * Raised when a customer order has been written. The kitchen intake listener picks it up
 * and puts the order in front of the restaurant.
 */
public record OrderPlacedEvent(Long orderId, String orderCode) {
}
