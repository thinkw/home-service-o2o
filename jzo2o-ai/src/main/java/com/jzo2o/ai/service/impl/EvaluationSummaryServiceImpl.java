package com.jzo2o.ai.service.impl;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jzo2o.ai.client.AiEngineWebSocketClient;
import com.jzo2o.ai.mapper.EvaluationSummaryMapper;
import com.jzo2o.ai.model.domain.EvaluationSummary;
import com.jzo2o.ai.service.EvaluationSummaryService;
import com.jzo2o.api.customer.EvaluationApi;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * AI 评价总结服务实现 — 增量模式
 */
@Slf4j
@Service
public class EvaluationSummaryServiceImpl implements EvaluationSummaryService {

    @Resource
    private AiEngineWebSocketClient wsClient;

    @Resource
    private EvaluationSummaryMapper summaryMapper;

    @Resource
    private EvaluationApi evaluationApi;

    @Resource
    private RedissonClient redissonClient;

    private static final String LOCK_KEY_PREFIX = "eval:summary:lock:";

    @Override
    public String getSummary(Integer targetTypeId, Long targetId) {
        EvaluationSummary summary = summaryMapper.selectOne(
                new LambdaQueryWrapper<EvaluationSummary>()
                        .eq(EvaluationSummary::getTargetTypeId, targetTypeId)
                        .eq(EvaluationSummary::getTargetId, targetId));
        return summary != null ? summary.getSummaryContent() : null;
    }

