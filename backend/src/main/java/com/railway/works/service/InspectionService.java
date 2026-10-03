package com.railway.works.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.railway.works.common.BizException;
import com.railway.works.domain.Inspection;
import com.railway.works.domain.Section;
import com.railway.works.domain.WorksDict;
import com.railway.works.dto.InspectionDtos;
import com.railway.works.dto.Views;
import com.railway.works.repository.InspectionRepository;
import com.railway.works.repository.SectionRepository;

/**
 * 模块三：检查单。
 *
 * <p>规则面：
 * <ol>
 *   <li>检查单号唯一</li>
 *   <li>检查日期不得晚于今天</li>
 *   <li>同一区段同一天不允许重复开同一种类型的检查单</li>
 * </ol>
 */
@Service
public class InspectionService {

    private final InspectionRepository inspectionRepository;
    private final SectionRepository sectionRepository;
    private final ViewSupport viewSupport;

    public InspectionService(InspectionRepository inspectionRepository,
                             SectionRepository sectionRepository,
                             ViewSupport viewSupport) {
        this.inspectionRepository = inspectionRepository;
        this.sectionRepository = sectionRepository;
        this.viewSupport = viewSupport;
    }

    @Transactional(readOnly = true)
    public List<Views.InspectionView> list(Long sectionId) {
        List<Inspection> inspections = sectionId == null
                ? inspectionRepository.findAllByOrderByInspectDateDesc()
                : inspectionRepository.findAllBySectionIdOrderByInspectDateDesc(sectionId);
        List<Views.InspectionView> list = new ArrayList<>();
        for (Inspection inspection : inspections) {
            list.add(viewSupport.toView(inspection));
        }
        return list;
    }

    @Transactional
    public Views.InspectionView create(InspectionDtos.InspectionSaveReq req) {
        String inspectNo = req.getInspectNo() == null ? "" : req.getInspectNo().trim();
        if (inspectionRepository.existsByInspectNo(inspectNo)) {
            throw new BizException("检查单号 " + inspectNo + " 已存在");
        }
        Section section = req.getSectionId() == null ? null : sectionRepository.findById(req.getSectionId()).orElse(null);
        if (section == null) {
            throw new BizException("请先选择检查所属区段");
        }
        if (!WorksDict.isValidInspectType(req.getInspectType())) {
            throw new BizException("检查类型只能是 STATIC 静态 / DYNAMIC 动态 / RIDE 添乘");
        }
        LocalDate inspectDate = req.getInspectDate() == null ? LocalDate.now() : req.getInspectDate();
        if (inspectDate.isAfter(LocalDate.now())) {
            throw new BizException("检查日期不能晚于今天");
        }
        for (Inspection exist : inspectionRepository.findAllBySectionIdOrderByInspectDateDesc(section.getId())) {
            if (inspectDate.equals(exist.getInspectDate()) && req.getInspectType().equals(exist.getInspectType())) {
                throw new BizException("区段 " + section.getSectionNo() + " 在 " + inspectDate
                        + " 已有" + WorksDict.inspectTypeName(req.getInspectType()) + "检查单 " + exist.getInspectNo() + "，不能重复开单");
            }
        }
        Inspection inspection = new Inspection();
        inspection.setInspectNo(inspectNo);
        inspection.setSectionId(section.getId());
        inspection.setInspector(req.getInspector() == null ? "" : req.getInspector().trim());
        inspection.setInspectDate(inspectDate);
        inspection.setInspectType(req.getInspectType());
        inspection.setRemark(req.getRemark());
        return viewSupport.toView(inspectionRepository.save(inspection));
    }
}
