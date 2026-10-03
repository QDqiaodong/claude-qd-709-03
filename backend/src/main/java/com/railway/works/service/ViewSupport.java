package com.railway.works.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;

import com.railway.works.domain.Bridge;
import com.railway.works.domain.Defect;
import com.railway.works.domain.Device;
import com.railway.works.domain.Equipment;
import com.railway.works.domain.Inspection;
import com.railway.works.domain.Line;
import com.railway.works.domain.Material;
import com.railway.works.domain.Rail;
import com.railway.works.domain.Section;
import com.railway.works.domain.Sleeper;
import com.railway.works.domain.Switch;
import com.railway.works.domain.Tunnel;
import com.railway.works.domain.WorkOrder;
import com.railway.works.domain.WorksDict;
import com.railway.works.dto.Views;
import com.railway.works.repository.DefectRepository;
import com.railway.works.repository.DeviceRepository;
import com.railway.works.repository.InspectionRepository;
import com.railway.works.repository.LineRepository;
import com.railway.works.repository.SectionRepository;

/**
 * 实体 → 出参视图的公共装配：名称翻译、里程格式化、跨表统计都集中在此。
 */
@Component
public class ViewSupport {

    private final LineRepository lineRepository;
    private final SectionRepository sectionRepository;
    private final DefectRepository defectRepository;
    private final InspectionRepository inspectionRepository;
    private final DeviceRepository deviceRepository;

    public ViewSupport(LineRepository lineRepository,
                       SectionRepository sectionRepository,
                       DefectRepository defectRepository,
                       InspectionRepository inspectionRepository,
                       DeviceRepository deviceRepository) {
        this.lineRepository = lineRepository;
        this.sectionRepository = sectionRepository;
        this.defectRepository = defectRepository;
        this.inspectionRepository = inspectionRepository;
        this.deviceRepository = deviceRepository;
    }

    public Views.LineView toView(Line line) {
        Views.LineView view = new Views.LineView();
        view.setId(line.getId());
        view.setName(line.getName());
        view.setStartStation(line.getStartStation());
        view.setEndStation(line.getEndStation());
        view.setMileageKm(line.getMileageKm());
        view.setDirection(line.getDirection());
        view.setDirectionName("DOWN".equals(line.getDirection()) ? "下行" : "上行");
        view.setBureau(line.getBureau());
        List<Section> sections = sectionRepository.findAllByLineIdOrderByStartMileAsc(line.getId());
        view.setSectionCount(sections.size());
        int total = 0;
        int open = 0;
        for (Section section : sections) {
            int dc = 0;
            int oc = 0;
            for (Defect defect : defectRepository.findAllBySectionIdOrderByMileageAsc(section.getId())) {
                dc++;
                if (!Boolean.TRUE.equals(defect.getClosed())) {
                    oc++;
                }
            }
            total += dc;
            open += oc;
        }
        view.setDefectCount(total);
        view.setOpenDefectCount(open);
        return view;
    }

    public Views.SectionView toView(Section section) {
        Views.SectionView view = new Views.SectionView();
        view.setId(section.getId());
        view.setLineId(section.getLineId());
        view.setLineName(lineName(section.getLineId()));
        view.setSectionNo(section.getSectionNo());
        view.setStartMile(section.getStartMile());
        view.setEndMile(section.getEndMile());
        view.setStartMileText(WorksDict.mileageText(section.getStartMile()));
        view.setEndMileText(WorksDict.mileageText(section.getEndMile()));
        view.setLengthKm(section.getEndMile().subtract(section.getStartMile()).setScale(3, java.math.RoundingMode.HALF_UP));
        view.setRailType(section.getRailType());
        view.setRailTypeName(WorksDict.railTypeName(section.getRailType()));
        view.setBedType(section.getBedType());
        view.setBedTypeName(WorksDict.bedTypeName(section.getBedType()));
        view.setDesignSpeed(section.getDesignSpeed());
        view.setStatus(section.getStatus());
        view.setStatusName(WorksDict.sectionStatusName(section.getStatus()));
        view.setStatusColor(WorksDict.sectionColor(section.getStatus()));
        view.setSpeedLimit(section.getSpeedLimit());
        view.setRemark(section.getRemark());
        view.setDeviceCount(deviceRepository.findAllBySectionIdOrderByMileageAsc(section.getId()).size());
        int total = 0;
        int open = 0;
        String worst = null;
        for (Defect defect : defectRepository.findAllBySectionIdOrderByMileageAsc(section.getId())) {
            total++;
            if (!Boolean.TRUE.equals(defect.getClosed())) {
                open++;
                worst = worse(worst, defect.getSeverity());
            }
        }
        view.setDefectCount(total);
        view.setOpenDefectCount(open);
        view.setWorstLevel(worst);
        view.setWorstLevelName(worst == null ? "无" : WorksDict.defectLevelName(worst));
        return view;
    }

