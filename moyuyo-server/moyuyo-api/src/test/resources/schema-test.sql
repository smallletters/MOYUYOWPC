DROP TABLE IF EXISTS mo_user;
CREATE TABLE mo_user (
    id BIGINT PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(100),
    avatar VARCHAR(500),
    phone VARCHAR(50),
    birthday DATE,
    points INT DEFAULT 0,
    email_verified BIT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    magic_link_token VARCHAR(500),
    magic_link_expire DATETIME,
    two_factor_secret VARCHAR(255),
    two_factor_enabled BIT DEFAULT 0,
    last_login_time DATETIME,
    create_time DATETIME NOT NULL,
    update_time DATETIME
);

DROP TABLE IF EXISTS mo_member;
CREATE TABLE mo_member (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    level VARCHAR(20) DEFAULT 'NORMAL',
    growth_value INT DEFAULT 0,
    create_time DATETIME NOT NULL,
    update_time DATETIME
);

DROP TABLE IF EXISTS mo_wallet;
CREATE TABLE mo_wallet (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    balance DECIMAL(10,2) DEFAULT 0,
    total_recharged DECIMAL(10,2) DEFAULT 0,
    total_spent DECIMAL(10,2) DEFAULT 0,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    create_time DATETIME NOT NULL,
    update_time DATETIME
);

DROP TABLE IF EXISTS mo_category;
CREATE TABLE mo_category (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    parent_id BIGINT DEFAULT 0,
    image VARCHAR(500),
    sort_order INT DEFAULT 0,
    create_time DATETIME NOT NULL
);

DROP TABLE IF EXISTS mo_product;
CREATE TABLE mo_product (
    id BIGINT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    category_id BIGINT,
    brand_ip_id BIGINT,
    description TEXT,
    detail TEXT,
    original_price DECIMAL(10,2),
    regular_price DECIMAL(10,2),
    sale_price DECIMAL(10,2),
    price DECIMAL(10,2),
    main_image VARCHAR(500),
    stock INT DEFAULT 0,
    on_sale BIT DEFAULT 1,
    create_time DATETIME NOT NULL,
    update_time DATETIME
);

DROP TABLE IF EXISTS mo_product_sku;
CREATE TABLE mo_product_sku (
    id BIGINT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    sku_code VARCHAR(100),
    spec VARCHAR(500),
    price DECIMAL(10,2),
    stock INT DEFAULT 0,
    create_time DATETIME NOT NULL
);

DROP TABLE IF EXISTS mo_product_image;
CREATE TABLE mo_product_image (
    id BIGINT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    url VARCHAR(500) NOT NULL,
    sort_order INT DEFAULT 0,
    create_time DATETIME NOT NULL
);

DROP TABLE IF EXISTS mo_order;
CREATE TABLE mo_order (
    id BIGINT PRIMARY KEY,
    order_no VARCHAR(50) NOT NULL,
    user_id BIGINT NOT NULL,
    address_id BIGINT,
    goods_amount DECIMAL(10,2) DEFAULT 0,
    freight DECIMAL(10,2) DEFAULT 0,
    tax_amount DECIMAL(10,2) DEFAULT 0,
    coupon_discount DECIMAL(10,2) DEFAULT 0,
    points_discount DECIMAL(10,2) DEFAULT 0,
    pay_amount DECIMAL(10,2) DEFAULT 0,
    currency VARCHAR(10) DEFAULT 'USD',
    status VARCHAR(20) DEFAULT 'PENDING_PAY',
    pay_channel VARCHAR(20),
    pay_transaction_id VARCHAR(255),
    woo_order_id BIGINT,
    sync_status INT DEFAULT 0,
    sync_retry_count INT DEFAULT 0,
    sync_last_time DATETIME,
    tracking_number VARCHAR(100),
    shipping_carrier VARCHAR(100),
    receiver_name VARCHAR(100),
    receiver_phone VARCHAR(50),
    receiver_address VARCHAR(500),
    receiver_zip VARCHAR(20),
    delete_status INT DEFAULT 0,
    create_time DATETIME NOT NULL,
    paid_at DATETIME,
    cancel_time DATETIME,
    cancel_reason VARCHAR(500),
    deliver_time DATETIME,
    received_time DATETIME,
    update_time DATETIME
);

