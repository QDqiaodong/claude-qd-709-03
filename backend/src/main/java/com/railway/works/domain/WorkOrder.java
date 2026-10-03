package com.railway.works.domain;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/** 养修作业单：天窗点 + 作业项目 + 状态流转。 */
@Data
@Entity
@Table(name = "work_order")
public class WorkOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 作业单号 */
    @Column(name = "order_no", nullable = false, length = 32)
    private String orderNo;

    @Column(name = "section_id", nullable = false)
    private Long sectionId;

    /** LIFT 起道 / ALIGN 拨道 / TAMP 捣固 / RAIL_CHANGE 换轨 / SCREEN 清筛 / GRIND 打磨 / SLEEPER_CHANGE 换枕 / STRESS 应力放散 */
    @Column(name = "work_item", nullable = false, length = 24)
    private String workItem;

    /** 天窗点日期 */
    @Column(name = "window_date", nullable = false)
    private LocalDate windowDate;

    /** 天窗起始时刻 */
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    /** 天窗结束时刻 */
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    /** 施工负责人 */
    @Column(name = "leader", nullable = false, length = 32)
    private String leader;

    @Column(name = "worker_count", nullable = false)
    private Integer workerCount;

    /** 配合单位 */
    @Column(name = "cooperate_unit", length = 64)
    private String cooperateUnit;

    /** PLAN 计划 / APPROVED 已批复 / WORKING 施工中 / CHECKED 已验收 / CLOSED 已销号 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;
}
