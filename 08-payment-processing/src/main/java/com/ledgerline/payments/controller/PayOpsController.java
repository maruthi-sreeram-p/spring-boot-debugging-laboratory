package com.ledgerline.payments.controller;

import com.ledgerline.payments.gateway.GatewayCall;
import com.ledgerline.payments.gateway.SimulatedPaymentGateway;
import com.ledgerline.payments.scheduling.ReconciliationJob;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payops")
public class PayOpsController {

    private final ReconciliationJob reconciliationJob;
    private final SimulatedPaymentGateway gateway;

    public PayOpsController(ReconciliationJob reconciliationJob, SimulatedPaymentGateway gateway) {
        this.reconciliationJob = reconciliationJob;
        this.gateway = gateway;
    }

    @PostMapping("/jobs/reconcile")
    public Map<String, Object> reconcile() {
        return Map.of("closed", reconciliationJob.reconcile());
    }

    /**
     * The settlement view: everything the acquirer accepted, including captures whose
     * response never reached us.
     */
    @GetMapping("/acquirer/calls")
    public List<GatewayCall> acquirerCalls(@RequestParam(required = false) String paymentRef) {
        return paymentRef == null ? gateway.allCalls() : gateway.lookup(paymentRef);
    }

    @PostMapping("/acquirer/reset")
    public Map<String, Object> resetAcquirer() {
        gateway.reset();
        return Map.of("cleared", true);
    }
}
