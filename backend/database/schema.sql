-- ============================================================
--  9Tails Ecommerce  –  MySQL Schema
--  Run this in MySQL Workbench BEFORE starting the Spring Boot app.
--  The app's JPA (ddl-auto=update) will keep tables in sync after that.
-- ============================================================

-- 1. Create & use the database
CREATE DATABASE IF NOT EXISTS nintails_ecommerce
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE nintails_ecommerce;

-- ============================================================
-- 2. Tables
-- ============================================================

-- Users
CREATE TABLE IF NOT EXISTS users (
    id          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    full_name   VARCHAR(100)  NOT NULL,
    email       VARCHAR(150)  NOT NULL UNIQUE,
    password    VARCHAR(255)  NOT NULL,
    role        ENUM('USER','ADMIN') NOT NULL DEFAULT 'USER',
    created_at  DATETIME(6),
    updated_at  DATETIME(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Categories
CREATE TABLE IF NOT EXISTS categories (
    id          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100)  NOT NULL UNIQUE,
    description TEXT,
    image_url   VARCHAR(500)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Products
CREATE TABLE IF NOT EXISTS products (
    id              BIGINT           NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(255)     NOT NULL,
    description     TEXT,
    price           DECIMAL(10,2)    NOT NULL,
    original_price  DECIMAL(10,2),
    stock           INT              NOT NULL DEFAULT 0,
    image_url       VARCHAR(500),
    gallery_images  TEXT,
    rating          DOUBLE           DEFAULT 0.0,
    review_count    INT              DEFAULT 0,
    active          TINYINT(1)       NOT NULL DEFAULT 1,
    category_id     BIGINT           NOT NULL,
    created_at      DATETIME(6),
    updated_at      DATETIME(6),
    CONSTRAINT fk_product_category FOREIGN KEY (category_id)
        REFERENCES categories(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Addresses
CREATE TABLE IF NOT EXISTS addresses (
    id          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    full_name   VARCHAR(100)  NOT NULL,
    street      VARCHAR(255)  NOT NULL,
    city        VARCHAR(100)  NOT NULL,
    postal_code VARCHAR(20)   NOT NULL,
    country     VARCHAR(100)  NOT NULL,
    is_default  TINYINT(1)    NOT NULL DEFAULT 0,
    user_id     BIGINT        NOT NULL,
    CONSTRAINT fk_address_user FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Orders
CREATE TABLE IF NOT EXISTS orders (
    id                   BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_number         VARCHAR(50)   NOT NULL UNIQUE,
    status               ENUM('PENDING','CONFIRMED','PACKED','SHIPPED',
                              'OUT_FOR_DELIVERY','DELIVERED','CANCELLED')
                         NOT NULL DEFAULT 'PENDING',
    subtotal             DECIMAL(10,2) NOT NULL,
    delivery_charge      DECIMAL(10,2) NOT NULL DEFAULT 0,
    total                DECIMAL(10,2) NOT NULL,
    payment_method       ENUM('COD','UPI'),
    payment_status       ENUM('PENDING','PAID','FAILED','REFUNDED') DEFAULT 'PENDING',
    address_full_name    VARCHAR(100),
    address_street       VARCHAR(255),
    address_city         VARCHAR(100),
    address_postal_code  VARCHAR(20),
    address_country      VARCHAR(100),
    expected_delivery_date DATETIME(6),
    return_requested     TINYINT(1)    NOT NULL DEFAULT 0,
    exchange_requested   TINYINT(1)    NOT NULL DEFAULT 0,
    user_id              BIGINT        NOT NULL,
    placed_at            DATETIME(6),
    updated_at           DATETIME(6),
    CONSTRAINT fk_order_user FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Order Items
CREATE TABLE IF NOT EXISTS order_items (
    id                BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id          BIGINT        NOT NULL,
    product_id        BIGINT        NOT NULL,
    product_name      VARCHAR(255),
    product_image_url VARCHAR(500),
    category_name     VARCHAR(100),
    unit_price        DECIMAL(10,2) NOT NULL,
    quantity          INT           NOT NULL,
    line_total        DECIMAL(10,2) NOT NULL,
    CONSTRAINT fk_orderitem_order   FOREIGN KEY (order_id)   REFERENCES orders(id)   ON DELETE CASCADE,
    CONSTRAINT fk_orderitem_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Cart Items
CREATE TABLE IF NOT EXISTS cart_items (
    id          BIGINT    NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT    NOT NULL,
    product_id  BIGINT    NOT NULL,
    quantity    INT       NOT NULL DEFAULT 1,
    updated_at  DATETIME(6),
    UNIQUE KEY uq_cart_user_product (user_id, product_id),
    CONSTRAINT fk_cart_user    FOREIGN KEY (user_id)    REFERENCES users(id)    ON DELETE CASCADE,
    CONSTRAINT fk_cart_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Wishlist Items
CREATE TABLE IF NOT EXISTS wishlist_items (
    id          BIGINT    NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT    NOT NULL,
    product_id  BIGINT    NOT NULL,
    added_at    DATETIME(6),
    UNIQUE KEY uq_wishlist_user_product (user_id, product_id),
    CONSTRAINT fk_wish_user    FOREIGN KEY (user_id)    REFERENCES users(id)    ON DELETE CASCADE,
    CONSTRAINT fk_wish_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 3. Seed Data
-- ============================================================

-- Categories
INSERT IGNORE INTO categories (name, description, image_url) VALUES
('Fashion',           'Clothing and apparel for all styles',       'Assets/Products/Fashion/fashion1.avif'),
('Jewelry',           'Rings, necklaces, bracelets and more',      'Assets/Products/Jewelry/jewelry1.avif'),
('Watches',           'Luxury and casual watches for every wrist', 'Assets/Products/Watches/watches1.avif'),
('Bags',              'Handbags, backpacks and travel bags',        'Assets/Products/bags/bag1.avif'),
('Footwear',          'Shoes, sandals and boots',                  'Assets/Products/Footwear/footwear1.avif'),
('Beauty & Fragrance','Perfumes, skincare and beauty essentials',  'Assets/Products/Beauty & Fragrance/fregrance1.avif'),
('Audio & Electronics','Headphones, speakers and gadgets',         'Assets/Products/Audio & Electronics/audio1.webp'),
('Gadgets',           'Smart devices and tech accessories',        'Assets/Products/Gadgets/gadgets4.webp'),
('Books',             'Fiction, non-fiction and educational books','Assets/Products/Books/books1.webp');

-- Admin user  (password = Admin@123  — BCrypt hash)
INSERT IGNORE INTO users (full_name, email, password, role) VALUES
('Admin', 'admin@9tails.com',
 '$2a$12$zQk1EyD7Nf5lzBkkVlWFvOhBFIHdLqzCBfnVOg7S1SYvPkMH7XoGC',
 'ADMIN');

-- Sample products – Fashion
INSERT IGNORE INTO products (name, description, price, original_price, stock, image_url, rating, review_count, active, category_id)
SELECT 'Classic White Kurta',       'Premium cotton kurta, perfect for casual and festive occasions.',       899.00,  1299.00, 50, 'Assets/Products/Fashion/fashion1.avif', 4.5, 128, 1, c.id FROM categories c WHERE c.name='Fashion';
INSERT IGNORE INTO products (name, description, price, original_price, stock, image_url, rating, review_count, active, category_id)
SELECT 'Printed Floral Dress',      'Lightweight summer dress with vibrant floral print.',                  1199.00, 1799.00, 35, 'Assets/Products/Fashion/fashion2.avif', 4.3, 95,  1, c.id FROM categories c WHERE c.name='Fashion';
INSERT IGNORE INTO products (name, description, price, original_price, stock, image_url, rating, review_count, active, category_id)
SELECT 'Slim Fit Denim Jacket',     'Versatile denim jacket, great for layering any outfit.',               1899.00, 2499.00, 28, 'Assets/Products/Fashion/fashion3.avif', 4.7, 210, 1, c.id FROM categories c WHERE c.name='Fashion';

-- Sample products – Jewelry
INSERT IGNORE INTO products (name, description, price, original_price, stock, image_url, rating, review_count, active, category_id)
SELECT 'Gold-plated Chain Necklace','Elegant 18k gold-plated necklace for everyday elegance.',              599.00,  899.00,  60, 'Assets/Products/Jewelry/jewelry1.avif', 4.6, 182, 1, c.id FROM categories c WHERE c.name='Jewelry';
INSERT IGNORE INTO products (name, description, price, original_price, stock, image_url, rating, review_count, active, category_id)
SELECT 'Pearl Drop Earrings',       'Classic pearl drop earrings to complement any outfit.',                449.00,  699.00,  45, 'Assets/Products/Jewelry/Jewelry2.avif', 4.4, 143, 1, c.id FROM categories c WHERE c.name='Jewelry';

-- Sample products – Watches
INSERT IGNORE INTO products (name, description, price, original_price, stock, image_url, rating, review_count, active, category_id)
SELECT 'Minimalist Leather Watch',  'Clean-dial leather strap watch for the modern professional.',          2499.00, 3499.00, 22, 'Assets/Products/Watches/watches1.avif', 4.8, 320, 1, c.id FROM categories c WHERE c.name='Watches';
INSERT IGNORE INTO products (name, description, price, original_price, stock, image_url, rating, review_count, active, category_id)
SELECT 'Sports Chronograph',        'Water-resistant sports watch with chronograph functionality.',          3299.00, 4999.00, 18, 'Assets/Products/Watches/watches2.avif', 4.6, 265, 1, c.id FROM categories c WHERE c.name='Watches';

-- Sample products – Bags
INSERT IGNORE INTO products (name, description, price, original_price, stock, image_url, rating, review_count, active, category_id)
SELECT 'Leather Tote Bag',          'Spacious genuine leather tote perfect for office and weekend use.',    2999.00, 3999.00, 30, 'Assets/Products/bags/bag1.avif',        4.7, 198, 1, c.id FROM categories c WHERE c.name='Bags';
INSERT IGNORE INTO products (name, description, price, original_price, stock, image_url, rating, review_count, active, category_id)
SELECT 'Mini Crossbody Bag',        'Compact crossbody bag with adjustable strap, ideal for outings.',     1299.00, 1799.00, 42, 'Assets/Products/bags/bag2.avif',        4.5, 156, 1, c.id FROM categories c WHERE c.name='Bags';

-- Sample products – Audio
INSERT IGNORE INTO products (name, description, price, original_price, stock, image_url, rating, review_count, active, category_id)
SELECT 'Wireless Noise-Cancelling Headphones', 'Premium ANC headphones with 30-hour battery life.', 4999.00, 6999.00, 15, 'Assets/Products/Audio & Electronics/audio1.webp', 4.9, 412, 1, c.id FROM categories c WHERE c.name='Audio & Electronics';
INSERT IGNORE INTO products (name, description, price, original_price, stock, image_url, rating, review_count, active, category_id)
SELECT 'Bluetooth Earbuds TWS',     'True wireless earbuds with active noise cancellation.',                2499.00, 3499.00, 25, 'Assets/Products/Audio & Electronics/audio2.webp', 4.6, 289, 1, c.id FROM categories c WHERE c.name='Audio & Electronics';

-- Sample products – Books
INSERT IGNORE INTO products (name, description, price, original_price, stock, image_url, rating, review_count, active, category_id)
SELECT 'Atomic Habits',             'James Clear - Build good habits and break bad ones.',                  399.00,  499.00,  100,'Assets/Products/Books/books1.webp',     4.9, 1250, 1, c.id FROM categories c WHERE c.name='Books';
INSERT IGNORE INTO products (name, description, price, original_price, stock, image_url, rating, review_count, active, category_id)
SELECT 'The Alchemist',             'Paulo Coelho - A magical story about following your dreams.',          299.00,  399.00,  80, 'Assets/Products/Books/book2.webp',      4.8, 980,  1, c.id FROM categories c WHERE c.name='Books';

-- ============================================================
-- 4. Verify
-- ============================================================
SELECT 'Categories' AS tbl, COUNT(*) AS rows FROM categories
UNION ALL SELECT 'Products',  COUNT(*) FROM products
UNION ALL SELECT 'Users',     COUNT(*) FROM users;
