package com.railway.works.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.railway.works.common.BizException;
import com.railway.works.domain.Defect;
import com.railway.works.domain.Handover;
import com.railway.works.domain.HandoverTimeline;
import com.railway.works.domain.Section;
import com.railway.works.domain.WorkOrder;
import com.railway.works.domain.WorksDict;
import com.railway.works.dto.HandoverDtos;
import com.railway.works.dto.Views;
import com.railway.works.repository.DefectRepository;
import com.railway.works.repository.HandoverRepository;
import com.railway.works.repository.HandoverTimelineRepository;
import com.railway.works.repository.SectionRepository;
import com.railway.works.repository.WorkOrderRepository;

/**
 * 封锁解除交接台。
 *
 * <p>业务口径：
 * <ol>
 *   <li>只有"已验收"作业能发起；发起人即施工负责人，同一作业单同时只允许一份进行中的申请。</li>
 *   <li>发起时逐项填写现场清障、人员撤离、机具撤出、线路复测，并给出恢复正常/继续限速/继续封锁建议。</li>
 *   <li>提交即固化作业状态、区段状态、未销号重级病害清单三份快照；复核完成前这些现场一旦变化，
 *       旧申请立即标记为"已失效"，页面展示变化项，必须基于最新现场重新提交，旧结论不得套用。</li>
 *   <li>复核人必须与施工负责人不是同一人；四项逐项确认，任一项退回即整体退回整改。</li>
 *   <li>区段仍有未销号重级病害时绝不能恢复正常；选择限速必须给出不高于设计时速的限速值和原因；
 *       没有重级病害也不自动放行，最终结论由复核人承担。</li>
 *   <li>申请有 待复核/已通过/已退回/已失效 四态，提交、退回、重提、结论、失效全部留痕。</li>
 *   <li>通过时在同一次事务里关闭作业单并按结论更新区段，任一失败整体回滚，杜绝半成品。</li>
 *   <li>乐观锁 + 状态前置校验：两名复核人并发只有一份结论生效，重复点击/网络重试不产生两次交接。</li>
 * </ol>
 */
@Service
public class HandoverService {

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMM");
    private static final String SYS_ACTOR = "系统";

    private final HandoverRepository handoverRepository;
    private final HandoverTimelineRepository timelineRepository;
    private final WorkOrderRepository workOrderRepository;
    private final SectionRepository sectionRepository;
    private final DefectRepository defectRepository;
    private final ViewSupport viewSupport;

    public HandoverService(HandoverRepository handoverRepository,
                           HandoverTimelineRepository timelineRepository,
                           WorkOrderRepository workOrderRepository,
                           SectionRepository sectionRepository,
                           DefectRepository defectRepository,
                           ViewSupport viewSupport) {
        this.handoverRepository = handoverRepository;
        this.timelineRepository = timelineRepository;
        this.workOrderRepository = workOrderRepository;
        this.sectionRepository = sectionRepository;
        this.defectRepository = defectRepository;
        this.viewSupport = viewSupport;
    }

    // ============================================================ 列表 / 详情

    /** 交接列表：支持按线路（落到其下属区段）、区段、交接状态筛选 */
    @Transactional(readOnly = true)
    public List<Views.HandoverView> list(Long lineId, Long sectionId, String status) {
        List<Long> sectionIds = null;
        if (lineId != null) {
            sectionIds = sectionRepository.findAllByLineIdOrderByStartMileAsc(lineId).stream()
                    .map(Section::getId).collect(Collectors.toList());
            if (sectionIds.isEmpty()) {
                return new ArrayList<>();
            }
        }
        List<Handover> rows;
        if (sectionId != null) {
            rows = status == null || status.isBlank()
                    ? handoverRepository.findAllBySectionIdOrderBySubmittedAtDesc(sectionId)
                    : handoverRepository.findAllBySectionIdAndStatusOrderBySubmittedAtDesc(sectionId, status);
        } else if (status != null && !status.isBlank()) {
            rows = handoverRepository.findAllByStatusOrderBySubmittedAtDesc(status);
        } else {
            rows = handoverRepository.findAllByOrderBySubmittedAtDesc();
        }
        List<Views.HandoverView> list = new ArrayList<>();
        for (Handover h : rows) {
            if (sectionIds != null && !sectionIds.contains(h.getSectionId())) {
                continue;
            }
            list.add(toListView(h));
        }
        return list;
    }

