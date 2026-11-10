package com.moyuyo.api.service.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.dto.admin.OperationResult;
import com.moyuyo.common.dto.admin.PageResponse;
import com.moyuyo.common.dto.admin.campaign.*;
import com.moyuyo.common.enums.OrderStatusEnum;
import com.moyuyo.dao.admin.entity.AbTestEntity;
import com.moyuyo.dao.admin.entity.MarketingCampaignEntity;
import com.moyuyo.dao.admin.mapper.AbTestMapper;
import com.moyuyo.dao.admin.mapper.MarketingCampaignMapper;
import com.moyuyo.dao.entity.CouponEntity;
import com.moyuyo.dao.entity.FlashSaleEntity;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.mapper.CouponMapper;
import com.moyuyo.dao.mapper.FlashSaleMapper;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.service.admin.impl.CampaignMarketingServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 营销活动服务实现单元测试
 * 覆盖:listCampaigns / createCampaign / getCampaignDetail / updateCampaign / deleteCampaign
 *      listAbTests / createAbTest / updateAbTest / getMarketingEffects
 */
@ExtendWith(MockitoExtension.class)
class CampaignMarketingServiceImplTest {

  @Mock
  private MarketingCampaignMapper marketingCampaignMapper;

  @Mock
  private AbTestMapper abTestMapper;

  @Mock
  private OrderMapper orderMapper;

  @Mock
  private com.moyuyo.dao.mapper.OrderItemMapper orderItemMapper;

  @Mock
  private CouponMapper couponMapper;

  @Mock
  private FlashSaleMapper flashSaleMapper;

  // CampaignMarketingServiceImpl 现有依赖：缺任一 Mock 都会在对应维度方法中 NPE
  @Mock
  private com.moyuyo.dao.mapper.UserCouponMapper userCouponMapper;

  @Mock
  private com.moyuyo.dao.mapper.FlashSaleOrderMapper flashSaleOrderMapper;

  @Mock
  private com.moyuyo.dao.mapper.InviteMapper inviteMapper;

  @InjectMocks
  private CampaignMarketingServiceImpl service;

  // ============ listCampaigns ============

  @Test
  void listCampaigns_正常调用_返回VO列表并正确格式化日期() {
    // given:数据库返回 2 个活动
    MarketingCampaignEntity c1 = buildCampaign(1L, "618大促", "ACTIVE");
    MarketingCampaignEntity c2 = buildCampaign(2L, "双11", "UPCOMING");
    Page<MarketingCampaignEntity> page = new Page<>(1, 15);
    page.setRecords(List.of(c1, c2));
    page.setTotal(2);
    when(marketingCampaignMapper.selectPage(any(), any())).thenReturn(page);
    // 订单明细归因：活动1 有 1 笔已完成订单，活动2 无订单
    com.moyuyo.dao.entity.OrderItemEntity item = new com.moyuyo.dao.entity.OrderItemEntity();
    item.setOrderId(900L);
    item.setCampaignId(1L);
    when(orderItemMapper.selectList(any())).thenReturn(List.of(item));
    OrderEntity paidOrder = buildOrder(new BigDecimal("5000.00"));
    paidOrder.setId(900L);
    paidOrder.setUserId(1001L);
    when(orderMapper.selectList(any())).thenReturn(List.of(paidOrder));

    // when
    PageResponse<CampaignResponse> result = service.listCampaigns(1, 15);

    // then:验证分页元数据
    assertEquals(2, result.getTotal());
    assertEquals(1, result.getPage());
    assertEquals(15, result.getSize());
    assertEquals(2, result.getRecords().size());

    // 验证 VO 字段映射
    CampaignResponse vo1 = result.getRecords().get(0);
    assertEquals(1L, vo1.getId());
    assertEquals("618大促", vo1.getName());
    assertEquals("DISCOUNT", vo1.getType());
    assertEquals("ACTIVE", vo1.getStatus());
    assertEquals("2026-06-01 00:00:00", vo1.getStartDate(), "LocalDateTime 应格式化为 yyyy-MM-dd HH:mm:ss");
    assertEquals("2026-06-18 00:00:00", vo1.getEndDate());
    assertEquals(new BigDecimal("10000.00"), vo1.getBudget());
    // 真实指标覆盖活动表静态值：1 个用户、GMV 5000、ROI=5000/10000
    assertEquals(1, vo1.getParticipants());
    assertEquals(new BigDecimal("5000.00"), vo1.getGmv());
    assertEquals(new BigDecimal("0.50"), vo1.getRoi());
    // 无归因订单的活动展示真实 0
    CampaignResponse vo2 = result.getRecords().get(1);
    assertEquals(0, vo2.getParticipants());
    assertEquals(0, BigDecimal.ZERO.compareTo(vo2.getGmv()));
  }

