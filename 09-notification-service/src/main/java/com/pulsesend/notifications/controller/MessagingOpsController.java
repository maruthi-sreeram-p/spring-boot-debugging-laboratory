package com.pulsesend.notifications.controller;

import com.pulsesend.notifications.channel.ChannelDispatcher;
import com.pulsesend.notifications.channel.DispatchedMessage;
import com.pulsesend.notifications.dto.BroadcastRequest;
import com.pulsesend.notifications.dto.NotificationResponse;
import com.pulsesend.notifications.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ops")
public class MessagingOpsController {

    private final NotificationService notificationService;
    private final ChannelDispatcher dispatcher;

    public MessagingOpsController(NotificationService notificationService, ChannelDispatcher dispatcher) {
        this.notificationService = notificationService;
        this.dispatcher = dispatcher;
    }

    @PostMapping("/broadcast")
    public List<NotificationResponse> broadcast(@Valid @RequestBody BroadcastRequest request) {
        return notificationService.broadcast(request);
    }

    @PostMapping("/notifications/{notificationRef}/requeue")
    public NotificationResponse requeue(@PathVariable String notificationRef) {
        return notificationService.requeue(notificationRef);
    }

    /**
     * What the providers actually sent. The delivery dashboard is supposed to agree with
     * this list.
     */
    @GetMapping("/outbox")
    public List<DispatchedMessage> outbox(@RequestParam(required = false) String notificationRef) {
        return notificationRef == null ? dispatcher.outbox() : dispatcher.outboxFor(notificationRef);
    }

    @PostMapping("/outbox/reset")
    public Map<String, Object> resetOutbox() {
        dispatcher.reset();
        return Map.of("cleared", true);
    }
}
