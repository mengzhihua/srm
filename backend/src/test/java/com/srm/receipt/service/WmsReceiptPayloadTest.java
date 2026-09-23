package com.srm.receipt.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WmsReceiptPayloadTest {
    @Test
    void cumulativeReplacesQtyAndDefaultStaysDelta() {
        WmsReceiptPayload delta = new WmsReceiptPayload();
        assertTrue(delta.delta());
        WmsReceiptPayload cumulative = new WmsReceiptPayload();
        cumulative.setCumulative(true);
        assertFalse(cumulative.delta());
    }
}
