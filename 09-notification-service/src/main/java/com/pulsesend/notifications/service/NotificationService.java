package com.pulsesend.notifications.service;

import com.pulsesend.notifications.config.PulsesendProperties;
import com.pulsesend.notifications.dto.BroadcastRequest;
import com.pulsesend.notifications.dto.BroadcastTarget;
import com.pulsesend.notifications.dto.NotificationResponse;
import com.pulsesend.notifications.dto.PagedResponse;
import com.pulsesend.notifications.dto.SendNotificationRequest;
import com.pulsesend.notifications.entity.Channel;
import com.pulsesend.notifications.entity.Notification;
import com.pulsesend.notifications.entity.NotificationPreference;
import com.pulsesend.notifications.entity.NotificationStatus;
import com.pulsesend.notifications.entity.NotificationTemplate;
import com.pulsesend.notifications.exception.NotificationRuleException;
import com.pulsesend.notifications.exception.ResourceNotFoundException;
import com.pulsesend.notifications.mapper.NotificationMapper;
import com.pulsesend.notifications.messaging.NotificationPublisher;
import com.pulsesend.notifications.repository.NotificationPreferenceRepository;
import com.pulsesend.notifications.repository.NotificationRepository;
import com.pulsesend.notifications.repository.NotificationTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final NotificationTemplateRepository templateRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationPublisher publisher;
    private final NotificationMapper notificationMapper;
    private final PulsesendProperties properties;

    public NotificationService(NotificationRepository notificationRepository,
                               NotificationTemplateRepository templateRepository,
                               NotificationPreferenceRepository preferenceRepository,
                               NotificationPublisher publisher,
                               NotificationMapper notificationMapper,
                               PulsesendProperties properties) {
        this.notificationRepository = notificationRepository;
        this.templateRepository = templateRepository;
        this.preferenceRepository = preferenceRepository;
        this.publisher = publisher;
        this.notificationMapper = notificationMapper;
        this.properties = properties;
    }

    /**
     * The path every product team uses. Honours the opt-out the recipient set for this
     * channel: a suppressed notification is recorded so the audit is complete, but nothing
     * is queued.
     */
    @Transactional
    public NotificationResponse send(SendNotificationRequest request) {
        NotificationTemplate template = templateRepository.findByCode(request.getTemplateCode())
                .orElseThrow(() -> new ResourceNotFoundException("Template", request.getTemplateCode()));
        if (!template.isActive()) {
            throw new NotificationRuleException("Template " + template.getCode() + " is retired");
        }

        Notification notification = newNotification(request.getRecipientRef(), request.getDestination(),
                template, request.getPayload());

        if (!channelEnabledFor(request.getRecipientRef(), template.getChannel())) {
            notification.setStatus(NotificationStatus.SUPPRESSED);
            notification.setLastError("Recipient has opted out of " + template.getChannel());
            Notification suppressed = notificationRepository.save(notification);
            log.info("Suppressed {} for {}: opted out of {}",
                    suppressed.getNotificationRef(), request.getRecipientRef(), template.getChannel());
            return notificationMapper.toResponse(suppressed);
        }

        Notification saved = notificationRepository.save(notification);
        publisher.publish(saved);
        log.info("Queued {} for {} on {}", saved.getNotificationRef(),
                saved.getRecipientRef(), saved.getChannel());
        return notificationMapper.toResponse(saved);
    }

    /**
     * Used by the lifecycle campaigns the growth team runs. Same template for a list of
     * recipients, queued in one go.
     */
    @Transactional
    public List<NotificationResponse> broadcast(BroadcastRequest request) {
        NotificationTemplate template = templateRepository.findByCode(request.getTemplateCode())
                .orElseThrow(() -> new ResourceNotFoundException("Template", request.getTemplateCode()));
        if (!template.isActive()) {
            throw new NotificationRuleException("Template " + template.getCode() + " is retired");
        }

        List<NotificationResponse> queued = new ArrayList<>();
        for (BroadcastTarget target : request.getTargets()) {
            Notification notification = newNotification(target.getRecipientRef(), target.getDestination(),
                    template, request.getPayload());
            Notification saved = notificationRepository.save(notification);
            publisher.publish(saved);
            queued.add(notificationMapper.toResponse(saved));
        }

        log.info("Broadcast {} queued {} notification(s)", template.getCode(), queued.size());
        return queued;
    }

    @Transactional(readOnly = true)
    public NotificationResponse getNotification(String notificationRef) {
        Notification notification = notificationRepository.findByNotificationRef(notificationRef)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationRef));
        return notificationMapper.toResponse(notification);
    }

    @Transactional(readOnly = true)
    public PagedResponse<NotificationResponse> forRecipient(String recipientRef, Pageable pageable) {
        Page<Notification> page = notificationRepository
                .findByRecipientRefOrderByCreatedAtDesc(recipientRef, pageable);
        return PagedResponse.of(page, notificationMapper.toResponses(page.getContent()));
    }

    @Transactional(readOnly = true)
    public PagedResponse<NotificationResponse> byStatus(NotificationStatus status, Pageable pageable) {
        Page<Notification> page = notificationRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        return PagedResponse.of(page, notificationMapper.toResponses(page.getContent()));
    }

    @Transactional
    public NotificationResponse requeue(String notificationRef) {
        Notification notification = notificationRepository.findByNotificationRef(notificationRef)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationRef));
        publisher.publish(notification);
        log.info("Requeued {}", notificationRef);
        return notificationMapper.toResponse(notification);
    }

    private boolean channelEnabledFor(String recipientRef, Channel channel) {
        Optional<NotificationPreference> preference =
                preferenceRepository.findByRecipientRefAndChannel(recipientRef, channel);
        return preference.map(NotificationPreference::isEnabled).orElse(true);
    }

    private Notification newNotification(String recipientRef, String destination,
                                         NotificationTemplate template, String payload) {
        Notification notification = new Notification();
        notification.setNotificationRef(nextReference());
        notification.setRecipientRef(recipientRef);
        notification.setDestination(destination);
        notification.setChannel(template.getChannel());
        notification.setTemplateCode(template.getCode());
        notification.setPayload(payload == null ? "{}" : payload);
        notification.setStatus(NotificationStatus.PENDING);
        notification.setAttempts(0);
        return notification;
    }

    private String nextReference() {
        String candidate;
        do {
            candidate = "NTF-" + LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE)
                    + "-" + String.format("%06d", ThreadLocalRandom.current().nextInt(1, 999_999));
        } while (notificationRepository.existsByNotificationRef(candidate));
        return candidate;
    }
}
