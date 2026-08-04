-- 修复 mo_flash_sale_order 表不存在
CREATE TABLE IF NOT EXISTS mo_flash_sale_order (
    id BIGINT PRIMARY KEY,
    flash_sale_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    order_id BIGINT,
    quantity INT DEFAULT 1,
    create_time DATETIME NOT NULL,
    INDEX idx_fso_flash_sale_id (flash_sale_id),
    INDEX idx_fso_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
