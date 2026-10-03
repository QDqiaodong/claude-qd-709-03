package com.railway.works.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 隧道：全长、衬砌类型、照明。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "tunnel")
public class Tunnel extends Device {

    /** 全长（米） */
    @Column(name = "tunnel_length", nullable = false, precision = 8, scale = 2)
    private BigDecimal tunnelLength;

    /** 衬砌类型，如 复合式衬砌 / 整体式衬砌 */
    @Column(name = "lining_type", nullable = false, length = 32)
    private String liningType;

    /** 照明状态：NORMAL 正常 / FAULT 故障 / NONE 无照明 */
    @Column(name = "lighting", nullable = false, length = 16)
    private String lighting;

    @Override
    public String typeCode() {
        return "TUNNEL";
    }

    @Override
    public String specText() {
        return "全长 " + tunnelLength + "m · " + liningType + " · 照明" + ("NORMAL".equals(lighting) ? "正常" : ("FAULT".equals(lighting) ? "故障" : "缺失"));
    }
}
