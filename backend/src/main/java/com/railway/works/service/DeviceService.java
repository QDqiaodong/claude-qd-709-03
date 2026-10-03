package com.railway.works.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.railway.works.common.BizException;
import com.railway.works.domain.Bridge;
import com.railway.works.domain.Device;
import com.railway.works.domain.Rail;
import com.railway.works.domain.Section;
import com.railway.works.domain.Sleeper;
import com.railway.works.domain.Switch;
import com.railway.works.domain.Tunnel;
import com.railway.works.domain.WorksDict;
import com.railway.works.dto.DeviceDtos;
import com.railway.works.dto.Views;
import com.railway.works.repository.BridgeRepository;
import com.railway.works.repository.DeviceRepository;
import com.railway.works.repository.RailRepository;
import com.railway.works.repository.SectionRepository;
import com.railway.works.repository.SleeperRepository;
import com.railway.works.repository.SwitchRepository;
import com.railway.works.repository.TunnelRepository;

/**
 * 模块二：设备台账（继承体系）。
 *
 * <p>查基类 {@link DeviceRepository} 即 TABLE_PER_CLASS 的多态查询：一次 findAll 把
 * rail / switch / sleeper / bridge / tunnel 五张表 UNION 起来，返回五个子类的实例；
 * 传 typeCode 时才落到具体子类仓库查单表。
 *
 * <p>规则面：
 * <ol>
 *   <li>设备编号全局唯一（跨五个子类）</li>
 *   <li>设备里程必须落在其所属区段的里程区间内</li>
 *   <li>上次检查日不得晚于今天</li>
 *   <li>失格设备不允许只做检查登记就转良好，必须给出状态</li>
 * </ol>
 */
