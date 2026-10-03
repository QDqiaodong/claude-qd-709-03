package com.railway.works.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/** 病害记录：某次检查发现的一处线路病害，含销号与整治措施。 */
@Data
@Entity
@Table(name = "defect")
public class Defect {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属检查单 */
    @Column(name = "inspection_id", nullable = false)
    private Long inspectionId;

    /** 所属区段（冗余自检查单，便于按区段直接检索） */
    @Column(name = "section_id", nullable = false)
    private Long sectionId;

    /** CRACK 裂纹 / WEAR 磨耗 / SETTLEMENT 沉降 / GEOMETRY 几何超限 / MUD 翻浆冒泥 / BOLT 螺栓松动 */
    @Column(name = "defect_type", nullable = false, length = 16)
    private String defectType;

    /** LIGHT 轻 / MIDDLE 中 / HEAVY 重 */
    @Column(name = "severity", nullable = false, length = 16)
    private String severity;

    /** 里程位置（公里，含三位小数） */
    @Column(name = "mileage", nullable = false, precision = 10, scale = 3)
    private BigDecimal mileage;

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    @Column(name = "found_date", nullable = false)
    private LocalDate foundDate;

    /** 是否销号 */
    @Column(name = "closed", nullable = false)
    private Boolean closed;

    @Column(name = "close_date")
    private LocalDate closeDate;

    /** 整治措施 */
    @Column(name = "treatment", length = 255)
    private String treatment;
}
