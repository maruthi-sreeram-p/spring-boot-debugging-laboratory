package com.riverstone.orderevents.controller;

import com.riverstone.orderevents.dto.CreateOrderRequest;
import com.riverstone.orderevents.dto.OrderResponse;
import com.riverstone.orderevents.dto.OrderTraceResponse;
import com.riverstone.orderevents.dto.PagedResponse;
import com.riverstone.orderevents.entity.OrderStatus;
import com.riverstone.orderevents.exception.ResourceNotFoundException;
import com.riverstone.orderevents.mapper.PipelineMapper;
import com.riverstone.orderevents.repository.EventLogRepository;
import com.riverstone.orderevents.repository.OrderNotificationRepository;
import com.riverstone.orderevents.repository.OrderRecordRepository;
import com.riverstone.orderevents.repository.ReservationRepository;
import com.riverstone.orderevents.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderRecordRepository orderRepository;
    private final EventLogRepository eventLogRepository;
    private final ReservationRepository reservationRepository;
    private final OrderNotificationRepository notificationRepository;
    private final PipelineMapper pipelineMapper;

    public OrderController(OrderService orderService,
                           OrderRecordRepository orderRepository,
                           EventLogRepository eventLogRepository,
                           ReservationRepository reservationRepository,
                           OrderNotificationRepository notificationRepository,
                           PipelineMapper pipelineMapper) {
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.eventLogRepository = eventLogRepository;
        this.reservationRepository = reservationRepository;
        this.notificationRepository = notificationRepository;
        this.pipelineMapper = pipelineMapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OrderResponse create(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.createOrder(request);
    }

    @GetMapping("/{orderRef}")
    public OrderResponse get(@PathVariable String orderRef) {
        return orderService.getOrder(orderRef);
    }

    @GetMapping
    public PagedResponse<OrderResponse> byStatus(@RequestParam(defaultValue = "CREATED") OrderStatus status,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return orderService.byStatus(status, pageable);
    }

    /**
     * Everything that happened to one order: its current state, every event any consumer
     * took off a topic for it, and what the downstream services recorded.
     */
    @GetMapping("/{orderRef}/trace")
    @Transactional(readOnly = true)
    public OrderTraceResponse trace(@PathVariable String orderRef) {
        var order = orderRepository.findByOrderRef(orderRef)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderRef));
        return new OrderTraceResponse(
                pipelineMapper.toResponse(order),
                pipelineMapper.toEventResponses(eventLogRepository.findByOrderRefOrderByCreatedAtAsc(orderRef)),
                reservationRepository.findByOrderRefOrderByCreatedAtAsc(orderRef).stream()
                        .map(r -> r.getQuantity() + " x " + r.getSku()).toList(),
                notificationRepository.findByOrderRefOrderByCreatedAtAsc(orderRef).stream()
                        .map(n -> n.getSubject()).toList());
    }
}
