package com.railway.works.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.railway.works.common.BizException;
import com.railway.works.domain.Device;
import com.railway.works.domain.RecheckBatch;
import com.railway.works.domain.RecheckItem;
import com.railway.works.domain.Section;
import com.railway.works.domain.WorksDict;
import com.railway.works.dto.RecheckDtos;
import com.railway.works.dto.Views;
import com.railway.works.repository.DeviceRepository;
import com.railway.works.repository.RecheckBatchRepository;
import com.railway.works.repository.RecheckItemRepository;
import com.railway.works.repository.SectionRepository;

/**
 * 模块六：定检复查批次。
 *
 * <p>业务口径：
 * <ol>
 *   <li>按区段把超过 {@link WorksDict#DEVICE_CHECK_CYCLE_DAYS} 天周期未检的五类设备
 *       （钢轨/道岔/轨枕/桥梁/隧道）汇总成到期清单，可跨类型勾选后指定检查人与计划日期建批。</li>
 *   <li>一台设备同时只能被一个未完成（草稿/待复核/已退回）批次占用——数据库唯一键 + 服务校验双保险；
 *       已退回批次仍占用设备，整改必须在本批次重提，不允许换批重复登记。</li>
 *   <li>批次内逐台登记结论（合格/不合格）、实测里程、复查后状态与备注；
 *       提交时逐台核对：缺结论、实测里程超出所属区段里程区间、设备被其他批次占用，任一不符禁止提交。</li>
 *   <li>复核人可通过或退回：退回保留全部明细与退回原因，批次回到已退回供整改重提；
 *       通过在同一事务内按各台计划日期批量回写设备上次检查日期与状态，任一失败整体回滚。</li>
 *   <li>乐观锁守护并发复核，只有一份结论生效。</li>
 * </ol>
 */
@Service
public class RecheckService {

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMM");

    private final RecheckBatchRepository batchRepository;
    private final RecheckItemRepository itemRepository;
    private final DeviceRepository deviceRepository;
    private final SectionRepository sectionRepository;
    private final ViewSupport viewSupport;

    public RecheckService(RecheckBatchRepository batchRepository,
                          RecheckItemRepository itemRepository,
                          DeviceRepository deviceRepository,
                          SectionRepository sectionRepository,
                          ViewSupport viewSupport) {
        this.batchRepository = batchRepository;
        this.itemRepository = itemRepository;
        this.deviceRepository = deviceRepository;
        this.sectionRepository = sectionRepository;
        this.viewSupport = viewSupport;
    }

    // ============================================================ 到期汇总

