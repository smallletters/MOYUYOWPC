package com.moyuyo.service.impl;

import com.moyuyo.dao.entity.FlashSaleEntity;
import com.moyuyo.dao.entity.ProductEntity;
import com.moyuyo.dao.entity.ProductSkuEntity;
import com.moyuyo.dao.mapper.FlashSaleMapper;
import com.moyuyo.dao.mapper.FlashSaleOrderMapper;
import com.moyuyo.dao.mapper.ProductMapper;
import com.moyuyo.dao.mapper.ProductSkuMapper;
import com.moyuyo.service.admin.impl.AdminFlashSaleServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * AdminFlashSaleServiceImpl#update 单元测试
 * <p>
 * 重点覆盖 productId 与 skuId 归属一致性的修复场景，避免脏数据：
 * 1) 只改 productId，旧 skuId 属于新商品 → 正常更新
 * 2) 只改 productId，旧 skuId 不属于新商品 → 抛 IllegalArgumentException，updateById 不被调用
 * 3) 只改 productId，skuId 为空 → 不走校验，正常更新
 * 4) 同时改 productId + skuId → 仍以 skuId 自身校验为准（用新 productId）
 * 5) id 不存在 → 静默 return
 * 6) id 缺失 → 静默 return
 */
@ExtendWith(MockitoExtension.class)
class AdminFlashSaleServiceImplUpdateTest {

    @Mock
    private FlashSaleMapper flashSaleMapper;
    @Mock
    private FlashSaleOrderMapper flashSaleOrderMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private ProductSkuMapper productSkuMapper;

    @InjectMocks
    private AdminFlashSaleServiceImpl service;

    @Captor
    private ArgumentCaptor<FlashSaleEntity> entityCaptor;

    /** 构造一个数据库已存在的秒杀活动：属于商品 100、绑定 SKU 50 */
    private FlashSaleEntity buildExistingFlashSale() {
        FlashSaleEntity e = new FlashSaleEntity();
        e.setId(1L);
        e.setName("old-name");
        e.setProductId(100L);
        e.setSkuId(50L);
        return e;
    }

    private ProductEntity product(long id) {
        ProductEntity p = new ProductEntity();
        p.setId(id);
        p.setName("商品-" + id);
        return p;
    }

    private ProductSkuEntity sku(long skuId, long productId) {
        ProductSkuEntity s = new ProductSkuEntity();
        s.setId(skuId);
        s.setProductId(productId);
        return s;
    }

    @Test
    @DisplayName("只改 productId：旧 skuId 仍属于新商品 → 正常更新")
    void update_onlyProductId_skuBelongsToNewProduct_success() {
        FlashSaleEntity existing = buildExistingFlashSale();
        when(flashSaleMapper.selectById(1L)).thenReturn(existing);
        // 新商品 200 存在
        when(productMapper.selectById(200L)).thenReturn(product(200L));
        // SKU 50 现在属于商品 200（数据已迁移/重新归属）
        when(productSkuMapper.selectById(50L)).thenReturn(sku(50L, 200L));

        Map<String, Object> data = new HashMap<>();
        data.put("id", 1L);
        data.put("productId", 200L);

        service.update(data);

        verify(flashSaleMapper, times(1)).updateById(entityCaptor.capture());
        FlashSaleEntity saved = entityCaptor.getValue();
        assertEquals(200L, saved.getProductId(), "productId 应被更新到新值");
        assertEquals(50L, saved.getSkuId(), "skuId 不在入参中应保留旧值");
    }

    @Test
    @DisplayName("只改 productId：旧 skuId 不属于新商品 → 抛异常，不写库")
    void update_onlyProductId_skuNotBelongToNewProduct_throwAndNoUpdate() {
        FlashSaleEntity existing = buildExistingFlashSale();
        when(flashSaleMapper.selectById(1L)).thenReturn(existing);
        // 新商品 200 存在
        when(productMapper.selectById(200L)).thenReturn(product(200L));
        // SKU 50 仍属于商品 100 → 与新 productId 不一致
        when(productSkuMapper.selectById(50L)).thenReturn(sku(50L, 100L));

        Map<String, Object> data = new HashMap<>();
        data.put("id", 1L);
        data.put("productId", 200L);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.update(data),
                "SKU 不属于新商品时应阻断写入");
        assertTrue(ex.getMessage().contains("SKU 不属于该商品"),
                "异常消息应说明 SKU 不属于该商品，实际：" + ex.getMessage());

