# SRM 供应商关系管理系统

基于 **Java 8 / Spring Boot 2.7 / MyBatis-Plus** 与 **Vue 3 / Element Plus** 的供应商关系管理系统，业务模型对标 **SAP Ariba**：
以 **采购申请 PR → 询价 RFQ → 采购订单 PO → 下发 SAP → 供应商确认 → 发货通知 ASN → 同步 WMS → 收货 → SAP 记账（GR/MIGO 101）→ 供应商考核 → 发票三单匹配（3-way match）→ 过账 SAP** 为主链路，
覆盖寻源协同、订单协同、发货协同、收货核算与供应商绩效。

与同机 [WMS 仓储管理系统](../wms) 通过 REST 集成：SRM 将供应商 ASN 下发为 WMS 入库单，WMS 收货完成后回传收货数量，SRM 据此生成收货单（GR）并模拟 SAP MIGO 101 过账。

## 技术栈

| 层 | 技术 |
| --- | --- |
| 后端 | Java 8（`maven.compiler 1.8`，JDK 17 可编译运行）、Spring Boot **2.7.18**、MyBatis-Plus **3.5.3.1**、Spring Validation、Lombok、Jackson |
| 前端 | Vue **3.5**、Vite **5.4**、Element Plus **2.9**、Vue Router **4.5**、Axios **1.7** |
| 数据库 | H2 文件库（`MODE=MySQL`，默认零配置）/ MySQL（`mysql` profile，`DB_*` 环境变量） |
| 构建 | Maven（后端，`mvn spring-boot:run`）、npm + Vite（前端，`npm run dev` / `npm run build`） |

## 系统架构

```mermaid
flowchart LR
    U[浏览器 :5174] -->|/api 代理| FE[Vue3 + Vite 前端 :5174]
    FE -->|REST + Bearer Token| BE[Spring Boot 后端 :8081]
    BE --> DB[(H2 文件库 ./data/srm<br/>或 MySQL)]
    BE -->|PO下发 / GR过账 / 发票过账 / 考核同步| SAP[SAP S/4 OData<br/>Mock 或 Http]
    BE -->|POST /api/inbound/asn 下发ASN<br/>GET /api/inbound/asn/{id} 轮询收货| WMS[WMS 后端 :8080]
    WMS -->|POST /api/integration/wms/receipt 收货回传<br/>POST /api/integration/wms/evaluation 考核推送| BE
```

- 前端 `vite.config.js` 将 `/api` 代理到 `http://localhost:8081`。
- `SapClient` / `WmsClient` 均有 `mock`（默认，不依赖外部）与 `http` 两种实现，由 `SRM_SAP_MODE` / `SRM_WMS_MODE` 切换。
- WMS → SRM 的回调走 `X-Api-Key` 认证（免登录），SRM → WMS 走登录 token（自动刷新）。

## 业务流程

```
采购申请 PR ──审批──▶ 转询价 RFQ ──发布──▶ 供应商报价 ──定标──▶ 自动生成 PO
        └── 转 PO ────────────────────────────────────────────▶
                              │
                              ▼
采购订单 PO ──approve──▶ sendToSap(SAP PO 号) ──▶ 供应商确认 CONFIRMED
                              │
                              ▼
供应商创建 ASN ──自动同步──▶ WMS 入库单（externalNo = SRM ASN 号）
                              │
          ┌───────────────────┼──────────────────────┐
          │ WMS 回调 /api/integration/wms/receipt    │ SRM 定时轮询 WMS ASN 状态
          ▼                                          ▼
              SRM 计算收货数 → 生成 GR → SapClient 过账（物料凭证 50xxxxxxxx）
                              │
                              ▼
        PO 收货数量回写 → 供应商考核记录（准时/数量/质量）→ 月度汇总评级回写供应商
                              │
                              ▼
供应商提交发票 → match（与 PO/GR 三单匹配）→ approve → postToSap（发票凭证 51xxxxxxxx）
```

