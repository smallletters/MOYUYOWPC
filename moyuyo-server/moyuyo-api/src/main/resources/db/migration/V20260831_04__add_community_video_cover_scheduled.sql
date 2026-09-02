-- 社区帖子升级:视频封面 + 定时发布
-- V20260831_04
ALTER TABLE mo_community_post
  ADD COLUMN cover        VARCHAR(255) NULL              COMMENT '视频封面 URL(仅视频帖有值)',
  ADD COLUMN scheduled_at DATETIME     NULL              COMMENT '定时发布时间:null=立即发布;非空时由调度任务到点切换为已发布';

-- status 字段语义扩展:0=删除(已隐含) 1=已发布 2=审核中 3=待发布(定时)
-- 表字段为 tinyint(1) 已经兼容 0/1/2/3,无需改类型
