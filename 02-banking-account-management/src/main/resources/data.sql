-- Reference and demo data for Meridian Bank.
-- Idempotent: ON CONFLICT DO NOTHING, and the identity sequences are re-synchronised at the end.

INSERT INTO roles (id, name) VALUES
    (1, 'ROLE_CUSTOMER'),
    (2, 'ROLE_TELLER')
ON CONFLICT (id) DO NOTHING;

-- Demo passwords: customers use "Password123!", the teller account uses "Admin123!".
INSERT INTO customers (id, email, password_hash, full_name, national_id, status, created_at) VALUES
    (1, 'devika.rao@example.com',    '$2a$10$Qoeh9Vl6k.enhigi0fhELeDmesn7WtAOX1O9MlpuAqq55890K5b7W', 'Devika Rao',      'NID-4471-2298', 'ACTIVE', '2023-05-04 10:15:00+00'),
    (2, 'martin.oduor@example.com',  '$2a$10$Qoeh9Vl6k.enhigi0fhELeDmesn7WtAOX1O9MlpuAqq55890K5b7W', 'Martin Oduor',    'NID-8812-0043', 'ACTIVE', '2023-06-19 14:02:00+00'),
    (3, 'yuki.tanabe@example.com',   '$2a$10$Qoeh9Vl6k.enhigi0fhELeDmesn7WtAOX1O9MlpuAqq55890K5b7W', 'Yuki Tanabe',     'NID-3390-7716', 'ACTIVE', '2023-09-30 08:44:00+00'),
    (9, 'teller.desk@meridian.test', '$2a$10$BfSO2Iny00pHa97kZJKGfOmWQPg0EJiFX/Kp4ocpiShu1DVmmhsCa', 'Branch Teller',   'NID-0000-0001', 'ACTIVE', '2023-01-02 07:00:00+00')
ON CONFLICT (id) DO NOTHING;

INSERT INTO customer_roles (customer_id, role_id) VALUES
    (1, 1), (2, 1), (3, 1), (9, 2)
ON CONFLICT DO NOTHING;

INSERT INTO accounts (id, account_number, customer_id, account_type, currency, balance, status, opened_at) VALUES
    (1, 'MB-0000-1001', 1, 'CHECKING', 'INR',  84250.00, 'ACTIVE', '2023-05-04 10:20:00+00'),
    (2, 'MB-0000-1002', 1, 'SAVINGS',  'INR', 312800.00, 'ACTIVE', '2023-05-04 10:24:00+00'),
    (3, 'MB-0000-1003', 2, 'CHECKING', 'INR',  17640.50, 'ACTIVE', '2023-06-19 14:10:00+00'),
    (4, 'MB-0000-1004', 3, 'CHECKING', 'INR',   9120.00, 'ACTIVE', '2023-09-30 08:50:00+00'),
    (5, 'MB-0000-1005', 3, 'SAVINGS',  'USD',   4300.00, 'ACTIVE', '2024-01-11 11:05:00+00'),
    (6, 'MB-0000-1006', 2, 'SAVINGS',  'INR',       0.00, 'CLOSED', '2023-06-19 14:12:00+00')
ON CONFLICT (id) DO NOTHING;

INSERT INTO transactions (id, reference, account_id, counterparty_account_id, tx_type, amount, balance_after, description, created_at) VALUES
    (1, 'DEP-20240902-000001', 1, NULL, 'DEPOSIT',      50000.00,  50000.00, 'Salary credit September',      '2024-09-02 04:31:00+00'),
    (2, 'WDR-20240905-000002', 1, NULL, 'WITHDRAWAL',    8000.00,  42000.00, 'ATM Bandra West',              '2024-09-05 12:11:00+00'),
    (3, 'TRF-20240908-000003', 1, 3,    'TRANSFER_OUT',  5000.00,  37000.00, 'Rent share',                   '2024-09-08 06:45:00+00'),
    (4, 'TRF-20240908-000004', 3, 1,    'TRANSFER_IN',   5000.00,  22640.50, 'Rent share',                   '2024-09-08 06:45:00+00'),
    (5, 'DEP-20241001-000005', 1, NULL, 'DEPOSIT',      50000.00,  87000.00, 'Salary credit October',        '2024-10-01 04:28:00+00'),
    (6, 'WDR-20241014-000006', 3, NULL, 'WITHDRAWAL',    5000.00,  17640.50, 'ATM Westlands',                '2024-10-14 09:02:00+00'),
    (7, 'WDR-20241102-000007', 1, NULL, 'WITHDRAWAL',    2750.00,  84250.00, 'Card payment - utilities',     '2024-11-02 15:20:00+00'),
    (8, 'DEP-20240115-000008', 2, NULL, 'DEPOSIT',     312800.00, 312800.00, 'Fixed deposit maturity',       '2024-01-15 05:00:00+00'),
    (9, 'DEP-20240930-000009', 4, NULL, 'DEPOSIT',       9120.00,   9120.00, 'Opening balance',              '2023-09-30 08:55:00+00'),
    (10,'DEP-20240111-000010', 5, NULL, 'DEPOSIT',       4300.00,   4300.00, 'Opening balance',              '2024-01-11 11:10:00+00')
ON CONFLICT (id) DO NOTHING;

SELECT setval(pg_get_serial_sequence('roles', 'id'),        COALESCE((SELECT MAX(id) FROM roles), 1));
SELECT setval(pg_get_serial_sequence('customers', 'id'),    COALESCE((SELECT MAX(id) FROM customers), 1));
SELECT setval(pg_get_serial_sequence('accounts', 'id'),     COALESCE((SELECT MAX(id) FROM accounts), 1));
SELECT setval(pg_get_serial_sequence('transactions', 'id'), COALESCE((SELECT MAX(id) FROM transactions), 1));
