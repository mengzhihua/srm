<template>
  <CrudPage title="物料" :api="basic.material" :columns="columns" :option-sources="{}" />
</template>

<script setup>
import CrudPage from '../../components/CrudPage.vue'
import { basic } from '../../api'
import { statusCol } from '../../composables/useOptions'

const columns = [
  { prop: 'code', label: '编码', required: true, readonlyOnEdit: true, width: 100 },
  { prop: 'name', label: '名称', required: true, minWidth: 150 },
  { prop: 'spec', label: '规格', width: 110 },
  { prop: 'unit', label: '单位', width: 70, default: 'EA' },
  { prop: 'category', label: '品类', filter: true, width: 100 },
  { prop: 'sapMaterialCode', label: 'SAP物料', width: 100 },
  { prop: 'wmsItemCode', label: 'WMS物料', width: 100 },
  { prop: 'taxRate', label: '税率', type: 'number', precision: 4, min: 0, width: 90 },
  statusCol
]
</script>