  // ============ createCampaign ============

  @Test
  void createCampaign_有效请求_初始状态根据时间计算() {
    // given:开始时间为未来,状态应为 UPCOMING
    CampaignRequest request = new CampaignRequest();
    request.setName("新年活动");
    request.setType("DISCOUNT");
    request.setStartDate("2099-01-01");
    request.setEndDate("2099-01-15");
    request.setBudget(new BigDecimal("5000"));

    // when
    OperationResult result = service.createCampaign(request);

    // then:验证插入的实体
    ArgumentCaptor<MarketingCampaignEntity> captor = ArgumentCaptor.forClass(MarketingCampaignEntity.class);
    verify(marketingCampaignMapper).insert(captor.capture());
    MarketingCampaignEntity inserted = captor.getValue();
    assertEquals("新年活动", inserted.getName());
    assertEquals("DISCOUNT", inserted.getType());
    assertEquals("UPCOMING", inserted.getStatus(), "开始时间在未来,状态应为 UPCOMING");
    assertEquals(LocalDateTime.of(2099, 1, 1, 0, 0, 0), inserted.getStartDate());
    assertEquals(new BigDecimal("5000"), inserted.getBudget());

    // 返回体校验
    assertEquals("活动创建成功", result.getMessage());
  }

  @Test
  void createCampaign_name为空_使用兜底名称和默认type() {
    // given:name 和 type 都为空
    CampaignRequest request = new CampaignRequest();
    request.setStartDate("2026-08-04");
    request.setEndDate("2026-08-10");

    // when
    service.createCampaign(request);

    // then:应使用兜底名称和默认 type=DISCOUNT
    ArgumentCaptor<MarketingCampaignEntity> captor = ArgumentCaptor.forClass(MarketingCampaignEntity.class);
    verify(marketingCampaignMapper).insert(captor.capture());
    assertTrue(captor.getValue().getName().startsWith("营销活动_"), "空名称应使用兜底值");
    assertEquals("DISCOUNT", captor.getValue().getType());
  }

  @Test
  void createCampaign_budget为空_使用ZERO() {
    // given
    CampaignRequest request = new CampaignRequest();
    request.setName("测试");
    request.setStartDate("2026-08-04");
    request.setEndDate("2026-08-10");

    // when
    service.createCampaign(request);

    // then
    ArgumentCaptor<MarketingCampaignEntity> captor = ArgumentCaptor.forClass(MarketingCampaignEntity.class);
    verify(marketingCampaignMapper).insert(captor.capture());
    assertEquals(BigDecimal.ZERO, captor.getValue().getBudget());
  }

  // ============ getCampaignDetail ============

  @Test
  void getCampaignDetail_活动不存在_返回null() {
    when(marketingCampaignMapper.selectById(1L)).thenReturn(null);

    CampaignDetailResponse result = service.getCampaignDetail(1L);

    assertNull(result);
  }

