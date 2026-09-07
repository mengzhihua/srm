package com.srm.purchase.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.srm.common.R;
import com.srm.purchase.entity.PurchaseOrder;
import com.srm.purchase.service.PurchaseOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/purchase/order")
@RequiredArgsConstructor
public class PurchaseOrderController {
    private final PurchaseOrderService service;

    @GetMapping("/page")
    public R<Page<PurchaseOrder>> page(@RequestParam(defaultValue = "1") long current,
                                       @RequestParam(defaultValue = "20") long size,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(required = false) String supplierCode,
                                       @RequestParam(required = false) String keyword) {
        return R.ok(service.page(current, size, status, supplierCode, keyword));
    }

    @GetMapping("/{id}")
    public R<PurchaseOrder> get(@PathVariable Long id) {
        return R.ok(service.load(id));
    }

    @PostMapping
    public R<PurchaseOrder> create(@RequestBody PurchaseOrder po) {
        return R.ok(service.create(po));
    }

    @PutMapping("/{id}")
    public R<PurchaseOrder> update(@PathVariable Long id, @RequestBody PurchaseOrder po) {
        return R.ok(service.update(id, po));
    }

    @PostMapping("/{id}/approve")
    public R<PurchaseOrder> approve(@PathVariable Long id) {
        return R.ok(service.approve(id));
    }

    @PostMapping("/{id}/send-to-sap")
    public R<PurchaseOrder> sendToSap(@PathVariable Long id) {
        return R.ok(service.sendToSap(id));
    }

    @PostMapping("/{id}/confirm")
    public R<PurchaseOrder> confirm(@PathVariable Long id) {
        return R.ok(service.confirm(id));
    }

    @PostMapping("/{id}/cancel")
    public R<PurchaseOrder> cancel(@PathVariable Long id) {
        return R.ok(service.cancel(id));
    }

    @PostMapping("/{id}/close")
    public R<PurchaseOrder> close(@PathVariable Long id) {
        return R.ok(service.close(id));
    }
}
