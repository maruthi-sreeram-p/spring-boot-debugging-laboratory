package com.pulsesend.notifications.repository;

import com.pulsesend.notifications.entity.Channel;
import com.pulsesend.notifications.entity.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    Optional<NotificationPreference> findByRecipientRefAndChannel(String recipientRef, Channel channel);

    List<NotificationPreference> findByRecipientRefOrderByChannelAsc(String recipientRef);
}
