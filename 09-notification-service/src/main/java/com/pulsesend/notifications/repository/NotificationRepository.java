package com.pulsesend.notifications.repository;

import com.pulsesend.notifications.entity.Notification;
import com.pulsesend.notifications.entity.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findByNotificationRef(String notificationRef);

    Page<Notification> findByRecipientRefOrderByCreatedAtDesc(String recipientRef, Pageable pageable);

    Page<Notification> findByStatusOrderByCreatedAtDesc(NotificationStatus status, Pageable pageable);

    boolean existsByNotificationRef(String notificationRef);

    long countByStatus(NotificationStatus status);
}
