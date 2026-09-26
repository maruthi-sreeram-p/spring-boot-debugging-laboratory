package com.spicebox.ordering.service;

import com.spicebox.ordering.dto.NotificationResponse;
import com.spicebox.ordering.entity.FoodOrder;
import com.spicebox.ordering.entity.OrderNotification;
import com.spicebox.ordering.exception.ResourceNotFoundException;
import com.spicebox.ordering.mapper.OrderingMapper;
import com.spicebox.ordering.repository.FoodOrderRepository;
import com.spicebox.ordering.repository.OrderNotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Order confirmations are sent on a background thread so that checkout does not wait on
 * the mail gateway. The recipient is the signed-in customer who placed the order.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final FoodOrderRepository foodOrderRepository;
    private final OrderNotificationRepository orderNotificationRepository;
    private final OrderingMapper orderingMapper;

    public NotificationService(FoodOrderRepository foodOrderRepository,
                               OrderNotificationRepository orderNotificationRepository,
                               OrderingMapper orderingMapper) {
        this.foodOrderRepository = foodOrderRepository;
        this.orderNotificationRepository = orderNotificationRepository;
        this.orderingMapper = orderingMapper;
    }

    @Async
    @Transactional
    public void sendOrderConfirmation(Long orderId) {
        String recipient = SecurityContextHolder.getContext().getAuthentication().getName();

        FoodOrder order = foodOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        OrderNotification notification = new OrderNotification();
        notification.setOrderId(order.getId());
        notification.setChannel("EMAIL");
        notification.setRecipient(recipient);
        notification.setSubject("Your Spicebox order " + order.getOrderCode() + " is confirmed");
        orderNotificationRepository.save(notification);

        log.info("Order confirmation for {} queued to {}", order.getOrderCode(), recipient);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> notificationsFor(Long orderId) {
        return orderingMapper.toNotificationResponses(
                orderNotificationRepository.findByOrderIdOrderBySentAtAsc(orderId));
    }
}
