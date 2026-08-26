package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.Result;
import com.moyuyo.dao.entity.FollowEntity;
import com.moyuyo.dao.entity.UserEntity;
import com.moyuyo.dao.mapper.FollowMapper;
import com.moyuyo.dao.mapper.UserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "用户主页")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

  private final UserMapper userMapper;
  private final FollowMapper followMapper;

  @GetMapping("/{id}/profile")
  public Result<Map<String, Object>> profile(@PathVariable Long id) {
    UserEntity u = userMapper.selectById(id);
    if (u == null) throw new IllegalArgumentException("用户不存在");
    long following = followMapper.selectCount(
        new LambdaQueryWrapper<FollowEntity>().eq(FollowEntity::getUserId, id));
    long followers = followMapper.selectCount(
        new LambdaQueryWrapper<FollowEntity>().eq(FollowEntity::getTargetId, id));
    Map<String, Object> p = new HashMap<>();
    p.put("id", u.getId());
    p.put("nickname", u.getNickname());
    p.put("avatar", u.getAvatar());
    p.put("country", u.getCountry());
    p.put("gender", u.getGender());
    p.put("bio", "");
    p.put("points", u.getPoints());
    p.put("following", following);
    p.put("followers", followers);
    p.put("isFollowing", false);
    return Result.success(p);
  }
}
