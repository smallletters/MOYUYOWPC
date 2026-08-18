package com.moyuyo.service.admin.impl;

import com.moyuyo.dao.admin.entity.DataExportRequestEntity;
import com.moyuyo.dao.admin.mapper.DataExportRequestMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 后台任务运行器，用于执行异步导出任务。
 * 该 Bean 会被 Spring AOP 拦截，调用方需要从外部 Bean 引用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExportTaskRunner {

    private final DataExportRequestMapper exportRequestMapper;

    /**
     * 生成导出文件。由 Spring 调度的 @Async 方法。
     */
    @Async
    public void generateExportFile(DataExportRequestEntity entity) throws InterruptedException {
        try {
            // 模拟耗时操作（实际应替换为 Excel/CSV 文件生成逻辑）
            Thread.sleep(500);

            String downloadUrl = "/api/admin/order-ops/export/download/" + entity.getExportId();
            entity.setStatus("COMPLETED");
            entity.setDownloadUrl(downloadUrl);
            entity.setCompleteTime(LocalDateTime.now());
            exportRequestMapper.updateById(entity);
        } catch (InterruptedException e) {
            // 线程被中断，恢复中断标记并更新任务状态
            Thread.currentThread().interrupt();
            log.warn("\u51fa\u53e3\u4efb\u52a1\u88ab\u4e2d\u65ad exportId={}", entity.getExportId());
            entity.setStatus("FAILED");
            entity.setRemark("\u4efb\u52a1\u88ab\u4e2d\u65ad\u7ec8\u6b62");
            exportRequestMapper.updateById(entity);
        } catch (Exception e) {
            // 异常处理：记录日志并更新任务状态为失败
            log.error("\u51fa\u53e3\u4efb\u52a1\u8fd0\u884c\u51fa\u9519 exportId={}", entity.getExportId(), e);
            entity.setStatus("FAILED");
            entity.setRemark("\u4efb\u52a1\u8fd0\u884c\u5f02\u5e38\uff0c\u8bf7\u8054\u7cfb\u8fd0\u7ef4");
            exportRequestMapper.updateById(entity);
        }
    }
}
