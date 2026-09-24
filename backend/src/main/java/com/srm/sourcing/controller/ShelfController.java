package com.srm.sourcing.controller;

import com.srm.common.R;
import com.srm.sourcing.entity.Coupon;
import com.srm.sourcing.entity.PlantPool;
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
                                         @RequestParam(required = false) String coupon,
                                         @RequestParam(required = false) String plantCode) {
        return R.ok(shelfService.shelf(supplierCode, coupon, plantCode));
    }

    @GetMapping("/pool")
    public R<List<PlantPool>> pool(@RequestParam(required = false) String plantCode) {
        return R.ok(shelfService.pool(plantCode));
    }

    @PostMapping("/pool")
    public R<PlantPool> addPool(@RequestBody PoolRequest req) {
        return R.ok(shelfService.addPool(req == null ? null : req.getPlantCode(), req == null ? null : req.getMaterialCode()));
    }

    @DeleteMapping("/pool")
    public R<Void> removePool(@RequestParam String plantCode, @RequestParam String materialCode) {
        shelfService.removePool(plantCode, materialCode);
        return R.ok();
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
        if (req == null) {
            return R.ok(shelfService.saveCoupon(null, null, null, null, null));
        }
        return R.ok(shelfService.saveCoupon(req.getCode(), req.getPercent(), req.getValidFrom(), req.getValidTo(), req.getMaxUses()));
    }

    @PostMapping("/coupon/use")
    public R<Coupon> useCoupon(@RequestBody CouponRequest req) {
        return R.ok(shelfService.useCoupon(req == null ? null : req.getCode()));
    }

    @Data
    public static class CodeRequest {
        private String materialCode;
    }

    @Data
    public static class CouponRequest {
        private String code;
        private BigDecimal percent;
        private String validFrom;
        private String validTo;
        private Integer maxUses;
    }

    @Data
    public static class PoolRequest {
        private String plantCode;
        private String materialCode;
    }
}
