package com.srm.integration.client;

import com.srm.evaluation.entity.SupplierEvaluation;
import com.srm.invoice.entity.Invoice;
import com.srm.purchase.entity.PurchaseOrder;
import com.srm.receipt.entity.GoodsReceipt;

/** SAP（标准 S/4HANA）集成客户端 */
public interface SapClient {
    /** 创建采购订单，返回 SAP PO 号 */
    String createPurchaseOrder(PurchaseOrder po);

    /** 收货过账（MIGO 101），返回物料凭证号 */
    String postGoodsReceipt(GoodsReceipt gr);

    /** 发票校验过账，返回发票凭证号 */
    String postInvoice(Invoice invoice);

    /** 同步供应商考核结果，返回 SAP 侧确认号 */
    String syncVendorEvaluation(SupplierEvaluation eval);
}
