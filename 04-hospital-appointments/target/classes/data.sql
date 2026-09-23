-- Reference and demo data for Harbourview Clinic.
-- Idempotent: re-running on an existing database changes nothing.

INSERT IGNORE INTO patients (id, mrn, full_name, email, phone, date_of_birth, created_at) VALUES
    (1, 'MRN-100241', 'Rhea Sundaram',  'rhea.sundaram@example.com',  '+91-9876500011', '1991-03-14', '2023-02-11 10:00:00.000000'),
    (2, 'MRN-100258', 'Joseph Mwangi',  'joseph.mwangi@example.com',  '+254-712445566', '1978-11-02', '2023-04-19 14:20:00.000000'),
    (3, 'MRN-100263', 'Lucia Moretti',  'lucia.moretti@example.com',  '+39-3401122334', '1986-07-25', '2023-06-30 09:05:00.000000'),
    (4, 'MRN-100277', 'Tobias Fenner',  'tobias.fenner@example.com',  '+49-15112233445','2001-01-09', '2024-01-08 16:45:00.000000'),
    (5, 'MRN-100289', 'Amara Nwosu',    'amara.nwosu@example.com',    '+234-8033445566','1995-09-18', '2024-03-22 11:30:00.000000');

INSERT IGNORE INTO doctors (id, code, full_name, specialty, email, consultation_minutes, active) VALUES
    (1, 'DOC-0101', 'Dr Anita Deshpande', 'General Medicine', 'anita.deshpande@harbourview.test', 30, 1),
    (2, 'DOC-0102', 'Dr Mark Ellery',     'Cardiology',       'mark.ellery@harbourview.test',     40, 1),
    (3, 'DOC-0103', 'Dr Sofia Keller',    'Dermatology',      'sofia.keller@harbourview.test',    20, 1),
    (4, 'DOC-0104', 'Dr Ravi Balakrishna','Orthopaedics',     'ravi.balakrishna@harbourview.test',30, 0);

-- Dr Deshpande: weekday mornings. Dr Ellery: Mon/Wed/Fri afternoons. Dr Keller: Tue/Thu full day.
INSERT IGNORE INTO doctor_schedules (id, doctor_id, day_of_week, start_time, end_time, slot_minutes, active) VALUES
    (1,  1, 'MONDAY',    '09:00:00', '13:00:00', 30, 1),
    (2,  1, 'TUESDAY',   '09:00:00', '13:00:00', 30, 1),
    (3,  1, 'WEDNESDAY', '09:00:00', '13:00:00', 30, 1),
    (4,  1, 'THURSDAY',  '09:00:00', '13:00:00', 30, 1),
    (5,  1, 'FRIDAY',    '09:00:00', '13:00:00', 30, 1),
    (6,  1, 'SATURDAY',  '09:00:00', '12:00:00', 30, 1),
    (7,  1, 'SUNDAY',    '10:00:00', '12:00:00', 30, 1),
    (8,  2, 'MONDAY',    '14:00:00', '18:00:00', 40, 1),
    (9,  2, 'WEDNESDAY', '14:00:00', '18:00:00', 40, 1),
    (10, 2, 'FRIDAY',    '14:00:00', '18:00:00', 40, 1),
    (11, 3, 'TUESDAY',   '10:00:00', '16:00:00', 20, 1),
    (12, 3, 'THURSDAY',  '10:00:00', '16:00:00', 20, 1),
    (13, 3, 'SATURDAY',  '10:00:00', '14:00:00', 20, 1),
    (14, 3, 'SUNDAY',    '10:00:00', '14:00:00', 20, 1),
    (15, 4, 'MONDAY',    '09:00:00', '17:00:00', 30, 0);

-- A short history so reporting screens are not empty. Dates are in the clinic timezone.
INSERT IGNORE INTO appointments (id, reference, doctor_id, patient_id, start_time, end_time, status, reason, cancellation_reason, created_at, updated_at) VALUES
    (1, 'APT-20250210-4471', 1, 1, '2025-02-10 09:30:00', '2025-02-10 10:00:00', 'COMPLETED', 'Annual check-up',        NULL,                      '2025-02-03 12:01:00.000000', '2025-02-10 10:05:00.000000'),
    (2, 'APT-20250212-4478', 2, 2, '2025-02-12 14:40:00', '2025-02-12 15:20:00', 'COMPLETED', 'Chest pain follow-up',   NULL,                      '2025-02-05 08:30:00.000000', '2025-02-12 15:25:00.000000'),
    (3, 'APT-20250218-4502', 3, 3, '2025-02-18 10:20:00', '2025-02-18 10:40:00', 'CANCELLED', 'Rash review',            'Patient rescheduled',     '2025-02-11 17:45:00.000000', '2025-02-17 09:12:00.000000'),
    (4, 'APT-20250225-4530', 1, 4, '2025-02-25 11:00:00', '2025-02-25 11:30:00', 'NO_SHOW',   'Persistent cough',       NULL,                      '2025-02-19 13:20:00.000000', '2025-02-25 11:25:00.000000'),
    (5, 'APT-20250304-4566', 3, 5, '2025-03-04 11:40:00', '2025-03-04 12:00:00', 'COMPLETED', 'Eczema treatment plan',  NULL,                      '2025-02-26 10:10:00.000000', '2025-03-04 12:05:00.000000');

-- Sign-in accounts. Patients and doctors use "Password123!", the scheduling desk uses "Admin123!".
INSERT IGNORE INTO users (id, username, password_hash, role_name, patient_id, doctor_id, enabled) VALUES
    (1, 'rhea.sundaram@example.com',          '$2a$10$Qoeh9Vl6k.enhigi0fhELeDmesn7WtAOX1O9MlpuAqq55890K5b7W', 'ROLE_PATIENT', 1,    NULL, 1),
    (2, 'joseph.mwangi@example.com',          '$2a$10$Qoeh9Vl6k.enhigi0fhELeDmesn7WtAOX1O9MlpuAqq55890K5b7W', 'ROLE_PATIENT', 2,    NULL, 1),
    (3, 'lucia.moretti@example.com',          '$2a$10$Qoeh9Vl6k.enhigi0fhELeDmesn7WtAOX1O9MlpuAqq55890K5b7W', 'ROLE_PATIENT', 3,    NULL, 1),
    (4, 'anita.deshpande@harbourview.test',   '$2a$10$Qoeh9Vl6k.enhigi0fhELeDmesn7WtAOX1O9MlpuAqq55890K5b7W', 'ROLE_DOCTOR',  NULL, 1,    1),
    (5, 'mark.ellery@harbourview.test',       '$2a$10$Qoeh9Vl6k.enhigi0fhELeDmesn7WtAOX1O9MlpuAqq55890K5b7W', 'ROLE_DOCTOR',  NULL, 2,    1),
    (6, 'scheduling.desk@harbourview.test',   '$2a$10$BfSO2Iny00pHa97kZJKGfOmWQPg0EJiFX/Kp4ocpiShu1DVmmhsCa', 'ROLE_OPS',     NULL, NULL, 1);
