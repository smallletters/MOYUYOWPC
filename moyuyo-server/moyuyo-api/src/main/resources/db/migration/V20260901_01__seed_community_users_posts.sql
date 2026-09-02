-- 社区演示种子数据:他人发帖 + 互动
-- V20260901_01
-- 用途:让 bloom 进入帖子详情时能看到 "+ 关注" 按钮(只能关注别人)并能涨粉/涨收藏数
-- 仅在没有任何"非 bloom"帖子时插入,避免重复

-- 1) 演示用户:6 个(Alice / Bob / Charlie 之前已存在,这里扩展为完整 demo 群)
-- 注意:mo_user 没有 create_time 字段(@TableField(fill = INSERT) 也没声明),所以不带 create_time
INSERT IGNORE INTO mo_user (id, email, password_hash, nickname, avatar, status, points, registration_channel, locale, timezone, deleted) VALUES
  (180000001, 'alice@moyuyo.demo', '$2a$10$abcdefghijklmnopqrstuv', 'Alice Johnson', 'https://i.pravatar.cc/100?img=47', 1, 320, 'app', 'en_US', 'Asia/Shanghai', 0),
  (180000002, 'bob@moyuyo.demo',   '$2a$10$abcdefghijklmnopqrstuv', 'Bob Smith',     'https://i.pravatar.cc/100?img=12', 1, 150, 'app', 'en_US', 'Asia/Shanghai', 0),
  (180000003, 'charlie@moyuyo.demo','$2a$10$abcdefghijklmnopqrstuv', 'Charlie Brown', 'https://i.pravatar.cc/100?img=33', 1, 540, 'app', 'en_US', 'Asia/Shanghai', 0),
  (180000004, 'diana@moyuyo.demo',  '$2a$10$abcdefghijklmnopqrstuv', 'Diana Wang',    'https://i.pravatar.cc/100?img=44', 1, 80,  'app', 'en_US', 'Asia/Shanghai', 0),
  (180000005, 'evan@moyuyo.demo',   '$2a$10$abcdefghijklmnopqrstuv', 'Evan Chen',     'https://i.pravatar.cc/100?img=68', 1, 260, 'app', 'en_US', 'Asia/Shanghai', 0),
  (180000006, 'fiona@moyuyo.demo',  '$2a$10$abcdefghijklmnopqrstuv', 'Fiona Liu',     'https://i.pravatar.cc/100?img=23', 1, 410, 'app', 'en_US', 'Asia/Shanghai', 0);

