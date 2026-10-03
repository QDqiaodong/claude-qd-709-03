package com.railway.works.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/** 线路：一条运营线的起终点与运营里程。 */
@Data
@Entity
@Table(name = "line")
public class Line {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 线路名称，如 京广上行 */
    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "start_station", nullable = false, length = 64)
    private String startStation;

    @Column(name = "end_station", nullable = false, length = 64)
    private String endStation;

    /** 运营里程（公里） */
    @Column(name = "mileage_km", nullable = false, precision = 10, scale = 3)
    private BigDecimal mileageKm;

    /** 上下行方向：UP 上行 / DOWN 下行 */
    @Column(name = "direction", length = 16)
    private String direction;

    @Column(name = "bureau", length = 64)
    private String bureau;
}
