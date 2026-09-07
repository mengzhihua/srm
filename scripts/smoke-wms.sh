#!/usr/bin/env bash
# SRM <-> WMS 真实联调冒烟（SRM srm.wms.mode=http，轮询回传收货）：
#   SRM PO -> 下发SAP -> 供应商确认 -> ASN(自动同步WMS) -> WMS收货 -> SRM轮询
#   -> GR生成并过账SAP -> PO收货数量 -> 考核记录 -> 集成日志
# 前置：WMS 后端 8080（admin/admin123）、SRM 后端 8081 均以 http 模式运行。
# Usage: scripts/smoke-wms.sh [srm_base] [wms_base]
set -euo pipefail
SRM="${1:-http://localhost:8081}/api"
WMS="${2:-http://localhost:8080}/api"
J='Content-Type: application/json'
BODY=/tmp/srm_wms_body

need() { command -v "$1" >/dev/null || { echo "missing $1"; exit 1; }; }
need curl; need jq
fail() { echo "FAIL: $*"; exit 1; }

SRM_TOKEN=""; WMS_TOKEN=""
http() { # method url token [json] -> prints body, dies on non-2xx
  local code
  if [ -n "${4:-}" ]; then
    code=$(curl -s -o "$BODY" -w '%{http_code}' -X "$1" "$2" -H "$J" -H "Authorization: Bearer $3" -d "$4")
  else
    code=$(curl -s -o "$BODY" -w '%{http_code}' -X "$1" "$2" -H "$J" -H "Authorization: Bearer $3")
  fi
  [ "$code" = "200" ] || fail "$1 $2 http=$code $(cat "$BODY")"
  cat "$BODY"
}
scall() { local out; out=$(http "$1" "$SRM$2" "$SRM_TOKEN" "${3:-}")
  [ "$(echo "$out" | jq -r .code)" = "0" ] || fail "SRM $1 $2 -> $out"; echo "$out" | jq -c .data; }
wcall() { local out; out=$(http "$1" "$WMS$2" "$WMS_TOKEN" "${3:-}")
  [ "$(echo "$out" | jq -r .code)" = "0" ] || fail "WMS $1 $2 -> $out"; echo "$out" | jq -c .data; }

echo "== 0. logins"
SRM_TOKEN=$(scall POST /auth/login '{"username":"buyer","password":"buyer123"}' | jq -r .token)
SUP_TOKEN=$(scall POST /auth/login '{"username":"sup01","password":"sup123"}' | jq -r .token)
WMS_TOKEN=$(wcall POST /auth/login '{"username":"admin","password":"admin123"}' | jq -r .token)
echo "srm buyer + sup01 + wms admin ok"

echo "== 1. SRM: PO -> approve -> send-to-sap -> supplier confirm"
PO=$(scall POST /purchase/order '{"supplierCode":"SUP01","plantCode":"P001","expectedDate":"2026-10-01",
  "lines":[{"materialCode":"SKU001","qty":100,"price":45},{"materialCode":"SKU003","qty":50,"price":2.5}]}')
PO_ID=$(echo "$PO" | jq .id); PO_CODE=$(echo "$PO" | jq -r .code)
scall POST "/purchase/order/$PO_ID/approve" >/dev/null
PO=$(scall POST "/purchase/order/$PO_ID/send-to-sap")
echo "po=$PO_CODE sapPoNo=$(echo "$PO" | jq -r .sapPoNo)"
SRM_TOKEN=$SUP_TOKEN
PO=$(scall POST "/purchase/order/$PO_ID/confirm")
[ "$(echo "$PO" | jq -r .status)" = "CONFIRMED" ] || fail "confirm"
L1=$(echo "$PO" | jq '.lines[0].id'); L2=$(echo "$PO" | jq '.lines[1].id')

echo "== 2. SRM: 供应商建 ASN -> 自动同步 WMS"
ASN=$(scall POST /delivery/asn "{\"poId\":$PO_ID,\"expectedDate\":\"2026-09-10\",\"carrier\":\"SF\",\"trackingNo\":\"SF9001\",
  \"lines\":[{\"poLineId\":$L1,\"qty\":100,\"lotNo\":\"LOT-HTTP-A\"},{\"poLineId\":$L2,\"qty\":50,\"lotNo\":\"LOT-HTTP-B\"}]}")
ASN_ID=$(echo "$ASN" | jq .id); ASN_CODE=$(echo "$ASN" | jq -r .code)
[ "$(echo "$ASN" | jq -r .status)" = "SYNCED" ] || fail "asn not SYNCED: $ASN"
WMS_ASN_ID=$(echo "$ASN" | jq .wmsAsnId); WMS_ASN_CODE=$(echo "$ASN" | jq -r .wmsAsnCode)
echo "$WMS_ASN_CODE" | grep -q '^ASN' || fail "unexpected wmsAsnCode: $WMS_ASN_CODE"
echo "srm asn=$ASN_CODE -> wms asn=$WMS_ASN_CODE (id=$WMS_ASN_ID)"