## 核心单据状态机

状态值均取自 `service` 层代码：

| 单据 | 状态流转 |
| --- | --- |
| PR 采购申请 | `DRAFT → SUBMITTED → APPROVED → ORDERED`（审批驳回 `REJECTED`，任意环节可 `CANCELLED`；`to-po` / `to-rfq` 后置 `ORDERED`） |
| RFQ 询价 | `DRAFT → PUBLISHED → QUOTING（首家报价后）→ AWARDED`（`CANCELLED`，已定标不可取消；报价行 `SUBMITTED → AWARDED / LOST`） |
| PO 采购订单 | `DRAFT → APPROVED → SENT（sendToSap）→ CONFIRMED（供应商确认）→ PARTIALLY_RECEIVED → RECEIVED → CLOSED`（`CANCELLED`） |
| ASN 发货通知 | `CREATED → SYNCED（同步 WMS 成功）/ SYNC_FAILED（失败可 retry-sync）→ RECEIVING（部分收货）→ RECEIVED → POSTED（GR 已过账）`（`CANCELLED`） |
| GR 收货单 | `PENDING → POSTED（SAP 物料凭证过账）/ POST_FAILED（可 retry-post）` |
| Invoice 发票 | `SUBMITTED → MATCHED / MISMATCH → APPROVED → POSTED`（`REJECTED`） |

WMS 侧入库单完成态：`RECEIVED / PUTAWAY / CLOSED`（`RECEIVING` 需 `close-receiving`）。
轮询模式只信 WMS 完成状态——`done = complete || (delta && allReceived)`，部分收货只同步数量、不建 GR。

## 功能范围

| 模块 | 功能 |
| --- | --- |
| 基础数据 | 供应商（分类 / 税号 / 付款条件 / 银行 / SAP 供应商编码 / WMS 供应商映射 / 等级与得分由考核回写 / 黑名单）、物料（SAP/WMS 物料映射、税率）、工厂/收货地（SAP 工厂、库存地点、WMS 仓库/货主映射）、价格/合同价 |
| 寻源 | 采购申请 PR（提交 / 审批 / 驳回 / 取消 / 转 PO / 转询价）、询价 RFQ（发布、邀请供应商、供应商报价、报价对比、定标自动生 PO 并回写合同价） |
| 采购协同 | 采购订单（审批 / 下发 SAP / 供应商确认 / 取消 / 关闭，超交容差控制，来源 MANUAL/PR/RFQ 追溯） |
| 发货协同 | 供应商 ASN（行含批次 / 效期，自动或手工同步 WMS，失败 SYNC_FAILED 可重试 / 取消 / 拉取 WMS / 手工回传收货） |
| 收货记账 | 收货单 GR（按行收货 / 拒收 / 合格数 / 金额，SAP 物料凭证过账，POST_FAILED 可重试，幂等防重） |
| 供应商考核 | 每张 GR 一条考核明细（准时率、数量准确率、质量率、提前天数、WMS 评分），按供应商 + 月份汇总评级 A/B/C/D 并回写供应商档案，可同步 SAP |
| 发票对账 | 简版 Ariba 3-way match：供应商提交发票 → 数量（≤ 收货合格数 − 已开票）与单价（容差可配）匹配 → 审批 → 过账 SAP |
| 集成日志 | SAP / WMS 全部出入向调用留痕（请求 / 响应 / 耗时 / 状态），失败可一键重试 |
| 工作台 | 待审批、待下发、待确认、在途 ASN、待记账、失败数、本月采购金额、供应商等级分布、最近集成日志 |
| 系统管理 | 登录 / 修改密码，用户管理（角色 + 供应商绑定），操作日志审计 |

## 角色与演示账号

