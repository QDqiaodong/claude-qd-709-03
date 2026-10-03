package com.railway.works.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * 铁路工务词典：枚举码、中文名与几条硬规则集中在此，便于出题时被要求调整或扩展。
 */
public final class WorksDict {

    private WorksDict() {
    }

    // ---------------- 区段 ----------------
    private static final Map<String, String> RAIL_TYPE = Map.of(
            "60KG", "60kg/m",
            "75KG", "75kg/m");

    private static final Map<String, String> BED_TYPE = Map.of(
            "BALLAST", "有砟",
            "SLAB", "无砟");

    /** 正常 / 限速 / 封锁施工 */
    private static final Map<String, String> SECTION_STATUS = Map.of(
            "NORMAL", "正常",
            "SPEED_LIMIT", "限速",
            "BLOCKED", "封锁施工");

    /** 各状态下的区段色条颜色（页面里程条用） */
    private static final Map<String, String> SECTION_COLOR = Map.of(
            "NORMAL", "#2e9e5b",
            "SPEED_LIMIT", "#e8a33d",
            "BLOCKED", "#4a5b63");

    /** 限速区段默认限速值（km/h） */
    public static final int DEFAULT_SPEED_LIMIT = 45;

    // ---------------- 设备（继承体系） ----------------
    private static final Map<String, String> DEVICE_TYPE = Map.of(
            "RAIL", "钢轨",
            "SWITCH", "道岔",
            "SLEEPER", "轨枕",
            "BRIDGE", "桥梁",
            "TUNNEL", "隧道");

    /** 设备台账状态 */
    private static final Map<String, String> DEVICE_STATUS = Map.of(
            "GOOD", "良好",
            "WATCH", "关注",
            "FAULT", "失格");

    /** 设备定检周期（天）：超过这个天数未检查即列入超期 */
    public static final int DEVICE_CHECK_CYCLE_DAYS = 90;

    // ---------------- 检查与病害 ----------------
    private static final Map<String, String> INSPECT_TYPE = Map.of(
            "STATIC", "静态",
            "DYNAMIC", "动态",
            "RIDE", "添乘");

    private static final Map<String, String> DEFECT_TYPE = Map.of(
            "CRACK", "裂纹",
            "WEAR", "磨耗",
            "SETTLEMENT", "沉降",
            "GEOMETRY", "几何超限",
            "MUD", "翻浆冒泥",
            "BOLT", "螺栓松动");

    private static final Map<String, String> DEFECT_LEVEL = Map.of(
            "LIGHT", "轻",
            "MIDDLE", "中",
            "HEAVY", "重");

    /** 严重等级对应色条/标记颜色 */
    private static final Map<String, String> DEFECT_COLOR = Map.of(
            "LIGHT", "#e8c33d",
            "MIDDLE", "#e88b3d",
            "HEAVY", "#c62828");

    /** 病害销号时限（天）：重级 7 天、中 15 天、轻 30 天，逾期页面标红 */
    private static final Map<String, Integer> DEFECT_LIMIT_DAYS = Map.of(
            "LIGHT", 30,
            "MIDDLE", 15,
            "HEAVY", 7);

    // ---------------- 养修作业 ----------------
    private static final Map<String, String> WORK_ITEM = Map.of(
            "LIFT", "起道",
            "ALIGN", "拨道",
            "TAMP", "捣固",
            "RAIL_CHANGE", "换轨",
            "SCREEN", "清筛",
            "GRIND", "打磨",
            "SLEEPER_CHANGE", "换枕",
            "STRESS", "应力放散");

    /** 状态机顺序：计划 → 已批复 → 施工中 → 已验收 → 已销号 */
    private static final List<String> WORK_STATUS_ORDER = List.of("PLAN", "APPROVED", "WORKING", "CHECKED", "CLOSED");

