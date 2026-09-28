-- Ledgerline payment processing schema (MySQL 8)
-- Applied on every boot; statements are idempotent.

CREATE TABLE IF NOT EXISTS merchants (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    code       VARCHAR(24)  NOT NULL,
    name       VARCHAR(140) NOT NULL,
    active     TINYINT(1)   NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uk_merchants_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS orders (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    order_ref    VARCHAR(32)   NOT NULL,
    merchant_id  BIGINT        NOT NULL,
    customer_ref VARCHAR(64)   NOT NULL,
    amount       DECIMAL(12,2) NOT NULL,
    currency     CHAR(3)       NOT NULL DEFAULT 'INR',
    status       VARCHAR(24)   NOT NULL DEFAULT 'AWAITING_PAYMENT',
    created_at   DATETIME(6)   NOT NULL,
    updated_at   DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_orders_ref (order_ref),
    KEY idx_orders_status (status),
    CONSTRAINT fk_orders_merchant FOREIGN KEY (merchant_id) REFERENCES merchants (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS payments (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    payment_ref     VARCHAR(32)   NOT NULL,
    order_id        BIGINT        NOT NULL,
    idempotency_key VARCHAR(80)   NOT NULL,
    amount          DECIMAL(12,2) NOT NULL,
    currency        CHAR(3)       NOT NULL DEFAULT 'INR',
    instrument      VARCHAR(40)   NOT NULL,
    status          VARCHAR(24)   NOT NULL DEFAULT 'PENDING',
    attempts        INT           NOT NULL DEFAULT 0,
    gateway_ref     VARCHAR(64)   DEFAULT NULL,
    failure_reason  VARCHAR(200)  DEFAULT NULL,
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payments_ref (payment_ref),
    KEY idx_payments_order (order_id),
    KEY idx_payments_status (status),
    KEY idx_payments_idempotency (idempotency_key),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS payment_attempts (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    payment_id  BIGINT       NOT NULL,
    attempt_no  INT          NOT NULL,
    outcome     VARCHAR(24)  NOT NULL,
    gateway_ref VARCHAR(64)  DEFAULT NULL,
    message     VARCHAR(200) DEFAULT NULL,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_attempts_payment (payment_id),
    CONSTRAINT fk_attempts_payment FOREIGN KEY (payment_id) REFERENCES payments (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(160) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role_name     VARCHAR(40)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