| 角色 | 账号 | 说明 |
| --- | --- | --- |
| ADMIN | `admin / admin123`（`SRM_ADMIN_PASSWORD`） | 不受限 |
| BUYER | `buyer / buyer123` | 采购员：主数据、PR/RFQ/PO/收货/发票/考核全部可写（不含 `/api/system/**` 与集成日志重试） |
| SUPPLIER | `sup01 / sup123`（绑定 SUP01） | 供应商门户：只看到自己的 RFQ 报价、PO 确认、ASN、收货、发票、考核 |
| VIEWER | — | 只读（前端隐藏写按钮，可改自己密码） |

演示主数据：供应商 SUP01/SUP02/SUP03（SUP01、SUP02 映射 WMS 同名供应商，SUP03 无 WMS 对应），物料 SKU001–SKU004（`wmsItemCode` 与 WMS 一致），工厂 P001 → WMS WH01/OWN01。

## 前端菜单与页面

菜单定义于 `frontend/src/router/index.js`，按角色过滤（SUPPLIER 仅见「供应商门户」组，`/system` 仅 ADMIN，VIEWER 无写按钮）：

| 菜单 | 路径 | 可见角色 |
| --- | --- | --- |
| 工作台 | `/dashboard` | ADMIN / BUYER / VIEWER |
| 基础数据 → 供应商 / 物料 / 工厂/收货地 / 价格/合同价 | `/basic/supplier` `/basic/material` `/basic/plant` `/basic/pricelist` | ADMIN / BUYER / VIEWER |
| 寻源管理 → 采购申请 / 询价单(RFQ) | `/sourcing/pr` `/sourcing/rfq` | ADMIN / BUYER / VIEWER |
| 采购执行 → 采购订单 / 发货通知(ASN) / 收货记账(GR) | `/purchase/order` `/purchase/asn` `/purchase/receipt` | ADMIN / BUYER / VIEWER |
| 对账考核 → 发票对账 / 供应商考核 | `/settle/invoice` `/settle/evaluation` | ADMIN / BUYER / VIEWER |
| 集成日志 | `/integration` | ADMIN / BUYER / VIEWER |
| 系统管理 → 用户管理 / 操作日志 | `/system/user` `/system/oplog` | ADMIN |
| 供应商门户 → 我的询价 / 我的订单 / 我的发货 / 我的收货 / 我的发票 / 我的考核 | `/portal/rfq` `/portal/order` `/portal/asn` `/portal/receipt` `/portal/invoice` `/portal/evaluation` | SUPPLIER |

路由守卫：未登录跳 `/login`；SUPPLIER 访问非 `/portal/**` 重定向 `/portal/order`，非供应商访问 `/portal/**` 重定向 `/dashboard`；登录成功后先拉取 `/auth/me` 再渲染菜单。

## API 概览

统一返回 `R{code,data,msg}`，除标注「免登录」外均需 `Authorization: Bearer <token>`。
通用 CRUD 基类提供 `GET /page` `GET /list` `GET /{id}` `POST` `PUT /{id}` `DELETE /{id}`（各模块按需要开启，写权限按 `AccessPolicy`：ADMIN 全通；GET 全登录用户可读；BUYER 可写业务接口；SUPPLIER 仅报价 / 订单确认 / 建 ASN / 提交发票，且按 `supplierCode` 过滤；VIEWER 只读）。

