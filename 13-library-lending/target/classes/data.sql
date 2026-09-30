-- Reference and demo data for the Athenaeum circulation service.
-- Idempotent: INSERT IGNORE everywhere. Loan dates are relative to boot time so that the
-- overdue examples stay overdue however long the container has been sitting idle.
--
-- Seeded passwords: the librarian account uses "Admin123!", member accounts "Password123!".

INSERT IGNORE INTO books (id, isbn, title, author, publisher, published_year, shelf_mark) VALUES
    (1, '9780132350884', 'Clean Code',                                  'Robert C. Martin', 'Prentice Hall',    2008, 'QA76.76.C55'),
    (2, '9780321127426', 'Patterns of Enterprise Application Architecture', 'Martin Fowler', 'Addison-Wesley',  2002, 'QA76.76.P37'),
    (3, '9780596007126', 'MySQL Cookbook',                               'Paul DuBois',     'O''Reilly Media',  2006, 'QA76.73.M99'),
    (4, '9781617297571', 'Spring in Action',                             'Craig Walls',     'Manning',          2022, 'QA76.73.S67'),
    (5, '9780262033848', 'Introduction to Algorithms',                   'Thomas H. Cormen','MIT Press',        2009, 'QA76.6.C66'),
    (6, '9780140399974', 'The Odyssey',                                  'Homer',           'Penguin Classics', 2003, 'PA4025.A5'),
    (7, '9780552997041', 'A Short History of Nearly Everything',         'Bill Bryson',     'Black Swan',       2004, 'Q162.B79'),
    (8, '9780571334650', 'Normal People',                                'Sally Rooney',    'Faber and Faber',  2018, 'PR6118.O59');

INSERT IGNORE INTO book_copies (id, book_id, barcode, branch, status, acquired_on, version) VALUES
    (1,  1, 'ATH-000101', 'Central',       'AVAILABLE', '2021-03-11', 0),
    (2,  1, 'ATH-000102', 'Central',       'AVAILABLE', '2021-03-11', 0),
    (3,  1, 'ATH-000103', 'Central',       'AVAILABLE', '2022-07-04', 0),
    (4,  1, 'ATH-000104', 'Riverside',     'AVAILABLE', '2022-07-04', 0),
    (5,  1, 'ATH-000105', 'Riverside',     'AVAILABLE', '2023-01-19', 0),
    (6,  2, 'ATH-000201', 'Central',       'ON_LOAN',   '2019-09-02', 0),
    (7,  2, 'ATH-000202', 'Central',       'AVAILABLE', '2019-09-02', 0),
    (8,  3, 'ATH-000301', 'Central',       'AVAILABLE', '2018-05-30', 0),
    (9,  3, 'ATH-000302', 'Riverside',     'LOST',      '2018-05-30', 0),
    (10, 3, 'ATH-000303', 'Central',       'REPAIR',    '2020-11-17', 0),
    (11, 4, 'ATH-000401', 'Central',       'ON_LOAN',   '2022-10-08', 0),
    (12, 4, 'ATH-000402', 'Central',       'ON_LOAN',   '2022-10-08', 0),
    (13, 4, 'ATH-000403', 'Riverside',     'AVAILABLE', '2023-02-21', 0),
    (14, 4, 'ATH-000404', 'Hillside',      'AVAILABLE', '2023-02-21', 0),
    (15, 5, 'ATH-000501', 'Central',       'ON_LOAN',   '2017-06-14', 0),
    (16, 5, 'ATH-000502', 'Riverside',     'ON_LOAN',   '2017-06-14', 0),
    (17, 6, 'ATH-000601', 'Central',       'AVAILABLE', '2016-04-25', 0),
    (18, 6, 'ATH-000602', 'Hillside',      'AVAILABLE', '2016-04-25', 0),
    (19, 7, 'ATH-000701', 'Central',       'ON_LOAN',   '2020-08-09', 0),
    (20, 7, 'ATH-000702', 'Riverside',     'AVAILABLE', '2020-08-09', 0),
    (21, 8, 'ATH-000801', 'Central',       'AVAILABLE', '2023-05-16', 0),
    (22, 8, 'ATH-000802', 'Hillside',      'AVAILABLE', '2023-05-16', 0);

