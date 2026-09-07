package com.srm.sourcing.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.srm.common.R;
import com.srm.purchase.entity.PurchaseOrder;
import com.srm.sourcing.entity.Rfq;
import com.srm.sourcing.entity.RfqQuote;
import com.srm.sourcing.service.RfqService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/sourcing/rfq")
@RequiredArgsConstructor
public class RfqController {
    private final RfqService service;

    @GetMapping("/page")
    public R<Page<Rfq>> page(@RequestParam(defaultValue = "1") long current,
                             @RequestParam(defaultValue = "20") long size,
                             @RequestParam(required = false) String status,
                             @RequestParam(required = false) String keyword) {
        return R.ok(service.page(current, size, status, keyword));
    }

    @GetMapping("/{id}")
    public R<Rfq> get(@PathVariable Long id) {
        return R.ok(service.load(id));
    }

    @PostMapping
    public R<Rfq> create(@RequestBody Rfq rfq) {
        return R.ok(service.create(rfq));
    }

    @PostMapping("/{id}/publish")
    public R<Rfq> publish(@PathVariable Long id) {
        return R.ok(service.publish(id));
    }

    @PostMapping("/{id}/cancel")
    public R<Rfq> cancel(@PathVariable Long id) {
        return R.ok(service.cancel(id));
    }

    @PostMapping("/{id}/quote")
    public R<RfqQuote> quote(@PathVariable Long id, @RequestBody RfqQuote quote) {
        return R.ok(service.quote(id, quote));
    }

    @Data
    public static class AwardReq {
        private Long quoteId;
        private LocalDate expectedDate;
    }

    @PostMapping("/{id}/award")
    public R<PurchaseOrder> award(@PathVariable Long id, @RequestBody AwardReq req) {
        return R.ok(service.award(id, req.getQuoteId(), req.getExpectedDate()));
    }
}
