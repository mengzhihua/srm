package com.srm.evaluation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.srm.basic.entity.Supplier;
import com.srm.basic.mapper.SupplierMapper;
import com.srm.common.BizException;
import com.srm.delivery.entity.Asn;
import com.srm.delivery.mapper.AsnMapper;
import com.srm.evaluation.entity.SupplierEvaluation;
import com.srm.evaluation.entity.SupplierEvaluationRecord;
import com.srm.evaluation.mapper.SupplierEvaluationMapper;
import com.srm.evaluation.mapper.SupplierEvaluationRecordMapper;
import com.srm.integration.client.SapClient;
import com.srm.integration.service.IntegrationLogService;
import com.srm.receipt.entity.GoodsReceipt;
import com.srm.receipt.service.WmsReceiptPayload;
import com.srm.system.auth.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** 供应商考核：每张 GR 一条记录，按 供应商+月份 汇总并回写供应商等级 */
@Service
@RequiredArgsConstructor
public class EvaluationService {
    private final SupplierEvaluationRecordMapper recordMapper;
    private final SupplierEvaluationMapper summaryMapper;
    private final SupplierMapper supplierMapper;
    private final AsnMapper asnMapper;
    private final SapClient sapClient;
    private final IntegrationLogService logService;

    @Value("${srm.evaluation.wms-weight:0.3}")
    private double wmsWeight;

    /** 收货完成后计算单票考核记录并重算当期汇总 */
    @Transactional
    public SupplierEvaluationRecord evaluateReceipt(GoodsReceipt gr, WmsReceiptPayload payload) {
        SupplierEvaluationRecord r = new SupplierEvaluationRecord();
        r.setSupplierCode(gr.getSupplierCode());
        r.setGrCode(gr.getCode());
        r.setAsnCode(gr.getAsnCode());
        r.setPoCode(gr.getPoCode());

        Asn asn = asnMapper.selectById(gr.getAsnId());
        LocalDateTime receivedAt = gr.getReceivedAt() != null ? gr.getReceivedAt() : LocalDateTime.now();
        boolean onTime = asn == null || asn.getExpectedDate() == null
                || !receivedAt.toLocalDate().isAfter(asn.getExpectedDate());
        r.setOnTime(onTime);
        if (asn != null && asn.getCreatedAt() != null) {
            r.setLeadDays((int) ChronoUnit.DAYS.between(asn.getCreatedAt().toLocalDate(), receivedAt.toLocalDate()));
        }

        BigDecimal received = nz(gr.getTotalReceivedQty());
        BigDecimal rejected = nz(gr.getTotalRejectedQty());
        BigDecimal shipped = nz(asn != null ? asn.getTotalQty() : received);
        // 收货准确率 = 合格数/发货数 %
        BigDecimal accepted = received.subtract(rejected).max(BigDecimal.ZERO);
        r.setQtyAccuracy(pct(accepted, shipped));
        r.setQualityRate(pct(accepted, received));

        r.setScore(score(r));
        r.setPeriod(receivedAt.format(DateTimeFormatter.ofPattern("yyyy-MM")));
        recordMapper.insert(r);
        recomputeSummary(r.getSupplierCode(), r.getPeriod());
        return r;
    }

    private BigDecimal score(SupplierEvaluationRecord r) {
        double onTime = Boolean.TRUE.equals(r.getOnTime()) ? 100.0 : 0.0;
        double qa = r.getQtyAccuracy() == null ? 0.0 : r.getQtyAccuracy().doubleValue();
        double qr = r.getQualityRate() == null ? 0.0 : r.getQualityRate().doubleValue();
        double s = onTime * 0.4 + qa * 0.3 + qr * 0.3;
        if (r.getWmsScore() != null) {
            s = s * (1 - wmsWeight) + r.getWmsScore().doubleValue() * wmsWeight;
        }
        return BigDecimal.valueOf(s).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal pct(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() <= 0) {
            return new BigDecimal("100.00");
        }
        return part.multiply(new BigDecimal("100")).divide(whole, 2, RoundingMode.HALF_UP);
    }

