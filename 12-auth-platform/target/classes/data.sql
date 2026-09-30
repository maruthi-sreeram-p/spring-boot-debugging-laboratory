-- Reference and demo data for the Aegis identity platform.
-- Idempotent: ON CONFLICT DO NOTHING plus a sequence re-sync at the end.
--
-- Seeded passwords: the administrator uses "Admin123!", everybody else "Password123!".

INSERT INTO roles (id, name, description) VALUES
    (1, 'ROLE_ADMIN',   'Platform administrator'),
    (2, 'ROLE_EDITOR',  'Creates and edits documents'),
    (3, 'ROLE_VIEWER',  'Reads documents'),
    (4, 'ROLE_AUDITOR', 'Reads documents and the audit trail')
ON CONFLICT (id) DO NOTHING;

INSERT INTO permissions (id, name, description) VALUES
    (1, 'document:read',   'Read a document'),
    (2, 'document:write',  'Create or edit a document'),
    (3, 'document:delete', 'Delete a document'),
    (4, 'audit:read',      'Read the audit trail'),
    (5, 'account:manage',  'Lock, unlock and inspect accounts')
ON CONFLICT (id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id) VALUES
    (1, 1), (1, 2), (1, 3), (1, 4), (1, 5),
    (2, 1), (2, 2),
    (3, 1),
    (4, 1), (4, 4)
ON CONFLICT DO NOTHING;

INSERT INTO accounts (id, email, password_hash, display_name, status, failed_attempts, created_at) VALUES
    (1, 'admin@aegis.test',   '$2a$10$BfSO2Iny00pHa97kZJKGfOmWQPg0EJiFX/Kp4ocpiShu1DVmmhsCa', 'Ravi Menon',     'ACTIVE', 0, '2024-07-01 08:00:00+00'),
    (2, 'editor@aegis.test',  '$2a$10$Qoeh9Vl6k.enhigi0fhELeDmesn7WtAOX1O9MlpuAqq55890K5b7W', 'Ananya Sharma',  'ACTIVE', 0, '2024-07-04 09:30:00+00'),
    (3, 'viewer@aegis.test',  '$2a$10$Qoeh9Vl6k.enhigi0fhELeDmesn7WtAOX1O9MlpuAqq55890K5b7W', 'Karthik Rao',    'ACTIVE', 0, '2024-08-12 11:15:00+00'),
    (4, 'auditor@aegis.test', '$2a$10$Qoeh9Vl6k.enhigi0fhELeDmesn7WtAOX1O9MlpuAqq55890K5b7W', 'Meera Iyer',     'ACTIVE', 0, '2024-09-02 14:45:00+00'),
    (5, 'Dev.Ops@aegis.test', '$2a$10$Qoeh9Vl6k.enhigi0fhELeDmesn7WtAOX1O9MlpuAqq55890K5b7W', 'Platform on-call','ACTIVE', 0, '2024-09-20 10:05:00+00'),
    (6, 'former@aegis.test',  '$2a$10$Qoeh9Vl6k.enhigi0fhELeDmesn7WtAOX1O9MlpuAqq55890K5b7W', 'Sanjay Pillai',  'LOCKED', 3, '2024-07-18 16:20:00+00')
ON CONFLICT (id) DO NOTHING;

INSERT INTO account_roles (account_id, role_id) VALUES
    (1, 1),
    (2, 2),
    (3, 3),
    (4, 4),
    (5, 2),
    (6, 3)
ON CONFLICT DO NOTHING;

INSERT INTO documents (id, reference, title, body, owner_id, sensitivity, created_at, updated_at) VALUES
    (1, 'DOC-2024-0001', 'Quarterly platform roadmap',   'Delivery milestones for the next two quarters.',        2, 'INTERNAL',     '2024-09-10 10:00:00+00', '2024-09-10 10:00:00+00'),
    (2, 'DOC-2024-0002', 'Incident review: cache outage','Timeline and follow-up actions from the March outage.', 2, 'INTERNAL',     '2024-10-02 15:30:00+00', '2024-10-02 15:30:00+00'),
    (3, 'DOC-2024-0003', 'Vendor contract summary',      'Commercial terms for the managed database contract.',   1, 'CONFIDENTIAL', '2024-10-18 09:10:00+00', '2024-10-18 09:10:00+00'),
    (4, 'DOC-2025-0004', 'Onboarding checklist',         'What a new engineer needs in their first week.',        5, 'INTERNAL',     '2025-01-14 12:40:00+00', '2025-01-14 12:40:00+00'),
    (5, 'DOC-2025-0005', 'Access review, February',      'Accounts and roles reviewed by the security group.',    4, 'CONFIDENTIAL', '2025-02-28 17:05:00+00', '2025-02-28 17:05:00+00')
ON CONFLICT (id) DO NOTHING;

INSERT INTO audit_events (id, account_id, event_type, detail, created_at) VALUES
    (1, 6,    'ACCOUNT_LOCKED', 'Locked after three failed sign-in attempts',   '2024-11-05 08:12:00+00'),
    (2, 1,    'ROLE_GRANTED',   'ROLE_EDITOR granted to Dev.Ops@aegis.test',    '2024-09-20 10:06:00+00'),
    (3, NULL, 'LOGIN_FAILED',   'Unknown account: contractor@partner.test',     '2025-01-09 21:44:00+00')
ON CONFLICT (id) DO NOTHING;

SELECT setval(pg_get_serial_sequence('roles', 'id'),          COALESCE((SELECT MAX(id) FROM roles), 1));
SELECT setval(pg_get_serial_sequence('permissions', 'id'),    COALESCE((SELECT MAX(id) FROM permissions), 1));
SELECT setval(pg_get_serial_sequence('accounts', 'id'),       COALESCE((SELECT MAX(id) FROM accounts), 1));
SELECT setval(pg_get_serial_sequence('documents', 'id'),      COALESCE((SELECT MAX(id) FROM documents), 1));
SELECT setval(pg_get_serial_sequence('audit_events', 'id'),   COALESCE((SELECT MAX(id) FROM audit_events), 1));
SELECT setval(pg_get_serial_sequence('refresh_tokens', 'id'), COALESCE((SELECT MAX(id) FROM refresh_tokens), 1));
