package com.railway.works.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/** 区段：线路上的一段养修单元，里程起止 + 轨道结构 + 状态。 */
@Data
@Entity
@Table(name = "section")
public class Section {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "line_id", nullable = false)
    private Long lineId;

    /** 区段编号，如 GX-03 */
    @Column(name = "section_no", nullable = false, length = 32)
    private String sectionNo;

    /** 里程起点（公里，含三位小数） */
    @Column(name = "start_mile", nullable = false, precision = 10, scale = 3)
    private BigDecimal startMile;

    /** 里程终点（公里，含三位小数） */
    @Column(name = "end_mile", nullable = false, precision = 10, scale = 3)
    private BigDecimal endMile;

    /** 轨型 60KG / 75KG */
    @Column(name = "rail_type", nullable = false, length = 16)
    private String railType;

    /** 道床类型 BALLAST 有砟 / SLAB 无砟 */
    @Column(name = "bed_type", nullable = false, length = 16)
    private String bedType;

    @Column(name = "design_speed", nullable = false)
    private Integer designSpeed;

    /** NORMAL 正常 / SPEED_LIMIT 限速 / BLOCKED 封锁施工 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 限速状态下的实际限速值 */
    @Column(name = "speed_limit")
    private Integer speedLimit;

    @Column(name = "remark", length = 255)
    private String remark;
}
