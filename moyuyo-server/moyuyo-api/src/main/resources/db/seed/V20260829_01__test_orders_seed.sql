-- ============================================================
-- V20260829_01__test_orders_seed.sql
-- 待发货 / 待收货 测试订单种子数据
-- ============================================================
-- 用途：APP 端 / 管理端订单列表与物流弹窗联调测试
-- 关联账号：test@moyuyo.com（APP 端登录用）
-- 关联 Mock 物流：运单号 MOCK-* 前缀配合 MockLogisticsTrackProvider（moyuyo.logistics.provider=mock）
--
-- 数据范围：
--   - 待发货订单 3 笔（PAID 状态，未发货）
--   - 待收货订单 3 笔（SHIPPED 状态，已发货，含物流记录）
--
-- 冲突处理：所有 INSERT 使用 INSERT IGNORE，重复启动不会报错
-- 已通过 APP 注册 test@moyuyo.com 时，用户插入被跳过，订单仍会正确关联
-- ============================================================

-- 1. 测试用户（密码 = "123456"，与 demo 数据一致）
-- 若用户已通过 APP 注册（密码可能不同），此处 INSERT IGNORE 跳过
INSERT IGNORE INTO mo_user (id, email, password_hash, nickname, country, locale, timezone, phone, status, points, email_verified, marketing_opt_in, created_at) VALUES
(180000099, 'test@moyuyo.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Test User', 'CN', 'zh_CN', 'Asia/Shanghai', '+8613800000001', 1, 500, 1, 1, '2026-08-29 10:00:00');

-- 2. 默认收货地址（与 APP 端 checkout 页选地址测试配套）
INSERT IGNORE INTO mo_address (id, user_id, receiver, phone, country, province, city, district, detail, zip_code, tag, is_default, create_time) VALUES
(182000099, 180000099, '测试用户', '+8613800000001', 'CN', '上海市', '上海市', '浦东新区', '张江高科技园区博云路2号', '201203', 'HOME', 1, '2026-08-29 10:00:00');

-- ============================================================
-- 3. 待发货订单（PAID 状态，已支付待发货，无物流记录）
-- ============================================================

INSERT IGNORE INTO mo_order (id, order_no, user_id, address_id, goods_amount, freight, tax_amount, coupon_discount, points_discount, pay_amount, currency, status, pay_channel, pay_transaction_id, tracking_number, shipping_carrier, remark, create_time, paid_at) VALUES
-- 订单 1：2 件商品，Stripe 支付，已支付待发货
(186100001, 'TEST_ORD_20260829001', 180000099, 182000099, 33.98, 5.99, 4.42, 0, 0, 44.39, 'USD', 'PAID', 'STRIPE', 'ch_test_stripe_001', NULL, NULL, '测试订单-待发货-Stripe', '2026-08-28 10:15:00', '2026-08-28 10:16:00'),
-- 订单 2：1 件商品，PayPal 支付，已支付待发货
(186100002, 'TEST_ORD_20260829002', 180000099, 182000099, 89.99, 0, 8.10, 0, 0, 98.09, 'USD', 'PAID', 'PAYPAL', 'pp_test_paypal_002', NULL, NULL, '测试订单-待发货-PayPal', '2026-08-28 14:30:00', '2026-08-28 14:31:00'),
-- 订单 3：3 件商品，Apple Pay 支付，PENDING_SHIP 状态（更明确的"待发货"标签）
(186100003, 'TEST_ORD_20260829003', 180000099, 182000099, 56.97, 5.99, 6.99, 10.00, 0, 59.95, 'USD', 'PENDING_SHIP', 'APPLE_PAY', 'ap_test_applepay_003', NULL, NULL, '测试订单-待发货-ApplePay', '2026-08-29 09:00:00', '2026-08-29 09:01:00');