DROP TABLE IF EXISTS mo_order_item;
CREATE TABLE mo_order_item (
    id BIGINT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT,
    sku_id BIGINT,
    product_name VARCHAR(255),
    sku_spec VARCHAR(500),
    main_image VARCHAR(500),
    price DECIMAL(10,2),
    quantity INT,
    subtotal DECIMAL(10,2),
    create_time DATETIME NOT NULL
);

DROP TABLE IF EXISTS mo_address;
CREATE TABLE mo_address (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    receiver_name VARCHAR(100) NOT NULL,
    receiver_phone VARCHAR(50) NOT NULL,
    country VARCHAR(50) DEFAULT 'US',
    state VARCHAR(100),
    city VARCHAR(100),
    district VARCHAR(100),
    address_line VARCHAR(500) NOT NULL,
    zip_code VARCHAR(20),
    is_default BIT DEFAULT 0,
    label VARCHAR(50),
    create_time DATETIME NOT NULL,
    update_time DATETIME
);

DROP TABLE IF EXISTS mo_cart;
CREATE TABLE mo_cart (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    sku_id BIGINT,
    product_name VARCHAR(255),
    main_image VARCHAR(500),
    price DECIMAL(10,2),
    quantity INT NOT NULL,
    selected BIT DEFAULT 1,
    create_time DATETIME NOT NULL,
    update_time DATETIME
);

DROP TABLE IF EXISTS mo_coupon;
CREATE TABLE mo_coupon (
    id BIGINT PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100),
    description VARCHAR(500),
    type VARCHAR(20) NOT NULL,
    discount_value DECIMAL(10,2),
    min_order_amount DECIMAL(10,2) DEFAULT 0,
    max_discount_amount DECIMAL(10,2),
    total_count INT,
    claimed_count INT DEFAULT 0,
    used_count INT DEFAULT 0,
    start_time DATETIME,
    end_time DATETIME,
    active BIT DEFAULT 1,
    create_time DATETIME NOT NULL,
    update_time DATETIME
);

DROP TABLE IF EXISTS mo_user_coupon;
CREATE TABLE mo_user_coupon (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    coupon_id BIGINT NOT NULL,
    status VARCHAR(20) DEFAULT 'UNUSED',
    used_time DATETIME,
    used_order_id BIGINT,
    create_time DATETIME NOT NULL
);

DROP TABLE IF EXISTS mo_points_log;
CREATE TABLE mo_points_log (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    change_value INT NOT NULL,
    type VARCHAR(50),
    biz_no VARCHAR(100),
    remark VARCHAR(500),
    deleted INT DEFAULT 0,
    created_at DATETIME NOT NULL
);

DROP TABLE IF EXISTS mo_logistics;
CREATE TABLE mo_logistics (
    id BIGINT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    carrier VARCHAR(100),
    tracking_number VARCHAR(200),
    traces TEXT,
    shipped_at DATETIME,
    received_at DATETIME
);

DROP TABLE IF EXISTS mo_refund;
CREATE TABLE mo_refund (
    id BIGINT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    refund_no VARCHAR(50) NOT NULL,
    type VARCHAR(20),
    amount DECIMAL(10,2),
    reason VARCHAR(500),
    description TEXT,
    images TEXT,
    status VARCHAR(20) DEFAULT 'PENDING',
    woo_refund_id BIGINT,
    create_time DATETIME NOT NULL,
    complete_time DATETIME
);

DROP TABLE IF EXISTS mo_community_post;
CREATE TABLE mo_community_post (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    content TEXT,
    images TEXT,
    topic VARCHAR(100),
    likes INT DEFAULT 0,
    comments INT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'PUBLISHED',
    deleted INT DEFAULT 0,
    create_time DATETIME NOT NULL,
    update_time DATETIME
);

DROP TABLE IF EXISTS mo_community_comment;
CREATE TABLE mo_community_comment (
    id BIGINT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    parent_id BIGINT,
    content TEXT,
    deleted INT DEFAULT 0,
    create_time DATETIME NOT NULL
);

DROP TABLE IF EXISTS mo_community_like;
CREATE TABLE mo_community_like (
    id BIGINT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL
);

DROP TABLE IF EXISTS mo_flash_sale;
CREATE TABLE mo_flash_sale (
    id BIGINT PRIMARY KEY,
    name VARCHAR(200),
    product_id BIGINT,
    sku_id BIGINT,
    flash_price DECIMAL(10,2),
    original_price DECIMAL(10,2),
    total_stock INT DEFAULT 0,
    sold_stock INT DEFAULT 0,
    limit_per_user INT,
    start_time DATETIME,
    end_time DATETIME,
    active BIT DEFAULT 1,
    create_time DATETIME NOT NULL,
    update_time DATETIME
);

