package com.moyuyo.service;

import com.moyuyo.dao.entity.CategoryEntity;

import java.util.List;

public interface CategoryService {

  List<CategoryEntity> getCategoryTree();

  List<CategoryEntity> getCategoryByParent(Long parentId);

  /**
   * 新增分类（仅支持一级 / 二级）。
   *
   * @param input 分类表单：name、nameI18n、icon、sort、parentId、level
   * @return 新建后的分类
   */
  CategoryEntity createCategory(CategoryEntity input);

  /**
   * 更新分类的基础信息（名称 / 多语言名称 / 图标 / 排序）。
   *
   * @param id    分类ID
   * @param input 分类表单
   * @return 更新后的分类
   */
  CategoryEntity updateCategory(Long id, CategoryEntity input);

  /**
   * 删除分类。存在下级分类或商品引用时拒绝删除。
   *
   * @param id 分类ID
   */
  void deleteCategory(Long id);
}
