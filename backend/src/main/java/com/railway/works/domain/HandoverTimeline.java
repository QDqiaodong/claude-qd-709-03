package com.railway.works.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 交接处理轨迹：提交、退回、重提、复核结论、系统失效每次操作留痕一行，
 * 保留时间、人员与说明，组成申请详情页的完整处理轨迹。
 */
@Data
@Entity
@Table(name = "handover_timeline")
public class HandoverTimeline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "handover_id", nullable = false)
    private Long handoverId;

    /** SUBMIT 提交 / RESUBMIT 重提 / RETURN 退回 / APPROVE 通过 / EXPIRE 失效 */
    @Column(name = "action", nullable = false, length = 16)
    private String action;

    /** 操作人；系统失效记为"系统" */
    @Column(name = "actor", length = 32)
    private String actor;

    @Column(name = "note", length = 255)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
