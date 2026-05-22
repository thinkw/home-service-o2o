package com.jzo2o.orders.manager.controller.agency;

import com.jzo2o.common.model.PageResult;
import com.jzo2o.common.utils.UserContext;
import com.jzo2o.orders.manager.model.dto.request.*;
import com.jzo2o.orders.manager.model.dto.response.OrdersServeDetailResDTO;
import com.jzo2o.orders.manager.model.dto.response.OrdersServeResDTO;
import com.jzo2o.orders.manager.model.dto.response.OrdersServeStatusNumResDTO;
import com.jzo2o.orders.manager.service.IOrdersServeManagerService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController("orders-agency")
@Api(tags = "机构端-服务单相关接口")
@RequestMapping("/agency")
public class AgencyOrdersServeController {

    @Resource
    private IOrdersServeManagerService ordersServeManagerService;

    @GetMapping("/queryForPage")
    @ApiOperation("机构端分页查询服务单列表")
    public PageResult<OrdersServeResDTO> queryForPage(@Validated OrdersServePageQueryReqDTO ordersServePageQueryReqDTO) {
        return ordersServeManagerService.queryForPage(
                UserContext.currentUserId(),
                3,
                ordersServePageQueryReqDTO
        );
    }

    @PostMapping("/allocation")
    @ApiOperation("机构端分配服务人员")
    public void allocation(@Validated @RequestBody OrdersServeAllocationReqDTO ordersServeAllocationReqDTO) {
        ordersServeManagerService.allocation(
                ordersServeAllocationReqDTO.getId(),
                UserContext.currentUserId(),
                ordersServeAllocationReqDTO.getInstitutionStaffId()
        );
    }
}
