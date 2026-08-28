package com.moyuyo.service.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.dao.entity.PointsLogEntity;
import com.moyuyo.dao.entity.UserEntity;
import com.moyuyo.dao.mapper.PointsLogMapper;
import com.moyuyo.dao.mapper.UserMapper;
import com.moyuyo.service.MemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 积分过期清理定时任务
 * <p>
 * 每天凌晨扫描 mo_points_log 中已过期（expire_time &lt; now）的正向流水（change_value &gt; 0），
 * 按 userId 汇总后调用 MemberService.addPoints 写入一条 EXPIRE 扣减流水，amount 取实际可用余额（避免积分不足）。
 * <p>
 * 设计要点：
 * 1. 仅清理正向流水（change_value &gt; 0）；扣减/退款/兑换流水不设 expireTime，不会被清理
 * 2. 每用户累计本次清理总额，下调时按 user.points 兜底，防止"积分不足"
 * 3. 单批 batchSize 防 DB 抖动
 * 4. 总开关 moyuyo.points.expire.enabled，默认 true
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PointsExpireJob {

    private final PointsLogMapper pointsLogMapper;
    private final UserMapper userMapper;
    private final MemberService memberService;

    @Value("${moyuyo.points.expire.enabled:true}")
    private boolean enabled;

    @Value("${moyuyo.points.expire.batch-size:200}")
    private int batchSize;

    /**
     * 每天凌晨 3 点执行（与 OperationLogRetentionJob 错峰）。
     * <p>
     * cron 而非 fixedDelay：定时语义更清晰；任务执行窗口不固定可控。
     */
    @Scheduled(cron = "${moyuyo.points.expire-cron:0 0 3 * * *}")
    public void expireOverduePoints() {
        if (!enabled) {
            return;
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            // 仅查正向且已过期的流水；deleted=0 由 @TableLogic 自动追加
            List<PointsLogEntity> dueList = pointsLogMapper.selectList(
                    new LambdaQueryWrapper<PointsLogEntity>()
                            .gt(PointsLogEntity::getChangeValue, 0)
                            .isNotNull(PointsLogEntity::getExpireTime)
                            .lt(PointsLogEntity::getExpireTime, now)
                            .last("LIMIT " + Math.max(1, batchSize)));
            if (dueList.isEmpty()) {
                return;
            }

            // 按用户聚合过期金额
            Map<Long, Integer> perUserTotal = new HashMap<>();
            for (PointsLogEntity log : dueList) {
                perUserTotal.merge(log.getUserId(), log.getChangeValue(), Integer::sum);
            }

            int expiredUsers = 0;
            int expiredTotal = 0;
            for (Map.Entry<Long, Integer> entry : perUserTotal.entrySet()) {
                Long userId = entry.getKey();
                int sum = entry.getValue();
                try {
                    int actual = expireForUser(userId, sum);
                    if (actual > 0) {
                        expiredUsers++;
                        expiredTotal += actual;
                    }
                } catch (Exception e) {
                    log.warn("[points-expire] 清理用户 {} 过期积分失败: {}", userId, e.getMessage());
                }
            }
            log.info("[points-expire] 扫描 {} 条过期流水，影响用户 {} 位，累计清理积分 {}",
                    dueList.size(), expiredUsers, expiredTotal);
        } catch (Exception e) {
            log.error("[points-expire] 定时任务异常", e);
        }
    }

    /**
     * 单用户过期清理：实际扣减 = min(过期总额, 当前可用余额)
     */
    @Transactional
    public int expireForUser(Long userId, int expireTotal) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null || user.getPoints() == null || user.getPoints() <= 0) {
            return 0;
        }
        int actual = Math.min(expireTotal, user.getPoints());
        if (actual <= 0) {
            return 0;
        }
        // addPoints 走负值分支：不设置 expireTime（避免重复清理），并自动重算会员等级
        memberService.addPoints(
                userId,
                -actual,
                "EXPIRE",
                "expire:" + LocalDateTime.now(),
                String.format("过期清零：累计过期 %d 积分，实际扣减 %d 积分", expireTotal, actual));
        return actual;
    }
}