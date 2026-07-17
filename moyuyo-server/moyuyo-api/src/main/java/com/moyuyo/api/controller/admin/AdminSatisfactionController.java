package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Tag(name = "管理后台 - 满意度管理")
@RestController
@RequestMapping("/api/admin/satisfaction")
@RequiredArgsConstructor
public class AdminSatisfactionController {

    @Operation(summary = "满意度概览")
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("avgScore", new BigDecimal("4.2"));
        result.put("totalReviews", 3680);
        result.put("positiveRate", new BigDecimal("86.5"));
        result.put("replyRate", new BigDecimal("72.3"));
        return Result.success(result);
    }

    @Operation(summary = "评价列表")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size) {
        List<Map<String, Object>> list = new ArrayList<>();
        String[][] data = {
                {"Luna Chen", "商品质量很好，快递速度也很快，很满意！", "5", "REPLIED"},
                {"Milo Wang", "性价比很高，已经回购多次了", "4", "REPLIED"},
                {"Sophie Li", "包装有点破损，但商品没问题", "3", "UNREPLIED"},
                {"Kevin Zhang", "物流太慢了，等了5天才到", "2", "REPLIED"},
                {"Emma Liu", "非常棒的商品，下次还会再买", "5", "UNREPLIED"},
                {"Oliver Zhao", "和描述一致，颜色很漂亮", "4", "REPLIED"},
                {"Amy Xu", "一般般，没有想象中好", "3", "UNREPLIED"},
                {"Jack Huang", "商品有问题，申请售后处理中", "1", "REPLIED"},
        };
        for (String[] row : data) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", new Random().nextInt(10000));
            item.put("userName", row[0]);
            item.put("content", row[1].length() > 20 ? row[1].substring(0, 20) + "..." : row[1]);
            item.put("score", new Integer(row[2]));
            item.put("replyStatus", row[3]);
            item.put("createTime", LocalDateTime.now().minusDays((long) (Math.random() * 30)));
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "回复评价")
    @PostMapping("/{id}/reply")
    public Result<Map<String, Object>> reply(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("replyContent", body.get("content"));
        result.put("message", "回复成功");
        return Result.success(result);
    }
}