    @Override
    public String summarize(Integer targetTypeId, Long targetId) {
        String lockKey = LOCK_KEY_PREFIX + targetTypeId + ":" + targetId;
        RLock lock = redissonClient.getLock(lockKey);

        boolean locked = false;
        try {
            locked = lock.tryLock(0, 180, TimeUnit.SECONDS);
            if (!locked) {
                log.info("分布式锁被占用, 拒绝重复生成: targetTypeId={}, targetId={}", targetTypeId, targetId);
                throw new RuntimeException("PROCESSING:该目标的评价总结正在生成中, 请稍后点击【查看总结】");
            }
            return doSummarizeInternal(targetTypeId, targetId, false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("获取分布式锁被中断", e);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public String summarizeFull(Integer targetTypeId, Long targetId) {
        String lockKey = LOCK_KEY_PREFIX + targetTypeId + ":" + targetId;
        RLock lock = redissonClient.getLock(lockKey);

        boolean locked = false;
        try {
            locked = lock.tryLock(0, 180, TimeUnit.SECONDS);
            if (!locked) {
                log.info("分布式锁被占用, 拒绝重复生成(全量): targetTypeId={}, targetId={}", targetTypeId, targetId);
                throw new RuntimeException("PROCESSING:该目标的评价总结正在生成中, 请稍后点击【查看总结】");
            }
            return doSummarizeInternal(targetTypeId, targetId, true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("获取分布式锁被中断", e);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 总结内部逻辑
     *
     * @param full true=全量总结(忽略游标/旧总结), false=增量总结(只用游标之后的新评价)
     */
    private String doSummarizeInternal(Integer targetTypeId, Long targetId, boolean full) {
        // 1. 查询上次总结
        EvaluationSummary prev = summaryMapper.selectOne(
                new LambdaQueryWrapper<EvaluationSummary>()
                        .eq(EvaluationSummary::getTargetTypeId, targetTypeId)
                        .eq(EvaluationSummary::getTargetId, targetId));

        // 全量模式: 忽略游标和旧总结; 增量模式: 使用游标
        LocalDateTime afterTime;
        String prevSummary;
        if (full) {
            afterTime = null;
            prevSummary = "";
            log.info("全量总结: targetTypeId={}, targetId={}", targetTypeId, targetId);
        } else if (prev != null && prev.getLastEvaluationTime() != null) {
            afterTime = prev.getLastEvaluationTime();
            prevSummary = prev.getSummaryContent();
            log.info("增量总结: targetTypeId={}, targetId={}, 上次总结时间={}", targetTypeId, targetId, afterTime);
        } else {
            afterTime = null;
            prevSummary = "";
            log.info("首次总结: targetTypeId={}, targetId={}", targetTypeId, targetId);
        }
        // 2. 检查是否有新评价
        log.info("查询新评价: targetTypeId={}, targetId={}, afterTime={}", targetTypeId, targetId, afterTime);
        String newEvaluationsJson;
        try {
            newEvaluationsJson = evaluationApi.queryByTargetIdAndTime(
                    targetTypeId, targetId,
                    afterTime != null ? afterTime.toString() : null);
        } catch (Exception e) {
            log.error("查询评价失败 (Feign调用异常): targetTypeId={}, targetId={}", targetTypeId, targetId, e);
            throw new RuntimeException("查询评价数据失败: " + e.getMessage(), e);
        }
        log.info("评价查询返回: length={}, preview={}",
                newEvaluationsJson != null ? newEvaluationsJson.length() : 0,
                newEvaluationsJson != null ? newEvaluationsJson.substring(0, Math.min(500, newEvaluationsJson.length())) : "null");

        @SuppressWarnings({"unchecked", "rawtypes"})
        List<Map<String, Object>> newEvals = (List) JSONUtil.toList(newEvaluationsJson, Map.class);
        if (newEvals == null || newEvals.isEmpty()) {
            // 有新评价则走增量总结, 无新评价则返回旧总结(若存在)
            if (!prevSummary.isEmpty()) {
                log.info("无新评价, 返回旧总结: targetTypeId={}, targetId={}", targetTypeId, targetId);
                return prevSummary;
            }
            log.info("无新评价且无旧总结, 跳过: targetTypeId={}, targetId={}", targetTypeId, targetId);
            throw new RuntimeException("该目标暂无新评价数据");
        }
        log.info("发现 {} 条新评价: targetTypeId={}, targetId={}", newEvals.size(), targetTypeId, targetId);

    // 3. 构建 prompt
        String targetLabel = targetTypeId == 7 ? "服务人员" : "服务项";
        String prompt;
        if (!prevSummary.isEmpty()) {
            prompt = String.format(
                    "你是家政平台的评价分析助手。请根据以下信息为%s(ID=%d)生成综合评价总结。\n" +
                    "## 历史评价总结\n%s\n\n## 新增评价 (共%d条)\n%s\n\n" +
                    "请将历史总结与新增评价融合，用一句话概括该%s的整体评价（80字以内，口语化，不要任何格式标记）。如果新旧评价有矛盾，应体现变化趋势。仅包括评价总结内容，不要给出额外输出",
                    targetLabel, targetId, prevSummary, newEvals.size(), newEvaluationsJson, targetLabel);
        } else {
            prompt = String.format(
                    "你是家政平台的评价分析助手。请根据以下评价内容为%s(ID=%d)生成评价总结。\n" +
                    "## 评价列表 (共%d条)\n%s\n\n" +
                    "请用一句话概括这些评价的核心观点（50字以内，口语化，不要任何格式标记）。仅包括评价总结内容，不要给出额外输出",
                    targetLabel, targetId, newEvals.size(), newEvaluationsJson);
        }

        // 4. 通过 WebSocket 调用 AI
        String sessionId = "eval-summary-" + UUID.randomUUID().toString().substring(0, 8);
        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", prompt));
        log.info("发送 AI 请求: sessionId={}, prompt 长度={}, prompt 末尾(300字符)={}",
                sessionId, prompt.length(),
                prompt.substring(Math.max(0, prompt.length() - 300)));
        try {
            CompletableFuture<String> future = wsClient.connectAndCollect(sessionId, messages);
            String summary = future.get(180, TimeUnit.SECONDS);
            log.info("AI 总结完成: targetTypeId={}, targetId={}, 长度={}", targetTypeId, targetId,
                    summary != null ? summary.length() : 0);

            // 5. 保存/更新总结
            LocalDateTime now = LocalDateTime.now();
            // 取最后一条评价的时间作为游标 (JSON 中可能是 Long 时间戳或 String)
            Map<String, Object> lastEval = newEvals.get(newEvals.size() - 1);
            Object lastTimeObj = lastEval.get("createTime");
            LocalDateTime lastTime;
            if (lastTimeObj instanceof String) {
                lastTime = LocalDateTimeUtil.parse((String) lastTimeObj);
            } else if (lastTimeObj instanceof Number) {
                long ts = ((Number) lastTimeObj).longValue();
                // JSON 序列化可能是秒级或毫秒级时间戳, 以 10^10 为界区分
                lastTime = LocalDateTimeUtil.of(ts < 10_000_000_000L ? ts * 1000 : ts);
            } else {
                lastTime = now;
            }
            Object lastIdObj = lastEval.get("id");
            Long lastId;
            if (lastIdObj instanceof Number) {
                lastId = ((Number) lastIdObj).longValue();
            } else {
                lastId = Long.valueOf(String.valueOf(lastIdObj));
            }

            if (prev == null) {
                prev = new EvaluationSummary();
                prev.setTargetTypeId(targetTypeId);
                prev.setTargetId(targetId);
                prev.setSummaryContent(summary);
                prev.setLastEvaluationId(lastId);
                prev.setLastEvaluationTime(lastTime);
                summaryMapper.insert(prev);
            } else {
                prev.setSummaryContent(summary);
                prev.setLastEvaluationId(lastId);
                prev.setLastEvaluationTime(lastTime);
                summaryMapper.updateById(prev);
            }

            return summary;
        } catch (Exception e) {
            log.error("AI 总结失败: targetTypeId={}, targetId={}", targetTypeId, targetId, e);
            throw new RuntimeException("AI 评价总结失败: " + e.getMessage(), e);
        }
    }
}
