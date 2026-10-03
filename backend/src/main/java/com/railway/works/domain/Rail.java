package com.railway.works.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 钢轨：轨型、长度、锁定轨温。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "rail")
public class Rail extends Device {

    /** 轨型 60KG / 75KG */
    @Column(name = "rail_type", nullable = false, length = 16)
    private String railType;

    /** 单根长度（米） */
    @Column(name = "rail_length", nullable = false, precision = 8, scale = 2)
    private BigDecimal railLength;

    /** 锁定轨温（℃） */
    @Column(name = "lock_temp", nullable = false, precision = 5, scale = 1)
    private BigDecimal lockTemp;

    @Override
    public String typeCode() {
        return "RAIL";
    }

    @Override
    public String specText() {
        return WorksDict.railTypeName(railType) + " · " + railLength + "m · 锁定轨温 " + lockTemp + "℃";
    }
}
