-- 演示数据（幂等：已存在则跳过）。wms_* 编码与 WMS 项目 data.sql 保持一致。
INSERT INTO srm_supplier (code, name, short_name, contact, phone, email, address, category, payment_terms, currency, sap_vendor_code, wms_supplier_code, status, created_at, updated_at)
SELECT 'SUP01', '深圳精密电子厂', '精密电子', '王强', '13700000001', 'sales@sup01.example', '深圳市宝安区', '原材料', '月结30天', 'CNY', '100010', 'SUP01', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM srm_supplier WHERE code='SUP01');
INSERT INTO srm_supplier (code, name, short_name, contact, phone, email, address, category, payment_terms, currency, sap_vendor_code, wms_supplier_code, status, created_at, updated_at)
SELECT 'SUP02', '东莞线材制造', '东莞线材', '赵敏', '13700000002', 'sales@sup02.example', '东莞市松山湖', '原材料', '月结60天', 'CNY', '100020', 'SUP02', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM srm_supplier WHERE code='SUP02');
INSERT INTO srm_supplier (code, name, short_name, contact, phone, email, address, category, payment_terms, currency, sap_vendor_code, wms_supplier_code, status, created_at, updated_at)
SELECT 'SUP03', '苏州包装制品', '苏州包装', '陈杰', '13700000003', 'sales@sup03.example', '苏州市工业园区', '包材', '货到付款', 'CNY', '100030', 'SUP03', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM srm_supplier WHERE code='SUP03');

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
