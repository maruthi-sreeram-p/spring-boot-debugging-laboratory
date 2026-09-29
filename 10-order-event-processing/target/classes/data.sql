-- Reference and demo data for the Riverstone order pipeline.
-- Idempotent: ON CONFLICT DO NOTHING plus a sequence re-sync at the end.

INSERT INTO stock_items (id, sku, name, available, reserved) VALUES
    (1, 'RS-WIDGET-01',  'Standard widget',            5000, 0),
    (2, 'RS-WIDGET-02',  'Reinforced widget',          1200, 0),
    (3, 'RS-GADGET-01',  'Compact gadget',              800, 0),
    (4, 'RS-SCARCE-01',  'Limited run assembly',           3, 0),
    (5, 'RS-GHOST-01',   'Pre-release assembly',         500, 0),
    (6, 'RS-POISON-01',  'Discontinued assembly',        500, 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO users (id, username, password_hash, role_name) VALUES
    (1, 'orders@riverstone.test',  '$2a$10$Qoeh9Vl6k.enhigi0fhELeDmesn7WtAOX1O9MlpuAqq55890K5b7W', 'ROLE_ORDERS'),
    (2, 'platform@riverstone.test','$2a$10$BfSO2Iny00pHa97kZJKGfOmWQPg0EJiFX/Kp4ocpiShu1DVmmhsCa', 'ROLE_PLATFORM')
ON CONFLICT (id) DO NOTHING;

SELECT setval(pg_get_serial_sequence('stock_items', 'id'),        COALESCE((SELECT MAX(id) FROM stock_items), 1));
SELECT setval(pg_get_serial_sequence('orders', 'id'),             COALESCE((SELECT MAX(id) FROM orders), 1));
SELECT setval(pg_get_serial_sequence('reservations', 'id'),       COALESCE((SELECT MAX(id) FROM reservations), 1));
SELECT setval(pg_get_serial_sequence('order_notifications', 'id'),COALESCE((SELECT MAX(id) FROM order_notifications), 1));
SELECT setval(pg_get_serial_sequence('event_log', 'id'),          COALESCE((SELECT MAX(id) FROM event_log), 1));
SELECT setval(pg_get_serial_sequence('users', 'id'),              COALESCE((SELECT MAX(id) FROM users), 1));
