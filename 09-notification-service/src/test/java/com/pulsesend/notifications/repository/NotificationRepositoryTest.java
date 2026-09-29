package com.pulsesend.notifications.repository;

import com.pulsesend.notifications.entity.Channel;
import com.pulsesend.notifications.entity.Notification;
import com.pulsesend.notifications.entity.NotificationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class NotificationRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void findsNotificationsByRecipientAndByStatus() {
        persist("NTF-T-0001", "CUST-1", Channel.EMAIL, NotificationStatus.SENT);
        persist("NTF-T-0002", "CUST-1", Channel.SMS, NotificationStatus.PENDING);
        persist("NTF-T-0003", "CUST-2", Channel.EMAIL, NotificationStatus.PENDING);
        entityManager.flush();
        entityManager.clear();

        assertThat(notificationRepository
                .findByRecipientRefOrderByCreatedAtDesc("CUST-1", PageRequest.of(0, 10))
                .getTotalElements()).isEqualTo(2);

        assertThat(notificationRepository
                .findByStatusOrderByCreatedAtDesc(NotificationStatus.PENDING, PageRequest.of(0, 10))
                .getTotalElements()).isEqualTo(2);

        assertThat(notificationRepository.countByStatus(NotificationStatus.SENT)).isEqualTo(1);
    }

    private void persist(String ref, String recipient, Channel channel, NotificationStatus status) {
        Notification notification = new Notification();
        notification.setNotificationRef(ref);
        notification.setRecipientRef(recipient);
        notification.setDestination("target@example.com");
        notification.setChannel(channel);
        notification.setTemplateCode("welcome");
        notification.setStatus(status);
        entityManager.persist(notification);
    }
}
