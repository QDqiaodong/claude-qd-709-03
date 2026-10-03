package com.railway.works.domain;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/** 机具：编号、名称、型号、检修日期与状态。 */
@Data
@Entity
@Table(name = "equipment")
public class Equipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 机具编号 */
    @Column(name = "equip_no", nullable = false, length = 32)
    private String equipNo;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "model", nullable = false, length = 32)
    private String model;

    @Column(name = "last_maintain_date")
    private LocalDate lastMaintainDate;

    @Column(name = "next_maintain_date")
    private LocalDate nextMaintainDate;

    /** AVAILABLE 可用 / IN_USE 在用 / MAINTAIN 检修 / SCRAP 报废 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;
}
