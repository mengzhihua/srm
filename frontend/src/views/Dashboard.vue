<template>
  <div class="page">
    <el-row :gutter="12">
      <el-col v-for="c in cards" :key="c.label" :span="4">
        <div class="stat">
          <div class="label">{{ c.label }}</div>
          <div class="value" :style="{ color: c.color || '#303133' }">{{ c.value }}</div>
        </div>
      </el-col>
    </el-row>
    <el-row :gutter="12" style="margin-top: 12px">
      <el-col :span="10">
        <div class="card">
          <h3 style="margin-top: 0">供应商等级分布（最近一期）</h3>
          <el-table :data="gradeRows" border size="small">
            <el-table-column prop="grade" label="等级" width="80" />
            <el-table-column prop="count" label="供应商数" width="100" />
            <el-table-column label="占比">
              <template #default="{ row }">
                <el-progress :percentage="row.pct" :stroke-width="14" />
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!gradeRows.length" description="暂无考核数据" :image-size="60" />
        </div>
      </el-col>
      <el-col :span="14">
        <div class="card">
          <h3 style="margin-top: 0">最近集成日志</h3>
          <el-table :data="d.recentLogs || []" border size="small">
            <el-table-column prop="createdAt" label="时间" width="150"><template #default="{ row }">{{ fmt(row.createdAt) }}</template></el-table-column>
            <el-table-column prop="direction" label="方向" width="70"><template #default="{ row }"><StatusTag :value="row.direction" /></template></el-table-column>
            <el-table-column prop="system" label="系统" width="70" />
            <el-table-column prop="action" label="动作" width="150" show-overflow-tooltip />
            <el-table-column prop="bizCode" label="单号" width="150" show-overflow-tooltip />
            <el-table-column prop="status" label="结果" width="80"><template #default="{ row }"><StatusTag :value="row.status" /></template></el-table-column>
          </el-table>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { dashboard } from '../api'
import { fmt } from '../utils'
import StatusTag from '../components/StatusTag.vue'

const d = ref({})

const cards = computed(() => [
  { label: '待审批申请', value: d.value.prPending ?? 0 },
  { label: '待审批订单', value: d.value.poPendingApprove ?? 0 },
  { label: '待下发SAP', value: d.value.poToSap ?? 0 },
  { label: '待供应商确认', value: d.value.poToConfirm ?? 0 },
  { label: '在途ASN', value: d.value.asnInTransit ?? 0 },
  { label: '待/失败记账', value: d.value.grPendingPost ?? 0, color: d.value.grPendingPost ? '#f56c6c' : undefined },
  { label: 'ASN同步失败', value: d.value.asnSyncFailed ?? 0, color: d.value.asnSyncFailed ? '#f56c6c' : undefined },
  { label: '本月采购金额', value: `¥${Number(d.value.monthPurchaseAmount || 0).toLocaleString()}` }
])

const gradeRows = computed(() => {
  const g = d.value.supplierGrades || {}
  const total = Object.values(g).reduce((a, b) => a + b, 0)
  return ['A', 'B', 'C', 'D', 'N/A'].filter((k) => g[k])
    .map((k) => ({ grade: k, count: g[k], pct: total ? Math.round((g[k] / total) * 100) : 0 }))
})

onMounted(async () => { d.value = await dashboard.summary() })
</script>
