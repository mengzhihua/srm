package com.srm.invoice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.srm.common.R;
import com.srm.invoice.entity.Invoice;
import com.srm.invoice.service.InvoiceService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/invoice")
@RequiredArgsConstructor
public class InvoiceController {
    private final InvoiceService service;

    @GetMapping("/page")
    public R<Page<Invoice>> page(@RequestParam(defaultValue = "1") long current,
                                 @RequestParam(defaultValue = "20") long size,
                                 @RequestParam(required = false) String status,
                                 @RequestParam(required = false) String supplierCode) {
        return R.ok(service.page(current, size, status, supplierCode));
    }

    @GetMapping("/{id}")
    public R<Invoice> get(@PathVariable Long id) {
        return R.ok(service.load(id));
    }

    @PostMapping
    public R<Invoice> submit(@RequestBody Invoice inv) {
        return R.ok(service.submit(inv));
    }

    @PostMapping("/{id}/match")
    public R<Invoice> match(@PathVariable Long id) {
        return R.ok(service.match(id));
    }

    @PostMapping("/{id}/approve")
    public R<Invoice> approve(@PathVariable Long id) {
        return R.ok(service.approve(id));
    }

    @Data
    public static class RejectReq {
        private String reason;
    }

    @PostMapping("/{id}/reject")
    public R<Invoice> reject(@PathVariable Long id, @RequestBody(required = false) RejectReq req) {
        return R.ok(service.reject(id, req == null ? null : req.getReason()));
    }

    @PostMapping("/{id}/post-to-sap")
    public R<Invoice> postToSap(@PathVariable Long id) {
        return R.ok(service.postToSap(id));
    }
}