        // 关键：updateById 一次都没被调用，数据库没被污染
        verify(flashSaleMapper, never()).updateById(any(FlashSaleEntity.class));
    }

    @Test
    @DisplayName("只改 productId：skuId 为空 → 跳过 SKU 校验，正常更新")
    void update_onlyProductId_skuIdIsNull_success() {
        FlashSaleEntity existing = buildExistingFlashSale();
        existing.setSkuId(null); // 原本就没绑 SKU
        when(flashSaleMapper.selectById(1L)).thenReturn(existing);
        when(productMapper.selectById(200L)).thenReturn(product(200L));

        Map<String, Object> data = new HashMap<>();
        data.put("id", 1L);
        data.put("productId", 200L);

        service.update(data);

        verify(productSkuMapper, never()).selectById(anyLong());
        verify(flashSaleMapper, times(1)).updateById(entityCaptor.capture());
        assertEquals(200L, entityCaptor.getValue().getProductId());
        assertNull(entityCaptor.getValue().getSkuId());
    }

    @Test
    @DisplayName("同时改 productId + skuId：仍以新 productId 校验新 skuId → 通过")
    void update_changeBothProductAndSku_success() {
        FlashSaleEntity existing = buildExistingFlashSale();
        when(flashSaleMapper.selectById(1L)).thenReturn(existing);
        when(productMapper.selectById(200L)).thenReturn(product(200L));
        // 修复点会先校验"旧 skuId=50 是否属于新商品 200"，再走 skuId 更新分支校验新 skuId=70
        when(productSkuMapper.selectById(50L)).thenReturn(sku(50L, 200L));
        when(productSkuMapper.selectById(70L)).thenReturn(sku(70L, 200L));

        Map<String, Object> data = new HashMap<>();
        data.put("id", 1L);
        data.put("productId", 200L);
        data.put("skuId", 70L);

        service.update(data);

        verify(flashSaleMapper, times(1)).updateById(entityCaptor.capture());
        FlashSaleEntity saved = entityCaptor.getValue();
        assertEquals(200L, saved.getProductId());
        assertEquals(70L, saved.getSkuId());
    }

    @Test
    @DisplayName("同时改 productId + skuId：新 skuId 不属于新商品 → 抛异常")
    void update_changeBothProductAndSku_mismatch_throw() {
        FlashSaleEntity existing = buildExistingFlashSale();
        when(flashSaleMapper.selectById(1L)).thenReturn(existing);
        when(productMapper.selectById(200L)).thenReturn(product(200L));
        // 修复点：先校验旧 skuId=50（属于商品 100）与新 productId=200 不一致 → 抛异常
        when(productSkuMapper.selectById(50L)).thenReturn(sku(50L, 100L));

        Map<String, Object> data = new HashMap<>();
        data.put("id", 1L);
        data.put("productId", 200L);
        data.put("skuId", 70L);

        assertThrows(IllegalArgumentException.class, () -> service.update(data));
        verify(flashSaleMapper, never()).updateById(any(FlashSaleEntity.class));
    }

    @Test
    @DisplayName("id 不存在 → 静默 return，不调用 updateById")
    void update_idNotFound_silentReturn() {
        when(flashSaleMapper.selectById(99L)).thenReturn(null);

        Map<String, Object> data = new HashMap<>();
        data.put("id", 99L);
        data.put("productId", 200L);

        service.update(data);

        verify(productMapper, never()).selectById(anyLong());
        verify(flashSaleMapper, never()).updateById(any(FlashSaleEntity.class));
    }

    @Test
    @DisplayName("id 缺失 → 静默 return，不调用任何写操作")
    void update_idMissing_silentReturn() {
        Map<String, Object> data = new HashMap<>();
        data.put("productId", 200L);

        service.update(data);

        verify(flashSaleMapper, never()).selectById(anyLong());
        verify(flashSaleMapper, never()).updateById(any(FlashSaleEntity.class));
    }

    @Test
    @DisplayName("商品不存在 → 抛异常，不写库（避免 productId 指向不存在的商品）")
    void update_productNotFound_throw() {
        FlashSaleEntity existing = buildExistingFlashSale();
        when(flashSaleMapper.selectById(1L)).thenReturn(existing);
        when(productMapper.selectById(200L)).thenReturn(null); // 商品不存在

        Map<String, Object> data = new HashMap<>();
        data.put("id", 1L);
        data.put("productId", 200L);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> service.update(data));
        assertTrue(ex.getMessage().contains("商品不存在"));
        verify(flashSaleMapper, never()).updateById(any(FlashSaleEntity.class));
    }
}
