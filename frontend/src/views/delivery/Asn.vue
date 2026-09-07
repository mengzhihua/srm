<template>
  <div class="page">
    <div class="card">
      <div class="toolbar">
        <el-input v-model="query.poCode" placeholder="采购订单号" clearable @keyup.enter="load" @clear="load" />
        <el-select v-model="query.status" placeholder="状态" clearable @change="load">
          <el-option v-for="s in ASN_STATUS" :key="s" :label="s" :value="s" />
        </el-select>
        <el-button type="primary" @click="load"><el-icon><Search /></el-icon>查询</el-button>
        <el-button v-if="isSupplier()" type="success" @click="$router.push('/portal/order')">去订单页发货</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" border stripe size="small">
        <el-table-column prop="code" label="ASN单号" width="170" />
        <el-table-column prop="poCode" label="采购订单" width="160" />
        <el-table-column prop="supplierCode" label="供应商" width="90" />
        <el-table-column prop="status" label="状态" width="100"><template #default="{ row }"><StatusTag :value="row.status" /></template></el-table-column>
        <el-table-column prop="totalQty" label="发货数量" width="90" />
        <el-table-column prop="receivedQty" label="已收货" width="90" />
        <el-table-column prop="rejectedQty" label="拒收" width="70" />
        <el-table-column prop="wmsAsnCode" label="WMS单号" width="150" />
        <el-table-column prop="expectedDate" label="预计到货" width="100" />
        <el-table-column prop="carrier" label="承运商" width="90" />
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
            <el-button v-if="canWritePortal() && ['CREATED','SYNC_FAILED'].includes(row.status)" link type="warning" size="small" @click="act(row, 'retrySync')">重试同步</el-button>
            <el-button v-if="canWritePortal() && ['CREATED','SYNC_FAILED'].includes(row.status)" link type="danger" size="small" @click="act(row, 'cancel')">取消</el-button>
            <el-button v-if="canWrite() && row.wmsAsnId && ['SYNCED','RECEIVING'].includes(row.status)" link type="primary" size="small" @click="act(row, 'pullWms')">拉取WMS</el-button>
            <el-button v-if="canWrite() && ['SYNCED','RECEIVING'].includes(row.status)" link type="success" size="small" @click="openReceipt(row)">手工收货</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total" layout="total, sizes, prev, pager, next" @change="load" />
      </div>
    </div>

    <!-- 详情 -->
    <el-drawer v-model="detailVisible" :title="`ASN ${detail.code || ''}`" size="700px">
      <el-descriptions :column="2" border size="small" style="margin-bottom: 12px">
        <el-descriptions-item label="采购订单">{{ detail.poCode }}</el-descriptions-item>
        <el-descriptions-item label="状态"><StatusTag :value="detail.status" /></el-descriptions-item>
        <el-descriptions-item label="供应商">{{ detail.supplierCode }}</el-descriptions-item>
        <el-descriptions-item label="工厂">{{ detail.plantCode }}</el-descriptions-item>
        <el-descriptions-item label="WMS单号">{{ detail.wmsAsnCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="同步时间">{{ fmt(detail.syncedAt) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="预计到货">{{ detail.expectedDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="收货时间">{{ fmt(detail.receivedAt) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="承运商/运单">{{ detail.carrier || '-' }} / {{ detail.trackingNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="detail.lines || []" border size="small">
        <el-table-column prop="lineNo" label="#" width="45" />
        <el-table-column prop="materialCode" label="物料" width="110" />
        <el-table-column prop="qty" label="发货数" width="90" />
        <el-table-column prop="receivedQty" label="已收" width="90" />
        <el-table-column prop="rejectedQty" label="拒收" width="80" />
        <el-table-column prop="lotNo" label="批次" width="100" />
        <el-table-column prop="expiryDate" label="效期" width="100" />
      </el-table>
    </el-drawer>

    <!-- 手工收货回传 -->
    <el-dialog v-model="receiptVisible" title="手工录入收货（模拟WMS回传）" width="700px" destroy-on-close>
      <el-table :data="receiptForm.lines" border size="small">
        <el-table-column prop="materialCode" label="物料" width="110" />
        <el-table-column prop="qty" label="发货数" width="80" />
        <el-table-column label="实收数" width="130"><template #default="{ row }"><el-input-number v-model="row.receivedQty" :min="0" :precision="3" /></template></el-table-column>
        <el-table-column label="拒收数" width="120"><template #default="{ row }"><el-input-number v-model="row.rejectedQty" :min="0" :precision="3" /></template></el-table-column>
        <el-table-column label="批次号" width="140"><template #default="{ row }"><el-input v-model="row.lotNo" /></template></el-table-column>
        <el-table-column label="拒收原因" min-width="110"><template #default="{ row }"><el-input v-model="row.rejectReason" /></template></el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="receiptVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doReceipt">提交收货</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { delivery } from '../../api'
import { canWrite, canWritePortal, fmt, isSupplier } from '../../auth'
import StatusTag from '../../components/StatusTag.vue'

const ASN_STATUS = ['CREATED', 'SYNCED', 'SYNC_FAILED', 'RECEIVING', 'RECEIVED', 'POSTED', 'CANCELLED']
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const detailVisible = ref(false)
const receiptVisible = ref(false)
const detail = ref({})
const receiptForm = reactive({ asnId: null, asnCode: '', lines: [] })
const query = reactive({ current: 1, size: 20, status: '', poCode: '', supplierCode: '' })

async function load() {
  loading.value = true
  try {
    const p = await delivery.page(query)
    rows.value = p.records
    total.value = p.total
  } finally { loading.value = false }
}

async function openDetail(row) {
  detail.value = await delivery.get(row.id)
  detailVisible.value = true
}

async function act(row, action) {
  const r = await delivery[action](row.id)
  ElMessage.success('操作成功')
  load()
  if (detailVisible.value && detail.value.id === row.id) detail.value = r
}

async function openReceipt(row) {
  const asn = await delivery.get(row.id)
  receiptForm.asnId = asn.id
  receiptForm.asnCode = asn.code
  receiptForm.lines = (asn.lines || [])
    .filter((l) => (l.qty || 0) - (l.receivedQty || 0) > 0)
    .map((l) => ({ itemCode: l.materialCode, materialCode: l.materialCode, qty: l.qty, lotNo: l.lotNo || '', receivedQty: 0, rejectedQty: 0, rejectReason: '' }))
  receiptVisible.value = true
}

async function doReceipt() {
  const lines = receiptForm.lines.filter((l) => l.receivedQty > 0 || l.rejectedQty > 0)
  if (!lines.length) return ElMessage.warning('请填写收货数量')
  saving.value = true
  try {
    const gr = await delivery.manualReceipt(receiptForm.asnId, {
      externalNo: receiptForm.asnCode,
      lines: lines.map((l) => ({ itemCode: l.itemCode, lotNo: l.lotNo, receivedQty: l.receivedQty, rejectedQty: l.rejectedQty, rejectReason: l.rejectReason }))
    })
    ElMessage.success(`收货单 ${gr.code} 已生成，SAP凭证 ${gr.sapMaterialDoc || '过账失败'}`)
    receiptVisible.value = false
    load()
  } finally { saving.value = false }
}

onMounted(load)
</script>
