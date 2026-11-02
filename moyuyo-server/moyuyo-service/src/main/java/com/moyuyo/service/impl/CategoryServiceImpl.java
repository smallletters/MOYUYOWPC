package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.dao.entity.CategoryEntity;
import com.moyuyo.dao.entity.ProductEntity;
import com.moyuyo.dao.mapper.CategoryMapper;
import com.moyuyo.dao.mapper.ProductMapper;
import com.moyuyo.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
// 抑制 MyBatis-Plus 3.x @Nonnull T 与 JDT 静态分析差异（覆盖 nullUncheckedConversion 等所有 null 子类别）
@SuppressWarnings("null")
public class CategoryServiceImpl implements CategoryService {

  /** 分类名称最大长度，与 mo_category.name VARCHAR(64) 保持一致 */
  private static final int MAX_NAME_LENGTH = 64;
  /** 语言代码最大长度，避免异常数据写入 */
  private static final int MAX_LOCALE_LENGTH = 16;

  private final CategoryMapper categoryMapper;
  private final ProductMapper productMapper;
  private final ObjectMapper objectMapper;

  @Override
  public List<CategoryEntity> getCategoryTree() {
    List<CategoryEntity> all = categoryMapper.selectList(
        new LambdaQueryWrapper<CategoryEntity>()
            .orderByAsc(CategoryEntity::getSort));

    Map<Long, List<CategoryEntity>> childrenMap = all.stream()
        .collect(Collectors.groupingBy(c -> c.getParentId() != null ? c.getParentId() : 0L));

    List<CategoryEntity> roots = childrenMap.getOrDefault(0L, new ArrayList<>());
    fillChildren(roots, childrenMap);
    return roots;
  }

  private void fillChildren(List<CategoryEntity> parents, Map<Long, List<CategoryEntity>> childrenMap) {
    for (CategoryEntity parent : parents) {
      List<CategoryEntity> children = childrenMap.getOrDefault(parent.getId(), new ArrayList<>());
      parent.setChildren(children);
      fillChildren(children, childrenMap);
    }
  }

  @Override
  public List<CategoryEntity> getCategoryByParent(Long parentId) {
    return categoryMapper.selectList(
        new LambdaQueryWrapper<CategoryEntity>()
            .eq(CategoryEntity::getParentId, parentId)
            .orderByAsc(CategoryEntity::getSort));
  }

  @Override
  public CategoryEntity createCategory(CategoryEntity input) {
    Long parentId = normalizeParentId(input.getParentId());
    int level;
    if (parentId == 0L) {
      level = 1;
    } else {
      CategoryEntity parent = categoryMapper.selectById(parentId);
      if (parent == null) {
        throw new IllegalArgumentException("上级分类不存在");
      }
      // 当前业务只维护两级分类，禁止在三级及以下创建，避免 APP 分类页展示异常
      if (normalizeParentId(parent.getParentId()) != 0L) {
        throw new IllegalArgumentException("仅支持创建一级 / 二级分类");
      }
      level = 2;
    }

    CategoryEntity entity = new CategoryEntity();
    applyFields(entity, input);
    entity.setParentId(parentId);
    entity.setLevel(level);
    entity.setCreateTime(LocalDateTime.now());
    checkDuplicateName(null, parentId, entity.getName());
    categoryMapper.insert(entity);
    log.info("[category] 新增分类：id={}, parentId={}, name={}", entity.getId(), parentId, entity.getName());
    return entity;
  }

  @Override
  public CategoryEntity updateCategory(Long id, CategoryEntity input) {
    CategoryEntity existing = categoryMapper.selectById(id);
    if (existing == null) {
      throw new IllegalArgumentException("分类不存在");
    }
    applyFields(existing, input);
    // 层级与上级分类不支持在此修改，避免移动分类导致商品归属混乱
    checkDuplicateName(id, normalizeParentId(existing.getParentId()), existing.getName());
    // 全局 update-strategy=not_null 会忽略 null 字段，这里用 UpdateWrapper 显式 set，
    // 保证运营清空多语言名称 / 图标时能真正写回 NULL
    LambdaUpdateWrapper<CategoryEntity> update = new LambdaUpdateWrapper<CategoryEntity>()
        .eq(CategoryEntity::getId, id)
        .set(CategoryEntity::getName, existing.getName())
        .set(CategoryEntity::getNameI18n, existing.getNameI18n())
        .set(CategoryEntity::getIcon, existing.getIcon())
        .set(CategoryEntity::getSort, existing.getSort());
    categoryMapper.update(null, update);
    log.info("[category] 更新分类：id={}, name={}", id, existing.getName());
    return existing;
  }

