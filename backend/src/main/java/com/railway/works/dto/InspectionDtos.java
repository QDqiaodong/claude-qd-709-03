package com.railway.works.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 模块三（检查单）入参。 */
public final class InspectionDtos {

    private InspectionDtos() {
    }

    @Data
    public static class InspectionSaveReq {
        @NotBlank(message = "检查单号不能为空")
        private String inspectNo;
        private Long sectionId;
        @NotBlank(message = "检查人不能为空")
        private String inspector;
        private LocalDate inspectDate;
        /** STATIC / DYNAMIC / RIDE */
        private String inspectType;
        private String remark;
    }
}
