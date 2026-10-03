package com.railway.works.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.railway.works.common.ApiResponse;
import com.railway.works.dto.Views;
import com.railway.works.service.OverviewService;

/** 工区总览计数器。 */
@RestController
@RequestMapping("/api/works")
public class WorksOverviewController {

    private final OverviewService overviewService;

    public WorksOverviewController(OverviewService overviewService) {
        this.overviewService = overviewService;
    }

    @GetMapping("/overview")
    public ApiResponse<Views.Overview> overview() {
        return ApiResponse.success(overviewService.overview());
    }
}
