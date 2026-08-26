-- 补齐 PRD 设计稿所需的完整分类树（GEAR/CARE/PLAY/HOME + 一级 6 个 + 二级 30+）
-- 原则：保留已有 6 个一级和 7 个二级（id=1,2,3,4,5,6,101..104,201..203），补充缺失的二级；
--       新增 4 个 GEAR 子分类，挂到合适的一级下；为 Toys/Feeding/Beds/Travel 补子分类
-- Id 编号规则：1xx = Bath&Grooming 下子分类；2xx = Apparel 下；3xx = Beds&Furniture；4xx = Toys；
--              5xx = Feeding；6xx = Travel&Outdoor

-- 一、补充 Bath & Grooming 下缺失的子分类
INSERT INTO mo_category (id, parent_id, name, icon, sort, level, create_time) VALUES
  (105, 1, '干洗喷雾', '🧴', 5, 2, NOW()),
  (106, 1, '眼部清洁', '👁️', 6, 2, NOW()),
  (107, 1, '护爪膏', '🐾', 7, 2, NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name), sort=VALUES(sort);

-- 二、补充 Apparel 下缺失的子分类（补到 PRD 要求的 6 个）
INSERT INTO mo_category (id, parent_id, name, icon, sort, level, create_time) VALUES
  (204, 2, '服饰上衣', '👕', 4, 2, NOW()),
  (205, 2, '服饰下装', '👖', 5, 2, NOW()),
  (206, 2, '围巾配饰', '🧣', 6, 2, NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name), sort=VALUES(sort);

-- 三、为 Beds & Furniture 补子分类（窝床/垫子/餐具/家居饰品）
INSERT INTO mo_category (id, parent_id, name, icon, sort, level, create_time) VALUES
  (301, 3, '窝床', '🛏️', 1, 2, NOW()),
  (302, 3, '垫子', '🟦', 2, 2, NOW()),
  (303, 3, '餐具', '🥣', 3, 2, NOW()),
  (304, 3, '家居饰品', '🏠', 4, 2, NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name), sort=VALUES(sort);

-- 四、为 Toys 补子分类（毛绒/啃咬/益智/训练）
INSERT INTO mo_category (id, parent_id, name, icon, sort, level, create_time) VALUES
  (401, 4, '毛绒玩具', '🧸', 1, 2, NOW()),
  (402, 4, '啃咬玩具', '🦷', 2, 2, NOW()),
  (403, 4, '益智玩具', '🧩', 3, 2, NOW()),
  (404, 4, '训练用品', '🎯', 4, 2, NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name), sort=VALUES(sort);

-- 五、为 Feeding 补子分类（主粮/零食/饮水/喂食工具）
INSERT INTO mo_category (id, parent_id, name, icon, sort, level, create_time) VALUES
  (501, 5, '主粮', '🍖', 1, 2, NOW()),
  (502, 5, '零食', '🍗', 2, 2, NOW()),
  (503, 5, '饮水设备', '💧', 3, 2, NOW()),
  (504, 5, '喂食工具', '🥄', 4, 2, NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name), sort=VALUES(sort);

-- 六、为 Travel & Outdoor 补子分类（PRD 中 GEAR 装备 7 个分类的核心）
INSERT INTO mo_category (id, parent_id, name, icon, sort, level, create_time) VALUES
  (601, 6, '牵引绳', '🦮', 1, 2, NOW()),
  (602, 6, '胸背带', '🎽', 2, 2, NOW()),
  (603, 6, '宠物服饰', '👔', 3, 2, NOW()),
  (604, 6, '出行装备', '🧳', 4, 2, NOW()),
  (605, 6, '户外运动', '⛰️', 5, 2, NOW()),
  (606, 6, '防水系列', '🌧️', 6, 2, NOW()),
  (607, 6, '季节限定', '🍂', 7, 2, NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name), sort=VALUES(sort);

-- 七、同步更新现有 icon 为 emoji（让前端 tab 显示更直观）
UPDATE mo_category SET icon = '🛁' WHERE id = 1;
UPDATE mo_category SET icon = '👗' WHERE id = 2;
UPDATE mo_category SET icon = '🛋️' WHERE id = 3;
UPDATE mo_category SET icon = '🎾' WHERE id = 4;
UPDATE mo_category SET icon = '🍽️' WHERE id = 5;
UPDATE mo_category SET icon = '✈️' WHERE id = 6;