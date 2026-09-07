<template>
  <CrudPage title="工厂/收货地" :api="basic.plant" :columns="columns" :option-sources="{}" />
</template>

<script setup>
import CrudPage from '../../components/CrudPage.vue'
import { basic } from '../../api'
import { statusCol } from '../../composables/useOptions'

const columns = [
  { prop: 'code', label: '编码', required: true, readonlyOnEdit: true, width: 90 },
  { prop: 'name', label: '名称', required: true, minWidth: 140 },
  { prop: 'sapPlantCode', label: 'SAP工厂', width: 100 },
  { prop: 'sapStorageLocation', label: 'SAP库存地', width: 100 },
  { prop: 'wmsWarehouseCode', label: 'WMS仓库', width: 100 },
  { prop: 'wmsOwnerCode', label: 'WMS货主', width: 100 },
  { prop: 'address', label: '地址', minWidth: 180 },
  statusCol
]
</script>
