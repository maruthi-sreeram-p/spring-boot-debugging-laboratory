package com.northwind.shop.service;

import com.northwind.shop.cache.CartRedisRepository;
import com.northwind.shop.dto.OrderResponse;
import com.northwind.shop.dto.PagedResponse;
import com.northwind.shop.dto.PlaceOrderRequest;
import com.northwind.shop.entity.Customer;
import com.northwind.shop.entity.Order;
import com.northwind.shop.entity.OrderItem;
import com.northwind.shop.entity.OrderStatus;
import com.northwind.shop.entity.Product;
import com.northwind.shop.exception.EmptyCartException;
import com.northwind.shop.exception.InsufficientStockException;
import com.northwind.shop.exception.ResourceNotFoundException;
import com.northwind.shop.mapper.OrderMapper;
import com.northwind.shop.repository.CustomerRepository;
import com.northwind.shop.repository.OrderRepository;
import com.northwind.shop.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final CartRedisRepository cartRedisRepository;
    private final StringRedisTemplate redisTemplate;
    private final PricingCalculator pricingCalculator;
    private final OrderMapper orderMapper;

    public OrderService(OrderRepository orderRepository,
                        ProductRepository productRepository,
                        CustomerRepository customerRepository,
                        CartRedisRepository cartRedisRepository,
                        StringRedisTemplate redisTemplate,
                        PricingCalculator pricingCalculator,
                        OrderMapper orderMapper) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.cartRedisRepository = cartRedisRepository;
        this.redisTemplate = redisTemplate;
        this.pricingCalculator = pricingCalculator;
        this.orderMapper = orderMapper;
    }

    /**
     * Turns the current cart contents into an order and empties the cart afterwards.
     */
    public OrderResponse placeOrder(Long customerId, PlaceOrderRequest request) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

        Map<Long, Integer> cartLines = cartRedisRepository.findCart(customerId);
        if (cartLines.isEmpty()) {
            throw new EmptyCartException();
        }

        Order order = buildAndStoreOrder(customer, cartLines, request);

        redisTemplate.delete("cart:" + customerId);

        log.info("Order {} placed for customer {} with {} line(s), total {}",
                order.getOrderNumber(), customerId, order.getItems().size(), order.getTotalAmount());
        return orderMapper.toResponse(order);
    }

    @Transactional
    public Order buildAndStoreOrder(Customer customer, Map<Long, Integer> cartLines, PlaceOrderRequest request) {
        Order order = new Order();
        order.setCustomer(customer);
        order.setOrderNumber(nextOrderNumber());
        order.setShippingAddress(request.getShippingAddress());
        order.setStatus(OrderStatus.PENDING);

        BigDecimal subtotal = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> cartLine : cartLines.entrySet()) {
            Long productId = cartLine.getKey();
            int quantity = cartLine.getValue();

            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product", productId));

            if (product.getStockQuantity() < quantity) {
                throw new InsufficientStockException(product.getName(), quantity, product.getStockQuantity());
            }
            product.setStockQuantity(product.getStockQuantity() - quantity);

            OrderItem item = new OrderItem();
            item.setProduct(product);
            item.setProductName(product.getName());
            item.setQuantity(quantity);
            item.setUnitPrice(product.getPrice());
            item.setLineTotal(pricingCalculator.lineTotal(product.getPrice(), quantity));
            order.addItem(item);

            subtotal = subtotal.add(item.getLineTotal());
        }

        BigDecimal shippingFee = pricingCalculator.shippingFeeFor(subtotal);
        order.setSubtotal(pricingCalculator.normalize(subtotal));
        order.setShippingFee(shippingFee);
        order.setTotalAmount(pricingCalculator.normalize(subtotal.add(shippingFee)));

        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public PagedResponse<OrderResponse> listOrders(Long customerId, Pageable pageable) {
        Page<Order> page = orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable);
        List<OrderResponse> content = page.getContent().stream()
                .map(orderMapper::toResponse)
                .toList();
        return PagedResponse.of(page, content);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        return orderMapper.toResponse(order);
    }

    private String nextOrderNumber() {
        String candidate;
        do {
            candidate = "ORD-"
                    + LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE)
                    + "-" + ThreadLocalRandom.current().nextInt(100_000, 999_999);
        } while (orderRepository.existsByOrderNumber(candidate));
        return candidate;
    }
}
