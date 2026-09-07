<template>
  <div class="page">
    <div class="card">
      <div class="toolbar">
        <el-select v-model="query.status" placeholder="状态" clearable @change="load">
          <el-option v-for="s in INV_STATUS" :key="s" :label="s" :value="s" />
        </el-select>
        <el-select v-if="!isSupplier()" v-model="query.supplierCode" placeholder="供应商" clearable filterable @change="load">
          <el-option v-for="o in options.supplier || []" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-button type="primary" @click="load"><el-icon><Search /></el-icon>查询</el-button>
        <el-button v-if="isSupplier()" type="success" @click="openSubmit"><el-icon><Plus /></el-icon>提交发票</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" border stripe size="small">
        <el-table-column prop="code" label="单号" width="160" />
        <el-table-column prop="invoiceNo" label="发票号" width="130" />
        <el-table-column prop="supplierCode" label="供应商" width="90" />
        <el-table-column prop="poCode" label="采购订单" width="160" />
        <el-table-column prop="amount" label="金额" width="110" align="right" />
        <el-table-column prop="taxAmount" label="税额" width="90" align="right" />
        <el-table-column prop="status" label="状态" width="100"><template #default="{ row }"><StatusTag :value="row.status" /></template></el-table-column>
        <el-table-column prop="sapInvoiceDoc" label="SAP发票凭证" width="120" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
            <template v-if="canWrite()">
              <el-button v-if="['SUBMITTED','MISMATCH'].includes(row.status)" link type="success" size="small" @click="act(row, 'match')">对账</el-button>
              <el-button v-if="row.status === 'MATCHED'" link type="success" size="small" @click="act(row, 'approve')">审批</el-button>
              <el-button v-if="['SUBMITTED','MISMATCH'].includes(row.status)" link type="danger" size="small" @click="reject(row)">驳回</el-button>
              <el-button v-if="row.status === 'APPROVED'" link type="warning" size="small" @click="act(row, 'postToSap')">过账SAP</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total" layout="total, sizes, prev, pager, next" @change="load" />
      </div>
    </div>

    <!-- 详情 -->
    <el-drawer v-model="detailVisible" :title="`发票 ${detail.code || ''}`" size="680px">
      <el-descriptions :column="2" border size="small" style="margin-bottom: 12px">
        <el-descriptions-item label="发票号">{{ detail.invoiceNo }}</el-descriptions-item>
        <el-descriptions-item label="状态"><StatusTag :value="detail.status" /></el-descriptions-item>
        <el-descriptions-item label="供应商">{{ detail.supplierCode }}</el-descriptions-item>
        <el-descriptions-item label="采购订单">{{ detail.poCode }}</el-descriptions-item>
        <el-descriptions-item label="金额">¥{{ detail.amount }}</el-descriptions-item>
        <el-descriptions-item label="税额">¥{{ detail.taxAmount }}</el-descriptions-item>
        <el-descriptions-item label="SAP凭证">{{ detail.sapInvoiceDoc || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="detail.lines || []" border size="small">
        <el-table-column prop="materialCode" label="物料" width="120" />
        <el-table-column prop="qty" label="数量" width="90" />
        <el-table-column prop="price" label="单价" width="90" />
        <el-table-column prop="amount" label="金额" width="110" align="right" />
        <el-table-column prop="poLineId" label="PO行" width="70" />
      </el-table>
    </el-drawer>

    <!-- 供应商提交 -->
    <el-dialog v-model="submitVisible" title="提交发票" width="760px" destroy-on-close>
      <el-form label-width="90px">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="采购订单" required>
            <el-select v-model="submitForm.poId" filterable style="width: 100%" @change="loadPoLines">
              <el-option v-for="p in poOptions" :key="p.id" :label="`${p.code} (已收 ${p.receivedText})`" :value="p.id" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="发票号" required><el-input v-model="submitForm.invoiceNo" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="发票日期"><el-date-picker v-model="submitForm.invoiceDate" type="date" value-format="YYYY-MM-DD" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="税额"><el-input-number v-model="submitForm.taxAmount" :min="0" :precision="2" /></el-form-item></el-col>
        </el-row>
        <el-table :data="submitForm.lines" border size="small">
          <el-table-column prop="materialCode" label="物料" width="110" />
          <el-table-column prop="available" label="可开票数" width="90" />
          <el-table-column label="开票数量" width="140"><template #default="{ row }"><el-input-number v-model="row.qty" :min="0" :max="row.available" :precision="3" /></template></el-table-column>
          <el-table-column label="单价" width="140"><template #default="{ row }"><el-input-number v-model="row.price" :min="0" :precision="4" /></template></el-table-column>
          <el-table-column label="金额"><template #default="{ row }">{{ ((row.qty || 0) * (row.price || 0)).toFixed(2) }}</template></el-table-column>
        </el-table>
      </el-form>
      <template #footer>
        <el-button @click="submitVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doSubmit">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { invoice, purchase } from '../../api'
import { canWrite, fmt, isSupplier } from '../../auth'
import { useOptions } from '../../composables/useOptions'
import StatusTag from '../../components/StatusTag.vue'

const INV_STATUS = ['SUBMITTED', 'MATCHED', 'MISMATCH', 'APPROVED', 'POSTED', 'REJECTED']
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const detailVisible = ref(false)
const submitVisible = ref(false)
const detail = ref({})
const poList = ref([])
const submitForm = reactive({ poId: null, poCode: '', invoiceNo: '', invoiceDate: '', taxAmount: 0, lines: [] })
const query = reactive({ current: 1, size: 20, status: '', supplierCode: '' })
const { options } = useOptions(['supplier'])

const poOptions = computed(() => poList.value)

async function load() {
  loading.value = true
  try {
    const p = await invoice.page(query)
    rows.value = p.records
    total.value = p.total
  } finally { loading.value = false }
}

async function openDetail(row) {
  detail.value = await invoice.get(row.id)
  detailVisible.value = true
}

async function act(row, action) {
  await invoice[action](row.id)
  ElMessage.success('操作成功')
  load()
}

async function reject(row) {
  const { value } = await ElMessageBox.prompt('驳回原因', '驳回发票', { confirmButtonText: '驳回', cancelButtonText: '取消' })
  await invoice.reject(row.id, { reason: value })
  ElMessage.success('已驳回')
  load()
}

async function openSubmit() {
  const p = await purchase.page({ current: 1, size: 100, status: '' })
  poList.value = (p.records || [])
    .filter((po) => ['PARTIALLY_RECEIVED', 'RECEIVED'].includes(po.status))
    .map((po) => ({ ...po, receivedText: '' }))
  Object.assign(submitForm, { poId: null, poCode: '', invoiceNo: '', invoiceDate: new Date().toISOString().substring(0, 10), taxAmount: 0, lines: [] })
  submitVisible.value = true
}

async function loadPoLines(poId) {
  const po = poList.value.find((x) => x.id === poId)
  submitForm.poCode = po?.code
  const full = await purchase.get(poId)
  submitForm.lines = (full.lines || [])
    .map((l) => ({
      poLineId: l.id, materialCode: l.materialCode,
      available: (l.receivedQty || 0) - (l.rejectedQty || 0) - (l.invoicedQty || 0),
      qty: Math.max(0, (l.receivedQty || 0) - (l.rejectedQty || 0) - (l.invoicedQty || 0)),
      price: l.price
    }))
    .filter((l) => l.available > 0)
  if (!submitForm.lines.length) ElMessage.warning('该订单无可开票数量')
}

async function doSubmit() {
  const lines = submitForm.lines.filter((l) => l.qty > 0)
  if (!submitForm.poCode || !submitForm.invoiceNo || !lines.length) return ElMessage.warning('请选订单、填发票号并确认开票行')
  saving.value = true
  try {
    const inv = await invoice.submit({
      poCode: submitForm.poCode, invoiceNo: submitForm.invoiceNo,
      invoiceDate: submitForm.invoiceDate, taxAmount: submitForm.taxAmount,
      amount: lines.reduce((a, l) => a + l.qty * l.price, 0),
      lines: lines.map((l) => ({ poLineId: l.poLineId, materialCode: l.materialCode, qty: l.qty, price: l.price }))
    })
    ElMessage.success(`发票 ${inv.code} 已提交`)
    submitVisible.value = false
    load()
  } finally { saving.value = false }
}

onMounted(load)
</script>
