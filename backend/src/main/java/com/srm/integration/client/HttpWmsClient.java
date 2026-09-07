package com.srm.integration.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.srm.basic.entity.Material;
import com.srm.basic.entity.Plant;
import com.srm.basic.entity.Supplier;
import com.srm.basic.mapper.MaterialMapper;
import com.srm.basic.mapper.PlantMapper;
import com.srm.basic.mapper.SupplierMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.srm.common.BizException;
import com.srm.delivery.entity.Asn;
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
import java.util.stream.Collectors;

/**
 * 调 WMS REST 接口（同机 /home/ubuntu/repos/wms 部署，默认 http://localhost:8080）。
 * 登录 POST /api/auth/login 取 token 缓存，401 时刷新重试一次。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "srm.wms.mode", havingValue = "http")
public class HttpWmsClient implements WmsClient {
    private final RestTemplate rest;
    private final ObjectMapper om;
    private final String baseUrl;
    private final String username;
    private final String password;
    private final SupplierMapper supplierMapper;
    private final MaterialMapper materialMapper;
    private final PlantMapper plantMapper;
    private volatile String token;

    public HttpWmsClient(RestTemplateBuilder builder,
                         @Value("${srm.wms.base-url:http://localhost:8080}") String baseUrl,
                         @Value("${srm.wms.username:admin}") String username,
                         @Value("${srm.wms.password:admin123}") String password,
                         SupplierMapper supplierMapper, MaterialMapper materialMapper, PlantMapper plantMapper) {
        this.rest = builder.setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(30)).build();
        this.om = new ObjectMapper().registerModule(new JavaTimeModule());
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.username = username;
        this.password = password;
        this.supplierMapper = supplierMapper;
        this.materialMapper = materialMapper;
        this.plantMapper = plantMapper;
    }

    private synchronized String login() {
        try {
            Map<String, String> body = new HashMap<>();
            body.put("username", username);
            body.put("password", password);
            String resp = rest.postForObject(baseUrl + "/api/auth/login", body, String.class);
            JsonNode data = om.readTree(resp).path("data");
            this.token = data.path("token").asText();
            if (this.token == null || this.token.isEmpty() || "null".equals(this.token)) {
                throw new BizException("WMS 登录失败: " + resp);
            }
            return this.token;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("WMS 登录失败: " + e.getMessage());
        }
    }

    private JsonNode call(HttpMethod method, String path, Object body, boolean retried) {
        if (token == null) {
            login();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        try {
            ResponseEntity<String> resp = rest.exchange(baseUrl + path,
                    method, new HttpEntity<>(body == null ? null : om.writeValueAsString(body), headers), String.class);
            JsonNode root = om.readTree(resp.getBody());
            if (root.path("code").asInt() != 0) {
                throw new BizException("WMS 返回错误: " + resp.getBody());
            }
            return root.path("data");
        } catch (HttpClientErrorException.Unauthorized e) {
            if (retried) {
                throw new BizException("WMS 鉴权失败");
            }
            this.token = null;
            return call(method, path, body, true);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("WMS 调用失败: " + e.getMessage());
        }
    }

    @Override
    public WmsAsnResult createAsn(Asn asn) {
        Supplier supplier = supplierMapper.selectOne(new LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getCode, asn.getSupplierCode()));
        Plant plant = plantMapper.selectOne(new LambdaQueryWrapper<Plant>()
                .eq(Plant::getCode, asn.getPlantCode()));
        if (plant == null || plant.getWmsWarehouseCode() == null) {
            throw new BizException("工厂 " + asn.getPlantCode() + " 未配置 WMS 仓库映射");
        }
        Map<String, String> wmsItems = materialMapper.selectList(null).stream()
                .filter(m -> m.getWmsItemCode() != null)
                .collect(Collectors.toMap(Material::getCode, Material::getWmsItemCode, (a, b) -> a));

        Map<String, Object> body = new HashMap<>();
        body.put("warehouseCode", plant.getWmsWarehouseCode());
        body.put("ownerCode", plant.getWmsOwnerCode());
        body.put("supplierCode", supplier != null && supplier.getWmsSupplierCode() != null
                ? supplier.getWmsSupplierCode() : asn.getSupplierCode());
        body.put("type", "PURCHASE");
        body.put("externalNo", asn.getCode());
        if (asn.getExpectedDate() != null) {
            body.put("expectedDate", asn.getExpectedDate().toString());
        }
        body.put("remark", "SRM PO " + asn.getPoCode());
        List<Map<String, Object>> lines = new ArrayList<>();
        for (com.srm.delivery.entity.AsnLine l : asn.getLines()) {
            Map<String, Object> item = new HashMap<>();
            item.put("itemCode", wmsItems.getOrDefault(l.getMaterialCode(), l.getMaterialCode()));
            item.put("expectedQty", l.getQty());
            if (l.getLotNo() != null) {
                item.put("lotNo", l.getLotNo());
            }
            if (l.getExpiryDate() != null) {
                item.put("expiryDate", l.getExpiryDate().toString());
            }
            lines.add(item);
        }
        body.put("lines", lines);

        JsonNode data = call(HttpMethod.POST, "/api/inbound/asn", body, false);
        try {
            return om.treeToValue(data, WmsAsnResult.class);
        } catch (Exception e) {
            throw new BizException("解析 WMS ASN 响应失败: " + e.getMessage());
        }
    }

    @Override
    public WmsAsnResult getAsn(Long wmsAsnId) {
        JsonNode data = call(HttpMethod.GET, "/api/inbound/asn/" + wmsAsnId, null, false);
        try {
            return om.treeToValue(data, WmsAsnResult.class);
        } catch (Exception e) {
            throw new BizException("解析 WMS ASN 响应失败: " + e.getMessage());
        }
    }
}
