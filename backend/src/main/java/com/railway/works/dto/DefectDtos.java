package com.railway.works.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 模块三（病害）入参。 */
public final class DefectDtos {

    private DefectDtos() {
    }

    @Data
    public static class DefectSaveReq {
        @NotNull(message = "请选择所属检查单")
        private Long inspectionId;
        /** CRACK / WEAR / SETTLEMENT / GEOMETRY / MUD / BOLT */
        private String defectType;
        /** LIGHT / MIDDLE / HEAVY */
        private String severity;
        private BigDecimal mileage;
        @NotBlank(message = "病害描述不能为空")
        private String description;
        private LocalDate foundDate;
    }

    /** 病害销号 */
    @Data
    public static class DefectCloseReq {
        private String treatment;
        private LocalDate closeDate;
    }
}
