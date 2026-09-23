package com.srm.integration.client;

import com.srm.invoice.entity.Invoice;
import com.srm.invoice.entity.InvoiceLine;
import com.srm.purchase.entity.PoLine;
import com.srm.purchase.entity.PurchaseOrder;
import com.srm.receipt.entity.GoodsReceipt;
import com.srm.receipt.entity.GrLine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EcosystemSapClientTest {
    @Test
    public void poAndGoodsReceiptUseSapDocumentNumbers() {
        PurchaseOrder po = new PurchaseOrder();
        po.setCode("PO1");
        po.setSupplierCode("SUP01");
        PoLine line = new PoLine();
        line.setMaterialCode("MAT1");
        line.setQty(new BigDecimal("2"));
        line.setPrice(new BigDecimal("8"));
        po.setLines(Collections.singletonList(line));

        Map<String, Object> body = EcosystemSapClient.poBody(po);
        assertEquals("SUP01", body.get("lifnr"));
        assertEquals("PO1", body.get("externalRef"));

        GoodsReceipt gr = new GoodsReceipt();
        gr.setPoCode("PO1");
        GrLine received = new GrLine();
        received.setMaterialCode("MAT1");
        received.setAcceptedQty(new BigDecimal("2"));
        gr.setLines(Collections.singletonList(received));
        Map<String, Object> migo = EcosystemSapClient.migoBody("4500000001", gr);
        assertEquals("4500000001", migo.get("refNo"));
        assertEquals("PO", migo.get("refType"));

        Invoice invoice = new Invoice();
        invoice.setSupplierCode("SUP01");
        invoice.setInvoiceNo("INV1");
        invoice.setAmount(new BigDecimal("16"));
        InvoiceLine invoiceLine = new InvoiceLine();
        invoiceLine.setQty(new BigDecimal("2"));
        invoiceLine.setPrice(new BigDecimal("8"));
        invoice.setLines(Collections.singletonList(invoiceLine));
        Map<String, Object> miro = EcosystemSapClient.miroBody("4500000001", invoice);
        assertEquals("4500000001", miro.get("ebeln"));
        assertEquals("INV1", miro.get("externalInvoiceNo"));
    }
}
