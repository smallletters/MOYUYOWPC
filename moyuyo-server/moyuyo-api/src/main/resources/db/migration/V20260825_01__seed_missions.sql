-- 任务中心种子数据（对应 MOYUYO会员积分规则说明 V1.0 第 2.6 节）
INSERT INTO mo_mission (id, type, name, description, icon, points, target, sort_order, active, create_time) VALUES
-- 每日任务
(1,  'DAILY', '每日签到',           '完成每日签到获得积分',                    '📅',  5,  1,  10, b'1', NOW()),
(2,  'DAILY', '浏览 5 个商品',       '浏览 5 件商品详情',                       '👀',  3,  5,  20, b'1', NOW()),
(3,  'DAILY', '分享 1 个商品',       '分享商品给好友',                          '📤',  5,  1,  30, b'1', NOW()),
(4,  'DAILY', '完成 1 次 Pet Hub 互动', '在 Pet Hub 完成任意互动',              '🐾',  2,  1,  40, b'1', NOW()),
-- 每周任务
(11, 'WEEKLY', '累计签到 5 天',       '本周内累计签到 5 天',                    '📆',  20, 5,  10, b'1', NOW()),
(12, 'WEEKLY', '完成 1 单购物',       '本周内完成一笔订单',                    '🛒', 30, 1,  20, b'1', NOW()),
(13, 'WEEKLY', '发布 1 条社区笔记',   '在社区发布一篇笔记',                    '📝', 15, 1,  30, b'1', NOW()),
(14, 'WEEKLY', '邀请 1 位好友注册',   '本周邀请 1 位好友',                      '🤝', 50, 1,  40, b'1', NOW()),
-- 成就任务
(21, 'ACHIEVEMENT', '首单完成',                    '完成首笔订单',                          '🏆', 100, 1, 10, b'1', NOW()),
(22, 'ACHIEVEMENT', '累计消费满 $500',             '历史累计消费达 500 美元',                '💎', 200, 500, 20, b'1', NOW()),
(23, 'ACHIEVEMENT', '连续签到 30 天',              '累计连续签到 30 天',                    '🔥', 100, 30, 30, b'1', NOW()),
(24, 'ACHIEVEMENT', '发布 10 条笔记',              '社区累计发布 10 条笔记',                '✍️', 80, 10, 40, b'1', NOW()),
(25, 'ACHIEVEMENT', '邀请 10 位好友',              '累计成功邀请 10 位好友',                '🌟', 500, 10, 50, b'1', NOW())
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  points = VALUES(points),
  target = VALUES(target),
  sort_order = VALUES(sort_order);