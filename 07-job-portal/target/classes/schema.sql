-- Hirestack job board schema (MySQL 8)
-- Applied on every boot; statements are idempotent.

CREATE TABLE IF NOT EXISTS companies (
    id       BIGINT       NOT NULL AUTO_INCREMENT,
    name     VARCHAR(140) NOT NULL,
    slug     VARCHAR(140) NOT NULL,
    industry VARCHAR(80)  NOT NULL,
    city     VARCHAR(80)  NOT NULL,
    verified TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_companies_slug (slug)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS candidates (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    full_name        VARCHAR(120)  NOT NULL,
    email            VARCHAR(180)  NOT NULL,
    phone            VARCHAR(24)   DEFAULT NULL,
    headline         VARCHAR(200)  NOT NULL,
    location         VARCHAR(80)   NOT NULL,
    years_experience INT           NOT NULL DEFAULT 0,
    skills           VARCHAR(400)  NOT NULL DEFAULT '',
    expected_salary  DECIMAL(12,2) DEFAULT NULL,
    created_at       DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_candidates_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(180) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role_name     VARCHAR(40)  NOT NULL,
    company_id    BIGINT       DEFAULT NULL,
    candidate_id  BIGINT       DEFAULT NULL,
    enabled       TINYINT(1)   NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email),
    CONSTRAINT fk_users_company   FOREIGN KEY (company_id)   REFERENCES companies (id),
    CONSTRAINT fk_users_candidate FOREIGN KEY (candidate_id) REFERENCES candidates (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS job_postings (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    reference       VARCHAR(24)   NOT NULL,
    company_id      BIGINT        NOT NULL,
    posted_by_id    BIGINT        NOT NULL,
    title           VARCHAR(160)  NOT NULL,
    description     VARCHAR(2000) NOT NULL,
    location        VARCHAR(80)   NOT NULL,
    employment_type VARCHAR(24)   NOT NULL,
    remote          TINYINT(1)    NOT NULL DEFAULT 0,
    min_experience  INT           NOT NULL DEFAULT 0,
    min_salary      DECIMAL(12,2) NOT NULL,
    max_salary      DECIMAL(12,2) NOT NULL,
    status          VARCHAR(16)   NOT NULL DEFAULT 'DRAFT',
    created_at      DATETIME(6)   NOT NULL,
    closes_on       DATE          DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_job_postings_reference (reference),
    KEY idx_job_postings_company (company_id),
    KEY idx_job_postings_status (status),
    CONSTRAINT fk_job_postings_company FOREIGN KEY (company_id)   REFERENCES companies (id),
    CONSTRAINT fk_job_postings_user    FOREIGN KEY (posted_by_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS applications (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    job_posting_id BIGINT       NOT NULL,
    candidate_id   BIGINT       NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'SUBMITTED',
    cover_letter   VARCHAR(2000) DEFAULT NULL,
    applied_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_applications_job (job_posting_id),
    KEY idx_applications_candidate (candidate_id),
    CONSTRAINT fk_applications_job       FOREIGN KEY (job_posting_id) REFERENCES job_postings (id),
    CONSTRAINT fk_applications_candidate FOREIGN KEY (candidate_id)   REFERENCES candidates (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
