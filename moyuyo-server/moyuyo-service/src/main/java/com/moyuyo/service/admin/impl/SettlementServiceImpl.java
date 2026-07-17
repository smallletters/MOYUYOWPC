package com.moyuyo.service.admin.impl;

import com.moyuyo.service.admin.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 结算管理服务实现
 */
@Service
@RequiredArgsConstructor
public class SettlementServiceImpl implements SettlementService {

  @Override
  public List<Map<String, Object>> listAll(String status) {
    List<Map<String, Object>> list = new ArrayList<>();
    String[][] data = {
        {"SET-20260701", "2026-07-01", "35000", "COMPLETED"},
        {"SET-20260702", "2026-07-02", "42000", "PROCESSING"},
        {"SET-20260703", "2026-07-03", "38000", "PENDING"},
        {"SET-20260704", "2026-07-04", "29500", "PENDING"},
        {"SET-20260705", "2026-07-05", "51000", "PROCESSING"},
    };
    for (String[] row : data) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("settlementNo", row[0]);
      item.put("date", row[1]);
      item.put("amount", new BigDecimal(row[2]));
      item.put("status", row[3]);
      list.add(item);
    }
    return list;
  }

  @Override
  public Map<String, Object> getDetail(Long id) {
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("settlementNo", "SET-20260701");
    detail.put("date", "2026-07-01");
    detail.put("totalAmount", 35000);
    detail.put("commission", 1750);
    detail.put("actualAmount", 33250);
    detail.put("orderCount", 128);
    detail.put("status", "COMPLETED");
    detail.put("completedAt", LocalDateTime.now().minusDays(2));
    return detail;
  }

  @Override
  public void settle(Long id) {
    // 待实现：执行结算逻辑
  }

  @Override
  public void confirm(Long id) {
    // 待实现：确认结算完成
  }
}
