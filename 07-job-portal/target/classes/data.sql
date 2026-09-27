-- Reference and demo data for Hirestack.
-- Idempotent: re-running on an existing database changes nothing.

INSERT IGNORE INTO companies (id, name, slug, industry, city, verified) VALUES
    (1, 'Northwind Retail Technologies', 'northwind-retail', 'Retail Technology', 'Bengaluru', 1),
    (2, 'Cobalt Analytics',              'cobalt-analytics', 'Data and Analytics', 'Pune',      1),
    (3, 'Harbour Fintech',               'harbour-fintech',  'Financial Services', 'Mumbai',    0);

INSERT IGNORE INTO candidates (id, full_name, email, phone, headline, location, years_experience, skills, expected_salary, created_at) VALUES
    (1, 'Sana Qureshi',   'sana.qureshi@example.com',   '+91-9812001122', 'Backend engineer, JVM and distributed systems', 'Bengaluru', 6,  'Java,Spring Boot,Kafka,PostgreSQL', 3200000.00, '2024-11-02 09:14:00.000000'),
    (2, 'Vikram Solanki', 'vikram.solanki@example.com', '+91-9812003344', 'Full stack developer',                          'Pune',      3,  'Java,Spring,React,MySQL',           1800000.00, '2025-01-18 15:40:00.000000'),
    (3, 'Nadia Farouk',   'nadia.farouk@example.com',   '+91-9812005566', 'Data engineer',                                 'Mumbai',    8,  'Python,Spark,Airflow,SQL',          4100000.00, '2025-02-09 11:25:00.000000'),
    (4, 'Ethan Brandt',   'ethan.brandt@example.com',   '+49-15122334455','Graduate software engineer',                    'Remote',    1,  'Java,JavaScript,Git',                900000.00, '2025-03-27 08:02:00.000000');

-- Candidates and recruiters use "Password123!", the platform admin uses "Admin123!".
INSERT IGNORE INTO users (id, email, password_hash, role_name, company_id, candidate_id, enabled) VALUES
    (1, 'sana.qureshi@example.com',    '$2a$10$Qoeh9Vl6k.enhigi0fhELeDmesn7WtAOX1O9MlpuAqq55890K5b7W', 'ROLE_CANDIDATE', NULL, 1,    1),
    (2, 'vikram.solanki@example.com',  '$2a$10$Qoeh9Vl6k.enhigi0fhELeDmesn7WtAOX1O9MlpuAqq55890K5b7W', 'ROLE_CANDIDATE', NULL, 2,    1),
    (3, 'nadia.farouk@example.com',    '$2a$10$Qoeh9Vl6k.enhigi0fhELeDmesn7WtAOX1O9MlpuAqq55890K5b7W', 'ROLE_CANDIDATE', NULL, 3,    1),
    (4, 'ethan.brandt@example.com',    '$2a$10$Qoeh9Vl6k.enhigi0fhELeDmesn7WtAOX1O9MlpuAqq55890K5b7W', 'ROLE_CANDIDATE', NULL, 4,    1),
    (10, 'priya.nambiar@northwind.test','$2a$10$Qoeh9Vl6k.enhigi0fhELeDmesn7WtAOX1O9MlpuAqq55890K5b7W', 'ROLE_RECRUITER', 1,   NULL, 1),
    (11, 'rahul.dev@northwind.test',    '$2a$10$Qoeh9Vl6k.enhigi0fhELeDmesn7WtAOX1O9MlpuAqq55890K5b7W', 'ROLE_RECRUITER', 1,   NULL, 1),
    (12, 'fatima.ali@cobalt.test',      '$2a$10$Qoeh9Vl6k.enhigi0fhELeDmesn7WtAOX1O9MlpuAqq55890K5b7W', 'ROLE_RECRUITER', 2,   NULL, 1),
    (13, 'james.okoro@harbour.test',    '$2a$10$Qoeh9Vl6k.enhigi0fhELeDmesn7WtAOX1O9MlpuAqq55890K5b7W', 'ROLE_RECRUITER', 3,   NULL, 1),
    (99, 'platform.admin@hirestack.test','$2a$10$BfSO2Iny00pHa97kZJKGfOmWQPg0EJiFX/Kp4ocpiShu1DVmmhsCa','ROLE_ADMIN',    NULL, NULL, 1);

