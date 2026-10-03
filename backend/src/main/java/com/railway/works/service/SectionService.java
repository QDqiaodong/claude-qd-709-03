package com.railway.works.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.railway.works.common.BizException;
import com.railway.works.domain.Line;
import com.railway.works.domain.Section;
import com.railway.works.domain.WorksDict;
import com.railway.works.dto.SectionDtos;
import com.railway.works.dto.Views;
import com.railway.works.repository.LineRepository;
import com.railway.works.repository.SectionRepository;

/**
 * 模块一：区段。
 *
 * <p>规则面：
 * <ol>
 *   <li>区段编号唯一</li>
 *   <li>里程终点必须大于起点</li>
 *   <li>区段不得超出所属线路的运营里程</li>
 *   <li>限速值不得高于设计时速；封锁施工时限速按 0 计</li>
 * </ol>
 */
@Service
public class SectionService {

    private final SectionRepository sectionRepository;
    private final LineRepository lineRepository;
    private final ViewSupport viewSupport;
    private final HandoverService handoverService;

    public SectionService(SectionRepository sectionRepository,
                          LineRepository lineRepository,
                          ViewSupport viewSupport,
                          HandoverService handoverService) {
        this.sectionRepository = sectionRepository;
        this.lineRepository = lineRepository;
        this.viewSupport = viewSupport;
        this.handoverService = handoverService;
    }

    @Transactional(readOnly = true)
    public List<Views.SectionView> list(Long lineId) {
        List<Section> sections = lineId == null
                ? sectionRepository.findAllByOrderByStartMileAsc()
                : sectionRepository.findAllByLineIdOrderByStartMileAsc(lineId);
        List<Views.SectionView> list = new ArrayList<>();
        for (Section section : sections) {
            list.add(viewSupport.toView(section));
        }
        return list;
    }

    @Transactional
    public Views.SectionView create(SectionDtos.SectionSaveReq req) {
        String sectionNo = req.getSectionNo() == null ? "" : req.getSectionNo().trim();
        if (sectionRepository.existsBySectionNo(sectionNo)) {
            throw new BizException("区段编号 " + sectionNo + " 已存在");
        }
        Long lineId = req.getLineId();
        Line line = lineId == null ? null : lineRepository.findById(lineId).orElse(null);
        if (line == null) {
            throw new BizException("请先选择区段所属的线路");
        }
        BigDecimal startMile = viewSupport.mileageOrZero(req.getStartMile());
        BigDecimal endMile = viewSupport.mileageOrZero(req.getEndMile());
        if (endMile.compareTo(startMile) <= 0) {
            throw new BizException("里程终点必须大于里程起点");
        }
        if (endMile.compareTo(line.getMileageKm()) > 0) {
            throw new BizException("区段终点 " + WorksDict.mileageText(endMile) + " 超出线路运营里程 "
                    + WorksDict.mileageText(line.getMileageKm()));
        }
        if (!WorksDict.isValidRailType(req.getRailType())) {
            throw new BizException("轨型只能是 60KG 或 75KG");
        }
        if (!WorksDict.isValidBedType(req.getBedType())) {
            throw new BizException("道床类型只能是有砟 BALLAST 或无砟 SLAB");
        }
        Integer designSpeed = req.getDesignSpeed() == null ? 0 : req.getDesignSpeed();
        if (designSpeed <= 0) {
            throw new BizException("设计时速必须大于 0");
        }
        Section section = new Section();
        section.setLineId(line.getId());
        section.setSectionNo(sectionNo);
        section.setStartMile(startMile);
        section.setEndMile(endMile);
        section.setRailType(req.getRailType());
        section.setBedType(req.getBedType());
        section.setDesignSpeed(designSpeed);
        section.setStatus("NORMAL");
        section.setSpeedLimit(designSpeed);
        section.setRemark(req.getRemark());
        return viewSupport.toView(sectionRepository.save(section));
    }

    /** 区段状态变更：正常 / 限速 / 封锁施工 */
    @Transactional
    public Views.SectionView setStatus(Long id, SectionDtos.SectionStatusReq req) {
        Section section = sectionRepository.findById(id)
                .orElseThrow(() -> new BizException("区段不存在：id=" + id));
        String status = req.getStatus() == null ? "" : req.getStatus().trim();
        if (!WorksDict.isValidSectionStatus(status)) {
            throw new BizException("区段状态只能是 NORMAL 正常 / SPEED_LIMIT 限速 / BLOCKED 封锁施工");
        }
        if ("SPEED_LIMIT".equals(status)) {
            Integer limit = req.getSpeedLimit() == null ? WorksDict.DEFAULT_SPEED_LIMIT : req.getSpeedLimit();
            if (limit <= 0 || limit > section.getDesignSpeed()) {
                throw new BizException("限速值需在 1 ~ " + section.getDesignSpeed() + " km/h 之间");
            }
            section.setSpeedLimit(limit);
        } else if ("BLOCKED".equals(status)) {
            section.setSpeedLimit(0);
        } else {
            section.setSpeedLimit(section.getDesignSpeed());
        }
        section.setStatus(status);
        Section saved = sectionRepository.save(section);
        // 区段状态/限速在复核完成前被手工调整：在途交接申请即时失效，旧结论不得套用
        handoverService.invalidateStaleApplications();
        return viewSupport.toView(saved);
    }
}
