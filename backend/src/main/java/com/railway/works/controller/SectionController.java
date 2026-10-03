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
import com.railway.works.dto.SectionDtos;
import com.railway.works.dto.Views;
import com.railway.works.service.SectionService;

import jakarta.validation.Valid;

/** 模块一：区段。 */
@RestController
@RequestMapping("/api/sections")
public class SectionController {

    private final SectionService sectionService;

    public SectionController(SectionService sectionService) {
        this.sectionService = sectionService;
    }

    @GetMapping
    public ApiResponse<List<Views.SectionView>> list(
            @RequestParam(value = "lineId", required = false) Long lineId) {
        return ApiResponse.success(sectionService.list(lineId));
    }

    @PostMapping
    public ApiResponse<Views.SectionView> create(@Valid @RequestBody SectionDtos.SectionSaveReq req) {
        return ApiResponse.success("区段已建档", sectionService.create(req));
    }

    @PostMapping("/{id}/status")
    public ApiResponse<Views.SectionView> setStatus(@PathVariable("id") Long id,
                                                    @RequestBody SectionDtos.SectionStatusReq req) {
        return ApiResponse.success("区段状态已更新", sectionService.setStatus(id, req));
    }
}
