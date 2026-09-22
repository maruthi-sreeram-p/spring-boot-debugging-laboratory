-- Northwind storefront schema (MySQL 8)
-- Applied on every boot; statements are idempotent so repeated starts are safe.

CREATE TABLE IF NOT EXISTS roles (
    id   BIGINT       NOT NULL AUTO_INCREMENT,
    name VARCHAR(40)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_roles_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS customers (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(180) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    phone         VARCHAR(20)  DEFAULT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_customers_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS customer_roles (
    customer_id BIGINT NOT NULL,
    role_id     BIGINT NOT NULL,
    PRIMARY KEY (customer_id, role_id),
    CONSTRAINT fk_customer_roles_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_customer_roles_role     FOREIGN KEY (role_id)     REFERENCES roles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS categories (
    id   BIGINT      NOT NULL AUTO_INCREMENT,
    name VARCHAR(80) NOT NULL,
    slug VARCHAR(80) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_categories_slug (slug)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS products (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    sku            VARCHAR(40)   NOT NULL,
    name           VARCHAR(160)  NOT NULL,
    description    VARCHAR(1000) DEFAULT NULL,
    price          DECIMAL(12,2) NOT NULL,
    stock_quantity INT           NOT NULL DEFAULT 0,
    category_id    BIGINT        DEFAULT NULL,
    active         TINYINT(1)    NOT NULL DEFAULT 1,
    created_at     DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_products_sku (sku),
    KEY idx_products_category (category_id),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS orders (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    order_number  VARCHAR(32)   NOT NULL,
    customer_id   BIGINT        NOT NULL,
    status        VARCHAR(20)   NOT NULL,
    subtotal      DECIMAL(12,2) NOT NULL,
    shipping_fee  DECIMAL(12,2) NOT NULL,
    total_amount  DECIMAL(12,2) NOT NULL,
    shipping_address VARCHAR(400) NOT NULL,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_orders_number (order_number),
    KEY idx_orders_customer (customer_id),
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES customers (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS order_items (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    order_id     BIGINT        NOT NULL,
    product_id   BIGINT        NOT NULL,
    product_name VARCHAR(160)  NOT NULL,
    quantity     INT           NOT NULL,
    unit_price   DECIMAL(12,2) NOT NULL,
    line_total   DECIMAL(12,2) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_order_items_order (order_id),
    CONSTRAINT fk_order_items_order   FOREIGN KEY (order_id)   REFERENCES orders (id),
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
