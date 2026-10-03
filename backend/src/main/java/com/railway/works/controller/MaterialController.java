package com.railway.works.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.railway.works.common.ApiResponse;
import com.railway.works.dto.MaterialDtos;
import com.railway.works.dto.Views;
import com.railway.works.service.MaterialService;

import jakarta.validation.Valid;

/** 模块五：物资库存。 */
@RestController
@RequestMapping("/api/materials")
public class MaterialController {

    private final MaterialService materialService;

    public MaterialController(MaterialService materialService) {
        this.materialService = materialService;
    }

    @GetMapping
    public ApiResponse<List<Views.MaterialView>> list() {
        return ApiResponse.success(materialService.list());
    }

    @PostMapping
    public ApiResponse<Views.MaterialView> create(@Valid @RequestBody MaterialDtos.MaterialSaveReq req) {
        return ApiResponse.success("物资已入账", materialService.create(req));
    }

    /** 出入库：quantity 正为入库、负为出库 */
    @PostMapping("/{id}/stock")
    public ApiResponse<Views.MaterialView> adjustStock(@PathVariable("id") Long id,
                                                       @RequestBody MaterialDtos.MaterialStockReq req) {
        return ApiResponse.success("库存已调整", materialService.adjustStock(id, req));
    }
}
