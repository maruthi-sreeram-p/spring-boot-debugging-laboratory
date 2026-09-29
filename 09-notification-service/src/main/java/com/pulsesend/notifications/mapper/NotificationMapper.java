package com.pulsesend.notifications.mapper;

import com.pulsesend.notifications.dto.DeliveryLogResponse;
import com.pulsesend.notifications.dto.NotificationResponse;
import com.pulsesend.notifications.dto.PreferenceResponse;
import com.pulsesend.notifications.entity.DeliveryLogEntry;
import com.pulsesend.notifications.entity.Notification;
import com.pulsesend.notifications.entity.NotificationPreference;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NotificationMapper {

    public NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getNotificationRef(),
                notification.getRecipientRef(),
                notification.getDestination(),
                notification.getChannel().name(),
                notification.getTemplateCode(),
                notification.getPayload(),
                notification.getStatus().name(),
                notification.getAttempts(),
                notification.getLastError(),
                notification.getCreatedAt(),
                notification.getUpdatedAt());
    }

    public List<NotificationResponse> toResponses(List<Notification> notifications) {
        return notifications.stream().map(this::toResponse).toList();
    }

    public DeliveryLogResponse toResponse(DeliveryLogEntry entry) {
        return new DeliveryLogResponse(
                entry.getId(),
                entry.getAttemptNo(),
                entry.getOutcome().name(),
                entry.getDetail(),
                entry.getCreatedAt());
    }

    public List<DeliveryLogResponse> toLogResponses(List<DeliveryLogEntry> entries) {
        return entries.stream().map(this::toResponse).toList();
    }

    public PreferenceResponse toResponse(NotificationPreference preference) {
        return new PreferenceResponse(
                preference.getId(),
                preference.getRecipientRef(),
                preference.getChannel().name(),
                preference.isEnabled(),
                preference.getUpdatedAt());
    }

    public List<PreferenceResponse> toPreferenceResponses(List<NotificationPreference> preferences) {
        return preferences.stream().map(this::toResponse).toList();
    }
}
