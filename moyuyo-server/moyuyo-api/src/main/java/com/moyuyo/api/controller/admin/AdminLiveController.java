package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.common.dto.admin.OperationResult;
import com.moyuyo.common.dto.admin.live.LiveRoomProductResponse;
import com.moyuyo.common.dto.admin.live.LiveRoomResponse;
import com.moyuyo.common.dto.admin.live.LiveRoomStatusRequest;
import com.moyuyo.dao.entity.LiveRoomEntity;
import com.moyuyo.dao.entity.LiveRoomProductEntity;
import com.moyuyo.dao.mapper.LiveRoomMapper;
import com.moyuyo.service.LiveRoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Tag(name = "管理后台 - 直播管理")
@RestController
@RequestMapping("/api/admin/live")
@RequiredArgsConstructor
public class AdminLiveController {

  private final LiveRoomService liveRoomService;
  private final LiveRoomMapper liveRoomMapper;

  @Operation(summary = "直播间列表")
  @GetMapping("/rooms")
  public Result<List<LiveRoomResponse>> rooms() {
    List<LiveRoomEntity> roomList = liveRoomService.list();
    List<LiveRoomResponse> list = new ArrayList<>();
    for (LiveRoomEntity room : roomList) {
      LiveRoomResponse item = new LiveRoomResponse();
      item.setId(room.getId());
      item.setName(room.getName());
      item.setStatus(room.getStatus());
      item.setViewerCount(room.getViewerCount());
      item.setProductCount(room.getProductCount());
      item.setStartTime(room.getStartTime());
      list.add(item);
    }
    return Result.success(list);
  }

  @Operation(summary = "创建直播间")
  @PostMapping("/rooms")
  public Result<OperationResult> createRoom(@RequestBody LiveRoomEntity entity) {
    // 写入数据库
    liveRoomService.createRoom(entity);
    OperationResult result = new OperationResult();
    result.setId(entity.getId());
    result.setMessage("直播间创建成功");
    return Result.success(result);
  }

  @Operation(summary = "更新直播间")
  @PutMapping("/rooms/{id}")
  public Result<OperationResult> updateRoom(@PathVariable Long id, @RequestBody LiveRoomEntity entity) {
    // 更新数据库中的直播间信息
    entity.setId(id);
    liveRoomService.updateRoom(entity);
    OperationResult result = new OperationResult();
    result.setId(id);
    result.setMessage("直播间更新成功");
    return Result.success(result);
  }

  @Operation(summary = "更新直播状态")
  @PutMapping("/rooms/{id}/status")
  public Result<OperationResult> updateRoomStatus(@PathVariable Long id, @RequestParam String status) {
    // 更新数据库中的直播间状态
    liveRoomService.updateRoomStatus(id, status);
    OperationResult result = new OperationResult();
    result.setId(id);
    result.setMessage("直播状态更新成功");
    return Result.success(result);
  }

  @Operation(summary = "直播间详情")
  @GetMapping("/rooms/{id}")
  public Result<LiveRoomResponse> roomDetail(@PathVariable Long id) {
    LiveRoomEntity room = liveRoomService.getDetail(id);
    if (room == null) {
      return Result.error("直播间不存在");
    }
    List<LiveRoomProductEntity> products = liveRoomService.getProducts(id);

    LiveRoomResponse item = new LiveRoomResponse();
    item.setId(room.getId());
    item.setName(room.getName());
    item.setStatus(room.getStatus());
    item.setViewerCount(room.getViewerCount());
    item.setProductCount(room.getProductCount());
    item.setStartTime(room.getStartTime());

    // 商品列表
    List<LiveRoomProductResponse> productList = new ArrayList<>();
    for (LiveRoomProductEntity p : products) {
      LiveRoomProductResponse product = new LiveRoomProductResponse();
      product.setId(p.getId());
      productList.add(product);
    }
    item.setProducts(productList);
    return Result.success(item);
  }

  @Operation(summary = "删除直播间")
  @DeleteMapping("/rooms/{id}")
  public Result<OperationResult> deleteRoom(@PathVariable Long id) {
    LiveRoomEntity entity = liveRoomMapper.selectById(id);
    if (entity == null) {
      return Result.error("直播间不存在");
    }
    liveRoomMapper.deleteById(id);
    OperationResult result = new OperationResult();
    result.setId(id);
    result.setMessage("直播间删除成功");
    return Result.success(result);
  }
}