    private static final Map<String, String> WORK_STATUS = Map.of(
            "PLAN", "计划",
            "APPROVED", "已批复",
            "WORKING", "施工中",
            "CHECKED", "已验收",
            "CLOSED", "已销号");

    /** 天窗点单次最长时长（分钟），超过需分段提报 */
    public static final int MAX_WINDOW_MINUTES = 300;

    // ---------------- 封锁解除交接 ----------------
    /** 交接申请四态 + SUPERSEDED（已退回/失效单被重提后进入的历史态，不再占"进行中"名额） */
    private static final Map<String, String> HANDOVER_STATUS = Map.of(
            "PENDING", "待复核",
            "APPROVED", "已通过",
            "REJECTED", "已退回",
            "EXPIRED", "已失效",
            "SUPERSEDED", "已重提");

    /** 现场四项填报结果 / 复核逐项确认 */
    private static final Map<String, String> CHECK_RESULT = Map.of(
            "OK", "合格",
            "NG", "不合格");

    private static final Map<String, String> REVIEW_CHECK = Map.of(
            "CONFIRM", "确认合格",
            "RETURN", "退回整改");

    /** 轨迹动作 */
    private static final Map<String, String> HANDOVER_ACTION = Map.of(
            "SUBMIT", "提交申请",
            "RESUBMIT", "整改重提",
            "RETURN", "复核退回",
            "APPROVE", "复核通过",
            "EXPIRE", "系统失效");

    /** 解除建议/最终结论 与区段状态共用一套码：NORMAL / SPEED_LIMIT / BLOCKED */

    // ---------------- 物资机具 ----------------
    private static final Map<String, String> EQUIP_STATUS = Map.of(
            "AVAILABLE", "可用",
            "IN_USE", "在用",
            "MAINTAIN", "检修",
            "SCRAP", "报废");

    /** 机具检修周期（天） */
    public static final int EQUIP_MAINTAIN_CYCLE_DAYS = 180;

    /** 临近检修提醒天数 */
    public static final int EQUIP_DUE_SOON_DAYS = 30;

    // ---------------- 定检复查批次 ----------------
    /** 批次四态：草稿 / 待复核 / 已通过 / 已退回（退回保留明细，整改后重提） */
    private static final Map<String, String> RECHECK_STATUS = Map.of(
            "DRAFT", "草稿",
            "PENDING", "待复核",
            "APPROVED", "已通过",
            "REJECTED", "已退回");

    /** 逐台复查结论：合格 / 不合格 */
    private static final Map<String, String> RECHECK_CONCLUSION = Map.of(
            "OK", "合格",
            "NG", "不合格");

    // ---------------- 取值方法 ----------------

    public static boolean isValidRailType(String code) {
        return code != null && RAIL_TYPE.containsKey(code);
    }

    public static String railTypeName(String code) {
        return nameOf(RAIL_TYPE, code);
    }

    public static boolean isValidBedType(String code) {
        return code != null && BED_TYPE.containsKey(code);
    }

    public static String bedTypeName(String code) {
        return nameOf(BED_TYPE, code);
    }

    public static boolean isValidSectionStatus(String code) {
        return code != null && SECTION_STATUS.containsKey(code);
    }

    public static String sectionStatusName(String code) {
        return nameOf(SECTION_STATUS, code);
    }

    public static String sectionColor(String code) {
        return SECTION_COLOR.getOrDefault(code, "#8d9aa3");
    }

    public static boolean isValidDeviceType(String code) {
        return code != null && DEVICE_TYPE.containsKey(code);
    }

    public static String deviceTypeName(String code) {
        return nameOf(DEVICE_TYPE, code);
    }

    public static boolean isValidDeviceStatus(String code) {
        return code != null && DEVICE_STATUS.containsKey(code);
    }

    public static String deviceStatusName(String code) {
        return nameOf(DEVICE_STATUS, code);
    }

    public static boolean isValidInspectType(String code) {
        return code != null && INSPECT_TYPE.containsKey(code);
    }

