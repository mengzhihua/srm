package com.srm.integration.controller;

import com.srm.common.BizException;
import com.srm.common.R;
import com.srm.sourcing.entity.PurchaseRequisition;
import com.srm.sourcing.service.PrService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

/** IR 控制塔采购建议入口（X-Api-Key）。 */
@RestController
@RequestMapping("/api/open/ir")
@RequiredArgsConstructor
public class OpenIrController {
    private final PrService prService;

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

    @PostMapping("/purchase-suggest")
    public R<PurchaseRequisition> suggest(
            @RequestHeader(value = "X-Api-Key", required = false) String key,
            @RequestBody SuggestReq req) {
        checkKey(key);
        return R.ok(prService.suggestFromControlTower(
                sku(req), qty(req), plant(req), remark(req)));
    }

    @PostMapping("/actions")
    public R<PurchaseRequisition> actions(
            @RequestHeader(value = "X-Api-Key", required = false) String key,
            @RequestBody SuggestReq req) {
        checkKey(key);
        if (req.getType() != null && !"SRM_PURCHASE_SUGGEST".equals(req.getType())) {
            throw new BizException("不支持的 IR 指令: " + req.getType());
        }
        return R.ok(prService.suggestFromControlTower(
                sku(req), qty(req), plant(req), remark(req)));
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

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty() || "null".equals(value);
    }
}