  @Override
  public void deleteCategory(Long id) {
    CategoryEntity existing = categoryMapper.selectById(id);
    if (existing == null) {
      throw new IllegalArgumentException("分类不存在");
    }

    Long childCount = categoryMapper.selectCount(
        new LambdaQueryWrapper<CategoryEntity>().eq(CategoryEntity::getParentId, id));
    if (childCount != null && childCount > 0) {
      throw new IllegalArgumentException("该分类下还有 " + childCount + " 个二级分类，请先删除二级分类");
    }

    Long productCount = productMapper.selectCount(
        new LambdaQueryWrapper<ProductEntity>().eq(ProductEntity::getCategoryId, id));
    if (productCount != null && productCount > 0) {
      throw new IllegalArgumentException("该分类下还有 " + productCount + " 个商品，请先调整商品分类");
    }

    categoryMapper.deleteById(id);
    log.info("[category] 删除分类：id={}, name={}", id, existing.getName());
  }

  /**
   * 写入可编辑字段：名称、多语言名称、图标、排序。
   * name 为默认名称，多语言名称统一做 JSON 规整，避免前端拿到非法结构。
   */
  private void applyFields(CategoryEntity target, CategoryEntity input) {
    String name = trimToNull(input.getName());
    if (name == null) {
      throw new IllegalArgumentException("分类名称不能为空");
    }
    if (name.length() > MAX_NAME_LENGTH) {
      throw new IllegalArgumentException("分类名称不能超过 " + MAX_NAME_LENGTH + " 个字符");
    }
    String nameI18n = normalizeNameI18n(input.getNameI18n());
    String icon = trimToNull(input.getIcon());
    if (icon != null && icon.length() > 255) {
      throw new IllegalArgumentException("分类图标不能超过 255 个字符");
    }

    target.setName(name);
    target.setNameI18n(nameI18n);
    target.setIcon(icon);
    target.setSort(input.getSort() == null ? 0 : input.getSort());
  }

  /**
   * 校验同级分类下名称是否重复。
   * 一级分类父级在数据库中可能存 0 或 NULL（WooCommerce 同步数据），两种都视为同级。
   */
  private void checkDuplicateName(Long excludeId, Long parentId, String name) {
    LambdaQueryWrapper<CategoryEntity> wrapper = new LambdaQueryWrapper<CategoryEntity>()
        .eq(CategoryEntity::getName, name)
        .ne(excludeId != null, CategoryEntity::getId, excludeId);
    if (parentId == 0L) {
      wrapper.and(w -> w.eq(CategoryEntity::getParentId, 0L).or().isNull(CategoryEntity::getParentId));
    } else {
      wrapper.eq(CategoryEntity::getParentId, parentId);
    }
    Long count = categoryMapper.selectCount(wrapper);
    if (count != null && count > 0) {
      throw new IllegalArgumentException("同级分类下已存在同名分类");
    }
  }

  /**
   * 规整多语言名称 JSON：
   * 1) 只保留 key/value 均非空的项；
   * 2) 限制语言代码与名称长度；
   * 3) 空对象返回 null，避免入库 "{}"。
   */
  private String normalizeNameI18n(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    Map<String, String> parsed;
    try {
      parsed = objectMapper.readValue(raw, new TypeReference<Map<String, String>>() {});
    } catch (Exception e) {
      throw new IllegalArgumentException("多语言名称格式不合法，应为 JSON 对象");
    }
    Map<String, String> cleaned = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : parsed.entrySet()) {
      String locale = entry.getKey() == null ? "" : entry.getKey().trim();
      String value = entry.getValue() == null ? "" : entry.getValue().trim();
      if (locale.isEmpty() || value.isEmpty()) {
        continue;
      }
      if (locale.length() > MAX_LOCALE_LENGTH) {
        throw new IllegalArgumentException("语言代码不能超过 " + MAX_LOCALE_LENGTH + " 个字符");
      }
      if (value.length() > MAX_NAME_LENGTH) {
        throw new IllegalArgumentException("多语言分类名称不能超过 " + MAX_NAME_LENGTH + " 个字符");
      }
      cleaned.put(locale, value);
    }
    if (cleaned.isEmpty()) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(cleaned);
    } catch (Exception e) {
      throw new IllegalArgumentException("多语言名称序列化失败");
    }
  }

  private Long normalizeParentId(Long parentId) {
    return parentId == null ? 0L : parentId;
  }

  private String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
