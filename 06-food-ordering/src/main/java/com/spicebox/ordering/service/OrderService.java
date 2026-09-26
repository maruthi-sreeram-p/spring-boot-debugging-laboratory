package com.spicebox.ordering.service;

import com.spicebox.ordering.config.SpiceboxProperties;
import com.spicebox.ordering.dto.OrderResponse;
import com.spicebox.ordering.dto.PagedResponse;
import com.spicebox.ordering.dto.PlaceOrderLineRequest;
import com.spicebox.ordering.dto.PlaceOrderRequest;
import com.spicebox.ordering.entity.Customer;
import com.spicebox.ordering.entity.FoodOrder;
import com.spicebox.ordering.entity.MenuItem;
import com.spicebox.ordering.entity.OrderItem;
import com.spicebox.ordering.entity.OrderStatus;
import com.spicebox.ordering.entity.Restaurant;
import com.spicebox.ordering.exception.OrderRuleException;
import com.spicebox.ordering.exception.ResourceNotFoundException;
import com.spicebox.ordering.mapper.OrderingMapper;
import com.spicebox.ordering.messaging.OrderEventPublisher;
import com.spicebox.ordering.repository.CustomerRepository;
import com.spicebox.ordering.repository.FoodOrderRepository;
import com.spicebox.ordering.repository.MenuItemRepository;
import com.spicebox.ordering.security.SpiceboxUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final FoodOrderRepository foodOrderRepository;
    private final MenuItemRepository menuItemRepository;
    private final CustomerRepository customerRepository;
    private final OrderingMapper orderingMapper;
    private final OrderEventPublisher orderEventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final NotificationService notificationService;
    private final SpiceboxProperties properties;

    public OrderService(FoodOrderRepository foodOrderRepository,
                        MenuItemRepository menuItemRepository,
                        CustomerRepository customerRepository,
                        OrderingMapper orderingMapper,
                        OrderEventPublisher orderEventPublisher,
                        ApplicationEventPublisher applicationEventPublisher,
                        NotificationService notificationService,
                        SpiceboxProperties properties) {
        this.foodOrderRepository = foodOrderRepository;
        this.menuItemRepository = menuItemRepository;
        this.customerRepository = customerRepository;
        this.orderingMapper = orderingMapper;
        this.orderEventPublisher = orderEventPublisher;
        this.applicationEventPublisher = applicationEventPublisher;
        this.notificationService = notificationService;
        this.properties = properties;
    }

    @Transactional
    public OrderResponse placeOrder(SpiceboxUser principal, PlaceOrderRequest request) {
        Customer customer = customerRepository.findById(principal.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", principal.getCustomerId()));

        MenuItem firstItem = menuItemRepository.findById(request.getItems().get(0).getMenuItemId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Menu item", request.getItems().get(0).getMenuItemId()));
        Restaurant restaurant = firstItem.getRestaurant();
        if (!restaurant.isActive()) {
            throw new OrderRuleException(restaurant.getName() + " is not accepting orders right now");
        }

        FoodOrder order = new FoodOrder();
        order.setOrderCode(nextOrderCode());
        order.setCustomer(customer);
        order.setRestaurant(restaurant);
        order.setStatus(OrderStatus.PLACED);
        order.setDeliveryAddress(StringUtils.hasText(request.getDeliveryAddress())
                ? request.getDeliveryAddress().trim()
                : customer.getDefaultAddress());

        BigDecimal subtotal = BigDecimal.ZERO;
        for (PlaceOrderLineRequest line : request.getItems()) {
            MenuItem menuItem = menuItemRepository.findById(line.getMenuItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Menu item", line.getMenuItemId()));
            if (!menuItem.isAvailable()) {
                throw new OrderRuleException(menuItem.getName() + " is not available at the moment");
            }

            BigDecimal lineTotal = menuItem.getPrice()
                    .multiply(BigDecimal.valueOf(line.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);

            OrderItem item = new OrderItem();
            item.setMenuItem(menuItem);
            item.setItemName(menuItem.getName());
            item.setQuantity(line.getQuantity());
            item.setUnitPrice(menuItem.getPrice());
            item.setLineTotal(lineTotal);
            order.addItem(item);

            subtotal = subtotal.add(lineTotal);
        }

        if (subtotal.compareTo(properties.getOrdering().getMinimumOrderValue()) < 0) {
            throw new OrderRuleException("The minimum order value is "
                    + properties.getOrdering().getMinimumOrderValue());
        }

        BigDecimal deliveryFee = subtotal.compareTo(properties.getOrdering().getFreeDeliveryThreshold()) >= 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY)
                : properties.getOrdering().getDeliveryFee();

        order.setSubtotal(subtotal.setScale(2, RoundingMode.HALF_UP));
        order.setDeliveryFee(deliveryFee);
        order.setTotalAmount(subtotal.add(deliveryFee).setScale(2, RoundingMode.HALF_UP));

        FoodOrder saved = foodOrderRepository.save(order);

        applicationEventPublisher.publishEvent(new OrderPlacedEvent(saved.getId(), saved.getOrderCode()));
        orderEventPublisher.publishOrderPlaced(saved);
        notificationService.sendOrderConfirmation(saved.getId());

        log.info("Order {} placed by {} at {} for {}", saved.getOrderCode(), customer.getEmail(),
                restaurant.getName(), saved.getTotalAmount());
        return orderingMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PagedResponse<OrderResponse> myOrders(SpiceboxUser principal, Pageable pageable) {
        Page<FoodOrder> page = foodOrderRepository
                .findByCustomerIdOrderByPlacedAtDesc(principal.getCustomerId(), pageable);
        return PagedResponse.of(page, orderingMapper.toOrderResponses(page.getContent()));
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long orderId, SpiceboxUser principal) {
        FoodOrder order = foodOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        if (!principal.isOperations() && !order.getCustomer().getId().equals(principal.getCustomerId())) {
            throw new ResourceNotFoundException("Order", orderId);
        }
        return orderingMapper.toResponse(order);
    }

    private String nextOrderCode() {
        String candidate;
        do {
            candidate = "SPX-" + LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE)
                    + "-" + ThreadLocalRandom.current().nextInt(1000, 9999);
        } while (foodOrderRepository.existsByOrderCode(candidate));
        return candidate;
    }
}