  @Test
  void getCampaignDetail_活动存在_返回详情含效果统计() {
    // given:活动预算 100，静态 GMV 1000 应被真实归因值覆盖
    MarketingCampaignEntity entity = buildCampaign(1L, "618大促", "ACTIVE");
    entity.setGmv(new BigDecimal("1000"));
    entity.setBudget(new BigDecimal("100"));
    when(marketingCampaignMapper.selectById(1L)).thenReturn(entity);
    // 订单明细归因：该活动关联 1 笔已完成订单，实付 200
    com.moyuyo.dao.entity.OrderItemEntity item = new com.moyuyo.dao.entity.OrderItemEntity();
    item.setOrderId(900L);
    item.setCampaignId(1L);
    when(orderItemMapper.selectList(any())).thenReturn(List.of(item));
    OrderEntity paidOrder = buildOrder(new BigDecimal("200"));
    paidOrder.setId(900L);
    paidOrder.setUserId(1001L);
    when(orderMapper.selectList(any())).thenReturn(List.of(paidOrder));

    // when
    CampaignDetailResponse result = service.getCampaignDetail(1L);

    // then
    assertNotNull(result);
    assertEquals(1L, result.getCampaign().getId());
    assertEquals("618大促", result.getCampaign().getName());
    // 真实指标：参与 1 人、GMV 200、客单价 200、ROI = 200/100 = 2
    assertEquals(1, result.getCampaign().getParticipants());
    assertEquals(new BigDecimal("200.00"), result.getCampaign().getGmv());
    assertEquals(new BigDecimal("200.00"), result.getEffects().getAvgOrderValue());
    assertEquals(new BigDecimal("2.00"), result.getEffects().getRoi());
  }

  @Test
  void getCampaignDetail_budget为零_ROI返回0避免除零异常() {
    MarketingCampaignEntity entity = buildCampaign(1L, "免费活动", "ACTIVE");
    entity.setGmv(new BigDecimal("1000"));
    entity.setBudget(BigDecimal.ZERO);
    when(marketingCampaignMapper.selectById(1L)).thenReturn(entity);
    // 无归因订单：GMV 与 ROI 都应为真实 0
    when(orderItemMapper.selectList(any())).thenReturn(List.of());

    CampaignDetailResponse result = service.getCampaignDetail(1L);

    assertEquals(BigDecimal.ZERO, result.getEffects().getRoi(), "budget 为 0 时 ROI 应为 0");
    assertEquals(0, BigDecimal.ZERO.compareTo(result.getCampaign().getGmv()), "不应再展示活动表里的静态 GMV");
  }

  // ============ updateCampaign ============

  @Test
  void updateCampaign_活动不存在_返回活动不存在消息() {
    when(marketingCampaignMapper.selectById(1L)).thenReturn(null);

    OperationResult result = service.updateCampaign(1L, new CampaignRequest());

    assertEquals("活动不存在", result.getMessage());
    verify(marketingCampaignMapper, never()).updateById(any(MarketingCampaignEntity.class));
  }

  @Test
  void updateCampaign_有效请求_仅更新非空字段() {
    // given:数据库中已有活动
    when(marketingCampaignMapper.selectById(1L)).thenReturn(buildCampaign(1L, "旧名称", "ACTIVE"));
    CampaignRequest request = new CampaignRequest();
    request.setName("新名称");
    request.setStatus("ENDED");

    // when
    OperationResult result = service.updateCampaign(1L, request);

    // then
    ArgumentCaptor<MarketingCampaignEntity> captor = ArgumentCaptor.forClass(MarketingCampaignEntity.class);
    verify(marketingCampaignMapper).updateById(captor.capture());
    assertEquals("新名称", captor.getValue().getName());
    assertEquals("ENDED", captor.getValue().getStatus());
    // 未传字段应保持原值
    assertEquals("DISCOUNT", captor.getValue().getType(), "未传 type 应保持原值");
    assertEquals(1L, result.getId());
    assertEquals("活动更新成功", result.getMessage());
  }

  // ============ deleteCampaign ============

