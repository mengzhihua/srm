-- 演示数据（幂等：已存在则跳过）。wms_* 编码与 WMS 项目 data.sql 保持一致。
INSERT INTO srm_supplier (code, name, short_name, contact, phone, email, address, category, payment_terms, currency, sap_vendor_code, wms_supplier_code, status, created_at, updated_at)
SELECT 'SUP01', '深圳精密电子厂', '精密电子', '王强', '13700000001', 'sales@sup01.example', '深圳市宝安区', '原材料', '月结30天', 'CNY', '100010', 'SUP01', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM srm_supplier WHERE code='SUP01');
INSERT INTO srm_supplier (code, name, short_name, contact, phone, email, address, category, payment_terms, currency, sap_vendor_code, wms_supplier_code, status, created_at, updated_at)
SELECT 'SUP02', '东莞线材制造', '东莞线材', '赵敏', '13700000002', 'sales@sup02.example', '东莞市松山湖', '原材料', '月结60天', 'CNY', '100020', 'SUP02', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM srm_supplier WHERE code='SUP02');
INSERT INTO srm_supplier (code, name, short_name, contact, phone, email, address, category, payment_terms, currency, sap_vendor_code, wms_supplier_code, status, created_at, updated_at)
SELECT 'SUP03', '苏州包装制品', '苏州包装', '陈杰', '13700000003', 'sales@sup03.example', '苏州市工业园区', '包材', '货到付款', 'CNY', '100030', NULL, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM srm_supplier WHERE code='SUP03');
UPDATE srm_supplier SET wms_supplier_code = NULL WHERE code = 'SUP03' AND wms_supplier_code = 'SUP03';
UPDATE srm_supplier SET score = 96, grade = 'A' WHERE code = 'SUP01' AND (score IS NULL OR score = 0);
UPDATE srm_supplier SET score = 88, grade = 'B' WHERE code = 'SUP02' AND (score IS NULL OR score = 0);
UPDATE srm_supplier SET score = 72, grade = 'C' WHERE code = 'SUP03' AND (score IS NULL OR score = 0);

INSERT INTO srm_material (code, name, spec, unit, category, sap_material_code, wms_item_code, tax_rate, status, created_at, updated_at)
SELECT 'SKU001', '无线鼠标 M1', '黑色', 'EA', '电脑配件', 'M1001', 'SKU001', 0.13, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM srm_material WHERE code='SKU001');
INSERT INTO srm_material (code, name, spec, unit, category, sap_material_code, wms_item_code, tax_rate, status, created_at, updated_at)
SELECT 'SKU002', '机械键盘 K87', '白色 87键', 'EA', '电脑配件', 'M1002', 'SKU002', 0.13, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM srm_material WHERE code='SKU002');
INSERT INTO srm_material (code, name, spec, unit, category, sap_material_code, wms_item_code, tax_rate, status, created_at, updated_at)
SELECT 'SKU003', 'USB-C 数据线 1m', '1米', 'EA', '线材', 'M1003', 'SKU003', 0.13, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM srm_material WHERE code='SKU003');
INSERT INTO srm_material (code, name, spec, unit, category, sap_material_code, wms_item_code, tax_rate, status, created_at, updated_at)
SELECT 'SKU004', '27寸显示器', '2K 144Hz', 'EA', '显示设备', 'M1004', 'SKU004', 0.13, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM srm_material WHERE code='SKU004');

INSERT INTO srm_plant (code, name, sap_plant_code, sap_storage_location, wms_warehouse_code, wms_owner_code, address, status, created_at, updated_at)
SELECT 'P001', '上海工厂', '1000', '0001', 'WH01', 'OWN01', '上海市青浦区华新镇', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM srm_plant WHERE code='P001');

INSERT INTO srm_price_list (supplier_code, material_code, price, currency, min_qty, valid_from, valid_to, contract_no, status, created_at, updated_at)
SELECT 'SUP01', 'SKU001', 45.00, 'CNY', 10, '2025-01-01', '2026-12-31', 'CTR-2025-001', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM srm_price_list WHERE supplier_code='SUP01' AND material_code='SKU001');
INSERT INTO srm_price_list (supplier_code, material_code, price, currency, min_qty, valid_from, valid_to, contract_no, status, created_at, updated_at)
SELECT 'SUP01', 'SKU003', 2.50, 'CNY', 100, '2025-01-01', '2026-12-31', 'CTR-2025-001', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM srm_price_list WHERE supplier_code='SUP01' AND material_code='SKU003');