    /** 多态设备视图：公共字段 + 按具体子类填专有字段 */
    public Views.DeviceView toView(Device device) {
        Views.DeviceView view = new Views.DeviceView();
        view.setId(device.getId());
        view.setDeviceNo(device.getDeviceNo());
        view.setSectionId(device.getSectionId());
        view.setSectionNo(sectionNo(device.getSectionId()));
        view.setLineName(lineNameOfSection(device.getSectionId()));
        view.setMileage(device.getMileage());
        view.setMileageText(WorksDict.mileageText(device.getMileage()));
        view.setInstallDate(device.getInstallDate());
        view.setLastCheckDate(device.getLastCheckDate());
        long days = device.getLastCheckDate() == null
                ? WorksDict.DEVICE_CHECK_CYCLE_DAYS + 1
                : ChronoUnit.DAYS.between(device.getLastCheckDate(), LocalDate.now());
        view.setDaysSinceCheck(days);
        view.setCheckOverdue(days > WorksDict.DEVICE_CHECK_CYCLE_DAYS);
        view.setStatus(device.getStatus());
        view.setStatusName(WorksDict.deviceStatusName(device.getStatus()));
        view.setTypeCode(device.typeCode());
        view.setTypeName(WorksDict.deviceTypeName(device.typeCode()));
        view.setSpecText(device.specText());
        view.setRemark(device.getRemark());
        if (device instanceof Rail rail) {
            view.setRailType(rail.getRailType());
            view.setRailLength(rail.getRailLength());
            view.setLockTemp(rail.getLockTemp());
        } else if (device instanceof Switch sw) {
            view.setSwitchModel(sw.getSwitchModel());
            view.setDirection(sw.getDirection());
            view.setMachineModel(sw.getMachineModel());
        } else if (device instanceof Sleeper sleeper) {
            view.setSleeperType(sleeper.getSleeperType());
            view.setSleeperCount(sleeper.getSleeperCount());
        } else if (device instanceof Bridge bridge) {
            view.setBridgeLength(bridge.getBridgeLength());
            view.setSpanLayout(bridge.getSpanLayout());
            view.setLoadLimit(bridge.getLoadLimit());
        } else if (device instanceof Tunnel tunnel) {
            view.setTunnelLength(tunnel.getTunnelLength());
            view.setLiningType(tunnel.getLiningType());
            view.setLighting(tunnel.getLighting());
        }
        return view;
    }

    public Views.InspectionView toView(Inspection inspection) {
        Views.InspectionView view = new Views.InspectionView();
        view.setId(inspection.getId());
        view.setInspectNo(inspection.getInspectNo());
        view.setSectionId(inspection.getSectionId());
        view.setSectionNo(sectionNo(inspection.getSectionId()));
        view.setLineName(lineNameOfSection(inspection.getSectionId()));
        view.setInspector(inspection.getInspector());
        view.setInspectDate(inspection.getInspectDate());
        view.setInspectType(inspection.getInspectType());
        view.setInspectTypeName(WorksDict.inspectTypeName(inspection.getInspectType()));
        view.setRemark(inspection.getRemark());
        List<Defect> defects = defectRepository.findAllByInspectionIdOrderByMileageAsc(inspection.getId());
        view.setDefectCount(defects.size());
        int open = 0;
        for (Defect defect : defects) {
            if (!Boolean.TRUE.equals(defect.getClosed())) {
                open++;
            }
        }
        view.setOpenDefectCount(open);
        return view;
    }

