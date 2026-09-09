-- 会员卡号：每位用户随机唯一 12 位数字
-- 由应用在首次访问会员中心时生成并落库（member_no 为空即未分配）
ALTER TABLE mo_member
    ADD COLUMN member_no VARCHAR(20) NULL DEFAULT NULL COMMENT '会员卡号（随机唯一 12 位数字，不含 MY. 前缀）' AFTER user_id;

CREATE UNIQUE INDEX uk_member_no ON mo_member (member_no);
