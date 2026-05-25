package com.jzo2o.ai.constants;

/**
 * AI模块常量
 */
public class AiConstants {

    /** 用户角色 */
    public static final String ROLE_USER = "user";
    /** 助手角色 */
    public static final String ROLE_ASSISTANT = "assistant";
    /** 系统角色 */
    public static final String ROLE_SYSTEM = "system";

    /** SseEmitter 超时 (毫秒) — 5分钟 */
    public static final Long SSE_TIMEOUT = 300000L;

    // ========== 消息状态 ==========

    /** 生成中 */
    public static final int STATUS_GENERATING = 0;
    /** 正常完成 */
    public static final int STATUS_COMPLETE = 1;
    /** 用户取消 */
    public static final int STATUS_CANCELLED = 2;
    /** 异常中断 */
    public static final int STATUS_INTERRUPTED = 3;

    /** 异常中断时的后缀提示文案 */
    public static final String INTERRUPTED_SUFFIX = "\n\n回复未完成，输入继续以继续";

    // ========== WebSocket 心跳 & 停滞检测 ==========

    /** WebSocket 心跳 Ping 间隔 (秒) */
    public static final int PING_INTERVAL_SECONDS = 10;
    /** WebSocket Pong 超时 (秒): 超过此时长未收到任何帧视为连接断开 */
    public static final int PONG_TIMEOUT_SECONDS = 15;
    /** 内容停滞检测间隔 (秒) */
    public static final int STALE_CHECK_INTERVAL_SECONDS = 5;
    /** 内容停滞阈值 (秒): 超过此时长内容无变化视为推理卡死 */
    public static final int STALE_CONTENT_THRESHOLD_SECONDS = 30;
    /** 发送取消指令后等待响应的时间 (秒), 超时强制中断 */
    public static final int STALE_CANCEL_WAIT_SECONDS = 10;
}
