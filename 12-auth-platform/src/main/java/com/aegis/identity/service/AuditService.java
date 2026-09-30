package com.aegis.identity.service;

import com.aegis.identity.dto.AuditEventResponse;
import com.aegis.identity.entity.AuditEvent;
import com.aegis.identity.mapper.IdentityMapper;
import com.aegis.identity.repository.AuditEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Everything the security group reads when something goes wrong.
 */
@Service
public class AuditService {

    private final AuditEventRepository auditEventRepository;
    private final IdentityMapper identityMapper;

    public AuditService(AuditEventRepository auditEventRepository, IdentityMapper identityMapper) {
        this.auditEventRepository = auditEventRepository;
        this.identityMapper = identityMapper;
    }

    @Transactional
    public void record(Long accountId, String eventType, String detail) {
        AuditEvent event = new AuditEvent();
        event.setAccountId(accountId);
        event.setEventType(eventType);
        event.setDetail(detail.length() > 300 ? detail.substring(0, 300) : detail);
        auditEventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public List<AuditEventResponse> recent(Pageable pageable) {
        Page<AuditEvent> page = auditEventRepository.findAllByOrderByCreatedAtDesc(pageable);
        return page.getContent().stream().map(identityMapper::toResponse).collect(Collectors.toList());
    }
}
