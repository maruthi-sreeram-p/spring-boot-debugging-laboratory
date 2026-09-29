package com.pulsesend.notifications.messaging;

import com.pulsesend.notifications.channel.ChannelDispatcher;
import com.pulsesend.notifications.channel.DeliveryOutcome;
import com.pulsesend.notifications.entity.DeliveryLogEntry;
import com.pulsesend.notifications.entity.DeliveryOutcomeType;
import com.pulsesend.notifications.entity.Notification;
import com.pulsesend.notifications.entity.NotificationStatus;
import com.pulsesend.notifications.entity.NotificationTemplate;
import com.pulsesend.notifications.exception.ResourceNotFoundException;
import com.pulsesend.notifications.repository.DeliveryLogRepository;
import com.pulsesend.notifications.repository.NotificationRepository;
import com.pulsesend.notifications.repository.NotificationTemplateRepository;
import com.pulsesend.notifications.service.TemplateRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Renders a queued notification and hands it to the provider for its channel, then records
 * what happened in the delivery log.
 */
@Component
public class NotificationDeliveryHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryHandler.class);

    private final NotificationRepository notificationRepository;
    private final NotificationTemplateRepository templateRepository;
    private final DeliveryLogRepository deliveryLogRepository;
    private final ChannelDispatcher dispatcher;
    private final TemplateRenderer renderer;

    public NotificationDeliveryHandler(NotificationRepository notificationRepository,
                                       NotificationTemplateRepository templateRepository,
                                       DeliveryLogRepository deliveryLogRepository,
                                       ChannelDispatcher dispatcher,
                                       TemplateRenderer renderer) {
        this.notificationRepository = notificationRepository;
        this.templateRepository = templateRepository;
        this.deliveryLogRepository = deliveryLogRepository;
        this.dispatcher = dispatcher;
        this.renderer = renderer;
    }

    @Transactional
    public void handle(NotificationMessage message) {
        Notification notification = notificationRepository
                .findByNotificationRef(message.getNotificationRef())
                .orElseThrow(() -> new ResourceNotFoundException("Notification", message.getNotificationRef()));

        NotificationTemplate template = templateRepository.findByCode(notification.getTemplateCode())
                .orElseThrow(() -> new ResourceNotFoundException("Template", notification.getTemplateCode()));

        String subject = renderer.render(template.getSubjectTemplate(), notification.getPayload());
        String body = renderer.render(template.getBodyTemplate(), notification.getPayload());

        notification.setAttempts(notification.getAttempts() + 1);
        notification.setStatus(NotificationStatus.SENT);

        DeliveryOutcome outcome = dispatcher.deliver(
                notification.getChannel(),
                notification.getNotificationRef(),
                notification.getDestination(),
                subject,
                body);

        DeliveryLogEntry entry = new DeliveryLogEntry();
        entry.setNotificationId(notification.getId());
        entry.setAttemptNo(notification.getAttempts());
        entry.setOutcome(outcome.delivered() ? DeliveryOutcomeType.DELIVERED : DeliveryOutcomeType.REJECTED);
        entry.setDetail(outcome.detail());
        deliveryLogRepository.save(entry);

        log.debug("Handled {} on {}: {}", notification.getNotificationRef(),
                notification.getChannel(), outcome.detail());
    }
}
