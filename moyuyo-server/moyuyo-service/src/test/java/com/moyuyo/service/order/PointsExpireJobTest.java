package com.moyuyo.service.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.dao.entity.PointsLogEntity;
import com.moyuyo.dao.entity.UserEntity;
import com.moyuyo.dao.mapper.PointsLogMapper;
import com.moyuyo.dao.mapper.UserMapper;
import com.moyuyo.service.MemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PointsExpireJob 单元测试
 * <p>
 * 覆盖场景：
 * - 总开关关闭：不扫描、不调用 addPoints
 * - 无过期流水：直接返回
 * - 单用户过期：按 sum 调 addPoints(-sum, EXPIRE)
 * - 多用户过期：每个用户各调一次
 * - 过期总额 > 当前余额：clamp 到余额
 * - 用户不存在 / 余额为 0：跳过
 * - 单用户多条流水汇总
 * - 异常隔离：单用户失败不影响其他用户
 */
@ExtendWith(MockitoExtension.class)
class PointsExpireJobTest {

    @Mock
    private PointsLogMapper pointsLogMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private MemberService memberService;

    @InjectMocks
    private PointsExpireJob job;

    @Captor
    private ArgumentCaptor<Integer> changeCaptor;

    @BeforeEach
    void setUp() throws Exception {
        // @Value 注入字段由 Mockito 不处理，需手动开启
        setField("enabled", true);
        setField("batchSize", 200);
    }

    private void setField(String name, Object value) throws Exception {
        Field f = PointsExpireJob.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(job, value);
    }

    @Test
    @DisplayName("总开关关闭：不执行任何查询与扣减")
    void disabledJob_doesNothing() throws Exception {
        setField("enabled", false);

        job.expireOverduePoints();

        verify(pointsLogMapper, never()).selectList(any(LambdaQueryWrapper.class));
        verify(memberService, never()).addPoints(anyLong(), anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("无过期流水：直接返回，不调用 addPoints")
    void noExpiredLogs_skip() {
        when(pointsLogMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(new ArrayList<>());

        job.expireOverduePoints();

        verify(memberService, never()).addPoints(anyLong(), anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("单用户过期：调用 addPoints(-total, EXPIRE) 一次")
    void singleUser_expireSum() {
        when(pointsLogMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(buildLogs(100L, 200, 100));  // 300 总和
        when(userMapper.selectById(100L))
                .thenReturn(buildUser(100L, 500));

        job.expireOverduePoints();

        verify(memberService, times(1)).addPoints(eq(100L), eq(-300), eq("EXPIRE"),
                any(), any());
    }

    @Test
    @DisplayName("多用户过期：每个用户各自累加并扣减")
    void multiUsers_separateCalls() {
        when(pointsLogMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(mergeLogs(
                        buildLogs(100L, 100, 50),  // user100: 150
                        buildLogs(200L, 80, 20))); // user200: 100
        when(userMapper.selectById(100L))
                .thenReturn(buildUser(100L, 500));
        when(userMapper.selectById(200L))
                .thenReturn(buildUser(200L, 200));

        job.expireOverduePoints();

        verify(memberService, times(1)).addPoints(eq(100L), eq(-150), eq("EXPIRE"),
                any(), any());
        verify(memberService, times(1)).addPoints(eq(200L), eq(-100), eq("EXPIRE"),
                any(), any());
    }

    @Test
    @DisplayName("过期总额超过余额：clamp 到当前余额")
    void expireExceedsBalance_clamped() {
        when(pointsLogMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(buildLogs(100L, 800));  // 过期 800
        when(userMapper.selectById(100L))
                .thenReturn(buildUser(100L, 300));  // 余额 300

        job.expireOverduePoints();

        // 实际扣减 = min(800, 300) = 300
        verify(memberService, times(1)).addPoints(eq(100L), eq(-300), eq("EXPIRE"),
                any(), any());
    }

    @Test
    @DisplayName("用户余额为 0：跳过，不调 addPoints")
    void zeroBalance_skip() {
        when(pointsLogMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(buildLogs(100L, 100));
        when(userMapper.selectById(100L))
                .thenReturn(buildUser(100L, 0));

        job.expireOverduePoints();

        verify(memberService, never()).addPoints(anyLong(), anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("用户不存在：跳过")
    void userNotFound_skip() {
        when(pointsLogMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(buildLogs(100L, 100));
        when(userMapper.selectById(100L)).thenReturn(null);

        job.expireOverduePoints();

        verify(memberService, never()).addPoints(anyLong(), anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("单用户失败：不影响其他用户")
    void singleUserFailure_isolated() {
        when(pointsLogMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(mergeLogs(
                        buildLogs(100L, 100),
                        buildLogs(200L, 200)));
        when(userMapper.selectById(100L))
                .thenThrow(new RuntimeException("DB 异常"));
        when(userMapper.selectById(200L))
                .thenReturn(buildUser(200L, 500));

        // 不应抛出异常
        job.expireOverduePoints();

        // user200 仍正常扣减
        verify(memberService, times(1)).addPoints(eq(200L), eq(-200), eq("EXPIRE"),
                any(), any());
    }

    @Test
    @DisplayName("bizNo 含 'expire:' 前缀便于追溯")
    void bizNoFormat() {
        when(pointsLogMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(buildLogs(100L, 50));
        when(userMapper.selectById(100L))
                .thenReturn(buildUser(100L, 100));

        job.expireOverduePoints();

        verify(memberService).addPoints(eq(100L), anyInt(), any(),
                org.mockito.ArgumentMatchers.startsWith("expire:"), any());
    }

    @Test
    @DisplayName("单用户多条流水：累加求和后一次性扣减")
    void multipleLogsSameUser_summed() {
        when(pointsLogMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(buildLogs(100L, 30, 30, 40, 100));  // 200
        when(userMapper.selectById(100L))
                .thenReturn(buildUser(100L, 1000));

        job.expireOverduePoints();

        verify(memberService, times(1)).addPoints(eq(100L), eq(-200), eq("EXPIRE"),
                any(), any());
        // 验证不会因为多条记录就调多次
        verify(memberService, times(1)).addPoints(eq(100L), changeCaptor.capture(),
                any(), any(), any());
        assertEquals(-200, changeCaptor.getValue());
    }

    /**
     * 构造单个用户的过期正向流水列表
     */
    private List<PointsLogEntity> buildLogs(Long userId, int... values) {
        List<PointsLogEntity> list = new ArrayList<>();
        for (int v : values) {
            PointsLogEntity e = new PointsLogEntity();
            e.setUserId(userId);
            e.setType("REWARD");
            e.setChangeValue(v);
            e.setExpireTime(java.time.LocalDateTime.now().minusDays(1));
            list.add(e);
        }
        return list;
    }

    /**
     * 拼接多个用户的过期流水
     */
    @SafeVarargs
    private List<PointsLogEntity> mergeLogs(List<PointsLogEntity>... lists) {
        List<PointsLogEntity> all = new ArrayList<>();
        for (List<PointsLogEntity> l : lists) {
            all.addAll(l);
        }
        return all;
    }

    private UserEntity buildUser(Long id, int points) {
        UserEntity u = new UserEntity();
        u.setId(id);
        u.setPoints(points);
        return u;
    }
}