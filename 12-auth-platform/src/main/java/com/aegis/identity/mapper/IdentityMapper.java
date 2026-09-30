package com.aegis.identity.mapper;

import com.aegis.identity.dto.AccountResponse;
import com.aegis.identity.dto.AuditEventResponse;
import com.aegis.identity.dto.DocumentResponse;
import com.aegis.identity.dto.SessionResponse;
import com.aegis.identity.entity.Account;
import com.aegis.identity.entity.AuditEvent;
import com.aegis.identity.entity.Document;
import com.aegis.identity.entity.Permission;
import com.aegis.identity.entity.RefreshToken;
import com.aegis.identity.entity.Role;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class IdentityMapper {

    public List<String> roleNames(Account account) {
        return account.getRoles().stream()
                .map(Role::getName)
                .sorted()
                .collect(Collectors.toList());
    }

    public List<String> permissionNames(Account account) {
        return account.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getName)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getEmail(),
                account.getDisplayName(),
                account.getStatus().name(),
                account.getFailedAttempts(),
                roleNames(account),
                permissionNames(account),
                account.getCreatedAt(),
                account.getLastLoginAt());
    }

    public List<AccountResponse> toAccountResponses(List<Account> accounts) {
        List<AccountResponse> responses = new ArrayList<>();
        accounts.forEach(account -> responses.add(toResponse(account)));
        return responses;
    }

    public DocumentResponse toResponse(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getReference(),
                document.getTitle(),
                document.getBody(),
                document.getOwner().getId(),
                document.getOwner().getEmail(),
                document.getSensitivity().name(),
                document.getUpdatedAt());
    }

    public List<DocumentResponse> toDocumentResponses(List<Document> documents) {
        return documents.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public AuditEventResponse toResponse(AuditEvent event) {
        return new AuditEventResponse(event.getId(), event.getAccountId(),
                event.getEventType(), event.getDetail(), event.getCreatedAt());
    }

    public SessionResponse toResponse(RefreshToken token) {
        return new SessionResponse(token.getId(), token.getTokenId(), token.getIssuedAt(),
                token.getExpiresAt(), token.isRevoked(), token.getReplacedBy(), token.getUserAgent());
    }

    public List<SessionResponse> toSessionResponses(List<RefreshToken> tokens) {
        return tokens.stream().map(this::toResponse).collect(Collectors.toList());
    }
}
