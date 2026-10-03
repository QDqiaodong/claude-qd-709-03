package com.railway.works.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import lombok.Data;

/** 各模块给页面用的出参视图。 */
public final class Views {

    private Views() {
    }

    /** 线路 */
    @Data
    public static class LineView {
        private Long id;
        private String name;
        private String startStation;
        private String endStation;
        private BigDecimal mileageKm;
        private String direction;
        private String directionName;
        private String bureau;
        private Integer sectionCount;
        private Integer defectCount;
        private Integer openDefectCount;
    }

    /** 区段 */
    @Data
    public static class SectionView {
        private Long id;
        private Long lineId;
        private String lineName;
        private String sectionNo;
        private BigDecimal startMile;
        private BigDecimal endMile;
        private String startMileText;
        private String endMileText;
        private BigDecimal lengthKm;
        private String railType;
        private String railTypeName;
        private String bedType;
        private String bedTypeName;
        private Integer designSpeed;
        private String status;
        private String statusName;
        private String statusColor;
        private Integer speedLimit;
        private String remark;
        private Integer defectCount;
        private Integer openDefectCount;
        private Integer deviceCount;
        /** 该区段最严重的未销号病害等级，用于里程条着色 */
        private String worstLevel;
        private String worstLevelName;
    }

    /** 设备（五种子类共用一张视图，专有字段按类型填充） */
    @Data
    public static class DeviceView {
        private Long id;
        private String deviceNo;
        private Long sectionId;
        private String sectionNo;
        private String lineName;
        private BigDecimal mileage;
        private String mileageText;
        private LocalDate installDate;
        private LocalDate lastCheckDate;
        private Long daysSinceCheck;
        private Boolean checkOverdue;
        private String status;
        private String statusName;
        private String typeCode;
        private String typeName;
        private String specText;
        private String remark;

        // ---- 钢轨 ----
        private String railType;
        private BigDecimal railLength;
        private BigDecimal lockTemp;
        // ---- 道岔 ----
        private String switchModel;
        private String direction;
        private String machineModel;
        // ---- 轨枕 ----
        private String sleeperType;
        private Integer sleeperCount;
        // ---- 桥梁 ----
        private BigDecimal bridgeLength;
        private String spanLayout;
        private BigDecimal loadLimit;
        // ---- 隧道 ----
        private BigDecimal tunnelLength;
        private String liningType;
        private String lighting;
    }

    /** 检查单 */
    @Data
    public static class InspectionView {
        private Long id;
        private String inspectNo;
        private Long sectionId;
        private String sectionNo;
        private String lineName;
        private String inspector;
        private LocalDate inspectDate;
        private String inspectType;
        private String inspectTypeName;
        private String remark;
        private Integer defectCount;
        private Integer openDefectCount;
    }

    /** 病害 */
    @Data
    public static class DefectView {
        private Long id;
        private Long inspectionId;
        private String inspectNo;
        private Long sectionId;
        private String sectionNo;
        private String lineName;
        private String defectType;
        private String defectTypeName;
        private String severity;
        private String severityName;
        private String severityColor;
        private BigDecimal mileage;
        private String mileageText;
        private String description;
        private LocalDate foundDate;
        private Boolean closed;
        private String closedName;
        private LocalDate closeDate;
        private String treatment;
        private Integer limitDays;
        private Long overdueDays;
    }

    /** 养修作业单 */
    @Data
    public static class WorkOrderView {
        private Long id;
        private String orderNo;
        private Long sectionId;
        private String sectionNo;
        private String lineName;
        private String workItem;
        private String workItemName;
        private LocalDate windowDate;
        private LocalTime startTime;
        private LocalTime endTime;
        private String windowText;
        private Long windowMinutes;
        private String leader;
        private Integer workerCount;
        private String cooperateUnit;
        private String status;
        private String statusName;
        private String nextStatusName;
        /** 已验收后不能再直接销号，必须走封锁解除交接台 */
        private Boolean handoverRequired;
        /** 该作业当前进行中（待复核/已退回）的交接申请 id，用于页面直达 */
        private Long activeHandoverId;
        private String activeHandoverStatus;
        private String activeHandoverStatusName;
    }

    /** 交接列表行 / 申请详情共用视图；详情字段在列表场景留空 */
    @Data
    public static class HandoverView {
        private Long id;
        private String handoverNo;
        private Long workOrderId;
        private String orderNo;
        private String workItemName;
        private Long sectionId;
        private String sectionNo;
        private Long lineId;
        private String lineName;
        private Integer designSpeed;
        private String applicant;
        private String reviewer;
        private String status;
        private String statusName;
        private Long version;
        private LocalDateTime submittedAt;
        private LocalDateTime reviewedAt;
        private String recommendation;
        private String recommendationName;
        private Integer speedLimit;
        private String limitReason;
        private String conclusion;
        private String conclusionName;
        private Integer conclusionSpeedLimit;
        private String conclusionLimitReason;
        private String reviewNote;
        private String applyNote;
        private String expiredReason;
        private Long reapplyOfId;

        // ---- 现场四项填报 ----
        private String clearanceResult;
        private String clearanceResultName;
        private String evacuationResult;
        private String evacuationResultName;
        private String equipmentResult;
        private String equipmentResultName;
        private String retestResult;
        private String retestResultName;
        private String clearanceRemark;
        private String evacuationRemark;
        private String equipmentRemark;
        private String retestRemark;