    /** 重算 供应商+期间 汇总并回写 srm_supplier.grade/score */
    @Transactional
    public SupplierEvaluation recomputeSummary(String supplierCode, String period) {
        List<SupplierEvaluationRecord> records = recordMapper.selectList(
                new LambdaQueryWrapper<SupplierEvaluationRecord>()
                        .eq(SupplierEvaluationRecord::getSupplierCode, supplierCode)
                        .eq(SupplierEvaluationRecord::getPeriod, period));
        if (records.isEmpty()) {
            return null;
        }
        SupplierEvaluation s = summaryMapper.selectOne(new LambdaQueryWrapper<SupplierEvaluation>()
                .eq(SupplierEvaluation::getSupplierCode, supplierCode)
                .eq(SupplierEvaluation::getPeriod, period));
        if (s == null) {
            s = new SupplierEvaluation();
            s.setSupplierCode(supplierCode);
            s.setPeriod(period);
        }
        s.setReceiptCount(records.size());
        s.setOnTimeRate(BigDecimal.valueOf(
                records.stream().filter(r -> Boolean.TRUE.equals(r.getOnTime())).count() * 100.0 / records.size())
                .setScale(2, RoundingMode.HALF_UP));
        s.setQtyAccuracy(avg(records.stream().map(SupplierEvaluationRecord::getQtyAccuracy)));
        s.setQualityRate(avg(records.stream().map(SupplierEvaluationRecord::getQualityRate)));
        s.setAvgScore(avg(records.stream().map(SupplierEvaluationRecord::getScore)));
        double score = s.getAvgScore().doubleValue();
        s.setGrade(score >= 90 ? "A" : score >= 80 ? "B" : score >= 70 ? "C" : "D");
        if (s.getId() == null) {
            summaryMapper.insert(s);
        } else {
            s.setSapSynced(false);
            summaryMapper.updateById(s);
        }
        // 回写供应商等级/最近得分
        Supplier supplier = supplierMapper.selectOne(new LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getCode, supplierCode));
        if (supplier != null) {
            supplier.setGrade(s.getGrade());
            supplier.setScore(s.getAvgScore());
            supplierMapper.updateById(supplier);
        }
        return s;
    }

    private static BigDecimal avg(java.util.stream.Stream<BigDecimal> stream) {
        List<BigDecimal> list = stream.filter(v -> v != null).collect(java.util.stream.Collectors.toList());
        if (list.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return list.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(list.size()), 2, RoundingMode.HALF_UP);
    }

    /** 接收 WMS 推送的考核信息，写入对应 GR 的考核记录并重算 */
    @Transactional
    public SupplierEvaluationRecord applyWmsEvaluation(String supplierCode, String asnCode, String grCode,
                                                       BigDecimal wmsScore, String remark) {
        LambdaQueryWrapper<SupplierEvaluationRecord> qw = new LambdaQueryWrapper<SupplierEvaluationRecord>()
                .eq(supplierCode != null, SupplierEvaluationRecord::getSupplierCode, supplierCode)
                .eq(asnCode != null, SupplierEvaluationRecord::getAsnCode, asnCode)
                .eq(grCode != null, SupplierEvaluationRecord::getGrCode, grCode)
                .orderByDesc(SupplierEvaluationRecord::getId);
        List<SupplierEvaluationRecord> list = recordMapper.selectList(qw);
        if (list.isEmpty()) {
            throw new BizException("未找到对应考核记录 (supplier=" + supplierCode + ", asn=" + asnCode + ")");
        }
        SupplierEvaluationRecord r = list.get(0);
        r.setWmsScore(wmsScore);
        r.setWmsRemark(remark);
        r.setScore(score(r));
        recordMapper.updateById(r);
        recomputeSummary(r.getSupplierCode(), r.getPeriod());
        return r;
    }

    public Page<SupplierEvaluation> summaryPage(long current, long size, String supplierCode, String period) {
        QueryWrapper<SupplierEvaluation> qw = new QueryWrapper<>();
        String mine = CurrentUser.supplierCode();
        qw.eq(mine != null, "supplier_code", mine)
                .eq(StringUtils.isNotBlank(supplierCode), "supplier_code", supplierCode)
                .eq(StringUtils.isNotBlank(period), "period", period)
                .orderByDesc("period").orderByDesc("id");
        return summaryMapper.selectPage(new Page<>(current, size), qw);
    }

    public Page<SupplierEvaluationRecord> recordPage(long current, long size, String supplierCode) {
        QueryWrapper<SupplierEvaluationRecord> qw = new QueryWrapper<>();
        String mine = CurrentUser.supplierCode();
        qw.eq(mine != null, "supplier_code", mine)
                .eq(StringUtils.isNotBlank(supplierCode), "supplier_code", supplierCode)
                .orderByDesc("id");
        return recordMapper.selectPage(new Page<>(current, size), qw);
    }

    /** 同步考核结果到 SAP */
    @Transactional
    public SupplierEvaluation syncSap(Long id) {
        SupplierEvaluation s = summaryMapper.selectById(id);
        if (s == null) {
            throw new BizException("考核汇总不存在: " + id);
        }
        String ref = logService.execute("SAP", IntegrationLogService.SAP_SYNC_EVAL,
                "EVAL", s.getId(), s.getSupplierCode() + "/" + s.getPeriod(), s,
                () -> sapClient.syncVendorEvaluation(s));
        s.setSapSynced(true);
        s.setSapSyncedAt(LocalDateTime.now());
        summaryMapper.updateById(s);
        return s;
    }

    static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