    /** 可发起交接的作业单：已验收且当前没有进行中申请 */
    @Transactional(readOnly = true)
    public List<Views.WorkOrderView> eligibleOrders() {
        List<Views.WorkOrderView> list = new ArrayList<>();
        for (WorkOrder order : workOrderRepository.findAllByOrderByWindowDateAscStartTimeAsc()) {
            if (!"CHECKED".equals(order.getStatus())) {
                continue;
            }
            if (handoverRepository.countByWorkOrderIdAndStatus(order.getId(), "PENDING") > 0
                    || handoverRepository.countByWorkOrderIdAndStatus(order.getId(), "REJECTED") > 0) {
                continue;
            }
            list.add(viewSupport.toView(order));
        }
        return list;
    }

    @Transactional(readOnly = true)
    public Views.HandoverView detail(Long id) {
        Handover handover = mustGet(id);
        Views.HandoverView view = toFullView(handover);
        // 重提链路：沿 reapplyOfId 上溯到最早一张，再向下展开整条链
        Long rootId = handover.getId();
        Handover cursor = handover;
        while (cursor.getReapplyOfId() != null) {
            Long parentId = cursor.getReapplyOfId();
            Handover parent = handoverRepository.findById(parentId).orElse(null);
            if (parent == null) {
                break;
            }
            rootId = parentId;
            cursor = parent;
        }
        List<Handover> chain = new ArrayList<>();
        collectChain(rootId, chain);
        List<Views.HandoverView> chainViews = new ArrayList<>();
        for (Handover c : chain) {
            chainViews.add(toListView(c));
        }
        view.setChain(chainViews);
        return view;
    }

    private void collectChain(Long id, List<Handover> out) {
        handoverRepository.findById(id).ifPresent(out::add);
        for (Handover child : handoverRepository.findAllByReapplyOfIdOrderBySubmittedAtAsc(id)) {
            collectChain(child.getId(), out);
        }
    }

    // ============================================================ 发起 / 重提

