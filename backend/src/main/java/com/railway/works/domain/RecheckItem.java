package com.railway.works.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 定检复查批次明细：一行一台设备。
 *
 * <p>建批时只固化设备与计划日期；{@code conclusion / measuredMileage / deviceStatus / remark}
 * 由检查人逐台登记。{@code batchStatus} 冗余批次状态，配合生成列 {@code active_key}
 * 做数据库级"一台设备同时只能被一个未完成批次占用"的唯一约束（APPROVED 后释放占用）。
 */
@Data
@Entity
@Table(name = "recheck_item",
        uniqueConstraints = {
                @jakarta.persistence.UniqueConstraint(name = "uk_recheck_item_batch_device",
                        columnNames = {"batch_id", "device_id"}),
                @jakarta.persistence.UniqueConstraint(name = "uk_recheck_item_active",
                        columnNames = {"active_key"})
        })
public class RecheckItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    /** 随批次状态联动：DRAFT / PENDING / APPROVED / REJECTED */
    @Column(name = "batch_status", nullable = false, length = 16)
    private String batchStatus;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(name = "device_no", nullable = false, length = 32)
    private String deviceNo;

    /** RAIL / SWITCH / SLEEPER / BRIDGE / TUNNEL */
    @Column(name = "device_type", nullable = false, length = 16)
    private String deviceType;

    @Column(name = "section_id", nullable = false)
    private Long sectionId;

    @Column(name = "plan_date", nullable = false)
    private LocalDate planDate;

    /** 复查结论：OK 合格 / NG 不合格 */
    @Column(name = "conclusion", length = 16)
    private String conclusion;

    /** 实测里程：提交时必须落在所属区段里程区间内 */
    @Column(name = "measured_mileage", precision = 10, scale = 3)
    private BigDecimal measuredMileage;

    /** 复查后设备状态：GOOD / WATCH / FAULT */
    @Column(name = "device_status", length = 16)
    private String deviceStatus;

    @Column(name = "remark", length = 255)
    private String remark;

    @Column(name = "registered_at")
    private LocalDateTime registeredAt;

    /**
     * 未完成批次的设备占用键（DB 生成列）：DRAFT/PENDING/REJECTED 时取 deviceId，APPROVED 后为 NULL。
     * 配合 uk_recheck_item_active 保证一台设备同时只被一个未完成批次占用。
     * 实体不映射该列（insertable/updatable=false），列定义仅为 ddl-auto 建库场景（测试）保留；
     * 生产 MySQL 以 schema.sql 为准。
     */
    @jakarta.persistence.Column(name = "active_key", insertable = false, updatable = false,
            columnDefinition = "BIGINT GENERATED ALWAYS AS (CASE WHEN batch_status IN ('DRAFT','PENDING','REJECTED') THEN device_id ELSE NULL END)")
    private Long activeKey;
}
