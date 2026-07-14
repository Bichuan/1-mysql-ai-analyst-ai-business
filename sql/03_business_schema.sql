USE ai_business;

CREATE TABLE IF NOT EXISTS biz_customer (
    id BIGINT NOT NULL AUTO_INCREMENT,
    customer_name VARCHAR(100) NOT NULL,
    customer_level VARCHAR(20) NOT NULL DEFAULT 'C' COMMENT 'VIP/A/B/C',
    region VARCHAR(50) NOT NULL,
    industry VARCHAR(50) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_biz_customer_region (region),
    KEY idx_biz_customer_level (customer_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户信息表';

CREATE TABLE IF NOT EXISTS biz_product (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_name VARCHAR(200) NOT NULL,
    category VARCHAR(50) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    stock INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_biz_product_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品信息表';

CREATE TABLE IF NOT EXISTS biz_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_no VARCHAR(50) NOT NULL,
    customer_id BIGINT NOT NULL,
    amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    status VARCHAR(20) NOT NULL COMMENT 'PAID/SHIPPED/COMPLETED/CANCELLED',
    order_date DATE NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_biz_order_no (order_no),
    KEY idx_biz_order_customer_date (customer_id, order_date),
    KEY idx_biz_order_status_date (status, order_date),
    CONSTRAINT fk_biz_order_customer FOREIGN KEY (customer_id) REFERENCES biz_customer (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单主表';

CREATE TABLE IF NOT EXISTS biz_order_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    subtotal DECIMAL(12,2) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_biz_order_item_order (order_id),
    KEY idx_biz_order_item_product (product_id),
    CONSTRAINT fk_biz_order_item_order FOREIGN KEY (order_id) REFERENCES biz_order (id),
    CONSTRAINT fk_biz_order_item_product FOREIGN KEY (product_id) REFERENCES biz_product (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细表';