-- 4. 待发货订单的商品明细（复用 demo 商品 ID 183000001 等）
INSERT IGNORE INTO mo_order_item (id, order_id, product_id, sku_id, product_name, sku_spec, main_image, price, quantity, subtotal) VALUES
-- 订单 1 明细：洗发水 2 瓶 + 耳朵清洁湿巾 1 包
(187100001, 186100001, 183000001, 184000001, 'Premium Pet Shampoo 500ml', '500ml',   'https://images.unsplash.com/photo-1565708097481-e285a4bb4ff1?w=400', 18.99, 2, 37.98),
(187100002, 186100001, 183000009, 184000012, 'Ear Cleaning Wipes 50-pack', '50-pack', 'https://images.unsplash.com/photo-1535241749838-2994de95b2f7?w=400', 8.99,  1, 8.99),
-- 订单 2 明细：宠物床 1 个
(187100003, 186100002, 183000003, 184000005, 'Orthopedic Pet Bed Large', 'Large',   'https://images.unsplash.com/photo-1541592568934-d22f60f68380?w=400', 89.99, 1, 89.99),
-- 订单 3 明细：玩具 1 个 + 洗发水 1 瓶 + 牙齿咀嚼棒 1 包
(187100004, 186100003, 183000004, 184000006, 'Interactive Treat Ball Toy', 'Standard','https://images.unsplash.com/photo-1583511656206-52dd61175823?w=400', 14.99, 1, 14.99),
(187100005, 186100003, 183000001, 184000001, 'Premium Pet Shampoo 500ml', '500ml',   'https://images.unsplash.com/photo-1565708097481-e285a4bb4ff1?w=400', 18.99, 1, 18.99),
(187100006, 186100003, 183000007, 184000010, 'Dental Chew Sticks 30-pack', '30-pack', 'https://images.unsplash.com/photo-1530293149014-8d6ef22d90d5?w=400', 12.99, 1, 12.99);

-- 5. 待发货订单的支付记录
INSERT IGNORE INTO mo_payment (id, order_id, pay_channel, transaction_id, amount, currency, status, paid_at, create_time) VALUES
(188100001, 186100001, 'STRIPE',    'ch_test_stripe_001',  44.39, 'USD', 'SUCCESS', '2026-08-28 10:16:00', '2026-08-28 10:15:30'),
(188100002, 186100002, 'PAYPAL',    'pp_test_paypal_002',  98.09, 'USD', 'SUCCESS', '2026-08-28 14:31:00', '2026-08-28 14:30:30'),
(188100003, 186100003, 'APPLE_PAY', 'ap_test_applepay_003', 59.95, 'USD', 'SUCCESS', '2026-08-29 09:01:00', '2026-08-29 09:00:30');

-- ============================================================
-- 6. 待收货订单（SHIPPED 状态，已发货，含物流记录）
--    运单号使用 MOCK-* 前缀，配合 provider=mock 即可看到不同物流场景
-- ============================================================

INSERT IGNORE INTO mo_order (id, order_no, user_id, address_id, goods_amount, freight, tax_amount, coupon_discount, points_discount, pay_amount, currency, status, pay_channel, pay_transaction_id, tracking_number, shipping_carrier, remark, create_time, paid_at) VALUES
-- 订单 4：运输中场景（MOCK-INTRANSIT-），3 节点轨迹，未签收
(186100004, 'TEST_ORD_20260829004', 180000099, 182000099, 31.98, 5.99, 3.81, 0, 0, 41.78, 'USD', 'SHIPPED', 'STRIPE', 'ch_test_stripe_004', 'MOCK-INTRANSIT-004', 'SF', '测试订单-待收货-运输中', '2026-08-25 10:00:00', '2026-08-25 10:01:00'),
-- 订单 5：已签收场景（MOCK-DELIVERED-），5 节点轨迹，最新含"已签收"
-- 注意：状态仍为 SHIPPED，查询物流时会自动触发 confirmReceived 升级为 RECEIVED
(186100005, 'TEST_ORD_20260829005', 180000099, 182000099, 89.99, 0, 8.10, 0, 0, 98.09, 'USD', 'SHIPPED', 'PAYPAL', 'pp_test_paypal_005', 'MOCK-DELIVERED-005', 'YTO', '测试订单-待收货-已签收', '2026-08-20 14:30:00', '2026-08-20 14:31:00'),
-- 订单 6：仅发货场景（MOCK-PENDING-），1 条发货轨迹
(186100006, 'TEST_ORD_20260829006', 180000099, 182000099, 22.99, 4.99, 2.80, 0, 0, 30.78, 'USD', 'SHIPPED', 'STRIPE', 'ch_test_stripe_006', 'MOCK-PENDING-006', 'USPS', '测试订单-待收货-刚发货', '2026-08-29 08:00:00', '2026-08-29 08:01:00');

