package com.moyuyo.service.config;

import com.moyuyo.service.admin.SystemConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * PaymentMethodConfigService 单元测试
 * <p>
 * 重点覆盖后台「支付设置」开关的语义：
 * 1) 未配置 = 开启（历史环境不误伤）
 * 2) 渠道开关（Stripe / PayPal）
 * 3) 方式开关（Apple Pay 独立，Google Pay / 信用卡跟随 Stripe）
 * 4) 配置读取失败时全开启兜底
 */
@ExtendWith(MockitoExtension.class)
class PaymentMethodConfigServiceTest {

  @Mock
  private SystemConfigService systemConfigService;

  @InjectMocks
  private PaymentMethodConfigService service;

  private void givenConfig(Map<String, Object> config) {
    when(systemConfigService.getConfig("payment")).thenReturn(config);
  }

  @Test
  void noConfig_shouldExposeAllMethodsAndAllowPayment() {
    givenConfig(Collections.emptyMap());

    List<Map<String, Object>> methods = service.listEnabledMethods();
    assertEquals(4, methods.size(), "未配置开关时应返回全部支付方式");

    assertNull(service.findDisabledReason("STRIPE", "CARD"));
    assertNull(service.findDisabledReason("STRIPE", "APPLE_PAY"));
    assertNull(service.findDisabledReason("PAYPAL", "PAYPAL"));
  }

  @Test
  void stripeDisabled_shouldBlockStripeChannelAndWalletMethods() {
    Map<String, Object> config = new HashMap<>();
    config.put("payment_stripe", "false");
    givenConfig(config);

    assertTrue(service.findDisabledReason("STRIPE", "CARD").contains("支付渠道已关闭"));
    assertTrue(service.findDisabledReason("STRIPE", "GOOGLE_PAY").contains("支付渠道已关闭"));

    // Stripe 关闭不影响 PayPal 渠道
    assertNull(service.findDisabledReason("PAYPAL", "PAYPAL"));

    // 展示列表中只剩 PayPal / Apple Pay（Apple Pay 有独立开关且未配置）
    List<Map<String, Object>> methods = service.listEnabledMethods();
    assertFalse(methods.stream().anyMatch(m -> "CARD".equals(m.get("code"))));
    assertFalse(methods.stream().anyMatch(m -> "GOOGLE_PAY".equals(m.get("code"))));
    assertTrue(methods.stream().anyMatch(m -> "PAYPAL".equals(m.get("code"))));
    assertTrue(methods.stream().anyMatch(m -> "APPLE_PAY".equals(m.get("code"))));
  }

  @Test
  void applePayDisabled_shouldOnlyBlockApplePay() {
    Map<String, Object> config = new HashMap<>();
    config.put("payment_applepay", "false");
    givenConfig(config);

    assertTrue(service.findDisabledReason("STRIPE", "APPLE_PAY").contains("支付方式已关闭"));
    // 同一 Stripe 渠道下的其他方式不受 Apple Pay 开关影响
    assertNull(service.findDisabledReason("STRIPE", "CARD"));
    assertNull(service.findDisabledReason("STRIPE", "GOOGLE_PAY"));
  }

  @Test
  void paypalDisabled_shouldBlockPayPalChannel() {
    Map<String, Object> config = new HashMap<>();
    config.put("payment_paypal", "0");
    givenConfig(config);

    assertTrue(service.findDisabledReason("PAYPAL", "PAYPAL").contains("支付渠道已关闭"));
    assertNull(service.findDisabledReason("STRIPE", "CARD"));
  }

  @Test
  void configLoadFailure_shouldFallbackToAllEnabled() {
    when(systemConfigService.getConfig("payment")).thenThrow(new RuntimeException("db down"));

    assertNull(service.findDisabledReason("STRIPE", "CARD"), "配置读取失败不能阻断支付");
    assertEquals(4, service.listEnabledMethods().size());
  }

  @Test
  void blankChannel_shouldBeRejected() {
    assertEquals("支付渠道不能为空", service.findDisabledReason(" ", "CARD"));
  }

  @Test
  void listSwitches_noConfig_shouldReturnAllEnabled() {
    givenConfig(Collections.emptyMap());

    List<Map<String, Object>> switches = service.listSwitches();
    assertEquals(3, switches.size());
    for (Map<String, Object> item : switches) {
      assertEquals(true, item.get("enabled"), "未配置时应全部为开启");
      assertEquals("active", item.get("status"));
    }
  }

  @Test
  void listSwitches_shouldReflectSavedSwitchState() {
    Map<String, Object> config = new HashMap<>();
    config.put("payment_paypal", "false");
    givenConfig(config);

    List<Map<String, Object>> switches = service.listSwitches();
    Map<String, Object> paypal = switches.stream()
        .filter(item -> "paypal".equals(item.get("code")))
        .findFirst()
        .orElseThrow();
    assertEquals(false, paypal.get("enabled"));
    assertEquals("inactive", paypal.get("status"));

    // 其余开关不受影响
    switches.stream()
        .filter(item -> "stripe".equals(item.get("code")))
        .findFirst()
        .ifPresent(item -> assertEquals(true, item.get("enabled")));
  }
}
