package com.moyuyo.api.ws;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 私信 WebSocket 推送专用线程池。
 * <p>
 * 背景：{@link DmSessionRegistry#sendToUser} 由事务提交回调（AFTER_COMMIT）在 HTTP 业务线程上调用，
 * 若直接在调用线程里 {@code sendMessage}，慢客户端 / TCP 背压会把 Tomcat 工作线程拖住，
 * 并发一高就会耗尽线程池。因此推送统一投递到本线程池异步执行。
 * <p>
 * 队列有界（5000）+ 满则丢弃：私信有 REST 拉取兜底，宁可丢一次实时推送，
 * 也不能反向阻塞业务线程或造成 OOM。
 */
@Slf4j
@Component
public class DmPushExecutor {

    /** 核心线程数：推送是轻量 IO 任务，少量常驻线程即可 */
    private static final int CORE_POOL_SIZE = 4;
    private static final int MAX_POOL_SIZE = 16;
    /** 有界队列容量：按"突发群聊广播"场景估算，超出即丢弃并告警 */
    private static final int QUEUE_CAPACITY = 5000;

    private final ThreadPoolExecutor executor;

    public DmPushExecutor() {
        AtomicInteger seq = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                MAX_POOL_SIZE,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(QUEUE_CAPACITY),
                r -> {
                    Thread thread = new Thread(r, "dm-push-" + seq.incrementAndGet());
                    // 守护线程：不阻塞 JVM 退出
                    thread.setDaemon(true);
                    return thread;
                },
                // 拒绝策略：丢弃任务并打日志，绝不使用 CallerRuns（那会把压力还回业务线程）
                (r, pool) -> log.warn("[dm-ws] 推送队列已满（capacity={}），本次实时推送丢弃，由客户端拉取兜底",
                        QUEUE_CAPACITY));
    }

    /** 提交推送任务；队列满时按拒绝策略丢弃 */
    public void execute(Runnable task) {
        if (task == null) return;
        executor.execute(task);
    }

    /** 容器关闭时优雅停止，避免残留线程 */
    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