  @Test
  void deleteCampaign_活动不存在_返回活动不存在消息() {
    when(marketingCampaignMapper.selectById(1L)).thenReturn(null);

    OperationResult result = service.deleteCampaign(1L);

    assertEquals("活动不存在", result.getMessage());
    verify(marketingCampaignMapper, never()).deleteById(any(java.io.Serializable.class));
  }

  @Test
  void deleteCampaign_活动存在_删除并返回成功() {
    when(marketingCampaignMapper.selectById(1L)).thenReturn(buildCampaign(1L, "618大促", "ACTIVE"));

    OperationResult result = service.deleteCampaign(1L);

    verify(marketingCampaignMapper).deleteById(1L);
    assertEquals(1L, result.getId());
    assertEquals("活动删除成功", result.getMessage());
  }

  // ============ listAbTests ============

  @Test
  void listAbTests_正常调用_返回VO列表() {
    AbTestEntity t1 = buildAbTest(1L, "AB测试1", "RUNNING");
    AbTestEntity t2 = buildAbTest(2L, "AB测试2", "COMPLETED");
    when(abTestMapper.selectList(any())).thenReturn(List.of(t1, t2));

    List<AbTestResponse> result = service.listAbTests();

    assertEquals(2, result.size());
    AbTestResponse vo1 = result.get(0);
    assertEquals(1L, vo1.getId());
    assertEquals("AB测试1", vo1.getName());
    assertEquals("RUNNING", vo1.getStatus());
    assertEquals(100, vo1.getGroupAVisitors());
    assertEquals(120, vo1.getGroupBVisitors());
    assertEquals(new BigDecimal("3.50"), vo1.getGroupAConvRate());
    assertEquals(new BigDecimal("4.20"), vo1.getGroupBConvRate());
  }

  // ============ createAbTest ============

  @Test
  void createAbTest_有效请求_插入实体并返回ID() {
    AbTestRequest request = new AbTestRequest();
    request.setName("新测试");
    request.setStatus("RUNNING");
    request.setGroupAVisitors(50);
    request.setGroupBVisitors(60);

    OperationResult result = service.createAbTest(request);

    ArgumentCaptor<AbTestEntity> captor = ArgumentCaptor.forClass(AbTestEntity.class);
    verify(abTestMapper).insert(captor.capture());
    assertEquals("新测试", captor.getValue().getName());
    assertEquals("RUNNING", captor.getValue().getStatus());
    assertEquals(50, captor.getValue().getGroupAVisitors());
    assertEquals(60, captor.getValue().getGroupBVisitors());
    assertNotNull(captor.getValue().getStartTime(), "startTime 应自动设置为当前时间");

    assertEquals("A/B测试创建成功", result.getMessage());
  }

  // ============ updateAbTest ============

  @Test
  void updateAbTest_测试不存在_返回不存在消息() {
    when(abTestMapper.selectById(1L)).thenReturn(null);

    OperationResult result = service.updateAbTest(1L, new AbTestRequest());

    assertEquals("A/B测试不存在", result.getMessage());
    verify(abTestMapper, never()).updateById(any(AbTestEntity.class));
  }

  @Test
  void updateAbTest_有效请求_仅更新非空字段() {
    when(abTestMapper.selectById(1L)).thenReturn(buildAbTest(1L, "旧测试", "RUNNING"));
    AbTestRequest request = new AbTestRequest();
    request.setStatus("COMPLETED");
    request.setGroupAVisitors(200);

    OperationResult result = service.updateAbTest(1L, request);

    ArgumentCaptor<AbTestEntity> captor = ArgumentCaptor.forClass(AbTestEntity.class);
    verify(abTestMapper).updateById(captor.capture());
    assertEquals("COMPLETED", captor.getValue().getStatus());
    assertEquals(200, captor.getValue().getGroupAVisitors());
    assertEquals("旧测试", captor.getValue().getName(), "未传 name 应保持原值");
    assertEquals(1L, result.getId());
    assertEquals("A/B测试更新成功", result.getMessage());
  }

  // ============ getMarketingEffects ============

