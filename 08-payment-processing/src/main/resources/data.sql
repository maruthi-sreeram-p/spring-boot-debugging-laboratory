-- Reference and demo data for Ledgerline.
-- Idempotent: re-running on an existing database changes nothing.

INSERT IGNORE INTO merchants (id, code, name, active) VALUES
    (1, 'MER-NORTHWIND', 'Northwind Retail',    1),
    (2, 'MER-SPICEBOX',  'Spicebox Foods',      1),
    (3, 'MER-DORMANT',   'Dormant Traders Ltd', 0);

INSERT IGNORE INTO orders (id, order_ref, merchant_id, customer_ref, amount, currency, status, created_at, updated_at) VALUES
    (1, 'ORD-5001', 1, 'CUST-8841', 1299.00, 'INR', 'PAID',             '2025-05-02 11:04:00.000000', '2025-05-02 11:04:12.000000'),
    (2, 'ORD-5002', 1, 'CUST-8842',  849.50, 'INR', 'PAYMENT_FAILED',   '2025-05-03 09:21:00.000000', '2025-05-03 09:21:07.000000'),
    (3, 'ORD-5003', 2, 'CUST-9110', 2450.00, 'INR', 'AWAITING_PAYMENT', '2025-05-06 18:40:00.000000', '2025-05-06 18:40:00.000000'),
    (4, 'ORD-5004', 2, 'CUST-9114',  399.00, 'INR', 'AWAITING_PAYMENT', '2025-05-07 12:15:00.000000', '2025-05-07 12:15:00.000000'),
    (5, 'ORD-5005', 1, 'CUST-8850', 50000.00,'INR', 'AWAITING_PAYMENT', '2025-05-08 10:02:00.000000', '2025-05-08 10:02:00.000000'),
    (6, 'ORD-5006', 1, 'CUST-8851',  760.11, 'INR', 'AWAITING_PAYMENT', '2025-05-09 14:33:00.000000', '2025-05-09 14:33:00.000000'),
    (7, 'ORD-5007', 2, 'CUST-9120', 1120.22, 'INR', 'AWAITING_PAYMENT', '2025-05-09 16:05:00.000000', '2025-05-09 16:05:00.000000'),
    (8, 'ORD-5008', 2, 'CUST-9121', 3300.00, 'INR', 'AWAITING_PAYMENT', '2025-05-10 08:47:00.000000', '2025-05-10 08:47:00.000000');

INSERT IGNORE INTO payments (id, payment_ref, order_id, idempotency_key, amount, currency, instrument, status, attempts, gateway_ref, failure_reason, created_at, updated_at) VALUES
    (1, 'PAY-70001', 1, 'chk-8841-a91f', 1299.00, 'INR', 'CARD_VISA',  'SUCCEEDED', 1, 'SIMGW-4471023', NULL,                '2025-05-02 11:04:05.000000', '2025-05-02 11:04:12.000000'),
    (2, 'PAY-70002', 2, 'chk-8842-b23c',  849.50, 'INR', 'CARD_VISA',  'FAILED',    1, NULL,            'Insufficient funds','2025-05-03 09:21:02.000000', '2025-05-03 09:21:07.000000');

INSERT IGNORE INTO payment_attempts (id, payment_id, attempt_no, outcome, gateway_ref, message, created_at) VALUES
    (1, 1, 1, 'APPROVED', 'SIMGW-4471023', 'Captured',          '2025-05-02 11:04:12.000000'),
    (2, 2, 1, 'DECLINED', NULL,            'Insufficient funds','2025-05-03 09:21:07.000000');

-- Merchants use "Password123!", the payment operations account uses "Admin123!".
INSERT IGNORE INTO users (id, username, password_hash, role_name) VALUES
    (1, 'merchant@northwind.test', '$2a$10$Qoeh9Vl6k.enhigi0fhELeDmesn7WtAOX1O9MlpuAqq55890K5b7W', 'ROLE_MERCHANT'),
    (2, 'merchant@spicebox.test',  '$2a$10$Qoeh9Vl6k.enhigi0fhELeDmesn7WtAOX1O9MlpuAqq55890K5b7W', 'ROLE_MERCHANT'),
    (3, 'payops@ledgerline.test',  '$2a$10$BfSO2Iny00pHa97kZJKGfOmWQPg0EJiFX/Kp4ocpiShu1DVmmhsCa', 'ROLE_PAYOPS');
