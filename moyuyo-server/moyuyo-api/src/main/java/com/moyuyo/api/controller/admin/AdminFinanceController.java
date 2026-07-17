package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Tag(name = "管理后台 - 财务管理")
@RestController
@RequestMapping("/api/admin/finance")
@RequiredArgsConstructor
public class AdminFinanceController {

    @Operation(summary = "财务概览")
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("monthGmv", 358000);
        data.put("monthActualIncome", 326500);
        data.put("pendingSettlement", 45200);
        data.put("monthRefund", 26300);
        data.put("refundRate", 7.3);

        // 支付渠道分布
        List<Map<String, Object>> channels = new ArrayList<>();
        String[][] channelData = {
                {"WECHAT", "微信支付", "65.0"},
                {"ALIPAY", "支付宝", "25.0"},
                {"UNIONPAY", "银联支付", "7.0"},
                {"BALANCE", "余额支付", "3.0"},
        };
        for (String[] c : channelData) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", c[0]);
            item.put("name", c[1]);
            item.put("ratio", new BigDecimal(c[2]));
            channels.add(item);
        }
        data.put("paymentChannels", channels);

        // 待处理异常
        List<Map<String, Object>> exceptions = new ArrayList<>();
        Map<String, Object> exc1 = new LinkedHashMap<>();
        exc1.put("type", "对账异常");
        exc1.put("count", 3);
        exc1.put("amount", 12800);
        exceptions.add(exc1);
        Map<String, Object> exc2 = new LinkedHashMap<>();
        exc2.put("type", "支付超时");
        exc2.put("count", 5);
        exc2.put("amount", 3500);
        exceptions.add(exc2);
        data.put("pendingExceptions", exceptions);

        return Result.success(data);
    }

    @Operation(summary = "结算明细列表")
    @GetMapping("/settlements")
    public Result<List<Map<String, Object>>> settlements(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size) {
        List<Map<String, Object>> list = new ArrayList<>();
        String[] statuses = {"SETTLED", "PENDING", "FAILED"};
        for (int i = 1; i <= size; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i);
            item.put("settlementNo", "SET" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + String.format("%04d", i));
            item.put("period", "2026-06-" + String.format("%02d", (i % 28 + 1)));
            item.put("orderCount", 10 + i * 3);
            item.put("totalAmount", new BigDecimal(5000 + i * 1200));
            item.put("fee", new BigDecimal(50 + i * 12));
            item.put("netAmount", new BigDecimal(4950 + i * 1188));
            item.put("status", statuses[i % 3]);
            item.put("settleTime", i % 3 == 0 ? LocalDateTime.now().minusDays(i) : null);
            list.add(item);
        }
        return Result.success(list);
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
