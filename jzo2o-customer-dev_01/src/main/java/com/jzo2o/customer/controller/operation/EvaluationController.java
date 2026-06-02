package com.jzo2o.customer.controller.operation;

import com.jzo2o.api.AiApi;
import com.jzo2o.customer.model.dto.request.EvaluationPageByTargetReqDTO;
import com.jzo2o.customer.model.dto.response.EvaluationResDTO;
import com.jzo2o.customer.service.EvaluationService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 运营端 - 评价相关接口
 *
 * @author itcast
 **/
@Slf4j
@RestController("operationEvaluationController")
@RequestMapping("/operation/evaluation")
@Api(tags = "运营端 - 评价相关接口")
public class EvaluationController {
    @Resource
    private EvaluationService evaluationService;

    @Resource
    private AiApi aiApi;

    @GetMapping("/pageByTarget")
    @ApiOperation("运营端分页查询评价列表")
    public Map<String, Object> pageByTarget(EvaluationPageByTargetReqDTO evaluationPageByTargetReqDTO) {
        return evaluationService.pageByTarget(evaluationPageByTargetReqDTO);
    }

    @GetMapping("/{id}")
    @ApiOperation("查询评价详情")
    public EvaluationResDTO getById(@PathVariable("id") Long id) {
        return evaluationService.getById(id);
    }

    @DeleteMapping("/{id}")
    @ApiOperation("删除评价")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "评价id", required = true, dataTypeClass = Long.class)
    })
    public void delete(@PathVariable("id") Long id) {
        evaluationService.delete(id);
    }

    @PostMapping("/summarize")
    @ApiOperation("手动触发 AI 评价总结 (增量)")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "targetTypeId", value = "评价目标类型 (7=服务人员)", required = true, dataTypeClass = Integer.class),
            @ApiImplicitParam(name = "targetId", value = "目标ID", required = true, dataTypeClass = Long.class)
    })
    public Map<String, String> summarize(@RequestParam("targetTypeId") Integer targetTypeId,
                                         @RequestParam("targetId") Long targetId) {
        try {
            return aiApi.summarizeEvaluation(targetTypeId, targetId);
        } catch (Exception e) {
            log.error("调用 AI 增量总结服务失败: targetTypeId={}, targetId={}", targetTypeId, targetId, e);
            return Map.of("summary", "",
                           "status", "ERROR",
                           "msg", "AI总结服务暂不可用，请稍后重试");
        }
    }

    @PostMapping("/summarize/full")
    @ApiOperation("手动触发 AI 评价总结 (全量, 忽略历史游标)")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "targetTypeId", value = "评价目标类型 (7=服务人员)", required = true, dataTypeClass = Integer.class),
            @ApiImplicitParam(name = "targetId", value = "目标ID", required = true, dataTypeClass = Long.class)
    })
    public Map<String, String> summarizeFull(@RequestParam("targetTypeId") Integer targetTypeId,
                                              @RequestParam("targetId") Long targetId) {
        try {
            return aiApi.summarizeEvaluationFull(targetTypeId, targetId);
        } catch (Exception e) {
            log.error("调用 AI 全量总结服务失败: targetTypeId={}, targetId={}", targetTypeId, targetId, e);
            return Map.of("summary", "",
                           "status", "ERROR",
                           "msg", "AI总结服务暂不可用，请稍后重试");
        }
    }
}
