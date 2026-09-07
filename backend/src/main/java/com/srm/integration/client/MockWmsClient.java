package com.srm.integration.client;

import com.srm.delivery.entity.Asn;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** 本地模拟 WMS：内存记录 ASN，收货由回调/手工入口驱动 */
@Component
@ConditionalOnProperty(name = "srm.wms.mode", havingValue = "mock", matchIfMissing = true)
public class MockWmsClient implements WmsClient {
    private final AtomicLong seq = new AtomicLong(0);
    private final Map<Long, WmsAsnResult> store = new ConcurrentHashMap<>();

    @Override
    public WmsAsnResult createAsn(Asn asn) {
        WmsAsnResult r = new WmsAsnResult();
        long id = 9000 + seq.incrementAndGet();
        r.setId(id);
        r.setCode("WMS-ASN-" + String.format("%06d", id));
        r.setStatus("NEW");
        r.setExpectedDate(asn.getExpectedDate());
        r.setLines(new ArrayList<>());
        asn.getLines().forEach(l -> {
            WmsAsnResult.Line line = new WmsAsnResult.Line();
            line.setItemCode(l.getMaterialCode());
            line.setLotNo(l.getLotNo());
            line.setExpectedQty(l.getQty());
            r.getLines().add(line);
        });
        store.put(id, r);
        return r;
    }

    @Override
    public WmsAsnResult getAsn(Long wmsAsnId) {
        return store.get(wmsAsnId);
    }
}
