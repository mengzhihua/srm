-- ===================== 基础数据 =====================
CREATE TABLE IF NOT EXISTS srm_supplier (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(128) NOT NULL,
  short_name VARCHAR(64),
  tax_no VARCHAR(64),
  contact VARCHAR(64),
  phone VARCHAR(32),
  email VARCHAR(128),
  address VARCHAR(255),
  bank_name VARCHAR(128),
  bank_account VARCHAR(64),
  category VARCHAR(64),
  payment_terms VARCHAR(64),
  currency VARCHAR(8) DEFAULT 'CNY',
  sap_vendor_code VARCHAR(32),
  wms_supplier_code VARCHAR(32),
  grade VARCHAR(4),
  score DECIMAL(6,2),
  status INT DEFAULT 1,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT uk_srm_supplier_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS srm_material (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(255) NOT NULL,
  spec VARCHAR(128),
  unit VARCHAR(16) DEFAULT 'EA',
  category VARCHAR(64),
  sap_material_code VARCHAR(64),
  wms_item_code VARCHAR(64),
  tax_rate DECIMAL(6,4),
  status INT DEFAULT 1,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT uk_srm_material_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS srm_plant (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(128) NOT NULL,
  sap_plant_code VARCHAR(16),
  sap_storage_location VARCHAR(16),
  wms_warehouse_code VARCHAR(32),
  wms_owner_code VARCHAR(32),
  address VARCHAR(255),
  status INT DEFAULT 1,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT uk_srm_plant_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS srm_price_list (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  supplier_code VARCHAR(32) NOT NULL,
  material_code VARCHAR(64) NOT NULL,
  price DECIMAL(18,4) NOT NULL,
  currency VARCHAR(8) DEFAULT 'CNY',
  min_qty DECIMAL(18,3),
  valid_from DATE,
  valid_to DATE,
  contract_no VARCHAR(64),
  status INT DEFAULT 1,
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);
CREATE INDEX idx_price_sm ON srm_price_list (supplier_code, material_code);

-- ===================== 寻源 =====================
CREATE TABLE IF NOT EXISTS srm_purchase_requisition (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(32) NOT NULL,
  plant_code VARCHAR(32),
  requester VARCHAR(64),
  department VARCHAR(64),
  status VARCHAR(16) NOT NULL,
  remark VARCHAR(255),
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT uk_srm_pr_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS srm_pr_line (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  pr_id BIGINT NOT NULL,
  line_no INT,
  material_code VARCHAR(64) NOT NULL,
  qty DECIMAL(18,3) NOT NULL,
  required_date DATE,
  ordered_qty DECIMAL(18,3) DEFAULT 0,
  remark VARCHAR(255),
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);
CREATE INDEX idx_pr_line ON srm_pr_line (pr_id);

CREATE TABLE IF NOT EXISTS srm_rfq (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(32) NOT NULL,
  title VARCHAR(128),
  plant_code VARCHAR(32),
  status VARCHAR(16) NOT NULL,
  deadline TIMESTAMP,
  supplier_codes VARCHAR(512),
  remark VARCHAR(255),
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT uk_srm_rfq_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS srm_rfq_line (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  rfq_id BIGINT NOT NULL,
  line_no INT,
  material_code VARCHAR(64) NOT NULL,
  qty DECIMAL(18,3) NOT NULL,
  required_date DATE,
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);
CREATE INDEX idx_rfq_line ON srm_rfq_line (rfq_id);

CREATE TABLE IF NOT EXISTS srm_rfq_quote (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  rfq_id BIGINT NOT NULL,
  supplier_code VARCHAR(32) NOT NULL,
  status VARCHAR(16) NOT NULL,
  total_amount DECIMAL(18,2),
  remark VARCHAR(255),
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);
CREATE INDEX idx_rfq_quote ON srm_rfq_quote (rfq_id, supplier_code);

CREATE TABLE IF NOT EXISTS srm_rfq_quote_line (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  quote_id BIGINT NOT NULL,
  rfq_line_id BIGINT,
  material_code VARCHAR(64),
  price DECIMAL(18,4),
  lead_time_days INT,
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);
CREATE INDEX idx_rfq_quote_line ON srm_rfq_quote_line (quote_id);

-- ===================== 采购订单 =====================
CREATE TABLE IF NOT EXISTS srm_purchase_order (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(32) NOT NULL,
  supplier_code VARCHAR(32) NOT NULL,
  plant_code VARCHAR(32),
  currency VARCHAR(8) DEFAULT 'CNY',
  total_amount DECIMAL(18,2),
  status VARCHAR(24) NOT NULL,
  sap_po_no VARCHAR(32),
  sap_sent_at TIMESTAMP,
  confirmed_at TIMESTAMP,
  expected_date DATE,
  source_type VARCHAR(16),
  source_code VARCHAR(64),
  remark VARCHAR(255),
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT uk_srm_po_code UNIQUE (code)
);
CREATE INDEX idx_po_supplier ON srm_purchase_order (supplier_code, status);

CREATE TABLE IF NOT EXISTS srm_po_line (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  po_id BIGINT NOT NULL,
  line_no INT,
  material_code VARCHAR(64) NOT NULL,
  qty DECIMAL(18,3) NOT NULL,
  price DECIMAL(18,4),
  amount DECIMAL(18,2),
  delivery_date DATE,
  shipped_qty DECIMAL(18,3) DEFAULT 0,
  received_qty DECIMAL(18,3) DEFAULT 0,
  rejected_qty DECIMAL(18,3) DEFAULT 0,
  invoiced_qty DECIMAL(18,3) DEFAULT 0,
  sap_item_no VARCHAR(16),
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);
CREATE INDEX idx_po_line ON srm_po_line (po_id);

-- ===================== 发货通知 ASN =====================
CREATE TABLE IF NOT EXISTS srm_asn (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(32) NOT NULL,
  po_id BIGINT NOT NULL,
  po_code VARCHAR(32),
  supplier_code VARCHAR(32) NOT NULL,
  plant_code VARCHAR(32),
  status VARCHAR(16) NOT NULL,
  expected_date DATE,
  carrier VARCHAR(64),
  tracking_no VARCHAR(64),
  wms_asn_id BIGINT,
  wms_asn_code VARCHAR(32),
  synced_at TIMESTAMP,
  received_at TIMESTAMP,
  remark VARCHAR(255),
  total_qty DECIMAL(18,3),
  received_qty DECIMAL(18,3),
  rejected_qty DECIMAL(18,3),
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT uk_srm_asn_code UNIQUE (code)
);
CREATE INDEX idx_srm_asn ON srm_asn (po_id, status, supplier_code);

CREATE TABLE IF NOT EXISTS srm_asn_line (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  asn_id BIGINT NOT NULL,
  line_no INT,
  po_line_id BIGINT NOT NULL,
  material_code VARCHAR(64) NOT NULL,
  qty DECIMAL(18,3) NOT NULL,
  lot_no VARCHAR(64),
  expiry_date DATE,
  received_qty DECIMAL(18,3) DEFAULT 0,
  rejected_qty DECIMAL(18,3) DEFAULT 0,
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);
CREATE INDEX idx_srm_asn_line ON srm_asn_line (asn_id);

-- ===================== 收货 =====================
CREATE TABLE IF NOT EXISTS srm_goods_receipt (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(32) NOT NULL,
  asn_id BIGINT NOT NULL,
  asn_code VARCHAR(32),
  po_id BIGINT NOT NULL,
  po_code VARCHAR(32),
  supplier_code VARCHAR(32),
  plant_code VARCHAR(32),
  status VARCHAR(16) NOT NULL,
  received_at TIMESTAMP,
  sap_material_doc VARCHAR(32),
  sap_posted_at TIMESTAMP,
  total_received_qty DECIMAL(18,3),
  total_rejected_qty DECIMAL(18,3),
  source VARCHAR(16),
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT uk_srm_gr_code UNIQUE (code)
);
CREATE INDEX idx_gr ON srm_goods_receipt (asn_id, po_id);

CREATE TABLE IF NOT EXISTS srm_gr_line (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  gr_id BIGINT NOT NULL,
  asn_line_id BIGINT,
  po_line_id BIGINT,
  material_code VARCHAR(64),
  received_qty DECIMAL(18,3),
  rejected_qty DECIMAL(18,3),
  accepted_qty DECIMAL(18,3),
  lot_no VARCHAR(64),
  amount DECIMAL(18,2),
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);
CREATE INDEX idx_gr_line ON srm_gr_line (gr_id);

-- ===================== 供应商考核 =====================
CREATE TABLE IF NOT EXISTS srm_eval_record (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  supplier_code VARCHAR(32) NOT NULL,
  gr_code VARCHAR(32),
  asn_code VARCHAR(32),
  po_code VARCHAR(32),
  on_time BOOLEAN,
  qty_accuracy DECIMAL(6,2),
  quality_rate DECIMAL(6,2),
  lead_days INT,
  wms_remark VARCHAR(255),
  wms_score DECIMAL(6,2),
  score DECIMAL(6,2),
  period VARCHAR(8),
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);
CREATE INDEX idx_eval_record ON srm_eval_record (supplier_code, period);

CREATE TABLE IF NOT EXISTS srm_eval_summary (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  supplier_code VARCHAR(32) NOT NULL,
  period VARCHAR(8) NOT NULL,
  receipt_count INT,
  on_time_rate DECIMAL(6,2),
  qty_accuracy DECIMAL(6,2),
  quality_rate DECIMAL(6,2),
  avg_score DECIMAL(6,2),
  grade VARCHAR(4),
  sap_synced BOOLEAN DEFAULT FALSE,
  sap_synced_at TIMESTAMP,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT uk_eval_period UNIQUE (supplier_code, period)
);

-- ===================== 发票 =====================
CREATE TABLE IF NOT EXISTS srm_invoice (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(32) NOT NULL,
  supplier_code VARCHAR(32) NOT NULL,
  po_code VARCHAR(32),
  invoice_no VARCHAR(64),
  invoice_date DATE,
  amount DECIMAL(18,2),
  tax_amount DECIMAL(18,2),
  status VARCHAR(16) NOT NULL,
  sap_invoice_doc VARCHAR(32),
  remark VARCHAR(255),
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT uk_srm_invoice_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS srm_invoice_line (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  invoice_id BIGINT NOT NULL,
  gr_line_id BIGINT,
  po_line_id BIGINT,
  material_code VARCHAR(64),
  qty DECIMAL(18,3),
  price DECIMAL(18,4),
  amount DECIMAL(18,2),
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);
CREATE INDEX idx_inv_line ON srm_invoice_line (invoice_id);

-- ===================== 集成日志 =====================
CREATE TABLE IF NOT EXISTS srm_integration_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  direction VARCHAR(8) NOT NULL,
  system VARCHAR(8) NOT NULL,
  action VARCHAR(32) NOT NULL,
  biz_type VARCHAR(32),
  biz_id BIGINT,
  biz_code VARCHAR(64),
  request TEXT,
  response TEXT,
  status VARCHAR(16) NOT NULL,
  error_msg VARCHAR(512),
  duration_ms INT,
  retry_count INT DEFAULT 0,
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);
CREATE INDEX idx_int_log ON srm_integration_log (biz_type, biz_id);

-- ===================== 系统 / 公共 =====================
CREATE TABLE IF NOT EXISTS srm_user (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(64) NOT NULL,
  password VARCHAR(255) NOT NULL,
  real_name VARCHAR(64),
  role VARCHAR(16) NOT NULL,
  supplier_code VARCHAR(32),
  status INT DEFAULT 1,
  last_login_at TIMESTAMP,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT uk_srm_user_name UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS srm_sequence (
  prefix VARCHAR(16) NOT NULL,
  day_key VARCHAR(8) NOT NULL,
  seq_value INT NOT NULL,
  PRIMARY KEY (prefix, day_key)
);

CREATE TABLE IF NOT EXISTS srm_op_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(64),
  method VARCHAR(8),
  path VARCHAR(255),
  query VARCHAR(255),
  http_status INT,
  cost_ms INT,
  client_ip VARCHAR(64),
  created_at TIMESTAMP
);
CREATE INDEX idx_srm_op_log_created ON srm_op_log (created_at);
