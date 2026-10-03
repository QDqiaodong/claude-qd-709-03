package com.railway.works;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import com.railway.works.common.BizException;
import com.railway.works.domain.Bridge;
import com.railway.works.domain.Device;
import com.railway.works.domain.Line;
import com.railway.works.domain.Rail;
import com.railway.works.domain.Section;
import com.railway.works.domain.Sleeper;
import com.railway.works.domain.Switch;
import com.railway.works.domain.Tunnel;
import com.railway.works.dto.RecheckDtos;
import com.railway.works.dto.Views;
import com.railway.works.repository.BridgeRepository;
import com.railway.works.repository.DeviceRepository;
import com.railway.works.repository.LineRepository;
import com.railway.works.repository.RailRepository;
import com.railway.works.repository.SectionRepository;
import com.railway.works.repository.SleeperRepository;
import com.railway.works.repository.SwitchRepository;
import com.railway.works.repository.TunnelRepository;
import com.railway.works.service.RecheckService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 定检复查批次端到端业务测试（H2 内存库 + JPA 真实落库），覆盖：
 * <ol>
 *   <li>跨类型选择：钢轨/道岔/轨枕/桥梁/隧道在同一批次混合建批；</li>
 *   <li>重复占用：已被其他未完成批次占用的设备不能再建批/提交；</li>
 *   <li>退回重提：退回保留全部明细与原因，整改后重新提交；</li>
 *   <li>批量更新：通过后批量回写各设备上次检查日期与状态。</li>
 * </ol>
 * 另含：里程越界禁止提交、缺结论禁止提交、通过后释放占用。
 */
