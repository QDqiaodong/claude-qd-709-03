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
import com.railway.works.dto.DefectDtos;
import com.railway.works.dto.Views;
import com.railway.works.service.DefectService;

import jakarta.validation.Valid;

/** 模块三：病害记录与销号。 */
@RestController
@RequestMapping("/api/defects")
public class DefectController {

    private final DefectService defectService;

    public DefectController(DefectService defectService) {
        this.defectService = defectService;
    }

    @GetMapping
    public ApiResponse<List<Views.DefectView>> list(
            @RequestParam(value = "sectionId", required = false) Long sectionId,
            @RequestParam(value = "severity", required = false) String severity,
            @RequestParam(value = "closed", required = false) Boolean closed) {
        return ApiResponse.success(defectService.list(sectionId, severity, closed));
    }

    @PostMapping
    public ApiResponse<Views.DefectView> create(@Valid @RequestBody DefectDtos.DefectSaveReq req) {
        return ApiResponse.success("病害已登记", defectService.create(req));
    }

    /** 销号：必须填整治措施 */
    @PostMapping("/{id}/close")
    public ApiResponse<Views.DefectView> close(@PathVariable("id") Long id,
                                               @RequestBody(required = false) DefectDtos.DefectCloseReq req) {
        return ApiResponse.success("病害已销号", defectService.close(id, req));
    }
}