DROP TABLE IF EXISTS mo_flash_sale_order;
CREATE TABLE mo_flash_sale_order (
    id BIGINT PRIMARY KEY,
    flash_sale_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    order_id BIGINT,
    quantity INT DEFAULT 1,
    create_time DATETIME NOT NULL
);

-- ===== Admin 模块表（测试用） =====

DROP TABLE IF EXISTS mo_admin_user;
CREATE TABLE mo_admin_user (
    id BIGINT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(100),
    email VARCHAR(255),
    role VARCHAR(50) DEFAULT 'OPERATOR',
    status VARCHAR(20) DEFAULT 'ACTIVE',
    last_login_time DATETIME,
    created_at DATETIME,
    updated_at DATETIME
);

DROP TABLE IF EXISTS mo_admin_role;
CREATE TABLE mo_admin_role (
    id BIGINT PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(255),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at DATETIME,
    updated_at DATETIME
);

DROP TABLE IF EXISTS mo_admin_permission;
CREATE TABLE mo_admin_permission (
    id BIGINT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    permission_code VARCHAR(64) NOT NULL,
    created_at DATETIME,
    updated_at DATETIME
);

DROP TABLE IF EXISTS mo_push_record;
CREATE TABLE mo_push_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(200),
    summary TEXT,
    type VARCHAR(32),
    target_url VARCHAR(500),
    target_user_count INT DEFAULT 0,
    status VARCHAR(16) DEFAULT 'DRAFT',
    sent_count INT DEFAULT 0,
    delivered_count INT DEFAULT 0,
    click_count INT DEFAULT 0,
    scheduled_time DATETIME,
    sent_time DATETIME,
    created_at DATETIME NOT NULL,
    updated_at DATETIME
);

DROP TABLE IF EXISTS mo_cms_content;
CREATE TABLE mo_cms_content (
    id BIGINT PRIMARY KEY,
    title VARCHAR(200),
    type VARCHAR(32),
    content TEXT,
    image_url VARCHAR(500),
    link_url VARCHAR(500),
    location VARCHAR(100),
    status VARCHAR(16) DEFAULT 'ACTIVE',
    sort_order INT DEFAULT 0,
    start_time DATETIME,
    end_time DATETIME,
    ctr DECIMAL(5,2),
    created_at DATETIME NOT NULL,
    updated_at DATETIME
);

DROP TABLE IF EXISTS mo_sensitive_word;
CREATE TABLE mo_sensitive_word (
    id BIGINT PRIMARY KEY,
    word VARCHAR(100) NOT NULL,
    category VARCHAR(32),
    level VARCHAR(16) DEFAULT 'MEDIUM',
    status VARCHAR(16) DEFAULT 'ENABLED',
    hit_count INT DEFAULT 0,
    create_time DATETIME NOT NULL,
    update_time DATETIME
);

DROP TABLE IF EXISTS mo_system_config;
CREATE TABLE mo_system_config (
    id BIGINT PRIMARY KEY,
    config_key VARCHAR(100) NOT NULL UNIQUE,
    config_value TEXT,
    config_type VARCHAR(32),
    label VARCHAR(200),
    sort_order INT DEFAULT 0,
    create_time DATETIME NOT NULL,
    update_time DATETIME
);

DROP TABLE IF EXISTS mo_finance_record;
CREATE TABLE mo_finance_record (
    id BIGINT PRIMARY KEY,
    type VARCHAR(32),
    amount DECIMAL(10,2),
    status VARCHAR(16),
    remark VARCHAR(500),
    create_time DATETIME NOT NULL
);

DROP TABLE IF EXISTS mo_settlement;
CREATE TABLE mo_settlement (
    id BIGINT PRIMARY KEY,
    settlement_no VARCHAR(64),
    amount DECIMAL(10,2),
    status VARCHAR(16),
    create_time DATETIME NOT NULL
);

DROP TABLE IF EXISTS mo_app_version;
CREATE TABLE mo_app_version (id BIGINT PRIMARY KEY, app_type VARCHAR(20) NOT NULL, version_code INT NOT NULL, version_name VARCHAR(32) NOT NULL, update_title VARCHAR(200), update_desc TEXT, download_url VARCHAR(500), force_update BIT DEFAULT 0, file_size VARCHAR(32), status VARCHAR(16) DEFAULT 'DRAFT', publish_time DATETIME, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP);

