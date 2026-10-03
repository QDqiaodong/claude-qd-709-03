package com.railway.works.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 道岔：道岔型号、开闭向、转辙机型。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "switch")
public class Switch extends Device {

    /** 道岔型号，如 12号、18号 */
    @Column(name = "switch_model", nullable = false, length = 32)
    private String switchModel;

    /** 开闭向：LEFT 左开 / RIGHT 右开 */
    @Column(name = "direction", nullable = false, length = 16)
    private String direction;

    /** 转辙机型，如 ZD6-D、S700K */
    @Column(name = "machine_model", nullable = false, length = 32)
    private String machineModel;

    @Override
    public String typeCode() {
        return "SWITCH";
    }

    @Override
    public String specText() {
        return switchModel + " 道岔 · " + ("LEFT".equals(direction) ? "左开" : "右开") + " · 转辙机 " + machineModel;
    }
}
