package com.railway.works.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.railway.works.common.ApiResponse;
import com.railway.works.dto.RecheckDtos;
import com.railway.works.dto.Views;
import com.railway.works.service.RecheckService;

import jakarta.validation.Valid;

/**
 * 模块六：定检复查批次——按区段汇总 90 天周期到期设备，逐台登记、提交复核、通过回写台账。
 */
@RestController
@RequestMapping("/api/recheck-batches")
public class RecheckController {

    private final RecheckService recheckService;

    public RecheckController(RecheckService recheckService) {
        this.recheckService = recheckService;
    }

    /** 列表：按线路、区段、批次状态筛选，展示进度与逾期项 */
    @GetMapping
    public ApiResponse<List<Views.RecheckBatchView>> list(
            @RequestParam(value = "lineId", required = false) Long lineId,
            @RequestParam(value = "sectionId", required = false) Long sectionId,
            @RequestParam(value = "status", required = false) String status) {
        return ApiResponse.success(recheckService.list(lineId, sectionId, status));
    }

    /** 某区段 90 天周期到期设备（跨五类），含是否已被未完成批次占用标记 */
    @GetMapping("/due-devices")
    public ApiResponse<List<Views.RecheckItemView>> dueDevices(@RequestParam("sectionId") Long sectionId) {
        return ApiResponse.success(recheckService.eligibleDevices(sectionId));
    }

    @GetMapping("/{id}")
    public ApiResponse<Views.RecheckBatchView> detail(@PathVariable("id") Long id) {
        return ApiResponse.success(recheckService.detail(id));
    }

    /** 勾选到期设备并指定检查人、计划日期建批（草稿） */
    @PostMapping
    public ApiResponse<Views.RecheckBatchView> create(@Valid @RequestBody RecheckDtos.RecheckCreateReq req) {
        return ApiResponse.success("定检复查批次已建立，请逐台登记复查结果", recheckService.create(req));
    }

    /** 批次内逐台登记：结论 / 实测里程 / 设备状态 / 备注 */
    @PostMapping("/{id}/items/{itemId}/register")
    public ApiResponse<Views.RecheckBatchView> register(@PathVariable("id") Long id,
                                                        @PathVariable("itemId") Long itemId,
                                                        @Valid @RequestBody RecheckDtos.RecheckRegisterReq req) {
        return ApiResponse.success("复查结果已登记", recheckService.register(id, itemId, req));
    }

    /** 提交复核；已退回批次整改后调用同接口重提 */
    @PostMapping("/{id}/submit")
    public ApiResponse<Views.RecheckBatchView> submit(@PathVariable("id") Long id) {
        return ApiResponse.success("批次已提交，等待复核", recheckService.submit(id));
    }

    /** 复核人通过（批量回写台账）或退回（保留明细与原因） */
    @PostMapping("/{id}/review")
    public ApiResponse<Views.RecheckBatchView> review(@PathVariable("id") Long id,
                                                      @Valid @RequestBody RecheckDtos.RecheckReviewReq req) {
        boolean approve = "APPROVE".equalsIgnoreCase(req.getAction());
        String msg = approve ? "复核通过，已批量更新设备上次检查日期与状态" : "已退回，登记明细与退回原因已保留";
        return ApiResponse.success(msg, recheckService.review(id, req));
    }
}
