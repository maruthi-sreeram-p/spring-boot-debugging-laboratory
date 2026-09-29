package com.riverstone.orderevents.service;

import com.riverstone.orderevents.entity.EventLogEntry;
import com.riverstone.orderevents.repository.EventLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the audit row for a consumed event in its own transaction, so the record of what
 * arrived survives whatever the handler goes on to do with it.
 */
@Service
public class EventRecorder {

    private final EventLogRepository eventLogRepository;

    public EventRecorder(EventLogRepository eventLogRepository) {
        this.eventLogRepository = eventLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String topic, int partition, long offset, String key,
                       String eventType, String orderRef, String consumerGroup, String handledBy) {
        EventLogEntry entry = new EventLogEntry();
        entry.setTopic(topic);
        entry.setPartitionNo(partition);
        entry.setOffsetNo(offset);
        entry.setMessageKey(key);
        entry.setEventType(eventType);
        entry.setOrderRef(orderRef);
        entry.setConsumerGroup(consumerGroup);
        entry.setHandledBy(handledBy);
        eventLogRepository.save(entry);
    }
}
