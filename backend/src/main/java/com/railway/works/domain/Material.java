package com.railway.works.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/** 物资：养修用料的名称、规格、库存与安全库存。 */
@Data
@Entity
@Table(name = "material")
public class Material {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "spec", nullable = false, length = 64)
    private String spec;

    /** 单位：根 / 块 / 吨 / 套 */
    @Column(name = "unit", nullable = false, length = 16)
    private String unit;

    @Column(name = "stock_qty", nullable = false)
    private Integer stockQty;

    /** 安全库存，低于即预警 */
    @Column(name = "safety_stock", nullable = false)
    private Integer safetyStock;
}
