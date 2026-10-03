package com.railway.works.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 定检复查批次入参。 */
public final class RecheckDtos {

    private RecheckDtos() {
    }

    /** 建批：勾选到期设备 + 指定检查人和计划日期 */
    @Data
    public static class RecheckCreateReq {
        @NotNull(message = "请选择汇总区段")
        private Long sectionId;

        @NotBlank(message = "请指定检查人")
        private String inspector;

        @NotNull(message = "请指定计划检查日期")
        private LocalDate planDate;

        /** 勾选的设备 id（可跨钢轨/道岔/轨枕/桥梁/隧道五类） */
        @NotEmpty(message = "请至少勾选一台到期设备")
        private List<Long> deviceIds;

        private String remark;
    }

    /** 逐台登记：结论 / 实测里程 / 设备状态 / 备注 */
    @Data
    public static class RecheckRegisterReq {
        /** OK 合格 / NG 不合格 */
        @NotBlank(message = "请登记复查结论（合格/不合格）")
        private String conclusion;

        @NotNull(message = "请登记实测里程")
        private java.math.BigDecimal measuredMileage;

        @NotBlank(message = "请登记复查后设备状态")
        private String deviceStatus;

        private String remark;
    }

    /** 复核：通过或退回 */
    @Data
    public static class RecheckReviewReq {
        @NotBlank(message = "复核人不能为空")
        private String reviewer;

        /** APPROVE 通过 / RETURN 退回 */
        @NotBlank(message = "必须给出通过或退回的复核动作")
        private String action;

        /** 退回原因（退回必填，退回后保留在批次上） */
        private String reviewNote;
    }
}