| 模块 | 方法与路径 | 说明 |
| --- | --- | --- |
| 认证 | `POST /api/auth/login`、`GET /api/auth/me`、`POST /api/auth/password`、`POST /api/auth/logout` | 登录 / 当前用户 / 改密 / 登出（全部角色） |
| 基础数据 | `/api/basic/supplier` `/api/basic/material` `/api/basic/plant` `/api/basic/pricelist`（通用 CRUD） | 主数据维护（ADMIN/BUYER 写） |
| 寻源 PR | `GET /api/sourcing/pr/page`、`GET /{id}`、`POST`、`POST /{id}/submit|approve|reject|cancel|to-rfq|to-po` | 申请流转（ADMIN/BUYER） |
| 寻源 RFQ | `GET /api/sourcing/rfq/page`、`GET /{id}`、`POST`、`POST /{id}/publish|cancel|quote|award` | 询价/报价/定标（报价 SUPPLIER 可写） |
| 采购订单 | `GET /api/purchase/order/page`、`GET /{id}`、`POST`、`PUT /{id}`、`POST /{id}/approve|send-to-sap|confirm|cancel|close` | 订单协同（confirm SUPPLIER 可写） |
| 发货 ASN | `GET /api/delivery/asn/page`、`GET /{id}`、`POST`、`POST /{id}/sync|retry-sync|cancel|pull-wms|manual-receipt` | 供应商发货（创建 SUPPLIER 可写；pull-wms/manual-receipt/retry 仅 ADMIN/BUYER） |
| 收货 GR | `GET /api/receipt/page`、`GET /{id}`、`POST /{id}/retry-post` | 收货单与重试过账（ADMIN/BUYER） |
| 考核 | `GET /api/evaluation`（汇总分页）、`GET /api/evaluation/records`、`POST /api/evaluation/{id}/sync-sap` | 月度汇总 / 明细 / 同步 SAP |
| 发票 | `GET /api/invoice/page`、`GET /{id}`、`POST`（提交，SUPPLIER 可写）、`POST /{id}/match|approve|reject|post-to-sap` | 3-way match（ADMIN/BUYER） |
| 集成日志 | `GET /api/integration/logs/page`、`POST /api/integration/logs/{id}/retry` | 查询与重试（ADMIN 写；BUYER 被排除） |
| 系统 | `/api/system/user`（通用 CRUD，ADMIN）、`GET /api/system/oplog/page` | 用户与操作日志 |
| 工作台 | `GET /api/dashboard` | 统计汇总 |

### 免登录集成回调（`X-Api-Key` 认证）

| 方法与路径 | 说明 |
| --- | --- |
| `POST /api/integration/wms/receipt` | WMS 收货回传（按 `wmsAsnCode` / `externalNo` 找 ASN，增量累计建 GR） |
| `POST /api/integration/wms/evaluation` | WMS 推送供应商考核分（写入明细 wmsScore/wmsRemark 并重算汇总） |

## 数据库表

`schema.sql`（H2/MySQL 兼容，表名前缀 `srm_`）：

| 表 | 用途 |
| --- | --- |
| `srm_supplier` / `srm_material` / `srm_plant` / `srm_price_list` | 供应商、物料、工厂/收货地、价格/合同价 |
| `srm_purchase_requisition` / `srm_pr_line` | 采购申请头/行 |
| `srm_rfq` / `srm_rfq_line` / `srm_rfq_quote` / `srm_rfq_quote_line` | 询价头/行、供应商报价头/行 |
| `srm_purchase_order` / `srm_po_line` | 采购订单头/行（含 sapPoNo、发货/收货/开票累计） |
| `srm_asn` / `srm_asn_line` | 发货通知头/行（含 wmsAsnId/wmsAsnCode） |
| `srm_goods_receipt` / `srm_gr_line` | 收货单头/行（含 sapMaterialDoc） |
| `srm_eval_record` / `srm_eval_summary` | 考核明细（每 GR 一条）/ 按供应商+月份汇总 |
| `srm_invoice` / `srm_invoice_line` | 发票头/行（3-way match） |
| `srm_integration_log` | SAP/WMS 出入向调用日志 |
| `srm_user` | 用户（含 role、supplierCode） |
| `srm_sequence` | 单号序列（CodeGenerator） |
| `srm_op_log` | 操作日志审计 |

## 目录结构

