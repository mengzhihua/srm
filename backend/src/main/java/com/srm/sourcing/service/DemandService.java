package com.srm.sourcing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.srm.basic.entity.Material;
import com.srm.basic.mapper.MaterialMapper;
import com.srm.basic.service.CatalogMatch;
import com.srm.common.BizException;
import com.srm.sourcing.entity.DemandItem;
import com.srm.sourcing.entity.PrLine;
import com.srm.sourcing.entity.PurchaseRequisition;
import com.srm.sourcing.mapper.DemandItemMapper;
import com.srm.system.auth.CurrentUser;
import com.srm.system.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DemandService {
    private final DemandItemMapper demandMapper;
    private final MaterialMapper materialMapper;
    private final PrService prService;
    private final ShelfService shelfService;

    public List<DemandItem> mine() {
        CurrentUser.requireBuyerSide();
        return demandMapper.selectList(new LambdaQueryWrapper<DemandItem>()
                .eq(DemandItem::getOwnerName, owner())
                .eq(DemandItem::getStatus, "OPEN")
                .orderByDesc(DemandItem::getId));
    }

    @Transactional
    public DemandItem add(String text, BigDecimal qty, String couponCode) {
        CurrentUser.requireBuyerSide();
        if (text == null || text.trim().isEmpty() || qty == null || qty.signum() <= 0) {
            throw new BizException("需求和数量必填");
        }
        Material material = CatalogMatch.match(materialMapper.selectList(null), text);
        DemandItem item = new DemandItem();
        item.setOwnerName(owner());
        item.setRequestText(text.trim());
        item.setQty(qty);
        item.setStatus("OPEN");
        if (material != null) {
            item.setMaterialCode(material.getCode());
        }
        if (couponCode != null && !couponCode.trim().isEmpty()) {
            com.srm.sourcing.entity.Coupon coupon = shelfService.useCoupon(couponCode);
            item.setCouponCode(coupon.getCode());
            item.setCouponPercent(coupon.getPercent());
        }
        demandMapper.insert(item);
        return item;
    }

    public List<Map<String, Object>> match(List<Map<String, Object>> lines) {
        CurrentUser.requireBuyerSide();
        List<Material> materials = materialMapper.selectList(null);
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        if (lines == null) {
            return out;
        }
        for (Map<String, Object> line : lines) {
            String text = line.get("text") == null ? "" : String.valueOf(line.get("text"));
            Material material = CatalogMatch.match(materials, text);
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("text", text);
            row.put("qty", line.get("qty"));
            row.put("materialCode", material == null ? null : material.getCode());
            row.put("materialName", material == null ? null : material.getName());
            out.add(row);
        }
        return out;
    }

    @Transactional
    public PurchaseRequisition convert() {
        CurrentUser.requireBuyerSide();
        List<DemandItem> open = mine();
        if (open.isEmpty()) {
            throw new BizException("需求清单是空的");
        }
        for (DemandItem item : open) {
            if (item.getMaterialCode() == null || item.getMaterialCode().trim().isEmpty()) {
                throw new BizException("还有未匹配的需求，补上物料后再转申请");
            }
        }
        PurchaseRequisition pr = new PurchaseRequisition();
        pr.setPlantCode("P001");
        pr.setRequester(owner());
        pr.setDepartment("DEMAND");
        pr.setRemark("需求清单转入");
        List<PrLine> lines = new ArrayList<PrLine>();
        for (DemandItem item : open) {
            PrLine line = new PrLine();
            line.setMaterialCode(item.getMaterialCode());
            line.setQty(item.getQty());
            line.setRemark(item.getRequestText());
            line.setCouponPercent(item.getCouponPercent());
            lines.add(line);
        }
        pr.setLines(lines);
        PurchaseRequisition saved = prService.create(pr);
        for (DemandItem item : open) {
            item.setStatus("CONVERTED");
            item.setPrId(saved.getId());
            demandMapper.updateById(item);
        }
        return saved;
    }

    private static String owner() {
        User user = CurrentUser.get();
        if (user == null || user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            return "buyer";
        }
        return user.getUsername().trim();
    }
}
