package com.moyuyo.service.admin.impl;

import com.moyuyo.dao.admin.entity.CmsContentEntity;
import com.moyuyo.dao.admin.mapper.CmsContentMapper;
import com.moyuyo.service.admin.CmsContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * CMS内容管理服务实现
 */
@Service
@RequiredArgsConstructor
public class CmsContentServiceImpl implements CmsContentService {

  private final CmsContentMapper cmsContentMapper;

  @Override
  public List<CmsContentEntity> listByType(String type) {
    if (type == null || type.isEmpty()) {
      return cmsContentMapper.selectList(null);
    }
    return cmsContentMapper.selectList(
        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CmsContentEntity>()
            .eq(CmsContentEntity::getType, type)
            .orderByAsc(CmsContentEntity::getSortOrder)
    );
  }

  @Override
  public void create(CmsContentEntity entity) {
    cmsContentMapper.insert(entity);
  }

  @Override
  public void update(CmsContentEntity entity) {
    cmsContentMapper.updateById(entity);
  }

  @Override
  public void delete(Long id) {
    cmsContentMapper.deleteById(id);
  }

  @Override
  public CmsContentEntity getById(Long id) {
    return cmsContentMapper.selectById(id);
  }

  @Override
  public void updateStatus(Long id, String status) {
    CmsContentEntity entity = new CmsContentEntity();
    entity.setId(id);
    entity.setStatus(status);
    cmsContentMapper.updateById(entity);
  }
}
