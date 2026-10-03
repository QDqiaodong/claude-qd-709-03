package com.railway.works.domain;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/** 检查单：一次线路检查（静态 / 动态 / 添乘）。 */
@Data
@Entity
@Table(name = "inspection")
public class Inspection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 检查单号 */
    @Column(name = "inspect_no", nullable = false, length = 32)
    private String inspectNo;

    @Column(name = "section_id", nullable = false)
    private Long sectionId;

    /** 检查人 */
    @Column(name = "inspector", nullable = false, length = 32)
    private String inspector;

    @Column(name = "inspect_date", nullable = false)
    private LocalDate inspectDate;

    /** STATIC 静态 / DYNAMIC 动态 / RIDE 添乘 */
    @Column(name = "inspect_type", nullable = false, length = 16)
    private String inspectType;

    @Column(name = "remark", length = 255)
    private String remark;
}
