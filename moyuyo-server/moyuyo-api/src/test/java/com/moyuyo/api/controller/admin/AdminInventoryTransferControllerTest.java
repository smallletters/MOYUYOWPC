package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.common.dto.admin.OperationResult;
import com.moyuyo.common.dto.admin.PageResponse;
import com.moyuyo.common.dto.admin.inventory.InventoryTransferCreateRequest;
import com.moyuyo.common.dto.admin.inventory.InventoryTransferRejectRequest;
import com.moyuyo.common.dto.admin.inventory.InventoryTransferVO;
import com.moyuyo.service.admin.AdminInventoryTransferService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 管理后台库存调拨 Controller 单元测试
 * 覆盖:list / create / approve / reject / complete 五个端点
 */
@ExtendWith(MockitoExtension.class)
class AdminInventoryTransferControllerTest {

  @Mock
  private AdminInventoryTransferService adminInventoryTransferService;

  @InjectMocks
  private AdminInventoryTransferController adminInventoryTransferController;

  /** 构造测试 VO */
  private InventoryTransferVO buildVO(Long id, String status) {
    InventoryTransferVO vo = new InventoryTransferVO();
    vo.setId(id);
    vo.setSkuId(100L);
    vo.setStatus(status);
    vo.setQuantity(5);
    vo.setCreateTime(LocalDateTime.of(2026, 8, 4, 10, 0, 0));
    return vo;
  }

  // ============ list ============

  @Test
  void list_正常调用_返回分页VO列表() {
    // given:Service 返回 2 条记录
    PageResponse<InventoryTransferVO> pageResp = new PageResponse<>();
    pageResp.setRecords(List.of(buildVO(1L, "pending"), buildVO(2L, "approved")));
    pageResp.setTotal(2);
    pageResp.setPage(1);
    pageResp.setSize(10);
    when(adminInventoryTransferService.listAll(1, 10, "pending")).thenReturn(pageResp);

    // when
    Result<PageResponse<InventoryTransferVO>> result = adminInventoryTransferController.list(1, 10, "pending");

    // then
    assertEquals(0, result.getCode());
    assertEquals(2, result.getData().getTotal());
    assertEquals(2, result.getData().getRecords().size());
    assertEquals("pending", result.getData().getRecords().get(0).getStatus());
  }

  // ============ create ============

  @Test
  void create_有效请求_委托Service并返回成功() {
    // given
    InventoryTransferCreateRequest request = new InventoryTransferCreateRequest();
    request.setSkuId(100L);
    request.setFromWarehouseId(10L);
    request.setToWarehouseId(20L);
    request.setQuantity(5);

    // when
    Result<OperationResult> result = adminInventoryTransferController.create(request);

    // then:验证 Service 被调用且参数正确
    ArgumentCaptor<InventoryTransferCreateRequest> captor = ArgumentCaptor.forClass(InventoryTransferCreateRequest.class);
    verify(adminInventoryTransferService).create(captor.capture());
    assertEquals(100L, captor.getValue().getSkuId());
    assertEquals(10L, captor.getValue().getFromWarehouseId());
    assertEquals(20L, captor.getValue().getToWarehouseId());
    assertEquals(5, captor.getValue().getQuantity());

    // 返回体校验
    assertEquals(0, result.getCode());
    assertEquals("创建成功", result.getData().getMessage());
  }

  // ============ approve ============

  @Test
  void approve_传入ID_委托Service并返回成功() {
    Result<OperationResult> result = adminInventoryTransferController.approve(1L);

    verify(adminInventoryTransferService).approve(1L);
    assertEquals(0, result.getCode());
    assertEquals(1L, result.getData().getId());
    assertEquals("审批通过成功", result.getData().getMessage());
  }

  // ============ reject ============

  @Test
  void reject_带原因_委托Service并传递原因() {
    // given
    InventoryTransferRejectRequest request = new InventoryTransferRejectRequest();
    request.setReason("库存不足");

    // when
    Result<OperationResult> result = adminInventoryTransferController.reject(1L, request);

    // then:验证 reason 被正确传递
    verify(adminInventoryTransferService).reject(eq(1L), eq("库存不足"));
    assertEquals(0, result.getCode());
    assertEquals(1L, result.getData().getId());
    assertEquals("已驳回", result.getData().getMessage());
  }

  @Test
  void reject_请求体为空_reason为null仍正常调用() {
    // when:request 为 null
    Result<OperationResult> result = adminInventoryTransferController.reject(1L, null);

    // then:Service 应以 null reason 被调用
    verify(adminInventoryTransferService).reject(eq(1L), isNull());
    assertEquals(0, result.getCode());
    assertEquals("已驳回", result.getData().getMessage());
  }

  @Test
  void reject_请求体存在但reason为空_reason为null仍正常调用() {
    // given:request 对象存在但 reason 为 null
    InventoryTransferRejectRequest request = new InventoryTransferRejectRequest();

    // when
    Result<OperationResult> result = adminInventoryTransferController.reject(1L, request);

    // then
    verify(adminInventoryTransferService).reject(eq(1L), isNull());
    assertEquals(0, result.getCode());
  }

  // ============ complete ============

  @Test
  void complete_传入ID_委托Service并返回成功() {
    Result<OperationResult> result = adminInventoryTransferController.complete(1L);

    verify(adminInventoryTransferService).complete(1L);
    assertEquals(0, result.getCode());
    assertEquals(1L, result.getData().getId());
    assertEquals("已确认完成", result.getData().getMessage());
  }
}
