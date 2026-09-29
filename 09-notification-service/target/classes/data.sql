-- Reference and demo data for Pulsesend.
-- Idempotent: ON CONFLICT DO NOTHING plus a sequence re-sync at the end.

INSERT INTO notification_templates (id, code, channel, subject_template, body_template, active) VALUES
    (1, 'welcome',           'EMAIL',  'Welcome to {{brand}}',                  'Hello {{name}}, thanks for joining {{brand}}.',                  TRUE),
    (2, 'password.reset',    'EMAIL',  'Reset your {{brand}} password',         'Hello {{name}}, use code {{code}} to reset your password.',      TRUE),
    (3, 'order.shipped',     'EMAIL',  'Your order {{orderRef}} has shipped',   'Hello {{name}}, order {{orderRef}} is on its way.',              TRUE),
    (4, 'order.delivered',   'EMAIL',  'Your order {{orderRef}} was delivered', 'Hello {{name}}, order {{orderRef}} was delivered today.',        TRUE),
    (5, 'otp',               'SMS',    'OTP',                                   '{{code}} is your {{brand}} verification code.',                  TRUE),
    (6, 'delivery.eta',      'SMS',    'Delivery update',                       'Your order {{orderRef}} arrives by {{eta}}.',                    TRUE),
    (7, 'payment.receipt',   'EMAIL',  'Receipt for {{orderRef}}',              'Hello {{name}}, we received {{amount}} for order {{orderRef}}.', TRUE),
    (8, 'inbox.mention',     'IN_APP', 'You were mentioned',                    '{{actor}} mentioned you in {{context}}.',                        TRUE),
    (9, 'legacy.newsletter', 'EMAIL',  'Newsletter',                            'Monthly newsletter.',                                            FALSE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO notification_preferences (id, recipient_ref, channel, enabled, updated_at) VALUES
    (1, 'CUST-1001', 'EMAIL',  TRUE,  '2025-02-10 08:00:00+00'),
    (2, 'CUST-1001', 'SMS',    TRUE,  '2025-02-10 08:00:00+00'),
    (3, 'CUST-1002', 'EMAIL',  TRUE,  '2025-03-04 11:20:00+00'),
    (4, 'CUST-1002', 'SMS',    FALSE, '2025-04-18 19:45:00+00'),
    (5, 'CUST-1003', 'EMAIL',  FALSE, '2025-05-01 09:05:00+00'),
    (6, 'CUST-1003', 'IN_APP', TRUE,  '2025-05-01 09:05:00+00')
ON CONFLICT (id) DO NOTHING;

INSERT INTO notifications (id, notification_ref, recipient_ref, destination, channel, template_code, payload, status, attempts, last_error, created_at, updated_at) VALUES
    (1, 'NTF-20250510-000001', 'CUST-1001', 'asha.menon@example.com',   'EMAIL', 'welcome',         '{"name":"Asha","brand":"Northwind"}',                       'SENT',   1, NULL,                             '2025-05-10 07:02:00+00', '2025-05-10 07:02:03+00'),
    (2, 'NTF-20250512-000002', 'CUST-1002', 'ravi.kulkarni@example.com','EMAIL', 'order.shipped',   '{"name":"Ravi","orderRef":"ORD-5001"}',                     'SENT',   1, NULL,                             '2025-05-12 14:31:00+00', '2025-05-12 14:31:02+00'),
    (3, 'NTF-20250514-000003', 'CUST-1001', '+91-9845011223',           'SMS',   'otp',             '{"code":"448291","brand":"Northwind"}',                     'SENT',   1, NULL,                             '2025-05-14 09:12:00+00', '2025-05-14 09:12:01+00'),
    (4, 'NTF-20250515-000004', 'CUST-1003', 'meera.iyer@example.com',   'EMAIL', 'payment.receipt', '{"name":"Meera","orderRef":"ORD-5099","amount":"1299.00"}', 'FAILED', 3, 'Recipient rejected the message', '2025-05-15 16:40:00+00', '2025-05-15 16:41:10+00')
ON CONFLICT (id) DO NOTHING;

INSERT INTO delivery_log (id, notification_id, attempt_no, outcome, detail, created_at) VALUES
    (1, 1, 1, 'DELIVERED', 'Accepted by the email provider', '2025-05-10 07:02:03+00'),
    (2, 2, 1, 'DELIVERED', 'Accepted by the email provider', '2025-05-12 14:31:02+00'),
    (3, 3, 1, 'DELIVERED', 'Accepted by the SMS provider',   '2025-05-14 09:12:01+00'),
    (4, 4, 1, 'REJECTED',  'Recipient rejected the message', '2025-05-15 16:40:20+00'),
    (5, 4, 2, 'REJECTED',  'Recipient rejected the message', '2025-05-15 16:40:50+00'),
    (6, 4, 3, 'REJECTED',  'Recipient rejected the message', '2025-05-15 16:41:10+00')
ON CONFLICT (id) DO NOTHING;

-- Product teams use "Password123!", the messaging operations account uses "Admin123!".
INSERT INTO users (id, username, password_hash, role_name) VALUES
    (1, 'product@pulsesend.test', '$2a$10$Qoeh9Vl6k.enhigi0fhELeDmesn7WtAOX1O9MlpuAqq55890K5b7W', 'ROLE_PRODUCT'),
    (2, 'msgops@pulsesend.test',  '$2a$10$BfSO2Iny00pHa97kZJKGfOmWQPg0EJiFX/Kp4ocpiShu1DVmmhsCa', 'ROLE_MSGOPS')
ON CONFLICT (id) DO NOTHING;

SELECT setval(pg_get_serial_sequence('notification_templates', 'id'),   COALESCE((SELECT MAX(id) FROM notification_templates), 1));
SELECT setval(pg_get_serial_sequence('notification_preferences', 'id'), COALESCE((SELECT MAX(id) FROM notification_preferences), 1));
SELECT setval(pg_get_serial_sequence('notifications', 'id'),            COALESCE((SELECT MAX(id) FROM notifications), 1));
SELECT setval(pg_get_serial_sequence('delivery_log', 'id'),             COALESCE((SELECT MAX(id) FROM delivery_log), 1));
SELECT setval(pg_get_serial_sequence('users', 'id'),                    COALESCE((SELECT MAX(id) FROM users), 1));
