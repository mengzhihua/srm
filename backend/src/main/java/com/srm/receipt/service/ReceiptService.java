package com.srm.receipt.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.srm.basic.entity.Material;
import com.srm.basic.mapper.MaterialMapper;
import com.srm.common.BizException;
import com.srm.common.CodeGenerator;
import com.srm.delivery.entity.Asn;
import com.srm.delivery.entity.AsnLine;
import com.srm.delivery.mapper.AsnLineMapper;
import com.srm.delivery.mapper.AsnMapper;
import com.srm.evaluation.service.EvaluationService;
import com.srm.integration.client.SapClient;
import com.srm.integration.service.IntegrationLogService;
import com.srm.purchase.entity.PoLine;
import com.srm.purchase.entity.PurchaseOrder;
import com.srm.purchase.mapper.PoLineMapper;
import com.srm.purchase.mapper.PurchaseOrderMapper;
import com.srm.purchase.service.PurchaseOrderService;
import com.srm.receipt.entity.GoodsReceipt;
import com.srm.receipt.entity.GrLine;
import com.srm.receipt.mapper.GoodsReceiptMapper;
import com.srm.receipt.mapper.GrLineMapper;
import com.srm.system.auth.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 收货处理：WMS 回传数量 -> 更新 ASN/PO -> 生成收货单 GR -> SAP 记账(MIGO 101) -> 触发供应商考核。
 * 幂等：同一 ASN 已生成过 GR 后再次收到回传不重复建单。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptService {
    private final AsnMapper asnMapper;
    private final AsnLineMapper asnLineMapper;
    private final GoodsReceiptMapper grMapper;
    private final GrLineMapper grLineMapper;
    private final PoLineMapper poLineMapper;
    private final PurchaseOrderMapper poMapper;
    private final MaterialMapper materialMapper;
    private final PurchaseOrderService poService;
    private final SapClient sapClient;
    private final IntegrationLogService logService;
    private final EvaluationService evaluationService;
    private final CodeGenerator codeGenerator;

    public GoodsReceipt load(Long id) {
        GoodsReceipt gr = grMapper.selectById(id);
        if (gr == null) {
            throw new BizException("收货单不存在: " + id);
        }
        gr.setLines(grLineMapper.selectList(new LambdaQueryWrapper<GrLine>().eq(GrLine::getGrId, id)));
        return gr;
    }

    public Page<GoodsReceipt> page(long current, long size, String status, String keyword) {
        QueryWrapper<GoodsReceipt> qw = new QueryWrapper<>();
        String mine = CurrentUser.supplierCode();
        qw.eq(mine != null, "supplier_code", mine)
                .eq(StringUtils.isNotBlank(status), "status", status)
                .and(StringUtils.isNotBlank(keyword),
                        w -> w.like("code", keyword).or().like("po_code", keyword).or().like("asn_code", keyword))
                .orderByDesc("id");
        return grMapper.selectPage(new Page<>(current, size), qw);
    }

    /**
     * 处理 WMS 收货回传。
     * @param delta    true=回传为本次增量（回调/手工）；false=回传为 WMS 累计值（轮询）
     * @param complete true=收货完成，生成 GR 并过账 SAP；false=仅同步行数量（部分收货）
     */
    @Transactional
    public GoodsReceipt processWmsReceipt(Asn asn, WmsReceiptPayload payload, String source,
                                          boolean delta, boolean complete) {
        if ("CANCELLED".equals(asn.getStatus())) {
            throw new BizException("ASN 已取消: " + asn.getCode());
        }
        // 幂等：该 ASN 已建过 GR 则直接返回，不重复生成
        List<GoodsReceipt> existing = grMapper.selectList(new LambdaQueryWrapper<GoodsReceipt>()
                .eq(GoodsReceipt::getAsnId, asn.getId()).orderByDesc(GoodsReceipt::getId));
        if (!existing.isEmpty() && ("RECEIVED".equals(asn.getStatus()) || "POSTED".equals(asn.getStatus()))) {
            return load(existing.get(0).getId());
        }

        Map<String, String> wmsToSrm = materialMapper.selectList(null).stream()
                .filter(m -> m.getWmsItemCode() != null)
                .collect(Collectors.toMap(Material::getWmsItemCode, Material::getCode, (a, b) -> a));
        List<AsnLine> asnLines = asnLineMapper.selectList(new LambdaQueryWrapper<AsnLine>()
                .eq(AsnLine::getAsnId, asn.getId()).orderByAsc(AsnLine::getLineNo));

        // 按 (srmMaterialCode, lotNo) 匹配 ASN 行；同物料多行按顺序分摊
        Map<AsnLine, WmsReceiptPayload.Line> matched = new HashMap<>();
        for (WmsReceiptPayload.Line pl : payload.getLines() == null ? java.util.Collections.<WmsReceiptPayload.Line>emptyList() : payload.getLines()) {
            String matCode = wmsToSrm.getOrDefault(pl.getItemCode(), pl.getItemCode());
            BigDecimal remainRecv = nz(pl.getReceivedQty());
            BigDecimal remainRej = nz(pl.getRejectedQty());
            for (AsnLine al : asnLines) {
                if (!al.getMaterialCode().equals(matCode)) {
                    continue;
                }
                if (pl.getLotNo() != null && !pl.getLotNo().isEmpty()
                        && al.getLotNo() != null && !al.getLotNo().isEmpty()
                        && !al.getLotNo().equals(pl.getLotNo())) {
                    continue;
                }
                if (matched.containsKey(al)) {
                    continue;
                }
                if (remainRecv.signum() <= 0 && remainRej.signum() <= 0) {
                    break;
                }
                matched.put(al, pl);
                if (delta) {
                    al.setReceivedQty(nz(al.getReceivedQty()).add(remainRecv));
                    al.setRejectedQty(nz(al.getRejectedQty()).add(remainRej));
                } else {
                    // 轮询给累计值：该行累计收到的部分按剩余容量截取
                    BigDecimal capacity = al.getQty();
                    al.setReceivedQty(remainRecv.min(capacity));
                    al.setRejectedQty(remainRej);
                }
                remainRecv = BigDecimal.ZERO;
                remainRej = BigDecimal.ZERO;
                asnLineMapper.updateById(al);
                break;
            }
            if (!matched.values().contains(pl)) {
                log.warn("WMS 回传行未匹配到 ASN 行: itemCode={} lotNo={}", pl.getItemCode(), pl.getLotNo());
            }
        }

        // 汇总 ASN 头
        asnLines = asnLineMapper.selectList(new LambdaQueryWrapper<AsnLine>()
                .eq(AsnLine::getAsnId, asn.getId()).orderByAsc(AsnLine::getLineNo));
        asn.setReceivedQty(asnLines.stream().map(l -> nz(l.getReceivedQty())).reduce(BigDecimal.ZERO, BigDecimal::add));
        asn.setRejectedQty(asnLines.stream().map(l -> nz(l.getRejectedQty())).reduce(BigDecimal.ZERO, BigDecimal::add));
        asn.setReceivedAt(payload.getReceivedAt() != null ? payload.getReceivedAt() : LocalDateTime.now());
        boolean allReceived = asnLines.stream().allMatch(l -> nz(l.getReceivedQty()).signum() > 0);
        // 仅回调/手工(delta)且所有行都有收货时视作完成；轮询只信 WMS 的完成状态，避免部分收货时提前建 GR
        boolean done = complete || (delta && allReceived);
        asn.setStatus(done ? "RECEIVED" : "RECEIVING");
        asnMapper.updateById(asn);

        if (!done) {
            return null; // 部分收货：只同步数量
        }

        // 生成收货单
        PurchaseOrder po = poMapper.selectById(asn.getPoId());
        Map<Long, PoLine> poLines = poLineMapper.selectList(new LambdaQueryWrapper<PoLine>()
                        .eq(PoLine::getPoId, asn.getPoId())).stream()
                .collect(Collectors.toMap(PoLine::getId, l -> l));

        GoodsReceipt gr = new GoodsReceipt();
        gr.setCode(codeGenerator.next("GR"));
        gr.setAsnId(asn.getId());
        gr.setAsnCode(asn.getCode());
        gr.setPoId(asn.getPoId());
        gr.setPoCode(asn.getPoCode());
        gr.setSupplierCode(asn.getSupplierCode());
        gr.setPlantCode(asn.getPlantCode());
        gr.setStatus("PENDING");
        gr.setReceivedAt(asn.getReceivedAt());
        gr.setSource(source);
        gr.setTotalReceivedQty(asn.getReceivedQty());
        gr.setTotalRejectedQty(asn.getRejectedQty());
        grMapper.insert(gr);

        for (AsnLine al : asnLines) {
            if (nz(al.getReceivedQty()).signum() <= 0 && nz(al.getRejectedQty()).signum() <= 0) {
                continue;
            }
            PoLine pl = poLines.get(al.getPoLineId());
            GrLine gl = new GrLine();
            gl.setGrId(gr.getId());
            gl.setAsnLineId(al.getId());
            gl.setPoLineId(al.getPoLineId());
            gl.setMaterialCode(al.getMaterialCode());
            gl.setReceivedQty(nz(al.getReceivedQty()));
            gl.setRejectedQty(nz(al.getRejectedQty()));
            gl.setAcceptedQty(nz(al.getReceivedQty()).subtract(nz(al.getRejectedQty())));
            gl.setLotNo(al.getLotNo());
            if (pl != null) {
                gl.setAmount(gl.getAcceptedQty().multiply(nz(pl.getPrice())));
                pl.setReceivedQty(nz(pl.getReceivedQty()).add(gl.getReceivedQty()));
                pl.setRejectedQty(nz(pl.getRejectedQty()).add(gl.getRejectedQty()));
                poLineMapper.updateById(pl);
            }
            grLineMapper.insert(gl);
        }
        if (po != null) {
            poService.refreshStatus(po, poLineMapper.selectList(
                    new LambdaQueryWrapper<PoLine>().eq(PoLine::getPoId, po.getId())));
        }

        postToSap(gr, asn);
        GoodsReceipt saved = load(gr.getId());
        try {
            evaluationService.evaluateReceipt(saved, payload);
        } catch (RuntimeException e) {
            log.warn("供应商考核计算失败: {}", e.getMessage());
        }
        return saved;
    }

    /** SAP 记账；失败置 POST_FAILED（可重试） */
    private void postToSap(GoodsReceipt gr, Asn asn) {
        GoodsReceipt full = load(gr.getId());
        try {
            String doc = logService.execute("SAP", IntegrationLogService.SAP_POST_GR,
                    "GR", gr.getId(), gr.getCode(), full, () -> sapClient.postGoodsReceipt(full));
            full.setSapMaterialDoc(doc);
            full.setSapPostedAt(LocalDateTime.now());
            full.setStatus("POSTED");
            asn.setStatus("POSTED");
        } catch (RuntimeException e) {
            full.setStatus("POST_FAILED");
        }
        grMapper.updateById(full);
        asnMapper.updateById(asn);
    }

    /** 手动重试过账 */
    @Transactional
    public GoodsReceipt retryPost(Long grId) {
        GoodsReceipt gr = grMapper.selectById(grId);
        if (gr == null) {
            throw new BizException("收货单不存在: " + grId);
        }
        if (!"POST_FAILED".equals(gr.getStatus()) && !"PENDING".equals(gr.getStatus())) {
            throw new BizException("仅 POST_FAILED/PENDING 可重试，当前: " + gr.getStatus());
        }
        Asn asn = asnMapper.selectById(gr.getAsnId());
        postToSap(gr, asn);
        return load(grId);
    }

    static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
