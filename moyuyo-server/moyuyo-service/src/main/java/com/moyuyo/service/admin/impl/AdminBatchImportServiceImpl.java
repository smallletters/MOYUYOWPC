package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.dao.admin.entity.DataExportRequestEntity;
import com.moyuyo.dao.admin.mapper.DataExportRequestMapper;
import com.moyuyo.service.admin.AdminBatchImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 批量导入服务实现
 */
@Service
@RequiredArgsConstructor
public class AdminBatchImportServiceImpl implements AdminBatchImportService {

  private final DataExportRequestMapper dataExportRequestMapper;

  @Override
  public List<Map<String, Object>> listRecords(int page, int size) {
    List<DataExportRequestEntity> list = dataExportRequestMapper.selectList(null);
    // 按创建时间倒序
    list.sort((a, b) -> {
      LocalDateTime ta = a.getCreateTime();
      LocalDateTime tb = b.getCreateTime();
      if (ta == null) return 1;
      if (tb == null) return -1;
      return tb.compareTo(ta);
    });
    return list.stream().map(this::toVo).collect(Collectors.toList());
  }

  private Map<String, Object> toVo(DataExportRequestEntity entity) {
    Map<String, Object> vo = new LinkedHashMap<>();
    vo.put("id", entity.getId());
    vo.put("fileName", entity.getExportId() != null ? entity.getExportId() : "导入_" + entity.getId());
    vo.put("importType", entity.getRequestType() != null ? entity.getRequestType() : "商品");
    vo.put("totalCount", 0);
    vo.put("successCount", 0);
    vo.put("failCount", 0);
    vo.put("status", entity.getStatus() != null ? entity.getStatus() : "已完成");
    vo.put("importTime", entity.getCreateTime() != null ? entity.getCreateTime().toString().replace("T", " ") : "");
    return vo;
  }

  @Override
  public Map<String, Object> importData(Map<String, Object> data) {
    DataExportRequestEntity entity = new DataExportRequestEntity();
    entity.setRequestType((String) data.getOrDefault("type", "商品"));
    entity.setExportId("导入_" + System.currentTimeMillis());
    entity.setStatus("导入中");
    dataExportRequestMapper.insert(entity);

    Map<String, Object> result = new HashMap<>();
    result.put("id", entity.getId());
    result.put("message", "导入任务已提交");
    return result;
  }

  @Override
  public Map<String, String> getTemplate(String type) {
    Map<String, String> result = new LinkedHashMap<>();
    result.put("type", type);
    result.put("url", "/templates/import_" + type + ".xlsx");
    result.put("message", "模板下载路径");
    return result;
  }

  @Override
  public List<Map<String, Object>> getErrors(Long id) {
    DataExportRequestEntity entity = dataExportRequestMapper.selectById(id);
    if (entity == null) {
      return new ArrayList<>();
    }
    return new ArrayList<>();
  }

  @Override
  public Map<String, Object> deleteRecord(Long id) {
    dataExportRequestMapper.deleteById(id);
    Map<String, Object> result = new HashMap<>();
    result.put("message", "删除成功");
    return result;
  }
}
