-- 9Tails delivery workflow migration
-- The backend applies this after Hibernate has synchronized mapped columns.
-- The ALTER TABLE statements here intentionally contain only MySQL-supported syntax.

USE nintails_ecommerce;

UPDATE products
SET version = 0
WHERE version IS NULL;

ALTER TABLE products
    MODIFY COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE users
    MODIFY COLUMN role ENUM('USER','OWNER','DELIVERY_AGENT','WAREHOUSE_OPERATOR','ADMIN')
        NOT NULL DEFAULT 'USER';

ALTER TABLE orders
    MODIFY COLUMN status ENUM('PENDING','CONFIRMED','PICKED_UP_FROM_OWNER',
        'PACKED_READY','PACKED','SHIPPED','IN_TRANSIT_TO_STORE',
        'AT_DESTINATION_STORE','OUT_FOR_DELIVERY',
        'DELIVERY_CONFIRMATION_PENDING','DELIVERED','CANCELLED')
        NOT NULL DEFAULT 'PENDING',
    MODIFY COLUMN payment_status ENUM('PENDING','PAID','FAILED','REFUND_PENDING',
        'PARTIALLY_REFUNDED','REFUNDED') DEFAULT 'PENDING';

UPDATE order_items oi
JOIN orders o ON o.id = oi.order_id
SET oi.stock_reserved = 1
WHERE oi.cancelled = 0
    AND o.status <> 'CANCELLED'
    AND (o.payment_method = 'COD' OR o.payment_status = 'PAID');

