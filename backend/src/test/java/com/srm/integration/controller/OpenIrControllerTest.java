package com.srm.integration.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
public class OpenIrControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void snapshotsExposeSkuAndDelayedAsnThenExpedite() throws Exception {
        String snapshots = mockMvc.perform(get("/api/open/ir/snapshots")
                        .header("X-Api-Key", "test-open-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.system").value("SRM"))
                .andReturn().getResponse().getContentAsString();
        JsonNode rows = objectMapper.readTree(snapshots).get("data").get("snapshots");
        JsonNode delayed = null;
        JsonNode po = null;
        JsonNode pr = null;
        for (JsonNode row : rows) {
            if ("ASN-IR-DELAY".equals(row.path("bizKey").asText())) {
                delayed = row;
            }
            if ("PO-IR-EXPEDITE".equals(row.path("bizKey").asText())) {
                po = row;
            }
            if ("PR-IR-DRAFT".equals(row.path("bizKey").asText())) {
                pr = row;
            }
        }
        assertNotNull(delayed, "应包含延误 ASN");
        assertEquals("DELAYED", delayed.path("status").asText());
        assertEquals("SKU001", delayed.path("sku").asText());
        assertEquals("PO-IR-EXPEDITE", delayed.path("poCode").asText());
        assertNotNull(po);
        assertEquals("SKU001", po.path("sku").asText());
        assertNotNull(pr);
        assertEquals("DRAFT", pr.path("status").asText());
        JsonNode risk = null;
        for (JsonNode row : rows) {
            if ("SUPPLIER".equals(row.path("dataType").asText())
                    && "SUP03".equals(row.path("bizKey").asText())) {
                risk = row;
            }
        }
        assertNotNull(risk, "应包含低分供应商");
        assertEquals("RISK", risk.path("status").asText());

        mockMvc.perform(post("/api/open/ir/actions")
                        .header("X-Api-Key", "test-open-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SRM_EXPEDITE_PO\",\"targetKey\":\"PO-IR-EXPEDITE\","
                                + "\"params\":{\"reason\":\"库存风险\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/open/ir/actions")
                        .header("X-Api-Key", "test-open-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SRM_SUBMIT_PR\",\"targetKey\":\"PR-IR-DRAFT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));

        String after = mockMvc.perform(get("/api/open/ir/snapshots")
                        .header("X-Api-Key", "test-open-key"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode submitted = null;
        for (JsonNode row : objectMapper.readTree(after).get("data").get("snapshots")) {
            if ("PR-IR-DRAFT".equals(row.path("bizKey").asText())) {
                submitted = row;
            }
        }
        assertNotNull(submitted);
        assertEquals("SUBMITTED", submitted.path("status").asText());
        assertTrue(true);
    }
}
