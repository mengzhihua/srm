<template>
  <div class="page">
    <div class="card">
      <div class="toolbar">
        <el-select v-model="query.system" placeholder="系统" clearable style="width: 120px" @change="load">
          <el-option label="SAP" value="SAP" /><el-option label="WMS" value="WMS" />
        </el-select>
        <el-select v-model="query.status" placeholder="结果" clearable style="width: 120px" @change="load">
          <el-option label="成功" value="SUCCESS" /><el-option label="失败" value="FAILED" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="业务单号" clearable style="width: 200px" @keyup.enter="load" @clear="load" />
        <el-button type="primary" @click="load"><el-icon><Search /></el-icon>查询</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" border stripe size="small">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div style="padding: 8px 16px">
              <el-descriptions :column="3" size="small" border style="margin-bottom: 8px">
                <el-descriptions-item label="业务">{{ row.bizType }} #{{ row.bizId }} {{ row.bizCode }}</el-descriptions-item>
                <el-descriptions-item label="耗时">{{ row.durationMs }}ms</el-descriptions-item>
                <el-descriptions-item label="重试次数">{{ row.retryCount || 0 }}</el-descriptions-item>
                <el-descriptions-item label="错误" :span="3">{{ row.errorMsg || '-' }}</el-descriptions-item>
              </el-descriptions>
              <div style="font-weight: 600; margin: 4px 0">请求</div>
              <pre class="payload">{{ pretty(row.request) }}</pre>
              <div style="font-weight: 600; margin: 4px 0">响应</div>
              <pre class="payload">{{ pretty(row.response) }}</pre>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="时间" width="150"><template #default="{ row }">{{ fmt(row.createdAt) }}</template></el-table-column>
        <el-table-column prop="direction" label="方向" width="70"><template #default="{ row }"><StatusTag :value="row.direction" /></template></el-table-column>
        <el-table-column prop="system" label="系统" width="70" />
        <el-table-column prop="action" label="动作" width="160" show-overflow-tooltip />
        <el-table-column prop="bizCode" label="业务单号" width="170" show-overflow-tooltip />
        <el-table-column prop="status" label="结果" width="80"><template #default="{ row }"><StatusTag :value="row.status" /></template></el-table-column>
        <el-table-column prop="errorMsg" label="错误" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canWrite() && row.status === 'FAILED'" link type="warning" size="small" @click="retry(row)">重试</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total" layout="total, sizes, prev, pager, next" @change="load" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { integration } from '../../api'
import { canWrite, fmt } from '../../auth'
import StatusTag from '../../components/StatusTag.vue'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ current: 1, size: 20, system: '', status: '', keyword: '' })

function pretty(s) {
  if (!s) return '-'
  try { return JSON.stringify(JSON.parse(s), null, 2) } catch (e) { return s }
}

async function load() {
  loading.value = true
  try {
    const p = await integration.logsPage(query)
    rows.value = p.records
    total.value = p.total
  } finally { loading.value = false }
}

async function retry(row) {
  await integration.retry(row.id)
  ElMessage.success('重试已执行')
  load()
}

onMounted(load)
</script>

<style scoped>
.payload { background: #f5f7fa; border-radius: 4px; padding: 8px; font-size: 12px; max-height: 220px; overflow: auto; white-space: pre-wrap; word-break: break-all; }
</style>