  @Test
  void getMarketingEffects_有订单数据_正确计算GMV和趋势() {
    // given:2 个已完成订单,分别在今天和昨天
    OrderEntity today = buildOrder(new BigDecimal("500"));
    today.setCreateTime(LocalDateTime.now());
    OrderEntity yesterday = buildOrder(new BigDecimal("300"));
    yesterday.setCreateTime(LocalDateTime.now().minusDays(1));
    when(orderMapper.selectList(any())).thenReturn(List.of(today, yesterday));

    // when:查询最近 7 天
    EffectResponse result = service.getMarketingEffects(7);

    // then
    assertEquals(new BigDecimal("800"), result.getTotalGmv());
    assertEquals(2, result.getTotalOrders());
    // 趋势数据应有 8 条(7+1,从 7 天前到今天)
    assertEquals(8, result.getTrend().size());
    // campaignRatio = 800*100/800 = 100.0
    assertEquals(new BigDecimal("100.0"), result.getCampaignRatio());
  }

  @Test
  void getMarketingEffects_无订单_GMV为零且不抛异常() {
    when(orderMapper.selectList(any())).thenReturn(List.of());

    EffectResponse result = service.getMarketingEffects(7);

    assertEquals(BigDecimal.ZERO, result.getTotalGmv());
    assertEquals(0, result.getTotalOrders());
    assertEquals(BigDecimal.ZERO, result.getCampaignRatio(), "无订单时占比应为 0 而非 NaN");
    assertEquals(8, result.getTrend().size(), "即使无订单,趋势数组也应填充 0 值");
  }

  // ============ 辅助方法 ============

  private MarketingCampaignEntity buildCampaign(Long id, String name, String status) {
    MarketingCampaignEntity c = new MarketingCampaignEntity();
    c.setId(id);
    c.setName(name);
    c.setType("DISCOUNT");
    c.setStatus(status);
    c.setDescription("测试活动");
    c.setStartDate(LocalDateTime.of(2026, 6, 1, 0, 0, 0));
    c.setEndDate(LocalDateTime.of(2026, 6, 18, 0, 0, 0));
    c.setParticipants(100);
    c.setGmv(new BigDecimal("5000.00"));
    c.setBudget(new BigDecimal("10000.00"));
    c.setCost(new BigDecimal("800.00"));
    c.setCreateTime(LocalDateTime.of(2026, 5, 20, 10, 0, 0));
    return c;
  }

  private AbTestEntity buildAbTest(Long id, String name, String status) {
    AbTestEntity t = new AbTestEntity();
    t.setId(id);
    t.setName(name);
    t.setStatus(status);
    t.setDescription("测试描述");
    t.setGroupAVisitors(100);
    t.setGroupBVisitors(120);
    t.setGroupAConvRate(new BigDecimal("3.50"));
    t.setGroupBConvRate(new BigDecimal("4.20"));
    t.setStartTime(LocalDateTime.of(2026, 8, 1, 10, 0, 0));
    t.setCreateTime(LocalDateTime.of(2026, 8, 1, 10, 0, 0));
    return t;
  }

  private OrderEntity buildOrder(BigDecimal payAmount) {
    OrderEntity o = new OrderEntity();
    o.setId(1L);
    o.setStatus(OrderStatusEnum.COMPLETED.name());
    o.setPayAmount(payAmount);
    return o;
  }

  // ============ saveDraft ============

  @Test
  void saveDraft_有效请求_状态强制为DRAFT() {
    // given
    CampaignRequest request = new CampaignRequest();
    request.setName("草稿测试");
    request.setType("DISCOUNT");

    // when
    OperationResult result = service.saveDraft(request);

    // then
    ArgumentCaptor<MarketingCampaignEntity> captor = ArgumentCaptor.forClass(MarketingCampaignEntity.class);
    verify(marketingCampaignMapper).insert(captor.capture());
    assertEquals("草稿测试", captor.getValue().getName());
    assertEquals("DRAFT", captor.getValue().getStatus(), "草稿必须落 DRAFT 而非 calculateStatus");
    assertEquals("草稿保存成功", result.getMessage());
  }