        // ---- 复核四项确认 ----
        private String clearanceCheck;
        private String clearanceCheckName;
        private String evacuationCheck;
        private String evacuationCheckName;
        private String equipmentCheck;
        private String equipmentCheckName;
        private String retestCheck;
        private String retestCheckName;

        // ---- 提交时固化的快照 ----
        private String snapWorkStatus;
        private String snapWorkStatusName;
        private String snapSectionStatus;
        private String snapSectionStatusName;
        private Integer snapSectionSpeed;
        private List<DefectView> snapHeavyDefects;

        // ---- 当前最新现场（与快照逐项比对） ----
        private String currentWorkStatus;
        private String currentWorkStatusName;
        private String currentSectionStatus;
        private String currentSectionStatusName;
        private Integer currentSectionSpeed;
        private List<DefectView> currentHeavyDefects;

        /** 复核失效的变化项（作业状态/区段状态/重级病害销号或新增） */
        private List<HandoverChangeView> changes;

        /** 完整处理轨迹 */
        private List<HandoverTimelineView> timeline;

        /** 重提链路：同一条链上的历史申请（仅详情返回） */
        private List<HandoverView> chain;
    }

    /** 快照 vs 最新现场的单个变化项 */
    @Data
    public static class HandoverChangeView {
        /** WORK_STATUS 作业状态 / SECTION_STATUS 区段状态 / DEFECT 病害销号变化 */
        private String type;
        private String typeName;
        private String before;
        private String after;
        private String detail;
    }

    /** 交接处理轨迹行 */
    @Data
    public static class HandoverTimelineView {
        private Long id;
        private String action;
        private String actionName;
        private String actor;
        private String note;
        private LocalDateTime createdAt;
    }

    /** 物资 */
    @Data
    public static class MaterialView {
        private Long id;
        private String name;
        private String spec;
        private String unit;
        private Integer stockQty;
        private Integer safetyStock;
        private Boolean low;
        private Integer gap;
    }

    /** 机具 */
    @Data
    public static class EquipmentView {
        private Long id;
        private String equipNo;
        private String name;
        private String model;
        private LocalDate lastMaintainDate;
        private LocalDate nextMaintainDate;
        private Long daysToMaintain;
        private Boolean dueSoon;
        private String status;
        private String statusName;
    }

    /** 定检复查批次列表行 */
    @Data
    public static class RecheckBatchView {
        private Long id;
        private String batchNo;
        private Long sectionId;
        private String sectionNo;
        private Long lineId;
        private String lineName;
        private String inspector;
        private String reviewer;
        private java.time.LocalDate planDate;
        private String status;
        private String statusName;
        private Long version;
        private String remark;
        private String reviewNote;
        private java.time.LocalDateTime submittedAt;
        private java.time.LocalDateTime reviewedAt;

        /** 明细总数 / 已逐台登记数（有结论）/ 合格 / 不合格 */
        private Integer totalCount;
        private Integer registeredCount;
        private Integer okCount;
        private Integer ngCount;
        /** 计划日期已过但仍未通过的明细数（逾期项） */
        private Integer overdueCount;
        /** 进度百分比 0~100 */
        private Integer progress;

        /** 明细（详情接口返回，列表场景不填） */
        private List<RecheckItemView> items;
    }

    /** 定检复查批次明细行：登记值 + 设备台账最新值（通过后两者应一致） */
    @Data
    public static class RecheckItemView {
        private Long id;
        private Long batchId;
        private String batchStatus;
        private Long deviceId;
        private String deviceNo;
        private String deviceType;
        private String deviceTypeName;
        private Long sectionId;
        private String sectionNo;
        private java.time.LocalDate planDate;
        private String conclusion;
        private String conclusionName;
        private BigDecimal measuredMileage;
        private String measuredMileageText;
        private String deviceStatus;
        private String deviceStatusName;
        private String remark;
        private java.time.LocalDateTime registeredAt;
        private Boolean registered;

        /** 设备台账当前值（详情装配，台账刷新后反映最新结果） */
        private BigDecimal ledgerMileage;
        private String ledgerMileageText;
        private java.time.LocalDate ledgerLastCheckDate;
        private String ledgerStatus;
        private String ledgerStatusName;
        private Long ledgerDaysSinceCheck;
        private Boolean ledgerOverdue;
        private String ledgerRemark;

        /** 建批候选列表标记：已被其他未完成批次占用 */
        private Boolean occupied;
        private Long occupiedBatchId;
        private String occupiedBatchNo;

        /** 列表内联校验问题（提交时阻断原因），详情返回 */
        private List<String> problems;
        private Boolean planOverdue;
    }

    /** 顶部计数器 */
    @Data
    public static class Overview {        private Integer lineCount;
        private Integer sectionCount;
        private Integer deviceCount;
        private Integer defectOpen;
        private Integer defectHeavy;
        private Integer workOrderActive;
        private Integer materialLow;
        private Integer equipmentDue;
    }

    /** 线路里程看板：一条线的区段 + 病害，前端据此画里程条 */
    @Data
    public static class BoardView {
        private LineView line;
        private List<SectionView> sections;
        private List<DefectView> defects;
        private BigDecimal rangeStart;
        private BigDecimal rangeEnd;
        private String rangeStartText;
        private String rangeEndText;
    }
}