CREATE TABLE IF NOT EXISTS business_locations (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    street VARCHAR(255) NOT NULL,
    city VARCHAR(100) NOT NULL,
    state VARCHAR(100) NOT NULL,
    postal_code VARCHAR(6) NOT NULL,
    country VARCHAR(100) NOT NULL DEFAULT 'India',
    contact_phone VARCHAR(20) NOT NULL,
    verified_by_owner TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    CONSTRAINT fk_business_location_owner FOREIGN KEY (owner_id)
        REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS seller_fulfillments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    owner_id BIGINT,
    seller_key VARCHAR(320) NOT NULL,
    status VARCHAR(40) NOT NULL,
    delivery_agent_id BIGINT,
    expected_delivery_date DATETIME(6),
    owner_responded_at DATETIME(6),
    pickup_at DATETIME(6),
    warehouse_received_at DATETIME(6),
    dispatched_at DATETIME(6),
    delivered_at DATETIME(6),
    confirmation_requested_at DATETIME(6),
    customer_delivery_response VARCHAR(40),
    customer_delivery_response_at DATETIME(6),
    rejection_reason VARCHAR(500),
    pickup_name VARCHAR(255),
    pickup_street VARCHAR(255),
    pickup_city VARCHAR(100),
    pickup_state VARCHAR(100),
    pickup_postal_code VARCHAR(6),
    pickup_country VARCHAR(100),
    created_at DATETIME(6),
    updated_at DATETIME(6),
    CONSTRAINT uq_fulfillment_order_seller UNIQUE (order_id, seller_key),
    CONSTRAINT fk_fulfillment_order FOREIGN KEY (order_id)
        REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_fulfillment_owner FOREIGN KEY (owner_id)
        REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_fulfillment_agent FOREIGN KEY (delivery_agent_id)
        REFERENCES users(id) ON DELETE SET NULL,
    INDEX ix_fulfillment_owner_status (owner_id, status),
    INDEX ix_fulfillment_agent_status (delivery_agent_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE seller_fulfillments
    MODIFY COLUMN status VARCHAR(40) NOT NULL;

CREATE TABLE IF NOT EXISTS delivery_stage_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    fulfillment_id BIGINT NOT NULL,
    stage VARCHAR(32) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    token_ciphertext VARCHAR(256) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6),
    revoked_at DATETIME(6),
    issued_at DATETIME(6),
    CONSTRAINT uq_delivery_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_token_fulfillment FOREIGN KEY (fulfillment_id)
        REFERENCES seller_fulfillments(id) ON DELETE CASCADE,
    INDEX ix_token_fulfillment_stage (fulfillment_id, stage, issued_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS delivery_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    fulfillment_id BIGINT NOT NULL,
    actor_id BIGINT,
    event_type VARCHAR(50) NOT NULL,
    previous_status VARCHAR(40),
    new_status VARCHAR(40),
    outcome VARCHAR(20) NOT NULL,
    package_condition VARCHAR(100),
    notes VARCHAR(1000),
    created_at DATETIME(6),
    CONSTRAINT fk_event_fulfillment FOREIGN KEY (fulfillment_id)
        REFERENCES seller_fulfillments(id) ON DELETE CASCADE,
    CONSTRAINT fk_event_actor FOREIGN KEY (actor_id)
        REFERENCES users(id) ON DELETE SET NULL,
    INDEX ix_event_fulfillment_created (fulfillment_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    recipient_id BIGINT NOT NULL,
    order_id BIGINT,
    fulfillment_id BIGINT,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(150) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    action_url VARCHAR(500),
    read_at DATETIME(6),
    created_at DATETIME(6),
    CONSTRAINT fk_notification_recipient FOREIGN KEY (recipient_id)
        REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_order FOREIGN KEY (order_id)
        REFERENCES orders(id) ON DELETE SET NULL,
    CONSTRAINT fk_notification_fulfillment FOREIGN KEY (fulfillment_id)
        REFERENCES seller_fulfillments(id) ON DELETE SET NULL,
    INDEX ix_notification_recipient_created (recipient_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS service_requests (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_item_id BIGINT NOT NULL,
    type VARCHAR(16) NOT NULL,
    reason VARCHAR(40) NOT NULL,
    details VARCHAR(1000) NOT NULL,
    photo_url VARCHAR(500) NOT NULL,
    video_url VARCHAR(500) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'REQUESTED',
    reviewed_by_id BIGINT,
    review_notes VARCHAR(1000),
    reviewed_at DATETIME(6),
    warehouse_location_id BIGINT,
    delivery_agent_id BIGINT,
    picked_up_at DATETIME(6),
    warehouse_received_at DATETIME(6),
    seller_received_at DATETIME(6),
    replacement_delivered_at DATETIME(6),
    requested_at DATETIME(6),
    updated_at DATETIME(6),
    CONSTRAINT fk_service_request_item FOREIGN KEY (order_item_id)
        REFERENCES order_items(id) ON DELETE CASCADE,
    CONSTRAINT fk_service_request_reviewer FOREIGN KEY (reviewed_by_id)
        REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_service_request_warehouse FOREIGN KEY (warehouse_location_id)
        REFERENCES warehouse_locations(id) ON DELETE SET NULL,
    CONSTRAINT fk_service_request_agent FOREIGN KEY (delivery_agent_id)
        REFERENCES users(id) ON DELETE SET NULL,
    INDEX ix_service_request_item (order_item_id, requested_at),
    INDEX ix_service_request_warehouse (warehouse_location_id, status),
    INDEX ix_service_request_agent (delivery_agent_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS delivery_otp_challenges (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    fulfillment_id BIGINT NOT NULL,
    otp_hash CHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    failed_attempts INT NOT NULL DEFAULT 0,
    consumed_at DATETIME(6),
    created_at DATETIME(6),
    CONSTRAINT fk_otp_fulfillment FOREIGN KEY (fulfillment_id)
        REFERENCES seller_fulfillments(id) ON DELETE CASCADE,
    INDEX ix_otp_fulfillment_created (fulfillment_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS phone_verification_challenges (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    code_hash CHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    failed_attempts INT NOT NULL DEFAULT 0,
    consumed_at DATETIME(6),
    created_at DATETIME(6),
    CONSTRAINT fk_phone_challenge_user FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE CASCADE,
    INDEX ix_phone_challenge_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @migration_sql = IF(
    (SELECT COUNT(*) FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE() AND table_name = 'products'
            AND constraint_name = 'fk_product_owner') = 0,
    'ALTER TABLE products ADD CONSTRAINT fk_product_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE SET NULL',
    'SELECT 1');
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @migration_sql = IF(
    (SELECT COUNT(*) FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE() AND table_name = 'products'
            AND constraint_name = 'fk_product_business_location') = 0,
    'ALTER TABLE products ADD CONSTRAINT fk_product_business_location FOREIGN KEY (business_location_id) REFERENCES business_locations(id) ON DELETE SET NULL',
    'SELECT 1');
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @migration_sql = IF(
    (SELECT COUNT(*) FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE() AND table_name = 'order_items'
            AND constraint_name = 'fk_orderitem_fulfillment') = 0,
    'ALTER TABLE order_items ADD CONSTRAINT fk_orderitem_fulfillment FOREIGN KEY (fulfillment_id) REFERENCES seller_fulfillments(id) ON DELETE SET NULL',
    'SELECT 1');
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;
