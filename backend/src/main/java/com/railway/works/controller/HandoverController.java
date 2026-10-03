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
import com.railway.works.dto.HandoverDtos;
import com.railway.works.dto.Views;
import com.railway.works.service.HandoverService;

import jakarta.validation.Valid;

/**
 * 封锁解除交接台：已验收养修作业的解除申请、复核、失效检测与处理轨迹。
 */
@RestController
@RequestMapping("/api/handovers")
public class HandoverController {

    private final HandoverService handoverService;

    public HandoverController(HandoverService handoverService) {
        this.handoverService = handoverService;
    }

    /** 列表：按线路、区段、交接状态筛选 */
    @GetMapping
    public ApiResponse<List<Views.HandoverView>> list(
            @RequestParam(value = "lineId", required = false) Long lineId,
            @RequestParam(value = "sectionId", required = false) Long sectionId,
            @RequestParam(value = "status", required = false) String status) {
        return ApiResponse.success(handoverService.list(lineId, sectionId, status));
    }

    /** 可发起交接的已验收作业（且没有进行中申请） */
    @GetMapping("/eligible-orders")
    public ApiResponse<List<Views.WorkOrderView>> eligibleOrders() {
        return ApiResponse.success(handoverService.eligibleOrders());
    }

    @GetMapping("/{id}")
    public ApiResponse<Views.HandoverView> detail(@PathVariable("id") Long id) {
        return ApiResponse.success(handoverService.detail(id));
    }

    /** 施工负责人发起 / 整改后重提 */
    @PostMapping
    public ApiResponse<Views.HandoverView> submit(@Valid @RequestBody HandoverDtos.HandoverSubmitReq req) {
        boolean resubmit = req.getReapplyOfId() != null;
        return ApiResponse.success(resubmit ? "已基于最新现场重新提交，等待复核" : "封锁解除申请已提交，等待复核",
                handoverService.submit(req));
    }

    /** 复核人逐项确认通过或退回整改 */
    @PostMapping("/{id}/review")
    public ApiResponse<Views.HandoverView> review(@PathVariable("id") Long id,
                                                  @Valid @RequestBody HandoverDtos.HandoverReviewReq req) {
        Views.HandoverView view = handoverService.review(id, req);
        String msg = "RETURN".equals(req.getAction()) ? "已退回施工负责人整改" : "复核通过，作业已销号、区段已按结论更新";
        return ApiResponse.success(msg, view);
    }
}
