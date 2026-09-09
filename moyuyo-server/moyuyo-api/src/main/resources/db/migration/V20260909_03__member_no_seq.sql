-- 会员卡号自增序列：仅用于生成全局唯一、自然递增的卡号数字
-- 分配方式：INSERT 取自增 id → %012d 得到卡号（如 000000000001）
CREATE TABLE mo_member_no_seq (
    id BIGINT NOT NULL AUTO_INCREMENT,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
