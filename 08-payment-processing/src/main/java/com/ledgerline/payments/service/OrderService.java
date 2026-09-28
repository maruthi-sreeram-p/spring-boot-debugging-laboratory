package com.ledgerline.payments.service;

import com.ledgerline.payments.dto.CreateOrderRequest;
import com.ledgerline.payments.dto.OrderResponse;
import com.ledgerline.payments.entity.Merchant;
import com.ledgerline.payments.entity.OrderStatus;
import com.ledgerline.payments.entity.PaymentOrder;
import com.ledgerline.payments.exception.PaymentRuleException;
import com.ledgerline.payments.exception.ResourceNotFoundException;
import com.ledgerline.payments.mapper.PaymentMapper;
import com.ledgerline.payments.repository.MerchantRepository;
import com.ledgerline.payments.repository.PaymentOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ThreadLocalRandom;

@Service
public class OrderService {

    private final PaymentOrderRepository paymentOrderRepository;
    private final MerchantRepository merchantRepository;
    private final PaymentMapper paymentMapper;

    public OrderService(PaymentOrderRepository paymentOrderRepository,
                        MerchantRepository merchantRepository,
                        PaymentMapper paymentMapper) {
        this.paymentOrderRepository = paymentOrderRepository;
        this.merchantRepository = merchantRepository;
        this.paymentMapper = paymentMapper;
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        Merchant merchant = merchantRepository.findByCode(request.getMerchantCode())
                .orElseThrow(() -> new ResourceNotFoundException("Merchant", request.getMerchantCode()));
        if (!merchant.isActive()) {
            throw new PaymentRuleException("Merchant " + merchant.getCode() + " is not active");
        }

        PaymentOrder order = new PaymentOrder();
        order.setOrderRef(nextOrderRef());
        order.setMerchant(merchant);
        order.setCustomerRef(request.getCustomerRef());
        order.setAmount(request.getAmount());
        order.setCurrency(request.getCurrency() == null ? "INR" : request.getCurrency());
        order.setStatus(OrderStatus.AWAITING_PAYMENT);

        return paymentMapper.toResponse(paymentOrderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(String orderRef) {
        PaymentOrder order = paymentOrderRepository.findByOrderRef(orderRef)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderRef));
        return paymentMapper.toResponse(order);
    }

    private String nextOrderRef() {
        String candidate;
        do {
            candidate = "ORD-" + ThreadLocalRandom.current().nextInt(6000, 99_999);
        } while (paymentOrderRepository.existsByOrderRef(candidate));
        return candidate;
    }
}
