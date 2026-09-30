package com.aegis.identity.service;

import com.aegis.identity.dto.DocumentRequest;
import com.aegis.identity.dto.DocumentResponse;
import com.aegis.identity.entity.Account;
import com.aegis.identity.entity.Document;
import com.aegis.identity.entity.Sensitivity;
import com.aegis.identity.exception.ResourceNotFoundException;
import com.aegis.identity.mapper.IdentityMapper;
import com.aegis.identity.repository.AccountRepository;
import com.aegis.identity.repository.DocumentRepository;
import com.aegis.identity.security.AegisPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;
import java.util.Locale;

/**
 * The protected resource the platform exists to protect. Reads are open to anyone holding a
 * valid token; writes are gated on the fine-grained permissions carried by that token.
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository documentRepository;
    private final AccountRepository accountRepository;
    private final AuditService auditService;
    private final IdentityMapper identityMapper;

    public DocumentService(DocumentRepository documentRepository,
                           AccountRepository accountRepository,
                           AuditService auditService,
                           IdentityMapper identityMapper) {
        this.documentRepository = documentRepository;
        this.accountRepository = accountRepository;
        this.auditService = auditService;
        this.identityMapper = identityMapper;
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> all() {
        return identityMapper.toDocumentResponses(documentRepository.findAllWithOwner());
    }

    @Transactional(readOnly = true)
    public DocumentResponse byId(Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
        return identityMapper.toResponse(document);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('document:write')")
    @Transactional
    public DocumentResponse create(DocumentRequest request, AegisPrincipal principal) {
        Account owner = accountRepository.findById(principal.accountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account", principal.accountId()));

        Document document = new Document();
        document.setReference(nextReference());
        document.setTitle(request.getTitle().trim());
        document.setBody(request.getBody());
        document.setOwner(owner);
        document.setSensitivity(parseSensitivity(request.getSensitivity()));

        Document saved = documentRepository.save(document);
        auditService.record(principal.accountId(), "DOCUMENT_CREATED", saved.getReference());
        log.info("Document {} created by {}", saved.getReference(), principal.email());
        return identityMapper.toResponse(saved);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('document:write')")
    @Transactional
    public DocumentResponse update(Long documentId, DocumentRequest request, AegisPrincipal principal) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));

        document.setTitle(request.getTitle().trim());
        document.setBody(request.getBody());
        document.setSensitivity(parseSensitivity(request.getSensitivity()));

        auditService.record(principal.accountId(), "DOCUMENT_UPDATED", document.getReference());
        log.info("Document {} updated by {}", document.getReference(), principal.email());
        return identityMapper.toResponse(document);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('document:delete')")
    @Transactional
    public void delete(Long documentId, AegisPrincipal principal) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
        documentRepository.delete(document);
        auditService.record(principal.accountId(), "DOCUMENT_DELETED", document.getReference());
        log.info("Document {} deleted by {}", document.getReference(), principal.email());
    }

    private Sensitivity parseSensitivity(String value) {
        if (value == null || value.isBlank()) {
            return Sensitivity.INTERNAL;
        }
        try {
            return Sensitivity.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("sensitivity must be INTERNAL or CONFIDENTIAL");
        }
    }

    private String nextReference() {
        long count = documentRepository.count() + 1;
        String reference;
        do {
            reference = String.format("DOC-%d-%04d", Year.now().getValue(), count);
            count++;
        } while (documentRepository.existsByReference(reference));
        return reference;
    }
}
