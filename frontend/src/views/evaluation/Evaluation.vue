<template>
  <div class="page">
    <div class="card">
      <el-tabs v-model="tab">
        <el-tab-pane label="月度汇总" name="summary" />
        <el-tab-pane label="单票明细" name="records" />
      </el-tabs>
      <div class="toolbar">
        <el-input v-if="!isSupplier()" v-model="query.supplierCode" placeholder="供应商编码" clearable @keyup.enter="load" @clear="load" />
        <el-input v-if="tab === 'summary'" v-model="query.period" placeholder="期间 yyyy-MM" clearable style="width: 160px" @keyup.enter="load" @clear="load" />
        <el-button type="primary" @click="load"><el-icon><Search /></el-icon>查询</el-button>
      </div>

      <el-table v-if="tab === 'summary'" :data="rows" v-loading="loading" border stripe size="small">
        <el-table-column prop="supplierCode" label="供应商" width="110" />
        <el-table-column prop="period" label="期间" width="90" />
        <el-table-column prop="receiptCount" label="收货单数" width="90" />
        <el-table-column prop="onTimeRate" label="准时率%" width="90" />
        <el-table-column prop="qtyAccuracy" label="数量准确率%" width="110" />
        <el-table-column prop="qualityRate" label="质量合格率%" width="110" />
        <el-table-column prop="avgScore" label="综合得分" width="90" />
        <el-table-column prop="grade" label="等级" width="70">
          <template #default="{ row }"><el-tag :type="row.grade === 'A' ? 'success' : row.grade === 'B' ? 'primary' : row.grade === 'C' ? 'warning' : 'danger'">{{ row.grade }}</el-tag></template>
        </el-table-column>
        <el-table-column label="已同步SAP" width="100"><template #default="{ row }"><el-tag :type="row.sapSynced ? 'success' : 'info'" size="small">{{ row.sapSynced ? '是' : '否' }}</el-tag></template></el-table-column>
        <el-table-column prop="sapSyncedAt" label="同步时间" width="150"><template #default="{ row }">{{ fmt(row.sapSyncedAt) }}</template></el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canWrite() && !row.sapSynced" link type="warning" size="small" @click="syncSap(row)">同步SAP</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-table v-else :data="rows" v-loading="loading" border stripe size="small">
        <el-table-column prop="supplierCode" label="供应商" width="100" />
        <el-table-column prop="grCode" label="收货单" width="160" />
        <el-table-column prop="asnCode" label="ASN" width="170" />
        <el-table-column prop="poCode" label="PO" width="160" />
        <el-table-column label="准时" width="70"><template #default="{ row }"><el-tag :type="row.onTime ? 'success' : 'danger'" size="small">{{ row.onTime ? '是' : '否' }}</el-tag></template></el-table-column>
        <el-table-column prop="qtyAccuracy" label="数量准确率%" width="110" />
        <el-table-column prop="qualityRate" label="质量率%" width="90" />
        <el-table-column prop="leadDays" label="交付天数" width="80" />
        <el-table-column prop="wmsScore" label="WMS分" width="80" />
        <el-table-column prop="score" label="综合分" width="80" />
        <el-table-column prop="wmsRemark" label="WMS评价" min-width="140" show-overflow-tooltip />
      </el-table>

      <div class="pager">
        <el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total" layout="total, sizes, prev, pager, next" @change="load" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { evaluation } from '../../api'
import { canWrite, fmt, isSupplier } from '../../auth'

const tab = ref('summary')
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ current: 1, size: 20, supplierCode: '', period: '' })

async function load() {
  loading.value = true
  try {
    const p = tab.value === 'summary' ? await evaluation.page(query) : await evaluation.records(query)
    rows.value = p.records
    total.value = p.total
  } finally { loading.value = false }
}

async function syncSap(row) {
  await evaluation.syncSap(row.id)
  ElMessage.success('已同步 SAP')
  load()
}

watch(tab, () => { query.current = 1; load() })
onMounted(load)
</script>
