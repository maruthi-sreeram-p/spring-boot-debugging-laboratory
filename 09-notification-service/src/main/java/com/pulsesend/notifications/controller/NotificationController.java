package com.pulsesend.notifications.controller;

import com.pulsesend.notifications.dto.DeliveryLogResponse;
import com.pulsesend.notifications.dto.NotificationResponse;
import com.pulsesend.notifications.dto.PagedResponse;
import com.pulsesend.notifications.dto.SendNotificationRequest;
import com.pulsesend.notifications.entity.Notification;
import com.pulsesend.notifications.entity.NotificationStatus;
import com.pulsesend.notifications.exception.ResourceNotFoundException;
import com.pulsesend.notifications.mapper.NotificationMapper;
import com.pulsesend.notifications.repository.DeliveryLogRepository;
import com.pulsesend.notifications.repository.NotificationRepository;
import com.pulsesend.notifications.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;
    private final DeliveryLogRepository deliveryLogRepository;
    private final NotificationMapper notificationMapper;

    public NotificationController(NotificationService notificationService,
                                  NotificationRepository notificationRepository,
                                  DeliveryLogRepository deliveryLogRepository,
                                  NotificationMapper notificationMapper) {
        this.notificationService = notificationService;
        this.notificationRepository = notificationRepository;
        this.deliveryLogRepository = deliveryLogRepository;
        this.notificationMapper = notificationMapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public NotificationResponse send(@Valid @RequestBody SendNotificationRequest request) {
        return notificationService.send(request);
    }

    @GetMapping("/{notificationRef}")
    public NotificationResponse get(@PathVariable String notificationRef) {
        return notificationService.getNotification(notificationRef);
    }

    @GetMapping
    public PagedResponse<NotificationResponse> list(@RequestParam(required = false) String recipientRef,
                                                    @RequestParam(required = false) NotificationStatus status,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        if (recipientRef != null) {
            return notificationService.forRecipient(recipientRef, pageable);
        }
        return notificationService.byStatus(status == null ? NotificationStatus.PENDING : status, pageable);
    }

    @GetMapping("/{notificationRef}/deliveries")
    @Transactional(readOnly = true)
    public List<DeliveryLogResponse> deliveries(@PathVariable String notificationRef) {
        Notification notification = notificationRepository.findByNotificationRef(notificationRef)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationRef));
        return notificationMapper.toLogResponses(
                deliveryLogRepository.findByNotificationIdOrderByAttemptNoAsc(notification.getId()));
    }
}
