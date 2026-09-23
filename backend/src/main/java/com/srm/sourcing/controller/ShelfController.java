package com.srm.sourcing.controller;

import com.srm.common.R;
import com.srm.sourcing.entity.Coupon;
import com.srm.sourcing.entity.ShelfMark;
import com.srm.sourcing.service.ShelfService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sourcing/shelf")
@RequiredArgsConstructor
public class ShelfController {
    private final ShelfService shelfService;

    @GetMapping
    public R<Map<String, Object>> shelf(@RequestParam(required = false) String supplierCode,
                                         @RequestParam(required = false) String coupon) {
        return R.ok(shelfService.shelf(supplierCode, coupon));
    }

    @GetMapping("/marks")
    public R<Map<String, Object>> marks() {
        return R.ok(shelfService.marks());
    }

    @PostMapping("/favorite")
    public R<ShelfMark> favorite(@RequestBody CodeRequest req) {
        return R.ok(shelfService.favorite(req == null ? null : req.getMaterialCode()));
    }

    @DeleteMapping("/favorite")
    public R<Void> unfavorite(@RequestParam String materialCode) {
        shelfService.unfavorite(materialCode);
        return R.ok();
    }

    @PostMapping("/history")
    public R<ShelfMark> history(@RequestBody CodeRequest req) {
        return R.ok(shelfService.history(req == null ? null : req.getMaterialCode()));
    }

    @GetMapping("/coupon")
    public R<List<Coupon>> coupons() {
        return R.ok(shelfService.coupons());
    }

    @PostMapping("/coupon")
    public R<Coupon> saveCoupon(@RequestBody CouponRequest req) {
        return R.ok(shelfService.saveCoupon(req == null ? null : req.getCode(), req == null ? null : req.getPercent()));
    }

    @Data
    public static class CodeRequest {
        private String materialCode;
    }

    @Data
    public static class CouponRequest {
        private String code;
        private BigDecimal percent;
    }
}
