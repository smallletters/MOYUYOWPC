package com.moyuyo.service.config;

import com.moyuyo.service.admin.SystemConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 支付方式开关配置服务
 * <p>
 * 后台「系统配置 → 支付设置」的开关保存在 mo_system_config（key = payment_{方式}），
 * 本服务把开关翻译成「渠道 / 方式是否可用」，供两处使用：
 * 1) 下单前校验（防止绕过前端直接调支付接口）；
 * 2) 收银台动态展示（GET /api/v1/payments/methods）。
 * <p>
 * 约定：配置项不存在时视为「开启」，避免从未保存过支付配置的环境被误关。
 * 每次调用都实时读库（支付创建是低频操作），保证后台改开关立即生效。
 */
@Slf4j
@Service
public class PaymentMethodConfigService {

  /** 与 SystemConfig.vue 保存的 key 前缀保持一致（payment_${method.key}） */
  private static final String CONFIG_KEY_PREFIX = "payment_";

  /** 支付渠道开关 key：STRIPE / PAYPAL */
  private static final Map<String, String> CHANNEL_CONFIG_KEYS = Map.of(
      "STRIPE", CONFIG_KEY_PREFIX + "stripe",
      "PAYPAL", CONFIG_KEY_PREFIX + "paypal");

  /**
   * 支付方式目录：顺序即收银台展示顺序。
   * configKey 是该方式的开关配置 key —— Apple Pay 有独立开关；
   * 其余方式跟随所属渠道（Google Pay / 信用卡走 Stripe，PayPal 走 PayPal 渠道）。
   */
  private static final List<MethodOption> CATALOG = List.of(
      new MethodOption("GOOGLE_PAY", "STRIPE", CONFIG_KEY_PREFIX + "stripe", "Google Pay", "Google Pay 快捷支付"),
      new MethodOption("APPLE_PAY", "STRIPE", CONFIG_KEY_PREFIX + "applepay", "Apple Pay", "Apple 设备快捷支付"),
      new MethodOption("PAYPAL", "PAYPAL", CONFIG_KEY_PREFIX + "paypal", "PayPal", "PayPal 账户支付"),
      new MethodOption("CARD", "STRIPE", CONFIG_KEY_PREFIX + "stripe", "Credit/Debit Card", "信用卡 / 借记卡支付"));

  /**
   * 管理端「支付设置」可编辑的开关项：
   * code 与 SystemConfig.vue 保存的 key 后缀（payment_{code}）严格一致。
   * 说明：Stripe 开关同时控制信用卡与 Google Pay（二者都走 Stripe 的 card 通道）。
   */
  private static final List<SwitchOption> SWITCHES = List.of(
      new SwitchOption("stripe", CONFIG_KEY_PREFIX + "stripe", "Stripe", "信用卡 / 借记卡支付（含 Google Pay）"),
      new SwitchOption("paypal", CONFIG_KEY_PREFIX + "paypal", "PayPal", "PayPal 账户支付"),
      new SwitchOption("applepay", CONFIG_KEY_PREFIX + "applepay", "Apple Pay", "Apple 设备快捷支付"));

  private final SystemConfigService systemConfigService;

  public PaymentMethodConfigService(SystemConfigService systemConfigService) {
    this.systemConfigService = systemConfigService;
  }

  /**
   * 下单前校验：渠道或支付方式被后台关闭时返回拒绝原因，允许时返回 null。
   * <p>
   * 一次性读取配置，避免渠道校验与方式校验各查一次库。
   *
   * @param payChannel 渠道大类：STRIPE / PAYPAL
   * @param payMethod  方式细分：CARD / GOOGLE_PAY / APPLE_PAY / PAYPAL，可为空
   */
  public String findDisabledReason(String payChannel, String payMethod) {
    if (!StringUtils.hasText(payChannel)) {
      return "支付渠道不能为空";
    }
    Map<String, Object> config = loadConfig();
    String channel = payChannel.trim().toUpperCase();

    // 1) 渠道级校验：STRIPE / PAYPAL
    String channelKey = CHANNEL_CONFIG_KEYS.get(channel);
    if (channelKey != null && !isEnabled(config, channelKey)) {
      return "支付渠道已关闭: " + channel;
    }

    // 2) 方式级校验：仅对目录内方式生效，未知方式由 PaymentServiceImpl 的分支逻辑拒绝
    if (StringUtils.hasText(payMethod)) {
      MethodOption option = findOption(payMethod);
      if (option != null && !isEnabled(config, option.configKey())) {
        return "支付方式已关闭: " + option.code();
      }
    }
    return null;
  }

  /**
   * 收银台可用支付方式列表（仅返回开启项）。
   * <p>
   * 这是前端的唯一数据源；返回空列表代表运营把全部支付方式都关闭了，
   * 前端应提示「暂无可用的支付方式」而不是自行补默认值。
   */
  public List<Map<String, Object>> listEnabledMethods() {
    Map<String, Object> config = loadConfig();
    List<Map<String, Object>> methods = new ArrayList<>();
    for (MethodOption option : CATALOG) {
      if (!isEnabled(config, option.configKey())) {
        continue;
      }
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("code", option.code());
      item.put("channel", option.channel());
      item.put("name", option.name());
      item.put("desc", option.desc());
      methods.add(item);
    }
    return methods;
  }

  /**
   * 管理端「支付设置」开关列表（含当前状态）。
   * <p>
   * 让后台列表与 C 端收银台共用同一份数据源，避免前端硬编码一份开关、
   * 后台显示的状态与真实生效状态不一致。
   */
  public List<Map<String, Object>> listSwitches() {
    Map<String, Object> config = loadConfig();
    List<Map<String, Object>> switches = new ArrayList<>();
    for (SwitchOption option : SWITCHES) {
      boolean enabled = isEnabled(config, option.configKey());
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("code", option.code());
      item.put("name", option.name());
      item.put("desc", option.desc());
      item.put("enabled", enabled);
      // status 兼容旧前端字段（active / inactive）
      item.put("status", enabled ? "active" : "inactive");
      switches.add(item);
    }
    return switches;
  }

  private MethodOption findOption(String payMethod) {
    String code = payMethod.trim().toUpperCase();
    for (MethodOption option : CATALOG) {
      if (option.code().equals(code)) {
        return option;
      }
    }
    return null;
  }

  /**
   * 读取开关配置。DB 异常时按「全部开启」兜底：
   * 配置表不可用不应该阻断用户支付，失败仅记录告警供运维排查。
   */
  private Map<String, Object> loadConfig() {
    try {
      return systemConfigService.getConfig("payment");
    } catch (Exception e) {
      log.warn("[payment] 读取支付开关配置失败，按全部开启兜底: {}", e.getMessage());
      return Collections.emptyMap();
    }
  }

  /** 开关值解析：false / 0 / off 视为关闭，其余（含未配置）视为开启 */
  private boolean isEnabled(Map<String, Object> config, String configKey) {
    Object value = config == null ? null : config.get(configKey);
    if (value == null) {
      return true;
    }
    String text = String.valueOf(value).trim().toLowerCase();
    return !("false".equals(text) || "0".equals(text) || "off".equals(text));
  }

  /** 支付方式目录项 */
  private record MethodOption(String code, String channel, String configKey, String name, String desc) {
  }

  /** 管理端开关项 */
  private record SwitchOption(String code, String configKey, String name, String desc) {
  }
}
