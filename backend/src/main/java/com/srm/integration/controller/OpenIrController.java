package com.srm.integration.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.srm.common.BizException;
import com.srm.common.R;
import com.srm.delivery.entity.Asn;
import com.srm.delivery.entity.AsnLine;
import com.srm.delivery.mapper.AsnLineMapper;
import com.srm.delivery.mapper.AsnMapper;
import com.srm.purchase.entity.PoLine;
import com.srm.purchase.entity.PurchaseOrder;
import com.srm.purchase.mapper.PoLineMapper;
import com.srm.purchase.mapper.PurchaseOrderMapper;
import com.srm.purchase.service.PurchaseOrderService;
import com.srm.sourcing.entity.PrLine;
import com.srm.sourcing.entity.PurchaseRequisition;
import com.srm.sourcing.mapper.PrLineMapper;
import com.srm.sourcing.mapper.PurchaseRequisitionMapper;
import com.srm.sourcing.service.PrService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** IR 控制塔：采购申请 / 订单 / ASN 快照，以及采购建议、审批与催单。 */
@RestController
@RequestMapping("/api/open/ir")
@RequiredArgsConstructor
public class OpenIrController {
    private final PrService prService;
    private final PurchaseOrderService poService;
    private final PurchaseRequisitionMapper prMapper;
    private final PrLineMapper prLineMapper;
    private final PurchaseOrderMapper poMapper;
    private final PoLineMapper poLineMapper;
    private final AsnMapper asnMapper;
    private final AsnLineMapper asnLineMapper;

    @Value("${srm.integration.api-key:srm-wms-key}")
    private String apiKey;

    @Data
    public static class SuggestReq {
        private String type;
        private String targetKey;
        private String sku;
        private BigDecimal qty;
        private String plantCode;
        private String remark;
        private Map<String, Object> params;
    }

    @GetMapping("/snapshots")
    public R<Map<String, Object>> snapshots(
            @RequestHeader(value = "X-Api-Key", required = false) String key) {
        checkKey(key);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PurchaseRequisition pr : prMapper.selectList(null)) {
            PrLine line = firstPrLine(pr.getId());
            Map<String, Object> row = row("PR", pr.getCode(), pr.getStatus(),
                    line == null ? null : line.getMaterialCode(),
                    line == null ? BigDecimal.ZERO : line.getQty(),
                    null, pr.getPlantCode(), "采购申请 " + pr.getCode());
            rows.add(row);
        }
        for (PurchaseOrder po : poMapper.selectList(null)) {
            PoLine line = firstPoLine(po.getId());
            Map<String, Object> row = row("PO", po.getCode(), po.getStatus(),
                    line == null ? null : line.getMaterialCode(),
                    line == null ? BigDecimal.ONE : line.getQty(),
                    po.getTotalAmount(), po.getPlantCode(),
                    "采购订单 " + po.getCode());
            row.put("expectedDate", po.getExpectedDate());
            row.put("supplierCode", po.getSupplierCode());
            rows.add(row);
        }
        for (Asn asn : asnMapper.selectList(null)) {
            AsnLine line = firstAsnLine(asn.getId());
            String original = asn.getStatus();
            String status = late(asn) ? "DELAYED" : original;
            Map<String, Object> row = row("ASN", asn.getCode(), status,
                    line == null ? null : line.getMaterialCode(),
                    asn.getTotalQty() == null && line != null ? line.getQty() : asn.getTotalQty(),
                    null, asn.getPlantCode(),
                    "发货通知 " + asn.getCode());
            row.put("poCode", asn.getPoCode());
            row.put("refCode", asn.getPoCode());
            row.put("expectedDate", asn.getExpectedDate());
            row.put("originalStatus", original);
            row.put("supplierCode", asn.getSupplierCode());
            rows.add(row);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("system", "SRM");
        data.put("snapshots", rows);
        return R.ok(data);
    }

    @PostMapping("/purchase-suggest")
    public R<PurchaseRequisition> suggest(
            @RequestHeader(value = "X-Api-Key", required = false) String key,
            @RequestBody SuggestReq req) {
        checkKey(key);
        return R.ok(prService.suggestFromControlTower(
                sku(req), qty(req), plant(req), remark(req)));
    }

    @PostMapping("/actions")
    public R<Object> actions(
            @RequestHeader(value = "X-Api-Key", required = false) String key,
            @RequestBody SuggestReq req) {
        checkKey(key);
        String type = req.getType() == null ? "SRM_PURCHASE_SUGGEST" : req.getType();
        if ("SRM_PURCHASE_SUGGEST".equals(type)) {
            return R.ok(prService.suggestFromControlTower(
                    sku(req), qty(req), plant(req), remark(req)));
        }
        if ("SRM_SUBMIT_PR".equals(type)) {
            return R.ok(prService.submit(prId(req)));
        }
        if ("SRM_APPROVE_PR".equals(type)) {
            return R.ok(prService.approve(prId(req)));
        }
        if ("SRM_EXPEDITE_PO".equals(type)) {
            return R.ok(poService.expediteByCode(poCode(req), expediteRemark(req)));
        }
        throw new BizException("不支持的 IR 指令: " + type);
    }

