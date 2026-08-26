package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.Result;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.entity.NewuserGiftClaimEntity;
import com.moyuyo.dao.entity.NewuserGiftEntity;
import com.moyuyo.dao.mapper.NewuserGiftClaimMapper;
import com.moyuyo.dao.mapper.NewuserGiftMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "新人礼包")
@RestController
@RequestMapping("/api/v1/newuser")
@RequiredArgsConstructor
public class NewuserGiftController {

  private final NewuserGiftMapper giftMapper;
  private final NewuserGiftClaimMapper claimMapper;

  @GetMapping("/gifts")
  public Result<List<NewuserGiftEntity>> gifts() {
    return Result.success(giftMapper.selectList(
        new LambdaQueryWrapper<NewuserGiftEntity>()
            .eq(NewuserGiftEntity::getActive, 1)));
  }

  @PostMapping("/gifts/{id}/claim")
  @Transactional
  public Result<NewuserGiftClaimEntity> claim(@PathVariable Long id) {
    Long userId = UserContextHolder.getUserId();
    NewuserGiftEntity gift = giftMapper.selectById(id);
    if (gift == null || gift.getActive() != 1) throw new IllegalArgumentException("礼包不存在或已下架");
    NewuserGiftClaimEntity exist = claimMapper.selectOne(
        new LambdaQueryWrapper<NewuserGiftClaimEntity>()
            .eq(NewuserGiftClaimEntity::getUserId, userId)
            .eq(NewuserGiftClaimEntity::getGiftId, id));
    if (exist != null) return Result.success(exist);
    NewuserGiftClaimEntity c = new NewuserGiftClaimEntity();
    c.setUserId(userId);
    c.setGiftId(id);
    c.setStatus("CLAIMED");
    c.setExpireAt(LocalDate.now().plusDays(gift.getClaimWindowDays() == null ? 30 : gift.getClaimWindowDays()).atStartOfDay());
    claimMapper.insert(c);
    return Result.success(c);
  }

  @GetMapping("/my")
  public Result<Page<NewuserGiftClaimEntity>> myClaims() {
    return Result.success(claimMapper.selectPage(new Page<>(1, 20),
        new LambdaQueryWrapper<NewuserGiftClaimEntity>()
            .eq(NewuserGiftClaimEntity::getUserId, UserContextHolder.getUserId())
            .orderByDesc(NewuserGiftClaimEntity::getClaimTime)));
  }
}
