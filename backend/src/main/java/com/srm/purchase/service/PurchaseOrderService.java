package com.srm.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.srm.common.BizException;
import com.srm.common.CodeGenerator;
import com.srm.integration.client.SapClient;
import com.srm.integration.service.IntegrationLogService;
import com.srm.purchase.entity.PoLine;
import com.srm.purchase.entity.PurchaseOrder;
import com.srm.purchase.mapper.PoLineMapper;
import com.srm.purchase.mapper.PurchaseOrderMapper;
import com.srm.system.auth.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/** 采购订单：DRAFT -> APPROVED -> SENT(下发SAP) -> CONFIRMED(供应商确认) -> PARTIALLY_RECEIVED/RECEIVED -> CLOSED */
@Service
@RequiredArgsConstructor
public class PurchaseOrderService {
    private final PurchaseOrderMapper poMapper;
    private final PoLineMapper lineMapper;
    private final SapClient sapClient;
    private final IntegrationLogService logService;
    private final CodeGenerator codeGenerator;

    public PurchaseOrder require(Long id) {
        PurchaseOrder po = poMapper.selectById(id);
        if (po == null) {
            throw new BizException("采购订单不存在: " + id);
        }
        return po;
    }

    public PurchaseOrder load(Long id) {
        PurchaseOrder po = require(id);
        po.setLines(lineMapper.selectList(new LambdaQueryWrapper<PoLine>()
                .eq(PoLine::getPoId, id).orderByAsc(PoLine::getLineNo)));
        CurrentUser.checkSupplier(po.getSupplierCode());
        return po;
    }

    public Page<PurchaseOrder> page(long current, long size, String status, String supplierCode, String keyword) {
        QueryWrapper<PurchaseOrder> qw = new QueryWrapper<>();
        String mine = CurrentUser.supplierCode();
        qw.eq(mine != null, "supplier_code", mine)
                .eq(StringUtils.isNotBlank(supplierCode), "supplier_code", supplierCode)
                .eq(StringUtils.isNotBlank(status), "status", status)
                .and(StringUtils.isNotBlank(keyword),
                        w -> w.like("code", keyword).or().like("sap_po_no", keyword).or().like("source_code", keyword))
                .orderByDesc("id");
        return poMapper.selectPage(new Page<>(current, size), qw);
    }

    @Transactional
    public PurchaseOrder create(PurchaseOrder po) {
        CurrentUser.requireBuyerSide();
        validateLines(po);
        po.setId(null);
        po.setCode(codeGenerator.next("PO"));
        po.setStatus("DRAFT");
        if (po.getSourceType() == null) {
            po.setSourceType("MANUAL");
        }
        fillAmounts(po);
        poMapper.insert(po);
        saveLines(po);
        return load(po.getId());
    }

    @Transactional
    public PurchaseOrder update(Long id, PurchaseOrder po) {
        CurrentUser.requireBuyerSide();
        PurchaseOrder db = require(id);
        if (!"DRAFT".equals(db.getStatus())) {
            throw new BizException("仅草稿状态的采购订单可修改");
        }
        validateLines(po);
        db.setSupplierCode(po.getSupplierCode());
        db.setPlantCode(po.getPlantCode());
        db.setCurrency(po.getCurrency());
        db.setExpectedDate(po.getExpectedDate());
        db.setRemark(po.getRemark());
        db.setLines(po.getLines());
        fillAmounts(db);
        poMapper.updateById(db);
        lineMapper.delete(new LambdaQueryWrapper<PoLine>().eq(PoLine::getPoId, id));
        saveLines(db);
        return load(id);
    }

    @Transactional
    public PurchaseOrder approve(Long id) {
        CurrentUser.requireBuyerSide();
        PurchaseOrder po = require(id);
        if (!"DRAFT".equals(po.getStatus())) {
            throw new BizException("仅草稿状态可审批，当前: " + po.getStatus());
        }
        BigDecimal total = po.getTotalAmount();
        if (total == null || total.signum() <= 0) {
            throw new BizException("订单总金额必须大于 0");
        }
        po.setStatus("APPROVED");
        poMapper.updateById(po);
        return load(id);
    }

