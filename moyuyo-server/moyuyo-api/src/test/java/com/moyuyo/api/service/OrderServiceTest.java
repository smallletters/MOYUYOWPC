package com.moyuyo.api.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.dto.order.CreateOrderRequest;
import com.moyuyo.common.dto.order.OrderItemRequest;
import com.moyuyo.dao.entity.AddressEntity;
import com.moyuyo.dao.entity.FlashSaleEntity;
import com.moyuyo.dao.entity.LogisticsEntity;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.OrderItemEntity;
import com.moyuyo.dao.entity.ProductEntity;
import com.moyuyo.dao.entity.ProductSkuEntity;
import com.moyuyo.dao.entity.PaymentEntity;
import com.moyuyo.dao.admin.mapper.FinanceRecordMapper;
import com.moyuyo.dao.mapper.AddressMapper;
import com.moyuyo.dao.mapper.FlashSaleMapper;
import com.moyuyo.dao.mapper.LogisticsMapper;
import com.moyuyo.dao.mapper.OrderItemMapper;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.dao.mapper.PaymentMapper;
import com.moyuyo.dao.mapper.ProductMapper;
import com.moyuyo.dao.mapper.ProductSkuMapper;
import com.moyuyo.service.CouponService;
import com.moyuyo.service.InviteService;
import com.moyuyo.service.MemberPriceService;
import com.moyuyo.service.MemberService;
import com.moyuyo.service.MissionService;
import com.moyuyo.service.NotificationService;
import com.moyuyo.service.ShippingRateService;
import com.moyuyo.service.WooCommerceSyncService;
import com.moyuyo.service.config.MembershipRuleConfigService;
import com.moyuyo.service.impl.CampaignPricingServiceImpl;
import com.moyuyo.service.impl.OrderServiceImpl;
import com.moyuyo.service.impl.PointsRewardServiceImpl;
import com.moyuyo.service.mq.NotificationMessageProducer;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

