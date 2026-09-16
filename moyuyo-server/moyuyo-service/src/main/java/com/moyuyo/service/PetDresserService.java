package com.moyuyo.service;

import com.moyuyo.dao.entity.PetOutfitEntity;

import java.util.List;

public interface PetDresserService {

  /**
   * 列出某宠物的全部装扮（含系统默认 + 用户自定义）。
   * 系统默认装扮（user_id=NULL）所有用户可见；
   * 用户自定义装扮仅 owner 可见。V20260916_02 改造后此方法做权限过滤。
   */
  List<PetOutfitEntity> listByPetId(Long petId, Long userId);

  /**
   * 装备装扮。
   * <p>
   * 权限规则（V20260916_02 起强制）：
   * <ul>
   *   <li>系统装扮（user_id=null）：任何登录用户都可装备（社交体验）</li>
   *   <li>用户装扮（user_id=非null）：仅 owner 可装备</li>
   * </ul>
   */
  void equip(Long id, Long petId, Long userId);

  /**
   * 删除装扮。权限规则：
   * <ul>
   *   <li>系统装扮（user_id=null）：禁止删除（受保护）</li>
   *   <li>用户装扮（user_id=非null）：仅 owner 可删除</li>
   * </ul>
   */
  void delete(Long id, Long petId, Long userId);

  /**
   * 用户上传自定义装扮形象。
   * <p>
   * 流程：前端先调 {@code POST /api/v1/file/upload/image} 上传图片文件获取 imageUrl，
   * 再调本接口写入 mo_pet_outfit（user_id=当前用户, owned=true）。
   *
   * @param petId   目标宠物 ID（必须属于当前用户）
   * @param userId  上传用户 ID（自动写入 user_id 字段做归属）
   * @param category 装扮分类（hat/scarf/clothes/toy/custom）
   * @param name    用户命名（用于展示）
   * @param imageUrl 图片 URL（来自 UserUploadController 返回的 url 字段）
   * @return 写入的装扮记录（含自增 ID）
   */
  PetOutfitEntity uploadOutfit(Long petId, Long userId, String category, String name, String imageUrl);
}
