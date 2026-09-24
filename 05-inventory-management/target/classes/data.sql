-- Reference and demo data for Vantage Supply.
-- Idempotent: ON CONFLICT DO NOTHING plus a sequence re-sync at the end.

INSERT INTO warehouses (id, code, name, city, active) VALUES
    (1, 'WH-BLR', 'Bengaluru Central', 'Bengaluru', TRUE),
    (2, 'WH-MUM', 'Mumbai Port',       'Mumbai',    TRUE),
    (3, 'WH-DEL', 'Delhi North',       'Delhi',     TRUE),
    (4, 'WH-CHE', 'Chennai Annexe',    'Chennai',   FALSE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO products (id, sku, name, unit, reorder_level, active) VALUES
    (1, 'VS-CBL-0101', 'USB-C to USB-C cable 2m',   'EACH', 200, TRUE),
    (2, 'VS-PSU-0210', 'Desktop power supply 650W', 'EACH',  40, TRUE),
    (3, 'VS-SSD-0330', 'NVMe SSD 1TB',              'EACH',  60, TRUE),
    (4, 'VS-MON-0415', 'Monitor 27 inch QHD',       'EACH',  25, TRUE),
    (5, 'VS-KBD-0502', 'Mechanical keyboard TKL',   'EACH',  50, TRUE),
    (6, 'VS-BOX-0601', 'Shipping carton medium',    'EACH', 500, TRUE),
    (7, 'VS-CBL-0099', 'Micro-USB cable 1m',        'EACH',   0, FALSE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO suppliers (id, code, name, email) VALUES
    (1, 'SUP-ACT', 'Acton Components Ltd', 'orders@actoncomponents.test'),
    (2, 'SUP-NVE', 'Novelle Electronics',  'supply@novelle.test'),
    (3, 'SUP-PKG', 'Pacific Packaging Co', 'sales@pacificpackaging.test')
ON CONFLICT (id) DO NOTHING;

INSERT INTO stock_levels (id, product_id, warehouse_id, quantity_on_hand, quantity_reserved, updated_at) VALUES
    (1,  1, 1, 1420, 180, '2025-01-14 08:00:00+00'),
    (2,  1, 2,  860,  40, '2025-01-14 08:00:00+00'),
    (3,  1, 3,  210,   0, '2025-01-14 08:00:00+00'),
    (4,  2, 1,   95,  30, '2025-01-16 09:30:00+00'),
    (5,  2, 2,   48,  12, '2025-01-16 09:30:00+00'),
    (6,  3, 1,  310,  95, '2025-01-20 11:15:00+00'),
    (7,  3, 3,  140,  20, '2025-01-20 11:15:00+00'),
    (8,  4, 2,   62,  18, '2025-02-02 14:45:00+00'),
    (9,  5, 1,  240,  35, '2025-02-08 10:05:00+00'),
    (10, 5, 3,   88,   8, '2025-02-08 10:05:00+00'),
    (11, 6, 1, 3600, 400, '2025-02-11 07:20:00+00'),
    (12, 6, 2, 2100, 150, '2025-02-11 07:20:00+00')
ON CONFLICT (id) DO NOTHING;

INSERT INTO stock_movements (id, reference, product_id, warehouse_id, movement_type, quantity, reason, created_at) VALUES
    (1, 'GRN-20250114-0001', 1, 1, 'RECEIPT',    1500, 'PO-2025-0007 goods receipt', '2025-01-14 08:00:00+00'),
    (2, 'ISS-20250118-0002', 1, 1, 'ISSUE',        80, 'Sales order SO-11204',       '2025-01-18 12:40:00+00'),
    (3, 'GRN-20250116-0003', 2, 1, 'RECEIPT',     120, 'PO-2025-0009 goods receipt', '2025-01-16 09:30:00+00'),
    (4, 'ISS-20250122-0004', 2, 1, 'ISSUE',        25, 'Sales order SO-11251',       '2025-01-22 15:10:00+00'),
    (5, 'GRN-20250120-0005', 3, 1, 'RECEIPT',     350, 'PO-2025-0011 goods receipt', '2025-01-20 11:15:00+00'),
    (6, 'ADJ-20250205-0006', 3, 1, 'ADJUSTMENT',  -40, 'Cycle count correction',     '2025-02-05 16:00:00+00'),
    (7, 'GRN-20250202-0007', 4, 2, 'RECEIPT',      70, 'PO-2025-0014 goods receipt', '2025-02-02 14:45:00+00'),
    (8, 'ISS-20250210-0008', 4, 2, 'ISSUE',         8, 'Sales order SO-11390',       '2025-02-10 09:25:00+00')
ON CONFLICT (id) DO NOTHING;

INSERT INTO purchase_orders (id, po_number, supplier_id, warehouse_id, status, expected_date, created_at) VALUES
    (1, 'PO-2025-0021', 1, 1, 'OPEN',     '2025-06-18', '2025-06-02 09:00:00+00'),
    (2, 'PO-2025-0022', 2, 2, 'OPEN',     '2025-06-20', '2025-06-03 11:30:00+00'),
    (3, 'PO-2025-0019', 3, 1, 'RECEIVED', '2025-05-28', '2025-05-12 08:15:00+00')
ON CONFLICT (id) DO NOTHING;

INSERT INTO purchase_order_lines (id, purchase_order_id, product_id, quantity_ordered, quantity_received, unit_cost) VALUES
    (1, 1, 1, 1000,    0,   2.40),
    (2, 1, 5,  150,    0,  38.00),
    (3, 2, 4,   40,    0, 210.00),
    (4, 2, 3,  100,    0,  74.50),
    (5, 3, 6, 2000, 2000,   0.85)
ON CONFLICT (id) DO NOTHING;

-- Sign-in accounts. Warehouse staff use "Password123!", the inventory controller uses "Admin123!".
INSERT INTO users (id, username, password_hash, role_name, enabled) VALUES
    (1, 'dispatch.blr@vantage.test', '$2a$10$Qoeh9Vl6k.enhigi0fhELeDmesn7WtAOX1O9MlpuAqq55890K5b7W', 'ROLE_WAREHOUSE',  TRUE),
    (2, 'dispatch.mum@vantage.test', '$2a$10$Qoeh9Vl6k.enhigi0fhELeDmesn7WtAOX1O9MlpuAqq55890K5b7W', 'ROLE_WAREHOUSE',  TRUE),
    (3, 'controller@vantage.test',   '$2a$10$BfSO2Iny00pHa97kZJKGfOmWQPg0EJiFX/Kp4ocpiShu1DVmmhsCa', 'ROLE_CONTROLLER', TRUE)
ON CONFLICT (id) DO NOTHING;

SELECT setval(pg_get_serial_sequence('warehouses', 'id'),           COALESCE((SELECT MAX(id) FROM warehouses), 1));
SELECT setval(pg_get_serial_sequence('products', 'id'),             COALESCE((SELECT MAX(id) FROM products), 1));
SELECT setval(pg_get_serial_sequence('suppliers', 'id'),            COALESCE((SELECT MAX(id) FROM suppliers), 1));
SELECT setval(pg_get_serial_sequence('stock_levels', 'id'),         COALESCE((SELECT MAX(id) FROM stock_levels), 1));
SELECT setval(pg_get_serial_sequence('stock_movements', 'id'),      COALESCE((SELECT MAX(id) FROM stock_movements), 1));
SELECT setval(pg_get_serial_sequence('purchase_orders', 'id'),      COALESCE((SELECT MAX(id) FROM purchase_orders), 1));
SELECT setval(pg_get_serial_sequence('purchase_order_lines', 'id'), COALESCE((SELECT MAX(id) FROM purchase_order_lines), 1));
SELECT setval(pg_get_serial_sequence('users', 'id'),                COALESCE((SELECT MAX(id) FROM users), 1));
