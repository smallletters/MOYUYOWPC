package com.moyuyo.service.impl;

import com.moyuyo.service.WooCommerceSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * WooCommerce 库存自动同步定时任务
 * <p>
 * 定期从 WooCommerce 拉取所有已关联商品的库存数据 (stock_quantity, stock_status, manage_stock)
 * 并更新本地数据库，保持库存数据与 WC 实时一致。
 * <p>
 * 可通过配置关闭：woocommerce.stock-sync.auto-enabled=false
 */
@Slf4j
@Component
public class WooCommerceStockSyncTask {

  private final WooCommerceSyncService wooCommerceSyncService;
  private final WooCommerceClient wooCommerceClient;

  @Value("${woocommerce.stock-sync.auto-enabled:true}")
  private boolean autoSyncEnabled;

  public WooCommerceStockSyncTask(WooCommerceSyncService wooCommerceSyncService,
                                   WooCommerceClient wooCommerceClient) {
    this.wooCommerceSyncService = wooCommerceSyncService;
    this.wooCommerceClient = wooCommerceClient;
  }

  /**
   * 每 5 分钟自动同步一次库存。
   * cron 表达式可通过 application.yml 配置：
   *   woocommerce.stock-sync.cron: "0 x/5 * * * *"
   * （其中 x 代表斜杠符号，即每隔 5 分钟）
   */
  @Scheduled(cron = "${woocommerce.stock-sync.cron:0 */5 * * * *}")
  public void autoSyncStock() {
    if (!autoSyncEnabled) {
      return; // 管理员手动关闭时跳过
    }
    if (!wooCommerceClient.isConfigured()) {
      // WC 未配置时不执行，避免无用的网络重试
      return;
    }
    log.info("[WC-StockSync] 定时库存同步开始...");
    try {
      Map<String, Integer> result = wooCommerceSyncService.syncAllStocksFromWooCommerce();
      log.info("[WC-StockSync] 定时库存同步完成: total={}, updated={}, skipped={}, failed={}",
          result.get("total"), result.get("updated"), result.get("skipped"), result.get("failed"));
    } catch (Exception e) {
      log.error("[WC-StockSync] 定时库存同步异常: {}", e.getMessage(), e);
    }
  }
}
