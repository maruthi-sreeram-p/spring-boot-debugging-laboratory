-- Spicebox food ordering schema (MySQL 8)
-- Applied on every boot; statements are idempotent.

CREATE TABLE IF NOT EXISTS customers (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    email            VARCHAR(180) NOT NULL,
    password_hash    VARCHAR(100) NOT NULL,
    full_name        VARCHAR(120) NOT NULL,
    phone            VARCHAR(24)  DEFAULT NULL,
    default_address  VARCHAR(300) NOT NULL,
    role_name        VARCHAR(40)  NOT NULL DEFAULT 'ROLE_CUSTOMER',
    created_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_customers_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS restaurants (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    name         VARCHAR(120) NOT NULL,
    cuisine      VARCHAR(60)  NOT NULL,
    city         VARCHAR(60)  NOT NULL,
    prep_minutes INT          NOT NULL DEFAULT 20,
    active       TINYINT(1)   NOT NULL DEFAULT 1,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS menu_items (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    restaurant_id BIGINT        NOT NULL,
    name          VARCHAR(140)  NOT NULL,
    description   VARCHAR(400)  DEFAULT NULL,
    category      VARCHAR(60)   NOT NULL,
    price         DECIMAL(10,2) NOT NULL,
    available     TINYINT(1)    NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    KEY idx_menu_items_restaurant (restaurant_id),
    CONSTRAINT fk_menu_items_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS food_orders (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    order_code       VARCHAR(24)   NOT NULL,
    customer_id      BIGINT        NOT NULL,
    restaurant_id    BIGINT        NOT NULL,
    status           VARCHAR(24)   NOT NULL DEFAULT 'PLACED',
    subtotal         DECIMAL(10,2) NOT NULL,
    delivery_fee     DECIMAL(10,2) NOT NULL,
    total_amount     DECIMAL(10,2) NOT NULL,
    delivery_address VARCHAR(300)  NOT NULL,
    placed_at        DATETIME(6)   NOT NULL,
    updated_at       DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_food_orders_code (order_code),
    KEY idx_food_orders_customer (customer_id),
    KEY idx_food_orders_status (status),
    CONSTRAINT fk_food_orders_customer   FOREIGN KEY (customer_id)   REFERENCES customers (id),
    CONSTRAINT fk_food_orders_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS order_items (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    order_id     BIGINT        NOT NULL,
    menu_item_id BIGINT        NOT NULL,
    item_name    VARCHAR(140)  NOT NULL,
    quantity     INT           NOT NULL,
    unit_price   DECIMAL(10,2) NOT NULL,
    line_total   DECIMAL(10,2) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_order_items_order (order_id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id)     REFERENCES food_orders (id),
    CONSTRAINT fk_order_items_menu  FOREIGN KEY (menu_item_id) REFERENCES menu_items (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS order_notifications (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    order_id   BIGINT       NOT NULL,
    channel    VARCHAR(20)  NOT NULL,
    recipient  VARCHAR(180) NOT NULL,
    subject    VARCHAR(200) NOT NULL,
    sent_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_notifications_order (order_id),
    CONSTRAINT fk_notifications_order FOREIGN KEY (order_id) REFERENCES food_orders (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
