package com.moyuyo.service.schedule;

import com.moyuyo.service.CommunityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 社区帖子定时发布调度任务。
 * <p>
 * 每 60 秒扫描一次:
 * - 调用 {@link CommunityService#publishScheduledPosts()} 把到点的待发布帖子切换为已发布
 * - 当前实现固定 60s 间隔:粒度足够日常使用(用户最低可设置"未来 1 分钟"作为发布时间)
 * <p>
 * 注意:
 * - 单实例部署,无需分布式锁;若多实例需要换成 ShedLock 或 Redis 分布式锁
 * - 任务执行时间很短(单次扫表通常 <50ms),不会阻塞其他业务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommunityScheduledPublishTask {

    private final CommunityService communityService;

    /**
     * 每 60 秒执行一次(应用启动后开始计时)。
     * cron 表达式 second minute hour day month weekday
     * "0 * * * * ?" 表示每分钟 0 秒触发,等价于 fixedRate=60s
     */
    @Scheduled(cron = "0 * * * * ?")
    public void tick() {
        try {
            int n = communityService.publishScheduledPosts();
            if (n > 0) {
                log.info("[community-schedule] tick published {} posts", n);
            }
        } catch (Exception e) {
            // 任务失败不影响主流程,只记录日志
            log.error("[community-schedule] tick failed: {}", e.getMessage(), e);
        }
    }
}
