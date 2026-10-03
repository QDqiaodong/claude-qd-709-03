package com.railway.works.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 模块一（线路）入参。 */
public final class LineDtos {

    private LineDtos() {
    }

    @Data
    public static class LineSaveReq {
        @NotBlank(message = "线路名称不能为空")
        private String name;
        @NotBlank(message = "起点站不能为空")
        private String startStation;
        @NotBlank(message = "终点站不能为空")
        private String endStation;
        private BigDecimal mileageKm;
        /** UP 上行 / DOWN 下行 */
        private String direction;
        private String bureau;
    }
}
