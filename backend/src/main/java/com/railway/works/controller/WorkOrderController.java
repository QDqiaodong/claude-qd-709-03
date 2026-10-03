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
import com.railway.works.dto.Views;
import com.railway.works.dto.WorkOrderDtos;
import com.railway.works.service.WorkOrderService;

import jakarta.validation.Valid;

/** 模块四：养修作业单与状态流转。 */
@RestController
@RequestMapping("/api/work-orders")
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    public WorkOrderController(WorkOrderService workOrderService) {
        this.workOrderService = workOrderService;
    }

    @GetMapping
    public ApiResponse<List<Views.WorkOrderView>> list(
            @RequestParam(value = "sectionId", required = false) Long sectionId) {
        return ApiResponse.success(workOrderService.list(sectionId));
    }

    @PostMapping
    public ApiResponse<Views.WorkOrderView> create(@Valid @RequestBody WorkOrderDtos.WorkOrderSaveReq req) {
        return ApiResponse.success("作业单已提报", workOrderService.create(req));
    }

    /** 状态推进：计划 → 已批复 → 施工中 → 已验收 → 已销号 */
    @PostMapping("/{id}/advance")
    public ApiResponse<Views.WorkOrderView> advance(@PathVariable("id") Long id) {
        return ApiResponse.success("状态已推进", workOrderService.advance(id));
    }
}
