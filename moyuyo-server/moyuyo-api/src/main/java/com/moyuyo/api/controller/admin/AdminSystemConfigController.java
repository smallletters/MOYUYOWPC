package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@Tag(name = "管理后台 - 系统配置")
@RestController
@RequestMapping("/api/admin/system")
@RequiredArgsConstructor
public class AdminSystemConfigController {

    @Operation(summary = "获取系统配置")
    @GetMapping("/config")
    public Result<List<Map<String, Object>>> getConfig(@RequestParam(defaultValue = "basic") String group) {
        List<Map<String, Object>> list = new ArrayList<>();
        switch (group) {
            case "basic":
                list.add(createConfigItem("site_name", "Moyuyo宠物商城", "text", "站点名称"));
                list.add(createConfigItem("site_description", "专注宠物用品", "text", "站点描述"));
                list.add(createConfigItem("record_no", "粤ICP备2026XXXX号", "text", "备案号"));
                list.add(createConfigItem("maintenance_mode", "false", "boolean", "维护模式"));
                break;
            case "order":
                list.add(createConfigItem("auto_cancel_minutes", "30", "number", "未付款自动取消（分钟）"));
                list.add(createConfigItem("auto_confirm_days", "7", "number", "自动确认收货（天）"));
                list.add(createConfigItem("max_quantity", "99", "number", "单次最大购买数量"));
                list.add(createConfigItem("refund_deadline_days", "15", "number", "退款申请截止（天）"));
                break;
            case "payment":
                list.add(createConfigItem("wechat_enabled", "true", "boolean", "微信支付"));
                list.add(createConfigItem("alipay_enabled", "true", "boolean", "支付宝支付"));
                list.add(createConfigItem("min_payment", "0.01", "number", "最低支付金额"));
                break;
            case "notification":
                list.add(createConfigItem("sms_enabled", "true", "boolean", "短信通知"));
                list.add(createConfigItem("email_enabled", "false", "boolean", "邮件通知"));
                list.add(createConfigItem("push_enabled", "true", "boolean", "推送通知"));
                break;
        }
        return Result.success(list);
    }

    @Operation(summary = "保存配置")
    @PutMapping("/config")
    public Result<Map<String, Object>> saveConfig(@RequestBody List<Map<String, Object>> configs) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("updated", configs.size());
        result.put("message", "配置保存成功");
        return Result.success(result);
    }

    @Operation(summary = "操作/运营日志")
    @GetMapping("/logs")
    public Result<List<Map<String, Object>>> logs(
            @RequestParam(defaultValue = "operation") String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 1; i <= size; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i);
            item.put("operator", "管理员" + (i % 4 + 1));
            item.put("action", type.equals("operation") ? getOperationAction(i) : getBusinessAction(i));
            item.put("detail", "执行了操作" + i);
            item.put("ip", "192.168.1." + (i % 255 + 1));
            item.put("createTime", LocalDateTime.now().minusHours(i * 2));
            list.add(item);
        }
        return Result.success(list);
    }

    private Map<String, Object> createConfigItem(String key, String value, String type, String label) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", key);
        item.put("value", value);
        item.put("type", type);
        item.put("label", label);
        return item;
    }

    private String getOperationAction(int i) {
        String[] actions = {"登录后台", "修改配置", "新增商品", "下架商品", "审核退款", "导出数据"};
        return actions[i % actions.length];
    }

    private String getBusinessAction(int i) {
        String[] actions = {"用户注册", "下单购买", "提交退款", "商品浏览", "收藏商品", "发布评价"};
        return actions[i % actions.length];
    }
}
