package com.meridian.banking.controller;

import com.meridian.banking.dto.CustomerResponse;
import com.meridian.banking.dto.LoginRequest;
import com.meridian.banking.dto.RegisterRequest;
import com.meridian.banking.security.AuthenticatedCustomer;
import com.meridian.banking.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final CustomerService customerService;
    private final AuthenticationManager authenticationManager;

    public AuthController(CustomerService customerService, AuthenticationManager authenticationManager) {
        this.customerService = customerService;
        this.authenticationManager = authenticationManager;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse register(@Valid @RequestBody RegisterRequest request) {
        return customerService.register(request);
    }

    @PostMapping("/login")
    public CustomerResponse login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        AuthenticatedCustomer principal = (AuthenticatedCustomer) authentication.getPrincipal();
        return customerService.findByEmail(principal.getUsername());
    }

    @GetMapping("/me")
    public CustomerResponse me(@AuthenticationPrincipal AuthenticatedCustomer principal) {
        return customerService.findByEmail(principal.getUsername());
    }
}