    public Views.DefectView toView(Defect defect) {
        Views.DefectView view = new Views.DefectView();
        view.setId(defect.getId());
        view.setInspectionId(defect.getInspectionId());
        view.setInspectNo(inspectionRepository.findById(defect.getInspectionId())
                .map(Inspection::getInspectNo).orElse(""));
        view.setSectionId(defect.getSectionId());
        view.setSectionNo(sectionNo(defect.getSectionId()));
        view.setLineName(lineNameOfSection(defect.getSectionId()));
        view.setDefectType(defect.getDefectType());
        view.setDefectTypeName(WorksDict.defectTypeName(defect.getDefectType()));
        view.setSeverity(defect.getSeverity());
        view.setSeverityName(WorksDict.defectLevelName(defect.getSeverity()));
        view.setSeverityColor(WorksDict.defectColor(defect.getSeverity()));
        view.setMileage(defect.getMileage());
        view.setMileageText(WorksDict.mileageText(defect.getMileage()));
        view.setDescription(defect.getDescription());
        view.setFoundDate(defect.getFoundDate());
        view.setClosed(Boolean.TRUE.equals(defect.getClosed()));
        view.setClosedName(Boolean.TRUE.equals(defect.getClosed()) ? "已销号" : "未销号");
        view.setCloseDate(defect.getCloseDate());
        view.setTreatment(defect.getTreatment());
        int limitDays = WorksDict.defectLimitDays(defect.getSeverity());
        view.setLimitDays(limitDays);
        long overdue = Boolean.TRUE.equals(defect.getClosed()) || defect.getFoundDate() == null
                ? 0L
                : ChronoUnit.DAYS.between(defect.getFoundDate().plusDays(limitDays), LocalDate.now());
        view.setOverdueDays(Math.max(0L, overdue));
        return view;
    }

    public Views.WorkOrderView toView(WorkOrder order) {
        Views.WorkOrderView view = new Views.WorkOrderView();
        view.setId(order.getId());
        view.setOrderNo(order.getOrderNo());
        view.setSectionId(order.getSectionId());
        view.setSectionNo(sectionNo(order.getSectionId()));
        view.setLineName(lineNameOfSection(order.getSectionId()));
        view.setWorkItem(order.getWorkItem());
        view.setWorkItemName(WorksDict.workItemName(order.getWorkItem()));
        view.setWindowDate(order.getWindowDate());
        view.setStartTime(order.getStartTime());
        view.setEndTime(order.getEndTime());
        view.setWindowText(order.getWindowDate() + " " + order.getStartTime() + "~" + order.getEndTime());
        view.setWindowMinutes(ChronoUnit.MINUTES.between(order.getStartTime(), order.getEndTime()));
        view.setLeader(order.getLeader());
        view.setWorkerCount(order.getWorkerCount());
        view.setCooperateUnit(order.getCooperateUnit());
        view.setStatus(order.getStatus());
        view.setStatusName(WorksDict.workStatusName(order.getStatus()));
        String next = WorksDict.nextWorkStatus(order.getStatus());
        view.setNextStatusName(next == null ? "已终结" : WorksDict.workStatusName(next));
        return view;
    }

    public Views.MaterialView toView(Material material) {
        Views.MaterialView view = new Views.MaterialView();
        view.setId(material.getId());
        view.setName(material.getName());
        view.setSpec(material.getSpec());
        view.setUnit(material.getUnit());
        view.setStockQty(material.getStockQty());
        view.setSafetyStock(material.getSafetyStock());
        view.setLow(material.getStockQty() < material.getSafetyStock());
        view.setGap(material.getSafetyStock() - material.getStockQty());
        return view;
    }

    public Views.EquipmentView toView(Equipment equipment) {
        Views.EquipmentView view = new Views.EquipmentView();
        view.setId(equipment.getId());
        view.setEquipNo(equipment.getEquipNo());
        view.setName(equipment.getName());
        view.setModel(equipment.getModel());
        view.setLastMaintainDate(equipment.getLastMaintainDate());
        view.setNextMaintainDate(equipment.getNextMaintainDate());
        long days = WorksDict.daysFromToday(equipment.getNextMaintainDate());
        view.setDaysToMaintain(days);
        view.setDueSoon(days <= WorksDict.EQUIP_DUE_SOON_DAYS && !"SCRAP".equals(equipment.getStatus()));
        view.setStatus(equipment.getStatus());
        view.setStatusName(WorksDict.equipStatusName(equipment.getStatus()));
        return view;
    }

    /** 取两个病害等级中更严重的一个 */
    public String worse(String a, String b) {
        return rank(a) >= rank(b) ? a : b;
    }

    private int rank(String level) {
        return switch (level == null ? "" : level) {
            case "HEAVY" -> 3;
            case "MIDDLE" -> 2;
            case "LIGHT" -> 1;
            default -> 0;
        };
    }

    public String lineName(Long lineId) {
        return lineId == null ? "" : lineRepository.findById(lineId).map(Line::getName).orElse("");
    }

    private String lineNameOfSection(Long sectionId) {
        if (sectionId == null) {
            return "";
        }
        return sectionRepository.findById(sectionId).map(s -> lineName(s.getLineId())).orElse("");
    }

    private String sectionNo(Long sectionId) {
        return sectionId == null ? "" : sectionRepository.findById(sectionId).map(Section::getSectionNo).orElse("");
    }

    public BigDecimal mileageOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
