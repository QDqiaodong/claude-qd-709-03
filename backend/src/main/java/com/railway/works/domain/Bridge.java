package com.railway.works.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 桥梁：桥长、孔跨、限重。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "bridge")
public class Bridge extends Device {

    /** 桥长（米） */
    @Column(name = "bridge_length", nullable = false, precision = 8, scale = 2)
    private BigDecimal bridgeLength;

    /** 孔跨布置，如 3-32m */
    @Column(name = "span_layout", nullable = false, length = 32)
    private String spanLayout;

    /** 限重（吨） */
    @Column(name = "load_limit", nullable = false, precision = 8, scale = 2)
    private BigDecimal loadLimit;

    @Override
    public String typeCode() {
        return "BRIDGE";
    }

    @Override
    public String specText() {
        return "桥长 " + bridgeLength + "m · 孔跨 " + spanLayout + " · 限重 " + loadLimit + "t";
    }
}