    @Transactional
    public Views.HandoverView submit(HandoverDtos.HandoverSubmitReq req) {
        WorkOrder order = workOrderRepository.findById(req.getWorkOrderId())
                .orElseThrow(() -> new BizException("养修作业单不存在：id=" + req.getWorkOrderId()));
        if (!"CHECKED".equals(order.getStatus())) {
            throw new BizException("作业单 " + order.getOrderNo() + " 当前为「"
                    + WorksDict.workStatusName(order.getStatus()) + "」，只有已验收作业才能发起封锁解除交接");
        }
        String applicant = trim(req.getApplicant());
        if (applicant.isEmpty()) {
            throw new BizException("发起人（施工负责人）不能为空");
        }
        if (!applicant.equals(order.getLeader())) {
            throw new BizException("封锁解除申请必须由该作业的施工负责人 " + order.getLeader() + " 发起");
        }

        // 重提：旧单必须存在且属于本作业、处于已退回/已失效
        Handover old = null;
        if (req.getReapplyOfId() != null) {
            old = handoverRepository.findById(req.getReapplyOfId())
                    .orElseThrow(() -> new BizException("被重提的旧申请不存在：id=" + req.getReapplyOfId()));
            if (!old.getWorkOrderId().equals(order.getId())) {
                throw new BizException("重提申请与原申请不是同一张作业单");
            }
            if (!"REJECTED".equals(old.getStatus()) && !"EXPIRED".equals(old.getStatus())) {
                throw new BizException("只有已退回或已失效的申请才能重新提交");
            }
        }

        // 同一作业单同时只允许一份进行中申请（重提自身的旧单不算冲突）
        if (old == null) {
            if (handoverRepository.countByWorkOrderIdAndStatus(order.getId(), "PENDING") > 0
                    || handoverRepository.countByWorkOrderIdAndStatus(order.getId(), "REJECTED") > 0) {
                throw new BizException("作业单 " + order.getOrderNo() + " 已有一份待复核或已退回的交接申请，不能重复发起");
            }
        }

        // 现场四项逐项校验
        assertResult(req.getClearanceResult(), "现场清障");
        assertResult(req.getEvacuationResult(), "人员撤离");
        assertResult(req.getEquipmentResult(), "机具撤出");
        assertResult(req.getRetestResult(), "线路复测");
        String recommendation = trim(req.getRecommendation());
        if (!WorksDict.isValidSectionStatus(recommendation)) {
            throw new BizException("解除建议只能是 NORMAL 恢复正常 / SPEED_LIMIT 继续限速 / BLOCKED 继续封锁");
        }

        Section section = sectionRepository.findById(order.getSectionId())
                .orElseThrow(() -> new BizException("作业对应区段不存在"));
        List<Defect> heavyOpen = heavyOpenDefects(section.getId());

        // 恢复正常：四项必须全部合格，且区段不得仍有未销号重级病害
        if ("NORMAL".equals(recommendation)) {
            if (!"OK".equals(req.getClearanceResult()) || !"OK".equals(req.getEvacuationResult())
                    || !"OK".equals(req.getEquipmentResult()) || !"OK".equals(req.getRetestResult())) {
                throw new BizException("现场四项存在不合格项，不能建议恢复正常");
            }
            if (!heavyOpen.isEmpty()) {
                throw new BizException("区段 " + section.getSectionNo() + " 仍有 " + heavyOpen.size()
                        + " 处未销号重级病害，绝不能恢复正常，只能继续限速或继续封锁");
            }
        }
        Integer speedLimit = null;
        String limitReason = trim(req.getLimitReason());
        if ("SPEED_LIMIT".equals(recommendation)) {
            speedLimit = req.getSpeedLimit();
            if (speedLimit == null || speedLimit <= 0 || speedLimit > section.getDesignSpeed()) {
                throw new BizException("继续限速必须给出不高于设计时速 " + section.getDesignSpeed()
                        + " km/h 的限速值");
            }
            if (limitReason.isEmpty()) {
                throw new BizException("继续限速必须填写限速原因");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        Handover h = new Handover();
        h.setHandoverNo(nextNo(now));
        h.setWorkOrderId(order.getId());
        h.setSectionId(section.getId());
        h.setApplicant(applicant);
        h.setStatus("PENDING");
        h.setClearanceResult(req.getClearanceResult());
        h.setEvacuationResult(req.getEvacuationResult());
        h.setEquipmentResult(req.getEquipmentResult());
        h.setRetestResult(req.getRetestResult());
        h.setClearanceRemark(trim(req.getClearanceRemark()));
        h.setEvacuationRemark(trim(req.getEvacuationRemark()));
        h.setEquipmentRemark(trim(req.getEquipmentRemark()));
        h.setRetestRemark(trim(req.getRetestRemark()));
        h.setApplyNote(trim(req.getApplyNote()));
        h.setRecommendation(recommendation);
        h.setSpeedLimit(speedLimit);
        h.setLimitReason(limitReason.isEmpty() ? null : limitReason);
        // 固化现场快照
        h.setSnapWorkStatus(order.getStatus());
        h.setSnapSectionStatus(section.getStatus());
        h.setSnapSectionSpeed(section.getSpeedLimit());
        h.setSnapHeavyDefectIds(heavyOpen.stream().map(d -> String.valueOf(d.getId()))
                .collect(Collectors.joining(",")));
        h.setReapplyOfId(old == null ? null : old.getId());
        h.setSubmittedAt(now);
        h.setCreatedAt(now);
        Handover saved;
        try {
            // 重提时先把旧单从"进行中"释放为历史态 SUPERSEDED，腾出唯一约束名额
            if (old != null) {
                old.setStatus("SUPERSEDED");
                handoverRepository.saveAndFlush(old);
            }
            saved = handoverRepository.saveAndFlush(h);
        } catch (DataIntegrityViolationException e) {
            // uk_handover_active：并发重复发起时，同一作业单只有第一份进行中申请能落库
            throw new BizException("作业单 " + order.getOrderNo() + " 已存在一份待复核或已退回的交接申请，不能重复发起");
        }
        String firstAction = old == null ? "SUBMIT" : "RESUBMIT";
        addTimeline(saved.getId(), firstAction, applicant,
                old == null ? ("发起封锁解除申请，建议：" + WorksDict.sectionStatusName(recommendation))
                        : ("基于最新现场整改重提，建议：" + WorksDict.sectionStatusName(recommendation)
                                + (trim(req.getApplyNote()).isEmpty() ? "" : "；" + trim(req.getApplyNote()))),
                now);
        return toFullView(saved);
    }

    // ============================================================ 复核

    @Transactional
    public Views.HandoverView review(Long id, HandoverDtos.HandoverReviewReq req) {
        Handover handover = handoverRepository.findById(id)
                .orElseThrow(() -> new BizException("交接申请不存在：id=" + id));

        String reviewer = trim(req.getReviewer());
        if (reviewer.isEmpty()) {
            throw new BizException("复核人不能为空");
        }
        if (reviewer.equals(handover.getApplicant())) {
            throw new BizException("复核人必须与施工负责人（发起人 " + handover.getApplicant() + "）不是同一人");
        }
        String action = trim(req.getAction());
        if (!"APPROVE".equals(action) && !"RETURN".equals(action)) {
            throw new BizException("复核动作只能是 APPROVE 通过或 RETURN 退回整改");
        }

        // 取锁行内再判状态：并发/重试时较晚到达的一方只能看到已变化的申请
        // version 字段由 JPA 乐观锁守护，两个并发结论只有一份能写成功
        if ("APPROVED".equals(handover.getStatus())) {
            throw new BizException("该申请已由 " + nz(handover.getReviewer()) + " 于 "
                    + handover.getReviewedAt() + " 复核通过，结论已生效，不能重复交接");
        }
        if ("REJECTED".equals(handover.getStatus())) {
            throw new BizException("该申请已被退回整改，不能重复复核；请等待施工负责人重提");
        }
        if ("EXPIRED".equals(handover.getStatus())) {
            throw new BizException("现场已发生变化，该申请已失效，不能套用旧结论；请刷新后要求基于最新现场重新提交");
        }

        // 复核落键前最后一次失效检测：与快照不一致直接作废本次操作
        List<Views.HandoverChangeView> changes = diff(handover);
        if (!changes.isEmpty()) {
            String reason = changes.stream().map(Views.HandoverChangeView::getDetail)
                    .collect(Collectors.joining("；"));
            expire(handover, reason);
            throw new BizException("申请已失效（" + reason + "），请基于最新现场重新提交，旧结论不能继续套用");
        }

        LocalDateTime now = LocalDateTime.now();
        handover.setReviewer(reviewer);

        if ("RETURN".equals(action)) {
            boolean hasReturn = "RETURN".equals(req.getClearanceCheck())
                    || "RETURN".equals(req.getEvacuationCheck())
                    || "RETURN".equals(req.getEquipmentCheck())
                    || "RETURN".equals(req.getRetestCheck());
            if (!hasReturn) {
                throw new BizException("退回整改必须至少在一个复核项上选择退回");
            }
            handover.setClearanceCheck(checkOf(req.getClearanceCheck()));
            handover.setEvacuationCheck(checkOf(req.getEvacuationCheck()));
            handover.setEquipmentCheck(checkOf(req.getEquipmentCheck()));
            handover.setRetestCheck(checkOf(req.getRetestCheck()));
            String note = trim(req.getReviewNote());
            if (note.isEmpty()) {
                throw new BizException("退回整改必须填写整改说明");
            }
            handover.setReviewNote(note);
            handover.setStatus("REJECTED");
            handover.setReviewedAt(now);
            try {
                handoverRepository.saveAndFlush(handover);
            } catch (ObjectOptimisticLockingFailureException e) {
                throw new BizException("申请状态已被其他复核操作改变，请刷新后查看最新结论");
            }
            addTimeline(handover.getId(), "RETURN", reviewer, note, now);
            return toFullView(handover);
        }

        // ---- 通过：四项逐项确认必须全部 CONFIRM ----
        if (!"CONFIRM".equals(req.getClearanceCheck()) || !"CONFIRM".equals(req.getEvacuationCheck())
                || !"CONFIRM".equals(req.getEquipmentCheck()) || !"CONFIRM".equals(req.getRetestCheck())) {
            throw new BizException("复核通过要求现场清障、人员撤离、机具撤出、线路复测四项全部逐项确认合格");
        }
        handover.setClearanceCheck("CONFIRM");
        handover.setEvacuationCheck("CONFIRM");
        handover.setEquipmentCheck("CONFIRM");
        handover.setRetestCheck("CONFIRM");

        String conclusion = trim(req.getConclusion());
        if (!WorksDict.isValidSectionStatus(conclusion)) {
            throw new BizException("最终结论只能是 NORMAL 恢复正常 / SPEED_LIMIT 继续限速 / BLOCKED 继续封锁");
        }
        Section section = sectionRepository.findById(handover.getSectionId())
                .orElseThrow(() -> new BizException("对应区段不存在"));
        List<Defect> heavyOpen = heavyOpenDefects(section.getId());
        if ("NORMAL".equals(conclusion) && !heavyOpen.isEmpty()) {
            throw new BizException("区段 " + section.getSectionNo() + " 仍有 " + heavyOpen.size()
                    + " 处未销号重级病害，绝不能恢复正常");
        }
        Integer finalSpeed = null;
        String finalReason = trim(req.getConclusionLimitReason());
        if ("SPEED_LIMIT".equals(conclusion)) {
            finalSpeed = req.getConclusionSpeedLimit();
            if (finalSpeed == null || finalSpeed <= 0 || finalSpeed > section.getDesignSpeed()) {
                throw new BizException("限速结论必须给出不高于设计时速 " + section.getDesignSpeed()
                        + " km/h 的限速值");
            }
            if (finalReason.isEmpty()) {
                throw new BizException("限速结论必须填写限速原因");
            }
        }

        handover.setConclusion(conclusion);
        handover.setConclusionSpeedLimit(finalSpeed);
        handover.setConclusionLimitReason(finalReason.isEmpty() ? null : finalReason);
        handover.setReviewNote(trim(req.getReviewNote()));
        handover.setStatus("APPROVED");
        handover.setReviewedAt(now);

        // 原子交接：关闭作业单 + 更新区段，与申请落结论同一事务，任一失败整体回滚
        WorkOrder order = workOrderRepository.findById(handover.getWorkOrderId())
                .orElseThrow(() -> new BizException("养修作业单不存在"));
        if (!"CHECKED".equals(order.getStatus())) {
            throw new BizException("作业单状态已不是已验收（当前："
                    + WorksDict.workStatusName(order.getStatus()) + "），本次交接作废，请核实后重新发起");
        }
        order.setStatus("CLOSED");
        if ("NORMAL".equals(conclusion)) {
            section.setStatus("NORMAL");
            section.setSpeedLimit(section.getDesignSpeed());
        } else if ("SPEED_LIMIT".equals(conclusion)) {
            section.setStatus("SPEED_LIMIT");
            section.setSpeedLimit(finalSpeed);
        } else {
            section.setStatus("BLOCKED");
            section.setSpeedLimit(0);
        }

        try {
            handoverRepository.saveAndFlush(handover);   // 乐观锁：并发的第二份结论在此处失败回滚
            workOrderRepository.save(order);     // 作业销号
            sectionRepository.save(section);     // 区段更新
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BizException("该申请已被另一名复核人先行处理，只有一份结论生效，请刷新查看");
        }
        addTimeline(handover.getId(), "APPROVE", reviewer,
                "复核通过，交接完成；作业单 " + order.getOrderNo() + " 已销号，区段更新为「"
                        + WorksDict.sectionStatusName(conclusion) + "」"
                        + ("SPEED_LIMIT".equals(conclusion) ? "，限速 " + finalSpeed + " km/h" : ""),
                now);
        return toFullView(handover);
    }

    // ============================================================ 失效检测

    /**
     * 复核窗口内的申请与最新现场比对：作业状态、区段状态（含限速值）、
     * 快照中重级病害的销号情况或区段新出现未销号重级病害，任一变化即标记失效。
     * 在作业单推进、区段状态变更、病害销号/新增等事务内同步调用。
     */
    @Transactional
    public void invalidateStaleApplications() {
        for (Handover h : handoverRepository.findAllActiveForCheck()) {
            List<Views.HandoverChangeView> changes = diff(h);
            if (!changes.isEmpty()) {
                String reason = changes.stream().map(Views.HandoverChangeView::getDetail)
                        .collect(Collectors.joining("；"));
                expire(h, reason);
            }
        }
    }

    private void expire(Handover h, String reason) {
        h.setStatus("EXPIRED");
        h.setExpiredReason(reason);
        handoverRepository.save(h);
        addTimeline(h.getId(), "EXPIRE", SYS_ACTOR,
                "复核完成前现场发生变化，申请自动失效：" + reason + "；须基于最新现场重新提交",
                LocalDateTime.now());
    }

    /** 快照 vs 当前现场的逐项差异（不写库） */
    private List<Views.HandoverChangeView> diff(Handover h) {
        List<Views.HandoverChangeView> changes = new ArrayList<>();

        WorkOrder order = workOrderRepository.findById(h.getWorkOrderId()).orElse(null);
        if (order == null) {
            change(changes, "WORK_STATUS", "作业单已被删除");
        } else if (!nz(order.getStatus()).equals(nz(h.getSnapWorkStatus()))) {
            change(changes, "WORK_STATUS", "作业单状态由「" + WorksDict.workStatusName(h.getSnapWorkStatus())
                    + "」变为「" + WorksDict.workStatusName(order.getStatus()) + "」");
        }

        Section section = sectionRepository.findById(h.getSectionId()).orElse(null);
        if (section == null) {
            change(changes, "SECTION_STATUS", "区段已被删除");
        } else {
            if (!nz(section.getStatus()).equals(nz(h.getSnapSectionStatus()))) {
                change(changes, "SECTION_STATUS", "区段状态由「" + WorksDict.sectionStatusName(h.getSnapSectionStatus())
                        + "」变为「" + WorksDict.sectionStatusName(section.getStatus()) + "」");
            } else if (!txt(section.getSpeedLimit()).equals(txt(h.getSnapSectionSpeed()))) {
                change(changes, "SECTION_STATUS", "区段限速值由 " + h.getSnapSectionSpeed()
                        + " km/h 变为 " + section.getSpeedLimit() + " km/h");
            }
        }

        // 重级病害：快照集合（当时未销号重级）与当前集合（现在未销号重级）按 id 比对
        TreeSet<Long> snapIds = parseIds(h.getSnapHeavyDefectIds());
        TreeSet<Long> nowIds = new TreeSet<>();
        if (section != null) {
            for (Defect d : heavyOpenDefects(section.getId())) {
                nowIds.add(d.getId());
            }
        }
        for (Long closedId : new ArrayList<>(snapIds)) {
            if (!nowIds.contains(closedId)) {
                Defect d = defectRepository.findById(closedId).orElse(null);
                if (d != null && Boolean.TRUE.equals(d.getClosed())) {
                    change(changes, "DEFECT", "重级病害 " + WorksDict.mileageText(d.getMileage())
                            + "「" + d.getDescription() + "」已于 " + d.getCloseDate() + " 销号");
                } else if (d != null) {
                    change(changes, "DEFECT", "重级病害 " + WorksDict.mileageText(d.getMileage())
                            + "「" + d.getDescription() + "」已不在未销号清单");
                } else {
                    change(changes, "DEFECT", "快照中的重级病害 #" + closedId + " 已不存在");
                }
            }
        }
        for (Long newId : nowIds) {
            if (!snapIds.contains(newId)) {
                Defect d = defectRepository.findById(newId).orElse(null);
                change(changes, "DEFECT", "区段新出现未销号重级病害 "
                        + (d == null ? ("#" + newId)
                                : WorksDict.mileageText(d.getMileage()) + "「" + d.getDescription() + "」"));
            }
        }
        return changes;
    }

    private void change(List<Views.HandoverChangeView> list, String type, String detail) {
        Views.HandoverChangeView c = new Views.HandoverChangeView();
        c.setType(type);
        c.setTypeName("WORK_STATUS".equals(type) ? "作业状态"
                : "SECTION_STATUS".equals(type) ? "区段状态" : "重级病害");
        c.setDetail(detail);
        list.add(c);
    }

    // ============================================================ 视图装配

    private Views.HandoverView toListView(Handover h) {
        Views.HandoverView v = new Views.HandoverView();
        fillBase(v, h);
        v.setSubmittedAt(h.getSubmittedAt());
        v.setReviewedAt(h.getReviewedAt());
        v.setRecommendation(h.getRecommendation());
        v.setRecommendationName(WorksDict.sectionStatusName(h.getRecommendation()));
        v.setSpeedLimit(h.getSpeedLimit());
        v.setLimitReason(h.getLimitReason());
        v.setConclusion(h.getConclusion());
        v.setConclusionName(WorksDict.sectionStatusName(h.getConclusion()));
        v.setConclusionSpeedLimit(h.getConclusionSpeedLimit());
        v.setExpiredReason(h.getExpiredReason());
        v.setReapplyOfId(h.getReapplyOfId());
        return v;
    }

    private Views.HandoverView toFullView(Handover h) {
        Views.HandoverView v = toListView(h);
        v.setApplyNote(h.getApplyNote());
        v.setReviewNote(h.getReviewNote());
        v.setConclusionLimitReason(h.getConclusionLimitReason());
        v.setVersion(h.getVersion());

        v.setClearanceResult(h.getClearanceResult());
        v.setClearanceResultName(WorksDict.checkResultName(h.getClearanceResult()));
        v.setEvacuationResult(h.getEvacuationResult());
        v.setEvacuationResultName(WorksDict.checkResultName(h.getEvacuationResult()));
        v.setEquipmentResult(h.getEquipmentResult());
        v.setEquipmentResultName(WorksDict.checkResultName(h.getEquipmentResult()));
        v.setRetestResult(h.getRetestResult());
        v.setRetestResultName(WorksDict.checkResultName(h.getRetestResult()));
        v.setClearanceRemark(h.getClearanceRemark());
        v.setEvacuationRemark(h.getEvacuationRemark());
        v.setEquipmentRemark(h.getEquipmentRemark());
        v.setRetestRemark(h.getRetestRemark());

        v.setClearanceCheck(h.getClearanceCheck());
        v.setClearanceCheckName(WorksDict.reviewCheckName(h.getClearanceCheck()));
        v.setEvacuationCheck(h.getEvacuationCheck());
        v.setEvacuationCheckName(WorksDict.reviewCheckName(h.getEvacuationCheck()));
        v.setEquipmentCheck(h.getEquipmentCheck());
        v.setEquipmentCheckName(WorksDict.reviewCheckName(h.getEquipmentCheck()));
        v.setRetestCheck(h.getRetestCheck());
        v.setRetestCheckName(WorksDict.reviewCheckName(h.getRetestCheck()));

        v.setSnapWorkStatus(h.getSnapWorkStatus());
        v.setSnapWorkStatusName(WorksDict.workStatusName(h.getSnapWorkStatus()));
        v.setSnapSectionStatus(h.getSnapSectionStatus());
        v.setSnapSectionStatusName(WorksDict.sectionStatusName(h.getSnapSectionStatus()));
        v.setSnapSectionSpeed(h.getSnapSectionSpeed());
        List<Views.DefectView> snapDefects = new ArrayList<>();
        for (Long defectId : parseIds(h.getSnapHeavyDefectIds())) {
            defectRepository.findById(defectId).ifPresent(d -> snapDefects.add(viewSupport.toView(d)));
        }
        v.setSnapHeavyDefects(snapDefects);

        workOrderRepository.findById(h.getWorkOrderId()).ifPresent(o -> {
            v.setCurrentWorkStatus(o.getStatus());
            v.setCurrentWorkStatusName(WorksDict.workStatusName(o.getStatus()));
        });
        sectionRepository.findById(h.getSectionId()).ifPresent(s -> {
            v.setCurrentSectionStatus(s.getStatus());
            v.setCurrentSectionStatusName(WorksDict.sectionStatusName(s.getStatus()));
            v.setCurrentSectionSpeed(s.getSpeedLimit());
        });
        List<Views.DefectView> currentDefects = new ArrayList<>();
        for (Defect d : heavyOpenDefects(h.getSectionId())) {
            currentDefects.add(viewSupport.toView(d));
        }
        v.setCurrentHeavyDefects(currentDefects);
        v.setChanges(diff(h));

        List<Views.HandoverTimelineView> traces = new ArrayList<>();
        for (HandoverTimeline t : timelineRepository.findAllByHandoverIdOrderByCreatedAtAsc(h.getId())) {
            Views.HandoverTimelineView tv = new Views.HandoverTimelineView();
            tv.setId(t.getId());
            tv.setAction(t.getAction());
            tv.setActionName(WorksDict.handoverActionName(t.getAction()));
            tv.setActor(t.getActor());
            tv.setNote(t.getNote());
            tv.setCreatedAt(t.getCreatedAt());
            traces.add(tv);
        }
        v.setTimeline(traces);
        return v;
    }

    private void fillBase(Views.HandoverView v, Handover h) {
        v.setId(h.getId());
        v.setHandoverNo(h.getHandoverNo());
        v.setWorkOrderId(h.getWorkOrderId());
        v.setSectionId(h.getSectionId());
        v.setApplicant(h.getApplicant());
        v.setReviewer(h.getReviewer());
        v.setStatus(h.getStatus());
        v.setStatusName(WorksDict.handoverStatusName(h.getStatus()));
        WorkOrder order = workOrderRepository.findById(h.getWorkOrderId()).orElse(null);
        if (order != null) {
            v.setOrderNo(order.getOrderNo());
            v.setWorkItemName(WorksDict.workItemName(order.getWorkItem()));
        }
        Section section = sectionRepository.findById(h.getSectionId()).orElse(null);
        if (section != null) {
            v.setSectionNo(section.getSectionNo());
            v.setDesignSpeed(section.getDesignSpeed());
            v.setLineId(section.getLineId());
            v.setLineName(viewSupport.lineName(section.getLineId()));
        }
    }

    // ============================================================ 辅助

    private Handover mustGet(Long id) {
        return handoverRepository.findById(id)
                .orElseThrow(() -> new BizException("交接申请不存在：id=" + id));
    }

    private List<Defect> heavyOpenDefects(Long sectionId) {
        List<Defect> list = new ArrayList<>();
        for (Defect d : defectRepository.findAllBySectionIdOrderByMileageAsc(sectionId)) {
            if ("HEAVY".equals(d.getSeverity()) && !Boolean.TRUE.equals(d.getClosed())) {
                list.add(d);
            }
        }
        return list;
    }

    private TreeSet<Long> parseIds(String csv) {
        TreeSet<Long> ids = new TreeSet<>();
        if (csv == null || csv.isBlank()) {
            return ids;
        }
        for (String part : csv.split(",")) {
            String p = part.trim();
            if (!p.isEmpty()) {
                ids.add(Long.valueOf(p));
            }
        }
        return ids;
    }

    private void assertResult(String code, String label) {
        if (!WorksDict.isValidCheckResult(code)) {
            throw new BizException(label + "结果必须逐项填写为 OK 合格 或 NG 不合格");
        }
    }

    private String checkOf(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        if (!WorksDict.isValidReviewCheck(code)) {
            throw new BizException("复核项只能是 CONFIRM 确认合格 或 RETURN 退回整改");
        }
        return code;
    }

    private void addTimeline(Long handoverId, String action, String actor, String note, LocalDateTime at) {
        HandoverTimeline t = new HandoverTimeline();
        t.setHandoverId(handoverId);
        t.setAction(action);
        t.setActor(actor);
        t.setNote(note);
        t.setCreatedAt(at);
        timelineRepository.save(t);
    }

    /** 申请编号：HO-年月-4 位序号，序号按当月已有申请数 +1（同事务内串行发号） */
    private String nextNo(LocalDateTime now) {
        String prefix = "HO-" + now.format(NO_FMT) + "-";
        long max = 0;
        for (Handover h : handoverRepository.findAll()) {
            if (h.getHandoverNo() != null && h.getHandoverNo().startsWith(prefix)) {
                String tail = h.getHandoverNo().substring(prefix.length());
                try {
                    max = Math.max(max, Long.parseLong(tail));
                } catch (NumberFormatException ignore) {
                    // 非数字尾巴忽略
                }
            }
        }
        String no;
        do {
            max++;
            no = prefix + String.format("%04d", max);
        } while (handoverRepository.existsByHandoverNo(no));
        return no;
    }

    private String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }

    private String txt(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    /** 供作业页等场景按作业单取进行中申请（不抛异常） */
    @Transactional(readOnly = true)
    public Map<Long, Handover> activeByWorkOrders(List<Long> workOrderIds) {
        Map<Long, Handover> map = new LinkedHashMap<>();
        if (workOrderIds == null || workOrderIds.isEmpty()) {
            return map;
        }
        for (Handover h : handoverRepository.findAllByOrderBySubmittedAtDesc()) {
            if (workOrderIds.contains(h.getWorkOrderId())
                    && ("PENDING".equals(h.getStatus()) || "REJECTED".equals(h.getStatus()))
                    && !map.containsKey(h.getWorkOrderId())) {
                map.put(h.getWorkOrderId(), h);
            }
        }
        return map;
    }
}
