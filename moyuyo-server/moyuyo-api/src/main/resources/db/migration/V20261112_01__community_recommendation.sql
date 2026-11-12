-- 社区个性化推荐：帖子特征字段 + 用户兴趣画像 + 查询索引
-- 1) 帖子冗余 IP / 宠物类型，避免推荐时反复 join 宠物表
ALTER TABLE mo_community_post
  ADD COLUMN ip_code VARCHAR(32) NULL COMMENT '内容所属 IP（发帖时从宠物偏好冗余）' AFTER topic,
  ADD COLUMN pet_type VARCHAR(16) NULL COMMENT '内容关联宠物类型 DOG/CAT/OTHER' AFTER ip_code,
  ADD COLUMN hot_score DECIMAL(12,4) NOT NULL DEFAULT 0 COMMENT '热度分（定时任务刷新）' AFTER comments;

-- 2) 推荐召回/热度排序索引
ALTER TABLE mo_community_post
  ADD INDEX idx_status_hot (status, hot_score DESC),
  ADD INDEX idx_status_user_time (status, user_id, create_time DESC),
  ADD INDEX idx_status_topic_time (status, topic, create_time DESC);

-- 2.1) 历史帖子回填推荐特征：
--      - 有 pet_id 的帖子直接取关联宠物
--      - 无 pet_id 的帖子按作者第一只宠物兜底
UPDATE mo_community_post p
JOIN mo_pet pet ON p.pet_id = pet.id
SET p.ip_code = COALESCE(p.ip_code, pet.preferred_ip_code),
    p.pet_type = COALESCE(p.pet_type, pet.type)
WHERE p.pet_id IS NOT NULL
  AND pet.deleted = 0
  AND (p.ip_code IS NULL OR p.pet_type IS NULL);

UPDATE mo_community_post p
JOIN (
  SELECT user_id,
         MAX(preferred_ip_code) AS ip_code,
         MAX(type) AS pet_type
  FROM mo_pet
  WHERE deleted = 0
  GROUP BY user_id
) pet ON p.user_id = pet.user_id
SET p.ip_code = COALESCE(p.ip_code, pet.ip_code),
    p.pet_type = COALESCE(p.pet_type, pet.pet_type)
WHERE (p.ip_code IS NULL OR p.pet_type IS NULL);

-- 3) 用户兴趣画像（主题/作者权重，推荐排序使用）
CREATE TABLE IF NOT EXISTS mo_user_interest (
  id            BIGINT       NOT NULL,
  user_id       BIGINT       NOT NULL,
  dimension     VARCHAR(16)  NOT NULL COMMENT 'topic/author',
  interest_key  VARCHAR(64)  NOT NULL,
  weight        DECIMAL(10,4) NOT NULL DEFAULT 0,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_dim_key (user_id, dimension, interest_key),
  KEY idx_user_dim_weight (user_id, dimension, weight DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '社区用户兴趣画像';
