package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.dao.entity.LiveRoomEntity;
import com.moyuyo.dao.entity.LiveRoomProductEntity;
import com.moyuyo.dao.mapper.LiveRoomMapper;
import com.moyuyo.dao.mapper.LiveRoomProductMapper;
import com.moyuyo.service.LiveRoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveRoomServiceImpl implements LiveRoomService {

  private final LiveRoomMapper liveRoomMapper;
  private final LiveRoomProductMapper liveRoomProductMapper;

  @Override
  public List<LiveRoomEntity> list() {
    return liveRoomMapper.selectList(
        new LambdaQueryWrapper<LiveRoomEntity>()
            .eq(LiveRoomEntity::getStatus, "LIVE")
            .orderByDesc(LiveRoomEntity::getViewerCount));
  }

  @Override
  public LiveRoomEntity getDetail(Long id) {
    LiveRoomEntity entity = liveRoomMapper.selectById(id);
    if (entity == null) {
      throw new IllegalArgumentException("直播间不存在");
    }
    return entity;
  }

  @Override
  public List<LiveRoomProductEntity> getProducts(Long id) {
    return liveRoomProductMapper.selectList(
        new LambdaQueryWrapper<LiveRoomProductEntity>()
            .eq(LiveRoomProductEntity::getRoomId, id)
            .orderByAsc(LiveRoomProductEntity::getSortOrder));
  }

  @Override
  @Transactional
  public void createRoom(LiveRoomEntity entity) {
    liveRoomMapper.insert(entity);
    log.info("直播间创建成功: id={}, name={}", entity.getId(), entity.getName());
  }

  @Override
  @Transactional
  public void updateRoom(LiveRoomEntity entity) {
    liveRoomMapper.updateById(entity);
    log.info("直播间更新成功: id={}", entity.getId());
  }

  @Override
  @Transactional
  public void updateRoomStatus(Long id, String status) {
    LiveRoomEntity entity = liveRoomMapper.selectById(id);
    if (entity != null) {
      entity.setStatus(status);
      liveRoomMapper.updateById(entity);
      log.info("直播状态更新成功: id={}, status={}", id, status);
    }
  }
}
