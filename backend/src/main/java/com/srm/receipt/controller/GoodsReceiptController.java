package com.srm.receipt.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.srm.common.R;
import com.srm.receipt.entity.GoodsReceipt;
import com.srm.receipt.service.ReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/receipt")
@RequiredArgsConstructor
public class GoodsReceiptController {
    private final ReceiptService service;

    @GetMapping("/page")
    public R<Page<GoodsReceipt>> page(@RequestParam(defaultValue = "1") long current,
                                      @RequestParam(defaultValue = "20") long size,
                                      @RequestParam(required = false) String status,
                                      @RequestParam(required = false) String keyword) {
        return R.ok(service.page(current, size, status, keyword));
    }

    @GetMapping("/{id}")
    public R<GoodsReceipt> get(@PathVariable Long id) {
        return R.ok(service.load(id));
    }

    @PostMapping("/{id}/retry-post")
    public R<GoodsReceipt> retryPost(@PathVariable Long id) {
        return R.ok(service.retryPost(id));
    }
}
