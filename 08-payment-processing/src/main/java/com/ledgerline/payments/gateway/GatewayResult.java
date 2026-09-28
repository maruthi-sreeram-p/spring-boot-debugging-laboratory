package com.ledgerline.payments.gateway;

public record GatewayResult(boolean approved, String gatewayRef, String message) {

    public static GatewayResult approved(String gatewayRef) {
        return new GatewayResult(true, gatewayRef, "Captured");
    }

    public static GatewayResult declined(String message) {
        return new GatewayResult(false, null, message);
    }
}
