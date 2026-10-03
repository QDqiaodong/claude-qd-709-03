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
import com.railway.works.service.EquipmentService;

import jakarta.validation.Valid;

/** 模块五：机具与检修。 */
@RestController
@RequestMapping("/api/equipments")
public class EquipmentController {

    private final EquipmentService equipmentService;

    public EquipmentController(EquipmentService equipmentService) {
        this.equipmentService = equipmentService;
    }

    @GetMapping
    public ApiResponse<List<Views.EquipmentView>> list() {
        return ApiResponse.success(equipmentService.list());
    }

    @PostMapping
    public ApiResponse<Views.EquipmentView> create(@Valid @RequestBody MaterialDtos.EquipmentSaveReq req) {
        return ApiResponse.success("机具已入账", equipmentService.create(req));
    }

    /** 检修登记：刷新上次检修日并排下次检修日 */
    @PostMapping("/{id}/maintain")
    public ApiResponse<Views.EquipmentView> maintain(@PathVariable("id") Long id,
                                                     @RequestBody(required = false) MaterialDtos.EquipmentMaintainReq req) {
        return ApiResponse.success("检修已登记", equipmentService.maintain(id, req));
    }
}
