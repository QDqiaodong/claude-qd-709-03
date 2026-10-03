package com.railway.works.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 封锁解除交接台入参。 */
public final class HandoverDtos {

    private HandoverDtos() {
    }

    /** 发起 / 重提：施工负责人逐项填报现场四项并给出建议 */
    @Data
    public static class HandoverSubmitReq {
        @NotNull(message = "请选择要解除交接的养修作业")
        private Long workOrderId;

        /** 复核人姓名：仅用于前端"换人复核"提示，真正复核时以 review 接口的 reviewer 为准；这里不传 */

        @NotBlank(message = "发起人（施工负责人）不能为空")
        private String applicant;

        /** 现场清障 / 人员撤离 / 机具撤出 / 线路复测：OK / NG */
        @NotBlank(message = "必须填写现场清障结果")
        private String clearanceResult;
        @NotBlank(message = "必须填写人员撤离结果")
        private String evacuationResult;
        @NotBlank(message = "必须填写机具撤出结果")
        private String equipmentResult;
        @NotBlank(message = "必须填写线路复测结果")
        private String retestResult;

        private String clearanceRemark;
        private String evacuationRemark;
        private String equipmentRemark;
        private String retestRemark;
        private String applyNote;

        /** 建议 NORMAL 恢复正常 / SPEED_LIMIT 继续限速 / BLOCKED 继续封锁 */
        @NotBlank(message = "必须给出恢复正常、继续限速或继续封锁的建议")
        private String recommendation;

        /** 建议限速时必填，且不得高于设计时速 */
        private Integer speedLimit;
        private String limitReason;

        /** 重提时带上被退回/失效的旧申请 id，首提为空 */
        private Long reapplyOfId;
    }

    /** 复核：复核人逐项确认或退回，并承担最终结论 */
    @Data
    public static class HandoverReviewReq {
        @NotBlank(message = "复核人不能为空")
        private String reviewer;

        /**
         * 复核动作：APPROVE 通过 / RETURN 退回整改。
         * 任一项为 RETURN 即整体退回；全部 CONFIRM 才允许通过。
         */
        @NotBlank(message = "必须给出通过或退回的复核动作")
        private String action;

        private String clearanceCheck;
        private String evacuationCheck;
        private String equipmentCheck;
        private String retestCheck;

        /** 通过时的最终结论 NORMAL / SPEED_LIMIT / BLOCKED（没有重级病害也不自动放行，由复核人决定） */
        private String conclusion;
        private Integer conclusionSpeedLimit;
        private String conclusionLimitReason;

        private String reviewNote;
    }
}
