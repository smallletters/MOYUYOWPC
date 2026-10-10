package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.common.dto.admin.OperationResult;
import com.moyuyo.common.dto.admin.finance.FinanceOverviewResponse;
import com.moyuyo.common.dto.admin.finance.SettlementDetailResponse;
import com.moyuyo.common.dto.admin.finance.SettlementRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.dao.admin.entity.FinanceRecordEntity;
import com.moyuyo.dao.admin.entity.SettlementEntity;
import com.moyuyo.dao.admin.mapper.FinanceRecordMapper;
import com.moyuyo.dao.admin.mapper.SettlementMapper;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.service.admin.FinanceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 管理后台财务 Controller 单元测试
 * 覆盖:overview / settlementDetail / createSettlement / updateSettlement / deleteSettlement
 */
@ExtendWith(MockitoExtension.class)
class AdminFinanceControllerTest {

  @Mock
  private FinanceService financeService;

  @Mock
  private OrderMapper orderMapper;

  @Mock
  private SettlementMapper settlementMapper;

  @Mock
  private FinanceRecordMapper financeRecordMapper;

  @InjectMocks
  private AdminFinanceController adminFinanceController;

  /** 构造测试结算实体 */
  private SettlementEntity buildSettlement(Long id, String period, BigDecimal amount, String status) {
    SettlementEntity entity = new SettlementEntity();
    entity.setId(id);
    entity.setSettlementNo("SET-20260804");
    entity.setPeriod(period);
    entity.setAmount(amount);
    entity.setStatus(status);
    entity.setCreateTime(LocalDateTime.of(2026, 8, 4, 10, 0, 0));
    return entity;
  }

  // ============ overview ============

  @Test
  void overview_正常调用_返回FinanceOverviewResponse() {
    // given:Service 返回原始 Map,包含 monthGmv/actualIncome 等键
    when(financeService.getFinanceOverview()).thenReturn(Map.of(
        "monthGmv", new BigDecimal("10000"),
        "actualIncome", new BigDecimal("9500"),
        "pendingSettlement", new BigDecimal("500"),
        "pendingIssues", 3,
        "refundAmount", new BigDecimal("100"),
        "channelDistribution", List.of(Map.of("channel", "Stripe"))
    ));
    // 已完成结算数量
    when(settlementMapper.selectCount(any())).thenReturn(5L);

    // when
    Result<FinanceOverviewResponse> result = adminFinanceController.overview();

    // then:验证字段映射
    assertEquals(0, result.getCode());
    FinanceOverviewResponse data = result.getData();
    assertEquals(new BigDecimal("10000"), data.getTotalRevenue());
    assertEquals(new BigDecimal("9500"), data.getActualIncome());
    assertEquals(new BigDecimal("500"), data.getPendingSettlement());
    assertEquals(5, data.getCompletedSettlements());
    assertEquals(3, data.getPendingCount());
    assertEquals(new BigDecimal("100"), data.getRefundAmount());
    assertNotNull(data.getChannelDistribution());
    assertEquals(1, data.getChannelDistribution().size());
  }

  // ============ settlementDetail ============

  @Test
  void settlementDetail_记录不存在_返回错误() {
    when(settlementMapper.selectById(1L)).thenReturn(null);

    Result<SettlementDetailResponse> result = adminFinanceController.settlementDetail(1L);

    assertNotEquals(0, result.getCode());
    assertNull(result.getData());
  }

  @Test
  void settlementDetail_记录存在_返回详情包含手续费和净额() {
    // given:结算记录金额 1000,期望手续费 10,净额 990
    when(settlementMapper.selectById(1L))
        .thenReturn(buildSettlement(1L, "2026-08-04", new BigDecimal("1000.00"), "COMPLETED"));
    // period 解析为单日,查询当天订单
    when(orderMapper.selectList(any())).thenReturn(List.of());

    // when
    Result<SettlementDetailResponse> result = adminFinanceController.settlementDetail(1L);

    // then
    assertEquals(0, result.getCode());
    SettlementDetailResponse data = result.getData();
    assertEquals(1L, data.getId());
    assertEquals("SET-20260804", data.getSettlementNo());
    assertEquals(new BigDecimal("1000.00"), data.getTotalAmount());
    assertEquals(new BigDecimal("10.00"), data.getFee(), "手续费应为 1%");
    assertEquals(new BigDecimal("990.00"), data.getNetAmount());
    assertEquals("COMPLETED", data.getStatus());
    assertEquals(0, data.getOrderCount());
  }

  // ============ createSettlement ============

