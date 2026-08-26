package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.Result;
import com.moyuyo.dao.entity.FestivalEventEntity;
import com.moyuyo.dao.mapper.FestivalEventMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "节日活动")
@RestController
@RequestMapping("/api/v1/festivals")
@RequiredArgsConstructor
public class FestivalController {

  private final FestivalEventMapper eventMapper;

  @GetMapping("/active")
  public Result<List<FestivalEventEntity>> active() {
    LocalDateTime now = LocalDateTime.now();
    return Result.success(eventMapper.selectList(
        new LambdaQueryWrapper<FestivalEventEntity>()
            .eq(FestivalEventEntity::getActive, 1)
            .le(FestivalEventEntity::getStartTime, now)
            .ge(FestivalEventEntity::getEndTime, now)
            .orderByDesc(FestivalEventEntity::getStartTime)));
  }

  @GetMapping("/{id}")
  public Result<FestivalEventEntity> detail(@PathVariable Long id) {
    return Result.success(eventMapper.selectById(id));
  }
}
