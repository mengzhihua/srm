<template>
  <div class="page">
    <div class="card">
      <div class="toolbar">
        <el-input v-model="text" placeholder="物料编码、制造商料号，或品牌加料号" style="width: 360px" @keyup.enter="add" />
        <el-input-number v-model="qty" :min="0.001" :precision="3" />
        <el-button type="primary" :loading="saving" @click="add">加入清单</el-button>
        <el-button type="success" :loading="saving" @click="convert">转采购申请</el-button>
      </div>
      <p style="margin: 0 0 8px; color: #667085">对不上物料的行会留下来。清单里还有未匹配行时，不能转申请。</p>
      <el-table :data="rows" border stripe size="small">
        <el-table-column prop="requestText" label="需求" min-width="220" />
        <el-table-column prop="materialCode" label="物料" width="140">
          <template #default="{ row }">{{ row.materialCode || '未匹配' }}</template>
        </el-table-column>
        <el-table-column prop="qty" label="数量" width="100" />
        <el-table-column label="优惠券" width="120">
          <template #default="{ row }">{{ row.couponPercent ? `${row.couponCode} ${row.couponPercent}%` : '—' }}</template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { sourcing } from '../../api'

const rows = ref([])
const text = ref('')
const qty = ref(1)
const saving = ref(false)

async function load() {
  rows.value = await sourcing.demandList()
}

async function add() {
  if (!text.value.trim()) return ElMessage.warning('请填写需求')
  saving.value = true
  try {
    await sourcing.demandAdd({ text: text.value, qty: qty.value })
    text.value = ''
    await load()
  } finally {
    saving.value = false
  }
}

async function convert() {
  saving.value = true
  try {
    const pr = await sourcing.demandConvert()
    ElMessage.success(`已生成采购申请 ${pr.code}`)
    await load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>
