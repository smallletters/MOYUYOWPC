package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.moyuyo.common.Result;
import com.moyuyo.common.dto.notification.NotificationVO;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.common.utils.PageParamGuard;
import com.moyuyo.common.utils.PageUtils;
import com.moyuyo.dao.entity.NotificationEntity;
import com.moyuyo.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "通知管理")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

  private final NotificationService notificationService;

  @Operation(summary = "获取通知列表")
  @GetMapping
  public Result<IPage<NotificationVO>> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    // 分页守卫：防止 size=100000 触发大结果集与历史 bizType 回填的批量 IN 查询把数据库打满
    int[] pageParams = PageParamGuard.normalize(page, size, 20);
    IPage<NotificationEntity> entityPage =
        notificationService.listNotifications(UserContextHolder.getUserId(),
            pageParams[0], pageParams[1]);
    return Result.success(PageUtils.convertPage(entityPage, NotificationController::toVO));
  }

  @Operation(summary = "通知详情")
  @GetMapping("/{id}")
  public Result<NotificationVO> getDetail(@PathVariable Long id) {
    return Result.success(toVO(
        notificationService.getNotificationDetail(id, UserContextHolder.getUserId())));
  }

  /** 实体 → VO：去掉 userId 等内部字段，只返回展示与跳转所需信息 */
  private static NotificationVO toVO(NotificationEntity entity) {
    NotificationVO vo = new NotificationVO();
    vo.setId(entity.getId());
    vo.setType(entity.getType());
    vo.setBizType(entity.getBizType());
    vo.setTitle(entity.getTitle());
    vo.setContent(entity.getContent());
    vo.setRelatedId(entity.getRelatedId());
    vo.setRead(entity.getRead());
    vo.setCreateTime(entity.getCreateTime());
    return vo;
  }

  @Operation(summary = "标记已读")
  @PutMapping("/{id}/read")
  public Result<Void> markAsRead(@PathVariable Long id) {
    notificationService.markAsRead(id, UserContextHolder.getUserId());
    return Result.success();
  }

  @Operation(summary = "全部标记已读")
  @PostMapping("/read-all")
  public Result<Void> markAllAsRead() {
    notificationService.markAllAsRead(UserContextHolder.getUserId());
    return Result.success();
  }

  @Operation(summary = "删除通知")
  @DeleteMapping("/{id}")
  public Result<Void> delete(@PathVariable Long id) {
    notificationService.deleteNotification(id, UserContextHolder.getUserId());
    return Result.success();
  }

  @Operation(summary = "批量删除通知（ids 为空时清空全部，type 非空时只清该分类）")
  @DeleteMapping
  public Result<Integer> batchDelete(
      @RequestParam(required = false) List<Long> ids,
      @RequestParam(required = false) String type) {
    return Result.success(notificationService.deleteNotifications(
        UserContextHolder.getUserId(), ids, type));
  }
}
