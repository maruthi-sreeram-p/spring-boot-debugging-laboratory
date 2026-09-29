package com.pulsesend.notifications.repository;

import com.pulsesend.notifications.entity.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {

    Optional<NotificationTemplate> findByCode(String code);

    List<NotificationTemplate> findByActiveTrueOrderByCodeAsc();
}
