package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.Result;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.entity.ServiceBookingEntity;
import com.moyuyo.dao.mapper.ServiceBookingMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "服务预约")
@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

  private final ServiceBookingMapper bookingMapper;

  @GetMapping
  public Result<Page<ServiceBookingEntity>> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return Result.success(bookingMapper.selectPage(new Page<>(page, size),
        new LambdaQueryWrapper<ServiceBookingEntity>()
            .eq(ServiceBookingEntity::getUserId, UserContextHolder.getUserId())
            .orderByDesc(ServiceBookingEntity::getBookingDate)));
  }

  @GetMapping("/{id}")
  public Result<ServiceBookingEntity> detail(@PathVariable Long id) {
    ServiceBookingEntity b = bookingMapper.selectById(id);
    if (b == null || !b.getUserId().equals(UserContextHolder.getUserId())) throw new IllegalArgumentException("预约不存在");
    return Result.success(b);
  }

  @PostMapping
  public Result<ServiceBookingEntity> create(@RequestBody Map<String, Object> body) {
    ServiceBookingEntity b = new ServiceBookingEntity();
    b.setUserId(UserContextHolder.getUserId());
    if (body.get("petId") != null) b.setPetId(((Number) body.get("petId")).longValue());
    b.setServiceType((String) body.get("serviceType"));
    b.setBookingDate(java.time.LocalDate.parse((String) body.get("bookingDate")));
    b.setBookingTime(java.time.LocalTime.parse((String) body.get("bookingTime")));
    b.setDurationMin(body.get("durationMin") == null ? 60 : ((Number) body.get("durationMin")).intValue());
    b.setAddress((String) body.get("address"));
    b.setContactPhone((String) body.get("contactPhone"));
    b.setNotes((String) body.get("notes"));
    b.setPrice(new java.math.BigDecimal(String.valueOf(body.getOrDefault("price", "0"))));
    b.setStatus("PENDING");
    bookingMapper.insert(b);
    return Result.success(b);
  }

  @PostMapping("/{id}/cancel")
  public Result<Void> cancel(@PathVariable Long id) {
    ServiceBookingEntity b = bookingMapper.selectById(id);
    if (b == null || !b.getUserId().equals(UserContextHolder.getUserId())) throw new IllegalArgumentException("预约不存在");
    b.setStatus("CANCELLED");
    bookingMapper.updateById(b);
    return Result.success();
  }
}
