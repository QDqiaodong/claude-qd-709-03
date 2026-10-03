package com.railway.works.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;

/**
 * 封锁解除交接申请：已验收养修作业解除封锁、交接销号的唯一入口。
 *
 * <p>状态四选一：PENDING 待复核 / APPROVED 已通过 / REJECTED 已退回 / EXPIRED 已失效。
 * <p>{@code version} 为 JPA 乐观锁：两名复核人并发处理时，只有第一份结论能落库，
 * 较晚到达的提交会拿到陈旧版本号而失败，重复点击/网络重试不会产生两次交接。
 */
@Data
@Entity
@Table(name = "handover")
public class Handover {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "handover_no", nullable = false, length = 32)
    private String handoverNo;

    @Column(name = "work_order_id", nullable = false)
    private Long workOrderId;

    @Column(name = "section_id", nullable = false)
    private Long sectionId;

    /** 发起人 = 施工负责人 */
    @Column(name = "applicant", nullable = false, length = 32)
    private String applicant;

    /** 复核人，必须与发起人不同 */
    @Column(name = "reviewer", length = 32)
    private String reviewer;

    /** PENDING / APPROVED / REJECTED / EXPIRED */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 乐观锁版本号，并发复核只允许一份结论生效 */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    // ---- 现场四项逐项填报：OK 具备条件 / NG 不具备 ----
    @Column(name = "clearance_result", nullable = false, length = 8)
    private String clearanceResult;

    @Column(name = "evacuation_result", nullable = false, length = 8)
    private String evacuationResult;

    @Column(name = "equipment_result", nullable = false, length = 8)
    private String equipmentResult;

    @Column(name = "retest_result", nullable = false, length = 8)
    private String retestResult;

    @Column(name = "clearance_remark", length = 255)
    private String clearanceRemark;

    @Column(name = "evacuation_remark", length = 255)
    private String evacuationRemark;

    @Column(name = "equipment_remark", length = 255)
    private String equipmentRemark;

    @Column(name = "retest_remark", length = 255)
    private String retestRemark;

    @Column(name = "apply_note", length = 255)
    private String applyNote;

    /** 发起人建议：NORMAL 恢复正常 / SPEED_LIMIT 继续限速 / BLOCKED 继续封锁 */
    @Column(name = "recommendation", nullable = false, length = 16)
    private String recommendation;

    @Column(name = "speed_limit")
    private Integer speedLimit;

    @Column(name = "limit_reason", length = 255)
    private String limitReason;

    // ---- 复核四项逐项确认：CONFIRM 确认 / RETURN 退回整改 ----
    @Column(name = "clearance_check", length = 8)
    private String clearanceCheck;

    @Column(name = "evacuation_check", length = 8)
    private String evacuationCheck;

    @Column(name = "equipment_check", length = 8)
    private String equipmentCheck;

    @Column(name = "retest_check", length = 8)
    private String retestCheck;

    /** 复核最终结论 NORMAL / SPEED_LIMIT / BLOCKED */
    @Column(name = "conclusion", length = 16)
    private String conclusion;

    @Column(name = "conclusion_speed_limit")
    private Integer conclusionSpeedLimit;

    @Column(name = "conclusion_limit_reason", length = 255)
    private String conclusionLimitReason;

    @Column(name = "review_note", length = 255)
    private String reviewNote;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    // ---- 提交时刻固化的现场快照，复核前据此比对失效 ----
    @Column(name = "snap_work_status", nullable = false, length = 16)
    private String snapWorkStatus;

    @Column(name = "snap_section_status", nullable = false, length = 16)
    private String snapSectionStatus;

    @Column(name = "snap_section_speed")
    private Integer snapSectionSpeed;

    /** 未销号重级病害 id 清单，逗号分隔；销号/新增即视为变化 */
    @Column(name = "snap_heavy_defect_ids", nullable = false, length = 255)
    private String snapHeavyDefectIds;

    @Column(name = "expired_reason", length = 255)
    private String expiredReason;

    /** 重提自哪张旧申请 */
    @Column(name = "reapply_of_id")
    private Long reapplyOfId;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
