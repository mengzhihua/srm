package com.srm.integration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.srm.delivery.entity.Asn;
import com.srm.delivery.mapper.AsnMapper;
import com.srm.delivery.service.AsnService;
import com.srm.integration.client.WmsAsnResult;
import com.srm.integration.client.WmsClient;
import com.srm.receipt.service.ReceiptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/** http 模式下轮询在途 ASN 的 WMS 收货进度 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "srm.wms.mode", havingValue = "http")
public class WmsSyncJob {
    private final AsnMapper asnMapper;
    private final WmsClient wmsClient;
    private final ReceiptService receiptService;

    @Scheduled(fixedDelayString = "${srm.wms.poll-interval:30000}", initialDelay = 15000)
    public void poll() {
        List<Asn> open = asnMapper.selectList(new LambdaQueryWrapper<Asn>()
                .in(Asn::getStatus, "SYNCED", "RECEIVING")
                .isNotNull(Asn::getWmsAsnId));
        for (Asn asn : open) {
            try {
                WmsAsnResult w = wmsClient.getAsn(asn.getWmsAsnId());
                if (w == null) {
                    continue;
                }
                receiptService.processWmsReceipt(asn, AsnService.toPayload(asn, w),
                        "WMS_POLL", false, AsnService.isWmsDone(w.getStatus()));
            } catch (RuntimeException e) {
                log.warn("轮询 WMS ASN {} 失败: {}", asn.getCode(), e.getMessage());
            }
        }
    }
}
