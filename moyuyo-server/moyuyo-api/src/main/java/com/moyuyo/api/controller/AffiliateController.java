package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.Result;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.entity.AffiliateAccountEntity;
import com.moyuyo.dao.entity.AffiliateCommissionEntity;
import com.moyuyo.dao.mapper.AffiliateAccountMapper;
import com.moyuyo.dao.mapper.AffiliateCommissionMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "推广分销")
@RestController
@RequestMapping("/api/v1/affiliate")
@RequiredArgsConstructor
public class AffiliateController {

  private final AffiliateAccountMapper accountMapper;
  private final AffiliateCommissionMapper commissionMapper;

  @GetMapping("/account")
  public Result<AffiliateAccountEntity> myAccount() {
    Long userId = UserContextHolder.getUserId();
    AffiliateAccountEntity acc = accountMapper.selectOne(
        new LambdaQueryWrapper<AffiliateAccountEntity>()
            .eq(AffiliateAccountEntity::getUserId, userId));
    if (acc == null) {
      acc = new AffiliateAccountEntity();
      acc.setUserId(userId);
      acc.setLevel("BRONZE");
      acc.setStatus("ACTIVE");
      accountMapper.insert(acc);
    }
    return Result.success(acc);
  }

  @GetMapping("/commissions")
  public Result<Page<AffiliateCommissionEntity>> commissions(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return Result.success(commissionMapper.selectPage(new Page<>(page, size),
        new LambdaQueryWrapper<AffiliateCommissionEntity>()
            .eq(AffiliateCommissionEntity::getUserId, UserContextHolder.getUserId())
            .orderByDesc(AffiliateCommissionEntity::getCreateTime)));
  }
}