```
backend/   Spring Boot 后端 (端口 8081)
  src/main/java/com/srm
    common/       统一响应 R、异常处理、BaseEntity、通用 CRUD、单号生成
    system/       登录 Token、AuthInterceptor、AccessPolicy 角色控制、操作日志
    basic/        供应商 / 物料 / 工厂 / 价格
    sourcing/     采购申请 PR、询价 RFQ、报价
    purchase/     采购订单
    delivery/     供应商发货 ASN
    receipt/      收货单 GR（WMS 回调 / 轮询 / 手工三种入口）
    evaluation/   供应商考核
    invoice/      发票对账
    integration/  SapClient（Mock/Http）、WmsClient（Mock/Http）、集成日志、对外回调
    dashboard/    工作台统计
  src/main/resources
    schema.sql    表结构 (H2 / MySQL 兼容)
    data.sql      演示主数据
frontend/  Vue 3 + Vite + Element Plus 前端 (端口 5174，/api 代理到 8081)
scripts/smoke.sh      mock 模式全链路冒烟
scripts/smoke-wms.sh  与真实 WMS 联调冒烟
```

## 快速开始

环境：JDK 8+、Maven 3.6+、Node 18+。

```bash
# 后端（默认 H2 文件库 ./data/srm，零配置）
cd backend
mvn spring-boot:run          # http://localhost:8081

# 前端
cd frontend
npm install
npm run dev                  # http://localhost:5174
```

H2 控制台排障：`SRM_H2_CONSOLE=true` 开启 <http://localhost:8081/h2>（JDBC `jdbc:h2:file:./data/srm`，用户 `sa`）。

### 三分钟演示路径（mock 模式）

1. `buyer / buyer123` 登录 → 「采购执行 → 采购订单」新建订单（供应商 SUP01、工厂 P001、行 SKU001×100）→ 详情点 **审批 → 下发 SAP**（得到 `sapPoNo`）。
2. 退出后用 `sup01 / sup123` 登录 → 「供应商门户 → 我的订单」点 **确认** → **发起发货** 建 ASN（mock 模式自动同步成功，得 `wmsAsnCode`）。
3. 用 `admin / admin123` 登录 → 「采购执行 → 发货通知(ASN)」对该 ASN 点 **手工回传收货**（填收货数）→ 自动生成 GR 并模拟 SAP 过账。
4. 看「收货记账(GR)」（物料凭证号）、「对账考核 → 供应商考核」（新明细 + 评级）、「供应商门户 → 我的发票」提交发票后由 buyer **match / approve / post-to-sap**。
5. 「工作台」查看待办与统计，「集成日志」查看全部 SAP/WMS 调用留痕。

### 使用 MySQL

```bash
DB_HOST=127.0.0.1 DB_PORT=3306 DB_NAME=srm DB_USER=root DB_PASSWORD=xxx \
  mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

`DB_USER` / `DB_PASSWORD` 必填；两种数据库启动时都会自动执行幂等的 `schema.sql` / `data.sql`，MySQL 建议库字符集 utf8mb4。

## 配置项（环境变量）

| 变量 | 说明 | 默认 |
| --- | --- | --- |
| `SRM_AUTH_SECRET` | 登录令牌签名密钥，生产必须设置；未设置时随机生成 | 随机 |
| `SRM_TOKEN_TTL` | 令牌有效期 | `12h` |
| `SRM_ADMIN_PASSWORD` | 首次启动创建 admin 的初始密码 | `admin123` |
| `SRM_CORS_ORIGINS` | 允许跨域来源，逗号分隔 | `http://localhost:5174` |
| `SRM_H2_CONSOLE` | H2 控制台开关 | `false` |
| `SRM_SAP_MODE` | `mock` / `http` | `mock` |
| `SRM_SAP_BASE_URL` / `SRM_SAP_USERNAME` / `SRM_SAP_PASSWORD` | http 模式下 S/4 OData 地址与 Basic 认证 | — |
| `SRM_SAP_MOCK_FAIL_RATE` | mock 模式随机失败率（0~1） | `0` |
| `SRM_WMS_MODE` | `mock` / `http` | `mock` |
| `SRM_WMS_BASE_URL` | WMS 后端地址 | `http://localhost:8080` |
| `SRM_WMS_USERNAME` / `SRM_WMS_PASSWORD` | WMS 登录账号（自动取 token，401 刷新） | `admin/admin123` |
| `SRM_WMS_POLL_INTERVAL` | 轮询 WMS ASN 间隔（ms，仅 http 模式生效） | `30000` |
| `SRM_INTEGRATION_API_KEY` | WMS 回调接口的 `X-Api-Key` | `srm-wms-key` |
| `SRM_OVER_DELIVERY_TOLERANCE` | PO 行超交容差百分比 | `0` |
| `SRM_DELIVERY_AUTO_SYNC` | ASN 创建后自动同步 WMS（失败置 SYNC_FAILED 不影响创建） | `true` |
| `SRM_EVAL_WMS_WEIGHT` | 考核中 WMS 推送评分的权重 | `0.3` |
| `SRM_INVOICE_PRICE_TOLERANCE` | 发票单价容差 | `0.01` |

