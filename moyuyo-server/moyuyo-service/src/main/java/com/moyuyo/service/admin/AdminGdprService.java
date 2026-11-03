package com.moyuyo.service.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.Map;

import java.util.Map;

/**
 * 管理后台 GDPR 合规服务
 */
public interface AdminGdprService {

  /**
   * 用户同意记录列表（分页）
   */
  Page<Map<String, Object>> listConsents(Long userId, int page, int size);

  /**
   * 数据主体请求列表（分页）
   */
  Page<Map<String, Object>> listRequests(String status, int page, int size);

  /**
   * 处理数据请求
   */
  void processRequest(Long id, String result, String note);

    /**
     * 获取当前生效的隐私政策（后台使用，含中英双语文案）
     */
    Map<String, Object> getActivePolicy();

    /**
     * 获取当前生效的隐私政策（按客户端语言返回正文，对应语言缺失时回退另一种语言）
     * @param lang 语言（zh-CN / en-US）
     */
    Map<String, Object> getLocalizedPolicy(String lang);

    /**
     * 新建隐私政策版本
     */
    Map<String, Object> createPolicy(Map<String, Object> body);

    /**
     * 更新隐私政策（仅更新传入的字段，用于后台「隐私政策全文」页保存编辑内容）
     */
    Map<String, Object> updatePolicy(Long id, Map<String, Object> body);
}
