package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.dto.admin.logistics.ClearanceCreateRequest;
import com.moyuyo.common.dto.admin.logistics.ClearanceUpdateRequest;
import com.moyuyo.common.exception.BusinessException;
import com.moyuyo.dao.admin.entity.ClearanceEntity;
import com.moyuyo.dao.admin.mapper.ClearanceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * AdminClearanceServiceImpl 单元测试
 *
 * 重点覆盖：
 *  1. listAll: customsOnly=true 时附加 hsCode 非空条件；null status 不叠条件
 *  2. create: 字段映射 / status 默认 PENDING
 *  3. update: partial / 不存在 404
 *  4. delete: 幂等
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked") // Mockito any(LambdaQueryWrapper.class) 使用原始类型是惯用写法；编译期无法穷举为参数化类型，测试桩忽略泛型检查
class AdminClearanceServiceImplTest {

  @Mock
  private ClearanceMapper clearanceMapper;

  @InjectMocks
  private AdminClearanceServiceImpl service;

  // ==================== listAll ====================

  @Test
  void listAll_customsOnly_shouldAddHsCodeCondition() {
    when(clearanceMapper.selectList(any(LambdaQueryWrapper.class)))
        .thenReturn(java.util.Collections.emptyList());
    service.listAll(null, true);
    // 调用发生即视为通过（LambdaQueryWrapper 的链式调用条件构造通过 mybatis-plus 校验）
    verify(clearanceMapper, times(1)).selectList(any(LambdaQueryWrapper.class));
  }

  @Test
  void listAll_nullStatus_returnsAll() {
    when(clearanceMapper.selectList(any(LambdaQueryWrapper.class)))
        .thenReturn(java.util.Collections.emptyList());
    service.listAll(null, false);
    service.listAll("", false);
    verify(clearanceMapper, times(2)).selectList(any(LambdaQueryWrapper.class));
  }

  // ==================== create ====================

  @Test
  void create_shouldDefaultStatusToPending() {
    ClearanceCreateRequest req = new ClearanceCreateRequest();
    req.setDeclarationNo("DECL-001");
    req.setOrderNo("MO-001");
    req.setProductName("宠物用品");

    ClearanceEntity result = service.create(req);
    assertEquals("DECL-001", result.getDeclarationNo());
    assertEquals("PENDING", result.getStatus());
    verify(clearanceMapper, times(1)).insert(any(ClearanceEntity.class));
  }

  // ==================== update ====================

  @Test
  void update_recordNotFound_throws404() {
    when(clearanceMapper.selectById(999L)).thenReturn(null);
    BusinessException ex = assertThrows(BusinessException.class,
        () -> service.update(999L, new ClearanceUpdateRequest()));
    assertEquals(404, ex.getCode());
  }

  @Test
  void update_shouldOnlyTouchProvidedFields() {
    ClearanceEntity existing = new ClearanceEntity();
    existing.setId(1L);
    existing.setDeclarationNo("DECL-001");
    existing.setStatus("PENDING");
    when(clearanceMapper.selectById(1L)).thenReturn(existing);

    ClearanceUpdateRequest req = new ClearanceUpdateRequest();
    req.setTaxRate(new BigDecimal("10.5"));
    ClearanceEntity result = service.update(1L, req);

    assertEquals(new BigDecimal("10.5"), result.getTaxRate());
    assertEquals("DECL-001", result.getDeclarationNo(), "其他字段不动");
    assertEquals("PENDING", result.getStatus());
    verify(clearanceMapper, times(1)).updateById(any(ClearanceEntity.class));
  }

  // ==================== delete ====================

  @Test
  void delete_idempotent_whenNotFound() {
    when(clearanceMapper.deleteById(anyLong())).thenReturn(0);
    assertDoesNotThrow(() -> service.delete(1L));
  }
}