    public static String inspectTypeName(String code) {
        return nameOf(INSPECT_TYPE, code);
    }

    public static boolean isValidDefectType(String code) {
        return code != null && DEFECT_TYPE.containsKey(code);
    }

    public static String defectTypeName(String code) {
        return nameOf(DEFECT_TYPE, code);
    }

    public static boolean isValidDefectLevel(String code) {
        return code != null && DEFECT_LEVEL.containsKey(code);
    }

    public static String defectLevelName(String code) {
        return nameOf(DEFECT_LEVEL, code);
    }

    public static String defectColor(String code) {
        return DEFECT_COLOR.getOrDefault(code, "#8d9aa3");
    }

    /** 病害整治时限（天） */
    public static int defectLimitDays(String code) {
        return DEFECT_LIMIT_DAYS.getOrDefault(code, 30);
    }

    public static boolean isValidWorkItem(String code) {
        return code != null && WORK_ITEM.containsKey(code);
    }

    public static String workItemName(String code) {
        return nameOf(WORK_ITEM, code);
    }

    public static boolean isValidWorkStatus(String code) {
        return code != null && WORK_STATUS.containsKey(code);
    }

    public static String workStatusName(String code) {
        return nameOf(WORK_STATUS, code);
    }

    /** 作业单状态的下一站；已是终态返回 null */
    public static String nextWorkStatus(String code) {
        int index = WORK_STATUS_ORDER.indexOf(code);
        if (index < 0 || index >= WORK_STATUS_ORDER.size() - 1) {
            return null;
        }
        return WORK_STATUS_ORDER.get(index + 1);
    }

    public static boolean isValidHandoverStatus(String code) {
        return code != null && HANDOVER_STATUS.containsKey(code);
    }

    public static String handoverStatusName(String code) {
        return nameOf(HANDOVER_STATUS, code);
    }

    public static boolean isValidCheckResult(String code) {
        return code != null && CHECK_RESULT.containsKey(code);
    }

    public static String checkResultName(String code) {
        return nameOf(CHECK_RESULT, code);
    }

    public static boolean isValidReviewCheck(String code) {
        return code != null && REVIEW_CHECK.containsKey(code);
    }

    public static String reviewCheckName(String code) {
        return nameOf(REVIEW_CHECK, code);
    }

    public static String handoverActionName(String code) {
        return nameOf(HANDOVER_ACTION, code);
    }

    public static boolean isValidEquipStatus(String code) {
        return code != null && EQUIP_STATUS.containsKey(code);
    }

    public static String equipStatusName(String code) {
        return nameOf(EQUIP_STATUS, code);
    }

    public static boolean isValidRecheckStatus(String code) {
        return code != null && RECHECK_STATUS.containsKey(code);
    }

    public static String recheckStatusName(String code) {
        return nameOf(RECHECK_STATUS, code);
    }

    public static boolean isValidRecheckConclusion(String code) {
        return code != null && RECHECK_CONCLUSION.containsKey(code);
    }

    public static String recheckConclusionName(String code) {
        return nameOf(RECHECK_CONCLUSION, code);
    }

    /** 里程格式化：12.300 → K12+300 */
    public static String mileageText(BigDecimal mileage) {
        if (mileage == null) {
            return "";
        }
        BigDecimal value = mileage.setScale(3, RoundingMode.HALF_UP);
        int km = value.intValue();
        int meter = value.subtract(BigDecimal.valueOf(km)).movePointRight(3).setScale(0, RoundingMode.HALF_UP).intValue();
        return "K" + km + "+" + String.format("%03d", meter);
    }

    /** 距今天数：正数未到期，负数已逾期 */
    public static long daysFromToday(LocalDate date) {
        return date == null ? 0L : ChronoUnit.DAYS.between(LocalDate.now(), date);
    }

    private static String nameOf(Map<String, String> dict, String code) {
        return code == null ? "" : dict.getOrDefault(code, code);
    }
}