  @Test
  void createSettlement_有效请求_插入实体并返回ID() {
    // given
    SettlementRequest request = new SettlementRequest();
    request.setPeriod("2026-08-04");
    request.setAmount(new BigDecimal("5000.00"));
    request.setStatus("PENDING");
    request.setRemark("8月结算");
    request.setPayChannel("Stripe");

    // when
    Result<OperationResult> result = adminFinanceController.createSettlement(request);

    // then:验证插入的实体字段
    ArgumentCaptor<SettlementEntity> captor = ArgumentCaptor.forClass(SettlementEntity.class);
    verify(settlementMapper).insert(captor.capture());
    SettlementEntity inserted = captor.getValue();
    assertEquals("2026-08-04", inserted.getPeriod());
    assertEquals(new BigDecimal("5000.00"), inserted.getAmount());
    assertEquals("PENDING", inserted.getStatus());
    assertEquals("8月结算", inserted.getRemark());
    assertEquals("Stripe", inserted.getPayChannel());
    assertNotNull(inserted.getSettlementNo(), "结算单号应自动生成");
    // 返回体校验
    assertEquals(0, result.getCode());
    assertEquals("结算记录创建成功", result.getData().getMessage());
  }

  @Test
  void createSettlement_amount为空_插入NullAmount() {
    // given:未指定 amount
    SettlementRequest request = new SettlementRequest();
    request.setPeriod("2026-08-04");
    request.setStatus("PENDING");

    // when
    adminFinanceController.createSettlement(request);

    // then:amount 应为 null,不抛异常
    ArgumentCaptor<SettlementEntity> captor = ArgumentCaptor.forClass(SettlementEntity.class);
    verify(settlementMapper).insert(captor.capture());
    assertNull(captor.getValue().getAmount());
  }

  @Test
  @SuppressWarnings("unchecked") // any(LambdaQueryWrapper.class) raw type 桩 Wrapper<SettlementEntity> 是 Mockito 测试桩预期用法
  void createSettlement_并发撞唯一索引_返回原记录() {
    // given:应用层 selectOne 未命中(并发场景下另一线程刚好插入),
    // 随后本线程 insert 被 uk_settlement_no 拒绝抛 DuplicateKeyException,
    // Controller 应再查一次并返回原记录
    SettlementEntity raceWinner = buildSettlement(99L, "2026-10-10",
      new BigDecimal("8888.00"), "PENDING");
    when(settlementMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(raceWinner);
    doThrow(new DuplicateKeyException("Duplicate entry 'SET-20261010' for key 'uk_settlement_no'"))
      .when(settlementMapper).insert(any(SettlementEntity.class));

    SettlementRequest request = new SettlementRequest();
    request.setPeriod("2026-10-10");
    request.setAmount(new BigDecimal("8888.00"));
    request.setStatus("PENDING");

    // when
    Result<OperationResult> result = adminFinanceController.createSettlement(request);

    // then
    assertEquals(0, result.getCode(), "应返回成功,而不是错误");
    assertEquals(99L, result.getData().getId(), "返回原记录的 id");
    assertEquals("当日结算单已存在,已返回原记录", result.getData().getMessage());
    // 不应再插入一条 finance_record(避免重复流水)
    verify(financeRecordMapper, never()).insert(any(FinanceRecordEntity.class));
  }

  // ============ updateSettlement ============

  @Test
  void updateSettlement_记录不存在_返回错误() {
    when(settlementMapper.selectById(1L)).thenReturn(null);

    Result<OperationResult> result = adminFinanceController.updateSettlement(1L, new SettlementRequest());

    assertNotEquals(0, result.getCode());
    verify(settlementMapper, never()).updateById(any(SettlementEntity.class));
  }

  @Test
  void updateSettlement_有效请求_更新非空字段() {
    // given:数据库中已有记录
    when(settlementMapper.selectById(1L))
        .thenReturn(buildSettlement(1L, "2026-08-04", new BigDecimal("1000.00"), "PENDING"));
    SettlementRequest request = new SettlementRequest();
    request.setStatus("COMPLETED");
    request.setRemark("已结算");

    // when
    Result<OperationResult> result = adminFinanceController.updateSettlement(1L, request);

    // then:仅更新非空字段
    ArgumentCaptor<SettlementEntity> captor = ArgumentCaptor.forClass(SettlementEntity.class);
    verify(settlementMapper).updateById(captor.capture());
    SettlementEntity updated = captor.getValue();
    assertEquals("COMPLETED", updated.getStatus());
    assertEquals("已结算", updated.getRemark());
    // 未传字段保持原值
    assertEquals("2026-08-04", updated.getPeriod());
    assertEquals(new BigDecimal("1000.00"), updated.getAmount());
    // 返回体
    assertEquals(0, result.getCode());
    assertEquals(1L, result.getData().getId());
    assertEquals("结算记录更新成功", result.getData().getMessage());
  }

  // ============ deleteSettlement ============

  @Test
  void deleteSettlement_调用deleteById() {
    Result<OperationResult> result = adminFinanceController.deleteSettlement(1L);

    verify(settlementMapper).deleteById(1L);
    assertEquals(0, result.getCode());
    assertEquals(1L, result.getData().getId());
    assertEquals("结算记录删除成功", result.getData().getMessage());
  }
}
