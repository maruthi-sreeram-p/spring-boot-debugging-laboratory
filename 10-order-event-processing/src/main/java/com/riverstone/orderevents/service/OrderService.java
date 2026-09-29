package com.riverstone.orderevents.service;

import com.riverstone.orderevents.config.RiverstoneProperties;
import com.riverstone.orderevents.dto.CreateOrderRequest;
import com.riverstone.orderevents.dto.OrderResponse;
import com.riverstone.orderevents.dto.PagedResponse;
import com.riverstone.orderevents.entity.OrderRecord;
import com.riverstone.orderevents.entity.OrderStatus;
import com.riverstone.orderevents.exception.OrderRuleException;
import com.riverstone.orderevents.exception.ResourceNotFoundException;
import com.riverstone.orderevents.mapper.PipelineMapper;
import com.riverstone.orderevents.messaging.OrderCreatedEvent;
import com.riverstone.orderevents.repository.OrderRecordRepository;
import com.riverstone.orderevents.repository.StockItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRecordRepository orderRepository;
    private final StockItemRepository stockItemRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final PipelineMapper pipelineMapper;
    private final RiverstoneProperties properties;

    public OrderService(OrderRecordRepository orderRepository,
                        StockItemRepository stockItemRepository,
                        KafkaTemplate<String, Object> kafkaTemplate,
                        PipelineMapper pipelineMapper,
                        RiverstoneProperties properties) {
        this.orderRepository = orderRepository;
        this.stockItemRepository = stockItemRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.pipelineMapper = pipelineMapper;
        this.properties = properties;
    }

    /**
     * Accepts an order and hands it to the pipeline. Everything after this point is
     * asynchronous: inventory reserves the stock, notifications go out, and the order
     * status catches up.
     */
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        if (stockItemRepository.findBySku(request.getSku()).isEmpty()) {
            throw new ResourceNotFoundException("Stock item", request.getSku());
        }
        if (request.getQuantity() < 1) {
            throw new OrderRuleException("Quantity must be at least 1");
        }

        OrderRecord order = new OrderRecord();
        order.setOrderRef(nextOrderRef());
        order.setCustomerRef(request.getCustomerRef());
        order.setSku(request.getSku());
        order.setQuantity(request.getQuantity());
        order.setStatus(OrderStatus.CREATED);

        OrderRecord saved = orderRepository.save(order);

        OrderCreatedEvent event = new OrderCreatedEvent(
                saved.getOrderRef(),
                saved.getCustomerRef(),
                saved.getSku(),
                saved.getQuantity(),
                Instant.now());

        kafkaTemplate.send(properties.getTopics().getOrdersCreated(), event);

        log.info("Order {} accepted for {} x {}", saved.getOrderRef(), saved.getQuantity(), saved.getSku());
        return pipelineMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(String orderRef) {
        OrderRecord order = orderRepository.findByOrderRef(orderRef)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderRef));
        return pipelineMapper.toResponse(order);
    }

    @Transactional(readOnly = true)
    public PagedResponse<OrderResponse> byStatus(OrderStatus status, Pageable pageable) {
        Page<OrderRecord> page = orderRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        return PagedResponse.of(page, pipelineMapper.toResponses(page.getContent()));
    }

    private String nextOrderRef() {
        String candidate;
        do {
            candidate = "RS-" + ThreadLocalRandom.current().nextInt(100_000, 999_999);
        } while (orderRepository.existsByOrderRef(candidate));
        return candidate;
    }
}
