package com.moyuyo.common.dto.community;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 社区个性化信息流分页结果。
 *
 * 与旧的 Page 返回不同，推荐流使用快照 + 偏移游标分页：
 * - snapshotId + nextCursor 必须原样回传，保证翻页期间排序稳定
 * - feedVersion 用于客户端缓存失效（用户兴趣变化时递增）
 */
@Data
@Schema(description = "社区信息流分页")
public class CommunityFeedPageVO {

  private List<CommunityPostVO> records;

  /** 下一页游标；为空表示没有更多 */
  private String nextCursor;

  private Boolean hasMore;

  /** 推荐版本号：兴趣变化后变化，客户端可据此失效本地缓存 */
  private String feedVersion;

  /** 本次推荐快照 ID（服务端 Redis ZSET），客户端翻页时原样回传 */
  private String snapshotId;
}
