package com.jzo2o.ai.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.jzo2o.ai.client.AiEngineClient;
import com.jzo2o.ai.client.AiEngineWebSocketClient;
import com.jzo2o.ai.properties.AiEngineProperties;
import com.jzo2o.ai.constants.AiConstants;
import com.jzo2o.ai.mapper.AiChatRecordMapper;
import com.jzo2o.ai.model.domain.AiChatRecord;
import com.jzo2o.ai.model.dto.request.ChatRequestDTO;
import com.jzo2o.ai.service.ChatService;
import com.jzo2o.common.model.CurrentUserInfo;
import com.jzo2o.common.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

/**
 * 聊天服务实现 — 鉴权、持久化、SSE代理
 * Python引擎返回原始 LLM token 流, 由本层直接包装为 SSE 发给前端
 *
 * <p>持久化策略:
 * <ul>
 *   <li>user 消息: 同步落盘 (status=1)</li>
 *   <li>assistant 消息: 通过 {@link AsyncFlusher} 异步增量落盘
 *     <ul>
 *       <li>创建时预 INSERT status=0 空记录</li>
 *       <li>每 500ms 增量 UPDATE 当前内容</li>
 *       <li>结束时 finalize(status), 幂等保证只落盘一次</li>
 *     </ul>
 *   </li>
 * </ul>
 */
@Slf4j
@Service
public class ChatServiceImpl implements ChatService {

    @Resource
    private AiEngineClient aiEngineClient;

    @Resource
    private AiEngineWebSocketClient aiEngineWebSocketClient;

    @Resource
    private AiEngineProperties aiEngineProperties;

    @Resource
    private AiChatRecordMapper aiChatRecordMapper;

    @Resource(name = "asyncFlushScheduler")
    private ScheduledExecutorService asyncFlushScheduler;

    @Override
    public SseEmitter chat(ChatRequestDTO request) {
        // 1. 从 ThreadLocal 获取当前用户
        CurrentUserInfo user = UserContext.currentUser();
        Long userId = user.getId();
        Integer userType = user.getUserType();

        // 2. 生成或复用会话ID
        String sessionId = StrUtil.isNotBlank(request.getSessionId())
                ? request.getSessionId()
                : IdUtil.simpleUUID();

        // 3. 提取用户最后一条消息
        String userContent = extractLastUserMessage(request.getMessages());

        // 4. 同步持久化用户消息 (status=1)
        saveRecord(userId, userType, sessionId, AiConstants.ROLE_USER, userContent);

        // 5. 转换为 Python 引擎的消息格式
        List<Map<String, String>> messages = convertMessages(request.getMessages());

        // 6. 创建 SseEmitter (5分钟超时)
        SseEmitter emitter = new SseEmitter(AiConstants.SSE_TIMEOUT);

        // 7. 根据传输模式选择 HTTP 或 WebSocket
        if ("ws".equals(aiEngineProperties.getMode())) {
            chatViaWebSocket(sessionId, messages, emitter, userId, userType);
        } else {
            chatViaHttp(sessionId, messages, emitter, userId, userType);
        }

        return emitter;
    }

    // ==================== WebSocket 模式 ====================

    private void chatViaWebSocket(String sessionId, List<Map<String, String>> messages,
                                  SseEmitter emitter, Long userId, Integer userType) {
        StringBuffer responseBuffer = new StringBuffer();

        // 创建异步落盘器: 立即预插入 status=0 记录, 启动 500ms 定时刷写 + 内容停滞检测
        AsyncFlusher flusher = new AsyncFlusher(
                userId, userType, sessionId,
                responseBuffer::toString,
                aiChatRecordMapper,
                asyncFlushScheduler,
                // onStaleCallback: false=首次停滞(发cancel给Python), true=强制终结(清理WebSocket)
                forceCleanup -> {
                    if (!forceCleanup) {
                        log.warn("内容停滞, 发送取消指令给引擎, sessionId={}", sessionId);
                        aiEngineWebSocketClient.sendCancelToEngine(sessionId);
                    } else {
                        log.warn("取消后无响应, 强制清理WebSocket, sessionId={}", sessionId);
                        aiEngineWebSocketClient.cancelSession(sessionId);
                    }
                });

        aiEngineWebSocketClient.connectAndStream(sessionId, messages, emitter,
                // tokenAccumulator: 每个 token 追加到 buffer (Flusher 自动读取)
                token -> responseBuffer.append(token),
                // onComplete: agent_finish → status=1
                () -> {
                    flusher.finalize(AiConstants.STATUS_COMPLETE);
                    log.info("聊天会话完成(WS), sessionId={}", sessionId);
                },
                // onCancel: 用户取消 → status=2, 不拼接提示
                reason -> {
                    log.info("用户取消会话(WS), sessionId={}, reason={}", sessionId, reason);
                    flusher.finalize(AiConstants.STATUS_CANCELLED);
                },
                // onError: 异常中断 → status=3, 拼接后缀
                error -> {
                    log.error("会话异常中断(WS), sessionId={}, error={}", sessionId, error.getMessage());
                    flusher.finalize(AiConstants.STATUS_INTERRUPTED);
                },
                userId, userType);
    }

