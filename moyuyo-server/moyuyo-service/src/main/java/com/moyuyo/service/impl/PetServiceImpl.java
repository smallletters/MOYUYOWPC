package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.moyuyo.common.dto.pet.PetAchievementVO;
import com.moyuyo.common.dto.pet.PetCareSummaryVO;
import com.moyuyo.common.dto.pet.PetReminderVO;
import com.moyuyo.common.dto.pet.PetSceneVO;
import com.moyuyo.common.dto.pet.PetVO;
import com.moyuyo.dao.entity.GrowthRecordEntity;
import com.moyuyo.dao.entity.PetAchievementEntity;
import com.moyuyo.dao.entity.PetAlbumEntity;
import com.moyuyo.dao.entity.PetDiaryEntity;
import com.moyuyo.dao.entity.PetEntity;
import com.moyuyo.dao.entity.PetOutfitEntity;
import com.moyuyo.dao.entity.PetReminderEntity;
import com.moyuyo.dao.entity.PetSceneEntity;
import com.moyuyo.dao.entity.PetWeightEntity;
import com.moyuyo.dao.mapper.GrowthRecordMapper;
import com.moyuyo.dao.mapper.PetAchievementMapper;
import com.moyuyo.dao.mapper.PetAlbumMapper;
import com.moyuyo.dao.mapper.PetDiaryMapper;
import com.moyuyo.dao.mapper.PetMapper;
import com.moyuyo.dao.mapper.PetOutfitMapper;
import com.moyuyo.dao.mapper.PetReminderMapper;
import com.moyuyo.dao.mapper.PetSceneMapper;
import com.moyuyo.dao.mapper.PetWeightMapper;
import com.moyuyo.service.PetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PetServiceImpl implements PetService {

  private final PetMapper petMapper;
  private final PetAchievementMapper petAchievementMapper;
  private final PetSceneMapper petSceneMapper;
  private final GrowthRecordMapper growthRecordMapper;
  private final PetReminderMapper petReminderMapper;
  private final PetWeightMapper petWeightMapper;
  private final PetAlbumMapper petAlbumMapper;
  private final PetDiaryMapper petDiaryMapper;
  private final PetOutfitMapper petOutfitMapper;

  /** 主页护理卡的固定展示顺序（洗护/疫苗/驱虫/体检） */
  private static final List<String> CARE_TYPE_ORDER =
      List.of("BATH", "VACCINE", "DEWORM", "EXAM");

  /** 各护理类型的默认周期（天）：仅在首次产生记录、尚未配置提醒时用于自动生成计划 */
  private static final Map<String, Integer> DEFAULT_CYCLE_DAYS =
      Map.of("BATH", 7, "VACCINE", 365, "DEWORM", 90, "EXAM", 365);

  /** 未配置提醒时新建提醒的默认提前提醒天数 */
  private static final int DEFAULT_ADVANCE_DAYS = 3;

  @Override
  public List<PetVO> listByUserId(Long userId) {
    List<PetEntity> pets = petMapper.selectList(
        new LambdaQueryWrapper<PetEntity>()
            .eq(PetEntity::getUserId, userId)
            .orderByDesc(PetEntity::getCreatedAt));

    return pets.stream()
        .map(this::convertToPetVO)
        .collect(Collectors.toList());
  }

  @Override
  public PetVO getPetDetail(Long petId, Long userId) {
    PetEntity entity = petMapper.selectById(petId);
    if (entity == null || !entity.getUserId().equals(userId)) {
      throw new IllegalArgumentException("宠物不存在或无权访问");
    }
    return convertToPetVO(entity);
  }

  @Override
  @Transactional
  public PetEntity createPet(Long userId, PetEntity pet) {
    pet.setId(null);
    pet.setUserId(userId);
    // 前端 picker 直接传"狗狗/猫咪/兔子/鸟类/其他"中文，需映射成 type 枚举值再写入 DB
    pet.setType(deriveType(pet.getSpecies(), pet.getType()));
    petMapper.insert(pet);
    log.info("Pet created: userId={}, petId={}, name={}", userId, pet.getId(), pet.getName());
    return pet;
  }

  @Override
  @Transactional
  public PetEntity updatePet(Long userId, PetEntity pet) {
    PetEntity existing = petMapper.selectById(pet.getId());
    if (existing == null || !existing.getUserId().equals(userId)) {
      throw new IllegalArgumentException("宠物不存在或无权操作");
    }
    pet.setUserId(userId);
    pet.setType(deriveType(pet.getSpecies(), pet.getType()));
    petMapper.updateById(pet);
    return petMapper.selectById(pet.getId());
  }

  @Override
  @Transactional
  public PetEntity updatePetStatus(Long userId, Long petId, String status) {
    if (status == null || status.isBlank()) {
      throw new IllegalArgumentException("宠物状态不能为空");
    }
    String normalized = status.trim().toUpperCase();
    // 仅接受三种已定义状态，避免任意字符串写入
    if (!List.of("ACTIVE", "DIED", "ACCIDENT", "MEDICAL").contains(normalized)) {
      throw new IllegalArgumentException("非法的宠物状态：" + status);
    }
    PetEntity existing = petMapper.selectById(petId);
    if (existing == null) {
      throw new IllegalArgumentException("宠物不存在");
    }
    if (!existing.getUserId().equals(userId)) {
      throw new IllegalArgumentException("宠物不存在或无权操作");
    }
    // 用 LambdaUpdateWrapper 只更新 status 字段，避免整表覆盖其他属性
    petMapper.update(null, new LambdaUpdateWrapper<PetEntity>()
        .eq(PetEntity::getId, petId)
        .set(PetEntity::getStatus, normalized));
    log.info("Pet status updated: petId={}, userId={}, status={}", petId, userId, normalized);
    return petMapper.selectById(petId);
  }

  /**
   * 把前端传过来的种类归一化为 DB type（DOG / CAT / OTHER）。
   * 兼容两种输入：
   *  1. 前端 species 已是英文枚举（DOG/CAT/...）→ 直接复用
   *  2. 前端 species 是中文（狗狗/猫咪/...）→ 关键词映射
   * 已为 null直接回退 OTHER。
   */
  private String deriveType(String species, String type) {
    if (type != null && !type.isBlank()) {
      String u = type.trim().toUpperCase();
      if (u.equals("DOG") || u.equals("CAT") || u.equals("OTHER")) return u;
    }
    if (species == null) return "OTHER";
    String s = species.trim();
    if (s.isEmpty()) return "OTHER";
    if (s.contains("狗") || s.equalsIgnoreCase("DOG")) return "DOG";
    if (s.contains("猫") || s.equalsIgnoreCase("CAT")) return "CAT";
    return "OTHER";
  }

  @Override
  @Transactional
  public void deletePet(Long petId, Long userId) {
    PetEntity existing = petMapper.selectById(petId);
    if (existing == null || !existing.getUserId().equals(userId)) {
      throw new IllegalArgumentException("宠物不存在或无权操作");
    }
    // 级联删除所有关联子表数据，避免孤儿记录和外键约束
    petWeightMapper.delete(new LambdaQueryWrapper<PetWeightEntity>()
        .eq(PetWeightEntity::getPetId, petId));
    petAlbumMapper.delete(new LambdaQueryWrapper<PetAlbumEntity>()
        .eq(PetAlbumEntity::getPetId, petId));
    petDiaryMapper.delete(new LambdaQueryWrapper<PetDiaryEntity>()
        .eq(PetDiaryEntity::getPetId, petId));
    petOutfitMapper.delete(new LambdaQueryWrapper<PetOutfitEntity>()
        .eq(PetOutfitEntity::getPetId, petId));
    petReminderMapper.delete(new LambdaQueryWrapper<PetReminderEntity>()
        .eq(PetReminderEntity::getPetId, petId));
    petSceneMapper.delete(new LambdaQueryWrapper<PetSceneEntity>()
        .eq(PetSceneEntity::getPetId, petId));
    petAchievementMapper.delete(new LambdaQueryWrapper<PetAchievementEntity>()
        .eq(PetAchievementEntity::getPetId, petId));
    growthRecordMapper.delete(new LambdaQueryWrapper<GrowthRecordEntity>()
        .eq(GrowthRecordEntity::getPetId, petId));
    petMapper.deleteById(petId);
    log.info("Pet deleted: petId={}, userId={}", petId, userId);
  }

  @Override
  public List<GrowthRecordEntity> getGrowthRecords(Long petId, Long userId) {
    checkPetOwnership(petId, userId);
    return growthRecordMapper.selectList(
        new LambdaQueryWrapper<GrowthRecordEntity>()
            .eq(GrowthRecordEntity::getPetId, petId)
            .orderByDesc(GrowthRecordEntity::getRecordDate));
  }

  @Override
  @Transactional
  public GrowthRecordEntity createGrowthRecord(Long userId, GrowthRecordEntity record) {
    checkPetOwnership(record.getPetId(), userId);
    String type = normalizeCareType(record.getRecordType());
    if (type == null) {
      throw new IllegalArgumentException("护理类型不能为空");
    }
    if (record.getRecordDate() == null) {
      throw new IllegalArgumentException("护理日期不能为空");
    }
    record.setId(null);
    record.setRecordType(type);
    record.setUserId(userId);
    growthRecordMapper.insert(record);
    // 记录写入联动：产生护理记录后，自动把该类型下次护理日期滚动到 本次 + 周期，
    // 首次记录（尚无提醒配置）则自动生成一条默认提醒计划
    syncReminderByRecord(record);
    log.info("GrowthRecord created: petId={}, type={}, date={}", record.getPetId(), type,
        record.getRecordDate());
    return record;
  }

  /** 记录 → 提醒联动：按 pet_id + reminder_type 做 upsert（保留用户手动设置的周期/提前天数） */
  private void syncReminderByRecord(GrowthRecordEntity record) {
    String type = record.getRecordType();
    // 非护理计划类型（不在默认周期表内）只记流水，不联动提醒
    if (!DEFAULT_CYCLE_DAYS.containsKey(type)) {
      return;
    }
    LocalDate doneDate = record.getRecordDate();
    PetReminderEntity existing = petReminderMapper.selectOne(
        new LambdaQueryWrapper<PetReminderEntity>()
            .eq(PetReminderEntity::getPetId, record.getPetId())
            .eq(PetReminderEntity::getReminderType, type));
    if (existing != null) {
      // 已有提醒：保留 interval/advance 等人工配置，仅滚动上次/下次日期
      int cycle = (existing.getIntervalDays() != null && existing.getIntervalDays() > 0)
          ? existing.getIntervalDays()
          : DEFAULT_CYCLE_DAYS.get(type);
      PetReminderEntity patch = new PetReminderEntity();
      patch.setId(existing.getId());
      patch.setLastNotifiedDate(doneDate);
      patch.setNextDate(doneDate.plusDays(cycle));
      petReminderMapper.updateById(patch);
    } else {
      // 无提醒配置：按类型默认周期自动生成一条提醒计划
      int cycle = DEFAULT_CYCLE_DAYS.get(type);
      PetReminderEntity created = new PetReminderEntity();
      created.setPetId(record.getPetId());
      created.setReminderType(type);
      created.setIntervalDays(cycle);
      created.setAdvanceDays(DEFAULT_ADVANCE_DAYS);
      created.setEnabled(true);
      created.setLastNotifiedDate(doneDate);
      created.setNextDate(doneDate.plusDays(cycle));
      petReminderMapper.insert(created);
    }
  }

  /** 归一化护理类型：非空 → 大写，返回 null 表示非法空值 */
  private String normalizeCareType(String recordType) {
    if (recordType == null || recordType.isBlank()) {
      return null;
    }
    return recordType.trim().toUpperCase();
  }

  @Override
  @Transactional
  public void deleteGrowthRecord(Long userId, Long petId, Long recordId) {
    checkPetOwnership(petId, userId);
    GrowthRecordEntity record = growthRecordMapper.selectById(recordId);
    if (record == null || !record.getPetId().equals(petId)) {
      throw new IllegalArgumentException("记录不存在或不属于该宠物");
    }
    String type = record.getRecordType();
    // 仅当删除的是该类型“最近一条”时，删除后才需要按剩余记录重算提醒；
    // 删除历史记录不应覆盖用户手动设置的 nextDate
    boolean wasLatest = false;
    if (type != null) {
      List<GrowthRecordEntity> sameType = growthRecordMapper.selectList(
          new LambdaQueryWrapper<GrowthRecordEntity>()
              .eq(GrowthRecordEntity::getPetId, petId)
              .eq(GrowthRecordEntity::getRecordType, type)
              .orderByDesc(GrowthRecordEntity::getRecordDate)
              .last("LIMIT 1"));
      wasLatest = !sameType.isEmpty() && sameType.get(0).getId().equals(recordId);
    }
    growthRecordMapper.deleteById(recordId);
    if (wasLatest) {
      // 删除联动：按剩余最近一条记录回算该类型的上次/下次日期（记录驱动）
      resyncReminderForType(petId, type);
    }
    log.info("GrowthRecord deleted: petId={}, recordId={}, type={}", petId, recordId, type);
  }

  /**
   * 删除成长记录后，用该类型剩余最近一条记录重新推算提醒；
   * 若已无任何记录，则只清空“上次护理”标记，保留用户设置的提醒计划。
   */
  private void resyncReminderForType(Long petId, String type) {
    if (type == null || !DEFAULT_CYCLE_DAYS.containsKey(type)) {
      return;
    }
    PetReminderEntity reminder = petReminderMapper.selectOne(
        new LambdaQueryWrapper<PetReminderEntity>()
            .eq(PetReminderEntity::getPetId, petId)
            .eq(PetReminderEntity::getReminderType, type));
    if (reminder == null) {
      return;
    }
    List<GrowthRecordEntity> remain = growthRecordMapper.selectList(
        new LambdaQueryWrapper<GrowthRecordEntity>()
            .eq(GrowthRecordEntity::getPetId, petId)
            .eq(GrowthRecordEntity::getRecordType, type)
            .orderByDesc(GrowthRecordEntity::getRecordDate));
    // 显式 set 以便支持置空，避免 MP NOT_NULL 策略吞掉 null 更新
    if (remain.isEmpty()) {
      petReminderMapper.update(null, new LambdaUpdateWrapper<PetReminderEntity>()
          .eq(PetReminderEntity::getId, reminder.getId())
          .set(PetReminderEntity::getLastNotifiedDate, null));
      return;
    }
    LocalDate lastDate = remain.get(0).getRecordDate();
    int cycle = (reminder.getIntervalDays() != null && reminder.getIntervalDays() > 0)
        ? reminder.getIntervalDays()
        : DEFAULT_CYCLE_DAYS.get(type);
    petReminderMapper.update(null, new LambdaUpdateWrapper<PetReminderEntity>()
        .eq(PetReminderEntity::getId, reminder.getId())
        .set(PetReminderEntity::getLastNotifiedDate, lastDate)
        .set(PetReminderEntity::getNextDate, lastDate.plusDays(cycle)));
  }

  @Override
  public List<PetReminderVO> getReminders(Long petId, Long userId) {
    checkPetOwnership(petId, userId);
    List<PetReminderEntity> entities = petReminderMapper.selectList(
        new LambdaQueryWrapper<PetReminderEntity>()
            .eq(PetReminderEntity::getPetId, petId)
            .orderByAsc(PetReminderEntity::getNextDate));

    return entities.stream()
        .map(this::convertToReminderVO)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional
  public PetReminderEntity updateReminder(Long userId, PetReminderEntity reminder) {
    Long id = reminder.getId();
    // 1. reminderId > 0：按 ID 更新已有提醒（旧契约兼容）
    if (id != null && id > 0) {
      PetReminderEntity existing = petReminderMapper.selectById(id);
      if (existing == null) {
        throw new IllegalArgumentException("提醒不存在");
      }
      checkPetOwnership(existing.getPetId(), userId);
      if (reminder.getPetId() != null && !reminder.getPetId().equals(existing.getPetId())) {
        throw new IllegalArgumentException("宠物与提醒不匹配");
      }
      reminder.setPetId(existing.getPetId());
      petReminderMapper.updateById(reminder);
      return petReminderMapper.selectById(id);
    }

    // 2. reminderId=0 / null：按 petId + reminderType 做 upsert（不存在则新建）
    if (reminder.getPetId() == null) {
      throw new IllegalArgumentException("宠物ID不能为空");
    }
    String type = normalizeCareType(reminder.getReminderType());
    if (type == null) {
      throw new IllegalArgumentException("提醒类型不能为空");
    }
    checkPetOwnership(reminder.getPetId(), userId);

    PetReminderEntity existing = petReminderMapper.selectOne(
        new LambdaQueryWrapper<PetReminderEntity>()
            .eq(PetReminderEntity::getPetId, reminder.getPetId())
            .eq(PetReminderEntity::getReminderType, type));
    if (existing != null) {
      // 已存在：只合并本次传入的非空字段，避免覆盖未传的配置
      PetReminderEntity patch = new PetReminderEntity();
      patch.setId(existing.getId());
      if (reminder.getNextDate() != null) {
        patch.setNextDate(reminder.getNextDate());
      }
      if (reminder.getIntervalDays() != null && reminder.getIntervalDays() > 0) {
        patch.setIntervalDays(reminder.getIntervalDays());
      }
      if (reminder.getAdvanceDays() != null && reminder.getAdvanceDays() > 0) {
        patch.setAdvanceDays(reminder.getAdvanceDays());
      }
      if (reminder.getEnabled() != null) {
        patch.setEnabled(reminder.getEnabled());
      }
      if (reminder.getLastNotifiedDate() != null) {
        patch.setLastNotifiedDate(reminder.getLastNotifiedDate());
      }
      if (reminder.getExtra() != null) {
        patch.setExtra(reminder.getExtra());
      }
      petReminderMapper.updateById(patch);
      return petReminderMapper.selectById(existing.getId());
    }

    // 3. 新建提醒：补默认周期/提前天数，缺 nextDate 时按 lastDate（或今天）+ 周期推算
    int interval = (reminder.getIntervalDays() != null && reminder.getIntervalDays() > 0)
        ? reminder.getIntervalDays()
        : DEFAULT_CYCLE_DAYS.getOrDefault(type, 7);
    int advance = (reminder.getAdvanceDays() != null && reminder.getAdvanceDays() > 0)
        ? reminder.getAdvanceDays()
        : DEFAULT_ADVANCE_DAYS;
    PetReminderEntity created = new PetReminderEntity();
    created.setPetId(reminder.getPetId());
    created.setReminderType(type);
    created.setIntervalDays(interval);
    created.setAdvanceDays(advance);
    created.setEnabled(reminder.getEnabled() == null ? Boolean.TRUE : reminder.getEnabled());
    created.setLastNotifiedDate(reminder.getLastNotifiedDate());
    created.setExtra(reminder.getExtra());
    if (reminder.getNextDate() != null) {
      created.setNextDate(reminder.getNextDate());
    } else {
      LocalDate base = (reminder.getLastNotifiedDate() != null)
          ? reminder.getLastNotifiedDate()
          : LocalDate.now();
      created.setNextDate(base.plusDays(interval));
    }
    petReminderMapper.insert(created);
    return created;
  }

  @Override
  public List<PetCareSummaryVO> getCareSummary(Long petId, Long userId) {
    checkPetOwnership(petId, userId);

    // 1. 提醒配置按类型索引（uk_pet_reminder 保证同 pet+type 唯一）
    List<PetReminderEntity> reminders = petReminderMapper.selectList(
        new LambdaQueryWrapper<PetReminderEntity>()
            .eq(PetReminderEntity::getPetId, petId));
    Map<String, PetReminderEntity> reminderByType = reminders.stream()
        .collect(Collectors.toMap(
            PetReminderEntity::getReminderType,
            r -> r,
            (a, b) -> a));

    // 2. 成长记录按类型聚合：取最近一次护理日期 + 记录条数
    List<GrowthRecordEntity> records = growthRecordMapper.selectList(
        new LambdaQueryWrapper<GrowthRecordEntity>()
            .eq(GrowthRecordEntity::getPetId, petId)
            .orderByDesc(GrowthRecordEntity::getRecordDate));
    Map<String, LocalDate> lastDateByType = new HashMap<>();
    Map<String, Integer> countByType = new HashMap<>();
    for (GrowthRecordEntity record : records) {
      String type = record.getRecordType();
      if (type == null) {
        continue;
      }
      lastDateByType.putIfAbsent(type, record.getRecordDate());
      countByType.merge(type, 1, Integer::sum);
    }

    // 3. 按固定顺序输出；即使某类型尚未有任何配置也返回，保证前端卡片结构稳定
    LocalDate today = LocalDate.now();
    List<PetCareSummaryVO> result = new ArrayList<>();
    for (String type : CARE_TYPE_ORDER) {
      result.add(buildCareSummary(
          type,
          reminderByType.get(type),
          lastDateByType.get(type),
          countByType.getOrDefault(type, 0),
          today));
    }
    // 兜底：扩展类型（固定顺序之外）如已有数据也一并返回
    reminderByType.keySet().stream()
        .filter(type -> !CARE_TYPE_ORDER.contains(type))
        .forEach(type -> result.add(buildCareSummary(
            type,
            reminderByType.get(type),
            lastDateByType.get(type),
            countByType.getOrDefault(type, 0),
            today)));
    return result;
  }

  /** 组装单个护理类型的聚合摘要（最近记录 + 提醒配置） */
  private PetCareSummaryVO buildCareSummary(String careType, PetReminderEntity reminder,
      LocalDate lastRecordDate, int recordCount, LocalDate today) {
    PetCareSummaryVO vo = new PetCareSummaryVO();
    vo.setCareType(careType);
    vo.setRecordCount(recordCount);
    vo.setHasRecord(lastRecordDate != null);
    vo.setLastRecordDate(lastRecordDate);
    vo.setDaysAgo(lastRecordDate == null
        ? null
        : (int) ChronoUnit.DAYS.between(lastRecordDate, today));

    if (reminder != null) {
      vo.setEnabled(reminder.getEnabled());
      vo.setNextDate(reminder.getNextDate());
      vo.setIntervalDays(reminder.getIntervalDays());
      vo.setAdvanceDays(reminder.getAdvanceDays());
      vo.setLastNotifiedDate(reminder.getLastNotifiedDate());
      vo.setExtra(reminder.getExtra());
      LocalDate next = reminder.getNextDate();
      if (next != null) {
        long gap = ChronoUnit.DAYS.between(today, next);
        vo.setDaysUntilNext((int) gap);
        vo.setOverdue(gap < 0);
      }
    }
    return vo;
  }

  @Override
  public List<PetAchievementEntity> getAchievements(Long petId, Long userId) {
    checkPetOwnership(petId, userId);
    return petAchievementMapper.selectList(
        new LambdaQueryWrapper<PetAchievementEntity>()
            .eq(PetAchievementEntity::getPetId, petId)
            .orderByAsc(PetAchievementEntity::getAchievementCode));
  }

  private void checkPetOwnership(Long petId, Long userId) {
    PetEntity entity = petMapper.selectById(petId);
    if (entity == null || !entity.getUserId().equals(userId)) {
      throw new IllegalArgumentException("宠物不存在或无权操作");
    }
  }

  private PetVO convertToPetVO(PetEntity entity) {
    PetVO vo = new PetVO();
    BeanUtils.copyProperties(entity, vo);

    List<PetAchievementEntity> achievements = petAchievementMapper.selectList(
        new LambdaQueryWrapper<PetAchievementEntity>()
            .eq(PetAchievementEntity::getPetId, entity.getId()));

    List<PetSceneEntity> scenes = petSceneMapper.selectList(
        new LambdaQueryWrapper<PetSceneEntity>()
            .eq(PetSceneEntity::getPetId, entity.getId())
            .orderByAsc(PetSceneEntity::getSortOrder));

    vo.setAchievements(convertAchievementList(achievements));
    vo.setScenes(convertSceneList(scenes));
    return vo;
  }

  private List<PetAchievementVO> convertAchievementList(List<PetAchievementEntity> entities) {
    if (entities == null) {
      return Collections.emptyList();
    }
    return entities.stream().map(e -> {
      PetAchievementVO vo = new PetAchievementVO();
      BeanUtils.copyProperties(e, vo);
      return vo;
    }).collect(Collectors.toList());
  }

  private List<PetSceneVO> convertSceneList(List<PetSceneEntity> entities) {
    if (entities == null) {
      return Collections.emptyList();
    }
    return entities.stream().map(e -> {
      PetSceneVO vo = new PetSceneVO();
      BeanUtils.copyProperties(e, vo);
      return vo;
    }).collect(Collectors.toList());
  }

  private PetReminderVO convertToReminderVO(PetReminderEntity entity) {
    PetReminderVO vo = new PetReminderVO();
    BeanUtils.copyProperties(entity, vo);
    return vo;
  }
}
