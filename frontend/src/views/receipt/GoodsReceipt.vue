<template>
  <div class="page">
    <div class="card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="单号/PO/ASN" clearable @keyup.enter="load" @clear="load" />
        <el-select v-model="query.status" placeholder="状态" clearable @change="load">
          <el-option v-for="s in ['PENDING','POSTED','POST_FAILED']" :key="s" :label="s" :value="s" />
        </el-select>
        <el-button type="primary" @click="load"><el-icon><Search /></el-icon>查询</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" border stripe size="small">
        <el-table-column prop="code" label="收货单号" width="160" />
        <el-table-column prop="asnCode" label="ASN" width="170" />
        <el-table-column prop="poCode" label="采购订单" width="160" />
        <el-table-column prop="supplierCode" label="供应商" width="90" />
        <el-table-column prop="totalReceivedQty" label="收货数" width="90" />
        <el-table-column prop="totalRejectedQty" label="拒收" width="70" />
        <el-table-column prop="status" label="状态" width="100"><template #default="{ row }"><StatusTag :value="row.status" /></template></el-table-column>
        <el-table-column prop="sapMaterialDoc" label="SAP物料凭证" width="120" />
        <el-table-column prop="source" label="来源" width="110"><template #default="{ row }"><StatusTag :value="row.source" /></template></el-table-column>
        <el-table-column prop="receivedAt" label="收货时间" width="150"><template #default="{ row }">{{ fmt(row.receivedAt) }}</template></el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
            <el-button v-if="canWrite() && ['POST_FAILED','PENDING'].includes(row.status)" link type="warning" size="small" @click="act(row)">重试过账</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total" layout="total, sizes, prev, pager, next" @change="load" />
      </div>
    </div>

    <el-drawer v-model="detailVisible" :title="`收货单 ${detail.code || ''}`" size="680px">
      <el-descriptions :column="2" border size="small" style="margin-bottom: 12px">
        <el-descriptions-item label="ASN">{{ detail.asnCode }}</el-descriptions-item>
        <el-descriptions-item label="采购订单">{{ detail.poCode }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ detail.supplierCode }}</el-descriptions-item>
        <el-descriptions-item label="状态"><StatusTag :value="detail.status" /></el-descriptions-item>
        <el-descriptions-item label="SAP物料凭证">{{ detail.sapMaterialDoc || '-' }}</el-descriptions-item>
        <el-descriptions-item label="过账时间">{{ fmt(detail.sapPostedAt) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="来源">{{ detail.source }}</el-descriptions-item>
        <el-descriptions-item label="收货时间">{{ fmt(detail.receivedAt) }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="detail.lines || []" border size="small">
        <el-table-column prop="materialCode" label="物料" width="110" />
        <el-table-column prop="receivedQty" label="收货" width="80" />
        <el-table-column prop="rejectedQty" label="拒收" width="70" />
        <el-table-column prop="acceptedQty" label="合格" width="80" />
        <el-table-column prop="lotNo" label="批次" width="100" />
        <el-table-column prop="amount" label="金额" width="100" align="right" />
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { receipt } from '../../api'
import { canWrite, fmt } from '../../auth'
import StatusTag from '../../components/StatusTag.vue'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const detailVisible = ref(false)
const detail = ref({})
const query = reactive({ current: 1, size: 20, status: '', keyword: '' })

async function load() {
  loading.value = true
  try {
    const p = await receipt.page(query)
    rows.value = p.records
    total.value = p.total
  } finally { loading.value = false }
}

async function openDetail(row) {
  detail.value = await receipt.get(row.id)
  detailVisible.value = true
}

async function act(row) {
  const r = await receipt.retryPost(row.id)
  ElMessage.success(`过账结果: ${r.status}${r.sapMaterialDoc ? ' ' + r.sapMaterialDoc : ''}`)
  load()
}

onMounted(load)
</script>
