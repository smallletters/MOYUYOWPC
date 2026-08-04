package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.common.dto.admin.order.*;
import com.moyuyo.service.admin.AdminOrderOpsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 管理后台订单运营 Controller 单元测试
 * 覆盖:createExport / batchShip / updateRemark / recordPrint /
 *      createPriceModify / createIntercept / releaseIntercept 七个 DTO 化改造后的方法
 */
@ExtendWith(MockitoExtension.class)
class AdminOrderOpsControllerTest {

  @Mock
  private AdminOrderOpsService adminOrderOpsService;

  @InjectMocks
  private AdminOrderOpsController adminOrderOpsController;

  // ============ createExport ============

  @Test
  void createExport_有效请求_调用Service并返回任务信息() {
    // given:Service 返回 taskId 和初始状态
    when(adminOrderOpsService.createExportTask(any(Map.class)))
        .thenReturn(Map.of("taskId", "EXPORT-123", "status", "PENDING"));
    OrderExportCreateRequest request = new OrderExportCreateRequest();
    request.setTaskName("日终导出");
    request.setOrderScope("已支付订单");
    request.setFormat("CSV");

    // when
    Result<Map<String, Object>> result = adminOrderOpsController.createExport(request);

    // then:返回体应包含请求字段和 Service 返回字段
    assertEquals(0, result.getCode());
    Map<String, Object> data = result.getData();
    assertEquals("EXPORT-123", data.get("taskId"));
    assertEquals("日终导出", data.get("taskName"));
    assertEquals("已支付订单", data.get("orderScope"));
    assertEquals("CSV", data.get("format"));
    assertEquals("PENDING", data.get("status"));
    assertEquals("导出任务已创建", data.get("message"));
  }

  // ============ batchShip ============

  @Test
  void batchShip_空ids_返回400() {
    // given:ids 为空列表
    BatchShipRequest request = new BatchShipRequest();
    request.setIds(List.of());

    // when
    Result<Map<String, Object>> result = adminOrderOpsController.batchShip(request);

    // then:参数校验失败,不应调用 Service
    assertEquals(400, result.getCode());
    verify(adminOrderOpsService, never()).batchShip(anyList(), anyString(), anyString());
  }

  @Test
  void batchShip_有效请求_使用默认承运商并调用Service() {
    // given:ids 非空,未指定 carrier 和 trackingNo
    BatchShipRequest request = new BatchShipRequest();
    request.setIds(List.of(1L, 2L, 3L));

    // when
    Result<Map<String, Object>> result = adminOrderOpsController.batchShip(request);

    // then:调用 Service 时使用默认值 carrier=默认承运商, trackingNo=""
    verify(adminOrderOpsService).batchShip(List.of(1L, 2L, 3L), "默认承运商", "");
    assertEquals(0, result.getCode());
    assertEquals(3, result.getData().get("count"));
    assertEquals("批量发货成功", result.getData().get("message"));
  }

  @Test
  void batchShip_指定承运商_使用请求中的值() {
    BatchShipRequest request = new BatchShipRequest();
    request.setIds(List.of(10L));
    request.setCarrier("FedEx");
    request.setTrackingNo("FDX123");

    adminOrderOpsController.batchShip(request);

    verify(adminOrderOpsService).batchShip(List.of(10L), "FedEx", "FDX123");
  }

  // ============ updateRemark ============

  @Test
  void updateRemark_有效请求_调用Service() {
    OrderRemarkUpdateRequest request = new OrderRemarkUpdateRequest();
    request.setRemark("VIP 客户,优先处理");

    Result<Map<String, Object>> result = adminOrderOpsController.updateRemark(100L, request);

    verify(adminOrderOpsService).updateRemark(100L, "VIP 客户,优先处理");
    assertEquals(0, result.getCode());
    assertEquals(100L, result.getData().get("id"));
    assertEquals("备注更新成功", result.getData().get("message"));
  }

  @Test
  void updateRemark_remark为空_允许清空备注() {
    // given:remark 为 null,表示清空备注
    OrderRemarkUpdateRequest request = new OrderRemarkUpdateRequest();

    adminOrderOpsController.updateRemark(100L, request);

    // then:应调用 Service 并传 null
    verify(adminOrderOpsService).updateRemark(100L, null);
  }

  // ============ recordPrint ============

  @Test
  void recordPrint_orderId为空_返回400() {
    OrderPrintRecordRequest request = new OrderPrintRecordRequest();
    // orderId 未设置

    Result<Map<String, Object>> result = adminOrderOpsController.recordPrint(request);

    assertEquals(400, result.getCode());
    verify(adminOrderOpsService, never()).recordPrint(anyLong(), anyString(), anyString(), anyString(), anyString());
  }

  @Test
  void recordPrint_有效请求_使用请求中的值() {
    OrderPrintRecordRequest request = new OrderPrintRecordRequest();
    request.setOrderId(100L);
    request.setPrintType("SHIPPING");
    request.setTemplateName("快递单模板A");
    request.setPaperSize("A5");
    request.setOperator("张三");

    adminOrderOpsController.recordPrint(request);

    verify(adminOrderOpsService).recordPrint(100L, "SHIPPING", "快递单模板A", "A5", "张三");
  }

