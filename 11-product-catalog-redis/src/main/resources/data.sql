-- Reference and demo data for the Lumen Retail catalogue.
-- Idempotent: ON CONFLICT DO NOTHING plus a sequence re-sync at the end.

INSERT INTO categories (id, slug, name, position, active) VALUES
    (1, 'laptops',     'Laptops',          1, TRUE),
    (2, 'audio',       'Audio',            2, TRUE),
    (3, 'displays',    'Displays',         3, TRUE),
    (4, 'accessories', 'Accessories',      4, TRUE),
    (5, 'clearance',   'Clearance',        9, FALSE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO products (id, sku, name, description, brand, category_id, price, currency, active, created_at, updated_at) VALUES
    (1,  'LM-LAP-1401', 'Meridian 14 Ultrabook',      '14 inch magnesium chassis, 16 GB RAM, 512 GB NVMe',  'Meridian', 1, 124900.00, 'INR', TRUE,  '2024-11-02 09:00:00+00', '2024-11-02 09:00:00+00'),
    (2,  'LM-LAP-1601', 'Meridian 16 Creator',        '16 inch, discrete GPU, 32 GB RAM, 1 TB NVMe',        'Meridian', 1, 189900.00, 'INR', TRUE,  '2024-11-02 09:05:00+00', '2024-11-02 09:05:00+00'),
    (3,  'LM-LAP-1302', 'Cadet 13 Student Laptop',    'Entry level 13 inch, 8 GB RAM, 256 GB SSD',          'Cadet',    1,  64900.00, 'INR', TRUE,  '2025-01-18 12:20:00+00', '2025-01-18 12:20:00+00'),
    (4,  'LM-AUD-2201', 'Auralis Over-Ear ANC',       'Active noise cancelling headphones, 38 hour battery','Auralis',  2,  27900.00, 'INR', TRUE,  '2024-12-11 15:40:00+00', '2024-12-11 15:40:00+00'),
    (5,  'LM-AUD-2202', 'Auralis Buds Pro',           'True wireless earbuds with wireless charging case',  'Auralis',  2,  14900.00, 'INR', TRUE,  '2025-02-04 09:15:00+00', '2025-02-04 09:15:00+00'),
    (6,  'LM-AUD-2210', 'Studio Monitor Pair 5in',    'Near-field studio monitors, sold as a pair',         'Auralis',  2,  42900.00, 'INR', TRUE,  '2025-02-19 16:00:00+00', '2025-02-19 16:00:00+00'),
    (7,  'LM-DIS-4101', 'Clarity 27 QHD Monitor',     '27 inch 1440p IPS, 165 Hz, USB-C',                   'Clarity',  3,  39900.00, 'INR', TRUE,  '2025-01-25 13:25:00+00', '2025-01-25 13:25:00+00'),
    (8,  'LM-DIS-4102', 'Clarity 32 4K Monitor',      '32 inch 4K IPS, factory calibrated',                 'Clarity',  3,  74900.00, 'INR', TRUE,  '2025-02-28 09:55:00+00', '2025-02-28 09:55:00+00'),
    (9,  'LM-ACC-3101', 'Mechanical Keyboard 87K',    'Tenkeyless, hot swappable switches, PBT keycaps',    'Lumen',    4,  12900.00, 'INR', TRUE,  '2024-12-22 11:30:00+00', '2024-12-22 11:30:00+00'),
    (10, 'LM-ACC-3102', 'Precision Mouse 8K',         'Lightweight wireless mouse, 8000 Hz polling',        'Lumen',    4,   8900.00, 'INR', TRUE,  '2025-01-09 14:10:00+00', '2025-01-09 14:10:00+00'),
    (11, 'LM-ACC-3120', 'USB-C Dock 11-in-1',         'Dual display dock with 100 W passthrough charging',  'Lumen',    4,  17900.00, 'INR', TRUE,  '2025-03-02 10:45:00+00', '2025-03-02 10:45:00+00'),
    (12, 'LM-ACC-3140', 'Laptop Sleeve 14in',         'Water resistant felt sleeve',                        'Lumen',    4,   3900.00, 'INR', TRUE,  '2025-03-02 10:50:00+00', '2025-03-02 10:50:00+00'),
    (13, 'LM-DIS-4090', 'Clarity 24 FHD Monitor',     'Discontinued 24 inch 1080p panel',                   'Clarity',  3,  18900.00, 'INR', FALSE, '2023-08-14 08:00:00+00', '2024-06-01 08:00:00+00'),
    (14, 'LM-DIS-4150', 'Lumen Portable 15 Monitor',  'Travel monitor, 1080p, single cable USB-C',          'Lumen',    3,  21900.00, 'INR', TRUE,  '2025-03-05 11:15:00+00', '2025-03-05 11:15:00+00'),
    (15, 'LM-ACC-3160', 'Auralis Ear Tips Kit',       'Replacement silicone tips, three sizes',             'Auralis',  4,   1200.00, 'INR', TRUE,  '2025-03-06 09:30:00+00', '2025-03-06 09:30:00+00')
ON CONFLICT (id) DO NOTHING;

INSERT INTO product_variants (id, product_id, variant_sku, label, price_delta, active) VALUES
    (1, 1, 'LM-LAP-1401-SLV', 'Silver',            0.00,     TRUE),
    (2, 1, 'LM-LAP-1401-GRP', 'Graphite',       2000.00,     TRUE),
    (3, 2, 'LM-LAP-1601-32G', '32 GB RAM',         0.00,     TRUE),
    (4, 2, 'LM-LAP-1601-64G', '64 GB RAM',     35000.00,     TRUE),
    (5, 4, 'LM-AUD-2201-BLK', 'Black',             0.00,     TRUE),
    (6, 4, 'LM-AUD-2201-SND', 'Sand',           1500.00,     TRUE),
    (7, 7, 'LM-DIS-4101-STD', 'Standard stand',    0.00,     TRUE),
    (8, 7, 'LM-DIS-4101-ARM', 'Monitor arm',    6500.00,     TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO price_history (id, product_id, old_price, new_price, changed_by, changed_at) VALUES
    (1, 4, 29900.00, 27900.00, 'merch@lumen.test', '2025-03-14 10:02:00+00'),
    (2, 7, 42900.00, 39900.00, 'merch@lumen.test', '2025-03-20 16:40:00+00')
ON CONFLICT (id) DO NOTHING;

-- Storefront reads use "Password123!", the merchandising account uses "Admin123!".
INSERT INTO users (id, username, password_hash, role_name) VALUES
    (1, 'storefront@lumen.test', '$2a$10$Qoeh9Vl6k.enhigi0fhELeDmesn7WtAOX1O9MlpuAqq55890K5b7W', 'ROLE_STOREFRONT'),
    (2, 'merch@lumen.test',      '$2a$10$BfSO2Iny00pHa97kZJKGfOmWQPg0EJiFX/Kp4ocpiShu1DVmmhsCa', 'ROLE_MERCH')
ON CONFLICT (id) DO NOTHING;

SELECT setval(pg_get_serial_sequence('categories', 'id'),       COALESCE((SELECT MAX(id) FROM categories), 1));
SELECT setval(pg_get_serial_sequence('products', 'id'),         COALESCE((SELECT MAX(id) FROM products), 1));
SELECT setval(pg_get_serial_sequence('product_variants', 'id'), COALESCE((SELECT MAX(id) FROM product_variants), 1));
SELECT setval(pg_get_serial_sequence('price_history', 'id'),    COALESCE((SELECT MAX(id) FROM price_history), 1));
SELECT setval(pg_get_serial_sequence('users', 'id'),            COALESCE((SELECT MAX(id) FROM users), 1));
