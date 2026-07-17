package com.moyuyo.service.admin;

import com.moyuyo.dao.admin.entity.CmsContentEntity;

import java.util.List;

/**
 * CMS内容管理服务
 */
public interface CmsContentService {

  /**
   * 按类型列出CMS内容
   */
  List<CmsContentEntity> listByType(String type);

  /**
   * 创建CMS内容
   */
  void create(CmsContentEntity entity);

  /**
   * 更新CMS内容
   */
  void update(CmsContentEntity entity);

  /**
   * 删除CMS内容
   */
  void delete(Long id);

  /**
   * 根据ID获取CMS内容
   */
  CmsContentEntity getById(Long id);

  /**
   * 更新CMS内容状态
   */
  void updateStatus(Long id, String status);
}
