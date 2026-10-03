package com.railway.works.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.railway.works.common.BizException;
import com.railway.works.domain.Material;
import com.railway.works.dto.MaterialDtos;
import com.railway.works.dto.Views;
import com.railway.works.repository.MaterialRepository;

/**
 * 模块五：物资。
 *
 * <p>规则面：
 * <ol>
 *   <li>库存数量与安全库存不得为负</li>
 *   <li>出库数量不得大于现有库存</li>
 *   <li>库存低于安全库存即为预警，页面整行标红</li>
 * </ol>
 */
@Service
public class MaterialService {

    private final MaterialRepository materialRepository;
    private final ViewSupport viewSupport;

    public MaterialService(MaterialRepository materialRepository, ViewSupport viewSupport) {
        this.materialRepository = materialRepository;
        this.viewSupport = viewSupport;
    }

    @Transactional(readOnly = true)
    public List<Views.MaterialView> list() {
        List<Views.MaterialView> list = new ArrayList<>();
        for (Material material : materialRepository.findAllByOrderByIdAsc()) {
            list.add(viewSupport.toView(material));
        }
        return list;
    }

    @Transactional
    public Views.MaterialView create(MaterialDtos.MaterialSaveReq req) {
        if (req.getStockQty() == null || req.getStockQty() < 0) {
            throw new BizException("库存数量不能为负");
        }
        if (req.getSafetyStock() == null || req.getSafetyStock() < 0) {
            throw new BizException("安全库存不能为负");
        }
        Material material = new Material();
        material.setName(req.getName().trim());
        material.setSpec(req.getSpec() == null ? "" : req.getSpec().trim());
        material.setUnit(req.getUnit() == null ? "件" : req.getUnit().trim());
        material.setStockQty(req.getStockQty());
        material.setSafetyStock(req.getSafetyStock());
        return viewSupport.toView(materialRepository.save(material));
    }

    /** 出入库：正数为入库，负数为出库 */
    @Transactional
    public Views.MaterialView adjustStock(Long id, MaterialDtos.MaterialStockReq req) {
        Material material = materialRepository.findById(id)
                .orElseThrow(() -> new BizException("物资不存在：id=" + id));
        Integer quantity = req == null || req.getQuantity() == null ? 0 : req.getQuantity();
        if (quantity == 0) {
            throw new BizException("出入库数量不能为 0");
        }
        int after = material.getStockQty() + quantity;
        if (after < 0) {
            throw new BizException(material.getName() + " 现有库存 " + material.getStockQty() + "，不足以出库 " + (-quantity));
        }
        material.setStockQty(after);
        return viewSupport.toView(materialRepository.save(material));
    }
}
