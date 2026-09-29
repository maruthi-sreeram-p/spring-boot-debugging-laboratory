package com.pulsesend.notifications.service;

import com.pulsesend.notifications.dto.PreferenceResponse;
import com.pulsesend.notifications.dto.UpdatePreferenceRequest;
import com.pulsesend.notifications.entity.Channel;
import com.pulsesend.notifications.entity.NotificationPreference;
import com.pulsesend.notifications.mapper.NotificationMapper;
import com.pulsesend.notifications.repository.NotificationPreferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class PreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationMapper notificationMapper;

    public PreferenceService(NotificationPreferenceRepository preferenceRepository,
                             NotificationMapper notificationMapper) {
        this.preferenceRepository = preferenceRepository;
        this.notificationMapper = notificationMapper;
    }

    @Transactional(readOnly = true)
    public List<PreferenceResponse> forRecipient(String recipientRef) {
        return notificationMapper.toPreferenceResponses(
                preferenceRepository.findByRecipientRefOrderByChannelAsc(recipientRef));
    }

    @Transactional
    public PreferenceResponse update(String recipientRef, UpdatePreferenceRequest request) {
        Channel channel = Channel.valueOf(request.getChannel());
        NotificationPreference preference = preferenceRepository
                .findByRecipientRefAndChannel(recipientRef, channel)
                .orElseGet(() -> {
                    NotificationPreference created = new NotificationPreference();
                    created.setRecipientRef(recipientRef);
                    created.setChannel(channel);
                    return created;
                });

        preference.setEnabled(request.getEnabled());
        preference.setUpdatedAt(Instant.now());
        return notificationMapper.toResponse(preferenceRepository.save(preference));
    }
}
