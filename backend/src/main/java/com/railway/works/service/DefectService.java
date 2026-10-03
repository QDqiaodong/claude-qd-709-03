package com.railway.works.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.railway.works.common.BizException;
import com.railway.works.domain.Defect;
import com.railway.works.domain.Inspection;
import com.railway.works.domain.Section;
import com.railway.works.domain.WorksDict;
import com.railway.works.dto.DefectDtos;
import com.railway.works.dto.Views;
import com.railway.works.repository.DefectRepository;
import com.railway.works.repository.InspectionRepository;
import com.railway.works.repository.SectionRepository;

/**
 * 模块三：病害。
 *
 * <p>规则面：
 * <ol>
 *   <li>病害必须挂在存在的检查单上，区段随检查单带出</li>
 *   <li>病害里程必须落在所属区段里程区间内</li>
 *   <li>发现日期不早于检查日期</li>
 *   <li>已销号病害不可重复销号；销号必须填整治措施</li>
 * </ol>
 */
@Service
public class DefectService {

    private final DefectRepository defectRepository;
    private final InspectionRepository inspectionRepository;
    private final SectionRepository sectionRepository;
    private final ViewSupport viewSupport;
    private final HandoverService handoverService;

    public DefectService(DefectRepository defectRepository,
                         InspectionRepository inspectionRepository,
                         SectionRepository sectionRepository,
                         ViewSupport viewSupport,
                         HandoverService handoverService) {
        this.defectRepository = defectRepository;
        this.inspectionRepository = inspectionRepository;
        this.sectionRepository = sectionRepository;
        this.viewSupport = viewSupport;
        this.handoverService = handoverService;
    }

    /** 病害清单：可按区段、严重等级、是否销号筛选 */
    @Transactional(readOnly = true)
    public List<Views.DefectView> list(Long sectionId, String severity, Boolean closed) {
        List<Defect> defects;
        if (sectionId != null) {
            defects = defectRepository.findAllBySectionIdOrderByMileageAsc(sectionId);
        } else if (severity != null && !severity.isBlank()) {
            defects = defectRepository.findAllBySeverityOrderByFoundDateAsc(severity);
        } else if (closed != null) {
            defects = defectRepository.findAllByClosedOrderByFoundDateAsc(closed);
        } else {
            defects = defectRepository.findAllByOrderByFoundDateDesc();
        }
        List<Views.DefectView> list = new ArrayList<>();
        for (Defect defect : defects) {
            if (severity != null && !severity.isBlank() && !severity.equals(defect.getSeverity())) {
                continue;
            }
            if (closed != null && !closed.equals(Boolean.TRUE.equals(defect.getClosed()))) {
                continue;
            }
            list.add(viewSupport.toView(defect));
        }
        return list;
    }

    @Transactional
    public Views.DefectView create(DefectDtos.DefectSaveReq req) {
        Inspection inspection = req.getInspectionId() == null
                ? null
                : inspectionRepository.findById(req.getInspectionId()).orElse(null);
        if (inspection == null) {
            throw new BizException("检查单不存在：id=" + req.getInspectionId());
        }
        Section section = sectionRepository.findById(inspection.getSectionId())
                .orElseThrow(() -> new BizException("检查单所属区段不存在"));
        if (!WorksDict.isValidDefectType(req.getDefectType())) {
            throw new BizException("病害类型只能是 裂纹/磨耗/沉降/几何超限/翻浆冒泥/螺栓松动");
        }
        if (!WorksDict.isValidDefectLevel(req.getSeverity())) {
            throw new BizException("严重等级只能是 LIGHT 轻 / MIDDLE 中 / HEAVY 重");
        }
        BigDecimal mileage = viewSupport.mileageOrZero(req.getMileage());
        if (mileage.compareTo(section.getStartMile()) < 0 || mileage.compareTo(section.getEndMile()) > 0) {
            throw new BizException("病害里程 " + WorksDict.mileageText(mileage) + " 不在区段 "
                    + section.getSectionNo() + " 的里程区间内");
        }
        LocalDate foundDate = req.getFoundDate() == null ? LocalDate.now() : req.getFoundDate();
        if (foundDate.isBefore(inspection.getInspectDate())) {
            throw new BizException("病害发现日期不能早于检查日期 " + inspection.getInspectDate());
        }
        Defect defect = new Defect();
        defect.setInspectionId(inspection.getId());
        defect.setSectionId(section.getId());
        defect.setDefectType(req.getDefectType());
        defect.setSeverity(req.getSeverity());
        defect.setMileage(mileage);
        defect.setDescription(req.getDescription().trim());
        defect.setFoundDate(foundDate);
        defect.setClosed(false);
        Defect saved = defectRepository.save(defect);
        // 新登记（重级）病害改变了区段未销号重级病害清单：在途交接申请即时失效
        handoverService.invalidateStaleApplications();
        return viewSupport.toView(saved);
    }

    /** 病害销号 */
    @Transactional
    public Views.DefectView close(Long id, DefectDtos.DefectCloseReq req) {
        Defect defect = defectRepository.findById(id)
                .orElseThrow(() -> new BizException("病害记录不存在：id=" + id));
        if (Boolean.TRUE.equals(defect.getClosed())) {
            throw new BizException("病害已于 " + defect.getCloseDate() + " 销号，不能重复销号");
        }
        String treatment = req == null || req.getTreatment() == null ? "" : req.getTreatment().trim();
        if (treatment.isEmpty()) {
            throw new BizException("销号必须填写整治措施");
        }
        LocalDate closeDate = req.getCloseDate() == null ? LocalDate.now() : req.getCloseDate();
        if (closeDate.isBefore(defect.getFoundDate())) {
            throw new BizException("销号日期不能早于发现日期 " + defect.getFoundDate());
        }
        defect.setClosed(true);
        defect.setCloseDate(closeDate);
        defect.setTreatment(treatment);
        Defect saved = defectRepository.save(defect);
        // 病害销号（尤其重级）改变了快照清单：在途交接申请即时失效
        handoverService.invalidateStaleApplications();
        return viewSupport.toView(saved);
    }
}
