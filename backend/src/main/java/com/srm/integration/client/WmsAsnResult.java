package com.srm.integration.client;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** WMS ASN 查询/创建返回 */
@Data
public class WmsAsnResult {
    private Long id;
    private String code;
    private String status;
    private LocalDate expectedDate;
    private List<Line> lines;

    @Data
    public static class Line {
        private Long id;
        private String itemCode;
        private String lotNo;
        private BigDecimal expectedQty;
        private BigDecimal receivedQty;
        private BigDecimal rejectedQty;
    }
}
