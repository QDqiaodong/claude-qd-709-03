package com.railway.works.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 模块一（区段）入参。 */
public final class SectionDtos {

    private SectionDtos() {
    }

    @Data
    public static class SectionSaveReq {
        private Long lineId;
        @NotBlank(message = "区段编号不能为空")
        private String sectionNo;
        private BigDecimal startMile;
        private BigDecimal endMile;
        /** 60KG / 75KG */
        private String railType;
        /** BALLAST / SLAB */
        private String bedType;
        private Integer designSpeed;
        private String remark;
    }

    /** 区段状态变更（正常 / 限速 / 封锁施工） */
    @Data
    public static class SectionStatusReq {
        private String status;
        private Integer speedLimit;
    }
}