## 与 WMS 联调

```bash
# 1. 启动 WMS（../wms/backend，端口 8080）
cd ../wms/backend && mvn spring-boot:run

# 2. 重启 SRM 为 http 模式（新建库建议先 rm -rf backend/data）
cd backend && rm -rf data
SRM_WMS_MODE=http SRM_WMS_BASE_URL=http://localhost:8080 SRM_WMS_POLL_INTERVAL=5000 \
  mvn spring-boot:run

# 3. 跑联调冒烟
../scripts/smoke-wms.sh
```

`smoke-wms.sh` 全链路：建 PO → 审批 → 下发 SAP → 供应商确认 → 建 ASN 自动同步 WMS →
在 WMS 校验入库单（externalNo / 行物料 / 数量）→ WMS 收货 → 等 SRM 轮询回传 →
断言 GR 已过账（物料凭证）、PO 收货数 =150 且状态 RECEIVED、考核记录生成、集成日志含 `WMS_CREATE_ASN SUCCESS`。

WMS 侧字段约定（已验证）：入库单行 `itemCode / lotNo / expectedQty / receivedQty / rejectedQty`；
收货完成状态为 `RECEIVED / PUTAWAY / CLOSED`（`RECEIVING` 需 `close-receiving`）。
轮询只信 WMS 完成状态；部分收货只同步数量、不建 GR。

## 集成接口说明

### SRM 对外（免登录，`X-Api-Key: srm-wms-key`）

`POST /api/integration/wms/receipt` —— WMS 收货回调（按增量累计，全部行有收货即建 GR）：

```json
{
  "wmsAsnCode": "ASN20260907-0004",
  "externalNo": "ASN20260907-0003",
  "receivedAt": "2026-09-08T10:00:00",
  "lines": [{"itemCode": "SKU001", "lotNo": "LOT-HTTP-A", "receivedQty": 100, "rejectedQty": 0}]
}
```

`POST /api/integration/wms/evaluation` —— WMS 推送供应商考核：

```json
{"supplierCode": "SUP01", "externalNo": "ASN20260907-0003", "score": 92, "remark": "收货规范",
 "items": [{"key": "packaging", "value": "good"}]}
```

### SRM 调用外部（可扩展）

- `SapClient`：`MockSapClient`（默认）生成模拟凭证号（PO `45xxxxxxxx`、物料凭证 `50xxxxxxxx`、发票 `51xxxxxxxx`）；`HttpSapClient`（`SRM_SAP_MODE=http`）
  走 RestTemplate + Basic Auth，映射 S/4 OData 风格路径
  `/API_PURCHASEORDER_PROCESS_SRV/A_PurchaseOrder`（PO）、
  `/API_MATERIAL_DOCUMENT_SRV/A_MaterialDocumentHeader`（GR/MIGO 101）、
  `/API_SUPPLIERINVOICE_PROCESS_SRV/A_SupplierInvoice`（发票）、
  供应商考核同步另有 `/syncVendorEvaluation` 扩展点。
