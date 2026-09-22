-- Northgate people directory schema (MySQL 8)
-- Applied on every boot; statements are idempotent.

CREATE TABLE IF NOT EXISTS roles (
    id   BIGINT      NOT NULL AUTO_INCREMENT,
    name VARCHAR(40) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_roles_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS departments (
    id       BIGINT      NOT NULL AUTO_INCREMENT,
    code     VARCHAR(12) NOT NULL,
    name     VARCHAR(80) NOT NULL,
    location VARCHAR(80) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_departments_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS employees (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    employee_code     VARCHAR(16)    NOT NULL,
    first_name        VARCHAR(60)    NOT NULL,
    last_name         VARCHAR(60)    NOT NULL,
    email             VARCHAR(160)   NOT NULL,
    phone             VARCHAR(24)    DEFAULT NULL,
    job_title         VARCHAR(80)    NOT NULL,
    department_id     BIGINT         DEFAULT NULL,
    manager_id        BIGINT         DEFAULT NULL,
    employment_status VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    hire_date         DATE           NOT NULL,
    salary            DECIMAL(12, 2) NOT NULL,
    created_at        DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_employees_code (employee_code),
    UNIQUE KEY uk_employees_email (email),
    KEY idx_employees_department (department_id),
    KEY idx_employees_manager (manager_id),
    KEY idx_employees_last_name (last_name),
    CONSTRAINT fk_employees_department FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT fk_employees_manager    FOREIGN KEY (manager_id)    REFERENCES employees (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(160) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    employee_id   BIGINT       NOT NULL,
    enabled       TINYINT(1)   NOT NULL DEFAULT 1,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username),
    UNIQUE KEY uk_users_employee (employee_id),
    CONSTRAINT fk_users_employee FOREIGN KEY (employee_id) REFERENCES employees (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
