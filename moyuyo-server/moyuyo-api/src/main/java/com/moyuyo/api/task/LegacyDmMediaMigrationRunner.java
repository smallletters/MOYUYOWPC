package com.moyuyo.api.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.moyuyo.dao.entity.DmMessageEntity;
import com.moyuyo.dao.mapper.DmMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;

/**
 * 历史私信附件迁移：把落在公开上传目录的私信图片/语音/文件移动到 {@code /uploads/dm/}，
 * 并同步更新 {@code mo_dm_message.content}。
 * <p>
 * 背景：早期版本私信语音/文件直接落在 {@code /uploads/yyyy/MM/dd/}，该目录是公开静态资源，
 * 任何拿到 URL 的人都能访问。新版本已改为落 {@code dm/} 并由签名过滤器保护，
 * 但历史文件仍暴露，需要在升级后做一次性迁移。
 * <p>
 * 安全约束：
 * - 只处理 {@code mo_dm_message} 中 IMAGE/VOICE/FILE 类型且 content 以 {@code /uploads/} 开头的记录，
 *   不会误动社区/商品等公开图片
 * - 幂等：迁移后 content 以 {@code /uploads/dm/} 开头，不会被再次扫描
 * - 多实例：用 Redis 短锁互斥，避免同时搬同一文件
 * - 默认开启，可通过 {@code MOYUYO_MIGRATE_LEGACY_DM=false} 关闭
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "moyuyo.upload.migrate-legacy-dm", havingValue = "true", matchIfMissing = true)
public class LegacyDmMediaMigrationRunner implements ApplicationRunner {

    private static final String PUBLIC_PREFIX = "/uploads/";
    private static final String DM_PREFIX = "/uploads/dm/";
    private static final String DM_DIR = "dm";
    private static final String LOCK_KEY = "moyuyo:dm:migrate-lock";
    private static final List<String> MEDIA_TYPES = List.of("IMAGE", "VOICE", "FILE");

    private final DmMessageMapper dmMessageMapper;
    private final StringRedisTemplate redisTemplate;

    @Value("${moyuyo.upload.dir:/tmp/moyuyo-uploads}")
    private String uploadDir;

    @Value("${moyuyo.upload.migrate-batch-size:200}")
    private int batchSize;

    @Value("${moyuyo.upload.migrate-max-total:20000}")
    private int maxTotal;

    @Override
    public void run(ApplicationArguments args) {
        // 异步执行，避免大批量文件移动拖慢应用启动
        Thread worker = new Thread(this::migrateWithLock, "dm-media-migration");
        worker.setDaemon(true);
        worker.start();
    }

    /** 多实例互斥：拿不到锁说明其他实例正在迁移，直接跳过（幂等，下次启动会继续） */
    private void migrateWithLock() {
        Boolean locked;
        try {
            locked = redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, "1", Duration.ofMinutes(30));
        } catch (Exception e) {
            log.warn("[dm-migrate] Redis 不可用，跳过迁移以避免多实例重复搬移", e);
            return;
        }
        if (!Boolean.TRUE.equals(locked)) {
            log.info("[dm-migrate] 其他实例正在迁移，跳过");
            return;
        }
        try {
            migrate();
        } catch (Exception e) {
            log.error("[dm-migrate] 迁移异常", e);
        } finally {
            try {
                redisTemplate.delete(LOCK_KEY);
            } catch (Exception ignored) {
                // 解锁失败仅会导致锁在 TTL 到期后自动释放，不影响数据
            }
        }
    }

    private void migrate() {
        Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
        int scanned = 0;
        int moved = 0;
        int skipped = 0;
        while (scanned < maxTotal) {
            List<DmMessageEntity> rows = dmMessageMapper.selectList(
                    new LambdaQueryWrapper<DmMessageEntity>()
                            .select(DmMessageEntity::getId, DmMessageEntity::getContent)
                            .in(DmMessageEntity::getContentType, MEDIA_TYPES)
                            .likeRight(DmMessageEntity::getContent, PUBLIC_PREFIX)
                            // 必须显式排除已迁移记录：/uploads/dm/ 也以 /uploads/ 开头，
                            // 不排除会导致每批都扫到已迁移数据，批量满后提前结束、漏迁剩余记录
                            .notLikeRight(DmMessageEntity::getContent, DM_PREFIX)
                            .orderByAsc(DmMessageEntity::getId)
                            .last("LIMIT " + Math.max(1, batchSize)));
            if (rows.isEmpty()) {
                break;
            }
            boolean progressed = false;
            for (DmMessageEntity row : rows) {
                scanned++;
                String content = row.getContent();
                if (content == null) {
                    continue;
                }
                // 历史 content 可能带签名/查询参数，按纯路径判断
                String path = stripQuery(content.trim());
                if (path.startsWith(DM_PREFIX) || !path.startsWith(PUBLIC_PREFIX)) {
                    continue;
                }
                String relative = path.substring(PUBLIC_PREFIX.length());
                Path source = base.resolve(relative).normalize();
                Path target = base.resolve(DM_DIR).resolve(relative).normalize();
                // 防路径穿越：历史 content 若含 ../ 必须拒绝，否则可能搬移上传目录外的系统文件
                if (!source.startsWith(base) || !target.startsWith(base)) {
                    log.warn("[dm-migrate] 拒绝越界路径 id={} path={}", row.getId(), path);
                    skipped++;
                    continue;
                }
                try {
                    if (Files.isRegularFile(source)) {
                        Files.createDirectories(target.getParent());
                        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                    } else if (Files.isRegularFile(target)) {
                        // 同一文件可能被多条消息引用，文件已被前一条迁移，这里只补 DB
                        log.debug("[dm-migrate] 文件已迁移，仅更新记录 id={}", row.getId());
                    } else {
                        // 源文件与目标都不存在：保留原记录，避免指向不存在的 dm 路径
                        skipped++;
                        continue;
                    }
                    dmMessageMapper.update(null,
                            new LambdaUpdateWrapper<DmMessageEntity>()
                                    .eq(DmMessageEntity::getId, row.getId())
                                    .set(DmMessageEntity::getContent, DM_PREFIX + relative));
                    moved++;
                    progressed = true;
                } catch (Exception e) {
                    log.warn("[dm-migrate] 迁移失败 id={} path={}", row.getId(), path, e);
                    skipped++;
                }
            }
            // 整批都没有进展（全部失败/文件缺失）时停止，避免同一批数据无限循环
            if (!progressed) {
                break;
            }
        }
        log.info("[dm-migrate] 完成：扫描={}, 迁移={}, 跳过={}, 目录={}", scanned, moved, skipped, base);
    }

    /** 去掉查询参数，签名 URL 与历史带参 URL 都按纯路径处理 */
    private String stripQuery(String url) {
        int index = url.indexOf('?');
        return index >= 0 ? url.substring(0, index) : url;
    }
}