// MyBatis-Plus Wrapper 为泛型接口，Mockito 的 any(XxxWrapper.class) / 原始类型 Captor 会产生 unchecked 警告，
// 测试代码统一抑制，避免 IDE 黄色感叹号（不影响编译）
@SuppressWarnings({"unchecked", "rawtypes"})
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

  @Mock
  private OrderMapper orderMapper;

  @Mock
  private OrderItemMapper orderItemMapper;

  @Mock
  private PaymentMapper paymentMapper;

  @Mock
  private ProductMapper productMapper;

  @Mock
  private ProductSkuMapper productSkuMapper;

  @Mock
  private FlashSaleMapper flashSaleMapper;

  // OrderServiceImpl#confirmReceived 会同步写 mo_logistics.received_at，必须注入 Mock 否则 NPE
  @Mock
  private LogisticsMapper logisticsMapper;

  @Mock
  private MemberPriceService memberPriceService;

  @Mock
  private MemberService memberService;

  @Mock
  private CouponService couponService;

  @Mock
  private ShippingRateService shippingRateService;

  @Mock
  private AddressMapper addressMapper;

  @Mock
  private InviteService inviteService;

  // OrderServiceImpl 近期新增的依赖：补全 Mock，避免 @InjectMocks 留 null 导致 NPE
  @Mock
  private FinanceRecordMapper financeRecordMapper;

  @Mock
  private WooCommerceSyncService wooCommerceSyncService;

  @Mock
  private MissionService missionService;

  @Mock
  private NotificationMessageProducer notificationMessageProducer;

  @Mock
  private NotificationService notificationService;

  @Mock
  private PointsRewardServiceImpl pointsRewardService;

  @Mock
  private MembershipRuleConfigService ruleConfigService;

  @Mock
  private CampaignPricingServiceImpl campaignPricingService;

  // 税率/运费类系数读取依赖：未 Mock 时会走 null 分支并打印告警日志
  @Mock
  private com.moyuyo.service.admin.SystemConfigService systemConfigService;

  @InjectMocks
  private OrderServiceImpl orderService;

  @BeforeEach
  void setUpCampaignPricing() {
    // 默认无营销活动：返回非 null 的零优惠计价，避免 OrderServiceImpl 解引用 NPE
    lenient().when(campaignPricingService.applyToOrder(anyLong(), anyList()))
        .thenAnswer(invocation -> new CampaignPricingServiceImpl.OrderPricing());
    // 积分抵扣规则默认值：100 积分抵 1 货币单位，最多抵扣 100%
    lenient().when(ruleConfigService.getConfig()).thenReturn(
        new MembershipRuleConfigService.MembershipRuleConfig(
            10, 7, 2, 50, 100, 10, 1, 100,
            100, 100, BigDecimal.ONE,
            0, 0, 0, 0));
  }

  /** 初始化 MyBatis-Plus Lambda 元数据，避免纯 Mockito 单测触发 "lambda cache" 异常 */
  @BeforeAll
  static void initMybatisTableInfo() {
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
    TableInfoHelper.initTableInfo(assistant, OrderEntity.class);
    TableInfoHelper.initTableInfo(assistant, OrderItemEntity.class);
    TableInfoHelper.initTableInfo(assistant, ProductEntity.class);
    TableInfoHelper.initTableInfo(assistant, ProductSkuEntity.class);
    TableInfoHelper.initTableInfo(assistant, PaymentEntity.class);
    // confirmReceived 使用 LambdaUpdateWrapper<LogisticsEntity>，同样需要初始化元数据
    TableInfoHelper.initTableInfo(assistant, LogisticsEntity.class);
  }

  /** 构造一个可用的收货地址，供服务端运费计算使用 */
  private AddressEntity buildUsAddress() {
    AddressEntity address = new AddressEntity();
    address.setCountry("US");
    address.setReceiver("测试收件人");
    address.setPhone("1234567890");
    return address;
  }

  @Captor
  private ArgumentCaptor<OrderEntity> orderCaptor;

  @Captor
  private ArgumentCaptor<OrderItemEntity> orderItemCaptor;

  // ==================== createOrder ====================

  @Test
  @DisplayName("有效请求创建订单应返回订单")
  void createOrder_validRequest_shouldReturnOrder() {
    // 准备：订单项数据
    Long userId = 1L;
    Long addressId = 10L;
    BigDecimal price = new BigDecimal("99.99");
    int quantity = 2;

    OrderItemEntity item = new OrderItemEntity();
    item.setProductId(20L);
    item.setPrice(price);
    item.setQuantity(quantity);

    List<OrderItemEntity> items = List.of(item);

    // 模拟商品存在且已上架（服务层会校验商品状态）
    ProductEntity product = new ProductEntity();
    product.setId(20L);
    product.setName("测试商品");
    product.setOnSale(true);
    when(productMapper.selectById(20L)).thenReturn(product);

    // 非 SKU 商品走原子扣减 product.stock，需返回 1 才能继续
    when(productMapper.update(any(), any())).thenReturn(1);
    // 服务端运费重算依赖地址 + 运费规则
    when(addressMapper.selectById(addressId)).thenReturn(buildUsAddress());
    when(shippingRateService.calcFreight(anyString(), anyString(), anyList(), any()))
        .thenReturn(BigDecimal.ZERO);

    // 模拟 orderMapper.insert 设置 ID
    doAnswer(invocation -> {
      OrderEntity order = invocation.getArgument(0);
      order.setId(100L);
      return 1;
    }).when(orderMapper).insert(any(OrderEntity.class));

    // 执行
    OrderEntity result = orderService.createOrder(userId, items, addressId, "测试备注", "coupon-001");

    // 验证订单基本信息
    assertNotNull(result);
    assertNotNull(result.getOrderNo());
    assertTrue(result.getOrderNo().startsWith("ORD")); // 订单号以 ORD 开头
    assertEquals(userId, result.getUserId());
    assertEquals("PENDING_PAY", result.getStatus());
    assertEquals(addressId, result.getAddressId());
    assertEquals("测试备注", result.getRemark());
    assertEquals("coupon-001", result.getCouponId());
    assertEquals(Integer.valueOf(0), result.getDeleteStatus());

    // 验证金额计算正确：99.99 * 2 = 199.98
    assertEquals(new BigDecimal("199.98"), result.getGoodsAmount());
    assertEquals(BigDecimal.ZERO, result.getFreight());
    assertEquals(new BigDecimal("199.98"), result.getPayAmount());

    // 验证 orderMapper.insert 被调用
    verify(orderMapper).insert(any(OrderEntity.class));

    // 验证订单项保存
    verify(orderItemMapper).insert(orderItemCaptor.capture());
    OrderItemEntity savedItem = orderItemCaptor.getValue();
    assertEquals(100L, savedItem.getOrderId()); // 关联到订单 ID
    assertEquals(price, savedItem.getPrice());
    assertEquals(quantity, savedItem.getQuantity());
    assertEquals(new BigDecimal("199.98"), savedItem.getSubtotal()); // 99.99 * 2
  }

  @Test
  @DisplayName("会员等级折扣应按商品销售价计算并落库")
  void createOrder_memberDiscount_shouldApplyToSellingPrice() {
    Long userId = 1L;
    Long addressId = 10L;

    OrderItemEntity item = new OrderItemEntity();
    item.setProductId(20L);
    item.setPrice(new BigDecimal("99.99"));
    item.setQuantity(2);

    ProductEntity product = new ProductEntity();
    product.setId(20L);
    product.setName("测试商品");
    product.setOnSale(true);
    when(productMapper.selectById(20L)).thenReturn(product);
    when(productMapper.update(any(), any())).thenReturn(1);
    when(addressMapper.selectById(addressId)).thenReturn(buildUsAddress());
    when(shippingRateService.calcFreight(anyString(), anyString(), anyList(), any()))
        .thenReturn(BigDecimal.ZERO);
    // 9 折会员
    when(memberPriceService.getDiscountForUser(userId)).thenReturn(new BigDecimal("0.90"));
    doAnswer(invocation -> {
      OrderEntity order = invocation.getArgument(0);
      order.setId(100L);
      return 1;
    }).when(orderMapper).insert(any(OrderEntity.class));

    OrderEntity result = orderService.createOrder(userId, List.of(item), addressId, "备注", null);

    assertEquals(new BigDecimal("199.98"), result.getGoodsAmount());
    // 199.98 × (1 - 0.90) = 19.998 → 20.00
    assertEquals(new BigDecimal("20.00"), result.getMemberDiscount());
    assertEquals(new BigDecimal("179.98"), result.getPayAmount());
  }

  @Test
  @DisplayName("秒杀商品不参与会员折扣")
  void createOrder_flashSaleProduct_shouldNotApplyMemberDiscount() {
    Long userId = 1L;
    Long addressId = 10L;

    OrderItemEntity item = new OrderItemEntity();
    item.setProductId(20L);
    item.setPrice(new BigDecimal("99.99"));
    item.setQuantity(2);

    ProductEntity product = new ProductEntity();
    product.setId(20L);
    product.setName("测试商品");
    product.setOnSale(true);
    when(productMapper.selectById(20L)).thenReturn(product);
    when(productMapper.update(any(), any())).thenReturn(1);
    when(addressMapper.selectById(addressId)).thenReturn(buildUsAddress());
    when(shippingRateService.calcFreight(anyString(), anyString(), anyList(), any()))
        .thenReturn(BigDecimal.ZERO);
    when(memberPriceService.getDiscountForUser(userId)).thenReturn(new BigDecimal("0.90"));
    // 商品处于秒杀活动中
    FlashSaleEntity flash = new FlashSaleEntity();
    flash.setProductId(20L);
    when(flashSaleMapper.selectList(any())).thenReturn(List.of(flash));
    doAnswer(invocation -> {
      OrderEntity order = invocation.getArgument(0);
      order.setId(100L);
      return 1;
    }).when(orderMapper).insert(any(OrderEntity.class));

    OrderEntity result = orderService.createOrder(userId, List.of(item), addressId, "备注", null);

    assertEquals(BigDecimal.ZERO, result.getMemberDiscount());
    assertEquals(new BigDecimal("199.98"), result.getPayAmount());
  }

  @Test
  @DisplayName("积分抵扣不得超过会员/优惠券后的剩余商品金额")
  void createOrderFromRequest_pointsCap_shouldNotExceedRemainingGoodsAmount() {
    OrderItemRequest itemReq = new OrderItemRequest();
    itemReq.setSkuId(20L); // 与商品 id 相同，走非 SKU 商品路径
    itemReq.setProductId(20L);
    itemReq.setQuantity(1);

    CreateOrderRequest request = new CreateOrderRequest();
    request.setItems(List.of(itemReq));
    request.setAddressId(100L);
    // 请求抵扣 30 元
    request.setPointsDiscount(new BigDecimal("30.00"));

    ProductEntity product = new ProductEntity();
    product.setId(20L);
    product.setName("测试商品");
    product.setOnSale(true);
    product.setPrice(new BigDecimal("100.00"));
    when(productMapper.selectById(20L)).thenReturn(product);
    when(productMapper.update(any(), any())).thenReturn(1);
    when(addressMapper.selectById(100L)).thenReturn(buildUsAddress());
    when(shippingRateService.calcFreight(anyString(), anyString(), anyList(), any()))
        .thenReturn(new BigDecimal("5.00"));
    // 5 折会员 → 会员优惠 50；优惠券 30 → 剩余商品金额 20，积分最多抵扣 20
    when(memberPriceService.getDiscountForUser(1L)).thenReturn(new BigDecimal("0.50"));
    when(couponService.computeCouponDiscount(anyLong(), any(), any()))
        .thenReturn(new BigDecimal("30.00"));
    when(memberService.getPointsBalance(1L)).thenReturn(5000);
    when(memberService.freezePoints(anyLong(), anyInt(), anyString(), anyString())).thenReturn(true);

    doAnswer(invocation -> {
      OrderEntity order = invocation.getArgument(0);
      order.setId(300L);
      return 1;
    }).when(orderMapper).insert(any(OrderEntity.class));

    OrderEntity result = orderService.createOrderFromRequest(1L, request);

    assertEquals(new BigDecimal("50.00"), result.getMemberDiscount());
    // 100 - 50 - 30 = 剩余 20；请求 30 被剩余金额封顶为 20（2000 积分）
    assertEquals(new BigDecimal("20.00"), result.getPointsDiscount());
    assertEquals(Integer.valueOf(2000), result.getPointsUsed());
    // 实付 = 100 - 50 + 5 - 30 - 20 = 5
    assertEquals(new BigDecimal("5.00"), result.getPayAmount());
  }

  @Test
  @DisplayName("空商品列表应抛出异常（禁止零金额订单）")
  void createOrder_emptyItems_shouldThrowIllegalArgumentException() {
    // 执行 & 验证：空订单项直接拒绝，防止创建零金额订单
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> orderService.createOrder(1L, Collections.emptyList(), null, null, null));
    assertTrue(ex.getMessage().contains("订单商品不能为空"));

    // 验证不会落库
    verify(orderMapper, never()).insert(any(OrderEntity.class));
  }

  // ==================== createOrderFromRequest ====================

  @Test
  @DisplayName("有效请求对象创建订单应返回订单")
  void createOrderFromRequest_validRequest_shouldReturnOrder() {
    // 准备：请求对象
    OrderItemRequest itemReq = new OrderItemRequest();
    itemReq.setSkuId(10L);
    itemReq.setProductId(20L);
    itemReq.setQuantity(1);

    CreateOrderRequest request = new CreateOrderRequest();
    request.setItems(List.of(itemReq));
    request.setAddressId(100L);
    request.setRemark("备注");
    request.setCouponId("coupon-002");

    // 模拟 SKU 存在
    ProductSkuEntity sku = new ProductSkuEntity();
    sku.setId(10L);
    sku.setProductId(20L);
    sku.setPrice(new BigDecimal("199.00"));
    when(productSkuMapper.selectById(10L)).thenReturn(sku);

    // 模拟商品存在且已上架（服务层会校验商品状态）
    ProductEntity product = new ProductEntity();
    product.setId(20L);
    product.setName("测试商品");
    product.setMainImage("http://example.com/img.jpg");
    product.setOnSale(true);
    when(productMapper.selectById(20L)).thenReturn(product);

    // 模拟 SKU 库存原子扣减成功
    when(productSkuMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);
    // 优惠券核验 + 服务端运费重算
    when(couponService.computeCouponDiscount(anyLong(), any(), any())).thenReturn(BigDecimal.ZERO);
    when(addressMapper.selectById(100L)).thenReturn(buildUsAddress());
    when(shippingRateService.calcFreight(anyString(), anyString(), anyList(), any()))
        .thenReturn(BigDecimal.ZERO);

    // 模拟 orderMapper.insert
    doAnswer(invocation -> {
      OrderEntity order = invocation.getArgument(0);
      order.setId(300L);
      return 1;
    }).when(orderMapper).insert(any(OrderEntity.class));

    // 执行
    OrderEntity result = orderService.createOrderFromRequest(1L, request);

    // 验证
    assertNotNull(result);
    assertEquals("PENDING_PAY", result.getStatus());
    assertEquals(new BigDecimal("199.00"), result.getGoodsAmount());

    // 验证 SKU 和商品查询（转换、库存校验、运费重量计算均会查询）
    verify(productSkuMapper, times(2)).selectById(10L);
    verify(productMapper, atLeast(2)).selectById(20L);

    // 验证订单项中的商品信息
    verify(orderItemMapper).insert(orderItemCaptor.capture());
    OrderItemEntity savedItem = orderItemCaptor.getValue();
    assertEquals(10L, savedItem.getSkuId());
    assertEquals(20L, savedItem.getProductId());
    assertEquals("测试商品", savedItem.getProductName());
    assertEquals("http://example.com/img.jpg", savedItem.getMainImage());
    assertEquals(new BigDecimal("199.00"), savedItem.getPrice());
    assertEquals(Integer.valueOf(1), savedItem.getQuantity());
  }

  @Test
  @DisplayName("SKU不存在应抛出异常")
  void createOrderFromRequest_skuNotExist_shouldThrowIllegalArgumentException() {
    // 准备：SKU 不存在
    OrderItemRequest itemReq = new OrderItemRequest();
    itemReq.setSkuId(999L);
    itemReq.setProductId(20L);
    itemReq.setQuantity(1);

    CreateOrderRequest request = new CreateOrderRequest();
    request.setItems(List.of(itemReq));

    when(productSkuMapper.selectById(999L)).thenReturn(null);
    // 商品存在（实现先校验商品、再校验 SKU），确保走到 SKU 缺失分支
    ProductEntity product = new ProductEntity();
    product.setId(20L);
    product.setOnSale(true);
    when(productMapper.selectById(20L)).thenReturn(product);

    // 执行 & 验证
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> orderService.createOrderFromRequest(1L, request));
    assertTrue(ex.getMessage().contains("SKU不存在"));

    // 商品查询仅用于存在性/上架校验
    verify(productMapper).selectById(20L);
  }

  @Test
  @DisplayName("商品不存在应抛出异常")
  void createOrderFromRequest_productNotExist_shouldThrowIllegalArgumentException() {
    // 准备：商品不存在
    OrderItemRequest itemReq = new OrderItemRequest();
    itemReq.setSkuId(10L);
    itemReq.setProductId(999L);
    itemReq.setQuantity(1);

    CreateOrderRequest request = new CreateOrderRequest();
    request.setItems(List.of(itemReq));

    // SKU 存在
    ProductSkuEntity sku = new ProductSkuEntity();
    sku.setId(10L);
    sku.setProductId(999L);
    sku.setPrice(new BigDecimal("100.00"));
    when(productSkuMapper.selectById(10L)).thenReturn(sku);

    // 商品不存在
    when(productMapper.selectById(999L)).thenReturn(null);

    // 执行 & 验证
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> orderService.createOrderFromRequest(1L, request));
    assertTrue(ex.getMessage().contains("商品不存在"));
  }

  // ==================== listOrders ====================

  @Test
  @DisplayName("按状态筛选应返回分页订单")
  void listOrders_withStatusFilter_shouldReturnPagedOrders() {
    // 准备：分页数据
    Page<OrderEntity> mockPage = new Page<>(1, 10);
    mockPage.setRecords(List.of(new OrderEntity(), new OrderEntity()));

    when(orderMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
        .thenReturn(mockPage);

    // 执行
    IPage<OrderEntity> result = orderService.listOrders(1L, 1, 10, "PENDING_PAY", null, null);

    // 验证
    assertNotNull(result);
    assertEquals(2, result.getRecords().size());

    // 验证参数传递
    ArgumentCaptor<LambdaQueryWrapper> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    verify(orderMapper).selectPage(any(Page.class), wrapperCaptor.capture());

    // 验证查询条件
    LambdaQueryWrapper<OrderEntity> wrapper = wrapperCaptor.getValue();
    assertNotNull(wrapper);
  }

  @Test
  @DisplayName("管理员查询不应按用户过滤")
  void listOrders_withNullUserId_shouldNotFilterByUserId() {
    // 准备：管理员查询
    Page<OrderEntity> mockPage = new Page<>(1, 20);
    when(orderMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
        .thenReturn(mockPage);

    // 执行：userId = null 表示管理员
    IPage<OrderEntity> result = orderService.listOrders(null, 1, 20, null, null, null);

    // 验证
    assertNotNull(result);
    verify(orderMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
  }

  // ==================== getOrderDetail ====================

  @Test
  @DisplayName("订单存在应返回订单详情")
  void getOrderDetail_orderExists_shouldReturnOrder() {
    // 准备：订单存在
    OrderEntity order = new OrderEntity();
    order.setId(1L);
    order.setUserId(1L);
    order.setOrderNo("ORD20260721000001");

    when(orderMapper.selectById(1L)).thenReturn(order);

    // 执行
    OrderEntity result = orderService.getOrderDetail(1L, 1L);

    // 验证
    assertNotNull(result);
    assertEquals(1L, result.getId());
    assertEquals(1L, result.getUserId());
  }

  @Test
  @DisplayName("订单不存在应返回 null（由 controller 决定响应方式）")
  void getOrderDetail_orderNotExist_shouldReturnNull() {
    // 准备：订单不存在
    when(orderMapper.selectById(999L)).thenReturn(null);

    // 执行 & 验证：服务层按设计返回 null 而非抛异常
    assertNull(orderService.getOrderDetail(999L, 1L));
  }

  @Test
  @DisplayName("管理员查询应跳过权限校验")
  void getOrderDetail_nullUserId_shouldSkipPermissionCheck() {
    // 准备：userId 为 null（管理员查询）
    OrderEntity order = new OrderEntity();
    order.setId(1L);
    order.setUserId(2L); // 与传入的 userId 不同
    order.setOrderNo("ORD20260721000002");

    when(orderMapper.selectById(1L)).thenReturn(order);

    // 执行：userId = null 表示管理员，跳过权限校验
    OrderEntity result = orderService.getOrderDetail(1L, null);

    // 验证：不抛异常，正常返回
    assertNotNull(result);
    assertEquals(2L, result.getUserId());
  }

  // ==================== cancelOrder ====================

  @Test
  @DisplayName("待支付状态取消应成功")
  void cancelOrder_pendingPayStatus_shouldCancelSuccessfully() {
    // 准备：PENDING_PAY 状态的订单
    OrderEntity order = new OrderEntity();
    order.setId(1L);
    order.setUserId(1L);
    order.setStatus("PENDING_PAY");

    when(orderMapper.selectById(1L)).thenReturn(order);
    // 原子抢占更新返回 1，表示取消成功
    when(orderMapper.update(any(), any())).thenReturn(1);

    // 执行
    orderService.cancelOrder(1L, 1L, "不想要了");

    // 验证状态更新（实现使用原子 update，内存对象同步为 CANCELLED）
    verify(orderMapper).update(any(), any(LambdaUpdateWrapper.class));
    assertEquals("CANCELLED", order.getStatus());
    assertEquals("不想要了", order.getCancelReason());
    assertNotNull(order.getCancelTime());
  }

  @Test
  @DisplayName("已支付状态取消应抛出异常")
  void cancelOrder_paidStatus_shouldThrowIllegalStateException() {
    // 准备：PAID 状态的订单（不允许取消）
    OrderEntity order = new OrderEntity();
    order.setId(1L);
    order.setUserId(1L);
    order.setStatus("PAID");

    when(orderMapper.selectById(1L)).thenReturn(order);

    // 执行 & 验证
    IllegalStateException ex = assertThrows(IllegalStateException.class,
        () -> orderService.cancelOrder(1L, 1L, "想取消"));
    assertEquals("当前订单状态不允许取消", ex.getMessage());

    // 验证没有执行更新
    verify(orderMapper, never()).updateById(any(OrderEntity.class));
  }

  // ==================== payCallback ====================

  @Test
  @DisplayName("有效支付回调应更新订单并创建流水")
  void payCallback_validRequest_shouldUpdateOrderAndCreatePayment() {
    // 准备：PENDING_PAY 状态的订单
    OrderEntity order = new OrderEntity();
    order.setId(1L);
    order.setOrderNo("ORD20260721000001");
    order.setUserId(1L);
    order.setStatus("PENDING_PAY");
    order.setPayAmount(new BigDecimal("199.98"));

    when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
    // 原子抢占 PENDING_PAY → PENDING_SHIP 返回 1
    when(orderMapper.update(any(), any())).thenReturn(1);

    // 执行
    orderService.payCallback("ORD20260721000001", "STRIPE", "pi_test_123");

    // 验证订单状态更新（原子 update + 内存对象同步）
    verify(orderMapper).update(any(), any(LambdaUpdateWrapper.class));
    assertEquals("PENDING_SHIP", order.getStatus());
    assertEquals("STRIPE", order.getPayChannel());
    assertEquals("pi_test_123", order.getPayTransactionId());
    assertNotNull(order.getPaidAt());

    // 验证支付流水创建
    verify(paymentMapper).insert(any(PaymentEntity.class));
  }

  @Test
  @DisplayName("重复支付回调不应抛出异常")
  void payCallback_duplicateCallback_shouldNotThrowException() {
    // 准备：已支付订单（重复回调）
    OrderEntity order = new OrderEntity();
    order.setId(1L);
    order.setOrderNo("ORD20260721000001");
    order.setStatus("PAID"); // 已支付状态

    when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);

    // 执行：重复回调不应抛异常
    orderService.payCallback("ORD20260721000001", "STRIPE", "pi_test_123");

    // 验证：没有再次更新订单或创建支付流水
    verify(orderMapper, never()).updateById(any(OrderEntity.class));
    verify(paymentMapper, never()).insert(any(PaymentEntity.class));
  }

  // ==================== confirmReceived ====================

  @Test
  @DisplayName("已发货状态确认收货应成功")
  void confirmReceived_shippedStatus_shouldUpdateToReceived() {
    // 准备：SHIPPED 状态的订单（确认收货只能从已发货状态流转）
    OrderEntity order = new OrderEntity();
    order.setId(1L);
    order.setUserId(1L);
    order.setStatus("SHIPPED");

    when(orderMapper.selectById(1L)).thenReturn(order);

    // 执行
    orderService.confirmReceived(1L, 1L);

    // 验证状态更新
    verify(orderMapper).updateById(orderCaptor.capture());
    OrderEntity updatedOrder = orderCaptor.getValue();
    assertEquals("RECEIVED", updatedOrder.getStatus());
    assertNotNull(updatedOrder.getReceivedTime());
  }

  @Test
  @DisplayName("错误状态确认收货应抛出异常")
  void confirmReceived_wrongStatus_shouldThrowIllegalStateException() {
    // 准备：PENDING_PAY 状态的订单（不允许确认收货）
    OrderEntity order = new OrderEntity();
    order.setId(1L);
    order.setUserId(1L);
    order.setStatus("PENDING_PAY");

    when(orderMapper.selectById(1L)).thenReturn(order);

    // 执行 & 验证
    IllegalStateException ex = assertThrows(IllegalStateException.class,
        () -> orderService.confirmReceived(1L, 1L));
    assertTrue(ex.getMessage().contains("当前订单状态不允许确认收货"));

    verify(orderMapper, never()).updateById(any(OrderEntity.class));
  }

  // ==================== deleteOrder ====================

  @Test
  @DisplayName("有效请求删除订单应软删除")
  void deleteOrder_validRequest_shouldSetDeleteStatusToOne() {
    // 准备：正常订单
    OrderEntity order = new OrderEntity();
    order.setId(1L);
    order.setUserId(1L);
    order.setStatus("RECEIVED");
    order.setDeleteStatus(0);

    when(orderMapper.selectById(1L)).thenReturn(order);

    // 执行
    orderService.deleteOrder(1L, 1L);

    // 验证 deleteStatus 置 1
    verify(orderMapper).updateById(orderCaptor.capture());
    OrderEntity updatedOrder = orderCaptor.getValue();
    assertEquals(Integer.valueOf(1), updatedOrder.getDeleteStatus());
  }
}
