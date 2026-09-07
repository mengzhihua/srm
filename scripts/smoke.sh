#!/usr/bin/env bash
# SRM 全链路冒烟（mock 模式）：
# 登录 -> PR -> 审批 -> 转PO -> 审批 -> 下发SAP -> 供应商确认 -> ASN -> 同步WMS
# -> WMS收货回调 -> GR过账SAP -> WMS考核推送 -> 汇总/回写 -> 发票对账/过账 -> dashboard
# -> 供应商数据隔离 -> RFQ 询价/报价/定标
# Usage: scripts/smoke.sh [base_url]   (default http://localhost:8081)
set -euo pipefail
BASE="${1:-http://localhost:8081}/api"
J='Content-Type: application/json'
KEY='X-Api-Key: srm-wms-key'

need() { command -v "$1" >/dev/null || { echo "missing $1"; exit 1; }; }
need curl; need jq

fail() { echo "FAIL: $*"; exit 1; }

TOKEN=""
call() { # method path [json]
  local out
  out=$(curl -sf -X "$1" "$BASE$2" -H "$J" -H "Authorization: Bearer ${TOKEN}" ${3:+-d "$3"})
  [ "$(echo "$out" | jq -r .code)" = "0" ] || fail "$1 $2 -> $out"
  echo "$out" | jq -c .data
}
api() { # api-key 调用: method path [json]
  local out
  out=$(curl -sf -X "$1" "$BASE$2" -H "$J" -H "$KEY" ${3:+-d "$3"})
  [ "$(echo "$out" | jq -r .code)" = "0" ] || fail "$1 $2 -> $out"
  echo "$out" | jq -c .data
}

echo "== 0. login"
curl -s -o /dev/null -w "%{http_code}" "$BASE/purchase/order/page" | grep -q 401 || fail "unauthenticated request not rejected"
TOKEN=$(call POST /auth/login '{"username":"buyer","password":"buyer123"}' | jq -r .token)
echo "login ok as $(call GET /auth/me | jq -r .username)"
SUP_TOKEN=$(curl -sf -X POST "$BASE/auth/login" -H "$J" -d '{"username":"sup01","password":"sup123"}' | jq -r '.data.token')
[ -n "$SUP_TOKEN" ] || fail "sup01 login"

echo "== 1. PR -> approve -> to PO"
PR=$(call POST /sourcing/pr '{"plantCode":"P001","requester":"buyer","department":"采购部",
  "lines":[{"materialCode":"SKU001","qty":100,"requiredDate":"2026-10-01"},{"materialCode":"SKU003","qty":50,"requiredDate":"2026-10-01"}]}')
PR_ID=$(echo "$PR" | jq .id)
call POST "/sourcing/pr/$PR_ID/submit" >/dev/null
call POST "/sourcing/pr/$PR_ID/approve" >/dev/null
PO=$(call POST "/sourcing/pr/$PR_ID/to-po" '{"supplierCode":"SUP01",
  "lines":[{"materialCode":"SKU001","qty":100,"price":45},{"materialCode":"SKU003","qty":50,"price":2.5}]}')
PO_ID=$(echo "$PO" | jq .id); PO_CODE=$(echo "$PO" | jq -r .code)
echo "po=$PO_CODE status=$(echo "$PO" | jq -r .status) total=$(echo "$PO" | jq -r .totalAmount)"

echo "== 2. approve + send to SAP"
call POST "/purchase/order/$PO_ID/approve" >/dev/null
PO=$(call POST "/purchase/order/$PO_ID/send-to-sap")
SAP_PO=$(echo "$PO" | jq -r .sapPoNo)
[ -n "$SAP_PO" ] && [ "$SAP_PO" != "null" ] || fail "sapPoNo empty"
[ "$(echo "$PO" | jq -r .status)" = "SENT" ] || fail "po status not SENT"
echo "sapPoNo=$SAP_PO status=SENT"

echo "== 3. supplier confirm + create ASN"
TOKEN=$SUP_TOKEN
PO=$(call POST "/purchase/order/$PO_ID/confirm")
[ "$(echo "$PO" | jq -r .status)" = "CONFIRMED" ] || fail "confirm failed"
L1=$(echo "$PO" | jq '.lines[0].id'); L2=$(echo "$PO" | jq '.lines[1].id')
ASN=$(call POST /delivery/asn "{\"poId\":$PO_ID,\"expectedDate\":\"2026-09-10\",\"carrier\":\"顺丰\",\"trackingNo\":\"SF001\",
  \"lines\":[{\"poLineId\":$L1,\"qty\":100,\"lotNo\":\"LOT-A\"},{\"poLineId\":$L2,\"qty\":50,\"lotNo\":\"LOT-B\"}]}")