@SpringBootTest
@EnableAutoConfiguration(excludeName = {
        "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
class RecheckServiceIntegrationTest {

    @Autowired
    private RecheckService recheckService;
    @Autowired
    private SectionRepository sectionRepository;
    @Autowired
    private LineRepository lineRepository;
    @Autowired
    private DeviceRepository deviceRepository;
    @Autowired
    private RailRepository railRepository;
    @Autowired
    private SwitchRepository switchRepository;
    @Autowired
    private SleeperRepository sleeperRepository;
    @Autowired
    private BridgeRepository bridgeRepository;
    @Autowired
    private TunnelRepository tunnelRepository;

    private Section section;
    /** 五类各一台，全部 90 天超期 */
    private Rail rail;
    private Switch sw;
    private Sleeper sleeper;
    private Bridge bridge;
    private Tunnel tunnel;
    /** 一台未到期，用于排除测试 */
    private Rail freshRail;

    private static final LocalDate OLD_DATE = LocalDate.now().minusDays(120);
    private static final LocalDate FRESH_DATE = LocalDate.now().minusDays(10);

    @BeforeEach
    void setUp() {
        deviceRepository.deleteAll();
        sectionRepository.deleteAll();
        lineRepository.deleteAll();

        Line line = new Line();
        line.setName("测试线" + System.nanoTime());
        line.setStartStation("A");
        line.setEndStation("B");
        line.setMileageKm(new BigDecimal("100.000"));
        line.setDirection("UP");
        line = lineRepository.save(line);

        section = new Section();
        section.setLineId(line.getId());
        section.setSectionNo("T-01-" + System.nanoTime());
        section.setStartMile(new BigDecimal("10.000"));
        section.setEndMile(new BigDecimal("20.000"));
        section.setRailType("60KG");
        section.setBedType("BALLAST");
        section.setDesignSpeed(160);
        section.setStatus("NORMAL");
        section.setSpeedLimit(160);
        section = sectionRepository.save(section);

        rail = rail(section, "T-RAIL-1", new BigDecimal("11.000"), OLD_DATE, "WATCH");
        sw = switchDevice(section, "T-SW-1", new BigDecimal("12.000"), OLD_DATE, "WATCH");
        sleeper = sleeper(section, "T-SLP-1", new BigDecimal("13.000"), OLD_DATE, "WATCH");
        bridge = bridge(section, "T-BRG-1", new BigDecimal("14.000"), OLD_DATE, "WATCH");
        tunnel = tunnel(section, "T-TUN-1", new BigDecimal("15.000"), OLD_DATE, "FAULT");
        freshRail = rail(section, "T-RAIL-FRESH", new BigDecimal("16.000"), FRESH_DATE, "GOOD");
    }

    // ---------------- 到期汇总 + 跨类型选择 ----------------

    @Test
    void eligibleDevices_listsOnlyDueAcrossFiveTypes_withOccupancyFlags() {
        List<Views.RecheckItemView> due = recheckService.eligibleDevices(section.getId());
        assertEquals(5, due.size(), "五类各一台到期，未到期设备不应出现");
        assertEquals(5, due.stream().map(Views.RecheckItemView::getDeviceType).distinct().count(),
                "到期清单应覆盖钢轨/道岔/轨枕/桥梁/隧道五种类型");
        assertTrue(due.stream().noneMatch(Views.RecheckItemView::getOccupied), "初始无占用");

        // 跨类型勾选五类建批
        RecheckDtos.RecheckCreateReq req = createReq(
                List.of(rail.getId(), sw.getId(), sleeper.getId(), bridge.getId(), tunnel.getId()));
        Views.RecheckBatchView batch = recheckService.create(req);
        assertEquals("DRAFT", batch.getStatus());
        assertEquals(5, batch.getTotalCount());
        assertEquals(0, batch.getRegisteredCount());
        assertTrue(batch.getBatchNo().startsWith("RB-"));

        // 刷新到期清单：五台全部被自己批次占用
        List<Views.RecheckItemView> after = recheckService.eligibleDevices(section.getId());
        assertEquals(5, after.size());
        assertTrue(after.stream().allMatch(Views.RecheckItemView::getOccupied));
        assertEquals(batch.getId(), after.get(0).getOccupiedBatchId());
    }

    @Test
    void create_rejectsDeviceOutsideSectionOrNotDue() {
        // 未到期设备不能纳入
        BizException ex = assertThrows(BizException.class,
                () -> recheckService.create(createReq(List.of(freshRail.getId()))));
        assertTrue(ex.getMessage().contains("未到 90 天定检周期"), ex.getMessage());

        // 不存在的设备
        BizException ex2 = assertThrows(BizException.class,
                () -> recheckService.create(createReq(List.of(999999L))));
        assertTrue(ex2.getMessage().contains("设备不存在"), ex2.getMessage());
    }

    // ---------------- 逐台登记 + 缺结论/里程越界禁止提交 ----------------

    @Test
    void register_storesPartialResult_andSubmitBlockedWhenMissingConclusion() {
        Views.RecheckBatchView batch = recheckService.create(createReq(List.of(rail.getId(), tunnel.getId())));
        Long batchId = batch.getId();
        Long railItemId = itemId(batch, rail.getId());
        Long tunnelItemId = itemId(batch, tunnel.getId());

        // 明细都没登记 → 禁止提交，逐台提示缺结论
        BizException none = assertThrows(BizException.class, () -> recheckService.submit(batchId));
        assertTrue(none.getMessage().contains("缺少复查结论"), none.getMessage());
        assertTrue(none.getMessage().contains(rail.getDeviceNo()), none.getMessage());

        // 只登记一台，另一台仍缺结论，提交继续被拦
        recheckService.register(batchId, railItemId, registerReq("OK", "11.500", "GOOD", ""));
        BizException partial = assertThrows(BizException.class, () -> recheckService.submit(batchId));
        assertTrue(partial.getMessage().contains(tunnel.getDeviceNo()), partial.getMessage());

        // 非法结论/状态在登记阶段即被拒
        assertThrows(BizException.class, () -> recheckService.register(batchId, tunnelItemId,
                registerReq("MAYBE", "15.000", "GOOD", "")));
        assertThrows(BizException.class, () -> recheckService.register(batchId, tunnelItemId,
                registerReq("OK", "15.000", "BROKEN", "")));

        // 非草稿/已退回状态不允许登记
        recheckService.register(batchId, tunnelItemId, registerReq("NG", "15.000", "FAULT", ""));
        recheckService.submit(batchId);
        assertThrows(BizException.class, () -> recheckService.register(batchId, railItemId,
                registerReq("OK", "11.600", "GOOD", "")));
    }

    @Test
    void submit_rejectsMeasuredMileageOutOfSectionRange() {
        Views.RecheckBatchView batch = recheckService.create(createReq(List.of(rail.getId(), tunnel.getId())));
        Long batchId = batch.getId();
        recheckService.register(batchId, itemId(batch, rail.getId()),
                registerReq("OK", "11.500", "GOOD", ""));
        // 越界里程允许登记暂存
        recheckService.register(batchId, itemId(batch, tunnel.getId()),
                registerReq("NG", "20.500", "FAULT", "照明故障"));

        BizException ex = assertThrows(BizException.class, () -> recheckService.submit(batchId));
        assertTrue(ex.getMessage().contains("实测里程"), ex.getMessage());
        assertTrue(ex.getMessage().contains("超出区段"), ex.getMessage());

        // 改成区段内里程（端点 20.000 合法）后可以提交
        Views.RecheckBatchView detail = recheckService.detail(batchId);
        recheckService.register(batchId, itemId(detail, tunnel.getId()),
                registerReq("NG", "20.000", "FAULT", "照明故障"));
        Views.RecheckBatchView submitted = recheckService.submit(batchId);
        assertEquals("PENDING", submitted.getStatus());
        assertEquals(2, submitted.getRegisteredCount());
        assertEquals(1, submitted.getOkCount());
        assertEquals(1, submitted.getNgCount());
        assertEquals(100, submitted.getProgress());
    }

    // ---------------- 重复占用 ----------------

    @Test
    void duplicateOccupancy_secondBatchCannotUseDeviceInUnfinishedBatch() {
        // 第一个批次占用 rail（草稿态）
        Views.RecheckBatchView first = recheckService.create(createReq(List.of(rail.getId())));

        // 第二个批次再勾 rail：服务校验/唯一键必须拦截
        BizException ex = assertThrows(BizException.class,
                () -> recheckService.create(createReq(List.of(rail.getId(), sw.getId()))));
        assertTrue(ex.getMessage().contains("占用"), ex.getMessage());

        // 第一份批次提交到待复核后，其它批次仍不能用 rail
        recheckService.register(first.getId(), itemId(first, rail.getId()),
                registerReq("OK", "11.200", "GOOD", ""));
        recheckService.submit(first.getId());
        BizException ex2 = assertThrows(BizException.class,
                () -> recheckService.create(createReq(List.of(rail.getId()))));
        assertTrue(ex2.getMessage().contains("占用"), ex2.getMessage());

        // 待复核期间设备台账依然不变
        Device ledger = deviceRepository.findById(rail.getId()).orElseThrow();
        assertEquals(OLD_DATE, ledger.getLastCheckDate());
        assertEquals("WATCH", ledger.getStatus());
    }

    // ---------------- 退回重提 ----------------

    @Test
    void return_keepsItemsAndReason_thenResubmitAfterFix() {
        Views.RecheckBatchView batch = recheckService.create(createReq(List.of(rail.getId(), tunnel.getId())));
        Long batchId = batch.getId();
        recheckService.register(batchId, itemId(batch, rail.getId()),
                registerReq("OK", "11.200", "GOOD", ""));
        recheckService.register(batchId, itemId(batch, tunnel.getId()),
                registerReq("NG", "15.300", "FAULT", "衬砌渗水"));
        recheckService.submit(batchId);

        // 退回：必须有原因
        RecheckDtos.RecheckReviewReq noReason = reviewReq("RETURN", "");
        BizException ex = assertThrows(BizException.class,
                () -> recheckService.review(batchId, noReason));
        assertTrue(ex.getMessage().contains("退回原因"), ex.getMessage());

        Views.RecheckBatchView rejected = recheckService.review(batchId, reviewReq("RETURN", "隧道数据需复测"));
        assertEquals("REJECTED", rejected.getStatus());
        assertEquals("隧道数据需复测", rejected.getReviewNote());
        assertEquals("复核员", rejected.getReviewer());
        // 明细全部保留
        assertEquals(2, rejected.getItems().size());
        assertTrue(rejected.getItems().stream().allMatch(i -> i.getConclusion() != null), "退回后登记明细必须保留");

        // 退回状态仍占用设备：别的批次建不起来
        assertThrows(BizException.class,
                () -> recheckService.create(createReq(List.of(rail.getId()))));

        // 在本批次整改（修改隧道里程）后重提
        recheckService.register(batchId, itemId(rejected, tunnel.getId()),
                registerReq("NG", "15.400", "FAULT", "衬砌渗水复测"));
        Views.RecheckBatchView again = recheckService.submit(batchId);
        assertEquals("PENDING", again.getStatus());
        assertNotNull(again.getSubmittedAt());
    }

    // ---------------- 通过：批量更新台账 + 释放占用 ----------------

    @Test
    void approve_batchUpdatesLedgerLastCheckDateAndStatus_andReleasesOccupancy() {
        LocalDate planDate = LocalDate.of(2026, 10, 3);
        RecheckDtos.RecheckCreateReq req = createReq(
                List.of(rail.getId(), sw.getId(), sleeper.getId(), bridge.getId(), tunnel.getId()));
        req.setPlanDate(planDate);
        Views.RecheckBatchView batch = recheckService.create(req);
        Long batchId = batch.getId();
        for (Device d : List.of(rail, sw, sleeper, bridge, tunnel)) {
            Long itemId = itemId(batch, d.getId());
            String status = d instanceof Tunnel ? "FAULT" : "GOOD";
            String conclusion = d instanceof Tunnel ? "NG" : "OK";
            recheckService.register(batchId, itemId, registerReq(conclusion, "15.000", status, ""));
        }
        recheckService.submit(batchId);

        // 重复提交被拦（已在待复核）
        assertThrows(BizException.class, () -> recheckService.submit(batchId));

        Views.RecheckBatchView approved = recheckService.review(batchId, reviewReq("APPROVE", "同意"));
        assertEquals("APPROVED", approved.getStatus());

        // 台账批量回写：上次检查日期=各台计划日期，状态=登记状态
        assertEquals(planDate, deviceRepository.findById(rail.getId()).orElseThrow().getLastCheckDate());
        assertEquals("GOOD", deviceRepository.findById(rail.getId()).orElseThrow().getStatus());
        assertEquals(planDate, deviceRepository.findById(tunnel.getId()).orElseThrow().getLastCheckDate());
        assertEquals("FAULT", deviceRepository.findById(tunnel.getId()).orElseThrow().getStatus());
        assertEquals(planDate, deviceRepository.findById(sw.getId()).orElseThrow().getLastCheckDate());
        assertEquals(planDate, deviceRepository.findById(sleeper.getId()).orElseThrow().getLastCheckDate());
        assertEquals(planDate, deviceRepository.findById(bridge.getId()).orElseThrow().getLastCheckDate());

        // 通过后释放占用：把台账日期拨旧重新到期，应能再建批，且清单无占用标记
        for (Device d : List.of(rail, sw, sleeper, bridge, tunnel)) {
            Device fresh = deviceRepository.findById(d.getId()).orElseThrow();
            fresh.setLastCheckDate(OLD_DATE);
            deviceRepository.save(fresh);
        }
        List<Views.RecheckItemView> dueAgain = recheckService.eligibleDevices(section.getId());
        assertEquals(5, dueAgain.size(), "5 台通过后释放占用并重新到期（freshRail 仍未到期）");
        assertFalse(dueAgain.stream().anyMatch(Views.RecheckItemView::getOccupied),
                "已通过批次不应再占用设备");
        Views.RecheckBatchView second = recheckService.create(createReq(List.of(rail.getId())));
        assertNotNull(second.getId());

        // 已通过批次不能重复复核/提交
        assertThrows(BizException.class,
                () -> recheckService.review(batchId, reviewReq("APPROVE", "")));
        assertThrows(BizException.class, () -> recheckService.submit(batchId));
    }

    // ---------------- 辅助 ----------------

    private Long itemId(Views.RecheckBatchView batch, Long deviceId) {
        return batch.getItems().stream()
                .filter(i -> i.getDeviceId().equals(deviceId))
                .findFirst()
                .map(Views.RecheckItemView::getId)
                .orElseThrow(() -> new IllegalStateException("批次中找不到设备 " + deviceId));
    }

    private RecheckDtos.RecheckCreateReq createReq(List<Long> deviceIds) {
        RecheckDtos.RecheckCreateReq req = new RecheckDtos.RecheckCreateReq();
        req.setSectionId(section.getId());
        req.setInspector("检查员");
        req.setPlanDate(LocalDate.now().plusDays(3));
        req.setDeviceIds(deviceIds);
        return req;
    }

    private RecheckDtos.RecheckRegisterReq registerReq(String conclusion, String mileage, String status, String remark) {
        RecheckDtos.RecheckRegisterReq req = new RecheckDtos.RecheckRegisterReq();
        req.setConclusion(conclusion);
        req.setMeasuredMileage(new BigDecimal(mileage));
        req.setDeviceStatus(status);
        req.setRemark(remark);
        return req;
    }

    private RecheckDtos.RecheckReviewReq reviewReq(String action, String note) {
        RecheckDtos.RecheckReviewReq req = new RecheckDtos.RecheckReviewReq();
        req.setReviewer("复核员");
        req.setAction(action);
        req.setReviewNote(note);
        return req;
    }

    /** 造一台具体子类设备的公共字段填充 */
    private Rail rail(Section s, String no, BigDecimal mileage, LocalDate lastCheck, String status) {
        Rail r = new Rail();
        r.setRailType("60KG");
        r.setRailLength(new BigDecimal("100.00"));
        r.setLockTemp(new BigDecimal("25.0"));
        return deviceRepository.save(fillBase(r, s, no, mileage, lastCheck, status));
    }

    private Switch switchDevice(Section s, String no, BigDecimal mileage, LocalDate lastCheck, String status) {
        Switch x = new Switch();
        x.setSwitchModel("12号");
        x.setDirection("LEFT");
        x.setMachineModel("S700K");
        return switchRepository.save(fillBase(x, s, no, mileage, lastCheck, status));
    }

    private Sleeper sleeper(Section s, String no, BigDecimal mileage, LocalDate lastCheck, String status) {
        Sleeper x = new Sleeper();
        x.setSleeperType("CONCRETE");
        x.setSleeperCount(1760);
        return sleeperRepository.save(fillBase(x, s, no, mileage, lastCheck, status));
    }

    private Bridge bridge(Section s, String no, BigDecimal mileage, LocalDate lastCheck, String status) {
        Bridge x = new Bridge();
        x.setBridgeLength(new BigDecimal("100.00"));
        x.setSpanLayout("3-32m");
        x.setLoadLimit(new BigDecimal("60.00"));
        return bridgeRepository.save(fillBase(x, s, no, mileage, lastCheck, status));
    }

    private Tunnel tunnel(Section s, String no, BigDecimal mileage, LocalDate lastCheck, String status) {
        Tunnel x = new Tunnel();
        x.setTunnelLength(new BigDecimal("800.00"));
        x.setLiningType("复合式衬砌");
        x.setLighting("NORMAL");
        return tunnelRepository.save(fillBase(x, s, no, mileage, lastCheck, status));
    }

    private <D extends Device> D fillBase(D d, Section s, String no, BigDecimal mileage,
                                          LocalDate lastCheck, String status) {
        d.setDeviceNo(no);
        d.setSectionId(s.getId());
        d.setMileage(mileage);
        d.setInstallDate(LocalDate.now().minusYears(3));
        d.setLastCheckDate(lastCheck);
        d.setStatus(status);
        return d;
    }
}
