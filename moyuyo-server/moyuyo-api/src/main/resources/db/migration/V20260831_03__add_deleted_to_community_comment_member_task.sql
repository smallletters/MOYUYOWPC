-- ============================================================
-- V20260831_03__add_deleted_to_community_comment_member_task.sql
-- 补齐 @TableLogic 字段缺失的表
-- mo_community_comment: CommunityCommentEntity 用了 @TableLogic
-- mo_member_task:        MemberTaskEntity      用了 @TableLogic
-- 原因: V20260731_02__fix_user_api_schema.sql 只为部分表添加了 deleted 列，
--       社区评论表和会员任务表被遗漏，导致帖子详情/评论相关接口 500。
-- ============================================================

ALTER TABLE `mo_community_comment`
  ADD COLUMN `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除 0-正常 1-已删';

ALTER TABLE `mo_member_task`
  ADD COLUMN `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除 0-正常 1-已删';
