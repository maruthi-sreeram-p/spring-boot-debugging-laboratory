package com.aegis.identity.controller;

import com.aegis.identity.dto.AccountResponse;
import com.aegis.identity.dto.LoginRequest;
import com.aegis.identity.dto.RefreshRequest;
import com.aegis.identity.dto.RegisterRequest;
import com.aegis.identity.dto.SessionResponse;
import com.aegis.identity.dto.TokenResponse;
import com.aegis.identity.security.AegisPrincipal;
import com.aegis.identity.service.AccountService;
import com.aegis.identity.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AccountService accountService;

    public AuthController(AuthService authService, AccountService accountService) {
        this.authService = authService;
        this.accountService = accountService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse register(@Valid @RequestBody RegisterRequest request,
                                  @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        return authService.register(request, userAgent);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request,
                               @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        return authService.login(request, userAgent);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request,
                                 @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        return authService.refresh(request, userAgent);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request);
    }

    @GetMapping("/me")
    public AccountResponse me(@AuthenticationPrincipal AegisPrincipal principal) {
        return accountService.byId(principal.accountId());
    }

    @GetMapping("/sessions")
    public List<SessionResponse> sessions(@AuthenticationPrincipal AegisPrincipal principal) {
        return authService.sessions(principal.accountId());
    }
}
