package com.jzo2o.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.jzo2o.ai.constants.AiConstants;
import com.jzo2o.ai.mapper.AiChatRecordMapper;
import com.jzo2o.ai.model.domain.AiChatRecord;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * AI 回复异步定时落盘器 — 每个 chat() 调用创建一个实例。
 *
 * <p>生命周期:
 * <ol>
 *   <li>创建时: 预 INSERT 一条 status=0 的空记录, 拿到主键 ID</li>
 *   <li>运行中: 每 500ms 将当前 buffer 内容 UPDATE 到该记录</li>
 *   <li>结束时: {@link #finalize(int)} 停止定时任务, 执行最终 UPDATE (设置最终 status)</li>
 *   <li>finalize() 只执行一次 ({@link AtomicBoolean} 保证幂等)</li>
 * </ol>
 *
 * <p>线程安全: 定时任务和 {@code finalize()} 通过 {@link AtomicBoolean} 互斥,
 * 最终落盘保证只发生一次。多次调用 {@code finalize()} 只有第一次生效。
 */
@Slf4j
class AsyncFlusher {

    /** 预插入记录的主键 */
    private final Long recordId;
    /** 获取当前 buffer 内容 */
    private final Supplier<String> contentSupplier;
    private final AiChatRecordMapper mapper;
    private final ScheduledExecutorService scheduler;
    private final String sessionId;

    private volatile ScheduledFuture<?> flushTask;
    private final AtomicBoolean finalized = new AtomicBoolean(false);

    /** 刷新间隔 (毫秒) */
    private static final long FLUSH_INTERVAL_MS = 500;

    /**
     * 构造: 立即预插入一条 status=0 的空 assistant 记录, 并启动 500ms 定时刷写。
     */
    AsyncFlusher(Long userId, Integer userType, String sessionId,
                 Supplier<String> contentSupplier,
                 AiChatRecordMapper mapper,
                 ScheduledExecutorService scheduler) {
        this.contentSupplier = contentSupplier;
        this.mapper = mapper;
        this.scheduler = scheduler;
        this.sessionId = sessionId;

        // 预插入: status=0 (生成中), content 为空
        // createTime 在此设置后不再更新, 保证 getSessionMessages 排序稳定
        AiChatRecord record = new AiChatRecord();
        record.setUserId(userId);
        record.setUserType(userType);
        record.setSessionId(sessionId);
        record.setRole(AiConstants.ROLE_ASSISTANT);
        record.setContent("");
        record.setStatus(AiConstants.STATUS_GENERATING);
        record.setCreateTime(LocalDateTime.now());
        mapper.insert(record);
        this.recordId = record.getId();

        // 启动定时刷写
        this.flushTask = scheduler.scheduleWithFixedDelay(
                this::flush, FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);

        log.debug("AsyncFlusher 创建, sessionId={}, recordId={}", sessionId, recordId);
    }

    /**
     * 定时刷写: 将当前 buffer 内容写入数据库。
     * 已 finalize 则跳过, 避免与 final 更新冲突。
     * 注意: 不更新 createTime, 保证按创建时间排序的查询结果稳定。
     */
    private void flush() {
        if (finalized.get()) {
            return;
        }
        try {
            String currentContent = contentSupplier.get();
            // 仅在内容非空时更新 (避免空 update 的无效 IO)
            if (StrUtil.isNotBlank(currentContent)) {
                AiChatRecord record = new AiChatRecord();
                record.setId(recordId);
                record.setContent(currentContent);
                mapper.updateById(record);
            }
        } catch (Exception e) {
            log.error("AsyncFlusher 定时刷写失败, sessionId={}, recordId={}", sessionId, recordId, e);
        }
    }

    /**
     * 最终确认 — 停止定时任务, 写入最终状态。
     * 幂等: 多次调用只执行第一次。
     *
     * @param finalStatus 最终状态: 1=完成, 2=用户取消, 3=异常中断
     */
    void finalize(int finalStatus) {
        if (!finalized.compareAndSet(false, true)) {
            // 已被其他路径 finalize, 幂等返回
            return;
        }

        // 1. 取消定时任务
        if (flushTask != null && !flushTask.isDone()) {
            flushTask.cancel(false);
        }

        try {
            String content = contentSupplier.get();

            // 2. status=3 时拼接后缀提示
            if (finalStatus == AiConstants.STATUS_INTERRUPTED && StrUtil.isNotBlank(content)) {
                content = content + AiConstants.INTERRUPTED_SUFFIX;
            }

            // 3. 如果内容为空 (用户在第一个 token 到达前就取消), 删除空记录
            if (StrUtil.isBlank(content)) {
                mapper.deleteById(recordId);
                log.debug("AsyncFlusher 最终: 空记录已删除, sessionId={}, recordId={}", sessionId, recordId);
                return;
            }

            // 4. 最终 UPDATE
            AiChatRecord record = new AiChatRecord();
            record.setId(recordId);
            record.setContent(content);
            record.setStatus(finalStatus);
            mapper.updateById(record);

            log.info("AsyncFlusher 最终落盘, sessionId={}, recordId={}, status={}, contentLen={}",
                    sessionId, recordId, finalStatus, content.length());
        } catch (Exception e) {
            log.error("AsyncFlusher finalize 失败, sessionId={}, recordId={}", sessionId, recordId, e);
        }
    }
}