  @Test
  void recordPrint_未指定可选字段_使用默认值() {
    OrderPrintRecordRequest request = new OrderPrintRecordRequest();
    request.setOrderId(100L);

    adminOrderOpsController.recordPrint(request);

    // printType 默认 PICK, templateName 默认"默认模板", paperSize 默认 A4, operator 默认"系统"
    verify(adminOrderOpsService).recordPrint(100L, "PICK", "默认模板", "A4", "系统");
  }

  // ============ createPriceModify ============

  @Test
  void createPriceModify_orderId和orderNo都为空_返回400() {
    OrderPriceModifyRequest request = new OrderPriceModifyRequest();
    request.setAdjustAmount(new BigDecimal("10"));

    Result<Map<String, Object>> result = adminOrderOpsController.createPriceModify(request);

    assertEquals(400, result.getCode());
    verify(adminOrderOpsService, never())
        .createPriceModify(any(), any(), any(), any(), anyString(), anyString(), anyString());
  }

  @Test
  void createPriceModify_adjustAmount为空_返回400() {
    OrderPriceModifyRequest request = new OrderPriceModifyRequest();
    request.setOrderId(100L);

    Result<Map<String, Object>> result = adminOrderOpsController.createPriceModify(request);

    assertEquals(400, result.getCode());
    verify(adminOrderOpsService, never())
        .createPriceModify(any(), any(), any(), any(), anyString(), anyString(), anyString());
  }

  @Test
  void createPriceModify_通过orderId_有效请求_调用Service() {
    OrderPriceModifyRequest request = new OrderPriceModifyRequest();
    request.setOrderId(100L);
    request.setOriginalAmount(new BigDecimal("100"));
    request.setAdjustAmount(new BigDecimal("-20"));
    request.setReason("促销折扣");
    request.setReasonType("PROMOTION");
    request.setOperator("李四");

    adminOrderOpsController.createPriceModify(request);

    verify(adminOrderOpsService).createPriceModify(
        100L, null, new BigDecimal("100"), new BigDecimal("-20"), "促销折扣", "PROMOTION", "李四");
  }

  @Test
  void createPriceModify_通过orderNo_有效请求_调用Service() {
    OrderPriceModifyRequest request = new OrderPriceModifyRequest();
    request.setOrderNo("ORD20260804001");
    request.setAdjustAmount(new BigDecimal("5"));

    adminOrderOpsController.createPriceModify(request);

    verify(adminOrderOpsService).createPriceModify(
        null, "ORD20260804001", null, new BigDecimal("5"), "", "MANUAL", "系统");
  }

  // ============ createIntercept ============

  @Test
  void createIntercept_orderId为空_返回400() {
    OrderInterceptRequest request = new OrderInterceptRequest();

    Result<Map<String, Object>> result = adminOrderOpsController.createIntercept(request);

    assertEquals(400, result.getCode());
    verify(adminOrderOpsService, never())
        .createIntercept(anyLong(), anyString(), anyString(), anyString(), anyString());
  }

  @Test
  void createIntercept_有效请求_使用请求中的值() {
    OrderInterceptRequest request = new OrderInterceptRequest();
    request.setOrderId(100L);
    request.setInterceptType("RISK");
    request.setReason("风控拦截");
    request.setReasonTemplate("HIGH_RISK");
    request.setOperator("风控系统");

    adminOrderOpsController.createIntercept(request);

    verify(adminOrderOpsService).createIntercept(100L, "RISK", "风控拦截", "HIGH_RISK", "风控系统");
  }

  @Test
  void createIntercept_未指定可选字段_使用默认值() {
    OrderInterceptRequest request = new OrderInterceptRequest();
    request.setOrderId(100L);

    adminOrderOpsController.createIntercept(request);

    // interceptType 默认 MANUAL, reason 默认"", reasonTemplate 默认"", operator 默认"系统"
    verify(adminOrderOpsService).createIntercept(100L, "MANUAL", "", "", "系统");
  }

  // ============ releaseIntercept ============

  @Test
  void releaseIntercept_有效请求_使用请求中的值() {
    OrderInterceptReleaseRequest request = new OrderInterceptReleaseRequest();
    request.setReleaseReason("误拦截,已核实");
    request.setReleaseOperator("王五");

    adminOrderOpsController.releaseIntercept(50L, request);

    verify(adminOrderOpsService).releaseIntercept(50L, "误拦截,已核实", "王五");
  }

  @Test
  void releaseIntercept_未指定字段_使用默认值() {
    OrderInterceptReleaseRequest request = new OrderInterceptReleaseRequest();

    adminOrderOpsController.releaseIntercept(50L, request);

    // releaseReason 默认"", releaseOperator 默认"系统"
    verify(adminOrderOpsService).releaseIntercept(50L, "", "系统");
  }
}
