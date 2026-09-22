package com.northwind.shop.service;

import com.northwind.shop.cache.CartRedisRepository;
import com.northwind.shop.dto.AddCartItemRequest;
import com.northwind.shop.dto.CartLineResponse;
import com.northwind.shop.dto.CartResponse;
import com.northwind.shop.dto.UpdateCartItemRequest;
import com.northwind.shop.entity.Product;
import com.northwind.shop.exception.ResourceNotFoundException;
import com.northwind.shop.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CartService {

    private static final Logger log = LoggerFactory.getLogger(CartService.class);

    private final CartRedisRepository cartRedisRepository;
    private final ProductRepository productRepository;
    private final PricingCalculator pricingCalculator;

    public CartService(CartRedisRepository cartRedisRepository,
                       ProductRepository productRepository,
                       PricingCalculator pricingCalculator) {
        this.cartRedisRepository = cartRedisRepository;
        this.productRepository = productRepository;
        this.pricingCalculator = pricingCalculator;
    }

    public CartResponse getCart(Long customerId) {
        Map<Long, Integer> stored = cartRedisRepository.findCart(customerId);
        if (stored.isEmpty()) {
            return new CartResponse(List.of(), 0, pricingCalculator.normalize(BigDecimal.ZERO));
        }

        Map<Long, Product> catalogue = new HashMap<>();
        for (Product product : productRepository.findByIdInAndActiveTrue(stored.keySet())) {
            catalogue.put(product.getId(), product);
        }

        List<CartLineResponse> lines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : stored.entrySet()) {
            Product product = catalogue.get(entry.getKey());
            if (product == null) {
                log.debug("Dropping cart line for retired product {} of customer {}", entry.getKey(), customerId);
                continue;
            }
            int quantity = entry.getValue();
            BigDecimal lineTotal = pricingCalculator.lineTotal(product.getPrice(), quantity);
            lines.add(new CartLineResponse(
                    product.getId(),
                    product.getSku(),
                    product.getName(),
                    product.getPrice(),
                    quantity,
                    lineTotal,
                    product.getStockQuantity() >= quantity));
            subtotal = subtotal.add(lineTotal);
        }

        return new CartResponse(lines, lines.size(), pricingCalculator.normalize(subtotal));
    }

    public CartResponse addItem(Long customerId, AddCartItemRequest request) {
        Product product = productRepository.findById(request.getProductId())
                .filter(Product::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Product", request.getProductId()));

        cartRedisRepository.increaseQuantity(customerId, product.getId(), request.getQuantity());
        log.debug("Customer {} added {} x {} to cart", customerId, request.getQuantity(), product.getSku());
        return getCart(customerId);
    }

    public CartResponse updateItem(Long customerId, Long productId, UpdateCartItemRequest request) {
        Product product = productRepository.findById(productId)
                .filter(Product::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));

        cartRedisRepository.setQuantity(customerId, product.getId(), request.getQuantity());
        return getCart(customerId);
    }

    public CartResponse removeItem(Long customerId, Long productId) {
        cartRedisRepository.removeItem(customerId, productId);
        return getCart(customerId);
    }

    public void clear(Long customerId) {
        cartRedisRepository.clear(customerId);
    }
}
