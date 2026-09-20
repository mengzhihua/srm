---
name: testing-srm-browser
description: Run the SRM procurement lifecycle in the Chinese browser UI with mock SAP and WMS.
---

# Setup

- Follow the environment blueprint: backend `mvn spring-boot:run` on 8081, frontend `npm install` then `npm run dev` on 5174.
- Use default mock SAP/WMS for local lifecycle testing. Do not enable HTTP WMS unless real integration testing is requested.
- H2 persists in `backend/data`; retain existing records and use unique test identifiers. Do not reset the database merely to simplify testing.
- Demo credentials are displayed on the login screen: admin/admin123, buyer/buyer123, sup01/sup123. Supplier sup01 is bound to SUP01.
- Chrome may show a compromised-password warning for demo credentials; dismiss it before continuing.

# UI lifecycle

1. Buyer: 采购申请 → 新建申请 → select P001 and SKU001 → save → 提交 → 审批 → 转订单. Choose SUP01 and an explicit unit price.
2. 采购订单 → 审批 → 下发SAP. Capture SAP PO number and status 已下发SAP.
3. Supplier: 我的订单 → 确认订单 → 发货. Enter quantity and 批次. 我的发货 should show 已同步WMS.
4. Admin: 发货通知(ASN) → 手工收货. Actual receipt defaults to zero; enter the full quantity explicitly. Check ASN 已过账, matching GR 已过账 with SAP material document, and PO 已收货.
5. 供应商考核 has 月度汇总 and 单票明细 tabs; correlate the new GR and monthly score/grade.
6. Supplier: 我的发票 → 提交发票. The quantity input caps to available received quantity on blur. Submission creates 已提交, not automatic 对账相符.
7. Buyer: 发票对账 → 对账 → 审批 → 过账SAP are three separate actions. Verify 已过账 and SAP invoice document.

# Adversarial and evidence guidance

- Ensure a real SUP02 control PO exists before claiming supplier isolation. Verify admin/buyer can see it and sup01 cannot, including filtering by its exact code.
- Actual admin user route is `/system/user`; `/system/users` is unregistered and emits a Vue Router warning. Verify suppliers redirect away from both.
- Compare before/after receipt quantities and capture PO, ASN, GR and invoice identifiers so evidence is traceable.
- Over-quantity input capping proves UI prevention only, not backend tamper resistance or MISMATCH persistence.
- Admin 操作日志 may be below the fold in the independently scrollable sidebar.
- Record browser interactions, not dependency installation. Label menu-only navigation as regression, not full coverage of every module.

## Devin Secrets Needed

None for the seeded local demo with mock integrations. Real deployment credentials and real SAP/WMS secrets are outside this local flow.
