<template>
  <div class="page">
    <div class="card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="单号/SAP单号/来源单号" clearable @keyup.enter="load" @clear="load" />
        <el-select v-model="query.status" placeholder="状态" clearable @change="load">
          <el-option v-for="s in PO_STATUS" :key="s" :label="s" :value="s" />
        </el-select>
        <el-select v-if="!isSupplier()" v-model="query.supplierCode" placeholder="供应商" clearable filterable @change="load">
          <el-option v-for="o in options.supplier || []" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-button type="primary" @click="load"><el-icon><Search /></el-icon>查询</el-button>
        <el-button v-if="canWrite()" type="success" @click="openForm()"><el-icon><Plus /></el-icon>新建订单</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" border stripe size="small">
        <el-table-column prop="code" label="单号" width="160" />
        <el-table-column prop="supplierCode" label="供应商" width="90" />
        <el-table-column prop="plantCode" label="工厂" width="70" />
        <el-table-column prop="totalAmount" label="金额" width="110" align="right" />
        <el-table-column prop="status" label="状态" width="110"><template #default="{ row }"><StatusTag :value="row.status" /></template></el-table-column>
        <el-table-column prop="sapPoNo" label="SAP PO" width="110" />
        <el-table-column prop="sourceType" label="来源" width="90"><template #default="{ row }"><StatusTag :value="row.sourceType" /></template></el-table-column>
        <el-table-column prop="sourceCode" label="来源单号" width="150" show-overflow-tooltip />
        <el-table-column prop="expectedDate" label="期望到货" width="100" />
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
            <template v-if="canWrite()">
              <el-button v-if="row.status === 'DRAFT'" link type="success" size="small" @click="act(row, 'approve')">审批</el-button>
              <el-button v-if="['APPROVED','SENT'].includes(row.status)" link type="warning" size="small" @click="act(row, 'sendToSap')">下发SAP</el-button>
              <el-button v-if="['DRAFT','APPROVED','SENT','CONFIRMED'].includes(row.status)" link type="danger" size="small" @click="act(row, 'cancel')">取消</el-button>
              <el-button v-if="['SENT','CONFIRMED','PARTIALLY_RECEIVED','RECEIVED'].includes(row.status)" link type="info" size="small" @click="act(row, 'close')">关闭</el-button>
            </template>
            <template v-if="isSupplier()">
              <el-button v-if="row.status === 'SENT'" link type="success" size="small" @click="act(row, 'confirm')">确认订单</el-button>
              <el-button v-if="['CONFIRMED','SENT','PARTIALLY_RECEIVED'].includes(row.status)" link type="warning" size="small" @click="openShip(row)">发货</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total" layout="total, sizes, prev, pager, next" @change="load" />
      </div>
    </div>

    <!-- 新建 -->
    <el-dialog v-model="visible" title="新建采购订单" width="820px" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="供应商" required>
            <el-select v-model="form.supplierCode" filterable style="width: 100%"><el-option v-for="o in options.supplier || []" :key="o.value" :label="o.label" :value="o.value" /></el-select>
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item label="工厂">
            <el-select v-model="form.plantCode" style="width: 100%"><el-option v-for="o in options.plant || []" :key="o.value" :label="o.label" :value="o.value" /></el-select>
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item label="币种"><el-input v-model="form.currency" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="期望到货"><el-date-picker v-model="form.expectedDate" type="date" value-format="YYYY-MM-DD" /></el-form-item></el-col>
          <el-col :span="16"><el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="订单行">
          <el-table :data="form.lines" border size="small" style="width: 100%">
            <el-table-column label="物料" min-width="160"><template #default="{ row }">
              <el-select v-model="row.materialCode" filterable><el-option v-for="o in options.material || []" :key="o.value" :label="o.label" :value="o.value" /></el-select>
            </template></el-table-column>
            <el-table-column label="数量" width="125"><template #default="{ row }"><el-input-number v-model="row.qty" :min="0.001" :precision="3" /></template></el-table-column>
            <el-table-column label="单价" width="135"><template #default="{ row }"><el-input-number v-model="row.price" :min="0" :precision="4" /></template></el-table-column>
            <el-table-column label="交期" width="150"><template #default="{ row }"><el-date-picker v-model="row.deliveryDate" type="date" value-format="YYYY-MM-DD" /></template></el-table-column>
            <el-table-column width="50"><template #default="{ $index }"><el-button link type="danger" @click="form.lines.splice($index, 1)">删</el-button></template></el-table-column>
          </el-table>
          <el-button size="small" style="margin-top: 6px" @click="form.lines.push({ qty: 1 })">+ 添加行</el-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情 -->
    <el-drawer v-model="detailVisible" :title="`采购订单 ${detail.code || ''}`" size="720px">
      <el-descriptions :column="2" border size="small" style="margin-bottom: 12px">
        <el-descriptions-item label="供应商">{{ detail.supplierCode }}</el-descriptions-item>
        <el-descriptions-item label="状态"><StatusTag :value="detail.status" /></el-descriptions-item>
        <el-descriptions-item label="工厂">{{ detail.plantCode }}</el-descriptions-item>
        <el-descriptions-item label="金额">¥{{ detail.totalAmount }}</el-descriptions-item>
        <el-descriptions-item label="SAP PO号">{{ detail.sapPoNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="下发时间">{{ fmt(detail.sapSentAt) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="确认时间">{{ fmt(detail.confirmedAt) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="期望到货">{{ detail.expectedDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="来源">{{ detail.sourceType }} / {{ detail.sourceCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="detail.lines || []" border size="small">
        <el-table-column prop="lineNo" label="#" width="45" />
        <el-table-column prop="materialCode" label="物料" width="100" />
        <el-table-column prop="qty" label="订单量" width="80" />
        <el-table-column prop="price" label="单价" width="80" />
        <el-table-column prop="amount" label="金额" width="90" />
        <el-table-column prop="shippedQty" label="已发货" width="80" />
        <el-table-column prop="receivedQty" label="已收货" width="80" />
        <el-table-column prop="rejectedQty" label="拒收" width="70" />
        <el-table-column prop="invoicedQty" label="已开票" width="80" />
        <el-table-column prop="deliveryDate" label="交期" width="100" />
      </el-table>
    </el-drawer>

    <!-- 供应商发货 -->
    <el-dialog v-model="shipVisible" title="创建发货单 (ASN)" width="760px" destroy-on-close>
      <el-form label-width="90px">
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="预计到货"><el-date-picker v-model="shipForm.expectedDate" type="date" value-format="YYYY-MM-DD" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="承运商"><el-input v-model="shipForm.carrier" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="运单号"><el-input v-model="shipForm.trackingNo" /></el-form-item></el-col>
        </el-row>
        <el-table :data="shipForm.lines" border size="small">
          <el-table-column prop="materialCode" label="物料" width="110" />
          <el-table-column prop="remain" label="可发数量" width="90" />
          <el-table-column label="本次发货" width="140"><template #default="{ row }"><el-input-number v-model="row.qty" :min="0" :precision="3" /></template></el-table-column>
          <el-table-column label="批次号" width="150"><template #default="{ row }"><el-input v-model="row.lotNo" /></template></el-table-column>
          <el-table-column label="效期" width="160"><template #default="{ row }"><el-date-picker v-model="row.expiryDate" type="date" value-format="YYYY-MM-DD" /></template></el-table-column>
        </el-table>
        <el-input v-model="shipForm.remark" placeholder="备注" style="margin-top: 8px" />
      </el-form>
      <template #footer>
        <el-button @click="shipVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doShip">发货</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { delivery, purchase } from '../../api'
import { canWrite, fmt, isSupplier } from '../../auth'
import { useOptions } from '../../composables/useOptions'
import StatusTag from '../../components/StatusTag.vue'

const PO_STATUS = ['DRAFT', 'APPROVED', 'SENT', 'CONFIRMED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CLOSED', 'CANCELLED']
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const detailVisible = ref(false)
const shipVisible = ref(false)
const form = ref({ lines: [] })
const detail = ref({})
const shipForm = reactive({ poId: null, expectedDate: '', carrier: '', trackingNo: '', remark: '', lines: [] })
const query = reactive({ current: 1, size: 20, status: '', supplierCode: '', keyword: '' })
const { options } = useOptions(['supplier', 'material', 'plant'])

async function load() {
  loading.value = true
  try {
    const p = await purchase.page(query)
    rows.value = p.records
    total.value = p.total
  } finally { loading.value = false }
}

function openForm() {
  form.value = { supplierCode: '', plantCode: 'P001', currency: 'CNY', lines: [{ qty: 1 }] }
  visible.value = true
}

async function save() {
  if (!form.value.lines?.length) return ElMessage.warning('请添加订单行')
  saving.value = true
  try {
    await purchase.create(form.value)
    ElMessage.success('已创建')
    visible.value = false
    load()
  } finally { saving.value = false }
}

async function openDetail(row) {
  detail.value = await purchase.get(row.id)
  detailVisible.value = true
}

async function act(row, action) {
  const r = await purchase[action](row.id)
  ElMessage.success(action === 'sendToSap' ? `已下发 SAP: ${r.sapPoNo}` : '操作成功')
  load()
  if (detailVisible.value && detail.value.id === row.id) detail.value = r
}

async function openShip(row) {
  const po = await purchase.get(row.id)
  shipForm.poId = po.id
  shipForm.expectedDate = po.expectedDate || ''
  shipForm.carrier = ''
  shipForm.trackingNo = ''
  shipForm.remark = ''
  shipForm.lines = (po.lines || [])
    .map((l) => ({ poLineId: l.id, materialCode: l.materialCode, remain: l.qty - (l.shippedQty || 0), qty: Math.max(0, l.qty - (l.shippedQty || 0)), lotNo: '', expiryDate: '' }))
    .filter((l) => l.remain > 0)
  if (!shipForm.lines.length) return ElMessage.warning('订单已全部发货')
  shipVisible.value = true
}

async function doShip() {
  const lines = shipForm.lines.filter((l) => l.qty > 0)
  if (!lines.length) return ElMessage.warning('请填写发货数量')
  saving.value = true
  try {
    const asn = await delivery.create({
      poId: shipForm.poId, expectedDate: shipForm.expectedDate || null,
      carrier: shipForm.carrier, trackingNo: shipForm.trackingNo, remark: shipForm.remark,
      lines: lines.map((l) => ({ poLineId: l.poLineId, qty: l.qty, lotNo: l.lotNo, expiryDate: l.expiryDate || null }))
    })
    ElMessage.success(`发货单 ${asn.code} 已创建${asn.wmsAsnCode ? '，WMS单号 ' + asn.wmsAsnCode : ''}`)
    shipVisible.value = false
    load()
  } finally { saving.value = false }
}

onMounted(load)
</script>
