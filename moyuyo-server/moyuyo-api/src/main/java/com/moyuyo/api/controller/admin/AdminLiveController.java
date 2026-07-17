package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.dao.entity.LiveRoomEntity;
import com.moyuyo.dao.entity.LiveRoomProductEntity;
import com.moyuyo.service.LiveRoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@Tag(name = "管理后台 - 直播管理")
@RestController
@RequestMapping("/api/admin/live")
@RequiredArgsConstructor
public class AdminLiveController {

  private final LiveRoomService liveRoomService;

  @Operation(summary = "直播间列表")
  @GetMapping("/rooms")
  public Result<List<Map<String, Object>>> rooms() {
    List<LiveRoomEntity> roomList = liveRoomService.list();
    List<Map<String, Object>> list = new ArrayList<>();
    for (LiveRoomEntity room : roomList) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", room.getId());
      item.put("title", room.getName());
      item.put("anchor", room.getHostName());
      item.put("status", room.getStatus());
      item.put("viewerCount", room.getViewerCount());
      item.put("productCount", room.getProductCount());
      item.put("startTime", room.getStartTime());
      list.add(item);
    }
    return Result.success(list);
  }

  @Operation(summary = "创建直播间")
  @PostMapping("/rooms")
  // TODO: 需要创建直播间后写入数据库
  public Result<Map<String, Object>> createRoom(@RequestBody Map<String, Object> body) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", new Random().nextInt(1000));
    result.put("message", "直播间创建成功");
    return Result.success(result);
  }

  @Operation(summary = "更新直播间")
  @PutMapping("/rooms/{id}")
  // TODO: 需要更新数据库中的直播间信息
  public Result<Map<String, Object>> updateRoom(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("message", "直播间更新成功");
    return Result.success(result);
  }

  @Operation(summary = "更新直播状态")
  @PutMapping("/rooms/{id}/status")
  public Result<Map<String, Object>> updateRoomStatus(@PathVariable Long id, @RequestParam String status) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("status", status);
    result.put("message", "直播状态更新成功");
    return Result.success(result);
  }

  @Operation(summary = "直播间详情")
  @GetMapping("/rooms/{id}")
  public Result<Map<String, Object>> roomDetail(@PathVariable Long id) {
    LiveRoomEntity room = liveRoomService.getDetail(id);
    List<LiveRoomProductEntity> products = liveRoomService.getProducts(id);

    Map<String, Object> item = new LinkedHashMap<>();
    item.put("id", room.getId());
    item.put("title", room.getName());
    item.put("anchor", room.getHostName());
    item.put("status", room.getStatus());
    item.put("viewerCount", room.getViewerCount());
    item.put("productCount", room.getProductCount());
    item.put("startTime", room.getStartTime());
    item.put("cover", room.getCoverUrl());

    // 商品列表
    List<Map<String, Object>> productList = new ArrayList<>();
    for (LiveRoomProductEntity p : products) {
      Map<String, Object> product = new LinkedHashMap<>();
      product.put("id", p.getId());
      product.put("productId", p.getProductId());
      product.put("sortOrder", p.getSortOrder());
      productList.add(product);
    }
    item.put("products", productList);
    return Result.success(item);
  }
}
