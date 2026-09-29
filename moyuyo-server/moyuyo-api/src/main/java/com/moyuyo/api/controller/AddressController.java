package com.moyuyo.api.controller;

import com.moyuyo.common.Result;
import com.moyuyo.common.dto.address.AddressRequest;
import com.moyuyo.common.dto.address.AddressValidateResponse;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.entity.AddressEntity;
import com.moyuyo.service.AddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "地址管理")
@RestController
@RequestMapping("/api/v1/addresses")
@RequiredArgsConstructor
// 方法级校验：@PathVariable 上的 @Positive 才能生效（类级 @Validated 是方法级校验的开关）
@Validated
public class AddressController {

  private final AddressService addressService;

  @Operation(summary = "获取地址列表")
  @GetMapping
  public Result<List<AddressEntity>> list() {
    return Result.success(addressService.listByUserId(UserContextHolder.getUserId()));
  }

  @Operation(summary = "获取地址详情")
  @GetMapping("/{id}")
  public Result<AddressEntity> getById(@PathVariable @Positive(message = "地址 ID 必须为正整数") Long id) {
    try {
      AddressEntity entity = addressService.getById(id, UserContextHolder.getUserId());
      return Result.success(entity);
    } catch (IllegalArgumentException e) {
      return Result.error(404, "地址不存在");
    }
  }

  @Operation(summary = "创建地址")
  @PostMapping
  public Result<AddressEntity> create(@Valid @RequestBody AddressRequest request) {
    AddressEntity entity = buildEntity(request);
    return Result.success(addressService.create(UserContextHolder.getUserId(), entity));
  }

  @Operation(summary = "更新地址")
  @PutMapping("/{id}")
  public Result<AddressEntity> update(@PathVariable @Positive(message = "地址 ID 必须为正整数") Long id, @Valid @RequestBody AddressRequest request) {
    AddressEntity entity = buildEntity(request);
    entity.setId(id);
    return Result.success(addressService.update(UserContextHolder.getUserId(), entity));
  }

  @Operation(summary = "删除地址")
  @DeleteMapping("/{id}")
  public Result<Void> delete(@PathVariable @Positive(message = "地址 ID 必须为正整数") Long id) {
    addressService.delete(id, UserContextHolder.getUserId());
    return Result.success();
  }

  @Operation(summary = "设为默认地址")
  @PutMapping("/{id}/default")
  public Result<Void> setDefault(@PathVariable @Positive(message = "地址 ID 必须为正整数") Long id) {
    addressService.setDefault(id, UserContextHolder.getUserId());
    return Result.success();
  }

  @Operation(summary = "验证地址可配送")
  @GetMapping("/{id}/validate")
  public Result<AddressValidateResponse> validateAddress(@PathVariable @Positive(message = "地址 ID 必须为正整数") Long id) {
    return Result.success(addressService.validateAddress(id, UserContextHolder.getUserId()));
  }

  /**
   * 批量校验 APP 端地址列表可发货性。一次请求代替 N 次 /{id}/validate，避免 N+1。
   * 入参：用户当前所有地址 ID 列表；出参：归属当前用户的那部分地址的可发货结果。
   */
  @Operation(summary = "批量校验地址可配送（解决 N+1）")
  @PostMapping("/batch-validate")
  public Result<java.util.List<AddressValidateResponse.Item>> batchValidate(
      @RequestBody java.util.List<Long> addressIds) {
    return Result.success(addressService.batchValidate(addressIds, UserContextHolder.getUserId()));
  }

  @Operation(summary = "APP 国家选择器数据源：当前所有可配置的国家码（去重排序，ISO 3166-1 alpha-2）")
  @GetMapping("/supported-countries")
  public Result<java.util.List<String>> supportedCountries() {
    return Result.success(addressService.listSupportedCountries());
  }

  private AddressEntity buildEntity(AddressRequest request) {
    AddressEntity entity = new AddressEntity();
    entity.setReceiver(request.getReceiver());
    entity.setPhone(request.getPhone());
    entity.setCountry(request.getCountry());
    entity.setProvince(request.getProvince());
    entity.setCity(request.getCity());
    entity.setDistrict(request.getDistrict());
    entity.setDetail(request.getDetail());
    entity.setZipCode(request.getZipCode());
    entity.setTag(request.getTag());
    entity.setIsDefault(request.getIsDefault());
    return entity;
  }
}
