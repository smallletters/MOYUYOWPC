-- ============================================================
-- V20260717_02__demo_seed_data.sql
-- 演示种子数据：用户 / 商品 / 订单 / 物流 / 退款 / 会员 / 积分
-- ============================================================

-- 1. 用户（BCrypt 密码均为 "123456"）
INSERT INTO mo_user (id, email, password_hash, nickname, country, locale, timezone, phone, status, points, email_verified, marketing_opt_in, created_at) VALUES
(180000001, 'alice@example.com',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Alice Johnson', 'US', 'en_US', 'America/New_York',    '+12025550001', 1, 520,  1, 1, '2026-01-15 10:30:00'),
(180000002, 'bob@example.com',    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Bob Smith',     'US', 'en_US', 'America/Los_Angeles', '+12025550002', 1, 1200, 1, 0, '2026-02-20 14:00:00'),
(180000003, 'charlie@example.co.uk', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Charlie Brown', 'GB', 'en_GB', 'Europe/London',       '+44205550001', 1, 300,  0, 1, '2026-03-05 09:15:00'),
(180000004, 'diana@example.de',   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Diana Muller',  'DE', 'de_DE', 'Europe/Berlin',       '+49305550001', 1, 880,  1, 1, '2026-03-10 16:45:00'),
(180000005, 'eva@example.fr',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Eva Laurent',   'FR', 'fr_FR', 'Europe/Paris',        '+33155000001', 1, 150,  0, 0, '2026-04-01 11:30:00'),
(180000006, 'frank@example.jp',   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Frank Tanaka',  'JP', 'ja_JP', 'Asia/Tokyo',          '+81355000001', 1, 2100, 1, 1, '2026-04-12 08:00:00'),
(180000007, 'grace@example.au',   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Grace Lee',     'AU', 'en_AU', 'Australia/Sydney',    '+61255000001', 0, 0,    0, 0, '2026-05-01 22:10:00'),
(180000008, 'henry@example.ca',   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Henry Wilson',  'CA', 'en_CA', 'America/Toronto',     '+14165550001', 1, 420,  1, 1, '2026-05-20 13:25:00'),
(180000009, 'iris@example.sg',    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Iris Tan',      'SG', 'en_SG', 'Asia/Singapore',      '+6565000001',  1, 180,  0, 1, '2026-06-01 10:00:00'),
(180000010, 'jack@example.com',   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Jack Davis',    'US', 'en_US', 'America/Chicago',     '+13125550001', 1, 760,  1, 0, '2026-06-15 15:30:00');

-- 2. 会员等级
INSERT INTO mo_member (id, user_id, level, growth_value, create_time) VALUES
(181000001, 180000001, 'SILVER',  1200, '2026-01-15 10:30:00'),
(181000002, 180000002, 'GOLD',    3500, '2026-02-20 14:00:00'),
(181000003, 180000003, 'NORMAL',  400,  '2026-03-05 09:15:00'),
(181000004, 180000004, 'GOLD',    2800, '2026-03-10 16:45:00'),
(181000005, 180000005, 'NORMAL',  200,  '2026-04-01 11:30:00'),
(181000006, 180000006, 'PLATINUM',5800, '2026-04-12 08:00:00'),
(181000007, 180000007, 'NORMAL',  0,    '2026-05-01 22:10:00'),
(181000008, 180000008, 'SILVER',  1500, '2026-05-20 13:25:00'),
(181000009, 180000009, 'NORMAL',  600,  '2026-06-01 10:00:00'),
(181000010, 180000010, 'SILVER',  1800, '2026-06-15 15:30:00');

-- 3. 收货地址
INSERT INTO mo_address (id, user_id, receiver, phone, country, province, city, district, detail, zip_code, tag, is_default) VALUES
(182000001, 180000001, 'Alice Johnson', '+12025550001', 'US', 'NY', 'New York', 'Manhattan', '350 Fifth Avenue, Apt 12A', '10001', 'HOME', 1),
(182000002, 180000002, 'Bob Smith',    '+12025550002', 'US', 'CA', 'Los Angeles', 'Hollywood', '100 Sunset Blvd, Suite 200', '90028', 'HOME', 1),
(182000003, 180000004, 'Diana Muller', '+49305550001', 'DE', 'Berlin', 'Berlin', 'Mitte', 'Unter den Linden 15', '10117', 'HOME', 1),
(182000004, 180000006, 'Frank Tanaka', '+81355000001', 'JP', 'Tokyo', 'Shibuya', 'Shibuya-ku', '2-1-1 Dogenzaka', '150-0043', 'HOME', 1),
(182000005, 180000008, 'Henry Wilson', '+14165550001', 'CA', 'ON', 'Toronto', 'Downtown', '100 King Street West', 'M5X 1A9', 'COMPANY', 1);

-- 4. 商品 SPU
INSERT INTO mo_product (id, spu_code, name, category_id, brand_id, price, original_price, stock, stock_status, sales, detail, on_sale, create_time) VALUES
(183000001, 'SPU20260001', 'Premium Pet Shampoo 500ml',          101, 1, 18.99,  24.99,  500, 'IN_STOCK',  320,  'Gentle formula for all coat types, enriched with oatmeal and aloe vera.', 1, '2026-01-01 00:00:00'),
(183000002, 'SPU20260002', 'Dog Winter Coat Waterproof',         201, 1, 39.99,  54.99,  200, 'IN_STOCK',  180,  'Warm waterproof winter coat with reflective strips for night safety.',    1, '2026-01-05 00:00:00'),
(183000003, 'SPU20260003', 'Orthopedic Pet Bed Large',           3,   2, 89.99,  119.99, 80,  'IN_STOCK',  95,   'Memory foam orthopedic bed with washable cover, 36x24 inches.',           1, '2026-01-10 00:00:00'),
(183000004, 'SPU20260004', 'Interactive Treat Ball Toy',         4,   3, 14.99,  19.99,  1000,'IN_STOCK',  560,  'Dispenses treats as dog plays, adjustable difficulty levels.',            1, '2026-01-15 00:00:00'),
(183000005, 'SPU20260005', 'Automatic Pet Feeder WiFi',          5,   3, 79.99,  99.99,  150, 'IN_STOCK',  230,  'Smart WiFi feeder with portion control, schedule feeding via app.',       1, '2026-02-01 00:00:00'),
(183000006, 'SPU20260006', 'Cat Harness & Leash Set',            201, 5, 22.99,  29.99,  350, 'IN_STOCK',  410,  'Breathable mesh harness with bungee leash, adjustable straps.',           1, '2026-02-05 00:00:00'),
(183000007, 'SPU20260007', 'Dental Chew Sticks 30-pack',         104, 2, 12.99,  16.99,  800, 'IN_STOCK',  670,  'Natural dental chews that reduce plaque and tartar.',                     1, '2026-02-10 00:00:00'),
(183000008, 'SPU20260008', 'Portable Water Bottle for Pets',     6,   3, 9.99,   13.99,  600, 'IN_STOCK',  340,  'Leak-proof 500ml bottle with built-in drinking bowl.',                    1, '2026-03-01 00:00:00'),
(183000009, 'SPU20260009', 'Ear Cleaning Wipes 50-pack',         103, 1, 8.99,   11.99,  450, 'IN_STOCK',  280,  'Gentle ear cleaning wipes with aloe, alcohol-free formula.',              1, '2026-03-05 00:00:00'),
(183000010, 'SPU20260010', 'Elevated Dog Bowl Set',              5,   4, 34.99,  44.99,  250, 'IN_STOCK',  195,  'Stainless steel bowls with bamboo stand, adjustable height.',             1, '2026-03-10 00:00:00');

-- 5. 商品 SKU
INSERT INTO mo_product_sku (id, product_id, sku_code, spec, price, stock, sales) VALUES
(184000001, 183000001, 'SKU2026001001', '500ml',   18.99,  500,  320),
(184000002, 183000002, 'SKU2026002001', 'M Red',   39.99,  80,   70),
(184000003, 183000002, 'SKU2026002002', 'L Red',   42.99,  60,   55),
(184000004, 183000002, 'SKU2026002003', 'XL Red',  45.99,  60,   55),
(184000005, 183000003, 'SKU2026003001', 'Large',   89.99,  80,   95),
(184000006, 183000004, 'SKU2026004001', 'Standard',14.99,  1000, 560),
(184000007, 183000005, 'SKU2026005001', 'Standard',79.99,  150,  230),
(184000008, 183000006, 'SKU2026006001', 'M Blue',  22.99,  150,  180),
(184000009, 183000006, 'SKU2026006002', 'L Blue',  24.99,  200,  230),
(184000010, 183000007, 'SKU2026007001', '30-pack', 12.99,  800,  670),
(184000011, 183000008, 'SKU2026008001', '500ml',   9.99,   600,  340),
(184000012, 183000009, 'SKU2026009001', '50-pack', 8.99,   450,  280),
(184000013, 183000010, 'SKU2026010001', 'Set',     34.99,  250,  195);

-- 6. 商品图片
INSERT INTO mo_product_image (id, product_id, url, sort) VALUES
(185000001, 183000001, 'https://images.unsplash.com/photo-1583947215259-38e31be8751f?w=400', 1),
(185000002, 183000002, 'https://images.unsplash.com/photo-1601758228041-f3b2795255f1?w=400', 1),
(185000003, 183000003, 'https://images.unsplash.com/photo-1545249390-6bdfa286032f?w=400', 1),
(185000004, 183000004, 'https://images.unsplash.com/photo-1576201836106-db1758d239f0?w=400', 1),
(185000005, 183000005, 'https://images.unsplash.com/photo-1565708097481-e285a4bb4ff1?w=400', 1);

-- 7. 订单
INSERT INTO mo_order (id, order_no, user_id, address_id, goods_amount, freight, tax_amount, coupon_discount, points_discount, pay_amount, currency, status, pay_channel, create_time, paid_at) VALUES
(186000001, 'ORD20260701001', 180000001, 182000001, 58.98,  5.99,  7.79,  0,    0,    72.76,  'USD', 'COMPLETED',     'STRIPE',    '2026-07-01 10:30:00', '2026-07-01 10:31:00'),
(186000002, 'ORD20260702002', 180000002, 182000002, 89.99,  8.50,  9.80,  0,    0,    108.29, 'USD', 'PENDING_SHIP',  'PAYPAL',    '2026-07-02 14:20:00', '2026-07-02 14:22:00'),
(186000003, 'ORD20260703003', 180000004, 182000003, 33.98,  4.50,  6.46,  5.00, 0,    39.94,  'EUR', 'PENDING_SHIP',  'STRIPE',    '2026-07-03 09:15:00', '2026-07-03 09:16:00'),
(186000004, 'ORD20260705004', 180000006, 182000004, 79.99,  0,     8.00,  0,    0,    87.99,  'JPY', 'PENDING_PAY',   NULL,        '2026-07-05 16:45:00', NULL),
(186000005, 'ORD20260706005', 180000001, 182000001, 12.99,  3.99,  1.70,  0,    500,  13.68,  'USD', 'COMPLETED',     'APPLE_PAY', '2026-07-06 11:00:00', '2026-07-06 11:01:00'),
(186000006, 'ORD20260708006', 180000008, 182000005, 34.99,  5.50,  4.66,  0,    0,    45.15,  'CAD', 'COMPLETED',     'STRIPE',    '2026-07-08 13:30:00', '2026-07-08 13:31:00'),
(186000007, 'ORD20260710007', 180000004, 182000003, 44.97,  4.99,  7.50,  10.00,300,  37.46,  'EUR', 'COMPLETED',     'PAYPAL',    '2026-07-10 10:00:00', '2026-07-10 10:02:00'),
(186000008, 'ORD20260712008', 180000010, NULL,      22.99,  4.99,  2.80,  0,    0,    30.78,  'USD', 'CANCELLED',     NULL,        '2026-07-12 15:20:00', NULL),
(186000009, 'ORD20260714009', 180000003, NULL,      89.99,  9.50,  10.80, 0,    200,  99.29,  'GBP', 'COMPLETED',     'STRIPE',    '2026-07-14 12:00:00', '2026-07-14 12:01:00'),
(186000010, 'ORD20260716010', 180000002, 182000002, 31.98,  4.99,  3.70,  0,    0,    40.67,  'USD', 'PENDING_SHIP',  'APPLE_PAY', '2026-07-16 10:15:00', '2026-07-16 10:16:00');

-- 8. 订单明细
INSERT INTO mo_order_item (id, order_id, product_id, sku_id, product_name, sku_spec, price, quantity, subtotal) VALUES
(187000001, 186000001, 183000001, 184000001, 'Premium Pet Shampoo 500ml',          '500ml',   18.99,  2, 37.98),
(187000002, 186000001, 183000004, 184000006, 'Interactive Treat Ball Toy',         'Standard',14.99,  1, 14.99),
(187000003, 186000001, 183000009, 184000012, 'Ear Cleaning Wipes 50-pack',         '50-pack', 8.99,   1, 8.99),
(187000004, 186000002, 183000003, 184000005, 'Orthopedic Pet Bed Large',           'Large',   89.99,  1, 89.99),
(187000005, 186000003, 183000008, 184000011, 'Portable Water Bottle for Pets',     '500ml',   9.99,   2, 19.98),
(187000006, 186000003, 183000009, 184000012, 'Ear Cleaning Wipes 50-pack',         '50-pack', 8.99,   1, 8.99),
(187000007, 186000004, 183000005, 184000007, 'Automatic Pet Feeder WiFi',          'Standard',79.99,  1, 79.99),
(187000008, 186000005, 183000007, 184000010, 'Dental Chew Sticks 30-pack',         '30-pack', 12.99,  1, 12.99),
(187000009, 186000006, 183000010, 184000013, 'Elevated Dog Bowl Set',              'Set',     34.99,  1, 34.99),
(187000010, 186000007, 183000004, 184000006, 'Interactive Treat Ball Toy',         'Standard',14.99,  2, 29.98),
(187000011, 186000007, 183000007, 184000010, 'Dental Chew Sticks 30-pack',         '30-pack', 12.99,  1, 12.99),
(187000012, 186000008, 183000006, 184000008, 'Cat Harness & Leash Set',            'M Blue',  22.99,  1, 22.99),
(187000013, 186000009, 183000003, 184000005, 'Orthopedic Pet Bed Large',           'Large',   89.99,  1, 89.99),
(187000014, 186000010, 183000008, 184000011, 'Portable Water Bottle for Pets',     '500ml',   9.99,   2, 19.98),
(187000015, 186000010, 183000001, 184000001, 'Premium Pet Shampoo 500ml',          '500ml',   18.99,  1, 18.99);

-- 9. 支付记录
INSERT INTO mo_payment (id, order_id, pay_channel, transaction_id, amount, currency, status, paid_at, create_time) VALUES
(188000001, 186000001, 'STRIPE',    'ch_stripe_000001', 72.76,  'USD', 'SUCCESS', '2026-07-01 10:31:00', '2026-07-01 10:30:30'),
(188000002, 186000002, 'PAYPAL',    'pp_paypal_000002', 108.29, 'USD', 'SUCCESS', '2026-07-02 14:22:00', '2026-07-02 14:21:00'),
(188000003, 186000003, 'STRIPE',    'ch_stripe_000003', 39.94,  'EUR', 'SUCCESS', '2026-07-03 09:16:00', '2026-07-03 09:15:30'),
(188000004, 186000005, 'APPLE_PAY', 'ap_apple_000005',  13.68,  'USD', 'SUCCESS', '2026-07-06 11:01:00', '2026-07-06 11:00:30'),
(188000005, 186000006, 'STRIPE',    'ch_stripe_000006', 45.15,  'CAD', 'SUCCESS', '2026-07-08 13:31:00', '2026-07-08 13:30:30'),
(188000006, 186000007, 'PAYPAL',    'pp_paypal_000007', 37.46,  'EUR', 'SUCCESS', '2026-07-10 10:02:00', '2026-07-10 10:01:00'),
(188000007, 186000009, 'STRIPE',    'ch_stripe_000009', 99.29,  'GBP', 'SUCCESS', '2026-07-14 12:01:00', '2026-07-14 12:00:30'),
(188000008, 186000010, 'APPLE_PAY', 'ap_apple_000010',  40.67,  'USD', 'SUCCESS', '2026-07-16 10:16:00', '2026-07-16 10:15:30');

-- 10. 物流
INSERT INTO mo_logistics (id, order_id, carrier, tracking_number, shipped_at, received_at) VALUES
(189000001, 186000001, 'USPS', 'USPS9400100000000001', '2026-07-02 10:00:00', '2026-07-06 14:30:00'),
(189000002, 186000005, 'USPS', 'USPS9400100000000002', '2026-07-07 09:00:00', '2026-07-10 16:00:00'),
(189000003, 186000007, 'DHL',  'DHLDE0000000001',     '2026-07-11 11:00:00', '2026-07-15 10:30:00'),
(189000004, 186000006, 'UPS',  'UPS1Z0000000001',     '2026-07-09 14:00:00', '2026-07-14 11:00:00'),
(189000005, 186000002, 'FEDEX','FX000000000001',      '2026-07-03 10:00:00', NULL),
(189000006, 186000003, 'DHL',  'DHLDE0000000002',     '2026-07-04 09:30:00', NULL),
(189000007, 186000009, 'UPS',  'UPS1Z0000000002',     '2026-07-15 10:00:00', '2026-07-18 14:00:00'),
(189000008, 186000010, 'USPS', 'USPS9400100000000003', '2026-07-17 10:00:00', NULL);

-- 11. 退款
INSERT INTO mo_refund (id, order_id, refund_no, type, amount, reason, status, create_time, complete_time) VALUES
(190000001, 186000005, 'RF20260706001', 'REFUND_ONLY', 12.99, '商品破损',            'REFUNDED',  '2026-07-07 10:00:00', '2026-07-08 14:00:00'),
(190000002, 186000002, 'RF20260710002', 'REFUND_RETURN',89.99,'商品与描述不符',       'REFUNDING', '2026-07-10 16:00:00', NULL),
(190000003, 186000003, 'RF20260712003', 'REFUND_ONLY', 18.97, '重复下单',            'REFUNDED',  '2026-07-12 09:00:00', '2026-07-13 11:00:00');

-- 12. 积分流水
INSERT INTO mo_points_log (id, user_id, change_value, type, biz_no, remark, create_time) VALUES
(191000001, 180000001, 50,  'SIGN_IN',  NULL,             '签到奖励',          '2026-07-01 10:00:00'),
(191000002, 180000001, 200, 'ORDER',    'ORD20260701001', '订单完成',          '2026-07-06 14:30:00'),
(191000003, 180000002, 50,  'SIGN_IN',  NULL,             '签到奖励',          '2026-07-02 09:00:00'),
(191000004, 180000002, 300, 'ORDER',    'ORD20260702002', '订单完成',          '2026-07-02 14:22:00'),
(191000005, 180000004, 50,  'SIGN_IN',  NULL,             '签到奖励',          '2026-07-03 08:00:00'),
(191000006, 180000006, 100, 'SIGN_IN',  NULL,             '连续签到7天奖励',    '2026-07-05 09:00:00'),
(191000007, 180000004, -300,'ORDER',    'ORD20260710007', '订单使用积分抵扣',   '2026-07-10 10:00:00'),
(191000008, 180000001, -500,'ORDER',    'ORD20260706005', '订单使用积分抵扣',   '2026-07-06 11:00:00'),
(191000009, 180000008, 50,  'SIGN_IN',  NULL,             '签到奖励',          '2026-07-08 10:00:00'),
(191000010, 180000010, 50,  'SIGN_IN',  NULL,             '签到奖励',          '2026-07-12 10:00:00');

-- 13. 优惠券
INSERT INTO mo_coupon (id, name, type, amount, min_amount, start_time, end_time, total, remain, status, create_time) VALUES
(192000001, 'New Customer 10%',        'PERCENT', 10.00, 0,     '2026-01-01 00:00:00', '2026-12-31 23:59:59', 1000, 800, 1, '2026-01-01 00:00:00'),
(192000002, 'Summer Sale $5 Off',      'AMOUNT',  5.00,  30.00,'2026-07-01 00:00:00', '2026-08-31 23:59:59', 500,  350, 1, '2026-06-15 00:00:00'),
(192000003, 'VIP $20 Off',             'AMOUNT',  20.00, 100.00,'2026-01-01 00:00:00','2026-12-31 23:59:59', 200,  150, 1, '2026-01-01 00:00:00');

-- 14. 用户优惠券
INSERT INTO mo_user_coupon (id, user_id, coupon_id, status, receive_time, use_time, use_order_no) VALUES
(193000001, 180000001, 192000002, 'USED',   '2026-07-01 10:00:00', '2026-07-01 10:30:00', 'ORD20260701001'),
(193000002, 180000002, 192000002, 'UNUSED', '2026-07-02 10:00:00', NULL, NULL),
(193000003, 180000004, 192000002, 'USED',   '2026-07-10 09:00:00', '2026-07-10 10:00:00', 'ORD20260710007'),
(193000004, 180000006, 192000003, 'UNUSED', '2026-07-05 10:00:00', NULL, NULL),
(193000005, 180000010, 192000003, 'UNUSED', '2026-07-12 10:00:00', NULL, NULL);

-- 15. 消息中心
INSERT INTO mo_message (id, user_id, type, title, content, create_time) VALUES
(194000001, 180000001, 'ORDER',   '订单已发货',     '您的订单 ORD20260701001 已通过 USPS 发货',                     '2026-07-02 10:00:00'),
(194000002, 180000001, 'SYSTEM',  '欢迎回来',       'Alice, 欢迎回到 MOYUYO! 看看我们的新品吧',                    '2026-07-01 10:00:00'),
(194000003, 180000002, 'ORDER',   '订单已确认',     '您的订单 ORD20260702002 已确认，等待发货',                     '2026-07-02 14:30:00'),
(194000004, 180000004, 'ORDER',   '退款已完成',     '您的退款 RF20260712003 已完成，金额已原路返回',                 '2026-07-13 11:00:00'),
(194000005, 180000006, 'PROMO',   '限时促销',       '夏季大促！自动喂食器限时 8 折，不要错过',                      '2026-07-05 08:00:00'),
(194000006, 180000008, 'ORDER',   '订单已签收',     '您的订单 ORD20260708006 已签收，感谢购买',                     '2026-07-14 11:00:00');

-- 16. 管理员
INSERT INTO mo_admin_user (id, username, password, name, email, role, status, last_login_time, create_time) VALUES
(195000001, 'admin',    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Admin',    'admin@moyuyo.com',  'SUPER_ADMIN', 'ACTIVE',  '2026-07-18 08:00:00', '2026-01-01 00:00:00'),
(195000002, 'wang',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '小王',     'wang@moyuyo.com',   'OPERATOR',    'ACTIVE',  '2026-07-17 09:30:00', '2026-02-01 00:00:00'),
(195000003, 'li',       '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '小李',     'li@moyuyo.com',     'CUSTOMER_SVC','ACTIVE',  '2026-07-16 14:00:00', '2026-03-01 00:00:00'),
(195000004, 'zhang',    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '小张',     'zhang@moyuyo.com',  'FINANCE',     'ACTIVE',  '2026-07-15 10:00:00', '2026-04-01 00:00:00');

-- 17. 投诉/反馈
INSERT INTO mo_feedback (id, user_id, type, content, status, create_time) VALUES
(197000001, 180000001, 'COMPLAINT',  '客服响应太慢，等待超过30分钟',              'PENDING',   '2026-07-15 10:00:00'),
(197000002, 180000002, 'COMPLAINT',  '收到的商品包装破损',                        'PENDING',   '2026-07-16 14:30:00'),
(197000003, 180000003, 'SUGGESTION', '希望能增加更多宠物服饰颜色选择',            'CLOSED',    '2026-07-14 09:00:00'),
(197000004, 180000004, 'COMPLAINT',  '物流太慢，已经5天还没收到',                 'PROCESSING','2026-07-17 11:00:00'),
(197000005, 180000006, 'SUGGESTION', '网站搜索功能需要改善',                      'CLOSED',    '2026-07-13 16:00:00'),
(197000006, 180000008, 'COMPLAINT',  '退款申请一直未处理',                        'PROCESSING','2026-07-17 09:30:00');

-- 19. 直播间
INSERT INTO mo_live_room (id, name, cover_url, host_name, host_avatar, status, viewer_count, product_count, start_time, create_time) VALUES
(198000001, 'MOYUYO宠物洗护专场',     'https://images.unsplash.com/photo-1583947215259-38e31be8751f?w=400', '爱宠达人小王', 'https://i.pravatar.cc/100?u=host1', 'LIVE',     12580, 8,  '2026-07-18 10:00:00', '2026-07-18 09:00:00'),
(198000002, '宠物服饰秋冬新款发布',   'https://images.unsplash.com/photo-1601758228041-f3b2795255f1?w=400', '时尚宠物莉莉', 'https://i.pravatar.cc/100?u=host2', 'LIVE',     8560,  12, '2026-07-18 14:00:00', '2026-07-18 13:00:00'),
(198000003, '宠物玩具大测评',         'https://images.unsplash.com/photo-1576201836106-db1758d239f0?w=400', '宠物资深玩家', 'https://i.pravatar.cc/100?u=host3', 'SCHEDULED',0,     10, '2026-07-19 10:00:00', '2026-07-17 00:00:00'),
(198000004, '智能喂食器使用指南',     'https://images.unsplash.com/photo-1565708097481-e285a4bb4ff1?w=400', '科技宠物达人', 'https://i.pravatar.cc/100?u=host4', 'OFFLINE',  3200,  5,  '2026-07-17 15:00:00', '2026-07-16 00:00:00');

-- 20. 数据导出记录
INSERT INTO mo_data_export_request (id, user_id, export_id, status, create_time, complete_time) VALUES
(201000001, 195000001, 'EXP-20260701-001', 'COMPLETED',  '2026-07-01 10:00:00', '2026-07-01 10:05:00'),
(201000002, 195000001, 'EXP-20260701-002', 'COMPLETED',  '2026-07-02 14:00:00', '2026-07-02 14:08:00'),
(201000003, 195000002, 'EXP-20260703-001', 'PROCESSING', '2026-07-03 09:00:00', NULL),
(201000004, 195000003, 'EXP-20260704-001', 'FAILED',     '2026-07-04 11:00:00', '2026-07-04 11:02:00');

-- 21. 商品评价
INSERT INTO mo_product_review (id, product_id, user_id, rating, content, status, create_time) VALUES
(202000001, 183000001, 180000001, 5, 'Great shampoo, my dog loves it!',          'APPROVED',  '2026-07-05 10:00:00'),
(202000002, 183000002, 180000002, 4, 'Warm and comfortable, true to size.',      'APPROVED',  '2026-07-08 14:00:00'),
(202000003, 183000003, 180000004, 5, 'Very sturdy bed, easy to clean.',          'APPROVED',  '2026-07-12 09:00:00'),
(202000004, 183000004, 180000006, 3, 'My dog got bored of it after a week.',     'PENDING',   '2026-07-15 11:00:00'),
(202000005, 183000001, 180000008, 4, 'Good product, fast shipping.',              'APPROVED',  '2026-07-10 16:00:00'),
(202000006, 183000010, 180000009, 5, 'Perfect size for my golden retriever.',    'APPROVED',  '2026-07-14 10:00:00');
