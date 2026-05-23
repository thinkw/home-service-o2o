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
}