    private Long prId(SuggestReq req) {
        String code = first(req.getTargetKey(),
                req.getParams() == null ? null : string(req.getParams().get("code")));
        PurchaseRequisition pr = prMapper.selectOne(new LambdaQueryWrapper<PurchaseRequisition>()
                .eq(PurchaseRequisition::getCode, code));
        if (pr == null) {
            throw new BizException("采购申请不存在: " + code);
        }
        return pr.getId();
    }

    private String poCode(SuggestReq req) {
        return first(req.getTargetKey(),
                req.getParams() == null ? null : string(req.getParams().get("poCode")),
                req.getParams() == null ? null : string(req.getParams().get("code")));
    }

    private String expediteRemark(SuggestReq req) {
        if (!blank(req.getRemark())) {
            return req.getRemark();
        }
        if (req.getParams() != null && req.getParams().get("reason") != null) {
            return "IR 控制塔催单: " + req.getParams().get("reason");
        }
        return "IR 控制塔催单";
    }

    private PrLine firstPrLine(Long prId) {
        List<PrLine> lines = prLineMapper.selectList(new LambdaQueryWrapper<PrLine>()
                .eq(PrLine::getPrId, prId).orderByAsc(PrLine::getLineNo));
        return lines.isEmpty() ? null : lines.get(0);
    }

    private PoLine firstPoLine(Long poId) {
        List<PoLine> lines = poLineMapper.selectList(new LambdaQueryWrapper<PoLine>()
                .eq(PoLine::getPoId, poId).orderByAsc(PoLine::getLineNo));
        return lines.isEmpty() ? null : lines.get(0);
    }

    private AsnLine firstAsnLine(Long asnId) {
        List<AsnLine> lines = asnLineMapper.selectList(new LambdaQueryWrapper<AsnLine>()
                .eq(AsnLine::getAsnId, asnId).orderByAsc(AsnLine::getLineNo));
        return lines.isEmpty() ? null : lines.get(0);
    }

    private static boolean late(Asn asn) {
        if (asn.getExpectedDate() == null) {
            return "DELAYED".equals(asn.getStatus());
        }
        if (Arrays.asList("RECEIVED", "POSTED", "CANCELLED").contains(asn.getStatus())) {
            return false;
        }
        return asn.getExpectedDate().isBefore(LocalDate.now());
    }

    private void checkKey(String key) {
        if (key == null || !key.equals(apiKey)) {
            throw new BizException("无效的 API Key");
        }
    }

    private String sku(SuggestReq req) {
        String value = req.getSku();
        if (blank(value) && req.getParams() != null && req.getParams().get("sku") != null) {
            value = String.valueOf(req.getParams().get("sku"));
        }
        if (blank(value)) {
            value = req.getTargetKey();
        }
        return value;
    }

    private BigDecimal qty(SuggestReq req) {
        if (req.getQty() != null) {
            return req.getQty();
        }
        if (req.getParams() != null && req.getParams().get("qty") != null) {
            return new BigDecimal(String.valueOf(req.getParams().get("qty")));
        }
        if (req.getParams() != null && req.getParams().get("suggestQty") != null) {
            return new BigDecimal(String.valueOf(req.getParams().get("suggestQty")));
        }
        return BigDecimal.ONE;
    }

    private String plant(SuggestReq req) {
        if (!blank(req.getPlantCode())) {
            return req.getPlantCode();
        }
        if (req.getParams() != null && req.getParams().get("plantCode") != null) {
            return String.valueOf(req.getParams().get("plantCode"));
        }
        return "P001";
    }

    private String remark(SuggestReq req) {
        if (!blank(req.getRemark())) {
            return req.getRemark();
        }
        return "IR 控制塔采购建议";
    }

    private static Map<String, Object> row(
            String dataType, String bizKey, String status, String sku,
            BigDecimal qty, BigDecimal amount, String plantCode, String title) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("dataType", dataType);
        row.put("bizKey", bizKey);
        row.put("status", status);
        row.put("sku", sku);
        row.put("qty", qty);
        row.put("amount", amount);
        row.put("plantCode", plantCode);
        row.put("title", title);
        return row;
    }

    private static String first(String... values) {
        for (String value : values) {
            if (!blank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty() || "null".equals(value);
    }
}
