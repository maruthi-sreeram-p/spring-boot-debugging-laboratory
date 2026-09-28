package com.ledgerline.payments.gateway;

import com.ledgerline.payments.config.PaymentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stands in for the acquiring bank. Nothing leaves this process; the behaviour is driven by
 * the minor units of the amount, the same way a real sandbox uses magic card numbers.
 *
 *   .11  declined
 *   .22  the capture is taken but the response is lost
 *   .33  slow, then approved
 *   else approved
 *
 * Every call that reaches the acquirer is recorded, including the ones whose response never
 * made it back to us. That record is what settlement is reconciled against.
 */
@Component
public class SimulatedPaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(SimulatedPaymentGateway.class);

    private final PaymentProperties properties;
    private final List<GatewayCall> calls = new CopyOnWriteArrayList<>();
    private final AtomicLong sequence = new AtomicLong(4_471_000);

    public SimulatedPaymentGateway(PaymentProperties properties) {
        this.properties = properties;
    }

    public GatewayResult capture(String paymentRef, BigDecimal amount, String instrument) {
        String gatewayRef = properties.getGateway().getApprovalPrefix() + "-" + sequence.incrementAndGet();
        int minorUnits = amount.remainder(BigDecimal.ONE)
                .movePointRight(2)
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .intValueExact();

        if (minorUnits == 11) {
            calls.add(new GatewayCall(paymentRef, amount, instrument, "DECLINED", gatewayRef, Instant.now()));
            log.info("Simulator declined {} for {}", paymentRef, amount);
            return GatewayResult.declined("Insufficient funds");
        }

        if (minorUnits == 22) {
            calls.add(new GatewayCall(paymentRef, amount, instrument, "CAPTURED", gatewayRef, Instant.now()));
            log.warn("Simulator captured {} for {} but the response was lost", paymentRef, amount);
            throw new GatewayTimeoutException("No response from " + properties.getGateway().getName()
                    + " for " + paymentRef);
        }

        if (minorUnits == 33) {
            sleepQuietly(properties.getGateway().getSlowResponseMillis());
        } else {
            sleepQuietly(ThreadLocalRandom.current().nextLong(20, 80));
        }

        calls.add(new GatewayCall(paymentRef, amount, instrument, "CAPTURED", gatewayRef, Instant.now()));
        log.info("Simulator approved {} for {} as {}", paymentRef, amount, gatewayRef);
        return GatewayResult.approved(gatewayRef);
    }

    /**
     * What the acquirer believes happened for a payment reference. This is the settlement
     * view: every capture it accepted, whether or not we ever saw the response.
     */
    public List<GatewayCall> lookup(String paymentRef) {
        return calls.stream().filter(call -> call.paymentRef().equals(paymentRef)).toList();
    }

    public List<GatewayCall> allCalls() {
        return List.copyOf(calls);
    }

    public void reset() {
        calls.clear();
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
