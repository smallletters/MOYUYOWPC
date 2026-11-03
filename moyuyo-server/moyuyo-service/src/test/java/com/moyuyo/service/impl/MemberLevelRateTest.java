package com.moyuyo.service.impl;

import com.moyuyo.dao.admin.entity.SystemConfigEntity;
import com.moyuyo.dao.admin.mapper.SystemConfigMapper;
import com.moyuyo.dao.entity.MemberEntity;
import com.moyuyo.dao.entity.MemberEntity.Level;
import com.moyuyo.dao.mapper.MemberMapper;
import com.moyuyo.dao.mapper.PointsLogMapper;
import com.moyuyo.dao.mapper.UserMapper;
import com.moyuyo.dao.mapper.WalletMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * MemberServiceImpl 会员积分倍率单元测试：
 * 覆盖默认五档倍率、后台逐档配置覆盖、非法配置回退等分支。
 */
@ExtendWith(MockitoExtension.class)
class MemberLevelRateTest {

    @Mock
    private MemberMapper memberMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private WalletMapper walletMapper;

    @Mock
    private PointsLogMapper pointsLogMapper;

    @Mock
    private SystemConfigMapper systemConfigMapper;

    @InjectMocks
    private MemberServiceImpl memberService;

    @Test
    @DisplayName("未配置：按默认五档倍率返回（金卡 1.2）")
    void getCurrentPointsRate_defaultGoldRate() {
        when(memberMapper.selectOne(any())).thenReturn(member(Level.GOLD));
        when(systemConfigMapper.selectList(any())).thenReturn(List.of());

        assertEquals(1.2, memberService.getCurrentPointsRate(100L));
    }

    @Test
    @DisplayName("后台配置覆盖：金卡配置为 1.5 时按配置返回")
    void getCurrentPointsRate_configuredGoldRate() {
        when(memberMapper.selectOne(any())).thenReturn(member(Level.GOLD));
        when(systemConfigMapper.selectList(any()))
                .thenReturn(List.of(config("points.level_rate.GOLD", "1.5")));

        assertEquals(1.5, memberService.getCurrentPointsRate(100L));
    }

    @Test
    @DisplayName("非法配置：数值非数字时回退默认倍率（银卡 1.1）")
    void getCurrentPointsRate_invalidValueFallback() {
        when(memberMapper.selectOne(any())).thenReturn(member(Level.SILVER));
        when(systemConfigMapper.selectList(any()))
                .thenReturn(List.of(config("points.level_rate.SILVER", "abc")));

        assertEquals(1.1, memberService.getCurrentPointsRate(100L));
    }

    @Test
    @DisplayName("非法配置：未知等级键被忽略，不影响已配置档位")
    void getCurrentPointsRate_unknownKeyIgnored() {
        when(memberMapper.selectOne(any())).thenReturn(member(Level.DIAMOND));
        List<SystemConfigEntity> configs = new ArrayList<>();
        configs.add(config("points.level_rate.DIAMOND", "2.5"));
        configs.add(config("points.level_rate.VIP9", "3.0"));
        when(systemConfigMapper.selectList(any())).thenReturn(configs);

        assertEquals(2.5, memberService.getCurrentPointsRate(100L));
    }

    private MemberEntity member(Level level) {
        MemberEntity member = new MemberEntity();
        member.setUserId(100L);
        member.setLevel(level);
        member.setGrowthValue(0);
        return member;
    }

    private SystemConfigEntity config(String key, String value) {
        SystemConfigEntity entity = new SystemConfigEntity();
        entity.setConfigKey(key);
        entity.setConfigValue(value);
        return entity;
    }
}
