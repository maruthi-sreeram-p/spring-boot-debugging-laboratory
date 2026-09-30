-- Athenaeum circulation schema (MySQL 8)
-- Applied on every boot; every statement is idempotent.

CREATE TABLE IF NOT EXISTS books (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    isbn            VARCHAR(20)  NOT NULL UNIQUE,
    title           VARCHAR(250) NOT NULL,
    author          VARCHAR(180) NOT NULL,
    publisher       VARCHAR(180) NOT NULL,
    published_year  INT          NOT NULL,
    shelf_mark      VARCHAR(40)  NOT NULL
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS book_copies (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    book_id    BIGINT      NOT NULL,
    barcode    VARCHAR(30) NOT NULL UNIQUE,
    branch     VARCHAR(60) NOT NULL,
    status     VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    acquired_on DATE       NOT NULL,
    version    BIGINT      NOT NULL DEFAULT 0,
    KEY idx_copy_book_status (book_id, status),
    CONSTRAINT fk_copy_book FOREIGN KEY (book_id) REFERENCES books (id)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS members (
    id                BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    membership_number VARCHAR(20)  NOT NULL UNIQUE,
    full_name         VARCHAR(150) NOT NULL,
    email             VARCHAR(190) NOT NULL UNIQUE,
    tier              VARCHAR(20)  NOT NULL DEFAULT 'STANDARD',
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    active_loan_count INT          NOT NULL DEFAULT 0,
    joined_on         DATE         NOT NULL
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS loans (
    id            BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    copy_id       BIGINT      NOT NULL,
    member_id     BIGINT      NOT NULL,
    borrowed_at   DATETIME(6) NOT NULL,
    due_at        DATETIME(6) NOT NULL,
    returned_at   DATETIME(6) NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    renewal_count INT         NOT NULL DEFAULT 0,
    KEY idx_loan_member_status (member_id, status),
    KEY idx_loan_copy_status (copy_id, status),
    CONSTRAINT fk_loan_copy   FOREIGN KEY (copy_id)   REFERENCES book_copies (id),
    CONSTRAINT fk_loan_member FOREIGN KEY (member_id) REFERENCES members (id)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS reservations (
    id             BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    book_id        BIGINT      NOT NULL,
    member_id      BIGINT      NOT NULL,
    placed_at      DATETIME(6) NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    queue_position INT         NOT NULL,
    held_copy_id   BIGINT      NULL,
    ready_until    DATETIME(6) NULL,
    KEY idx_res_book_status (book_id, status),
    CONSTRAINT fk_res_book   FOREIGN KEY (book_id)   REFERENCES books (id),
    CONSTRAINT fk_res_member FOREIGN KEY (member_id) REFERENCES members (id)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS fines (
    id           BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    loan_id      BIGINT        NOT NULL,
    member_id    BIGINT        NOT NULL,
    amount       DECIMAL(10,2) NOT NULL,
    days_overdue INT           NOT NULL,
    assessed_at  DATETIME(6)   NOT NULL,
    paid_at      DATETIME(6)   NULL,
    status       VARCHAR(20)   NOT NULL DEFAULT 'OUTSTANDING',
    KEY idx_fine_member_status (member_id, status),
    CONSTRAINT fk_fine_loan   FOREIGN KEY (loan_id)   REFERENCES loans (id),
    CONSTRAINT fk_fine_member FOREIGN KEY (member_id) REFERENCES members (id)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS app_users (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(190) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role_name     VARCHAR(40)  NOT NULL,
    member_id     BIGINT       NULL,
    CONSTRAINT fk_user_member FOREIGN KEY (member_id) REFERENCES members (id)
) ENGINE = InnoDB;