ASN_ID=$(echo "$ASN" | jq .id); ASN_CODE=$(echo "$ASN" | jq -r .code)
[ "$(echo "$ASN" | jq -r .status)" = "SYNCED" ] || fail "asn not SYNCED: $ASN"
[ -n "$(echo "$ASN" | jq -r .wmsAsnCode)" ] && [ "$(echo "$ASN" | jq -r .wmsAsnCode)" != "null" ] || fail "wmsAsnCode empty"
echo "asn=$ASN_CODE wmsAsnCode=$(echo "$ASN" | jq -r .wmsAsnCode) status=SYNCED"

echo "== 4. WMS 收货回调: SKU001 全收100, SKU003 收55拒5"
GR=$(api POST /integration/wms/receipt "{\"wmsAsnCode\":\"$(echo "$ASN" | jq -r .wmsAsnCode)\",\"externalNo\":\"$ASN_CODE\",
  \"receivedAt\":\"2026-09-08T10:00:00\",
  \"lines\":[{\"itemCode\":\"SKU001\",\"lotNo\":\"LOT-A\",\"receivedQty\":100,\"rejectedQty\":0},
            {\"itemCode\":\"SKU003\",\"lotNo\":\"LOT-B\",\"receivedQty\":50,\"rejectedQty\":5,\"rejectReason\":\"破损\"}]}")
[ "$(echo "$GR" | jq -r .status)" = "POSTED" ] || fail "gr not POSTED: $GR"
[ -n "$(echo "$GR" | jq -r .sapMaterialDoc)" ] && [ "$(echo "$GR" | jq -r .sapMaterialDoc)" != "null" ] || fail "sapMaterialDoc empty"
echo "gr=$(echo "$GR" | jq -r .code) sapMaterialDoc=$(echo "$GR" | jq -r .sapMaterialDoc)"

ASN2=$(call GET "/delivery/asn/$ASN_ID")
[ "$(echo "$ASN2" | jq -r .status)" = "POSTED" ] || fail "asn status $(echo "$ASN2" | jq -r .status)"
PO=$(call GET "/purchase/order/$PO_ID")
[ "$(echo "$PO" | jq -r .status)" = "RECEIVED" ] || fail "po status $(echo "$PO" | jq -r .status)"
[ "$(echo "$PO" | jq '.lines[0].receivedQty')" = "100" ] || fail "po line1 receivedQty"
[ "$(echo "$PO" | jq '.lines[1].rejectedQty')" = "5" ] || fail "po line2 rejectedQty"
[ "$(echo "$PO" | jq '.lines[1].receivedQty')" = "50" ] || fail "po line2 receivedQty"
echo "po status=RECEIVED received=100/50 rejected=5"

echo "== 5. WMS 考核推送 + 汇总"
EVAL_REC=$(api POST /integration/wms/evaluation "{\"supplierCode\":\"SUP01\",\"asnCode\":\"$ASN_CODE\",\"score\":88,
  \"remark\":\"到货及时，包装完好\",\"items\":[{\"key\":\"packaging\",\"value\":\"good\"}]}")
[ -n "$(echo "$EVAL_REC" | jq -r .wmsScore)" ] || fail "wmsScore not recorded"
SUM=$(call GET "/evaluation/page?supplierCode=SUP01" | jq -c '.records[0]')
[ -n "$SUM" ] && [ "$SUM" != "null" ] || fail "no evaluation summary"
[ -n "$(echo "$SUM" | jq -r .grade)" ] && [ "$(echo "$SUM" | jq -r .grade)" != "null" ] || fail "grade empty"
echo "grade=$(echo "$SUM" | jq -r .grade) avgScore=$(echo "$SUM" | jq -r .avgScore)"
TOKEN=""
TOKEN=$(call POST /auth/login '{"username":"buyer","password":"buyer123"}' | jq -r .token)
SUP=$(call GET "/basic/supplier/list" | jq -c '.[] | select(.code=="SUP01")')
[ -n "$(echo "$SUP" | jq -r .grade)" ] && [ "$(echo "$SUP" | jq -r .grade)" != "null" ] || fail "supplier.grade not written back"
echo "supplier grade=$(echo "$SUP" | jq -r .grade) score=$(echo "$SUP" | jq -r .score)"

echo "== 6. 发票: 供应商提交 -> buyer match/approve/post-to-sap"
TOKEN=$SUP_TOKEN
GRL=$(call GET "/receipt/page" | jq -c '.records[0] | {id}')
GRD=$(call GET "/receipt/$(echo "$GRL" | jq .id)")
GL1=$(echo "$GRD" | jq '.lines[0].id'); GL2=$(echo "$GRD" | jq '.lines[1].id')
PL1=$(echo "$GRD" | jq '.lines[0].poLineId'); PL2=$(echo "$GRD" | jq '.lines[1].poLineId')
INV=$(call POST /invoice "{\"poCode\":\"$PO_CODE\",\"invoiceNo\":\"INV2026-001\",\"invoiceDate\":\"2026-09-08\",
  \"taxAmount\":585.25,
  \"lines\":[{\"grLineId\":$GL1,\"poLineId\":$PL1,\"materialCode\":\"SKU001\",\"qty\":100,\"price\":45},
            {\"grLineId\":$GL2,\"poLineId\":$PL2,\"materialCode\":\"SKU003\",\"qty\":45,\"price\":2.5}]}")
INV_ID=$(echo "$INV" | jq .id); echo "invoice=$(echo "$INV" | jq -r .code) status=$(echo "$INV" | jq -r .status)"
TOKEN=$(call POST /auth/login '{"username":"buyer","password":"buyer123"}' | jq -r .token)
INV=$(call POST "/invoice/$INV_ID/match"); [ "$(echo "$INV" | jq -r .status)" = "MATCHED" ] || fail "match: $INV"
call POST "/invoice/$INV_ID/approve" >/dev/null
INV=$(call POST "/invoice/$INV_ID/post-to-sap")
[ "$(echo "$INV" | jq -r .status)" = "POSTED" ] || fail "post-to-sap: $INV"
echo "invoice POSTED sapInvoiceDoc=$(echo "$INV" | jq -r .sapInvoiceDoc)"

echo "== 7. dashboard"
call GET /dashboard | jq -c '{prPending,poToSap,asnInTransit,grPendingPost,monthPurchaseAmount,supplierGrades}'

echo "== 8. 供应商数据隔离"
TOKEN=$SUP_TOKEN
POS=$(call GET "/purchase/order/page?size=50")
echo "$POS" | jq -e '[.records[].supplierCode] | all(. == "SUP01")' >/dev/null || fail "supplier sees other suppliers' POs"
call GET "/basic/supplier/list" >/dev/null
CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/sourcing/pr" -H "$J" -H "Authorization: Bearer $SUP_TOKEN" -d '{}')
[ "$CODE" = "403" ] || fail "supplier create PR expected 403 got $CODE"
echo "supplier isolation ok"

echo "== 9. RFQ 流程"
TOKEN=$(call POST /auth/login '{"username":"buyer","password":"buyer123"}' | jq -r .token)
RFQ=$(call POST /sourcing/rfq '{"title":"数据线询价","plantCode":"P001","supplierCodes":"SUP01,SUP02",
  "deadline":"2026-09-20T18:00:00",
  "lines":[{"materialCode":"SKU003","qty":200,"requiredDate":"2026-10-15"}]}')
RFQ_ID=$(echo "$RFQ" | jq .id)
call POST "/sourcing/rfq/$RFQ_ID/publish" >/dev/null
TOKEN=$SUP_TOKEN
Q=$(call POST "/sourcing/rfq/$RFQ_ID/quote" "{\"lines\":[{\"rfqLineId\":$(echo "$RFQ" | jq '.lines[0].id'),\"materialCode\":\"SKU003\",\"price\":2.2,\"leadTimeDays\":7}]}")
Q_ID=$(echo "$Q" | jq .id); echo "quote id=$Q_ID total=$(echo "$Q" | jq -r .totalAmount)"
TOKEN=$(call POST /auth/login '{"username":"buyer","password":"buyer123"}' | jq -r .token)
PO2=$(call POST "/sourcing/rfq/$RFQ_ID/award" "{\"quoteId\":$Q_ID,\"expectedDate\":\"2026-10-15\"}")
[ "$(echo "$PO2" | jq -r .supplierCode)" = "SUP01" ] || fail "award po: $PO2"
[ "$(echo "$PO2" | jq '.lines[0].price')" = "2.2" ] || fail "award price not from quote"
echo "award po=$(echo "$PO2" | jq -r .code) price=2.2 sourceType=$(echo "$PO2" | jq -r .sourceType)"

echo "== ALL GREEN =="
