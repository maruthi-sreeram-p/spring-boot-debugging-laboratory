-- Reference and demo data for the Northwind storefront.
-- Idempotent: re-running on an existing database changes nothing.

INSERT IGNORE INTO roles (id, name) VALUES
    (1, 'ROLE_CUSTOMER'),
    (2, 'ROLE_ADMIN');

-- Demo passwords: customers use "Password123!", the back-office account uses "Admin123!".
INSERT IGNORE INTO customers (id, email, password_hash, full_name, phone, status, created_at) VALUES
    (1, 'priya.sharma@example.com', '$2a$10$Qoeh9Vl6k.enhigi0fhELeDmesn7WtAOX1O9MlpuAqq55890K5b7W', 'Priya Sharma',  '+91-98200-11223', 'ACTIVE',    '2024-01-14 09:12:00.000000'),
    (2, 'arjun.mehta@example.com',  '$2a$10$Qoeh9Vl6k.enhigi0fhELeDmesn7WtAOX1O9MlpuAqq55890K5b7W', 'Arjun Mehta',   '+91-99300-44556', 'ACTIVE',    '2024-02-02 17:45:00.000000'),
    (3, 'lena.fischer@example.com', '$2a$10$Qoeh9Vl6k.enhigi0fhELeDmesn7WtAOX1O9MlpuAqq55890K5b7W', 'Lena Fischer',  '+49-151-2233445', 'ACTIVE',    '2024-03-21 11:03:00.000000'),
    (4, 'sam.oduya@example.com',    '$2a$10$Qoeh9Vl6k.enhigi0fhELeDmesn7WtAOX1O9MlpuAqq55890K5b7W', 'Sam Oduya',     '+254-712-334455', 'SUSPENDED', '2024-04-08 08:30:00.000000'),
    (9, 'ops.admin@northwind.test', '$2a$10$BfSO2Iny00pHa97kZJKGfOmWQPg0EJiFX/Kp4ocpiShu1DVmmhsCa', 'Back Office',   NULL,              'ACTIVE',    '2023-11-01 07:00:00.000000');

INSERT IGNORE INTO customer_roles (customer_id, role_id) VALUES
    (1, 1),
    (2, 1),
    (3, 1),
    (4, 1),
    (9, 2);

INSERT IGNORE INTO categories (id, name, slug) VALUES
    (1, 'Laptops',     'laptops'),
    (2, 'Audio',       'audio'),
    (3, 'Accessories', 'accessories'),
    (4, 'Displays',    'displays');

INSERT IGNORE INTO products (id, sku, name, description, price, stock_quantity, category_id, active, created_at) VALUES
    (1,  'NW-LAP-1401', 'Meridian 14 Ultrabook',        '14 inch magnesium chassis, 16 GB RAM, 512 GB NVMe',        1249.00,  24, 1, 1, '2023-12-01 10:00:00.000000'),
    (2,  'NW-LAP-1601', 'Meridian 16 Creator',          '16 inch, discrete GPU, 32 GB RAM, 1 TB NVMe',              1899.00,  11, 1, 1, '2023-12-01 10:05:00.000000'),
    (3,  'NW-LAP-1302', 'Cadet 13 Student Laptop',      'Entry level 13 inch, 8 GB RAM, 256 GB SSD',                 649.00,  63, 1, 1, '2024-01-18 12:20:00.000000'),
    (4,  'NW-AUD-2201', 'Auralis Over-Ear ANC',         'Active noise cancelling headphones, 38 hour battery',        279.00,  52, 2, 1, '2023-12-11 15:40:00.000000'),
    (5,  'NW-AUD-2202', 'Auralis Buds Pro',             'True wireless earbuds with wireless charging case',          149.00, 118, 2, 1, '2024-02-04 09:15:00.000000'),
    (6,  'NW-AUD-2210', 'Studio Monitor Pair 5in',      'Near-field studio monitors, sold as a pair',                 429.00,   9, 2, 1, '2024-02-19 16:00:00.000000'),
    (7,  'NW-ACC-3101', 'Mechanical Keyboard 87K',      'Tenkeyless, hot swappable switches, PBT keycaps',            129.00,  74, 3, 1, '2023-12-22 11:30:00.000000'),
    (8,  'NW-ACC-3102', 'Precision Mouse 8K',           'Lightweight wireless mouse, 8000 Hz polling',                 89.00,  96, 3, 1, '2024-01-09 14:10:00.000000'),
    (9,  'NW-ACC-3120', 'USB-C Dock 11-in-1',           'Dual display dock with 100 W passthrough charging',          179.00,  38, 3, 1, '2024-03-02 10:45:00.000000'),
    (10, 'NW-ACC-3140', 'Laptop Sleeve 14in',           'Water resistant felt sleeve',                                 39.00, 210, 3, 1, '2024-03-02 10:50:00.000000'),
    (11, 'NW-DIS-4101', 'Clarity 27 QHD Monitor',       '27 inch 1440p IPS, 165 Hz, USB-C',                           399.00,  17, 4, 1, '2024-01-25 13:25:00.000000'),
    (12, 'NW-DIS-4102', 'Clarity 32 4K Monitor',        '32 inch 4K IPS, factory calibrated',                         749.00,   6, 4, 1, '2024-02-28 09:55:00.000000'),
    (13, 'NW-DIS-4090', 'Clarity 24 FHD Monitor',       'Discontinued 24 inch 1080p panel',                           189.00,   0, 4, 0, '2023-08-14 08:00:00.000000'),
    (14, 'NW-ACC-3090', 'Braided USB-C Cable 2m',       'Discontinued cable, replaced by NW-ACC-3091',                 19.00,  45, 3, 0, '2023-07-30 08:00:00.000000');

INSERT IGNORE INTO orders (id, order_number, customer_id, status, subtotal, shipping_fee, total_amount, shipping_address, created_at, updated_at) VALUES
    (1, 'ORD-20240412-100341', 1, 'DELIVERED', 1378.00, 0.00,  1378.00, '12 Carter Road, Bandra West, Mumbai 400050, IN', '2024-04-12 18:22:41.000000', '2024-04-17 10:02:00.000000'),
    (2, 'ORD-20240605-233187', 1, 'SHIPPED',    238.00, 49.00,  287.00, '12 Carter Road, Bandra West, Mumbai 400050, IN', '2024-06-05 09:14:03.000000', '2024-06-06 07:40:00.000000'),
    (3, 'ORD-20240628-551209', 2, 'DELIVERED',  649.00, 0.00,   649.00, '4 Nehru Enclave, New Delhi 110019, IN',          '2024-06-28 14:51:19.000000', '2024-07-03 12:11:00.000000');

INSERT IGNORE INTO order_items (id, order_id, product_id, product_name, quantity, unit_price, line_total) VALUES
    (1, 1, 1,  'Meridian 14 Ultrabook',   1, 1249.00, 1249.00),
    (2, 1, 7,  'Mechanical Keyboard 87K', 1,  129.00,  129.00),
    (3, 2, 5,  'Auralis Buds Pro',        1,  149.00,  149.00),
    (4, 2, 8,  'Precision Mouse 8K',      1,   89.00,   89.00),
    (5, 3, 3,  'Cadet 13 Student Laptop', 1,  649.00,  649.00);
