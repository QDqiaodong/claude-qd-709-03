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
import com.railway.works.dto.DeviceDtos;
import com.railway.works.dto.Views;
import com.railway.works.service.DeviceService;

import jakarta.validation.Valid;

/** 模块二：设备台账（TABLE_PER_CLASS 多态查询）。 */
@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    /**
     * 不传 type 即多态查询：查抽象基类 Device，五种子类实例混排返回。
     * 传 type 时落到具体子类仓库单表查询。
     */
    @GetMapping
    public ApiResponse<List<Views.DeviceView>> list(
            @RequestParam(value = "sectionId", required = false) Long sectionId,
            @RequestParam(value = "type", required = false) String type) {
        return ApiResponse.success(deviceService.list(sectionId, type));
    }

    /** 多态 JPQL：按状态跨全部子类表检索 */
    @GetMapping("/by-status")
    public ApiResponse<List<Views.DeviceView>> byStatus(@RequestParam("status") String status) {
        return ApiResponse.success(deviceService.listByStatus(status));
    }

    @PostMapping
    public ApiResponse<Views.DeviceView> create(@Valid @RequestBody DeviceDtos.DeviceSaveReq req) {
        return ApiResponse.success("设备已入账", deviceService.create(req));
    }

    @PostMapping("/{id}/check")
    public ApiResponse<Views.DeviceView> check(@PathVariable("id") Long id,
                                               @RequestBody(required = false) DeviceDtos.DeviceCheckReq req) {
        return ApiResponse.success("检查已登记", deviceService.check(id, req));
    }
}
