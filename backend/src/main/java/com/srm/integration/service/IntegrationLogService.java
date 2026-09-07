package com.srm.integration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.srm.integration.entity.IntegrationLog;
import com.srm.integration.mapper.IntegrationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/** 所有外部系统调用（SAP/WMS）统一在此写集成日志 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntegrationLogService {
    private static final int MAX_LEN = 60000;
    private final IntegrationLogMapper mapper;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public static final String SAP_CREATE_PO = "SAP_CREATE_PO";
    public static final String SAP_POST_GR = "SAP_POST_GR";
    public static final String SAP_POST_INVOICE = "SAP_POST_INVOICE";
    public static final String SAP_SYNC_EVAL = "SAP_SYNC_EVAL";
    public static final String WMS_CREATE_ASN = "WMS_CREATE_ASN";
    public static final String WMS_GET_ASN = "WMS_GET_ASN";
    public static final String WMS_RECEIPT_IN = "WMS_RECEIPT_IN";
    public static final String WMS_EVAL_IN = "WMS_EVAL_IN";

    /**
     * 执行外部调用并写日志。抛出 BizException 时记 FAILED 并原样上抛。
     */
    public <T> T execute(String system, String action, String bizType, Long bizId, String bizCode,
                         Object request, Supplier<T> call) {
        IntegrationLog l = new IntegrationLog();
        l.setDirection("OUT");
        l.setSystem(system);
        l.setAction(action);
        l.setBizType(bizType);
        l.setBizId(bizId);
        l.setBizCode(bizCode);
        l.setRequest(trim(toJson(request)));
        long start = System.currentTimeMillis();
        try {
            T result = call.get();
            l.setStatus("SUCCESS");
            l.setResponse(trim(toJson(result)));
            return result;
        } catch (RuntimeException e) {
            l.setStatus("FAILED");
            l.setErrorMsg(trim(e.getMessage(), 512));
            throw e;
        } finally {
            l.setDurationMs((int) (System.currentTimeMillis() - start));
            try {
                mapper.insert(l);
            } catch (RuntimeException ex) {
                log.warn("写集成日志失败: {}", ex.getMessage());
            }
        }
    }

    /** 记录一次入站（外部系统推送/回调） */
    public IntegrationLog inbound(String system, String action, String bizType, Long bizId, String bizCode,
                                  Object request, String status, String errorMsg) {
        IntegrationLog l = new IntegrationLog();
        l.setDirection("IN");
        l.setSystem(system);
        l.setAction(action);
        l.setBizType(bizType);
        l.setBizId(bizId);
        l.setBizCode(bizCode);
        l.setRequest(trim(toJson(request)));
        l.setStatus(status);
        l.setErrorMsg(trim(errorMsg, 512));
        mapper.insert(l);
        return l;
    }

    public void markRetried(Long logId) {
        IntegrationLog l = mapper.selectById(logId);
        if (l != null) {
            l.setRetryCount(l.getRetryCount() == null ? 1 : l.getRetryCount() + 1);
            mapper.updateById(l);
        }
    }

    private String toJson(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof String) {
            return (String) o;
        }
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }

    private static String trim(String s) {
        return trim(s, MAX_LEN);
    }

    private static String trim(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
