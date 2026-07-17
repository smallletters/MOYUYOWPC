package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@Tag(name = "管理后台 - 权限管理")
@RestController
@RequestMapping("/api/admin/rbac")
@RequiredArgsConstructor
public class AdminRbacController {

    @Operation(summary = "角色列表")
    @GetMapping("/roles")
    public Result<List<Map<String, Object>>> roles() {
        List<Map<String, Object>> list = new ArrayList<>();
        String[][] data = {
                {"1", "超级管理员", "全部权限", "0"},
                {"2", "运营管理员", "商品、订单、营销", "8"},
                {"3", "客服管理员", "工单、退款", "5"},
                {"4", "财务管理员", "财务、结算", "3"},
        };
        for (String[] r : data) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", new Long(r[0]));
            item.put("name", r[1]);
            item.put("description", r[2]);
            item.put("userCount", new Integer(r[3]));
            item.put("createTime", LocalDateTime.now().minusDays(30));
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "新建角色")
    @PostMapping("/roles")
    public Result<Map<String, Object>> createRole(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", new Random().nextInt(1000));
        result.put("name", body.get("name"));
        result.put("message", "角色创建成功");
        return Result.success(result);
    }

    @Operation(summary = "更新角色")
    @PutMapping("/roles/{id}")
    public Result<Map<String, Object>> updateRole(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("name", body.get("name"));
        result.put("message", "角色更新成功");
        return Result.success(result);
    }

    @Operation(summary = "删除角色")
    @DeleteMapping("/roles/{id}")
    public Result<Map<String, Object>> deleteRole(@PathVariable Long id) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("message", "角色删除成功");
        return Result.success(result);
    }

    @Operation(summary = "获取角色权限")
    @GetMapping("/roles/{id}/permissions")
    public Result<Map<String, Object>> getPermissions(@PathVariable Long id) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("roleId", id);
        data.put("roleName", "运营管理员");

        List<Map<String, Object>> permissions = new ArrayList<>();
        String[][] perms = {
                {"product", "商品管理", "true"},
                {"order", "订单管理", "true"},
                {"marketing", "营销管理", "true"},
                {"finance", "财务管理", "false"},
                {"system", "系统设置", "false"},
        };
        for (String[] p : perms) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", p[0]);
            item.put("name", p[1]);
            item.put("enabled", Boolean.valueOf(p[2]));
            permissions.add(item);
        }
        data.put("permissions", permissions);
        return Result.success(data);
    }

    @Operation(summary = "更新角色权限")
    @PutMapping("/roles/{id}/permissions")
    public Result<Map<String, Object>> updatePermissions(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("roleId", id);
        result.put("message", "权限更新成功");
        return Result.success(result);
    }

    @Operation(summary = "管理员列表")
    @GetMapping("/users")
    public Result<List<Map<String, Object>>> users() {
        List<Map<String, Object>> list = new ArrayList<>();
        String[][] data = {
                {"1", "Admin", "admin@moyuyo.com", "超级管理员", "ACTIVE"},
                {"2", "小王", "wang@moyuyo.com", "运营管理员", "ACTIVE"},
                {"3", "小李", "li@moyuyo.com", "客服管理员", "ACTIVE"},
                {"4", "小张", "zhang@moyuyo.com", "财务管理员", "DISABLED"},
        };
        for (String[] u : data) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", new Long(u[0]));
            item.put("name", u[1]);
            item.put("email", u[2]);
            item.put("role", u[3]);
            item.put("status", u[4]);
            item.put("lastLogin", LocalDateTime.now().minusDays((long) (Math.random() * 7)));
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "新建管理员")
    @PostMapping("/users")
    public Result<Map<String, Object>> createUser(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", new Random().nextInt(1000));
        result.put("name", body.get("name"));
        result.put("message", "管理员创建成功");
        return Result.success(result);
    }

    @Operation(summary = "更新管理员")
    @PutMapping("/users/{id}")
    public Result<Map<String, Object>> updateUser(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("name", body.get("name"));
        result.put("message", "管理员更新成功");
        return Result.success(result);
    }
}
