-- 任务中心新增任务
--   每日：记录 1 次宠物体重（+5 积分）
--   每日：发布 1 条社区笔记（+5 积分）
--   每周：记录宠物体重 3 次（+10 积分）
-- 注意：任务进度埋点按 type + name 关键字匹配，任务名文案变更需同步修改
--      PetWeightServiceImpl / CommunityServiceImpl 中的 incrementByKeyword 关键字
INSERT INTO mo_mission (id, type, name, description, icon, points, target, sort_order, active, create_time) VALUES
-- 每日任务（接在 id=4 之后）
(5,  'DAILY',  '记录 1 次宠物体重', '在体重记录中新增一条体重', '⚖️',  5, 1, 45, b'1', NOW()),
(6,  'DAILY',  '发布 1 条社区笔记', '在社区发布一篇笔记',       '📝',  5, 1, 50, b'1', NOW()),
-- 每周任务（排在每周任务末尾）
(15, 'WEEKLY', '记录宠物体重 3 次', '本周内记录 3 次宠物体重',  '⚖️', 10, 3, 45, b'1', NOW())
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  points = VALUES(points),
  target = VALUES(target),
  sort_order = VALUES(sort_order);
