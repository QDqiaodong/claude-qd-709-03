package com.railway.works.domain;

import java.time.LocalDate;
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
 * 定检复查批次：按区段把超 {@link WorksDict#DEVICE_CHECK_CYCLE_DAYS} 天未检的五类设备
 * （钢轨/道岔/轨枕/桥梁/隧道）汇总建批，逐台登记后提交复核。
 *
 * <p>状态机：DRAFT 草稿 → PENDING 待复核 → APPROVED 已通过；
 * PENDING 可被复核退回为 REJECTED 已退回，退回后保留全部明细，整改重提回到 PENDING。
 *
 * <p>{@code version} 为乐观锁：两名复核人并发时只有一份结论生效。
 */
@Data
@Entity
@Table(name = "recheck_batch")
public class RecheckBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "batch_no", nullable = false, length = 32)
    private String batchNo;

    @Column(name = "section_id", nullable = false)
    private Long sectionId;

    /** 检查人 */
    @Column(name = "inspector", nullable = false, length = 32)
    private String inspector;

    /** 复核人 */
    @Column(name = "reviewer", length = 32)
    private String reviewer;

    /** 计划检查日期 */
    @Column(name = "plan_date", nullable = false)
    private LocalDate planDate;

    /** DRAFT / PENDING / APPROVED / REJECTED */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "remark", length = 255)
    private String remark;

    /** 退回原因 / 复核说明（退回时保留） */
    @Column(name = "review_note", length = 255)
    private String reviewNote;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /** 最近一次提交 / 整改重提时间 */
    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
