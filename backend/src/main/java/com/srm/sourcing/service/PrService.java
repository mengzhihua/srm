package com.srm.sourcing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.srm.common.BizException;
import com.srm.common.CodeGenerator;
import com.srm.purchase.entity.PurchaseOrder;
import com.srm.purchase.service.PurchaseOrderService;
import com.srm.sourcing.entity.PrLine;
import com.srm.sourcing.entity.PurchaseRequisition;
import com.srm.sourcing.mapper.PrLineMapper;
import com.srm.sourcing.mapper.PurchaseRequisitionMapper;
import com.srm.system.auth.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/** 采购申请：DRAFT -> SUBMITTED -> APPROVED -> ORDERED */
@Service
@RequiredArgsConstructor
public class PrService {
    private final PurchaseRequisitionMapper prMapper;
    private final PrLineMapper lineMapper;
    private final PurchaseOrderService poService;
    private final CodeGenerator codeGenerator;

    public PurchaseRequisition load(Long id) {
        PurchaseRequisition pr = prMapper.selectById(id);
        if (pr == null) {
            throw new BizException("采购申请不存在: " + id);
        }
        pr.setLines(lineMapper.selectList(new LambdaQueryWrapper<PrLine>()
                .eq(PrLine::getPrId, id).orderByAsc(PrLine::getLineNo)));
        return pr;
    }

    public Page<PurchaseRequisition> page(long current, long size, String status, String keyword) {
        QueryWrapper<PurchaseRequisition> qw = new QueryWrapper<>();
        qw.eq(StringUtils.isNotBlank(status), "status", status)
                .and(StringUtils.isNotBlank(keyword),
                        w -> w.like("code", keyword).or().like("requester", keyword))
                .orderByDesc("id");
        return prMapper.selectPage(new Page<>(current, size), qw);
    }

    @Transactional
    public PurchaseRequisition create(PurchaseRequisition pr) {
        CurrentUser.requireBuyerSide();
        if (pr.getLines() == null || pr.getLines().isEmpty()) {
            throw new BizException("申请行不能为空");
        }
        pr.setId(null);
        pr.setCode(codeGenerator.next("PR"));
        pr.setStatus("DRAFT");
        prMapper.insert(pr);
        saveLines(pr);
        return load(pr.getId());
    }

    private void saveLines(PurchaseRequisition pr) {
        int i = 1;
        for (PrLine l : pr.getLines()) {
            if (l.getMaterialCode() == null || l.getQty() == null || l.getQty().signum() <= 0) {
                throw new BizException("申请行物料/数量不合法");
            }
            l.setId(null);
            l.setPrId(pr.getId());
            l.setLineNo(i++);
            if (l.getOrderedQty() == null) {
                l.setOrderedQty(BigDecimal.ZERO);
            }
            lineMapper.insert(l);
        }
    }

    private PurchaseRequisition transit(Long id, String from, String to) {
        CurrentUser.requireBuyerSide();
        PurchaseRequisition pr = load(id);
        if (!from.equals(pr.getStatus())) {
            throw new BizException("当前状态 " + pr.getStatus() + " 不允许此操作（要求 " + from + "）");
        }
        pr.setStatus(to);
        prMapper.updateById(pr);
        return load(id);
    }

    public PurchaseRequisition submit(Long id) {
        return transit(id, "DRAFT", "SUBMITTED");
    }

    public PurchaseRequisition approve(Long id) {
        return transit(id, "SUBMITTED", "APPROVED");
    }

    public PurchaseRequisition reject(Long id) {
        return transit(id, "SUBMITTED", "REJECTED");
    }

    public PurchaseRequisition cancel(Long id) {
        CurrentUser.requireBuyerSide();
        PurchaseRequisition pr = load(id);
        if (!Arrays.asList("DRAFT", "SUBMITTED", "APPROVED").contains(pr.getStatus())) {
            throw new BizException("当前状态不可取消: " + pr.getStatus());
        }
        pr.setStatus("CANCELLED");
        prMapper.updateById(pr);
        return load(id);
    }

    /** PR 转采购订单：supplierCode 必填，价格可由调用方传入 */
    @Transactional
    public PurchaseOrder toPo(Long id, String supplierCode, List<com.srm.purchase.entity.PoLine> lines) {
        CurrentUser.requireBuyerSide();
        PurchaseRequisition pr = load(id);
        if (!"APPROVED".equals(pr.getStatus())) {
            throw new BizException("仅已审批的采购申请可转订单，当前: " + pr.getStatus());
        }
        PurchaseOrder po = new PurchaseOrder();
        po.setSupplierCode(supplierCode);
        po.setPlantCode(pr.getPlantCode());
        po.setSourceType("PR");
        po.setSourceCode(pr.getCode());
        po.setLines(lines);
        PurchaseOrder saved = poService.create(po);
        pr.setStatus("ORDERED");
        prMapper.updateById(pr);
        List<PrLine> prLines = lineMapper.selectList(new LambdaQueryWrapper<PrLine>().eq(PrLine::getPrId, id));
        for (PrLine l : prLines) {
            l.setOrderedQty(l.getQty());
            lineMapper.updateById(l);
        }
        return saved;
    }
}
