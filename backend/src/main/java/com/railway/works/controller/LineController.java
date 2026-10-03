package com.railway.works.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.railway.works.common.ApiResponse;
import com.railway.works.dto.LineDtos;
import com.railway.works.dto.Views;
import com.railway.works.service.LineService;

import jakarta.validation.Valid;

/** 模块一：线路（含线路里程看板）。 */
@RestController
@RequestMapping("/api/lines")
public class LineController {

    private final LineService lineService;

    public LineController(LineService lineService) {
        this.lineService = lineService;
    }

    @GetMapping
    public ApiResponse<List<Views.LineView>> list() {
        return ApiResponse.success(lineService.list());
    }

    /** 一条线路的里程看板：区段 + 病害 + 里程范围 */
    @GetMapping("/{id}/board")
    public ApiResponse<Views.BoardView> board(@PathVariable("id") Long id) {
        return ApiResponse.success(lineService.board(id));
    }

    @PostMapping
    public ApiResponse<Views.LineView> create(@Valid @RequestBody LineDtos.LineSaveReq req) {
        return ApiResponse.success("线路已建档", lineService.create(req));
    }
}