    /** 下发到 SAP，返回 SAP PO 号；失败抛异常并记 FAILED 日志（状态保持 APPROVED 可重试） */
    @Transactional
    public PurchaseOrder sendToSap(Long id) {
        CurrentUser.requireBuyerSide();
        PurchaseOrder po = load(id);
        if (!"APPROVED".equals(po.getStatus()) && !"SENT".equals(po.getStatus())) {
            throw new BizException("仅已审批/已发送状态可下发 SAP，当前: " + po.getStatus());
        }
        String sapPoNo = logService.execute("SAP", IntegrationLogService.SAP_CREATE_PO,
                "PO", po.getId(), po.getCode(), po, () -> sapClient.createPurchaseOrder(po));
        po.setSapPoNo(sapPoNo);
        po.setSapSentAt(LocalDateTime.now());
        po.setStatus("SENT");
        poMapper.updateById(po);
        return load(id);
    }

    /** 供应商确认订单（供应商门户只能确认自己的订单） */
    @Transactional
    public PurchaseOrder confirm(Long id) {
        PurchaseOrder po = require(id);
        CurrentUser.checkSupplier(po.getSupplierCode());
        if (!"SENT".equals(po.getStatus())) {
            throw new BizException("仅已下发(SENT)状态可确认，当前: " + po.getStatus());
        }
        po.setStatus("CONFIRMED");
        po.setConfirmedAt(LocalDateTime.now());
        poMapper.updateById(po);
        return load(id);
    }

    @Transactional
    public PurchaseOrder cancel(Long id) {
        CurrentUser.requireBuyerSide();
        PurchaseOrder po = require(id);
        if (!Arrays.asList("DRAFT", "APPROVED", "SENT", "CONFIRMED").contains(po.getStatus())) {
            throw new BizException("当前状态不可取消: " + po.getStatus());
        }
        po.setStatus("CANCELLED");
        poMapper.updateById(po);
        return load(id);
    }

    @Transactional
    public PurchaseOrder close(Long id) {
        CurrentUser.requireBuyerSide();
        PurchaseOrder po = require(id);
        if (!Arrays.asList("RECEIVED", "PARTIALLY_RECEIVED", "CONFIRMED", "SENT").contains(po.getStatus())) {
            throw new BizException("当前状态不可关闭: " + po.getStatus());
        }
        po.setStatus("CLOSED");
        poMapper.updateById(po);
        return load(id);
    }

    /** 收货/发货后回写 PO 汇总状态 */
    public void refreshStatus(PurchaseOrder po, List<PoLine> lines) {
        boolean allReceived = lines.stream().allMatch(l ->
                nz(l.getReceivedQty()).compareTo(l.getQty()) >= 0);
        boolean anyReceived = lines.stream().anyMatch(l -> nz(l.getReceivedQty()).signum() > 0);
        if (allReceived) {
            po.setStatus("RECEIVED");
        } else if (anyReceived) {
            po.setStatus("PARTIALLY_RECEIVED");
        }
        poMapper.updateById(po);
    }

    private void validateLines(PurchaseOrder po) {
        if (po.getSupplierCode() == null || po.getSupplierCode().trim().isEmpty()) {
            throw new BizException("供应商不能为空");
        }
        if (po.getLines() == null || po.getLines().isEmpty()) {
            throw new BizException("订单行不能为空");
        }
        for (PoLine l : po.getLines()) {
            if (l.getMaterialCode() == null || l.getQty() == null || l.getQty().signum() <= 0) {
                throw new BizException("订单行物料/数量不合法");
            }
        }
    }

    private void fillAmounts(PurchaseOrder po) {
        BigDecimal total = BigDecimal.ZERO;
        int i = 1;
        for (PoLine l : po.getLines()) {
            l.setId(null);
            l.setLineNo(i++);
            BigDecimal price = l.getPrice() == null ? BigDecimal.ZERO : l.getPrice();
            l.setAmount(l.getQty().multiply(price));
            total = total.add(l.getAmount());
        }
        po.setTotalAmount(total);
        if (po.getCurrency() == null) {
            po.setCurrency("CNY");
        }
    }

    private void saveLines(PurchaseOrder po) {
        for (PoLine l : po.getLines()) {
            l.setPoId(po.getId());
            if (l.getShippedQty() == null) l.setShippedQty(BigDecimal.ZERO);
            if (l.getReceivedQty() == null) l.setReceivedQty(BigDecimal.ZERO);
            if (l.getRejectedQty() == null) l.setRejectedQty(BigDecimal.ZERO);
            if (l.getInvoicedQty() == null) l.setInvoicedQty(BigDecimal.ZERO);
            lineMapper.insert(l);
        }
    }

    public static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
