package com.srm.integration.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.srm.common.BizException;
import com.srm.common.R;
import com.srm.delivery.entity.Asn;
import com.srm.delivery.mapper.AsnMapper;
import com.srm.delivery.service.AsnService;
import com.srm.evaluation.entity.SupplierEvaluation;
import com.srm.evaluation.entity.SupplierEvaluationRecord;
import com.srm.evaluation.service.EvaluationService;
import com.srm.integration.entity.IntegrationLog;
import com.srm.integration.mapper.IntegrationLogMapper;
import com.srm.integration.service.IntegrationLogService;
import com.srm.invoice.service.InvoiceService;
import com.srm.purchase.service.PurchaseOrderService;
import com.srm.receipt.entity.GoodsReceipt;
import com.srm.receipt.service.ReceiptService;
import com.srm.receipt.service.WmsReceiptPayload;
import com.srm.basic.entity.Supplier;
import com.srm.basic.mapper.SupplierMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/** 集成接口：WMS 回调/推送（X-Api-Key 免登录）+ 集成日志查询/重试（登录用户） */
@RestController
@RequestMapping("/api/integration")
@RequiredArgsConstructor
public class IntegrationController {
    private final AsnMapper asnMapper;
    private final AsnService asnService;
    private final ReceiptService receiptService;
    private final EvaluationService evaluationService;
    private final PurchaseOrderService poService;
    private final InvoiceService invoiceService;
    private final SupplierMapper supplierMapper;
    private final IntegrationLogMapper logMapper;
    private final IntegrationLogService logService;

    @Value("${srm.integration.api-key:srm-wms-key}")
    private String apiKey;

    private void checkKey(String key) {
        if (key == null || !key.equals(apiKey)) {
            throw new BizException("无效的 API Key");
        }
    }

    /** WMS 收货回调：按 wmsAsnCode 或 externalNo(SRM ASN单号) 定位 ASN，生成 GR 并过账 SAP */
    @PostMapping("/wms/receipt")
    public R<GoodsReceipt> wmsReceipt(@RequestHeader(value = "X-Api-Key", required = false) String key,
                                      @RequestBody WmsReceiptPayload payload) {
        checkKey(key);
        Asn asn = findAsn(payload);
        try {
            GoodsReceipt gr = receiptService.processWmsReceipt(asn, payload, "WMS_CALLBACK", true, true);
            logService.inbound("WMS", IntegrationLogService.WMS_RECEIPT_IN,
                    "ASN", asn.getId(), asn.getCode(), payload, "SUCCESS", null);
            return R.ok(gr);
        } catch (RuntimeException e) {
            logService.inbound("WMS", IntegrationLogService.WMS_RECEIPT_IN,
                    "ASN", asn != null ? asn.getId() : null,
                    asn != null ? asn.getCode() : payload.getExternalNo(), payload, "FAILED", e.getMessage());
            throw e;
        }
    }

    private Asn findAsn(WmsReceiptPayload payload) {
        Asn asn = null;
        if (StringUtils.isNotBlank(payload.getExternalNo())) {
            asn = asnMapper.selectOne(new LambdaQueryWrapper<Asn>().eq(Asn::getCode, payload.getExternalNo()));
        }
        if (asn == null && StringUtils.isNotBlank(payload.getWmsAsnCode())) {
            asn = asnMapper.selectOne(new LambdaQueryWrapper<Asn>().eq(Asn::getWmsAsnCode, payload.getWmsAsnCode()));
        }
        if (asn == null) {
            throw new BizException("未找到对应 ASN: " + payload.getWmsAsnCode() + "/" + payload.getExternalNo());
        }
        return asn;
    }

    @Data
    public static class WmsEvalPush {
        /** WMS 侧供应商编码（映射 srm_supplier.wms_supplier_code） */
        private String supplierCode;
        private String asnCode;
        private String externalNo;
        private String grCode;
        private BigDecimal score;
        private String remark;
        private List<Item> items;

        @Data
        public static class Item {
            private String key;
            private String value;
        }
    }

    /** WMS 推送供应商考核信息 */
    @PostMapping("/wms/evaluation")
    public R<SupplierEvaluationRecord> wmsEvaluation(@RequestHeader(value = "X-Api-Key", required = false) String key,
                                                     @RequestBody WmsEvalPush push) {
        checkKey(key);
        // wms supplierCode -> srm supplierCode
        String srmCode = push.getSupplierCode();
        if (srmCode != null) {
            Supplier s = supplierMapper.selectOne(new LambdaQueryWrapper<Supplier>()
                    .eq(Supplier::getWmsSupplierCode, srmCode));
            if (s != null) {
                srmCode = s.getCode();
            }
        }
        String asnCode = push.getAsnCode() != null ? push.getAsnCode() : push.getExternalNo();
        String remark = push.getRemark();
        if (push.getItems() != null && !push.getItems().isEmpty()) {
            StringBuilder sb = new StringBuilder(remark == null ? "" : remark + " | ");
            push.getItems().forEach(i -> sb.append(i.getKey()).append("=").append(i.getValue()).append(" "));
            remark = sb.toString().trim();
        }
        SupplierEvaluationRecord r = evaluationService.applyWmsEvaluation(
                srmCode, asnCode, push.getGrCode(), push.getScore(), remark);
        logService.inbound("WMS", IntegrationLogService.WMS_EVAL_IN,
                "EVAL", r.getId(), r.getGrCode(), push, "SUCCESS", null);
        return R.ok(r);
    }

    // ---------------------------------------------------------------- 集成日志

    @GetMapping("/logs/page")
    public R<Page<IntegrationLog>> logs(@RequestParam(defaultValue = "1") long current,
                                        @RequestParam(defaultValue = "20") long size,
                                        @RequestParam(required = false) String system,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(required = false) String action,
                                        @RequestParam(required = false) String keyword) {
        QueryWrapper<IntegrationLog> qw = new QueryWrapper<>();
        qw.eq(StringUtils.isNotBlank(system), "system", system)
                .eq(StringUtils.isNotBlank(status), "status", status)
                .eq(StringUtils.isNotBlank(action), "action", action)
                .and(StringUtils.isNotBlank(keyword), w -> w.like("biz_code", keyword))
                .orderByDesc("id");
        return R.ok(logMapper.selectPage(new Page<>(current, size), qw));
    }

    /** 按原 action 重新执行失败调用 */
    @PostMapping("/logs/{id}/retry")
    public R<Object> retry(@PathVariable Long id) {
        IntegrationLog l = logMapper.selectById(id);
        if (l == null) {
            throw new BizException("日志不存在: " + id);
        }
        if (!"FAILED".equals(l.getStatus())) {
            throw new BizException("仅失败的调用可重试");
        }
        logService.markRetried(id);
        switch (l.getAction()) {
            case IntegrationLogService.SAP_CREATE_PO:
                return R.ok(poService.sendToSap(l.getBizId()));
            case IntegrationLogService.SAP_POST_GR:
                return R.ok(receiptService.retryPost(l.getBizId()));
            case IntegrationLogService.SAP_POST_INVOICE:
                return R.ok(invoiceService.postToSap(l.getBizId()));
            case IntegrationLogService.SAP_SYNC_EVAL:
                return R.ok(evaluationService.syncSap(l.getBizId()));
            case IntegrationLogService.WMS_CREATE_ASN:
                return R.ok(asnService.syncToWms(l.getBizId()));
            default:
                throw new BizException("不支持重试的动作: " + l.getAction());
        }
    }
}
