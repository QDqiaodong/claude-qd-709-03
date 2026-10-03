package com.railway.works.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.railway.works.domain.Defect;
import com.railway.works.domain.Equipment;
import com.railway.works.domain.Material;
import com.railway.works.domain.WorkOrder;
import com.railway.works.domain.WorksDict;
import com.railway.works.dto.Views;
import com.railway.works.repository.DefectRepository;
import com.railway.works.repository.EquipmentRepository;
import com.railway.works.repository.LineRepository;
import com.railway.works.repository.MaterialRepository;
import com.railway.works.repository.SectionRepository;
import com.railway.works.repository.WorkOrderRepository;

/** 顶部计数器：一次算齐全路设备的几项关键指标。 */
@Service
public class OverviewService {

    private final LineRepository lineRepository;
    private final SectionRepository sectionRepository;
    private final DeviceService deviceService;
    private final DefectRepository defectRepository;
    private final WorkOrderRepository workOrderRepository;
    private final MaterialRepository materialRepository;
    private final EquipmentRepository equipmentRepository;

    public OverviewService(LineRepository lineRepository,
                           SectionRepository sectionRepository,
                           DeviceService deviceService,
                           DefectRepository defectRepository,
                           WorkOrderRepository workOrderRepository,
                           MaterialRepository materialRepository,
                           EquipmentRepository equipmentRepository) {
        this.lineRepository = lineRepository;
        this.sectionRepository = sectionRepository;
        this.deviceService = deviceService;
        this.defectRepository = defectRepository;
        this.workOrderRepository = workOrderRepository;
        this.materialRepository = materialRepository;
        this.equipmentRepository = equipmentRepository;
    }

    @Transactional(readOnly = true)
    public Views.Overview overview() {
        Views.Overview overview = new Views.Overview();
        overview.setLineCount((int) lineRepository.count());
        overview.setSectionCount((int) sectionRepository.count());
        overview.setDeviceCount(deviceService.list(null, null).size());
        int open = 0;
        int heavy = 0;
        for (Defect defect : defectRepository.findAllByOrderByFoundDateDesc()) {
            if (!Boolean.TRUE.equals(defect.getClosed())) {
                open++;
                if ("HEAVY".equals(defect.getSeverity())) {
                    heavy++;
                }
            }
        }
        overview.setDefectOpen(open);
        overview.setDefectHeavy(heavy);
        int active = 0;
        for (WorkOrder order : workOrderRepository.findAllByOrderByWindowDateAscStartTimeAsc()) {
            if (!"CLOSED".equals(order.getStatus())) {
                active++;
            }
        }
        overview.setWorkOrderActive(active);
        int low = 0;
        for (Material material : materialRepository.findAllByOrderByIdAsc()) {
            if (material.getStockQty() < material.getSafetyStock()) {
                low++;
            }
        }
        overview.setMaterialLow(low);
        int due = 0;
        for (Equipment equipment : equipmentRepository.findAllByOrderByNextMaintainDateAsc()) {
            if (!"SCRAP".equals(equipment.getStatus())
                    && WorksDict.daysFromToday(equipment.getNextMaintainDate()) <= WorksDict.EQUIP_DUE_SOON_DAYS) {
                due++;
            }
        }
        overview.setEquipmentDue(due);
        return overview;
    }

    /** 设备类型清单（页面 Tab） */
    public List<String> deviceTypes() {
        return List.of("RAIL", "SWITCH", "SLEEPER", "BRIDGE", "TUNNEL");
    }
}
