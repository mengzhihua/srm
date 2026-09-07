package com.srm.delivery.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.srm.common.R;
import com.srm.delivery.entity.Asn;
import com.srm.delivery.service.AsnService;
import com.srm.receipt.entity.GoodsReceipt;
import com.srm.receipt.service.WmsReceiptPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/delivery/asn")
@RequiredArgsConstructor
public class AsnController {
    private final AsnService service;

    @GetMapping("/page")
    public R<Page<Asn>> page(@RequestParam(defaultValue = "1") long current,
                             @RequestParam(defaultValue = "20") long size,
                             @RequestParam(required = false) String status,
                             @RequestParam(required = false) String poCode,
                             @RequestParam(required = false) String supplierCode) {
        return R.ok(service.page(current, size, status, poCode, supplierCode));
    }

    @GetMapping("/{id}")
    public R<Asn> get(@PathVariable Long id) {
        return R.ok(service.load(id));
    }

    @PostMapping
    public R<Asn> create(@RequestBody Asn asn) {
        return R.ok(service.create(asn));
    }

    @PostMapping("/{id}/sync")
    public R<Asn> sync(@PathVariable Long id) {
        return R.ok(service.syncToWms(id));
    }

    @PostMapping("/{id}/retry-sync")
    public R<Asn> retrySync(@PathVariable Long id) {
        return R.ok(service.syncToWms(id));
    }

    @PostMapping("/{id}/cancel")
    public R<Asn> cancel(@PathVariable Long id) {
        return R.ok(service.cancel(id));
    }

    @PostMapping("/{id}/pull-wms")
    public R<Asn> pullWms(@PathVariable Long id) {
        return R.ok(service.pullWms(id));
    }

    @PostMapping("/{id}/manual-receipt")
    public R<GoodsReceipt> manualReceipt(@PathVariable Long id, @RequestBody WmsReceiptPayload payload) {
        return R.ok(service.manualReceipt(id, payload));
    }
}
