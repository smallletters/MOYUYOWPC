-- ============================================================
-- V20260718_08__system_seed.sql
-- 系统配置 / 推送 / 盘点 / 政策 种子数据
-- ============================================================

-- 1. 系统配置
INSERT INTO mo_system_config (id, config_key, config_value, remark) VALUES
(207000001, 'site_name', 'MOYUYO 宠物商城', '站点名称'),
(207000002, 'site_description', '高端宠物用品商城', '站点描述'),
(207000003, 'record_no', '粤ICP备2025XXXXXX号', '备案号'),
(207000004, 'contact_email', 'admin@moyuyo.com', '联系邮箱'),
(207000005, 'auto_cancel', '30', '自动取消订单(分钟)'),
(207000006, 'auto_confirm', '7', '自动确认收货(天)'),
(207000007, 'email_notify', 'true', '启用邮件通知'),
(207000008, 'sms_notify', 'true', '启用短信通知');

-- 2. 推送记录
INSERT INTO mo_push_record (id, title, content, type, status, success_count, fail_count, create_time) VALUES
(208000001, '新品上架通知', '新到一批猫玩具...', 'NOTICE', 'SENT', 180, 3, '2026-07-15 10:00:00'),
(208000002, '促销活动提醒', '夏日狂欢节6折起', 'PROMOTION', 'SENT', 150, 5, '2026-07-16 14:00:00'),
(208000003, '系统维护通知', '7月20日凌晨维护', 'SYSTEM', 'SENT', 200, 2, '2026-07-17 09:00:00'),
(208000004, '优惠券发放通知', '您有一张满100减20优惠券', 'PROMOTION', 'SENT', 120, 8, '2026-07-17 15:00:00'),
(208000005, '库存预警通知', '宠物床库存不足', 'SYSTEM', 'SENT', 85, 1, '2026-07-18 08:00:00');

-- 3. 投诉处理记录
INSERT INTO mo_complaint_process (id, complaint_id, action, operator, remark, create_time) VALUES
(209000001, 185000001, '分配处理人', '张三', '已分配至客服一部', '2026-07-10 10:00:00'),
(209000002, 185000001, '联系用户核实', '张三', '已电话联系用户核实情况', '2026-07-10 14:00:00'),
(209000003, 185000002, '分配处理人', '李四', '已分配至客服二部', '2026-07-12 09:00:00');

-- 4. 库存盘点
INSERT INTO mo_inventory_check (id, check_no, sku_id, product_name, sku_spec, book_quantity, actual_quantity, difference, status, checker, create_time) VALUES
(210000001, 'CK20260718001', 184000001, 'Premium Pet Shampoo 500ml', '500ml', 500, 498, -2, 'COMPLETED', '张三', '2026-07-18 09:00:00'),
(210000002, 'CK20260718002', 184000006, 'Interactive Treat Ball Toy', 'Standard', 1000, 1002, 2, 'COMPLETED', '李四', '2026-07-18 09:30:00'),
(210000003, 'CK20260718003', 184000010, 'Dental Chew Sticks 30-pack', '30-pack', 800, 795, -5, 'PROCESSING', '王五', '2026-07-18 10:00:00'),
(210000004, 'CK20260718004', 184000013, 'Elevated Dog Bowl Set', 'Set', 250, 248, -2, 'PENDING', '赵六', '2026-07-18 11:00:00'),
(210000005, 'CK20260718005', 184000011, 'Portable Water Bottle for Pets', '500ml', 600, 600, 0, 'COMPLETED', '张三', '2026-07-18 11:30:00');

-- 5. GDPR 政策版本
INSERT INTO mo_gdpr_policy (id, version, title, content, status, effective_date) VALUES
(211000001, '3.0', '隐私政策', '本政策涵盖个人数据收集、使用、存储和保护等...', 'ACTIVE', '2026-07-01'),
(211000002, '2.0', '隐私政策', '上一版本隐私政策...', 'DEPRECATED', '2026-01-01'),
(211000003, '1.0', '隐私政策', '初始版本隐私政策...', 'DEPRECATED', '2025-07-01');
