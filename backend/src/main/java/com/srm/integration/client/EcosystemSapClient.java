package com.srm.integration.client;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.srm.common.BizException;
import com.srm.evaluation.entity.SupplierEvaluation;
import com.srm.invoice.entity.Invoice;
import com.srm.invoice.entity.InvoiceLine;
import com.srm.purchase.entity.PoLine;
import com.srm.purchase.entity.PurchaseOrder;
import com.srm.purchase.mapper.PurchaseOrderMapper;
import com.srm.receipt.entity.GoodsReceipt;
import com.srm.receipt.entity.GrLine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 调用本套 SAP 演示的登录接口和 MM 过账，不走 S/4 OData，也不生成 50xxxxxxxx 模拟凭证。
 * 物料、供应商必须已经在 SAP 主数据里，否则按 SAP 的校验失败并留下集成日志。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "srm.sap.mode", havingValue = "ecosystem")
public class EcosystemSapClient implements SapClient {
    private final RestTemplate rest;
    private final ObjectMapper om = new ObjectMapper();
    private final PurchaseOrderMapper purchaseOrders;
    private final String baseUrl;
    private final String username;
    private final String password;
    private volatile String token;

    public EcosystemSapClient(RestTemplateBuilder builder,
                              PurchaseOrderMapper purchaseOrders,
                              @Value("${srm.sap.base-url:http://localhost:8085}") String baseUrl,
                              @Value("${srm.sap.username:admin}") String username,
                              @Value("${srm.sap.password:admin123}") String password) {
        this.rest = builder.setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(30)).build();
        this.purchaseOrders = purchaseOrders;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/$", "");
        this.username = username;
        this.password = password;
    }

    @Override
    public String createPurchaseOrder(PurchaseOrder po) {
        JsonNode data = post("/api/mm/po", poBody(po), false);
        String ebeln = text(data, "ebeln");
        if (ebeln == null) {
            throw new BizException("SAP 未返回采购订单号");
        }
        return ebeln;
    }

    @Override
    public String postGoodsReceipt(GoodsReceipt gr) {
        String sapPo = sapPoNo(gr.getPoCode());
        JsonNode data = post("/api/mm/migo", migoBody(sapPo, gr), false);
        String mblnr = text(data, "mblnr");
        if (mblnr == null) {
            throw new BizException("SAP 未返回物料凭证号");
        }
        return mblnr;
    }

    @Override
    public String postInvoice(Invoice invoice) {
        String sapPo = sapPoNo(invoice.getPoCode());
        JsonNode data = post("/api/mm/miro", miroBody(sapPo, invoice), false);
        String belnr = text(data, "belnr");
        if (belnr == null) {
            throw new BizException("SAP 未返回发票凭证号");
        }
        return belnr;
    }

    @Override
    public String syncVendorEvaluation(SupplierEvaluation eval) {
        throw new BizException("SAP 演示没有供应商考核接口，考核结果留在 SRM");
    }

    static Map<String, Object> poBody(PurchaseOrder po) {
        Map<String, Object> body = new HashMap<>();
        body.put("lifnr", po.getSupplierCode());
        body.put("externalRef", po.getCode());
        List<Map<String, Object>> items = new ArrayList<>();
        if (po.getLines() != null) {
            for (PoLine line : po.getLines()) {
                Map<String, Object> item = new HashMap<>();
                item.put("matnr", line.getMaterialCode());
                item.put("werks", "1000");
                item.put("qty", line.getQty());
                item.put("price", line.getPrice());
                if (line.getDeliveryDate() != null) {
                    item.put("deliveryDate", line.getDeliveryDate().toString());
                }
                items.add(item);
            }
        }
        body.put("items", items);
        return body;
    }

    static Map<String, Object> migoBody(String sapPo, GoodsReceipt gr) {
        Map<String, Object> body = new HashMap<>();
        body.put("bwart", "101");
        body.put("refType", "PO");
        body.put("refNo", sapPo);
        List<Map<String, Object>> items = new ArrayList<>();
        if (gr.getLines() != null) {
            for (GrLine line : gr.getLines()) {
                if (line.getAcceptedQty() == null || line.getAcceptedQty().signum() <= 0) {
                    continue;
                }
                Map<String, Object> item = new HashMap<>();
                item.put("matnr", line.getMaterialCode());
                item.put("werks", "1000");
                item.put("qty", line.getAcceptedQty());
                items.add(item);
            }
        }
        body.put("items", items);
        return body;
    }

    static Map<String, Object> miroBody(String sapPo, Invoice invoice) {
        Map<String, Object> body = new HashMap<>();
        body.put("lifnr", invoice.getSupplierCode());
        body.put("ebeln", sapPo);
        body.put("externalInvoiceNo", invoice.getInvoiceNo());
        body.put("grossAmount", invoice.getAmount());
        List<Map<String, Object>> items = new ArrayList<>();
        if (invoice.getLines() != null) {
            for (InvoiceLine line : invoice.getLines()) {
                Map<String, Object> item = new HashMap<>();
                item.put("ebeln", sapPo);
                item.put("qty", line.getQty());
                item.put("price", line.getPrice());
                items.add(item);
            }
        }
        body.put("items", items);
        return body;
    }

    private String sapPoNo(String poCode) {
        if (poCode == null || poCode.trim().isEmpty()) {
            throw new BizException("收货缺少采购订单号，不能过账到 SAP");
        }
        PurchaseOrder po = purchaseOrders.selectOne(new LambdaQueryWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getCode, poCode.trim()).last("LIMIT 1"));
        if (po == null || po.getSapPoNo() == null || po.getSapPoNo().trim().isEmpty()) {
            throw new BizException("采购订单尚未在 SAP 建单: " + poCode);
        }
        return po.getSapPoNo().trim();
    }

    private synchronized String login() {
        try {
            Map<String, String> body = new HashMap<>();
            body.put("username", username);
            body.put("password", password);
            String resp = rest.postForObject(baseUrl + "/api/auth/login", body, String.class);
            JsonNode data = om.readTree(resp == null ? "{}" : resp).path("data");
            this.token = data.path("token").asText();
            if (this.token == null || this.token.isEmpty() || "null".equals(this.token)) {
                throw new BizException("SAP 登录失败: " + resp);
            }
            return this.token;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("SAP 登录失败: " + e.getMessage());
        }
    }

    private JsonNode post(String path, Object body, boolean retried) {
        if (token == null) {
            login();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        try {
            ResponseEntity<String> resp = rest.exchange(baseUrl + path, HttpMethod.POST,
                    new HttpEntity<>(om.writeValueAsString(body), headers), String.class);
            JsonNode root = om.readTree(resp.getBody() == null ? "{}" : resp.getBody());
            if (root.path("code").asInt() != 0) {
                throw new BizException("SAP 返回错误: " + root.path("msg").asText(resp.getBody()));
            }
            return root.path("data");
        } catch (HttpClientErrorException.Unauthorized e) {
            if (retried) {
                throw new BizException("SAP 鉴权失败");
            }
            this.token = null;
            return post(path, body, true);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("SAP 调用失败: " + e.getMessage());
        }
    }

    private static String text(JsonNode data, String field) {
        if (data == null || data.isMissingNode() || data.get(field) == null || data.get(field).isNull()) {
            return null;
        }
        String value = data.get(field).asText();
        return value == null || value.isEmpty() || "null".equals(value) ? null : value;
    }
}