    /**
     * 某区段 90 天周期到期设备（可跨五类），并标记每台是否已被其他未完成批次占用。
     * 占用设备默认仍返回（置 occupied 并附占用批次号），由页面勾选框置灰，保证汇总口径完整。
     */
    @Transactional(readOnly = true)
    public List<Views.RecheckItemView> eligibleDevices(Long sectionId) {
        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new BizException("区段不存在：id=" + sectionId));
        List<Device> devices = deviceRepository.findAllBySectionIdOrderByMileageAsc(sectionId).stream()
                .filter(this::isDue)
                .collect(Collectors.toList());
        Map<Long, RecheckItem> occupied = activeOccupancy(devices.stream().map(Device::getId).collect(Collectors.toList()));
        Map<Long, RecheckBatch> batchCache = new LinkedHashMap<>();
        List<Views.RecheckItemView> list = new ArrayList<>();
        for (Device device : devices) {
            Views.RecheckItemView v = new Views.RecheckItemView();
            fillDeviceSnapshot(v, device);
            v.setSectionId(section.getId());
            v.setSectionNo(section.getSectionNo());
            RecheckItem occ = occupied.get(device.getId());
            if (occ != null) {
                v.setOccupied(true);
                v.setOccupiedBatchId(occ.getBatchId());
                RecheckBatch b = batchCache.computeIfAbsent(occ.getBatchId(),
                        id -> batchRepository.findById(id).orElse(null));
                v.setOccupiedBatchNo(b == null ? "" : b.getBatchNo());
            } else {
                v.setOccupied(false);
            }
            list.add(v);
        }
        return list;
    }

    private boolean isDue(Device device) {
        if (device.getLastCheckDate() == null) {
            return true;
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(device.getLastCheckDate(), LocalDate.now());
        return days > WorksDict.DEVICE_CHECK_CYCLE_DAYS;
    }

    /** 给定设备集合当前的未完成占用明细（一台设备最多一条，唯一键保证） */
    private Map<Long, RecheckItem> activeOccupancy(List<Long> deviceIds) {
        Map<Long, RecheckItem> map = new LinkedHashMap<>();
        if (deviceIds == null || deviceIds.isEmpty()) {
            return map;
        }
        for (RecheckItem item : itemRepository.findActiveByDeviceIdIn(deviceIds)) {
            map.putIfAbsent(item.getDeviceId(), item);
        }
        return map;
    }

    // ============================================================ 列表 / 详情

    @Transactional(readOnly = true)
    public List<Views.RecheckBatchView> list(Long lineId, Long sectionId, String status) {
        List<Long> sectionIds = null;
        if (lineId != null) {
            sectionIds = sectionRepository.findAllByLineIdOrderByStartMileAsc(lineId).stream()
                    .map(Section::getId).collect(Collectors.toList());
            if (sectionIds.isEmpty()) {
                return new ArrayList<>();
            }
        }
        List<RecheckBatch> rows;
        if (sectionId != null) {
            rows = status == null || status.isBlank()
                    ? batchRepository.findAllBySectionIdOrderByCreatedAtDesc(sectionId)
                    : batchRepository.findAllBySectionIdAndStatusOrderByCreatedAtDesc(sectionId, status);
        } else if (status != null && !status.isBlank()) {
            rows = batchRepository.findAllByStatusOrderByCreatedAtDesc(status);
        } else {
            rows = batchRepository.findAllByOrderByCreatedAtDesc();
        }
        List<Views.RecheckBatchView> list = new ArrayList<>();
        for (RecheckBatch batch : rows) {
            if (sectionIds != null && !sectionIds.contains(batch.getSectionId())) {
                continue;
            }
            list.add(toView(batch, false));
        }
        return list;
    }

    @Transactional(readOnly = true)
    public Views.RecheckBatchView detail(Long id) {
        RecheckBatch batch = mustGet(id);
        return toView(batch, true);
    }

    // ============================================================ 建批

    @Transactional
    public Views.RecheckBatchView create(RecheckDtos.RecheckCreateReq req) {
        Section section = sectionRepository.findById(req.getSectionId())
                .orElseThrow(() -> new BizException("区段不存在：id=" + req.getSectionId()));
        String inspector = trim(req.getInspector());
        if (inspector.isEmpty()) {
            throw new BizException("检查人不能为空");
        }
        if (req.getPlanDate() == null) {
            throw new BizException("请指定计划检查日期");
        }
        List<Long> deviceIds = req.getDeviceIds() == null ? List.of()
                : req.getDeviceIds().stream().filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        if (deviceIds.isEmpty()) {
            throw new BizException("请至少勾选一台到期设备");
        }

        LocalDateTime now = LocalDateTime.now();
        RecheckBatch batch = new RecheckBatch();
        batch.setBatchNo(nextNo(now));
        batch.setSectionId(section.getId());
        batch.setInspector(inspector);
        batch.setPlanDate(req.getPlanDate());
        batch.setStatus("DRAFT");
        batch.setRemark(trim(req.getRemark()));
        batch.setCreatedAt(now);
        RecheckBatch saved = batchRepository.saveAndFlush(batch);

        List<RecheckItem> items = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (Long deviceId : deviceIds) {
            Device device = deviceRepository.findById(deviceId).orElse(null);
            if (device == null) {
                problems.add("设备不存在：id=" + deviceId);
                continue;
            }
            if (!device.getSectionId().equals(section.getId())) {
                problems.add("设备 " + device.getDeviceNo() + " 不属于区段 " + section.getSectionNo());
                continue;
            }
            if (!isDue(device)) {
                problems.add("设备 " + device.getDeviceNo() + " 未到 90 天定检周期，不应纳入本批次");
                continue;
            }
            RecheckItem item = new RecheckItem();
            item.setBatchId(saved.getId());
            item.setBatchStatus("DRAFT");
            item.setDeviceId(device.getId());
            item.setDeviceNo(device.getDeviceNo());
            item.setDeviceType(device.typeCode());
            item.setSectionId(device.getSectionId());
            item.setPlanDate(req.getPlanDate());
            items.add(item);
        }
        if (items.isEmpty()) {
            // 事务随异常整体回滚，不留半成品批次
            throw new BizException(problems.isEmpty() ? "没有可建批的设备" : String.join("；", problems));
        }
        if (!problems.isEmpty()) {
            throw new BizException(String.join("；", problems));
        }
        try {
            // uk_recheck_item_active：并发建批时同一设备只有第一个未完成批次能落库
            itemRepository.saveAll(items);
            itemRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BizException("勾选设备中存在已被其他未完成批次占用的设备，请刷新到期清单后重新勾选");
        }
        return toView(saved, true);
    }

    // ============================================================ 逐台登记

    @Transactional
    public Views.RecheckBatchView register(Long batchId, Long itemId, RecheckDtos.RecheckRegisterReq req) {
        RecheckBatch batch = mustGet(batchId);
        if (!"DRAFT".equals(batch.getStatus()) && !"REJECTED".equals(batch.getStatus())) {
            throw new BizException("批次当前为「" + WorksDict.recheckStatusName(batch.getStatus())
                    + "」，只有草稿或已退回批次允许逐台登记");
        }
        RecheckItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new BizException("复查明细不存在：id=" + itemId));
        if (!item.getBatchId().equals(batch.getId())) {
            throw new BizException("该台设备不属于批次 " + batch.getBatchNo());
        }
        String conclusion = trim(req.getConclusion()).toUpperCase();
        if (!WorksDict.isValidRecheckConclusion(conclusion)) {
            throw new BizException("复查结论只能是 OK 合格 或 NG 不合格");
        }
        String deviceStatus = trim(req.getDeviceStatus()).toUpperCase();
        if (!WorksDict.isValidDeviceStatus(deviceStatus)) {
            throw new BizException("设备状态只能是 GOOD 良好 / WATCH 关注 / FAULT 失格");
        }
        if (req.getMeasuredMileage() == null) {
            throw new BizException("请登记实测里程");
        }
        item.setConclusion(conclusion);
        item.setMeasuredMileage(req.getMeasuredMileage());
        item.setDeviceStatus(deviceStatus);
        item.setRemark(trim(req.getRemark()));
        item.setRegisteredAt(LocalDateTime.now());
        itemRepository.save(item);
        return toView(batch, true);
    }

    // ============================================================ 提交 / 重提

    @Transactional
    public Views.RecheckBatchView submit(Long id) {
        RecheckBatch batch = mustGet(id);
        if ("PENDING".equals(batch.getStatus())) {
            throw new BizException("批次已提交，正在等待复核");
        }
        if ("APPROVED".equals(batch.getStatus())) {
            throw new BizException("批次已复核通过，不能重复提交");
        }
        List<RecheckItem> items = itemRepository.findAllByBatchIdOrderByDeviceTypeAscMeasuredMileageAscIdAsc(id);
        if (items.isEmpty()) {
            throw new BizException("批次内没有设备明细，无法提交");
        }
        String blocked = validateForSubmit(batch, items);
        if (blocked != null) {
            throw new BizException(blocked);
        }
        LocalDateTime now = LocalDateTime.now();
        batch.setStatus("PENDING");
        batch.setSubmittedAt(now);
        batch.setReviewer(null);
        batch.setReviewedAt(null);
        for (RecheckItem item : items) {
            item.setBatchStatus("PENDING");
        }
        batchRepository.save(batch);
        itemRepository.saveAll(items);
        return toView(batch, true);
    }

    /** 提交前逐台核对，返回聚合的阻断原因；无问题返回 null */
    private String validateForSubmit(RecheckBatch batch, List<RecheckItem> items) {
        Section section = sectionRepository.findById(batch.getSectionId()).orElse(null);
        List<String> problems = new ArrayList<>();
        for (RecheckItem item : items) {
            String label = item.getDeviceNo();
            if (item.getConclusion() == null || item.getConclusion().isBlank()) {
                problems.add(label + " 缺少复查结论");
            } else if (!WorksDict.isValidRecheckConclusion(item.getConclusion())) {
                problems.add(label + " 复查结论非法");
            }
            if (item.getMeasuredMileage() == null) {
                problems.add(label + " 缺少实测里程");
            }
            if (item.getDeviceStatus() == null || item.getDeviceStatus().isBlank()) {
                problems.add(label + " 缺少复查后设备状态");
            } else if (!WorksDict.isValidDeviceStatus(item.getDeviceStatus())) {
                problems.add(label + " 设备状态非法");
            }
            Device device = deviceRepository.findById(item.getDeviceId()).orElse(null);
            if (device == null) {
                problems.add(label + " 已从台账删除");
                continue;
            }
            // 设备必须仍属于批次区段，且实测里程落在其所属区段里程区间内
            if (section == null || !device.getSectionId().equals(section.getId())) {
                problems.add(label + " 不属于区段 " + (section == null ? "" : section.getSectionNo()));
            } else if (item.getMeasuredMileage() != null
                    && (item.getMeasuredMileage().compareTo(section.getStartMile()) < 0
                        || item.getMeasuredMileage().compareTo(section.getEndMile()) > 0)) {
                problems.add(label + " 实测里程 " + WorksDict.mileageText(item.getMeasuredMileage())
                        + " 超出区段 " + section.getSectionNo() + "（"
                        + WorksDict.mileageText(section.getStartMile()) + " ~ "
                        + WorksDict.mileageText(section.getEndMile()) + "）");
            }
            // 重复占用：其他未完成批次仍挂着这台设备（本批次自身的明细不算）
            for (RecheckItem occ : itemRepository.findActiveByDeviceId(item.getDeviceId())) {
                if (!occ.getBatchId().equals(batch.getId())) {
                    RecheckBatch other = batchRepository.findById(occ.getBatchId()).orElse(null);
                    problems.add(label + " 已被未完成批次 " + (other == null ? ("#" + occ.getBatchId()) : other.getBatchNo())
                            + " 占用，不能重复提交");
                }
            }
        }
        return problems.isEmpty() ? null : String.join("；", problems);
    }

    // ============================================================ 复核

    @Transactional
    public Views.RecheckBatchView review(Long id, RecheckDtos.RecheckReviewReq req) {
        RecheckBatch batch = mustGet(id);
        String reviewer = trim(req.getReviewer());
        if (reviewer.isEmpty()) {
            throw new BizException("复核人不能为空");
        }
        String action = trim(req.getAction()).toUpperCase();
        if (!"APPROVE".equals(action) && !"RETURN".equals(action)) {
            throw new BizException("复核动作只能是 APPROVE 通过或 RETURN 退回");
        }
        if (!"PENDING".equals(batch.getStatus())) {
            throw new BizException("批次当前为「" + WorksDict.recheckStatusName(batch.getStatus())
                    + "」，只有待复核批次允许复核");
        }
        List<RecheckItem> items = itemRepository.findAllByBatchIdOrderByDeviceTypeAscMeasuredMileageAscIdAsc(id);
        LocalDateTime now = LocalDateTime.now();

        if ("RETURN".equals(action)) {
            String note = trim(req.getReviewNote());
            if (note.isEmpty()) {
                throw new BizException("退回必须填写退回原因（明细将原样保留供整改）");
            }
            batch.setReviewer(reviewer);
            batch.setReviewNote(note);
            batch.setStatus("REJECTED");
            batch.setReviewedAt(now);
            for (RecheckItem item : items) {
                item.setBatchStatus("REJECTED");
            }
            try {
                batchRepository.saveAndFlush(batch);
            } catch (ObjectOptimisticLockingFailureException e) {
                throw new BizException("批次已被其他复核操作改变，请刷新后查看最新结论");
            }
            itemRepository.saveAll(items);
            return toView(batch, true);
        }

        // ---- 通过：落键前再做一次完整核对，防止待复核期间台账变化 ----
        String blocked = validateForSubmit(batch, items);
        if (blocked != null) {
            throw new BizException("批次已不满足通过条件：" + blocked + "；请退回后由检查人整改重提");
        }
        batch.setReviewer(reviewer);
        batch.setReviewNote(trim(req.getReviewNote()));
        batch.setStatus("APPROVED");
        batch.setReviewedAt(now);

        // 批量回写台账：按各台计划日期更新上次检查日期，按登记状态更新设备状态
        List<Device> changed = new ArrayList<>();
        for (RecheckItem item : items) {
            Device device = deviceRepository.findById(item.getDeviceId())
                    .orElseThrow(() -> new BizException(item.getDeviceNo() + " 已从台账删除"));
            device.setLastCheckDate(item.getPlanDate());
            device.setStatus(item.getDeviceStatus());
            changed.add(device);
            item.setBatchStatus("APPROVED");
        }
        try {
            // 乐观锁：并发的第二份复核结论在此处失败回滚，台账绝不回写两次
            batchRepository.saveAndFlush(batch);
            deviceRepository.saveAll(changed);
            itemRepository.saveAll(items);
            itemRepository.flush();
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BizException("该批次已被另一名复核人先行处理，只有一份结论生效，请刷新查看");
        } catch (DataIntegrityViolationException e) {
            throw new BizException("批次通过时存在设备占用冲突，本次回写已整体回滚，请刷新后重试");
        }
        return toView(batch, true);
    }

    // ============================================================ 视图装配

    private Views.RecheckBatchView toView(RecheckBatch batch, boolean withItems) {
        Views.RecheckBatchView v = new Views.RecheckBatchView();
        v.setId(batch.getId());
        v.setBatchNo(batch.getBatchNo());
        v.setSectionId(batch.getSectionId());
        v.setInspector(batch.getInspector());
        v.setReviewer(batch.getReviewer());
        v.setPlanDate(batch.getPlanDate());
        v.setStatus(batch.getStatus());
        v.setStatusName(WorksDict.recheckStatusName(batch.getStatus()));
        v.setVersion(batch.getVersion());
        v.setRemark(batch.getRemark());
        v.setReviewNote(batch.getReviewNote());
        v.setSubmittedAt(batch.getSubmittedAt());
        v.setReviewedAt(batch.getReviewedAt());
        sectionRepository.findById(batch.getSectionId()).ifPresent(s -> {
            v.setSectionNo(s.getSectionNo());
            v.setLineId(s.getLineId());
            v.setLineName(viewSupport.lineName(s.getLineId()));
        });

        List<RecheckItem> rows = itemRepository
                .findAllByBatchIdOrderByDeviceTypeAscMeasuredMileageAscIdAsc(batch.getId());
        int registered = 0;
        int ok = 0;
        int ng = 0;
        int overdue = 0;
        List<Views.RecheckItemView> itemViews = new ArrayList<>();
        for (RecheckItem item : rows) {
            if (item.getConclusion() != null && !item.getConclusion().isBlank()) {
                registered++;
                if ("OK".equals(item.getConclusion())) {
                    ok++;
                } else if ("NG".equals(item.getConclusion())) {
                    ng++;
                }
            }
            if (!"APPROVED".equals(batch.getStatus())
                    && item.getPlanDate() != null && item.getPlanDate().isBefore(LocalDate.now())) {
                overdue++;
            }
            if (withItems) {
                itemViews.add(toItemView(batch, item));
            }
        }
        v.setTotalCount(rows.size());
        v.setRegisteredCount(registered);
        v.setOkCount(ok);
        v.setNgCount(ng);
        v.setOverdueCount(overdue);
        v.setProgress(rows.isEmpty() ? 0 : (int) Math.round(registered * 100.0 / rows.size()));
        if (withItems) {
            v.setItems(itemViews);
        }
        return v;
    }

    private Views.RecheckItemView toItemView(RecheckBatch batch, RecheckItem item) {
        Views.RecheckItemView v = new Views.RecheckItemView();
        v.setId(item.getId());
        v.setBatchId(item.getBatchId());
        v.setBatchStatus(item.getBatchStatus());
        v.setDeviceId(item.getDeviceId());
        v.setDeviceNo(item.getDeviceNo());
        v.setDeviceType(item.getDeviceType());
        v.setDeviceTypeName(WorksDict.deviceTypeName(item.getDeviceType()));
        v.setSectionId(item.getSectionId());
        v.setPlanDate(item.getPlanDate());
        v.setConclusion(item.getConclusion());
        v.setConclusionName(WorksDict.recheckConclusionName(item.getConclusion()));
        v.setMeasuredMileage(item.getMeasuredMileage());
        v.setMeasuredMileageText(WorksDict.mileageText(item.getMeasuredMileage()));
        v.setDeviceStatus(item.getDeviceStatus());
        v.setDeviceStatusName(WorksDict.deviceStatusName(item.getDeviceStatus()));
        v.setRemark(item.getRemark());
        v.setRegisteredAt(item.getRegisteredAt());
        v.setRegistered(item.getConclusion() != null && !item.getConclusion().isBlank());
        v.setPlanOverdue(!"APPROVED".equals(batch.getStatus())
                && item.getPlanDate() != null && item.getPlanDate().isBefore(LocalDate.now()));
        sectionRepository.findById(item.getSectionId()).ifPresent(s -> v.setSectionNo(s.getSectionNo()));
        deviceRepository.findById(item.getDeviceId()).ifPresent(d -> {
            v.setLedgerMileage(d.getMileage());
            v.setLedgerMileageText(WorksDict.mileageText(d.getMileage()));
            v.setLedgerLastCheckDate(d.getLastCheckDate());
            v.setLedgerStatus(d.getStatus());
            v.setLedgerStatusName(WorksDict.deviceStatusName(d.getStatus()));
            v.setLedgerRemark(d.getRemark());
            long days = d.getLastCheckDate() == null
                    ? WorksDict.DEVICE_CHECK_CYCLE_DAYS + 1L
                    : java.time.temporal.ChronoUnit.DAYS.between(d.getLastCheckDate(), LocalDate.now());
            v.setLedgerDaysSinceCheck(days);
            v.setLedgerOverdue(days > WorksDict.DEVICE_CHECK_CYCLE_DAYS);
        });
        // 详情内联提示：逐台问题（台账区段变化等运行期问题在提交/通过时才硬阻断）
        List<String> problems = new ArrayList<>();
        Section section = sectionRepository.findById(batch.getSectionId()).orElse(null);
        if (section != null) {
            if (v.getLedgerMileage() == null) {
                problems.add("设备已从台账删除");
            } else if (item.getSectionId() == null || !item.getSectionId().equals(section.getId())) {
                problems.add("设备已不属于批次区段");
            }
            if (item.getMeasuredMileage() != null
                    && (item.getMeasuredMileage().compareTo(section.getStartMile()) < 0
                        || item.getMeasuredMileage().compareTo(section.getEndMile()) > 0)) {
                problems.add("实测里程超出区段 " + section.getSectionNo());
            }
        }
        if (item.getConclusion() == null || item.getConclusion().isBlank()) {
            problems.add("尚未登记复查结论");
        }
        v.setProblems(problems);
        return v;
    }

    /** 到期清单行复用：从台账设备取设备快照字段 */
    private void fillDeviceSnapshot(Views.RecheckItemView v, Device device) {
        v.setDeviceId(device.getId());
        v.setDeviceNo(device.getDeviceNo());
        v.setDeviceType(device.typeCode());
        v.setDeviceTypeName(WorksDict.deviceTypeName(device.typeCode()));
        v.setLedgerMileage(device.getMileage());
        v.setLedgerMileageText(WorksDict.mileageText(device.getMileage()));
        v.setLedgerLastCheckDate(device.getLastCheckDate());
        v.setLedgerStatus(device.getStatus());
        v.setLedgerStatusName(WorksDict.deviceStatusName(device.getStatus()));
        v.setLedgerRemark(device.getRemark());
        long days = device.getLastCheckDate() == null
                ? WorksDict.DEVICE_CHECK_CYCLE_DAYS + 1L
                : java.time.temporal.ChronoUnit.DAYS.between(device.getLastCheckDate(), LocalDate.now());
        v.setLedgerDaysSinceCheck(days);
        v.setLedgerOverdue(days > WorksDict.DEVICE_CHECK_CYCLE_DAYS);
    }

    // ============================================================ 辅助

    private RecheckBatch mustGet(Long id) {
        return batchRepository.findById(id)
                .orElseThrow(() -> new BizException("定检复查批次不存在：id=" + id));
    }

    /** 批次编号：RB-年月-4 位序号 */
    private String nextNo(LocalDateTime now) {
        String prefix = "RB-" + now.format(NO_FMT) + "-";
        long max = 0;
        for (RecheckBatch b : batchRepository.findAll()) {
            if (b.getBatchNo() != null && b.getBatchNo().startsWith(prefix)) {
                try {
                    max = Math.max(max, Long.parseLong(b.getBatchNo().substring(prefix.length())));
                } catch (NumberFormatException ignore) {
                    // 非数字尾巴忽略
                }
            }
        }
        String no;
        do {
            max++;
            no = prefix + String.format("%04d", max);
        } while (batchRepository.existsByBatchNo(no));
        return no;
    }

    private String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
