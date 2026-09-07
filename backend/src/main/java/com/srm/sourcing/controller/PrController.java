package com.srm.sourcing.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.srm.common.R;
import com.srm.purchase.entity.PoLine;
import com.srm.purchase.entity.PurchaseOrder;
import com.srm.sourcing.entity.Rfq;
import com.srm.sourcing.entity.PurchaseRequisition;
import com.srm.sourcing.service.PrService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sourcing/pr")
@RequiredArgsConstructor
public class PrController {
    private final PrService service;

    @GetMapping("/page")
    public R<Page<PurchaseRequisition>> page(@RequestParam(defaultValue = "1") long current,
                                             @RequestParam(defaultValue = "20") long size,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(required = false) String keyword) {
        return R.ok(service.page(current, size, status, keyword));
    }

    @GetMapping("/{id}")
    public R<PurchaseRequisition> get(@PathVariable Long id) {
        return R.ok(service.load(id));
    }

    @PostMapping
    public R<PurchaseRequisition> create(@RequestBody PurchaseRequisition pr) {
        return R.ok(service.create(pr));
    }

    @PostMapping("/{id}/submit")
    public R<PurchaseRequisition> submit(@PathVariable Long id) {
        return R.ok(service.submit(id));
    }

    @PostMapping("/{id}/approve")
    public R<PurchaseRequisition> approve(@PathVariable Long id) {
        return R.ok(service.approve(id));
    }

    @PostMapping("/{id}/reject")
    public R<PurchaseRequisition> reject(@PathVariable Long id) {
        return R.ok(service.reject(id));
    }

    @PostMapping("/{id}/cancel")
    public R<PurchaseRequisition> cancel(@PathVariable Long id) {
        return R.ok(service.cancel(id));
    }

    @Data
    public static class ToPoReq {
        private String supplierCode;
        private List<PoLine> lines;
    }

    @PostMapping("/{id}/to-rfq")
    public R<Rfq> toRfq(@PathVariable Long id, @RequestBody Rfq rfq) {
        return R.ok(service.toRfq(id, rfq));
    }

    @PostMapping("/{id}/to-po")
    public R<PurchaseOrder> toPo(@PathVariable Long id, @RequestBody ToPoReq req) {
        return R.ok(service.toPo(id, req.getSupplierCode(), req.getLines()));
    }
}
