-- Reference and demo data for the Northgate people directory.
-- Idempotent: re-running on an existing database changes nothing.

INSERT IGNORE INTO roles (id, name) VALUES
    (1, 'ROLE_EMPLOYEE'),
    (2, 'ROLE_MANAGER'),
    (3, 'ROLE_HR_ADMIN');

INSERT IGNORE INTO departments (id, code, name, location) VALUES
    (1, 'ENG', 'Engineering', 'Bengaluru'),
    (2, 'SLS', 'Sales', 'Mumbai'),
    (3, 'FIN', 'Finance', 'Pune'),
    (4, 'PPL', 'People Operations', 'Bengaluru'),
    (5, 'SUP', 'Customer Support', 'Hyderabad');

INSERT IGNORE INTO employees (id, employee_code, first_name, last_name, email, phone, job_title,
                             department_id, manager_id, employment_status, hire_date, salary, created_at) VALUES
    (1, 'NG-1001', 'Ishita', 'Sheikh', 'ishita.sheikh@northgate.test', '+91-9778450602', 'Managing Director', 4, NULL, 'ACTIVE', '2019-09-11', 9800000.00, '2024-01-05 09:00:00.000000'),
    (2, 'NG-1002', 'Arjun', 'Reddy', 'arjun.reddy@northgate.test', '+91-9181473754', 'Director of Engineering', 1, 1, 'ACTIVE', '2018-11-04', 6250013.00, '2024-01-05 09:00:00.000000'),
    (3, 'NG-1003', 'Divya', 'Shukla', 'divya.shukla@northgate.test', '+91-9994808262', 'Director of Sales', 2, 1, 'ACTIVE', '2022-09-05', 6333240.00, '2024-01-05 09:00:00.000000'),
    (4, 'NG-1004', 'Manav', 'Mukherjee', 'manav.mukherjee@northgate.test', '+91-9931440377', 'Director of Finance', 3, 1, 'ACTIVE', '2018-09-01', 5183470.00, '2024-01-05 09:00:00.000000'),
    (5, 'NG-1005', 'Shreya', 'Vaidya', 'shreya.vaidya@northgate.test', '+91-9785153951', 'Director of People Operations', 4, 1, 'ACTIVE', '2016-03-01', 5416275.00, '2024-01-05 09:00:00.000000'),
    (6, 'NG-1006', 'Imran', 'Dsouza', 'imran.dsouza@northgate.test', '+91-9770773193', 'Director of Customer Support', 5, 1, 'ACTIVE', '2019-01-25', 5164367.00, '2024-01-05 09:00:00.000000'),
    (7, 'NG-1007', 'Leela', 'Rao', 'leela.rao@northgate.test', '+91-9595767379', 'QA Engineer', 1, 2, 'ACTIVE', '2019-06-04', 1075000.00, '2024-01-05 09:00:00.000000'),
    (8, 'NG-1008', 'Thomas', 'Kaur', 'thomas.kaur@northgate.test', '+91-9689052948', 'Regional Sales Manager', 2, 3, 'ACTIVE', '2017-12-10', 3025000.00, '2024-01-05 09:00:00.000000'),
    (9, 'NG-1009', 'Ira', 'Bhat', 'ira.bhat@northgate.test', '+91-9767601062', 'Accounts Payable Specialist', 3, 4, 'ACTIVE', '2022-10-07', 2075000.00, '2024-01-05 09:00:00.000000'),
    (10, 'NG-1010', 'Suresh', 'Kohli', 'suresh.kohli@northgate.test', '+91-9915794193', 'HR Business Partner', 4, 5, 'ACTIVE', '2017-10-25', 675000.00, '2024-01-05 09:00:00.000000'),
    (11, 'NG-1011', 'Tanvi', 'Gill', 'tanvi.gill@northgate.test', '+91-9471669777', 'Senior Support Engineer', 5, 6, 'ACTIVE', '2021-04-10', 1675000.00, '2024-01-05 09:00:00.000000'),
    (12, 'NG-1012', 'Aarav', 'Nair', 'aarav.nair@northgate.test', '+91-9344592605', 'Software Engineer', 1, 2, 'ACTIVE', '2020-03-20', 4000000.00, '2024-01-05 09:00:00.000000'),
    (13, 'NG-1013', 'Vikram', 'Sengupta', 'vikram.sengupta@northgate.test', '+91-9320861888', 'Regional Sales Manager', 2, 3, 'ACTIVE', '2021-11-26', 2975000.00, '2024-01-05 09:00:00.000000'),
    (14, 'NG-1014', 'Tara', 'Agarwal', 'tara.agarwal@northgate.test', '+91-9440677573', 'Accounts Payable Specialist', 3, 4, 'ACTIVE', '2017-01-14', 2050000.00, '2024-01-05 09:00:00.000000'),
    (15, 'NG-1015', 'Siddharth', 'Grover', 'siddharth.grover@northgate.test', '+91-9380596878', 'HR Business Partner', 4, 5, 'ACTIVE', '2024-02-24', 2350000.00, '2024-01-05 09:00:00.000000'),
    (16, 'NG-1016', 'Riya', 'Qureshi', 'riya.qureshi@northgate.test', '+91-9391812060', 'Support Engineer', 5, 6, 'ACTIVE', '2019-06-09', 475000.00, '2024-01-05 09:00:00.000000'),
    (17, 'NG-1017', 'Yash', 'Bose', 'yash.bose@northgate.test', '+91-9485502087', 'QA Engineer', 1, 2, 'ACTIVE', '2023-07-18', 2675000.00, '2024-01-05 09:00:00.000000'),
    (18, 'NG-1018', 'Farah', 'Varghese', 'farah.varghese@northgate.test', '+91-9873184988', 'Regional Sales Manager', 2, 3, 'ACTIVE', '2016-10-07', 1325000.00, '2024-01-05 09:00:00.000000'),
    (19, 'NG-1019', 'Samuel', 'Ghosh', 'samuel.ghosh@northgate.test', '+91-9472372237', 'Accounts Payable Specialist', 3, 4, 'ACTIVE', '2021-12-14', 1225000.00, '2024-01-05 09:00:00.000000'),
    (20, 'NG-1020', 'Ritu', 'Raman', 'ritu.raman@northgate.test', '+91-9477070496', 'HR Business Partner', 4, 5, 'ACTIVE', '2019-04-24', 1750000.00, '2024-01-05 09:00:00.000000'),
    (21, 'NG-1021', 'Nitin', 'Rana', 'nitin.rana@northgate.test', '+91-9565325179', 'Customer Success Manager', 5, 6, 'ACTIVE', '2024-03-07', 1425000.00, '2024-01-05 09:00:00.000000'),
    (22, 'NG-1022', 'Lakshmi', 'Verma', 'lakshmi.verma@northgate.test', '+91-9876070443', 'Software Engineer', 1, 2, 'ACTIVE', '2018-01-19', 1375000.00, '2024-01-05 09:00:00.000000'),
    (23, 'NG-1023', 'Deepak', 'Krishnan', 'deepak.krishnan@northgate.test', '+91-9918096946', 'Regional Sales Manager', 2, 3, 'ACTIVE', '2023-07-21', 2525000.00, '2024-01-05 09:00:00.000000'),
    (24, 'NG-1024', 'Ananya', 'Desai', 'ananya.desai@northgate.test', '+91-9616529640', 'Accounts Payable Specialist', 3, 4, 'ACTIVE', '2022-06-08', 2050000.00, '2024-01-05 09:00:00.000000'),
    (25, 'NG-1025', 'Priyanka', 'Chandra', 'priyanka.chandra@northgate.test', '+91-9101856224', 'HR Business Partner', 4, 5, 'ACTIVE', '2016-04-22', 2350000.00, '2024-01-05 09:00:00.000000'),
    (26, 'NG-1026', 'Nikhil', 'Kulkarni', 'nikhil.kulkarni@northgate.test', '+91-9967028647', 'Support Team Lead', 5, 6, 'ACTIVE', '2024-12-28', 1000000.00, '2024-01-05 09:00:00.000000'),
    (27, 'NG-1027', 'Neha', 'Kapoor', 'neha.kapoor@northgate.test', '+91-9145290623', 'QA Engineer', 1, 2, 'ACTIVE', '2018-08-17', 2100000.00, '2024-01-05 09:00:00.000000'),
    (28, 'NG-1028', 'Varun', 'Thomas', 'varun.thomas@northgate.test', '+91-9789507930', 'Regional Sales Manager', 2, 3, 'ACTIVE', '2016-04-11', 2650000.00, '2024-01-05 09:00:00.000000'),
    (29, 'NG-1029', 'Isha', 'Joshi', 'isha.joshi@northgate.test', '+91-9755687819', 'Accounts Payable Specialist', 3, 4, 'ACTIVE', '2022-08-10', 1900000.00, '2024-01-05 09:00:00.000000'),
    (30, 'NG-1030', 'Joseph', 'Farooqi', 'joseph.farooqi@northgate.test', '+91-9742405517', 'HR Business Partner', 4, 5, 'ACTIVE', '2016-08-16', 1850000.00, '2024-01-05 09:00:00.000000'),
    (31, 'NG-1031', 'Maya', 'Khanna', 'maya.khanna@northgate.test', '+91-9845553644', 'Senior Support Engineer', 5, 6, 'ACTIVE', '2021-10-25', 1750000.00, '2024-01-05 09:00:00.000000'),
    (32, 'NG-1032', 'Ayaan', 'Bansal', 'ayaan.bansal@northgate.test', '+91-9348947325', 'Software Engineer', 1, 2, 'ACTIVE', '2020-01-25', 1100000.00, '2024-01-05 09:00:00.000000'),
    (33, 'NG-1033', 'Aisha', 'Fernandes', 'aisha.fernandes@northgate.test', '+91-9181515986', 'Regional Sales Manager', 2, 3, 'ACTIVE', '2017-02-03', 2825000.00, '2024-01-05 09:00:00.000000'),
    (34, 'NG-1034', 'Vivek', 'Chauhan', 'vivek.chauhan@northgate.test', '+91-9256289237', 'Accounts Payable Specialist', 3, 4, 'ACTIVE', '2017-11-10', 800000.00, '2024-01-05 09:00:00.000000'),
    (35, 'NG-1035', 'Naina', 'Hegde', 'naina.hegde@northgate.test', '+91-9825660132', 'HR Business Partner', 4, 5, 'ACTIVE', '2023-06-10', 1950000.00, '2024-01-05 09:00:00.000000'),
    (36, 'NG-1036', 'Rohan', 'Sinha', 'rohan.sinha@northgate.test', '+91-9688208032', 'Support Engineer', 5, 6, 'ACTIVE', '2023-09-18', 1450000.00, '2024-01-05 09:00:00.000000'),
    (37, 'NG-1037', 'Dev', 'Anand', 'dev.anand@northgate.test', '+91-9384466538', 'QA Engineer', 1, 2, 'ACTIVE', '2017-03-24', 3650000.00, '2024-01-05 09:00:00.000000'),
    (38, 'NG-1038', 'Kavya', 'Mathew', 'kavya.mathew@northgate.test', '+91-9283183032', 'Regional Sales Manager', 2, 3, 'ACTIVE', '2017-11-13', 3125000.00, '2024-01-05 09:00:00.000000'),
    (39, 'NG-1039', 'Aditya', 'Banerjee', 'aditya.banerjee@northgate.test', '+91-9938513595', 'Accounts Payable Specialist', 3, 4, 'ACTIVE', '2016-11-15', 1250000.00, '2024-01-05 09:00:00.000000'),
    (40, 'NG-1040', 'Anjali', 'Patel', 'anjali.patel@northgate.test', '+91-9575436428', 'HR Business Partner', 4, 5, 'ACTIVE', '2019-12-19', 1150000.00, '2024-01-05 09:00:00.000000'),
    (41, 'NG-1041', 'Rajat', 'Chopra', 'rajat.chopra@northgate.test', '+91-9285333563', 'Customer Success Manager', 5, 6, 'ON_LEAVE', '2019-11-14', 1575000.00, '2024-01-05 09:00:00.000000'),
    (42, 'NG-1042', 'Grace', 'Bhattacharya', 'grace.bhattacharya@northgate.test', '+91-9994675122', 'Software Engineer', 1, 2, 'ACTIVE', '2024-05-23', 3525000.00, '2024-01-05 09:00:00.000000'),
    (43, 'NG-1043', 'Omar', 'Shetty', 'omar.shetty@northgate.test', '+91-9847920493', 'Regional Sales Manager', 2, 3, 'ACTIVE', '2016-02-19', 3350000.00, '2024-01-05 09:00:00.000000'),
    (44, 'NG-1044', 'Sara', 'Sharma', 'sara.sharma@northgate.test', '+91-9608014232', 'Accounts Payable Specialist', 3, 4, 'ACTIVE', '2023-10-22', 1050000.00, '2024-01-05 09:00:00.000000'),
    (45, 'NG-1045', 'Gaurav', 'Ali', 'gaurav.ali@northgate.test', '+91-9661593675', 'HR Business Partner', 4, 5, 'ACTIVE', '2022-12-15', 1325000.00, '2024-01-05 09:00:00.000000'),
    (46, 'NG-1046', 'Roshni', 'Mehta', 'roshni.mehta@northgate.test', '+91-9285650467', 'Support Team Lead', 5, 6, 'ACTIVE', '2020-09-09', 875000.00, '2024-01-05 09:00:00.000000'),
    (47, 'NG-1047', 'Ravi', 'Naidu', 'ravi.naidu@northgate.test', '+91-9185251117', 'QA Engineer', 1, 2, 'TERMINATED', '2021-04-20', 1675000.00, '2024-01-05 09:00:00.000000'),
    (48, 'NG-1048', 'Meera', 'Saxena', 'meera.saxena@northgate.test', '+91-9742195657', 'Regional Sales Manager', 2, 3, 'ACTIVE', '2017-03-20', 1850000.00, '2024-01-05 09:00:00.000000'),
    (49, 'NG-1049', 'Sneha', 'Menon', 'sneha.menon@northgate.test', '+91-9352233161', 'Accounts Payable Specialist', 3, 4, 'ACTIVE', '2018-12-03', 2550000.00, '2024-01-05 09:00:00.000000'),
    (50, 'NG-1050', 'Rahul', 'Bajaj', 'rahul.bajaj@northgate.test', '+91-9868607193', 'HR Business Partner', 4, 5, 'ACTIVE', '2016-08-01', 1525000.00, '2024-01-05 09:00:00.000000'),
    (51, 'NG-1051', 'Pooja', 'Malhotra', 'pooja.malhotra@northgate.test', '+91-9948113921', 'Senior Support Engineer', 5, 6, 'ACTIVE', '2020-03-15', 1125000.00, '2024-01-05 09:00:00.000000'),
    (52, 'NG-1052', 'Karan', 'Subramanian', 'karan.subramanian@northgate.test', '+91-9730201619', 'Software Engineer', 1, 2, 'ON_LEAVE', '2022-05-16', 3775000.00, '2024-01-05 09:00:00.000000'),
    (53, 'NG-1053', 'Nandini', 'Trivedi', 'nandini.trivedi@northgate.test', '+91-9647210046', 'Regional Sales Manager', 2, 3, 'ACTIVE', '2021-12-12', 875000.00, '2024-01-05 09:00:00.000000'),
    (54, 'NG-1054', 'Daniel', 'Iyer', 'daniel.iyer@northgate.test', '+91-9214984929', 'Accounts Payable Specialist', 3, 4, 'ACTIVE', '2018-09-16', 1600000.00, '2024-01-05 09:00:00.000000'),
    (55, 'NG-1055', 'Zara', 'Abraham', 'zara.abraham@northgate.test', '+91-9939410210', 'HR Business Partner', 4, 5, 'ACTIVE', '2017-11-05', 1575000.00, '2024-01-05 09:00:00.000000'),
    (56, 'NG-1056', 'Harsh', 'Pillai', 'harsh.pillai@northgate.test', '+91-9980127909', 'Support Engineer', 5, 6, 'ACTIVE', '2022-04-22', 775000.00, '2024-01-05 09:00:00.000000'),
    (57, 'NG-1057', 'Mira', 'Prasad', 'mira.prasad@northgate.test', '+91-9830191187', 'QA Engineer', 1, 2, 'ACTIVE', '2016-02-18', 1100000.00, '2024-01-05 09:00:00.000000'),
    (58, 'NG-1058', 'Anil', 'Dutta', 'anil.dutta@northgate.test', '+91-9666647438', 'Regional Sales Manager', 2, 3, 'TERMINATED', '2016-05-07', 2050000.00, '2024-01-05 09:00:00.000000'),
    (59, 'NG-1059', 'Bhavna', 'Wadhwa', 'bhavna.wadhwa@northgate.test', '+91-9179670202', 'Accounts Payable Specialist', 3, 4, 'ACTIVE', '2022-11-23', 650000.00, '2024-01-05 09:00:00.000000'),
    (60, 'NG-1060', 'Kabir', 'Ahmed', 'kabir.ahmed@northgate.test', '+91-9994248895', 'HR Business Partner', 4, 5, 'ACTIVE', '2018-07-26', 2150000.00, '2024-01-05 09:00:00.000000');