DROP TABLE IF EXISTS mo_audit_log;
CREATE TABLE mo_audit_log (id BIGINT PRIMARY KEY, operator_id BIGINT, operator_name VARCHAR(64), action VARCHAR(64) NOT NULL, module VARCHAR(64) NOT NULL, resource_id VARCHAR(64), detail VARCHAR(500), ip VARCHAR(64), user_agent VARCHAR(500), result VARCHAR(8) DEFAULT 'SUCCESS', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP);

DROP TABLE IF EXISTS mo_knowledge_base;
CREATE TABLE mo_knowledge_base (id BIGINT PRIMARY KEY, category VARCHAR(32) NOT NULL, title VARCHAR(200) NOT NULL, content TEXT NOT NULL, tags VARCHAR(500), sort_order INT DEFAULT 0, view_count INT DEFAULT 0, status VARCHAR(16) DEFAULT 'DRAFT', create_by BIGINT, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP);

DROP TABLE IF EXISTS mo_satisfaction_survey;
CREATE TABLE mo_satisfaction_survey (id BIGINT PRIMARY KEY, ticket_id BIGINT, order_id BIGINT, user_id BIGINT NOT NULL, score TINYINT NOT NULL, category VARCHAR(32), comment TEXT, dimensions_json TEXT, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP);

DROP TABLE IF EXISTS mo_risk_rule;
CREATE TABLE mo_risk_rule (id BIGINT PRIMARY KEY, rule_code VARCHAR(64) NOT NULL UNIQUE, rule_name VARCHAR(128) NOT NULL, rule_type VARCHAR(32) NOT NULL, condition_json TEXT NOT NULL, action VARCHAR(32) NOT NULL, priority INT DEFAULT 0, enabled BIT DEFAULT 1, description VARCHAR(500), create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP);

DROP TABLE IF EXISTS mo_risk_event;
CREATE TABLE mo_risk_event (id BIGINT PRIMARY KEY, rule_id BIGINT, rule_code VARCHAR(64), user_id BIGINT, event_type VARCHAR(32) NOT NULL, business_id VARCHAR(64), risk_level VARCHAR(16) NOT NULL, action_taken VARCHAR(32), detail_json TEXT, status VARCHAR(16) DEFAULT 'PENDING', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP);

DROP TABLE IF EXISTS mo_gdpr_consent;
CREATE TABLE mo_gdpr_consent (id BIGINT PRIMARY KEY, user_id BIGINT NOT NULL, consent_type VARCHAR(32) NOT NULL, granted BIT NOT NULL DEFAULT 1, ip VARCHAR(64), user_agent VARCHAR(500), create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP);

DROP TABLE IF EXISTS mo_gdpr_request;
CREATE TABLE mo_gdpr_request (id BIGINT PRIMARY KEY, user_id BIGINT NOT NULL, request_type VARCHAR(32) NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'PENDING', detail_json TEXT, processed_by BIGINT, processed_time DATETIME, response_note VARCHAR(1000), expire_time DATETIME, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP);

DROP TABLE IF EXISTS mo_price_strategy;
CREATE TABLE mo_price_strategy (id BIGINT PRIMARY KEY, strategy_name VARCHAR(128) NOT NULL, strategy_type VARCHAR(32) NOT NULL, apply_to VARCHAR(16) NOT NULL, apply_value BIGINT, discount_type VARCHAR(16) NOT NULL, discount_value DECIMAL(10,2) NOT NULL, min_amount DECIMAL(10,2), max_discount DECIMAL(10,2), start_time DATETIME, end_time DATETIME, priority INT DEFAULT 0, enabled BIT DEFAULT 1, create_by BIGINT, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP);

DROP TABLE IF EXISTS mo_sms_record;
CREATE TABLE mo_sms_record (id BIGINT PRIMARY KEY, phone VARCHAR(20) NOT NULL, template_code VARCHAR(64) NOT NULL, content VARCHAR(500) NOT NULL, params_json TEXT, channel VARCHAR(32) DEFAULT 'ALIYUN', status VARCHAR(16) DEFAULT 'PENDING', fail_reason VARCHAR(500), send_time DATETIME, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP);