INSERT INTO srm_discount_band (price_list_id, min_qty, rate, created_at, updated_at)
SELECT p.id, 10, 0.9500, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM srm_price_list p
WHERE p.supplier_code='SUP01' AND p.material_code='SKU001'
AND NOT EXISTS (SELECT 1 FROM srm_discount_band b WHERE b.price_list_id=p.id AND b.min_qty=10);
INSERT INTO srm_discount_band (price_list_id, min_qty, rate, created_at, updated_at)
SELECT p.id, 100, 0.9000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM srm_price_list p
WHERE p.supplier_code='SUP01' AND p.material_code='SKU001'
AND NOT EXISTS (SELECT 1 FROM srm_discount_band b WHERE b.price_list_id=p.id AND b.min_qty=100);
INSERT INTO srm_discount_band (price_list_id, min_qty, rate, created_at, updated_at)
SELECT p.id, 100, 0.9000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM srm_price_list p
WHERE p.supplier_code='SUP01' AND p.material_code='SKU003'
AND NOT EXISTS (SELECT 1 FROM srm_discount_band b WHERE b.price_list_id=p.id AND b.min_qty=100);

INSERT INTO srm_plant_pool (plant_code, material_code, status, created_at, updated_at)
SELECT 'P001', 'SKU001', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM srm_plant_pool WHERE plant_code='P001' AND material_code='SKU001');
INSERT INTO srm_plant_pool (plant_code, material_code, status, created_at, updated_at)
SELECT 'P001', 'SKU003', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM srm_plant_pool WHERE plant_code='P001' AND material_code='SKU003');

-- IR 控制塔：待提交采购申请、可催单采购订单、已延误 ASN
INSERT INTO srm_purchase_requisition (code, plant_code, requester, department, status, remark, created_at, updated_at)
SELECT 'PR-IR-DRAFT', 'P001', 'IR', 'CONTROL_TOWER', 'DRAFT', 'IR 卡单采购申请', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM srm_purchase_requisition WHERE code='PR-IR-DRAFT');
INSERT INTO srm_pr_line (pr_id, line_no, material_code, qty, required_date, ordered_qty, created_at, updated_at)
SELECT id, 1, 'SKU001', 30, CURRENT_DATE + 7, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM srm_purchase_requisition WHERE code='PR-IR-DRAFT'
AND NOT EXISTS (SELECT 1 FROM srm_pr_line WHERE pr_id = (SELECT id FROM srm_purchase_requisition WHERE code='PR-IR-DRAFT'));

INSERT INTO srm_purchase_requisition (code, plant_code, requester, department, status, remark, created_at, updated_at)
SELECT 'PR-IR-SUBMITTED', 'P001', 'IR', 'CONTROL_TOWER', 'SUBMITTED', 'IR 待批准采购申请', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM srm_purchase_requisition WHERE code='PR-IR-SUBMITTED');
INSERT INTO srm_pr_line (pr_id, line_no, material_code, qty, required_date, ordered_qty, created_at, updated_at)
SELECT id, 1, 'SKU001', 18, CURRENT_DATE + 7, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM srm_purchase_requisition WHERE code='PR-IR-SUBMITTED'
AND NOT EXISTS (SELECT 1 FROM srm_pr_line WHERE pr_id = (SELECT id FROM srm_purchase_requisition WHERE code='PR-IR-SUBMITTED'));

INSERT INTO srm_purchase_order (code, supplier_code, plant_code, currency, total_amount, status, expected_date, source_type, remark, created_at, updated_at)
SELECT 'PO-IR-EXPEDITE', 'SUP01', 'P001', 'CNY', 4500.00, 'CONFIRMED', CURRENT_DATE - 2, 'MANUAL', 'IR 催单演示', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM srm_purchase_order WHERE code='PO-IR-EXPEDITE');
INSERT INTO srm_po_line (po_id, line_no, material_code, qty, price, amount, delivery_date, shipped_qty, received_qty, rejected_qty, invoiced_qty, created_at, updated_at)
SELECT id, 1, 'SKU001', 100, 45.00, 4500.00, CURRENT_DATE - 2, 0, 0, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM srm_purchase_order WHERE code='PO-IR-EXPEDITE'
AND NOT EXISTS (SELECT 1 FROM srm_po_line WHERE po_id = (SELECT id FROM srm_purchase_order WHERE code='PO-IR-EXPEDITE'));

INSERT INTO srm_asn (code, po_id, po_code, supplier_code, plant_code, status, expected_date, total_qty, received_qty, rejected_qty, remark, created_at, updated_at)
SELECT 'ASN-IR-DELAY', id, 'PO-IR-EXPEDITE', 'SUP01', 'P001', 'CREATED', CURRENT_DATE - 1, 100, 0, 0, 'IR 延误 ASN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM srm_purchase_order WHERE code='PO-IR-EXPEDITE'
AND NOT EXISTS (SELECT 1 FROM srm_asn WHERE code='ASN-IR-DELAY');
INSERT INTO srm_asn_line (asn_id, line_no, po_line_id, material_code, qty, received_qty, rejected_qty, created_at, updated_at)
SELECT a.id, 1, l.id, 'SKU001', 100, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM srm_asn a
JOIN srm_po_line l ON l.po_id = a.po_id AND l.line_no = 1
WHERE a.code='ASN-IR-DELAY'
AND NOT EXISTS (SELECT 1 FROM srm_asn_line WHERE asn_id = (SELECT id FROM srm_asn WHERE code='ASN-IR-DELAY'));