INSERT IGNORE INTO job_postings (id, reference, company_id, posted_by_id, title, description, location, employment_type, remote, min_experience, min_salary, max_salary, status, created_at, closes_on) VALUES
    (1, 'JOB-2025-0101', 1, 10, 'Senior Backend Engineer',      'Own and scale the order platform. Java, Spring Boot, Kafka.',        'Bengaluru', 'FULL_TIME', 0, 5, 2800000.00, 4200000.00, 'PUBLISHED', '2025-04-02 10:00:00.000000', '2025-12-31'),
    (2, 'JOB-2025-0102', 1, 10, 'Platform Engineer (Remote)',   'Build internal tooling for a distributed team. Fully remote.',       'Remote',    'FULL_TIME', 1, 3, 2200000.00, 3400000.00, 'PUBLISHED', '2025-04-05 11:30:00.000000', '2025-12-31'),
    (3, 'JOB-2025-0103', 1, 11, 'Engineering Manager',          'Lead two backend squads in the fulfilment area.',                    'Bengaluru', 'FULL_TIME', 0, 8, 4500000.00, 6000000.00, 'DRAFT',     '2025-05-20 09:15:00.000000', NULL),
    (4, 'JOB-2025-0104', 2, 12, 'Data Engineer',                'Batch and streaming pipelines on Spark and Airflow.',                'Pune',      'FULL_TIME', 0, 4, 2400000.00, 3600000.00, 'PUBLISHED', '2025-04-11 14:20:00.000000', '2025-11-30'),
    (5, 'JOB-2025-0105', 2, 12, 'Analytics Intern',             'Six month internship working with the reporting team.',              'Pune',      'INTERNSHIP',0, 0,  600000.00,  900000.00, 'PUBLISHED', '2025-05-02 16:45:00.000000', '2025-10-31'),
    (6, 'JOB-2025-0106', 2, 12, 'Staff Data Scientist',         'Not yet approved by the hiring committee. Internal band L7.',        'Remote',    'FULL_TIME', 1, 9, 5200000.00, 7000000.00, 'DRAFT',     '2025-06-08 12:00:00.000000', NULL),
    (7, 'JOB-2025-0107', 3, 13, 'Payments Backend Engineer',    'Ledger and reconciliation services for a growing payments product.', 'Mumbai',    'FULL_TIME', 0, 4, 2600000.00, 3900000.00, 'PUBLISHED', '2025-04-18 08:50:00.000000', '2025-12-15'),
    (8, 'JOB-2025-0108', 3, 13, 'Site Reliability Engineer',    'On-call rotation, Kubernetes, observability.',                       'Remote',    'FULL_TIME', 1, 5, 3000000.00, 4400000.00, 'PUBLISHED', '2025-05-14 10:10:00.000000', '2025-12-15'),
    (9, 'JOB-2025-0099', 1, 10, 'QA Automation Engineer',       'Closed after the role was filled internally.',                       'Bengaluru', 'FULL_TIME', 0, 3, 1600000.00, 2400000.00, 'CLOSED',    '2025-01-09 09:00:00.000000', '2025-03-31');

INSERT IGNORE INTO applications (id, job_posting_id, candidate_id, status, cover_letter, applied_at, updated_at) VALUES
    (1, 1, 1, 'SHORTLISTED', 'Six years on JVM order systems, keen to talk.',        '2025-04-08 10:22:00.000000', '2025-04-15 09:00:00.000000'),
    (2, 1, 2, 'REJECTED',    'Interested in stepping up to a senior backend role.',  '2025-04-09 19:05:00.000000', '2025-04-14 11:30:00.000000'),
    (3, 4, 3, 'INTERVIEW',   'Eight years building Spark pipelines.',                '2025-04-14 08:41:00.000000', '2025-04-22 15:20:00.000000'),
    (4, 7, 1, 'SUBMITTED',   'Ledger work is exactly where I want to be.',           '2025-04-25 12:18:00.000000', '2025-04-25 12:18:00.000000'),
    (5, 5, 4, 'SUBMITTED',   'Final year student, available from July.',             '2025-05-06 17:55:00.000000', '2025-05-06 17:55:00.000000');
