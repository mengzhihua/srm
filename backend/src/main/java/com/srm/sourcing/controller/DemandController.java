package com.srm.sourcing.controller;

import com.srm.common.R;
import com.srm.sourcing.entity.DemandItem;
import com.srm.sourcing.entity.PurchaseRequisition;
import com.srm.sourcing.service.DemandService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sourcing")
@RequiredArgsConstructor
public class DemandController {
    private final DemandService demandService;

    @GetMapping("/demand")
    public R<List<DemandItem>> list() {
        return R.ok(demandService.mine());
    }

    @PostMapping("/demand")
    public R<DemandItem> add(@RequestBody AddRequest req) {
        return R.ok(demandService.add(req == null ? null : req.getText(), req == null ? null : req.getQty()));
    }

    @PostMapping("/demand/convert")
    public R<PurchaseRequisition> convert() {
        return R.ok(demandService.convert());
    }

    @PostMapping("/catalog/match")
    public R<List<Map<String, Object>>> match(@RequestBody MatchRequest req) {
        return R.ok(demandService.match(req == null ? null : req.getLines()));
    }

    @Data
    public static class AddRequest {
        private String text;
        private BigDecimal qty;
    }

    @Data
    public static class MatchRequest {
        private List<Map<String, Object>> lines;
    }
}
