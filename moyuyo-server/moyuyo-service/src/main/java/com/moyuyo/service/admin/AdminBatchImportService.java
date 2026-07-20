package com.moyuyo.service.admin;

import java.util.List;
import java.util.Map;

/**
 * 管理后台批量导入服务
 */
public interface AdminBatchImportService {

  /**
   * 导入记录列表
   */
  List<Map<String, Object>> listRecords(int page, int size);

  /**
   * 提交导入请求
   */
  Map<String, Object> importData(Map<String, Object> data);

  /**
   * 下载导入模板
   */
  Map<String, String> getTemplate(String type);

  /**
   * 查询导入错误详情
   */
  List<Map<String, Object>> getErrors(Long id);

  /**
   * 删除导入记录
   */
  Map<String, Object> deleteRecord(Long id);
}
