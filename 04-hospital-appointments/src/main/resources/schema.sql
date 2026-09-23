-- Harbourview Clinic outpatient scheduling schema (MySQL 8)
-- Applied on every boot; statements are idempotent.

CREATE TABLE IF NOT EXISTS patients (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    mrn           VARCHAR(16)  NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    email         VARCHAR(160) NOT NULL,
    phone         VARCHAR(24)  DEFAULT NULL,
    date_of_birth DATE         NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_patients_mrn (mrn),
    UNIQUE KEY uk_patients_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS doctors (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    code                 VARCHAR(16)  NOT NULL,
    full_name            VARCHAR(120) NOT NULL,
    specialty            VARCHAR(80)  NOT NULL,
    email                VARCHAR(160) NOT NULL,
    consultation_minutes INT          NOT NULL DEFAULT 30,
    active               TINYINT(1)   NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uk_doctors_code (code),
    UNIQUE KEY uk_doctors_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS doctor_schedules (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    doctor_id    BIGINT      NOT NULL,
    day_of_week  VARCHAR(12) NOT NULL,
    start_time   TIME        NOT NULL,
    end_time     TIME        NOT NULL,
    slot_minutes INT         NOT NULL DEFAULT 30,
    active       TINYINT(1)  NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    KEY idx_schedules_doctor (doctor_id),
    CONSTRAINT fk_schedules_doctor FOREIGN KEY (doctor_id) REFERENCES doctors (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS appointments (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    reference           VARCHAR(24)  NOT NULL,
    doctor_id           BIGINT       NOT NULL,
    patient_id          BIGINT       NOT NULL,
    start_time          DATETIME     NOT NULL,
    end_time            DATETIME     NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'BOOKED',
    reason              VARCHAR(300) DEFAULT NULL,
    cancellation_reason VARCHAR(300) DEFAULT NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_appointments_reference (reference),
    KEY idx_appointments_doctor_start (doctor_id, start_time),
    KEY idx_appointments_patient (patient_id),
    KEY idx_appointments_status (status),
    CONSTRAINT fk_appointments_doctor  FOREIGN KEY (doctor_id)  REFERENCES doctors (id),
    CONSTRAINT fk_appointments_patient FOREIGN KEY (patient_id) REFERENCES patients (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(160) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role_name     VARCHAR(40)  NOT NULL,
    patient_id    BIGINT       DEFAULT NULL,
    doctor_id     BIGINT       DEFAULT NULL,
    enabled       TINYINT(1)   NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username),
    CONSTRAINT fk_users_patient FOREIGN KEY (patient_id) REFERENCES patients (id),
    CONSTRAINT fk_users_doctor  FOREIGN KEY (doctor_id)  REFERENCES doctors (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
