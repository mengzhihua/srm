package com.srm.invoice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.srm.common.BizException;
import com.srm.common.CodeGenerator;
import com.srm.integration.client.SapClient;
import com.srm.integration.service.IntegrationLogService;
import com.srm.invoice.entity.Invoice;
import com.srm.invoice.entity.InvoiceLine;
import com.srm.invoice.mapper.InvoiceLineMapper;
import com.srm.invoice.mapper.InvoiceMapper;
import com.srm.purchase.entity.PoLine;
import com.srm.purchase.entity.PurchaseOrder;
import com.srm.purchase.mapper.PoLineMapper;
import com.srm.purchase.mapper.PurchaseOrderMapper;
import com.srm.system.auth.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 发票 3-way match（简版）：SUBMITTED -> MATCHED/MISMATCH -> APPROVED -> POSTED */
@Service
@RequiredArgsConstructor
public class InvoiceService {
    private final InvoiceMapper invoiceMapper;
    private final InvoiceLineMapper lineMapper;
    private final PurchaseOrderMapper poMapper;
    private final PoLineMapper poLineMapper;
    private final SapClient sapClient;
    private final IntegrationLogService logService;
    private final CodeGenerator codeGenerator;

    @Value("${srm.invoice.price-tolerance:0.01}")
    private BigDecimal priceTolerance;

    public Invoice load(Long id) {
        Invoice inv = invoiceMapper.selectById(id);
        if (inv == null) {
            throw new BizException("发票不存在: " + id);
        }
        inv.setLines(lineMapper.selectList(new LambdaQueryWrapper<InvoiceLine>()
                .eq(InvoiceLine::getInvoiceId, id)));
        CurrentUser.checkSupplier(inv.getSupplierCode());
        return inv;
    }

    public Page<Invoice> page(long current, long size, String status, String supplierCode) {
        QueryWrapper<Invoice> qw = new QueryWrapper<>();
        String mine = CurrentUser.supplierCode();
        qw.eq(mine != null, "supplier_code", mine)
                .eq(StringUtils.isNotBlank(status), "status", status)
                .eq(StringUtils.isNotBlank(supplierCode), "supplier_code", supplierCode)
                .orderByDesc("id");
        return invoiceMapper.selectPage(new Page<>(current, size), qw);
    }

    /** 供应商提交发票 */
    @Transactional
    public Invoice submit(Invoice inv) {
        String mine = CurrentUser.supplierCode();
        if (mine != null) {
            inv.setSupplierCode(mine);
        }
        if (inv.getSupplierCode() == null || inv.getLines() == null || inv.getLines().isEmpty()) {
            throw new BizException("发票缺少供应商或明细行");
        }
        inv.setId(null);
        inv.setCode(codeGenerator.next("INV"));
        inv.setStatus("SUBMITTED");
        BigDecimal amount = BigDecimal.ZERO;
        int i = 0;
        for (InvoiceLine l : inv.getLines()) {
            l.setId(null);
            l.setAmount(l.getQty() == null || l.getPrice() == null ? BigDecimal.ZERO
                    : l.getQty().multiply(l.getPrice()));
            amount = amount.add(l.getAmount());
        }
        if (inv.getAmount() == null) {
            inv.setAmount(amount);
        }
        invoiceMapper.insert(inv);
        for (InvoiceLine l : inv.getLines()) {
            l.setInvoiceId(inv.getId());
            lineMapper.insert(l);
        }
        return load(inv.getId());
    }

    /** 对账：数量不超过 PO 行收货合格数-已开票，单价容差内 */
    @Transactional
    public Invoice match(Long id) {
        CurrentUser.requireBuyerSide();
        Invoice inv = load(id);
        if (!"SUBMITTED".equals(inv.getStatus()) && !"MISMATCH".equals(inv.getStatus())) {
            throw new BizException("当前状态不可对账: " + inv.getStatus());
        }
        PurchaseOrder po = poMapper.selectOne(new LambdaQueryWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getCode, inv.getPoCode()));
        if (po == null) {
            throw new BizException("采购订单不存在: " + inv.getPoCode());
        }
        Map<Long, PoLine> poLines = poLineMapper.selectList(new LambdaQueryWrapper<PoLine>()
                        .eq(PoLine::getPoId, po.getId())).stream()
                .collect(Collectors.toMap(PoLine::getId, l -> l));
        StringBuilder err = new StringBuilder();
        for (InvoiceLine l : inv.getLines()) {
            PoLine pl = poLines.get(l.getPoLineId());
            if (pl == null) {
                err.append("行 ").append(l.getId()).append(" 订单行不存在; ");
                continue;
            }
            BigDecimal available = nz(pl.getReceivedQty()).subtract(nz(pl.getRejectedQty()))
                    .subtract(nz(pl.getInvoicedQty()));
            if (l.getQty() == null || l.getQty().compareTo(available) > 0) {
                err.append(pl.getMaterialCode()).append(" 开票数量 ").append(l.getQty())
                        .append(" 超过可开票数量 ").append(available).append("; ");
            }
            if (l.getPrice() != null && pl.getPrice() != null
                    && l.getPrice().subtract(pl.getPrice()).abs().compareTo(priceTolerance) > 0) {
                err.append(pl.getMaterialCode()).append(" 单价 ").append(l.getPrice())
                        .append(" 与订单价 ").append(pl.getPrice()).append(" 超容差; ");
            }
        }
        if (err.length() > 0) {
            inv.setStatus("MISMATCH");
            inv.setRemark(err.toString());
            invoiceMapper.updateById(inv);
            throw new BizException("发票对账不符: " + err);
        }
        for (InvoiceLine l : inv.getLines()) {
            PoLine pl = poLines.get(l.getPoLineId());
            pl.setInvoicedQty(nz(pl.getInvoicedQty()).add(l.getQty()));
            poLineMapper.updateById(pl);
        }
        inv.setStatus("MATCHED");
        invoiceMapper.updateById(inv);
        return load(id);
    }

    @Transactional
    public Invoice approve(Long id) {
        CurrentUser.requireBuyerSide();
        Invoice inv = load(id);
        if (!"MATCHED".equals(inv.getStatus())) {
            throw new BizException("仅已对账发票可审批: " + inv.getStatus());
        }
        inv.setStatus("APPROVED");
        invoiceMapper.updateById(inv);
        return load(id);
    }

    @Transactional
    public Invoice reject(Long id, String reason) {
        CurrentUser.requireBuyerSide();
        Invoice inv = load(id);
        inv.setStatus("REJECTED");
        inv.setRemark(reason);
        invoiceMapper.updateById(inv);
        return load(id);
    }

    /** 过账到 SAP 发票校验 */
    @Transactional
    public Invoice postToSap(Long id) {
        CurrentUser.requireBuyerSide();
        Invoice inv = load(id);
        if (!"APPROVED".equals(inv.getStatus())) {
            throw new BizException("仅已审批发票可过账: " + inv.getStatus());
        }
        String doc = logService.execute("SAP", IntegrationLogService.SAP_POST_INVOICE,
                "INVOICE", inv.getId(), inv.getCode(), inv, () -> sapClient.postInvoice(inv));
        inv.setSapInvoiceDoc(doc);
        inv.setStatus("POSTED");
        invoiceMapper.updateById(inv);
        return load(id);
    }

    static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
