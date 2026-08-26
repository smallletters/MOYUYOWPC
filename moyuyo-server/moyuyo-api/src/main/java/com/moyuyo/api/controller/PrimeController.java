package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.Result;
import com.moyuyo.dao.entity.PrimePlanEntity;
import com.moyuyo.dao.mapper.PrimePlanMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Prime 会员")
@RestController
@RequestMapping("/api/v1/prime")
@RequiredArgsConstructor
public class PrimeController {

  private final PrimePlanMapper planMapper;

  @GetMapping("/plans")
  public Result<List<PrimePlanEntity>> plans() {
    return Result.success(planMapper.selectList(
        new LambdaQueryWrapper<PrimePlanEntity>()
            .eq(PrimePlanEntity::getActive, 1)
            .orderByAsc(PrimePlanEntity::getSortOrder)));
  }

  @GetMapping("/plans/{id}")
  public Result<PrimePlanEntity> planDetail(@PathVariable Long id) {
    return Result.success(planMapper.selectById(id));
  }
}
