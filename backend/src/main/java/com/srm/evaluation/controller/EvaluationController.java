package com.srm.evaluation.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.srm.common.R;
import com.srm.evaluation.entity.SupplierEvaluation;
import com.srm.evaluation.entity.SupplierEvaluationRecord;
import com.srm.evaluation.service.EvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/evaluation")
@RequiredArgsConstructor
public class EvaluationController {
    private final EvaluationService service;

    /** 考核汇总分页 */
    @GetMapping({"", "/page"})
    public R<Page<SupplierEvaluation>> page(@RequestParam(defaultValue = "1") long current,
                                            @RequestParam(defaultValue = "20") long size,
                                            @RequestParam(required = false) String supplierCode,
                                            @RequestParam(required = false) String period) {
        return R.ok(service.summaryPage(current, size, supplierCode, period));
    }

    /** 单票考核记录分页 */
    @GetMapping("/records")
    public R<Page<SupplierEvaluationRecord>> records(@RequestParam(defaultValue = "1") long current,
                                                     @RequestParam(defaultValue = "20") long size,
                                                     @RequestParam(required = false) String supplierCode) {
        return R.ok(service.recordPage(current, size, supplierCode));
    }

    @PostMapping("/{id}/sync-sap")
    public R<SupplierEvaluation> syncSap(@PathVariable Long id) {
        return R.ok(service.syncSap(id));
    }
}
