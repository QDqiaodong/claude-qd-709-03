package com.railway.works.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 模块五（物资与机具）入参。 */
public final class MaterialDtos {

    private MaterialDtos() {
    }

    @Data
    public static class MaterialSaveReq {
        @NotBlank(message = "物资名称不能为空")
        private String name;
        private String spec;
        private String unit;
        private Integer stockQty;
        private Integer safetyStock;
    }

    /** 出入库：正入库、负出库 */
    @Data
    public static class MaterialStockReq {
        private Integer quantity;
    }

    @Data
    public static class EquipmentSaveReq {
        @NotBlank(message = "机具编号不能为空")
        private String equipNo;
        private String name;
        private String model;
        private LocalDate lastMaintainDate;
        private LocalDate nextMaintainDate;
        private String status;
    }

    /** 机具检修登记：检修后自动排下次检修日 */
    @Data
    public static class EquipmentMaintainReq {
        private LocalDate maintainDate;
        private String status;
    }
}
