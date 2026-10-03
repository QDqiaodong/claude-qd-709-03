package com.railway.works.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.railway.works.common.BizException;
import com.railway.works.domain.Defect;
import com.railway.works.domain.Line;
import com.railway.works.domain.Section;
import com.railway.works.domain.WorksDict;
import com.railway.works.dto.LineDtos;
import com.railway.works.dto.Views;
import com.railway.works.repository.DefectRepository;
import com.railway.works.repository.LineRepository;
import com.railway.works.repository.SectionRepository;

/**
 * 模块一：线路。
 *
 * <p>规则面：
 * <ol>
 *   <li>线路名称唯一</li>
 *   <li>运营里程必须大于 0</li>
 *   <li>看板 board 汇总一条线路下的全部区段与病害，供页面画里程条</li>
 * </ol>
 */
@Service
public class LineService {

    private final LineRepository lineRepository;
    private final SectionRepository sectionRepository;
    private final DefectRepository defectRepository;
    private final ViewSupport viewSupport;

    public LineService(LineRepository lineRepository,
                       SectionRepository sectionRepository,
                       DefectRepository defectRepository,
                       ViewSupport viewSupport) {
        this.lineRepository = lineRepository;
        this.sectionRepository = sectionRepository;
        this.defectRepository = defectRepository;
        this.viewSupport = viewSupport;
    }

    @Transactional(readOnly = true)
    public List<Views.LineView> list() {
        List<Views.LineView> list = new ArrayList<>();
        for (Line line : lineRepository.findAllByOrderByIdAsc()) {
            list.add(viewSupport.toView(line));
        }
        return list;
    }

    @Transactional
    public Views.LineView create(LineDtos.LineSaveReq req) {
        String name = req.getName() == null ? "" : req.getName().trim();
        if (lineRepository.existsByName(name)) {
            throw new BizException("线路 " + name + " 已存在，不能重复建档");
        }
        BigDecimal mileage = viewSupport.mileageOrZero(req.getMileageKm());
        if (mileage.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("运营里程必须大于 0");
        }
        Line line = new Line();
        line.setName(name);
        line.setStartStation(req.getStartStation() == null ? "" : req.getStartStation().trim());
        line.setEndStation(req.getEndStation() == null ? "" : req.getEndStation().trim());
        line.setMileageKm(mileage);
        line.setDirection("DOWN".equals(req.getDirection()) ? "DOWN" : "UP");
        line.setBureau(req.getBureau());
        return viewSupport.toView(lineRepository.save(line));
    }

    /** 线路里程看板：区段按里程排序 + 全部病害 + 里程范围 */
    @Transactional(readOnly = true)
    public Views.BoardView board(Long lineId) {
        Line line = lineRepository.findById(lineId)
                .orElseThrow(() -> new BizException("线路不存在：id=" + lineId));
        List<Section> sections = sectionRepository.findAllByLineIdOrderByStartMileAsc(lineId);
        if (sections.isEmpty()) {
            throw new BizException("线路 " + line.getName() + " 还没有划分区段，请先建区段");
        }
        List<Views.SectionView> sectionViews = new ArrayList<>();
        List<Views.DefectView> defectViews = new ArrayList<>();
        BigDecimal rangeStart = sections.get(0).getStartMile();
        BigDecimal rangeEnd = sections.get(0).getEndMile();
        for (Section section : sections) {
            sectionViews.add(viewSupport.toView(section));
            if (section.getStartMile().compareTo(rangeStart) < 0) {
                rangeStart = section.getStartMile();
            }
            if (section.getEndMile().compareTo(rangeEnd) > 0) {
                rangeEnd = section.getEndMile();
            }
            for (Defect defect : defectRepository.findAllBySectionIdOrderByMileageAsc(section.getId())) {
                defectViews.add(viewSupport.toView(defect));
            }
        }
        Views.BoardView board = new Views.BoardView();
        board.setLine(viewSupport.toView(line));
        board.setSections(sectionViews);
        board.setDefects(defectViews);
        board.setRangeStart(rangeStart);
        board.setRangeEnd(rangeEnd);
        board.setRangeStartText(WorksDict.mileageText(rangeStart));
        board.setRangeEndText(WorksDict.mileageText(rangeEnd));
        return board;
    }
}
