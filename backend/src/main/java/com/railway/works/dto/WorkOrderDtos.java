package com.railway.works.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 模块四（养修作业）入参。 */
public final class WorkOrderDtos {

    private WorkOrderDtos() {
    }

    @Data
    public static class WorkOrderSaveReq {
        @NotBlank(message = "作业单号不能为空")
        private String orderNo;
        private Long sectionId;
        private String workItem;
        private LocalDate windowDate;
        private LocalTime startTime;
        private LocalTime endTime;
        private String leader;
        private Integer workerCount;
        private String cooperateUnit;
    }
}
