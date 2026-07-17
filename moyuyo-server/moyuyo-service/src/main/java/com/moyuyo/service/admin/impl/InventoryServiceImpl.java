package com.moyuyo.service.admin.impl;

import com.moyuyo.service.admin.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 库存管理服务实现
 */
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

  @Override
  public Map<String, Object> getInventoryOverview() {
    Map<String, Object> overview = new LinkedHashMap<>();
    overview.put("totalSku", 1250);
    overview.put("lowStockCount", 18);
    overview.put("expiringBatchCount", 5);
    overview.put("inTransitCount", 3);

    List<Map<String, Object>> alerts = new ArrayList<>();
    Map<String, Object> alert1 = new LinkedHashMap<>();
    alert1.put("sku", "SKU-001");
    alert1.put("name", "宠物洗护套装");
    alert1.put("stock", 5);
    alert1.put("threshold", 20);
    alerts.add(alert1);

    Map<String, Object> alert2 = new LinkedHashMap<>();
    alert2.put("sku", "SKU-015");
    alert2.put("name", "舒适胸背带-M");
    alert2.put("stock", 3);
    alert2.put("threshold", 30);
    alerts.add(alert2);

    overview.put("alerts", alerts);
    return overview;
  }

  @Override
  public List<Map<String, Object>> getAlertList() {
    List<Map<String, Object>> list = new ArrayList<>();
    String[][] data = {
        {"SKU-001", "宠物洗护套装", "5", "LOW_STOCK"},
        {"SKU-015", "舒适胸背带-M", "3", "LOW_STOCK"},
        {"SKU-032", "鹿角磨牙棒", "2026-08-15", "EXPIRING"},
        {"SKU-045", "宠物航空箱-L", "2026-08-20", "EXPIRING"},
        {"SKU-078", "智能喂食器", "2", "LOW_STOCK"},
    };
    for (String[] row : data) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("sku", row[0]);
      item.put("name", row[1]);
      item.put("detail", row[2]);
      item.put("type", row[3]);
      list.add(item);
    }
    return list;
  }
}
