package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Tag(name = "管理后台 - 短信管理")
@RestController
@RequestMapping("/api/admin/sms")
@RequiredArgsConstructor
public class AdminSmsController {

    @Operation(summary = "短信统计")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("todaySent", 1256);
        data.put("monthSent", 35800);
        data.put("remainingBalance", 89200);
        data.put("successRate", new BigDecimal("98.5"));
        return Result.success(data);
    }

    @Operation(summary = "发送记录列表")
    @GetMapping("/records")
    public Result<List<Map<String, Object>>> records(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size) {
        List<Map<String, Object>> list = new ArrayList<>();
        String[] contents = {
                "您的验证码是123456，5分钟内有效。",
                "您的订单已发货，快递单号SF1234567890。",
                "亲爱的用户，您有一张满100减20优惠券即将过期。",
                "您购买的商品已到货，请及时取件。",
                "系统通知：您的账户密码已成功修改。"
        };
        String[] statuses = {"SUCCESS", "SUCCESS", "FAILED", "SUCCESS", "SUCCESS"};
        for (int i = 1; i <= size && i <= 15; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i);
            item.put("content", contents[i % contents.length]);
            item.put("phone", "138****" + String.format("%04d", 1000 + (int) (Math.random() * 9000)));
            item.put("status", statuses[i % statuses.length]);
            item.put("sendTime", LocalDateTime.now().minusHours(i * 2));
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "发送短信")
    @PostMapping("/send")
    public Result<Map<String, Object>> send(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", new Random().nextInt(10000));
        result.put("phone", body.get("phone"));
        result.put("content", body.get("content"));
        result.put("status", "SUCCESS");
        result.put("sendTime", LocalDateTime.now());
        result.put("message", "短信发送成功");
        return Result.success(result);
    }

    @Operation(summary = "批量发送")
    @PostMapping("/batch-send")
    public Result<Map<String, Object>> batchSend(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchNo", "BATCH" + System.currentTimeMillis());
        result.put("totalCount", body.get("phones") != null ? ((List<?>) body.get("phones")).size() : 0);
        result.put("successCount", body.get("phones") != null ? ((List<?>) body.get("phones")).size() : 0);
        result.put("failCount", 0);
        result.put("sendTime", LocalDateTime.now());
        result.put("message", "批量发送任务已提交");
        return Result.success(result);
    }

    @Operation(summary = "短信模板列表")
    @GetMapping("/templates")
    public Result<List<Map<String, Object>>> templates() {
        List<Map<String, Object>> list = new ArrayList<>();
        String[] templateNames = {
                "验证码模板",
                "订单发货通知",
                "优惠券提醒",
                "到货通知",
                "安全提醒"
        };
        String[] templateContents = {
                "您的验证码是${code}，${expire}分钟内有效。",
                "您的订单${orderNo}已发货，快递单号${trackingNo}。",
                "亲爱的用户，${couponDesc}即将过期，请尽快使用。",
                "您购买的商品${productName}已到货，请及时取件。",
                "系统通知：您的${action}已成功，如非本人操作请及时处理。"
        };
        String[] statuses = {"AUDITED", "AUDITED", "PENDING", "AUDITED", "AUDITED"};
        for (int i = 0; i < 5; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i + 1);
            item.put("templateName", templateNames[i]);
            item.put("content", templateContents[i]);
            item.put("status", statuses[i]);
            item.put("createTime", LocalDateTime.now().minusDays(30 - i * 5));
            list.add(item);
        }
        return Result.success(list);
    }
}
