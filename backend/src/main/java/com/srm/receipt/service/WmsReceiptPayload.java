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
    /** true 时行数量是累计收货，不是本次增量。缺省仍按增量，兼容原来的回调。 */
    private Boolean cumulative;
    private List<Line> lines;

    /** 累计回传覆盖行数量；缺省按增量累加。 */
    public boolean delta() {
        return !Boolean.TRUE.equals(cumulative);
    }

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