@Service
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final RailRepository railRepository;
    private final SwitchRepository switchRepository;
    private final SleeperRepository sleeperRepository;
    private final BridgeRepository bridgeRepository;
    private final TunnelRepository tunnelRepository;
    private final SectionRepository sectionRepository;
    private final ViewSupport viewSupport;

    public DeviceService(DeviceRepository deviceRepository,
                         RailRepository railRepository,
                         SwitchRepository switchRepository,
                         SleeperRepository sleeperRepository,
                         BridgeRepository bridgeRepository,
                         TunnelRepository tunnelRepository,
                         SectionRepository sectionRepository,
                         ViewSupport viewSupport) {
        this.deviceRepository = deviceRepository;
        this.railRepository = railRepository;
        this.switchRepository = switchRepository;
        this.sleeperRepository = sleeperRepository;
        this.bridgeRepository = bridgeRepository;
        this.tunnelRepository = tunnelRepository;
        this.sectionRepository = sectionRepository;
        this.viewSupport = viewSupport;
    }

    /**
     * 设备列表：typeCode 为空即多态查询（查基类，五种子类实例混排返回）。
     */
    @Transactional(readOnly = true)
    public List<Views.DeviceView> list(Long sectionId, String typeCode) {
        List<Device> devices = devicesOf(sectionId, typeCode);
        List<Views.DeviceView> list = new ArrayList<>();
        for (Device device : devices) {
            list.add(viewSupport.toView(device));
        }
        list.sort(Comparator.comparing(v -> viewSupport.mileageOrZero(v.getMileage())));
        return list;
    }

    private List<Device> devicesOf(Long sectionId, String typeCode) {
        if (typeCode == null || typeCode.isBlank()) {
            // 多态查询：查抽象基类 Device
            return sectionId == null
                    ? deviceRepository.findAll()
                    : deviceRepository.findAllBySectionIdOrderByMileageAsc(sectionId);
        }
        String type = typeCode.trim().toUpperCase();
        return switch (type) {
            case "RAIL" -> new ArrayList<>(railRepository.findAllByOrderByMileageAsc());
            case "SWITCH" -> new ArrayList<>(switchRepository.findAllByOrderByMileageAsc());
            case "SLEEPER" -> new ArrayList<>(sleeperRepository.findAllByOrderByMileageAsc());
            case "BRIDGE" -> new ArrayList<>(bridgeRepository.findAllByOrderByMileageAsc());
            case "TUNNEL" -> new ArrayList<>(tunnelRepository.findAllByOrderByMileageAsc());
            default -> throw new BizException("未知设备类型：" + typeCode);
        };
    }

    /** 按状态跨全部子类表检索（多态 JPQL） */
    @Transactional(readOnly = true)
    public List<Views.DeviceView> listByStatus(String status) {
        if (!WorksDict.isValidDeviceStatus(status)) {
            throw new BizException("设备状态只能是 GOOD 良好 / WATCH 关注 / FAULT 失格");
        }
        List<Views.DeviceView> list = new ArrayList<>();
        for (Device device : deviceRepository.findPolymorphicByStatus(status)) {
            list.add(viewSupport.toView(device));
        }
        return list;
    }

    @Transactional
    public Views.DeviceView create(DeviceDtos.DeviceSaveReq req) {
        String typeCode = req.getTypeCode() == null ? "" : req.getTypeCode().trim().toUpperCase();
        if (!WorksDict.isValidDeviceType(typeCode)) {
            throw new BizException("设备类型只能是 RAIL / SWITCH / SLEEPER / BRIDGE / TUNNEL");
        }
        String deviceNo = req.getDeviceNo() == null ? "" : req.getDeviceNo().trim();
        if (deviceRepository.existsByDeviceNo(deviceNo)) {
            throw new BizException("设备编号 " + deviceNo + " 已存在（五个子类全局唯一）");
        }
        Section section = req.getSectionId() == null ? null : sectionRepository.findById(req.getSectionId()).orElse(null);
        if (section == null) {
            throw new BizException("请先选择设备所属的区段");
        }
        BigDecimal mileage = viewSupport.mileageOrZero(req.getMileage());
        if (mileage.compareTo(section.getStartMile()) < 0 || mileage.compareTo(section.getEndMile()) > 0) {
            throw new BizException("设备里程 " + WorksDict.mileageText(mileage) + " 不在区段 "
                    + section.getSectionNo() + "（" + WorksDict.mileageText(section.getStartMile()) + " ~ "
                    + WorksDict.mileageText(section.getEndMile()) + "）内");
        }
        LocalDate lastCheck = req.getLastCheckDate();
        if (lastCheck != null && lastCheck.isAfter(LocalDate.now())) {
            throw new BizException("上次检查日不能晚于今天");
        }
        Device device = switch (typeCode) {
            case "RAIL" -> rail(req);
            case "SWITCH" -> switchDevice(req);
            case "SLEEPER" -> sleeper(req);
            case "BRIDGE" -> bridge(req);
            default -> tunnel(req);
        };
        device.setDeviceNo(deviceNo);
        device.setSectionId(section.getId());
        device.setMileage(mileage);
        device.setInstallDate(req.getInstallDate());
        device.setLastCheckDate(lastCheck);
        device.setStatus(WorksDict.isValidDeviceStatus(req.getStatus()) ? req.getStatus() : "GOOD");
        device.setRemark(req.getRemark());
        // 走基类仓库保存：TABLE_PER_CLASS 下会自动落到对应子类表
        return viewSupport.toView(deviceRepository.save(device));
    }

    /** 定期检查登记 */
    @Transactional
    public Views.DeviceView check(Long id, DeviceDtos.DeviceCheckReq req) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new BizException("设备不存在：id=" + id));
        LocalDate checkDate = req.getCheckDate() == null ? LocalDate.now() : req.getCheckDate();
        if (checkDate.isAfter(LocalDate.now())) {
            throw new BizException("检查日期不能晚于今天");
        }
        if (device.getLastCheckDate() != null && checkDate.isBefore(device.getLastCheckDate())) {
            throw new BizException("检查日期早于上次检查日 " + device.getLastCheckDate() + "，请核对");
        }
        device.setLastCheckDate(checkDate);
        if (req.getStatus() != null && !req.getStatus().isBlank()) {
            if (!WorksDict.isValidDeviceStatus(req.getStatus())) {
                throw new BizException("设备状态只能是 GOOD 良好 / WATCH 关注 / FAULT 失格");
            }
            device.setStatus(req.getStatus());
        }
        return viewSupport.toView(deviceRepository.save(device));
    }

    private Rail rail(DeviceDtos.DeviceSaveReq req) {
        if (!WorksDict.isValidRailType(req.getRailType())) {
            throw new BizException("钢轨轨型只能是 60KG 或 75KG");
        }
        Rail rail = new Rail();
        rail.setRailType(req.getRailType());
        rail.setRailLength(req.getRailLength() == null ? BigDecimal.valueOf(100) : req.getRailLength());
        rail.setLockTemp(req.getLockTemp() == null ? BigDecimal.valueOf(25) : req.getLockTemp());
        return rail;
    }

    private Switch switchDevice(DeviceDtos.DeviceSaveReq req) {
        Switch sw = new Switch();
        sw.setSwitchModel(req.getSwitchModel() == null ? "12号" : req.getSwitchModel());
        sw.setDirection("RIGHT".equals(req.getDirection()) ? "RIGHT" : "LEFT");
        sw.setMachineModel(req.getMachineModel() == null ? "ZD6-D" : req.getMachineModel());
        return sw;
    }

    private Sleeper sleeper(DeviceDtos.DeviceSaveReq req) {
        Sleeper sleeper = new Sleeper();
        sleeper.setSleeperType("WOOD".equals(req.getSleeperType()) ? "WOOD" : "CONCRETE");
        sleeper.setSleeperCount(req.getSleeperCount() == null ? 1760 : req.getSleeperCount());
        return sleeper;
    }

    private Bridge bridge(DeviceDtos.DeviceSaveReq req) {
        Bridge bridge = new Bridge();
        bridge.setBridgeLength(req.getBridgeLength() == null ? BigDecimal.valueOf(100) : req.getBridgeLength());
        bridge.setSpanLayout(req.getSpanLayout() == null ? "3-32m" : req.getSpanLayout());
        bridge.setLoadLimit(req.getLoadLimit() == null ? BigDecimal.valueOf(60) : req.getLoadLimit());
        return bridge;
    }

    private Tunnel tunnel(DeviceDtos.DeviceSaveReq req) {
        Tunnel tunnel = new Tunnel();
        tunnel.setTunnelLength(req.getTunnelLength() == null ? BigDecimal.valueOf(500) : req.getTunnelLength());
        tunnel.setLiningType(req.getLiningType() == null ? "复合式衬砌" : req.getLiningType());
        tunnel.setLighting(req.getLighting() == null ? "NORMAL" : req.getLighting());
        return tunnel;
    }
}