-- 2) 演示帖子:9 条覆盖图片/视频/纯文本 + 多种话题
-- MySQL 不支持 INSERT ... SELECT FROM (VALUES (...), (...)) 这种语法,
-- 改为单条 INSERT IGNORE + JSON_ARRAY 显式传 JSON 数组。
INSERT IGNORE INTO mo_community_post (id, user_id, content, images, video, topic, status, likes, comments, create_time) VALUES
  (290000001, 180000001, '今天带 Luna 去公园,她追了一只蝴蝶整整三分钟', JSON_ARRAY('https://picsum.photos/seed/alice1/600/600','https://picsum.photos/seed/alice2/600/600','https://picsum.photos/seed/alice3/600/600'), NULL, '宠物日常', 1, 23, 5, DATE_SUB(NOW(), INTERVAL 2 HOUR)),
  (290000002, 180000001, '新买的猫爬架到了,Luna 立刻占领最高层', NULL, 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4', '猫咪日常', 1, 18, 9, DATE_SUB(NOW(), INTERVAL 5 HOUR)),
  (290000003, 180000002, '周末遛狗好去处推荐,朝阳公园 + 奥森南园', JSON_ARRAY('https://picsum.photos/seed/bob1/600/600','https://picsum.photos/seed/bob2/600/600'), NULL, '遛狗日常', 1, 41, 12, DATE_SUB(NOW(), INTERVAL 8 HOUR)),
  (290000004, 180000003, '猫咪九宫格预警,一大波毛孩子来袭', JSON_ARRAY('https://picsum.photos/seed/c1/300/300','https://picsum.photos/seed/c2/300/300','https://picsum.photos/seed/c3/300/300','https://picsum.photos/seed/c4/300/300','https://picsum.photos/seed/c5/300/300','https://picsum.photos/seed/c6/300/300','https://picsum.photos/seed/c7/300/300','https://picsum.photos/seed/c8/300/300','https://picsum.photos/seed/c9/300/300'), NULL, '猫咪日常', 1, 156, 47, DATE_SUB(NOW(), INTERVAL 12 HOUR)),
  (290000005, 180000004, '新手养猫求助,小奶猫到家第一天一直叫怎么办', JSON_ARRAY('https://picsum.photos/seed/d1/600/600'), NULL, '新手养宠', 1, 12, 38, DATE_SUB(NOW(), INTERVAL 1 DAY)),
  (290000006, 180000005, '猫咪疫苗全攻略,妙三多+狂犬,每年打还是三年一次', JSON_ARRAY('https://picsum.photos/seed/e1/600/400'), NULL, '宠物医疗', 1, 89, 23, DATE_SUB(NOW(), INTERVAL 2 DAY)),
  (290000007, 180000006, '冻干零食红黑榜,这 5 款回购了 N 次', JSON_ARRAY('https://picsum.photos/seed/f1/600/600','https://picsum.photos/seed/f2/600/600'), NULL, '宠物零食', 1, 67, 19, DATE_SUB(NOW(), INTERVAL 3 DAY)),
  (290000008, 180000003, 'Coco 1 岁生日,给他做了个小蛋糕', JSON_ARRAY('https://picsum.photos/seed/coco1/600/600','https://picsum.photos/seed/coco2/600/600'), NULL, '宠物生日', 1, 234, 56, DATE_SUB(NOW(), INTERVAL 4 DAY)),
  (290000009, 180000002, '今天教 Buddy 坐下握手,30 分钟学会', NULL, 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4', '宠物训练', 1, 78, 31, DATE_SUB(NOW(), INTERVAL 6 DAY));

-- 3) 评论种子:几条热门评论让帖子更真实
-- mo_community_comment 有 @TableLogic deleted 字段,所以必须给 deleted=0,否则查询会被过滤
INSERT IGNORE INTO mo_community_comment (id, post_id, user_id, parent_id, content, status, deleted, create_time) VALUES
  (390000001, 290000001, 180000002, NULL, '太可爱了!我家狗也这样', 1, 0, DATE_SUB(NOW(), INTERVAL 1 HOUR)),
  (390000002, 290000001, 180000003, NULL, 'Luna 是什么品种呀?', 1, 0, DATE_SUB(NOW(), INTERVAL 30 MINUTE)),
  (390000003, 290000003, 180000001, NULL, '奥森南园周末人太多了 推荐顺义新城滨河森林公园', 1, 0, DATE_SUB(NOW(), INTERVAL 4 HOUR)),
  (390000004, 290000004, 180000006, NULL, '啊啊啊第九张笑死 😂', 1, 0, DATE_SUB(NOW(), INTERVAL 10 HOUR)),
  (390000005, 290000006, 180000001, NULL, '我家也是三年打一次 医生说够了', 1, 0, DATE_SUB(NOW(), INTERVAL 1 DAY)),
  (390000006, 290000007, 180000004, NULL, '求链接求链接!', 1, 0, DATE_SUB(NOW(), INTERVAL 2 DAY));

-- 4) 点赞种子:让帖子有 likes 数字(可选)
-- 必须显式给 id(因为用了 ASSIGN_ID 自增,这里批量插入要给具体 id 否则冲突)
INSERT IGNORE INTO mo_community_like (id, post_id, user_id, create_time) VALUES
  (490000001, 290000001, 180000002, DATE_SUB(NOW(), INTERVAL 1 HOUR)),
  (490000002, 290000001, 180000003, DATE_SUB(NOW(), INTERVAL 30 MINUTE)),
  (490000003, 290000001, 180000006, DATE_SUB(NOW(), INTERVAL 2 HOUR)),
  (490000004, 290000003, 180000001, DATE_SUB(NOW(), INTERVAL 4 HOUR)),
  (490000005, 290000003, 180000005, DATE_SUB(NOW(), INTERVAL 3 HOUR)),
  (490000006, 290000004, 180000001, DATE_SUB(NOW(), INTERVAL 10 HOUR)),
  (490000007, 290000004, 180000002, DATE_SUB(NOW(), INTERVAL 8 HOUR)),
  (490000008, 290000004, 180000006, DATE_SUB(NOW(), INTERVAL 6 HOUR)),
  (490000009, 290000006, 180000003, DATE_SUB(NOW(), INTERVAL 1 DAY)),
  (490000010, 290000008, 180000001, DATE_SUB(NOW(), INTERVAL 3 DAY));

-- 5) 双向关注关系:让 bloom 进入 user 页能立刻看到粉丝 +1
-- mo_follow 用 ASSIGN_ID 自增,批量插入要给具体 id
INSERT IGNORE INTO mo_follow (id, user_id, target_id, status, create_time) VALUES
  (590000001, 180000001, 2094332135488876546, 'FOLLOWING', DATE_SUB(NOW(), INTERVAL 3 DAY)),  -- Alice 关注 bloom(粉丝)
  (590000002, 180000003, 2094332135488876546, 'FOLLOWING', DATE_SUB(NOW(), INTERVAL 2 DAY)),  -- Charlie 关注 bloom
  (590000003, 180000005, 2094332135488876546, 'FOLLOWING', DATE_SUB(NOW(), INTERVAL 1 DAY));  -- Evan 关注 bloom
