<template>
  <CrudPage title="供应商" :api="basic.supplier" :columns="columns" :option-sources="{}" />
</template>

<script setup>
import CrudPage from '../../components/CrudPage.vue'
import { basic } from '../../api'
import { statusCol } from '../../composables/useOptions'

const columns = [
  { prop: 'code', label: '编码', required: true, readonlyOnEdit: true, width: 90 },
  { prop: 'name', label: '名称', required: true, minWidth: 160 },
  { prop: 'shortName', label: '简称', width: 110 },
  { prop: 'category', label: '品类', filter: true, width: 100 },
  { prop: 'contact', label: '联系人', width: 100 },
  { prop: 'phone', label: '电话', width: 120 },
  { prop: 'email', label: '邮箱', minWidth: 140 },
  { prop: 'taxNo', label: '税号', minWidth: 140 },
  { prop: 'address', label: '地址', minWidth: 160 },
  { prop: 'bankName', label: '开户行', minWidth: 120, hideInTable: true },
  { prop: 'bankAccount', label: '银行账号', minWidth: 140, hideInTable: true },
  { prop: 'paymentTerms', label: '付款条件', width: 110 },
  { prop: 'currency', label: '币种', width: 80, default: 'CNY' },
  { prop: 'sapVendorCode', label: 'SAP供应商', width: 100 },
  { prop: 'wmsSupplierCode', label: 'WMS供应商', width: 100 },
  { prop: 'grade', label: '等级', width: 70, hideInForm: true },
  { prop: 'score', label: '得分', width: 80, hideInForm: true },
  { prop: 'status', label: '状态', type: 'select', filter: true, width: 90, default: 1,
    options: [{ value: 1, label: '启用' }, { value: 0, label: '停用' }, { value: 2, label: '黑名单' }] }
]
</script>
