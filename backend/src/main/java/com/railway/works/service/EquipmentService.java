package com.railway.works.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.railway.works.common.BizException;
import com.railway.works.domain.Equipment;
import com.railway.works.domain.WorksDict;
import com.railway.works.dto.MaterialDtos;
import com.railway.works.dto.Views;
import com.railway.works.repository.EquipmentRepository;

/**
 * 模块五：机具。
 *
 * <p>规则面：
 * <ol>
 *   <li>机具编号唯一</li>
 *   <li>报废机具不再排检修</li>
 *   <li>检修登记后按 {@value WorksDict#EQUIP_MAINTAIN_CYCLE_DAYS} 天自动排下次检修日</li>
 * </ol>
 */
@Service
public class EquipmentService {

    private final EquipmentRepository equipmentRepository;
    private final ViewSupport viewSupport;

    public EquipmentService(EquipmentRepository equipmentRepository, ViewSupport viewSupport) {
        this.equipmentRepository = equipmentRepository;
        this.viewSupport = viewSupport;
    }

    @Transactional(readOnly = true)
    public List<Views.EquipmentView> list() {
        List<Views.EquipmentView> list = new ArrayList<>();
        for (Equipment equipment : equipmentRepository.findAllByOrderByNextMaintainDateAsc()) {
            list.add(viewSupport.toView(equipment));
        }
        return list;
    }

    @Transactional
    public Views.EquipmentView create(MaterialDtos.EquipmentSaveReq req) {
        String equipNo = req.getEquipNo() == null ? "" : req.getEquipNo().trim();
        if (equipmentRepository.existsByEquipNo(equipNo)) {
            throw new BizException("机具编号 " + equipNo + " 已存在");
        }
        Equipment equipment = new Equipment();
        equipment.setEquipNo(equipNo);
        equipment.setName(req.getName() == null ? "" : req.getName().trim());
        equipment.setModel(req.getModel() == null ? "" : req.getModel().trim());
        equipment.setLastMaintainDate(req.getLastMaintainDate());
        equipment.setNextMaintainDate(req.getNextMaintainDate());
        equipment.setStatus(WorksDict.isValidEquipStatus(req.getStatus()) ? req.getStatus() : "AVAILABLE");
        return viewSupport.toView(equipmentRepository.save(equipment));
    }

    /** 检修登记：刷新上次检修日并排下次检修日 */
    @Transactional
    public Views.EquipmentView maintain(Long id, MaterialDtos.EquipmentMaintainReq req) {
        Equipment equipment = equipmentRepository.findById(id)
                .orElseThrow(() -> new BizException("机具不存在：id=" + id));
        if ("SCRAP".equals(equipment.getStatus())) {
            throw new BizException("机具 " + equipment.getEquipNo() + " 已报废，不再安排检修");
        }
        LocalDate maintainDate = req == null || req.getMaintainDate() == null ? LocalDate.now() : req.getMaintainDate();
        if (maintainDate.isAfter(LocalDate.now())) {
            throw new BizException("检修日期不能晚于今天");
        }
        equipment.setLastMaintainDate(maintainDate);
        equipment.setNextMaintainDate(maintainDate.plusDays(WorksDict.EQUIP_MAINTAIN_CYCLE_DAYS));
        equipment.setStatus("AVAILABLE");
        return viewSupport.toView(equipmentRepository.save(equipment));
    }
}
