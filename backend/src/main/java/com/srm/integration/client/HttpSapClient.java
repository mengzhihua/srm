package com.srm.integration.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.srm.common.BizException;
import com.srm.evaluation.entity.SupplierEvaluation;
import com.srm.invoice.entity.Invoice;
import com.srm.purchase.entity.PurchaseOrder;
import com.srm.receipt.entity.GoodsReceipt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * S/4HANA OData 风格 HTTP 客户端（Basic Auth）。
 * 路径默认标准 API：A_PurchaseOrder / A_MaterialDocumentHeader / A_SupplierInvoice。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "srm.sap.mode", havingValue = "http")
public class HttpSapClient implements SapClient {
    private final RestTemplate rest;
    private final ObjectMapper om = new ObjectMapper();
    private final String baseUrl;
    private final HttpHeaders headers;

    public HttpSapClient(RestTemplateBuilder builder,
                         @Value("${srm.sap.base-url:}") String baseUrl,
                         @Value("${srm.sap.username:}") String username,
                         @Value("${srm.sap.password:}") String password) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/$", "");
        this.rest = builder.setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(30)).build();
        this.headers = new HttpHeaders();
        this.headers.setContentType(MediaType.APPLICATION_JSON);
        String auth = Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        this.headers.set(HttpHeaders.AUTHORIZATION, "Basic " + auth);
    }

    private String post(String path, Object body, String... docFields) {
        try {
            String resp = rest.postForObject(baseUrl + path, new HttpEntity<>(om.writeValueAsString(body), headers), String.class);
            JsonNode node = om.readTree(resp == null ? "{}" : resp);
            JsonNode d = node.has("d") ? node.get("d") : node;
            for (String f : docFields) {
                if (d.hasNonNull(f)) {
                    return d.get(f).asText();
                }
            }
            throw new BizException("SAP 响应中未找到凭证号: " + resp);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("SAP 调用失败: " + e.getMessage());
        }
    }

    @Override
    public String createPurchaseOrder(PurchaseOrder po) {
        Map<String, Object> body = new HashMap<>();
        body.put("PurchaseOrderType", "NB");
        body.put("Supplier", po.getSupplierCode());
        body.put("PurchasingOrganization", "1000");
        body.put("PurchasingGroup", "001");
        body.put("CompanyCode", "1000");
        body.put("DocumentCurrency", po.getCurrency() == null ? "CNY" : po.getCurrency());
        body.put("to_PurchaseOrderItem", po.getLines().stream().map(l -> {
            Map<String, Object> item = new HashMap<>();
            item.put("Material", l.getMaterialCode());
            item.put("OrderQuantity", l.getQty());
            item.put("NetPriceAmount", l.getPrice());
            item.put("Plant", po.getPlantCode());
            if (l.getDeliveryDate() != null) {
                item.put("ScheduleLineDeliveryDate", l.getDeliveryDate().toString());
            }
            return item;
        }).collect(Collectors.toList()));
        return post("/API_PURCHASEORDER_PROCESS_SRV/A_PurchaseOrder", body, "PurchaseOrder", "PurchaseOrderNo");
    }

    @Override
    public String postGoodsReceipt(GoodsReceipt gr) {
        Map<String, Object> body = new HashMap<>();
        body.put("GoodsMovementCode", "01");
        body.put("PostingDate", java.time.LocalDate.now().toString());
        body.put("to_MaterialDocumentItem", gr.getLines().stream().map(l -> {
            Map<String, Object> item = new HashMap<>();
            item.put("PurchaseOrder", gr.getPoCode());
            item.put("Material", l.getMaterialCode());
            item.put("QuantityInBaseUnit", l.getAcceptedQty());
            item.put("GoodsMovementType", "101");
            return item;
        }).collect(Collectors.toList()));
        return post("/API_MATERIAL_DOCUMENT_SRV/A_MaterialDocumentHeader", body, "MaterialDocument", "MaterialDocumentNo");
    }

    @Override
    public String postInvoice(Invoice invoice) {
        Map<String, Object> body = new HashMap<>();
        body.put("SupplierInvoice", invoice.getInvoiceNo());
        body.put("InvoicingParty", invoice.getSupplierCode());
        body.put("DocumentCurrency", "CNY");
        body.put("InvoiceGrossAmount", invoice.getAmount() == null ? BigDecimal.ZERO : invoice.getAmount());
        body.put("to_SuplrInvcItemPurOrdRef", invoice.getLines().stream().map(l -> {
            Map<String, Object> item = new HashMap<>();
            item.put("PurchaseOrder", invoice.getPoCode());
            item.put("PurchaseOrderItem", l.getMaterialCode());
            item.put("QuantityInPurchaseOrderUnit", l.getQty());
            item.put("PurchaseOrderItemPrice", l.getPrice());
            return item;
        }).collect(Collectors.toList()));
        return post("/API_SUPPLIERINVOICE_PROCESS_SRV/A_SupplierInvoice", body, "SupplierInvoice", "FiscalYear");
    }

    @Override
    public String syncVendorEvaluation(SupplierEvaluation eval) {
        Map<String, Object> body = new HashMap<>();
        body.put("Supplier", eval.getSupplierCode());
        body.put("EvaluationPeriod", eval.getPeriod());
        body.put("Score", eval.getAvgScore());
        body.put("Grade", eval.getGrade());
        return post("/API_SUPPLIER_EVALUATION_SRV/A_SupplierEvaluation", body, "EvaluationId", "Id");
    }
}
