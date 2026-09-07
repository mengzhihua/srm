package com.srm.dashboard;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.srm.common.R;
import com.srm.delivery.entity.Asn;
import com.srm.delivery.mapper.AsnMapper;
import com.srm.evaluation.entity.SupplierEvaluation;
import com.srm.evaluation.mapper.SupplierEvaluationMapper;
import com.srm.integration.entity.IntegrationLog;
import com.srm.integration.mapper.IntegrationLogMapper;
import com.srm.purchase.entity.PurchaseOrder;
import com.srm.purchase.mapper.PurchaseOrderMapper;
import com.srm.receipt.entity.GoodsReceipt;
import com.srm.receipt.mapper.GoodsReceiptMapper;
import com.srm.sourcing.entity.PurchaseRequisition;
import com.srm.sourcing.mapper.PurchaseRequisitionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final PurchaseRequisitionMapper prMapper;
    private final PurchaseOrderMapper poMapper;
    private final AsnMapper asnMapper;
    private final GoodsReceiptMapper grMapper;
    private final IntegrationLogMapper logMapper;
    private final SupplierEvaluationMapper evalMapper;

    @GetMapping
    public R<Map<String, Object>> summary() {
        Map<String, Object> m = new HashMap<>();
        m.put("prPending", prMapper.selectCount(
                new LambdaQueryWrapper<PurchaseRequisition>().eq(PurchaseRequisition::getStatus, "SUBMITTED")));
        m.put("poPendingApprove", poMapper.selectCount(
                new LambdaQueryWrapper<PurchaseOrder>().eq(PurchaseOrder::getStatus, "DRAFT")));
        m.put("poToSap", poMapper.selectCount(
                new LambdaQueryWrapper<PurchaseOrder>().eq(PurchaseOrder::getStatus, "APPROVED")));
        m.put("poToConfirm", poMapper.selectCount(
                new LambdaQueryWrapper<PurchaseOrder>().eq(PurchaseOrder::getStatus, "SENT")));
        m.put("asnInTransit", asnMapper.selectCount(
                new LambdaQueryWrapper<Asn>().in(Asn::getStatus, "SYNCED", "RECEIVING")));
        m.put("grPendingPost", grMapper.selectCount(
                new LambdaQueryWrapper<GoodsReceipt>().in(GoodsReceipt::getStatus, "PENDING", "POST_FAILED")));
        m.put("asnSyncFailed", asnMapper.selectCount(
                new LambdaQueryWrapper<Asn>().eq(Asn::getStatus, "SYNC_FAILED")));
        // 本月采购金额（已审批及之后的订单）
        String monthStart = LocalDate.now().withDayOfMonth(1).toString();
        List<PurchaseOrder> pos = poMapper.selectList(new QueryWrapper<PurchaseOrder>()
                .notIn("status", "DRAFT", "CANCELLED")
                .ge("created_at", monthStart));
        m.put("monthPurchaseAmount", pos.stream()
                .map(p -> p.getTotalAmount() == null ? BigDecimal.ZERO : p.getTotalAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        // 供应商等级分布（取最近一期汇总）
        List<SupplierEvaluation> evals = evalMapper.selectList(null);
        Map<String, SupplierEvaluation> latest = new HashMap<>();
        for (SupplierEvaluation e : evals) {
            SupplierEvaluation cur = latest.get(e.getSupplierCode());
            if (cur == null || e.getPeriod().compareTo(cur.getPeriod()) > 0) {
                latest.put(e.getSupplierCode(), e);
            }
        }
        m.put("supplierGrades", latest.values().stream().collect(Collectors.groupingBy(
                e -> e.getGrade() == null ? "N/A" : e.getGrade(), Collectors.counting())));
        m.put("recentLogs", logMapper.selectList(
                new QueryWrapper<IntegrationLog>().orderByDesc("id").last("LIMIT 10")));
        return R.ok(m);
    }
}
