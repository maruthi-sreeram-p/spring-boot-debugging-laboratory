package com.riverstone.orderevents.repository;

import com.riverstone.orderevents.entity.EventLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventLogRepository extends JpaRepository<EventLogEntry, Long> {

    List<EventLogEntry> findByOrderRefOrderByCreatedAtAsc(String orderRef);

    List<EventLogEntry> findByTopicOrderByCreatedAtAsc(String topic);

    List<EventLogEntry> findAllByOrderByCreatedAtAsc();
}
