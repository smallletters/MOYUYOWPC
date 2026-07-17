package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.service.admin.FinanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Tag(name = "管理后台 - 财务管理")
@RestController
@RequestMapping("/api/admin/finance")
@RequiredArgsConstructor
public class AdminFinanceController {

    private final FinanceService financeService;

    @Operation(summary = "财务概览")
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.success(financeService.getFinanceOverview());
    }

    @Operation(summary = "结算明细列表")
    @GetMapping("/settlements")
    public Result<List<Map<String, Object>>> settlements(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size) {
        return Result.success(financeService.getSettlementList());
    }

    @Operation(summary = "结算详情")
    @GetMapping("/settlements/{id}")
    public Result<Map<String, Object>> settlementDetail(@PathVariable Long id) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("settlementNo", "SET" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + String.format("%04d", id));
        data.put("period", "2026-06-15");
        data.put("orderCount", 236);
        data.put("totalAmount", 85600);
        data.put("fee", 856);
        data.put("netAmount", 84744);
        data.put("status", "SETTLED");
        data.put("settleTime", LocalDateTime.now().minusDays(1));

        List<Map<String, Object>> orders = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Map<String, Object> order = new LinkedHashMap<>();
            order.put("orderNo", "MOY202607" + String.format("%04d", i));
            order.put("amount", 100 + i * 50);
            order.put("fee", 1 + i * 5);
            order.put("payTime", LocalDateTime.now().minusDays(2));
            orders.add(order);
        }
        data.put("orders", orders);
        return Result.success(data);
    }

    @Operation(summary = "交易记录列表")
    @GetMapping("/records")
    public Result<List<Map<String, Object>>> records(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size) {
        List<Map<String, Object>> list = new ArrayList<>();
        String[] types = {"PAYMENT", "REFUND", "WITHDRAW", "FEE"};
        for (int i = 1; i <= size; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i);
            item.put("tradeNo", "T" + System.currentTimeMillis() + String.format("%03d", i));
            item.put("type", types[i % 4]);
            item.put("amount", new BigDecimal(10 + i * 33));
            item.put("balance", new BigDecimal(100000 - i * 500));
            item.put("remark", types[i % 4] + "交易-" + i);
            item.put("createTime", LocalDateTime.now().minusHours(i * 2));
            list.add(item);
        }
        return Result.success(list);
    }
}
