package com.srm.integration.client;

import com.srm.delivery.entity.Asn;

/** WMS 集成客户端：下发 ASN、查询收货进度 */
public interface WmsClient {
    WmsAsnResult createAsn(Asn asn);

    WmsAsnResult getAsn(Long wmsAsnId);
}
