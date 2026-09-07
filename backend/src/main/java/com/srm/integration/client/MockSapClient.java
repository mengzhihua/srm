package com.srm.integration.client;

import com.srm.common.BizException;
import com.srm.evaluation.entity.SupplierEvaluation;
import com.srm.invoice.entity.Invoice;
import com.srm.purchase.entity.PurchaseOrder;
import com.srm.receipt.entity.GoodsReceipt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/** 本地模拟 SAP：生成 45xxxxxxxx PO / 50xxxxxxxx 物料凭证 / 51xxxxxxxx 发票凭证 */
@Component
@ConditionalOnProperty(name = "srm.sap.mode", havingValue = "mock", matchIfMissing = true)
public class MockSapClient implements SapClient {
    private final AtomicLong seq = new AtomicLong(0);

    @Value("${srm.sap.mock-fail-rate:0}")
    private double failRate;

    private void maybeFail() {
        if (failRate > 0 && ThreadLocalRandom.current().nextDouble() < failRate) {
            throw new BizException("SAP 模拟调用失败（mock-fail-rate）");
        }
    }

    private String doc(String prefix) {
        long n = System.currentTimeMillis() % 1000000 * 100 + seq.incrementAndGet() % 100;
        return prefix + String.format("%08d", n % 100000000L);
    }

    @Override
    public String createPurchaseOrder(PurchaseOrder po) {
        maybeFail();
        return doc("45");
    }

    @Override
    public String postGoodsReceipt(GoodsReceipt gr) {
        maybeFail();
        return doc("50");
    }

    @Override
    public String postInvoice(Invoice invoice) {
        maybeFail();
        return doc("51");
    }

    @Override
    public String syncVendorEvaluation(SupplierEvaluation eval) {
        maybeFail();
        return "EV" + String.format("%08d", System.currentTimeMillis() % 100000000L);
    }
}
