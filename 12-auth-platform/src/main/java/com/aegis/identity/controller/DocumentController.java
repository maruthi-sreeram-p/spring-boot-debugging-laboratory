package com.aegis.identity.controller;

import com.aegis.identity.dto.DocumentRequest;
import com.aegis.identity.dto.DocumentResponse;
import com.aegis.identity.security.AegisPrincipal;
import com.aegis.identity.service.DocumentService;
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

import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public List<DocumentResponse> all() {
        return documentService.all();
    }

    @GetMapping("/{documentId}")
    public DocumentResponse byId(@PathVariable Long documentId) {
        return documentService.byId(documentId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse create(@AuthenticationPrincipal AegisPrincipal principal,
                                   @Valid @RequestBody DocumentRequest request) {
        return documentService.create(request, principal);
    }

    @PutMapping("/{documentId}")
    public DocumentResponse update(@AuthenticationPrincipal AegisPrincipal principal,
                                   @PathVariable Long documentId,
                                   @Valid @RequestBody DocumentRequest request) {
        return documentService.update(documentId, request, principal);
    }

    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AegisPrincipal principal,
                       @PathVariable Long documentId) {
        documentService.delete(documentId, principal);
    }
}
