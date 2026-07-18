package com.moyuyo.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.Result;
import com.moyuyo.dao.admin.entity.InventoryCheckEntity;
import com.moyuyo.dao.admin.mapper.InventoryCheckMapper;
import com.moyuyo.dao.entity.ProductEntity;
import com.moyuyo.dao.entity.ProductSkuEntity;
import com.moyuyo.dao.mapper.ProductMapper;
import com.moyuyo.dao.mapper.ProductSkuMapper;
import com.moyuyo.service.admin.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Tag(name = "管理后台 - 库存管理")
@RestController
@RequestMapping("/api/admin/inventory")
public class AdminInventoryController {

    private final InventoryService inventoryService;

    private final ProductSkuMapper productSkuMapper;

    private final ProductMapper productMapper;

    // 新DAO模块maven安装失败时允许为null，避免ClassNotFoundException
    @Autowired(required = false)
  private Object inventoryCheckMapper;

    // 手动构造器注入必需的依赖
    public AdminInventoryController(InventoryService inventoryService,
                                     ProductSkuMapper productSkuMapper,
                                     ProductMapper productMapper) {
        this.inventoryService = inventoryService;
        this.productSkuMapper = productSkuMapper;
        this.productMapper = productMapper;
    }

    @Operation(summary = "库存概览")
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        try {
            return Result.success(inventoryService.getInventoryOverview());
        } catch (Exception e) {
            return Result.error("查询库存概览失败: " + e.getMessage());
        }
    }

    @Operation(summary = "预警列表")
    @GetMapping("/alerts")
    public Result<List<Map<String, Object>>> alerts() {
        try {
            return Result.success(inventoryService.getAlertList());
        } catch (Exception e) {
            return Result.error("查询预警列表失败: " + e.getMessage());
        }
    }

    @Operation(summary = "商品库存列表")
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size) {
        try {
            // 从 mo_product_sku 分页查询
            Page<ProductSkuEntity> skuPage = productSkuMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<ProductSkuEntity>()
                    .orderByDesc(ProductSkuEntity::getId));

            // 批量查询商品信息（商品名称和分类）
            Set<Long> productIds = skuPage.getRecords().stream()
                .map(ProductSkuEntity::getProductId)
                .collect(Collectors.toSet());

            Map<Long, ProductEntity> productMap = new HashMap<>();
            if (!productIds.isEmpty()) {
                List<ProductEntity> products = productMapper.selectBatchIds(productIds);
                productMap = products.stream()
                    .collect(Collectors.toMap(ProductEntity::getId, p -> p));
            }

            // 构建返回数据
            List<Map<String, Object>> list = new ArrayList<>();
            for (ProductSkuEntity sku : skuPage.getRecords()) {
                ProductEntity product = productMap.get(sku.getProductId());
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", sku.getId());
                item.put("sku", sku.getSkuCode());
                item.put("name", product != null ? product.getName() : "未知商品");
                item.put("category", product != null ? product.getCategoryId() : null);
                item.put("stock", sku.getStock() != null ? sku.getStock() : 0);
                // 锁定库存：暂时保留为0，后续从订单未完成数量统计
                item.put("lockedStock", 0);
                item.put("availableStock", sku.getStock() != null ? sku.getStock() : 0);
                // 安全库存：取当前库存的10%作为安全库存线
                BigDecimal stockBd = new BigDecimal(sku.getStock() != null ? sku.getStock() : 0);
                item.put("safetyStock", stockBd.multiply(new BigDecimal("0.1")).setScale(0, RoundingMode.HALF_UP).intValue());
                item.put("price", sku.getPrice() != null ? sku.getPrice() : BigDecimal.ZERO);
                item.put("lastCheckTime", null);
                list.add(item);
            }

            // 返回含分页信息的数据
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("records", list);
            result.put("total", skuPage.getTotal());
            result.put("page", skuPage.getCurrent());
            result.put("size", skuPage.getSize());
            result.put("pages", skuPage.getPages());
            return Result.success(result);
        } catch (Exception e) {
            return Result.error("查询库存列表失败: " + e.getMessage());
        }
    }

    @Operation(summary = "更新库存")
    @PutMapping("/{id}/stock")
    public Result<Map<String, Object>> updateStock(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            Object stockObj = body.get("stock");
            if (stockObj == null) {
                return Result.error("库存数量不能为空");
            }
            Integer newStock = Integer.valueOf(stockObj.toString());
            ProductSkuEntity sku = productSkuMapper.selectById(id);
            if (sku == null) {
                return Result.error("SKU不存在");
            }
            sku.setStock(newStock);
            productSkuMapper.updateById(sku);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", id);
            result.put("stock", newStock);
            result.put("message", "库存更新成功");
            return Result.success(result);
        } catch (Exception e) {
            return Result.error("更新库存失败: " + e.getMessage());
        }
    }

    @Operation(summary = "新增盘点")
    @PostMapping("/check")
    public Result<Map<String, Object>> createCheck(@RequestBody Map<String, Object> body) {
        // 新Mapper可能因maven安装失败为null
        if (inventoryCheckMapper == null) {
            return Result.error("盘点服务暂不可用");
        }
        // 写入 mo_inventory_check 表
        InventoryCheckEntity entity = new InventoryCheckEntity();
        entity.setCheckNo("CK" + System.currentTimeMillis());
        if (body.get("skuId") != null) entity.setSkuId(Long.valueOf(body.get("skuId").toString()));
        if (body.get("productName") != null) entity.setProductName(body.get("productName").toString());
        if (body.get("skuSpec") != null) entity.setSkuSpec(body.get("skuSpec").toString());
        if (body.get("bookQuantity") != null) entity.setBookQuantity(Integer.valueOf(body.get("bookQuantity").toString()));
        if (body.get("actualQuantity") != null) entity.setActualQuantity(Integer.valueOf(body.get("actualQuantity").toString()));
        if (body.get("remark") != null) entity.setRemark(body.get("remark").toString());
        if (body.get("checker") != null) entity.setChecker(body.get("checker").toString());
        entity.setStatus("PENDING");
        ((InventoryCheckMapper) inventoryCheckMapper).insert(entity);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", entity.getId());
        result.put("checkNo", entity.getCheckNo());
        result.put("message", "盘点任务创建成功");
        return Result.success(result);
    }
}
