package com.pulsesend.notifications.repository;

import com.pulsesend.notifications.entity.DeliveryLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeliveryLogRepository extends JpaRepository<DeliveryLogEntry, Long> {

    List<DeliveryLogEntry> findByNotificationIdOrderByAttemptNoAsc(Long notificationId);
}
