package com.pulsesend.notifications.channel;

import com.pulsesend.notifications.entity.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Stands in for the email, SMS and in-app providers. Nothing leaves this process.
 *
 * The behaviour is driven by the destination, the way a provider sandbox uses reserved
 * addresses:
 *
 *   contains "bounce"  the provider accepts the request and rejects the recipient
 *   contains "outage"  the provider itself is unavailable and the call throws
 *   anything else      delivered
 *
 * Everything the providers actually sent is recorded here. That record is the only honest
 * answer to "did the customer receive this", and it is what the delivery dashboard is
 * supposed to agree with.
 */
@Component
public class ChannelDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ChannelDispatcher.class);

    private static final long PROVIDER_TIMEOUT_MILLIS = 500;

    private final List<DispatchedMessage> outbox = new CopyOnWriteArrayList<>();

    public DeliveryOutcome deliver(Channel channel, String notificationRef, String destination,
                                   String subject, String body) {
        String target = destination.toLowerCase();

        if (target.contains("outage")) {
            // An unavailable provider does not refuse quickly; the socket hangs until the
            // client gives up, so the caller waits out the full timeout on every attempt.
            sleepQuietly(PROVIDER_TIMEOUT_MILLIS);
            log.warn("Provider for {} timed out while sending {}", channel, notificationRef);
            throw new TransientDeliveryException("The " + channel + " provider is unavailable");
        }

        if (target.contains("bounce")) {
            log.info("Provider rejected {} for {}", notificationRef, destination);
            return DeliveryOutcome.rejected("Recipient rejected the message");
        }

        outbox.add(new DispatchedMessage(notificationRef, channel.name(), destination, subject, body, Instant.now()));
        log.info("Delivered {} to {} over {}", notificationRef, destination, channel);
        return DeliveryOutcome.delivered("Accepted by the " + channel.name().toLowerCase() + " provider");
    }

    public List<DispatchedMessage> outbox() {
        return List.copyOf(outbox);
    }

    public List<DispatchedMessage> outboxFor(String notificationRef) {
        return outbox.stream().filter(message -> message.notificationRef().equals(notificationRef)).toList();
    }

    public void reset() {
        outbox.clear();
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
