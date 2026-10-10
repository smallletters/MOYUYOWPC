package com.moyuyo.service.admin;

import com.moyuyo.dao.admin.entity.DataExportRequestEntity;
import com.moyuyo.dao.admin.mapper.DataExportRequestMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 订单导出任务异步执行器（独立 Bean）。
 * <p>
 * 为什么单独建一个 Bean 而不是写在 {@code AdminOrderOpsServiceImpl} 里？
 * <p>
 * Spring 的 {@code @Async} 基于代理 AOP 实现，<b>同类内部的方法调用</b>（如
 * {@code createExportTask()} 直接调用本类的 {@code generateExportFile()}）
 * <b>不会</b>经过代理，因此异步注解直接失效，仍然阻塞主请求线程。
 * 抽到独立的 Component 后，跨 Bean 调用走 Spring 代理，{@code @Async} 才真正生效。
 * <p>
 * 这里的 executeExport() 不带事务包裹（单条 updateById，不涉及多语句一致性）
 * —— 异步线程里再开事务会额外占用连接，不划算；MyBatis-Plus 的 updateById 本身
 * 是一条独立 SQL，足以保证本次写入的原子性。
 * <p>
 * 失败语义：异常被捕获后写入 status=FAILED + remark，对应列表里能看到一行"失败"。
 */
@Component
@RequiredArgsConstructor
public class ExportFileExecutor {

  private static final Logger log = LoggerFactory.getLogger(ExportFileExecutor.class);

  private final DataExportRequestMapper exportRequestMapper;

  /**
   * 异步执行导出任务：模拟处理 500ms 后将任务标记为已完成，并回填下载链接。
   *
   * @param entity 已落库的导出请求实体（包含 id / exportId）
   */
  @Async("moyuyoTaskExecutor")
  public void executeExport(DataExportRequestEntity entity) {
    if (entity == null || entity.getExportId() == null) {
      log.warn("executeExport 收到空实体或空 exportId，跳过");
      return;
    }
    try {
      // 实际场景应替换为：查询订单 → 写入 Excel/CSV → 上传到 OSS → 记录下载链接
      // 当前为模拟实现，sleep 模拟导出处理耗时
      Thread.sleep(500);

      String downloadUrl = "/api/admin/order-ops/export/file/" + entity.getExportId();
      entity.setStatus("COMPLETED");
      entity.setDownloadUrl(downloadUrl);
      entity.setCompleteTime(LocalDateTime.now());
      exportRequestMapper.updateById(entity);
    } catch (Exception e) {
      // 异常详情仅记录日志，不对外暴露，防止敏感信息泄露
      log.error("导出任务执行失败: {}", entity.getExportId(), e);
      try {
        entity.setStatus("FAILED");
        entity.setRemark("导出失败，请联系管理员查看日志");
        exportRequestMapper.updateById(entity);
      } catch (Exception inner) {
        // 连"标记 FAILED"都失败时（如数据库瞬时不可用），仅日志记录；
        //   任务留 PENDING 等下一次手动重试或运维批量改库
        log.error("标记任务 FAILED 也失败了: {}", entity.getExportId(), inner);
      }
    }
  }
}
