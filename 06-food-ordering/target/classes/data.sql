-- Reference and demo data for Spicebox.
-- Idempotent: re-running on an existing database changes nothing.

-- Customers use "Password123!", the operations account uses "Admin123!".
INSERT IGNORE INTO customers (id, email, password_hash, full_name, phone, default_address, role_name, created_at) VALUES
    (1, 'ayesha.khan@example.com',  '$2a$10$Qoeh9Vl6k.enhigi0fhELeDmesn7WtAOX1O9MlpuAqq55890K5b7W', 'Ayesha Khan',   '+91-9845012233', 'Flat 402, Palm Grove, Indiranagar, Bengaluru 560038', 'ROLE_CUSTOMER', '2024-08-11 18:22:00.000000'),
    (2, 'daniel.otieno@example.com','$2a$10$Qoeh9Vl6k.enhigi0fhELeDmesn7WtAOX1O9MlpuAqq55890K5b7W', 'Daniel Otieno', '+91-9845044556', '17 Brigade Terrace, Koramangala, Bengaluru 560034',   'ROLE_CUSTOMER', '2024-09-02 12:40:00.000000'),
    (3, 'mira.bhatt@example.com',   '$2a$10$Qoeh9Vl6k.enhigi0fhELeDmesn7WtAOX1O9MlpuAqq55890K5b7W', 'Mira Bhatt',    '+91-9845077889', '9 Church View, Frazer Town, Bengaluru 560005',        'ROLE_CUSTOMER', '2025-01-19 20:05:00.000000'),
    (9, 'ops@spicebox.test',        '$2a$10$BfSO2Iny00pHa97kZJKGfOmWQPg0EJiFX/Kp4ocpiShu1DVmmhsCa', 'Spicebox Ops',  NULL,             'Spicebox HQ, Bengaluru 560001',                       'ROLE_OPS',      '2024-01-05 09:00:00.000000');

INSERT IGNORE INTO restaurants (id, name, cuisine, city, prep_minutes, active) VALUES
    (1, 'Curry Leaf Kitchen',  'South Indian', 'Bengaluru', 25, 1),
    (2, 'Napoli Corner',       'Italian',      'Bengaluru', 30, 1),
    (3, 'Wok This Way',        'Pan Asian',    'Bengaluru', 20, 1),
    (4, 'The Grill House',     'Continental',  'Bengaluru', 35, 0);

INSERT IGNORE INTO menu_items (id, restaurant_id, name, description, category, price, available) VALUES
    (1,  1, 'Masala Dosa',            'Crisp rice crepe with spiced potato',        'Mains',      140.00, 1),
    (2,  1, 'Filter Coffee',          'Traditional south Indian filter coffee',     'Beverages',   60.00, 1),
    (3,  1, 'Idli Sambar',            'Two idlis with sambar and chutney',          'Mains',      110.00, 1),
    (4,  1, 'Mysore Pak',             'Ghee sweet, sold per piece',                 'Desserts',    80.00, 1),
    (5,  1, 'Rava Kesari',            'Semolina pudding with saffron',              'Desserts',    90.00, 0),
    (6,  2, 'Margherita Pizza',       'San Marzano tomato, fior di latte, basil',   'Pizza',      420.00, 1),
    (7,  2, 'Penne Arrabbiata',       'Penne in a spiced tomato sauce',             'Pasta',      380.00, 1),
    (8,  2, 'Tiramisu',               'Classic mascarpone and espresso dessert',    'Desserts',   260.00, 1),
    (9,  2, 'Garlic Bread',           'Sourdough with garlic butter',               'Sides',      180.00, 1),
    (10, 3, 'Pad Thai',               'Rice noodles, tamarind, peanuts',            'Mains',      340.00, 1),
    (11, 3, 'Chilli Garlic Noodles',  'Hakka noodles with chilli garlic sauce',     'Mains',      290.00, 1),
    (12, 3, 'Spring Rolls',           'Vegetable spring rolls, four pieces',        'Starters',   190.00, 1),
    (13, 4, 'Grilled Chicken Steak',  'With herb butter and seasonal vegetables',   'Mains',      560.00, 1);

INSERT IGNORE INTO food_orders (id, order_code, customer_id, restaurant_id, status, subtotal, delivery_fee, total_amount, delivery_address, placed_at, updated_at) VALUES
    (1, 'SPX-20250310-4471', 1, 1, 'DELIVERED', 340.00, 39.00, 379.00, 'Flat 402, Palm Grove, Indiranagar, Bengaluru 560038', '2025-03-10 13:12:00.000000', '2025-03-10 14:02:00.000000'),
    (2, 'SPX-20250314-4488', 2, 2, 'DELIVERED', 800.00,  0.00, 800.00, '17 Brigade Terrace, Koramangala, Bengaluru 560034',   '2025-03-14 20:41:00.000000', '2025-03-14 21:35:00.000000'),
    (3, 'SPX-20250321-4506', 1, 3, 'CANCELLED', 630.00,  0.00, 630.00, 'Flat 402, Palm Grove, Indiranagar, Bengaluru 560038', '2025-03-21 19:05:00.000000', '2025-03-21 19:09:00.000000');

INSERT IGNORE INTO order_items (id, order_id, menu_item_id, item_name, quantity, unit_price, line_total) VALUES
    (1, 1, 1,  'Masala Dosa',      2, 140.00, 280.00),
    (2, 1, 2,  'Filter Coffee',    1,  60.00,  60.00),
    (3, 2, 6,  'Margherita Pizza', 1, 420.00, 420.00),
    (4, 2, 7,  'Penne Arrabbiata', 1, 380.00, 380.00),
    (5, 3, 10, 'Pad Thai',         1, 340.00, 340.00),
    (6, 3, 11, 'Chilli Garlic Noodles', 1, 290.00, 290.00);

INSERT IGNORE INTO order_notifications (id, order_id, channel, recipient, subject, sent_at) VALUES
    (1, 1, 'EMAIL', 'ayesha.khan@example.com',   'Your Spicebox order SPX-20250310-4471 is confirmed', '2025-03-10 13:12:04.000000'),
    (2, 2, 'EMAIL', 'daniel.otieno@example.com', 'Your Spicebox order SPX-20250314-4488 is confirmed', '2025-03-14 20:41:03.000000');
