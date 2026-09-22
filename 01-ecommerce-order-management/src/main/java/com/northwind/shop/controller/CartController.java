package com.northwind.shop.controller;

import com.northwind.shop.dto.AddCartItemRequest;
import com.northwind.shop.dto.CartResponse;
import com.northwind.shop.dto.UpdateCartItemRequest;
import com.northwind.shop.security.AuthenticatedCustomer;
import com.northwind.shop.service.CartService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartResponse cart(@AuthenticationPrincipal AuthenticatedCustomer principal) {
        return cartService.getCart(principal.getCustomerId());
    }

    @PostMapping("/items")
    public CartResponse addItem(@AuthenticationPrincipal AuthenticatedCustomer principal,
                                @RequestBody AddCartItemRequest request) {
        return cartService.addItem(principal.getCustomerId(), request);
    }

    @PutMapping("/items/{productId}")
    public CartResponse updateItem(@AuthenticationPrincipal AuthenticatedCustomer principal,
                                   @PathVariable Long productId,
                                   @Valid @RequestBody UpdateCartItemRequest request) {
        return cartService.updateItem(principal.getCustomerId(), productId, request);
    }

    @DeleteMapping("/items/{productId}")
    public CartResponse removeItem(@AuthenticationPrincipal AuthenticatedCustomer principal,
                                   @PathVariable Long productId) {
        return cartService.removeItem(principal.getCustomerId(), productId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@AuthenticationPrincipal AuthenticatedCustomer principal) {
        cartService.clear(principal.getCustomerId());
    }
}