    // ==================== HTTP 模式 ====================

    private void chatViaHttp(String sessionId, List<Map<String, String>> messages,
                             SseEmitter emitter, Long userId, Integer userType) {
        StringBuffer responseBuffer = new StringBuffer();

        // HTTP 模式同样接入 AsyncFlusher (HTTP 模式无停滞回调, 由 HTTP 超时自行处理)
        AsyncFlusher flusher = new AsyncFlusher(
                userId, userType, sessionId,
                responseBuffer::toString,
                aiChatRecordMapper,
                asyncFlushScheduler,
                null);

        aiEngineClient.streamChat(messages)
                .subscribe(
                        rawChunk -> {
                            if (rawChunk.startsWith("[ERROR]")) {
                                log.error("Python引擎返回错误: {}", rawChunk);
                                return;
                            }
                            try {
                                emitter.send(SseEmitter.event().data(rawChunk));
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            // HTTP 行解码器剥掉了 \n，此处补回以还原原始文档结构
                            responseBuffer.append(rawChunk).append("\n");
                        },
                        error -> {
                            log.error("聊天流式传输异常: {}", error.getMessage());
                            flusher.finalize(AiConstants.STATUS_INTERRUPTED);
                            emitter.completeWithError(error);
                        },
                        () -> {
                            flusher.finalize(AiConstants.STATUS_COMPLETE);
                            emitter.complete();
                            log.info("聊天会话完成(HTTP), sessionId: {}", sessionId);
                        }
                );
    }

    /**
     * 提取最后一条用户消息
     */
    private String extractLastUserMessage(List<ChatRequestDTO.ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatRequestDTO.ChatMessage msg = messages.get(i);
            if (AiConstants.ROLE_USER.equals(msg.getRole())) {
                return msg.getContent();
            }
        }
        return "";
    }

    /**
     * DTO 消息列表转为 Map 列表，传给 Python 引擎
     */
    private List<Map<String, String>> convertMessages(List<ChatRequestDTO.ChatMessage> messages) {
        if (messages == null) {
            return List.of();
        }
        return messages.stream()
                .map(m -> Map.of("role", m.getRole(), "content", m.getContent()))
                .collect(Collectors.toList());
    }

    @Override
    public void cancel(String sessionId) {
        log.info("前端请求取消会话, sessionId={}", sessionId);
        aiEngineWebSocketClient.cancelSession(sessionId);
    }

    /**
     * 保存聊天记录到数据库
     */
    @Override
    public List<Map<String, Object>> listSessions() {
        CurrentUserInfo user = UserContext.currentUser();
        List<AiChatRecord> records = aiChatRecordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiChatRecord>()
                        .eq(AiChatRecord::getUserId, user.getId())
                        .eq(AiChatRecord::getRole, "user")
                        .orderByDesc(AiChatRecord::getCreateTime)
        );
        Map<String, Map<String, Object>> sessionMap = new java.util.LinkedHashMap<>();
        for (AiChatRecord r : records) {
            if (!sessionMap.containsKey(r.getSessionId())) {
                Map<String, Object> item = new java.util.HashMap<>();
                item.put("sessionId", r.getSessionId());
                item.put("preview", r.getContent().length() > 50
                        ? r.getContent().substring(0, 50) + "..." : r.getContent());
                item.put("lastTime", r.getCreateTime().toString());
                sessionMap.put(r.getSessionId(), item);
            }
        }
        return new ArrayList<>(sessionMap.values());
    }

    @Override
    public List<Map<String, Object>> getSessionMessages(String sessionId) {
        CurrentUserInfo user = UserContext.currentUser();
        List<AiChatRecord> records = aiChatRecordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiChatRecord>()
                        .eq(AiChatRecord::getUserId, user.getId())
                        .eq(AiChatRecord::getSessionId, sessionId)
                        .orderByAsc(AiChatRecord::getCreateTime)
        );
        List<Map<String, Object>> result = new ArrayList<>();
        for (AiChatRecord r : records) {
            Map<String, Object> item = new java.util.HashMap<>();
            item.put("role", r.getRole());
            item.put("content", r.getContent());
            item.put("status", r.getStatus());
            item.put("createTime", r.getCreateTime().toString());
            result.add(item);
        }
        return result;
    }

    private void saveRecord(Long userId, Integer userType, String sessionId, String role, String content) {
        AiChatRecord record = new AiChatRecord();
        record.setUserId(userId);
        record.setUserType(userType);
        record.setSessionId(sessionId);
        record.setRole(role);
        record.setContent(content);
        record.setStatus(AiConstants.STATUS_COMPLETE);
        record.setCreateTime(LocalDateTime.now());
        aiChatRecordMapper.insert(record);
    }
}
