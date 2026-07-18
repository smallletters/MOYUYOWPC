package com.moyuyo.service;

import com.moyuyo.dao.entity.LiveRoomEntity;
import com.moyuyo.dao.entity.LiveRoomProductEntity;

import java.util.List;

public interface LiveRoomService {

  List<LiveRoomEntity> list();

  LiveRoomEntity getDetail(Long id);

  List<LiveRoomProductEntity> getProducts(Long id);

  /**
   * 创建直播间
   */
  void createRoom(LiveRoomEntity entity);

  /**
   * 更新直播间
   */
  void updateRoom(LiveRoomEntity entity);

  /**
   * 更新直播状态
   */
  void updateRoomStatus(Long id, String status);
}
