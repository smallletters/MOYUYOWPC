package com.moyuyo.service.admin.impl;

import com.moyuyo.service.admin.FinanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * 财务管理服务实现
 */
@Service
@RequiredArgsConstructor
public class FinanceServiceImpl implements FinanceService {

  @Override
  public Map<String, Object> getFinanceOverview() {
    Map<String, Object> overview = new LinkedHashMap<>();
    overview.put("monthGmv", 368000);
    overview.put("actualIncome", 352000);
    overview.put("pendingSettlement", 28000);
    overview.put("refundAmount", 16000);

    // 支付渠道分布
    List<Map<String, Object>> channelDist = new ArrayList<>();
    Map<String, Object> wechat = new LinkedHashMap<>();
    wechat.put("channel", "微信支付");
    wechat.put("amount", 220000);
    wechat.put("ratio", 59.8);
    channelDist.add(wechat);

    Map<String, Object> alipay = new LinkedHashMap<>();
    alipay.put("channel", "支付宝");
    alipay.put("amount", 130000);
    alipay.put("ratio", 35.3);
    channelDist.add(alipay);

    Map<String, Object> unionpay = new LinkedHashMap<>();
    unionpay.put("channel", "银联");
    unionpay.put("amount", 18000);
    unionpay.put("ratio", 4.9);
    channelDist.add(unionpay);

    overview.put("channelDistribution", channelDist);
    overview.put("pendingIssues", 2);
    return overview;
  }

  @Override
  public List<Map<String, Object>> getSettlementList() {
    List<Map<String, Object>> list = new ArrayList<>();
    String[][] data = {
        {"SET-20260701", "2026-07-01", "35000", "COMPLETED"},
        {"SET-20260702", "2026-07-02", "42000", "PROCESSING"},
        {"SET-20260703", "2026-07-03", "38000", "PENDING"},
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
}
