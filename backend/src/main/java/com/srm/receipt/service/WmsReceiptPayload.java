package com.srm.receipt.service;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** WMS 收货回传（回调/轮询/手工共用同一结构） */
@Data
public class WmsReceiptPayload {
    /** WMS ASN 单号 */
    private String wmsAsnCode;
    /** SRM ASN 单号（WMS externalNo） */
    private String externalNo;
    private LocalDateTime receivedAt;
    private List<Line> lines;

    @Data
    public static class Line {
        /** WMS 物料编码（srm_material.wms_item_code） */
        private String itemCode;
        private String lotNo;
        private BigDecimal receivedQty;
        private BigDecimal rejectedQty;
        private String rejectReason;
    }
}
