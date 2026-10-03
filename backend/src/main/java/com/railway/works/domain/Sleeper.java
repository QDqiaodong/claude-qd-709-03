package com.railway.works.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 轨枕：类型（混凝土/木枕）、根数。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "sleeper")
public class Sleeper extends Device {

    /** CONCRETE 混凝土 / WOOD 木枕 */
    @Column(name = "sleeper_type", nullable = false, length = 16)
    private String sleeperType;

    /** 铺设根数 */
    @Column(name = "sleeper_count", nullable = false)
    private Integer sleeperCount;

    @Override
    public String typeCode() {
        return "SLEEPER";
    }

    @Override
    public String specText() {
        return ("WOOD".equals(sleeperType) ? "木枕" : "混凝土枕") + " · " + sleeperCount + " 根";
    }
}