echo "== 3. WMS: 校验下发的 ASN"
WASN=$(wcall GET "/inbound/asn/$WMS_ASN_ID")
[ "$(echo "$WASN" | jq -r .externalNo)" = "$ASN_CODE" ] || fail "externalNo mismatch: $WASN"
[ "$(echo "$WASN" | jq -r '.lines[0].itemCode')" = "SKU001" ] || fail "line1 itemCode"
[ "$(echo "$WASN" | jq '.lines[0].expectedQty')" = "100" ] || fail "line1 expectedQty"
[ "$(echo "$WASN" | jq '.lines[1].expectedQty')" = "50" ] || fail "line2 expectedQty"
[ "$(echo "$WASN" | jq -r .supplierCode)" = "SUP01" ] || fail "supplierCode"
[ "$(echo "$WASN" | jq -r .warehouseCode)" = "WH01" ] || fail "warehouseCode"
echo "wms asn verified: $(echo "$WASN" | jq -c '{code,status,warehouseCode,supplierCode,externalNo}')"

echo "== 4. WMS: 收货 (SKU001 x100, SKU003 x50)"
WL1=$(echo "$WASN" | jq '.lines[0].id'); WL2=$(echo "$WASN" | jq '.lines[1].id')
R=$(wcall POST "/inbound/asn/$WMS_ASN_ID/receive" "[{\"lineId\":$WL1,\"qty\":100,\"lotNo\":\"LOT-HTTP-A\"},{\"lineId\":$WL2,\"qty\":50,\"lotNo\":\"LOT-HTTP-B\"}]")
WST=$(echo "$R" | jq -r .status)
# WMS 收齐后可能直接 RECEIVED 或 PUTAWAY(已生成上架任务)，两者都视为收货完成；RECEIVING 才需关闭收货
if [ "$WST" = "RECEIVING" ]; then
  R=$(wcall POST "/inbound/asn/$WMS_ASN_ID/close-receiving"); WST=$(echo "$R" | jq -r .status)
fi
echo "$WST" | grep -qE '^(RECEIVED|PUTAWAY|CLOSED)$' || fail "wms asn not finished: $WST"
echo "wms asn status=$WST"
# WMS 无 QC 的物料不产生拒收（SKU003 qc_required=false），本场景 receivedQty=150 rejectedQty=0

echo "== 5. 等待 SRM 轮询回传（最多 60s）"
SRM_TOKEN=$SUP_TOKEN
ST=""
for i in $(seq 1 30); do
  ST=$(scall GET "/delivery/asn/$ASN_ID" | jq -r .status)
  echo "  poll#$i srm asn status=$ST"
  [ "$ST" = "POSTED" ] && break
  case "$ST" in SYNCED|RECEIVING|RECEIVED) ;; *) fail "unexpected status $ST";; esac
  sleep 2
done
[ "$ST" = "POSTED" ] || fail "srm asn did not reach POSTED, last=$ST"

echo "== 6. 断言 GR / PO / 考核 / 集成日志"
GR=$(scall GET "/receipt/page?size=10" | jq -c "[.records[] | select(.asnId==$ASN_ID)][0]")
[ -n "$GR" ] && [ "$GR" != "null" ] || fail "no GR for asn $ASN_ID"
[ "$(echo "$GR" | jq -r .status)" = "POSTED" ] || fail "gr status: $GR"
[ -n "$(echo "$GR" | jq -r .sapMaterialDoc)" ] && [ "$(echo "$GR" | jq -r .sapMaterialDoc)" != "null" ] || fail "sapMaterialDoc empty"
echo "gr=$(echo "$GR" | jq -r .code) sapMaterialDoc=$(echo "$GR" | jq -r .sapMaterialDoc)"

PO=$(scall GET "/purchase/order/$PO_ID")
[ "$(echo "$PO" | jq -r .status)" = "RECEIVED" ] || fail "po status: $(echo "$PO" | jq -r .status)"
TOT=$(echo "$PO" | jq '[.lines[].receivedQty] | add')
[ "$TOT" = "150" ] || fail "po receivedQty total=$TOT expect 150"
REJ=$(echo "$PO" | jq '[.lines[].rejectedQty // 0] | add')
echo "po status=RECEIVED receivedQty=$TOT rejectedQty=$REJ"

EV=$(scall GET "/evaluation/records?size=10" | jq -c ".records[] | select(.asnCode==\"$ASN_CODE\")")
[ -n "$EV" ] || fail "no evaluation record for $ASN_CODE"
echo "eval: onTime=$(echo "$EV" | jq .onTime) score=$(echo "$EV" | jq .score) period=$(echo "$EV" | jq -r .period)"

SRM_TOKEN=$(http POST "$SRM/auth/login" "" '{"username":"buyer","password":"buyer123"}' | jq -r '.data.token')
http GET "$SRM/integration/logs/page?size=50&system=WMS" "$SRM_TOKEN" | jq -c '.data.records' > /tmp/wms_logs.json
jq -e '[.[] | select(.action=="WMS_CREATE_ASN" and .status=="SUCCESS")] | length >= 1' /tmp/wms_logs.json >/dev/null || fail "no WMS_CREATE_ASN SUCCESS log"
jq -r '.[] | "\(.direction) \(.action) \(.status) \(.bizCode)"' /tmp/wms_logs.json | head -6

echo "== WMS-LINK ALL GREEN =="