- `WmsClient`：`MockWmsClient` 内存生成 ASN 号；`HttpWmsClient` 自动登录
  `POST /api/auth/login` 缓存 token（401 刷新），调用 `POST /api/inbound/asn` 建单、
  `GET /api/inbound/asn/{id}` 轮询。请求体按 WMS 契约映射
  `{warehouseCode, ownerCode, supplierCode, type:PURCHASE, externalNo, expectedDate, lines:[{itemCode, expectedQty, lotNo, expiryDate}]}`。
- 所有外部调用都写 `srm_integration_log`（direction/system/action/request/response/durationMs/status），
  失败可在「集成日志」页一键重试（SAP_CREATE_PO / SAP_POST_GR / SAP_POST_INVOICE / SAP_SYNC_EVAL / WMS_CREATE_ASN）。

## 冒烟测试

```bash
scripts/smoke.sh        # mock 模式全链路（PR→PO→SAP→确认→ASN→回调收货→GR→考核→发票→RFQ 定标→权限隔离）
scripts/smoke-wms.sh    # 需 WMS 8080 + SRM http 模式，真实联调
```

任一断言失败即 `exit 1`。

## 考核评分公式

每张 GR 生成一条考核明细：

- 准时：`receivedAt <= asn.expectedDate`（或 PO 行交期），权重 40
- 数量准确率：`acceptedQty / shippedQty`（accepted = received − rejected），权重 30
- 质量率：`1 − rejected / received`，权重 30
- `score = onTime*40 + qtyAccuracy*30 + qualityRate*30`；
  WMS 推送 `wmsScore` 存在时按 `SRM_EVAL_WMS_WEIGHT`（默认 0.3）与内部得分加权。

按供应商 + `yyyy-MM` 汇总为月度考核：准时率 / 数量准确率 / 质量率 / 均分，
`>=90 A`、`>=80 B`、`>=70 C`、否则 `D`，并回写供应商档案的 grade/score。

## 编码与中文显示

- 全项目 UTF-8：`frontend/index.html` 声明 `<meta charset="UTF-8">`，后端 `project.build.sourceEncoding=UTF-8`。
- 前端字体栈 `-apple-system, 'PingFang SC', 'Microsoft YaHei', sans-serif`（`style.css`），覆盖 macOS / Windows 中文。
- H2 以 `MODE=MySQL` 运行，时态/字符串行为贴近 MySQL；MySQL 部署时建议库字符集 `utf8mb4` 以完整支持中文。

## 常见问题（FAQ）

- **改 schema 后启动报字段/表错**：H2 文件库不会自动迁移，`rm -rf backend/data` 后重启重建（演示数据随 `data.sql` 重建）。
- **WMS 联调时 ASN 同步失败**：供应商必须有 `wmsSupplierCode` 且 WMS 中存在同名供应商、物料 `wmsItemCode` 与工厂 `wmsWarehouseCode/wmsOwnerCode` 必须在 WMS 主数据中存在；演示数据里 **SUP03 无 WMS 映射，http 模式会 SYNC_FAILED**，请用 SUP01/SUP02。
- **收货一直不同步**：`WmsSyncJob` 仅在 `SRM_WMS_MODE=http` 时启用，间隔由 `SRM_WMS_POLL_INTERVAL` 控制；mock 模式请用回调或 ASN 的「手工回传收货」。
- **重启后所有人被踢出登录**：未设置 `SRM_AUTH_SECRET` 时密钥随机生成，重启即失效；生产务必配置固定密钥。
- **供应商看不到数据 / 越权**：SUPPLIER 仅可访问「供应商门户」页面，接口层按 `supplierCode` 过滤；确认用户记录的 `supplierCode` 与供应商 `code` 一致。
- **收货后不产生 GR**：轮询模式只在 WMS 单据到 `RECEIVED/PUTAWAY/CLOSED` 才生成 GR；`RECEIVING`（部分收货）只回写数量。WMS 短收需先 `close-receiving`。
