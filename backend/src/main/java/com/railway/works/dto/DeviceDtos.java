package com.railway.works.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 模块二（设备台账）入参：按 typeCode 落到五个具体子类之一。 */
public final class DeviceDtos {

    private DeviceDtos() {
    }

    @Data
    public static class DeviceSaveReq {
        /** RAIL / SWITCH / SLEEPER / BRIDGE / TUNNEL */
        @NotBlank(message = "设备类型不能为空")
        private String typeCode;
        @NotBlank(message = "设备编号不能为空")
        private String deviceNo;
        private Long sectionId;
        private BigDecimal mileage;
        private LocalDate installDate;
        private LocalDate lastCheckDate;
        private String status;
        private String remark;

        /** 钢轨 */
        private String railType;
        private BigDecimal railLength;
        private BigDecimal lockTemp;
        /** 道岔 */
        private String switchModel;
        private String direction;
        private String machineModel;
        /** 轨枕 */
        private String sleeperType;
        private Integer sleeperCount;
        /** 桥梁 */
        private BigDecimal bridgeLength;
        private String spanLayout;
        private BigDecimal loadLimit;
        /** 隧道 */
        private BigDecimal tunnelLength;
        private String liningType;
        private String lighting;
    }

    /** 设备定期检查登记 */
    @Data
    public static class DeviceCheckReq {
        private LocalDate checkDate;
        private String status;
    }
}
