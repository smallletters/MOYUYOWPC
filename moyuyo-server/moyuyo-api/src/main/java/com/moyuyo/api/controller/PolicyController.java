package com.moyuyo.api.controller;

import com.moyuyo.common.Result;
import com.moyuyo.service.admin.AdminGdprService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * C 端协议公示接口（无需登录）
 * <p>
 * APP 端隐私政策正文统一从后台 GDPR 政策库读取，后台编辑保存后客户端刷新即可生效；
 * 政策库为空时返回空对象，客户端回退到内置文案。
 */
@Tag(name = "协议公示")
@RestController
@RequestMapping("/api/v1/policies")
@RequiredArgsConstructor
public class PolicyController {

  private final AdminGdprService adminGdprService;

  @Operation(summary = "获取当前生效的隐私政策（按语言返回，缺省语言时回退另一种）")
  @GetMapping("/active")
  public Result<Map<String, Object>> activePolicy(
      @RequestParam(required = false) String lang) {
    return Result.success(adminGdprService.getLocalizedPolicy(lang));
  }
}