-- Sign-in accounts. Directory users use "Password123!", the HR administrator uses "Admin123!".
INSERT IGNORE INTO users (id, username, password_hash, employee_id, enabled, created_at) VALUES
    (1, 'ishita.sheikh@northgate.test', '$2a$10$Qoeh9Vl6k.enhigi0fhELeDmesn7WtAOX1O9MlpuAqq55890K5b7W', 1, 1, '2024-01-05 09:00:00.000000'),
    (2, 'arjun.reddy@northgate.test', '$2a$10$Qoeh9Vl6k.enhigi0fhELeDmesn7WtAOX1O9MlpuAqq55890K5b7W', 2, 1, '2024-01-05 09:00:00.000000'),
    (3, 'imran.dsouza@northgate.test', '$2a$10$BfSO2Iny00pHa97kZJKGfOmWQPg0EJiFX/Kp4ocpiShu1DVmmhsCa', 6, 1, '2024-01-05 09:00:00.000000'),
    (4, 'aarav.nair@northgate.test', '$2a$10$Qoeh9Vl6k.enhigi0fhELeDmesn7WtAOX1O9MlpuAqq55890K5b7W', 12, 1, '2024-01-05 09:00:00.000000'),
    (5, 'deepak.krishnan@northgate.test', '$2a$10$Qoeh9Vl6k.enhigi0fhELeDmesn7WtAOX1O9MlpuAqq55890K5b7W', 23, 1, '2024-01-05 09:00:00.000000');

INSERT IGNORE INTO user_roles (user_id, role_id) VALUES
    (1, 1), (1, 2),
    (2, 1), (2, 2),
    (3, 1), (3, 3),
    (4, 1),
    (5, 1);