  @Test
  void saveDraft_name为空_使用兜底名称() {
    // given
    CampaignRequest request = new CampaignRequest();
    request.setStartDate("2026-08-04");
    request.setEndDate("2026-08-10");

    // when
    service.saveDraft(request);

    // then
    ArgumentCaptor<MarketingCampaignEntity> captor = ArgumentCaptor.forClass(MarketingCampaignEntity.class);
    verify(marketingCampaignMapper).insert(captor.capture());
    assertTrue(captor.getValue().getName().startsWith("未命名草稿_"));
    assertEquals("DRAFT", captor.getValue().getStatus());
  }

  // ============ getCouponEffects ============

  @Test
  void getCouponEffects_无券数据_指标全为0不抛异常() {
    when(couponMapper.selectList(any())).thenReturn(List.of());

    CouponEffectResponse result = service.getCouponEffects(7);

    assertEquals(0, result.getTotalIssued());
    assertEquals(BigDecimal.ZERO, result.getUsageRate(), "无券时核销率应为 0 而非 NaN");
    assertTrue(result.getItems().isEmpty());
  }

  @Test
  void getCouponEffects_有券无订单_核销率0且GMV0() {
    CouponEntity c = new CouponEntity();
    c.setId(1L);
    c.setName("测试券");
    c.setDiscountValue(new BigDecimal("20"));
    c.setClaimedCount(100);
    c.setUsedCount(50);
    when(couponMapper.selectList(any())).thenReturn(List.of(c));
    when(orderMapper.selectList(any())).thenReturn(List.of());

    CouponEffectResponse result = service.getCouponEffects(7);

    assertEquals(100, result.getTotalIssued());
    assertEquals(50, result.getTotalUsed());
    assertEquals(new BigDecimal("50.0"), result.getUsageRate());
    assertEquals(BigDecimal.ZERO, result.getGmv());
    assertEquals(1, result.getItems().size());
    assertEquals("测试券", result.getItems().get(0).getName());
    // 明细中的 issued 为累计发放量（claimedCount=100），used 为累计核销量（50）
    assertEquals(100, result.getItems().get(0).getIssued());
    assertEquals(50, result.getItems().get(0).getUsed());
  }

  // ============ getFlashEffects ============

  @Test
  void getFlashEffects_无秒杀_所有指标为0() {
    when(flashSaleMapper.selectList(any())).thenReturn(List.of());

    FlashEffectResponse result = service.getFlashEffects(7);

    assertEquals(BigDecimal.ZERO, result.getParticipationRate());
    assertEquals(BigDecimal.ZERO, result.getConversionRate());
    assertEquals(BigDecimal.ZERO, result.getAvgSelloutMinutes());
    assertEquals(BigDecimal.ZERO, result.getGmv());
    assertTrue(result.getItems().isEmpty());
  }

  @Test
  void getFlashEffects_已售罄场次_售罄率为100() {
    FlashSaleEntity f = new FlashSaleEntity();
    f.setId(1L);
    f.setName("秒杀A");
    f.setTotalStock(100);
    f.setSoldStock(100);
    f.setStartTime(LocalDateTime.now().minusHours(1));
    f.setEndTime(LocalDateTime.now().plusHours(1));
    when(flashSaleMapper.selectList(any())).thenReturn(List.of(f));
    // 该场次无秒杀订单（flashSaleOrderMapper 默认返回空列表），订单相关统计不会触发 orderMapper 查询

    FlashEffectResponse result = service.getFlashEffects(7);

    assertEquals(100, result.getItems().get(0).getSelloutRate());
    assertEquals("已售罄", result.getItems().get(0).getStatus());
    assertEquals(new BigDecimal("100.0"), result.getParticipationRate(), "1/1 场售罄 = 100%");
  }
}
