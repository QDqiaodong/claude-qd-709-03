package com.railway.works.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.railway.works.common.ApiResponse;
import com.railway.works.dto.InspectionDtos;
import com.railway.works.dto.Views;
import com.railway.works.service.InspectionService;

import jakarta.validation.Valid;

/** 模块三：检查单。 */
@RestController
@RequestMapping("/api/inspections")
public class InspectionController {

    private final InspectionService inspectionService;

    public InspectionController(InspectionService inspectionService) {
        this.inspectionService = inspectionService;
    }

    @GetMapping
    public ApiResponse<List<Views.InspectionView>> list(
            @RequestParam(value = "sectionId", required = false) Long sectionId) {
        return ApiResponse.success(inspectionService.list(sectionId));
    }

    @PostMapping
    public ApiResponse<Views.InspectionView> create(@Valid @RequestBody InspectionDtos.InspectionSaveReq req) {
        return ApiResponse.success("检查单已开立", inspectionService.create(req));
    }
}
