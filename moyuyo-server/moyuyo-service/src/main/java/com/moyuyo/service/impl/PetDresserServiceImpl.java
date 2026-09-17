package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.exception.BusinessException;
import com.moyuyo.dao.entity.PetEntity;
import com.moyuyo.dao.entity.PetOutfitEntity;
import com.moyuyo.dao.mapper.PetMapper;
import com.moyuyo.dao.mapper.PetOutfitMapper;
import com.moyuyo.service.PetDresserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 抑制 JDT 静态检查对 MyBatis-Plus Lambda 引用的 null type safety 警告
@SuppressWarnings("null")
@Slf4j
@Service
@RequiredArgsConstructor
public class PetDresserServiceImpl implements PetDresserService {

  private final PetOutfitMapper petOutfitMapper;
  /** 用于校验 petId 是否属于当前用户（防越权：上传到他人宠物下） */
  private final PetMapper petMapper;

  /**
   * 列出某宠物的装扮：系统默认（user_id=NULL）+ 当前用户上传（user_id=当前用户）。
   * <p>
   * 这里做了权限过滤（V20260916_02 改造后）：
   * 其他用户上传的自定义装扮**不在列表里**，防止数据泄露。
   * <p>
   * 注意：原来的 SQL {@code eq(petId)} 已被替换。
   */
  @Override
  public List<PetOutfitEntity> listByPetId(Long petId, Long userId) {
    return petOutfitMapper.selectList(
        new LambdaQueryWrapper<PetOutfitEntity>()
            .eq(PetOutfitEntity::getPetId, petId)
            // 系统装扮（user_id IS NULL）或本人上传（user_id = 当前用户）才返回
            .and(w -> w.isNull(PetOutfitEntity::getUserId)
                .or().eq(PetOutfitEntity::getUserId, userId))
            .orderByDesc(PetOutfitEntity::getCreateTime));
  }

  @Override
  @Transactional
  public void equip(Long id, Long petId, Long userId) {
    PetOutfitEntity existing = petOutfitMapper.selectById(id);
    if (existing == null || !existing.getPetId().equals(petId)) {
      throw new IllegalArgumentException("装扮不存在或无权操作");
    }
    // 权限校验(V20260916_02)：用户装扮仅 owner 可装备
    if (existing.getUserId() != null && !existing.getUserId().equals(userId)) {
      log.warn("PetOutfit equip denied: outfitId={} owned by userId={}, request by userId={}",
              id, existing.getUserId(), userId);
      throw new BusinessException(403, "无权装备他人的自定义装扮");
    }
    // 取消该宠物已装备的其他装扮（同一时刻只允许装备 1 个）
    List<PetOutfitEntity> equipped = petOutfitMapper.selectList(
        new LambdaQueryWrapper<PetOutfitEntity>()
            .eq(PetOutfitEntity::getPetId, petId)
            .eq(PetOutfitEntity::getEquipped, true));
    for (PetOutfitEntity item : equipped) {
      item.setEquipped(false);
      petOutfitMapper.updateById(item);
    }
    existing.setEquipped(true);
    petOutfitMapper.updateById(existing);
    log.info("PetOutfit equipped: outfitId={}, petId={}, by userId={}", id, petId, userId);
  }

  @Override
  @Transactional
  public void delete(Long id, Long petId, Long userId) {
    PetOutfitEntity existing = petOutfitMapper.selectById(id);
    if (existing == null || !existing.getPetId().equals(petId)) {
      throw new IllegalArgumentException("装扮不存在或无权操作");
    }
    // 系统装扮受保护：禁止任何用户删除(运营 seed 数据)
    if (existing.getUserId() == null) {
      log.warn("PetOutfit delete denied: outfitId={} is system default", id);
      throw new BusinessException(403, "系统默认装扮不可删除");
    }
    // 用户装扮仅 owner 可删除
    if (!existing.getUserId().equals(userId)) {
      log.warn("PetOutfit delete denied: outfitId={} owned by userId={}, request by userId={}",
              id, existing.getUserId(), userId);
      throw new BusinessException(403, "无权删除他人的自定义装扮");
    }
    petOutfitMapper.deleteById(id);
    log.info("PetOutfit deleted: outfitId={}, petId={}, by userId={}", id, petId, userId);
  }

  /**
   * 用户上传自定义装扮：写入 mo_pet_outfit(user_id=当前用户, owned=true)。
   * <p>
   * 安全校验：
   * <ol>
   *   <li>petId 必须属于当前用户（查 mo_pet），防止上传到他人宠物下</li>
   *   <li>category 白名单（避免任意文本污染分类筛选）</li>
   *   <li>imageUrl 必须以 http(s):// 或 /uploads/ 开头（防止恶意 URL 入库）</li>
   * </ol>
   */
  @Override
  @Transactional
  public PetOutfitEntity uploadOutfit(Long petId, Long userId, String category, String name, String imageUrl) {
    if (userId == null) {
      throw new IllegalArgumentException("User not logged in");
    }
    if (petId == null) {
      throw new IllegalArgumentException("宠物 ID 不能为空");
    }
    if (imageUrl == null || imageUrl.isBlank()) {
      throw new IllegalArgumentException("图片 URL 不能为空");
    }
    // 1) 校验 petId 归属
    PetEntity pet = petMapper.selectById(petId);
    if (pet == null) {
      throw new IllegalArgumentException("宠物不存在");
    }
    if (pet.getUserId() == null || !pet.getUserId().equals(userId)) {
      log.warn("PetOutfit upload denied: petId={} owned by userId={}, request by userId={}",
              petId, pet.getUserId(), userId);
      throw new BusinessException(403, "无权为他人宠物上传装扮");
    }
    // 2) category 白名单（与前端 tabs 对齐）
    String safeCategory = (category == null || category.isBlank()) ? "custom" : category.trim();
    if (!java.util.Set.of("hat", "scarf", "clothes", "toy", "custom").contains(safeCategory)) {
      throw new IllegalArgumentException("不支持的装扮分类: " + category);
    }
    // 3) imageUrl 协议白名单（与 ProfileUpdateRequest.isAvatarValid 同款策略）
    String trimmed = imageUrl.trim();
    String lower = trimmed.toLowerCase();
    if (lower.startsWith("javascript:") || lower.startsWith("data:") || lower.startsWith("vbscript:")) {
      throw new IllegalArgumentException("imageUrl 协议不合法");
    }
    if (!(lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("/uploads/"))) {
      throw new IllegalArgumentException("imageUrl 必须为 http(s):// 或 /uploads/ 路径");
    }
    if (trimmed.length() > 512) {
      throw new IllegalArgumentException("imageUrl 长度超限");
    }
    // 4) name 兜底
    String safeName = (name == null || name.isBlank()) ? "我的装扮" : name.trim();
    if (safeName.length() > 100) {
      safeName = safeName.substring(0, 100);
    }
    // 5) 落库
    PetOutfitEntity entity = new PetOutfitEntity();
    entity.setPetId(petId);
    entity.setUserId(userId);  // 关键:标记归属,只有 owner 能删
    entity.setCategory(safeCategory);
    entity.setName(safeName);
    entity.setImageUrl(trimmed);
    entity.setOwned(true);      // 用户拥有
    entity.setEquipped(false);  // 默认未装备
    entity.setPrice(null);      // 用户免费上传
    petOutfitMapper.insert(entity);
    log.info("PetOutfit uploaded: outfitId={}, petId={}, userId={}, category={}",
            entity.getId(), petId, userId, safeCategory);
    return entity;
  }
}
