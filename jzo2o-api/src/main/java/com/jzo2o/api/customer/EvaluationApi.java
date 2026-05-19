package com.jzo2o.api.customer;

import com.jzo2o.api.customer.dto.request.EvaluationSubmitReqDTO;
import com.jzo2o.api.customer.dto.response.EvaluationScoreResDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * @author itcast
 */
@FeignClient(contextId = "jzo2o-customer", value = "jzo2o-customer", path = "/customer/inner/evaluation")
public interface EvaluationApi {

    /**
     * 根据订单id列表查询师傅评分
     *
     * @param orderIds 订单id列表
     * @return 评分
     */
    @GetMapping("/queryServeProviderScoreByOrdersId")
    EvaluationScoreResDTO queryServeProviderScoreByOrdersId(@RequestParam("orderIds") List<Long> orderIds);

    /**
     * 自动评价
     *
     * @param evaluationSubmitReqDTO 评价信息
     */
    @PostMapping("/autoEvaluate")
    void autoEvaluate(@RequestBody EvaluationSubmitReqDTO evaluationSubmitReqDTO);

    /**
     * 查询指定目标在指定时间之后的新增评价 (供 AI 增量总结使用)
     * targetId 为 null 时查询该类型下所有目标
     */
    @GetMapping("/queryByTargetIdAndTime")
    String queryByTargetIdAndTime(@RequestParam("targetTypeId") Integer targetTypeId,
                                  @RequestParam(value = "targetId", required = false) Long targetId,
                                  @RequestParam(value = "afterTime", required = false) String afterTime);

    /**
     * 按目标名称模糊搜索评价
     *
     * @param targetTypeId 评价目标类型 (7=服务人员, 6=服务项, 可选)
     * @param name         目标名称关键词 (与 targetTypeId 至少填一个)
     * @return 匹配的评价列表 JSON
     */
    @GetMapping("/searchByName")
    String searchByName(@RequestParam(value = "targetTypeId", required = false) Integer targetTypeId,
                        @RequestParam("name") String name);
}