INSERT IGNORE INTO members (id, membership_number, full_name, email, tier, status, active_loan_count, joined_on) VALUES
    (1, 'LIB-0001', 'Anita Desai',       'anita.desai@athenaeum.test',       'STANDARD',  'ACTIVE',    1, '2019-02-14'),
    (2, 'LIB-0002', 'Rahul Verma',       'rahul.verma@athenaeum.test',       'PREMIUM',   'ACTIVE',    2, '2020-06-30'),
    (3, 'LIB-0003', 'Fatima Sheikh',     'fatima.sheikh@athenaeum.test',     'STANDARD',  'ACTIVE',    2, '2021-11-05'),
    (4, 'LIB-0004', 'Joseph Kuriakose',  'joseph.kuriakose@athenaeum.test',  'STANDARD',  'ACTIVE',    0, '2018-08-21'),
    (5, 'LIB-0005', 'Lakshmi Pillai',    'lakshmi.pillai@athenaeum.test',    'STAFF',     'ACTIVE',    1, '2017-01-09'),
    (6, 'LIB-0006', 'Daniel Fernandes',  'daniel.fernandes@athenaeum.test',  'STANDARD',  'ACTIVE',    0, '2022-04-18'),
    (7, 'LIB-0007', 'Neha Gupta',        'neha.gupta@athenaeum.test',        'PREMIUM',   'ACTIVE',    0, '2023-09-12'),
    (8, 'LIB-0008', 'Vikram Singh',      'vikram.singh@athenaeum.test',      'STANDARD',  'SUSPENDED', 0, '2016-12-01');

INSERT IGNORE INTO loans (id, copy_id, member_id, borrowed_at, due_at, returned_at, status, renewal_count) VALUES
    (1, 6,  1, DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(NOW(), INTERVAL 6 DAY),  NULL, 'ACTIVE', 0),
    (2, 11, 2, DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_ADD(NOW(), INTERVAL 4 DAY),  NULL, 'ACTIVE', 0),
    (3, 12, 2, DATE_SUB(NOW(), INTERVAL 25 DAY), DATE_SUB(NOW(), INTERVAL 11 DAY), NULL, 'ACTIVE', 1),
    (4, 15, 3, DATE_SUB(NOW(), INTERVAL 5 DAY),  DATE_ADD(NOW(), INTERVAL 9 DAY),  NULL, 'ACTIVE', 0),
    (5, 16, 5, DATE_SUB(NOW(), INTERVAL 30 DAY), DATE_SUB(NOW(), INTERVAL 16 DAY), NULL, 'ACTIVE', 0),
    (6, 19, 3, DATE_SUB(NOW(), INTERVAL 2 DAY),  DATE_ADD(NOW(), INTERVAL 12 DAY), NULL, 'ACTIVE', 0),
    (7, 17, 4, DATE_SUB(NOW(), INTERVAL 60 DAY), DATE_SUB(NOW(), INTERVAL 46 DAY), DATE_SUB(NOW(), INTERVAL 50 DAY), 'RETURNED', 0),
    (8, 20, 7, DATE_SUB(NOW(), INTERVAL 45 DAY), DATE_SUB(NOW(), INTERVAL 31 DAY), DATE_SUB(NOW(), INTERVAL 28 DAY), 'RETURNED', 0);

INSERT IGNORE INTO reservations (id, book_id, member_id, placed_at, status, queue_position, held_copy_id, ready_until) VALUES
    (1, 5, 6, DATE_SUB(NOW(), INTERVAL 3 DAY), 'WAITING', 1, NULL, NULL),
    (2, 5, 7, DATE_SUB(NOW(), INTERVAL 1 DAY), 'WAITING', 2, NULL, NULL);

INSERT IGNORE INTO fines (id, loan_id, member_id, amount, days_overdue, assessed_at, paid_at, status) VALUES
    (1, 8, 7, 15.00, 3, DATE_SUB(NOW(), INTERVAL 28 DAY), NULL, 'OUTSTANDING');

INSERT IGNORE INTO app_users (id, username, password_hash, role_name, member_id) VALUES
    (1, 'librarian@athenaeum.test',       '$2a$10$BfSO2Iny00pHa97kZJKGfOmWQPg0EJiFX/Kp4ocpiShu1DVmmhsCa', 'ROLE_LIBRARIAN', NULL),
    (2, 'anita.desai@athenaeum.test',     '$2a$10$Qoeh9Vl6k.enhigi0fhELeDmesn7WtAOX1O9MlpuAqq55890K5b7W', 'ROLE_MEMBER',    1),
    (3, 'rahul.verma@athenaeum.test',     '$2a$10$Qoeh9Vl6k.enhigi0fhELeDmesn7WtAOX1O9MlpuAqq55890K5b7W', 'ROLE_MEMBER',    2),
    (4, 'daniel.fernandes@athenaeum.test','$2a$10$Qoeh9Vl6k.enhigi0fhELeDmesn7WtAOX1O9MlpuAqq55890K5b7W', 'ROLE_MEMBER',    6);
