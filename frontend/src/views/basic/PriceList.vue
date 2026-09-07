<template>
  <CrudPage title="价格/合同价" :api="basic.priceList" :columns="columns" :option-sources="{}" />
</template>

<script setup>
import CrudPage from '../../components/CrudPage.vue'
import { basic } from '../../api'
import { statusCol } from '../../composables/useOptions'

const columns = [
  { prop: 'supplierCode', label: '供应商', required: true, filter: true, width: 110 },
  { prop: 'materialCode', label: '物料', required: true, filter: true, width: 110 },
  { prop: 'price', label: '价格', type: 'number', precision: 4, required: true, width: 100 },
  { prop: 'currency', label: '币种', width: 80, default: 'CNY' },
  { prop: 'minQty', label: '最小起订量', type: 'number', precision: 3, width: 110 },
  { prop: 'validFrom', label: '生效日期', width: 110 },
  { prop: 'validTo', label: '失效日期', width: 110 },
  { prop: 'contractNo', label: '合同号', minWidth: 130 },
  statusCol
]
</script>