-- 7. 待收货订单的商品明细
INSERT IGNORE INTO mo_order_item (id, order_id, product_id, sku_id, product_name, sku_spec, main_image, price, quantity, subtotal) VALUES
-- 订单 4 明细：便携水壶 2 个
(187100007, 186100004, 183000008, 184000011, 'Portable Water Bottle for Pets', '500ml', 'https://images.unsplash.com/photo-1605515996523-6b7688a6139b?w=400', 9.99, 2, 19.98),
-- 订单 5 明细：宠物床 1 个
(187100008, 186100005, 183000003, 184000005, 'Orthopedic Pet Bed Large', 'Large', 'https://images.unsplash.com/photo-1541592568934-d22f60f68380?w=400', 89.99, 1, 89.99),
-- 订单 6 明细：猫用胸背带 1 套
(187100009, 186100006, 183000006, 184000008, 'Cat Harness & Leash Set', 'M Blue', 'https://images.unsplash.com/photo-1605515996523-6b7688a6139b?w=400', 22.99, 1, 22.99);

-- 8. 待收货订单的支付记录
INSERT IGNORE INTO mo_payment (id, order_id, pay_channel, transaction_id, amount, currency, status, paid_at, create_time) VALUES
(188100004, 186100004, 'STRIPE', 'ch_test_stripe_004', 41.78, 'USD', 'SUCCESS', '2026-08-25 10:01:00', '2026-08-25 10:00:30'),
(188100005, 186100005, 'PAYPAL', 'pp_test_paypal_005', 98.09, 'USD', 'SUCCESS', '2026-08-20 14:31:00', '2026-08-20 14:30:30'),
(188100006, 186100006, 'STRIPE', 'ch_test_stripe_006', 30.78, 'USD', 'SUCCESS', '2026-08-29 08:01:00', '2026-08-29 08:00:30');

-- 9. 物流记录（待收货订单已发货，运单号配合 MOCK- 前缀）
-- traces 字段存储初始人工轨迹 JSON，查询时会被 MockLogisticsTrackProvider 返回的轨迹覆盖
INSERT IGNORE INTO mo_logistics (id, order_id, carrier, tracking_number, traces, shipped_at, received_at) VALUES
-- 订单 4 物流：运输中
(189100004, 186100004, 'SF', 'MOCK-INTRANSIT-004',
 JSON_ARRAY(
   JSON_OBJECT('time', '2026-08-25T10:30:00+08:00', 'location', '上海仓库', 'desc', '快件已揽收，运单号：MOCK-INTRANSIT-004', 'status', 'accepted')
 ),
 '2026-08-25 10:30:00', NULL),
-- 订单 5 物流：模拟 5 天前已签收（但订单状态仍为 SHIPPED，等待查询时自动触发 confirmReceived）
(189100005, 186100005, 'YTO', 'MOCK-DELIVERED-005',
 JSON_ARRAY(
   JSON_OBJECT('time', '2026-08-20T15:00:00+08:00', 'location', '上海仓库', 'desc', '快件已揽收，运单号：MOCK-DELIVERED-005', 'status', 'accepted')
 ),
 '2026-08-20 15:00:00', NULL),
-- 订单 6 物流：刚发货
(189100006, 186100006, 'USPS', 'MOCK-PENDING-006',
 JSON_ARRAY(
   JSON_OBJECT('time', '2026-08-29T08:30:00+08:00', 'location', '上海仓库', 'desc', '快件已揽收，运单号：MOCK-PENDING-006', 'status', 'accepted')
 ),
 '2026-08-29 08:30:00', NULL);
