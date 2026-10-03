package com.railway.works.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.TableGenerator;
import jakarta.persistence.Id;
import lombok.Data;

/**
 * 设备台账抽象基类。
 *
 * <p>采用 TABLE_PER_CLASS：五个具体子类各一张完整表（含基类全部字段），
 * 因此主键不能用 IDENTITY/AUTO 自增（MySQL 下会报错），统一走 {@code @TableGenerator} 的 TABLE 策略，
 * 五个子类共用同一张序列表 device_id_gen，保证多态查询时 id 全局唯一。
 */
@Data
@Entity
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
@TableGenerator(name = "device_gen",
        table = "device_id_gen",
        pkColumnName = "gen_key",
        valueColumnName = "gen_value",
        pkColumnValue = "device",
        initialValue = 1000,
        allocationSize = 20)
public abstract class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.TABLE, generator = "device_gen")
    @Column(name = "id")
    private Long id;

    /** 设备编号 */
    @Column(name = "device_no", nullable = false, length = 32)
    private String deviceNo;

    /** 所属区段 */
    @Column(name = "section_id", nullable = false)
    private Long sectionId;

    /** 里程位置（公里，含三位小数） */
    @Column(name = "mileage", nullable = false, precision = 10, scale = 3)
    private BigDecimal mileage;

    @Column(name = "install_date")
    private LocalDate installDate;

    /** 上次检查日 */
    @Column(name = "last_check_date")
    private LocalDate lastCheckDate;

    /** GOOD 良好 / WATCH 关注 / FAULT 失格 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "remark", length = 255)
    private String remark;

    /** 子类类型码：RAIL / SWITCH / SLEEPER / BRIDGE / TUNNEL */
    public abstract String typeCode();

    /** 子类专有参数的一句话摘要 */
    public abstract String specText();
}
