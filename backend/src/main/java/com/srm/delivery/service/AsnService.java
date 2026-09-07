package com.srm.delivery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.srm.common.BizException;
import com.srm.common.CodeGenerator;
import com.srm.delivery.entity.Asn;
import com.srm.delivery.entity.AsnLine;
import com.srm.delivery.mapper.AsnLineMapper;
import com.srm.delivery.mapper.AsnMapper;
import com.srm.integration.client.WmsAsnResult;
import com.srm.integration.client.WmsClient;
import com.srm.integration.service.IntegrationLogService;
import com.srm.purchase.entity.PoLine;
import com.srm.purchase.entity.PurchaseOrder;
import com.srm.purchase.mapper.PoLineMapper;
import com.srm.purchase.mapper.PurchaseOrderMapper;
import com.srm.receipt.service.ReceiptService;
import com.srm.receipt.service.WmsReceiptPayload;
import com.srm.system.auth.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 供应商发货通知 ASN：创建 -> 同步 WMS -> 收货回传 -> SAP 记账 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsnService {
    private final AsnMapper asnMapper;
    private final AsnLineMapper lineMapper;
    private final PurchaseOrderMapper poMapper;
    private final PoLineMapper poLineMapper;
    private final WmsClient wmsClient;
    private final IntegrationLogService logService;
    private final CodeGenerator codeGenerator;
    @Lazy
    private final ReceiptService receiptService;

    @Value("${srm.delivery.auto-sync:true}")
    private boolean autoSync;

    @Value("${srm.purchase.over-delivery-tolerance:0}")
    private BigDecimal overDeliveryTolerance;

    public Asn load(Long id) {
        Asn asn = asnMapper.selectById(id);
        if (asn == null) {
            throw new BizException("ASN 不存在: " + id);
        }
        asn.setLines(lineMapper.selectList(new LambdaQueryWrapper<AsnLine>()
                .eq(AsnLine::getAsnId, id).orderByAsc(AsnLine::getLineNo)));
        CurrentUser.checkSupplier(asn.getSupplierCode());
        return asn;
    }

    public Page<Asn> page(long current, long size, String status, String poCode, String supplierCode) {
        QueryWrapper<Asn> qw = new QueryWrapper<>();
        String mine = CurrentUser.supplierCode();
        qw.eq(mine != null, "supplier_code", mine)
                .eq(StringUtils.isNotBlank(status), "status", status)
                .eq(StringUtils.isNotBlank(supplierCode), "supplier_code", supplierCode)
                .eq(StringUtils.isNotBlank(poCode), "po_code", poCode)
                .orderByDesc("id");
        return asnMapper.selectPage(new Page<>(current, size), qw);
    }

    /** 供应商创建发货单；auto-sync 时立即下发 WMS（失败置 SYNC_FAILED 不影响创建） */
    @Transactional
    public Asn create(Asn asn) {
        PurchaseOrder po = poMapper.selectById(asn.getPoId());
        if (po == null) {
            throw new BizException("采购订单不存在: " + asn.getPoId());
        }
        CurrentUser.checkSupplier(po.getSupplierCode());
        if (!Arrays.asList("SENT", "CONFIRMED", "PARTIALLY_RECEIVED").contains(po.getStatus())) {
            throw new BizException("采购订单状态不允许发货: " + po.getStatus());
        }
        if (asn.getLines() == null || asn.getLines().isEmpty()) {
            throw new BizException("发货明细不能为空");
        }
        Map<Long, PoLine> poLines = poLineMapper.selectList(new LambdaQueryWrapper<PoLine>()
                        .eq(PoLine::getPoId, po.getId())).stream()
                .collect(Collectors.toMap(PoLine::getId, l -> l));

        asn.setId(null);
        asn.setCode(codeGenerator.next("ASN"));
        asn.setPoCode(po.getCode());
        asn.setSupplierCode(po.getSupplierCode());
        asn.setPlantCode(po.getPlantCode());
        asn.setStatus("CREATED");
        asn.setReceivedQty(BigDecimal.ZERO);
        asn.setRejectedQty(BigDecimal.ZERO);
        BigDecimal total = BigDecimal.ZERO;
        int i = 1;
        for (AsnLine l : asn.getLines()) {
            PoLine pl = poLines.get(l.getPoLineId());
            if (pl == null) {
                throw new BizException("订单行不存在: " + l.getPoLineId());
            }
            if (l.getQty() == null || l.getQty().signum() <= 0) {
                throw new BizException("发货数量必须大于 0");
            }
            // 超交校验：累计发货 <= 订单数 * (1 + tolerance%)
            BigDecimal allowed = pl.getQty().multiply(BigDecimal.ONE.add(
                    overDeliveryTolerance.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)));
            if (nz(pl.getShippedQty()).add(l.getQty()).compareTo(allowed) > 0) {
                throw new BizException("物料 " + pl.getMaterialCode() + " 累计发货超过订单数量(含容差)");
            }
            l.setId(null);
            l.setLineNo(i++);
            l.setMaterialCode(pl.getMaterialCode());
            l.setReceivedQty(BigDecimal.ZERO);
            l.setRejectedQty(BigDecimal.ZERO);
            total = total.add(l.getQty());
        }
        asn.setTotalQty(total);
        asnMapper.insert(asn);
        for (AsnLine l : asn.getLines()) {
            l.setAsnId(asn.getId());
            lineMapper.insert(l);
            // 回写 PO 行 shippedQty
            PoLine pl = poLines.get(l.getPoLineId());
            pl.setShippedQty(nz(pl.getShippedQty()).add(l.getQty()));
            poLineMapper.updateById(pl);
        }
        if (autoSync) {
            try {
                doSync(asn.getId());
            } catch (RuntimeException e) {
                log.warn("ASN {} 自动同步 WMS 失败: {}", asn.getCode(), e.getMessage());
                Asn db = asnMapper.selectById(asn.getId());
                db.setStatus("SYNC_FAILED");
                asnMapper.updateById(db);
            }
        }
        return load(asn.getId());
    }

    /** 同步到 WMS（显式调用，失败抛异常） */
    @Transactional
    public Asn syncToWms(Long id) {
        return doSync(id);
    }

    private Asn doSync(Long id) {
        Asn asn = load(id);
        if (!Arrays.asList("CREATED", "SYNC_FAILED").contains(asn.getStatus())) {
            throw new BizException("当前状态不可同步 WMS: " + asn.getStatus());
        }
        WmsAsnResult r = logService.execute("WMS", IntegrationLogService.WMS_CREATE_ASN,
                "ASN", asn.getId(), asn.getCode(), asn, () -> wmsClient.createAsn(asn));
        asn.setWmsAsnId(r.getId());
        asn.setWmsAsnCode(r.getCode());
        asn.setSyncedAt(LocalDateTime.now());
        asn.setStatus("SYNCED");
        asnMapper.updateById(asn);
        return load(id);
    }

    /** 取消（仅未同步/同步失败可取消），回退 PO 行 shippedQty */
    @Transactional
    public Asn cancel(Long id) {
        Asn asn = load(id);
        CurrentUser.checkSupplier(asn.getSupplierCode());
        if (!Arrays.asList("CREATED", "SYNC_FAILED").contains(asn.getStatus())) {
            throw new BizException("已同步/已收货的 ASN 不可取消，当前: " + asn.getStatus());
        }
        for (AsnLine l : asn.getLines()) {
            PoLine pl = poLineMapper.selectById(l.getPoLineId());
            if (pl != null) {
                pl.setShippedQty(nz(pl.getShippedQty()).subtract(l.getQty()).max(BigDecimal.ZERO));
                poLineMapper.updateById(pl);
            }
        }
        asn.setStatus("CANCELLED");
        asnMapper.updateById(asn);
        return load(id);
    }

    /** 立即从 WMS 拉取一次收货进度 */
    @Transactional
    public Asn pullWms(Long id) {
        Asn asn = load(id);
        if (asn.getWmsAsnId() == null) {
            throw new BizException("ASN 尚未同步 WMS");
        }
        WmsAsnResult w = logService.execute("WMS", IntegrationLogService.WMS_GET_ASN,
                "ASN", asn.getId(), asn.getCode(), asn.getWmsAsnId(), () -> wmsClient.getAsn(asn.getWmsAsnId()));
        if (w == null) {
            throw new BizException("WMS 中未找到 ASN: " + asn.getWmsAsnId());
        }
        receiptService.processWmsReceipt(asn, toPayload(asn, w), "WMS_POLL", false, isWmsDone(w.getStatus()));
        return load(id);
    }

    /** 手工录入收货（mock 演示 / ADMIN、BUYER） */
    @Transactional
    public com.srm.receipt.entity.GoodsReceipt manualReceipt(Long id, WmsReceiptPayload payload) {
        CurrentUser.requireBuyerSide();
        Asn asn = load(id);
        return receiptService.processWmsReceipt(asn, payload, "MANUAL", true, true);
    }

    /** WMS 收货是否完成：RECEIVED(收齐或关闭收货) / PUTAWAY / CLOSED */
    public static boolean isWmsDone(String wmsStatus) {
        return Arrays.asList("RECEIVED", "PUTAWAY", "CLOSED").contains(wmsStatus);
    }

    /** WMS 查询结果 -> 收货回传结构（行数量为累计值） */
    public static WmsReceiptPayload toPayload(Asn asn, WmsAsnResult w) {
        WmsReceiptPayload p = new WmsReceiptPayload();
        p.setWmsAsnCode(w.getCode());
        p.setExternalNo(asn.getCode());
        p.setReceivedAt(LocalDateTime.now());
        p.setLines(w.getLines() == null ? java.util.Collections.emptyList() : w.getLines().stream().map(l -> {
            WmsReceiptPayload.Line line = new WmsReceiptPayload.Line();
            line.setItemCode(l.getItemCode());
            line.setLotNo(l.getLotNo());
            line.setReceivedQty(l.getReceivedQty());
            line.setRejectedQty(l.getRejectedQty());
            return line;
        }).collect(Collectors.toList()));
        return p;
    }

    static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
