package com.jzo2o.ai.controller.inner;

import com.jzo2o.ai.service.EvaluationSummaryService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 内部接口 — AI 能力调用
 */
@Slf4j
@RestController("innerAiController")
@RequestMapping("/inner/ai")
@Api(tags = "内部接口 - AI 能力调用")
public class InnerAiController {

    @Resource
    private EvaluationSummaryService evaluationSummaryService;

    @PostMapping("/evaluation/summarize")
    @ApiOperation("触发 AI 评价总结 (增量)")
    public Map<String, String> summarizeEvaluation(@RequestParam("targetTypeId") Integer targetTypeId,
                                                    @RequestParam("targetId") Long targetId) {
        try {
            String summary = evaluationSummaryService.summarize(targetTypeId, targetId);
            return Map.of("summary", summary != null ? summary : "",
                           "status", "SUCCESS");
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("PROCESSING:")) {
                return Map.of("summary", "",
                               "status", "PROCESSING",
                               "msg", e.getMessage().substring("PROCESSING:".length()));
            }
            // AI 引擎不可用等异常 → 返回明确错误状态而非 500
            log.error("AI 评价总结失败: targetTypeId={}, targetId={}", targetTypeId, targetId, e);
            return Map.of("summary", "",
                           "status", "ERROR",
                           "msg", "AI总结服务暂不可用，请稍后重试");
        }
    }

    @PostMapping("/evaluation/summarize/full")
    @ApiOperation("触发 AI 评价总结 (全量, 忽略历史游标)")
    public Map<String, String> summarizeEvaluationFull(@RequestParam("targetTypeId") Integer targetTypeId,
                                                        @RequestParam("targetId") Long targetId) {
        try {
            String summary = evaluationSummaryService.summarizeFull(targetTypeId, targetId);
            return Map.of("summary", summary != null ? summary : "",
                           "status", "SUCCESS");
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("PROCESSING:")) {
                return Map.of("summary", "",
                               "status", "PROCESSING",
                               "msg", e.getMessage().substring("PROCESSING:".length()));
            }
            // AI 引擎不可用等异常 → 返回明确错误状态而非 500
            log.error("AI 全量评价总结失败: targetTypeId={}, targetId={}", targetTypeId, targetId, e);
            return Map.of("summary", "",
                           "status", "ERROR",
                           "msg", "AI总结服务暂不可用，请稍后重试");
        }
    }

    @GetMapping("/evaluation/summarize")
    @ApiOperation("查询已有 AI 评价总结")
    public Map<String, String> getEvaluationSummary(@RequestParam("targetTypeId") Integer targetTypeId,
                                                     @RequestParam("targetId") Long targetId) {
        String summary = evaluationSummaryService.getSummary(targetTypeId, targetId);
        if (summary == null || summary.isEmpty()) {
            return Map.of("summary", "",
                           "status", "EMPTY");
        }
        return Map.of("summary", summary, "status", "SUCCESS");
    }
}
