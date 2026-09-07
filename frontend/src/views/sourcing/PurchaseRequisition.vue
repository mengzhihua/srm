<template>
  <div class="page">
    <div class="card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="单号/申请人" clearable @keyup.enter="load" @clear="load" />
        <el-select v-model="query.status" placeholder="状态" clearable @change="load">
          <el-option v-for="s in PR_STATUS" :key="s" :label="s" :value="s" />
        </el-select>
        <el-button type="primary" @click="load"><el-icon><Search /></el-icon>查询</el-button>
        <el-button v-if="canWrite()" type="success" @click="openForm()"><el-icon><Plus /></el-icon>新建申请</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" border stripe size="small">
        <el-table-column prop="code" label="单号" width="160" />
        <el-table-column prop="plantCode" label="工厂" width="80" />
        <el-table-column prop="requester" label="申请人" width="90" />
        <el-table-column prop="department" label="部门" width="110" />
        <el-table-column prop="status" label="状态" width="100"><template #default="{ row }"><StatusTag :value="row.status" /></template></el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="150"><template #default="{ row }">{{ fmt(row.createdAt) }}</template></el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" />
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
            <template v-if="canWrite()">
              <el-button v-if="row.status === 'DRAFT'" link type="primary" size="small" @click="act(row, 'submit')">提交</el-button>
              <el-button v-if="row.status === 'SUBMITTED'" link type="success" size="small" @click="act(row, 'approve')">审批</el-button>
              <el-button v-if="row.status === 'SUBMITTED'" link type="danger" size="small" @click="act(row, 'reject')">驳回</el-button>
              <el-button v-if="row.status === 'APPROVED'" link type="warning" size="small" @click="openToPo(row)">转订单</el-button>
              <el-button v-if="row.status === 'APPROVED'" link type="warning" size="small" @click="openToRfq(row)">转询价</el-button>
              <el-button v-if="['DRAFT','SUBMITTED','APPROVED'].includes(row.status)" link type="danger" size="small" @click="act(row, 'cancel')">取消</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total" layout="total, sizes, prev, pager, next" @change="load" />
      </div>
    </div>

    <!-- 新建/详情 -->
    <el-dialog v-model="visible" :title="form.id ? `申请详情 ${form.code}` : '新建采购申请'" width="780px" destroy-on-close>
      <el-form :model="form" label-width="90px" :disabled="!!form.id">
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="工厂" required>
            <el-select v-model="form.plantCode" style="width: 100%"><el-option v-for="o in options.plant || []" :key="o.value" :label="o.label" :value="o.value" /></el-select>
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item label="申请人"><el-input v-model="form.requester" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="部门"><el-input v-model="form.department" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="申请行">
          <el-table :data="form.lines" border size="small" style="width: 100%">
            <el-table-column label="物料" min-width="170">
              <template #default="{ row }">
                <el-select v-model="row.materialCode" filterable :disabled="!!form.id">
                  <el-option v-for="o in options.material || []" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="数量" width="130"><template #default="{ row }"><el-input-number v-model="row.qty" :min="0.001" :precision="3" :disabled="!!form.id" /></template></el-table-column>
            <el-table-column label="需求日期" width="160"><template #default="{ row }"><el-date-picker v-model="row.requiredDate" type="date" value-format="YYYY-MM-DD" :disabled="!!form.id" /></template></el-table-column>
            <el-table-column v-if="form.id" prop="orderedQty" label="已下单" width="80" />
            <el-table-column label="备注" min-width="110"><template #default="{ row }"><el-input v-model="row.remark" :disabled="!!form.id" /></template></el-table-column>
            <el-table-column v-if="!form.id" width="60"><template #default="{ $index }"><el-button link type="danger" @click="form.lines.splice($index, 1)">删</el-button></template></el-table-column>
          </el-table>
          <el-button v-if="!form.id" size="small" style="margin-top: 6px" @click="form.lines.push({ qty: 1 })">+ 添加行</el-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">关闭</el-button>
        <el-button v-if="!form.id" type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 转订单 -->
    <el-dialog v-model="poVisible" title="PR 转采购订单" width="680px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="供应商" required>
          <el-select v-model="poForm.supplierCode" filterable style="width: 100%">
            <el-option v-for="o in options.supplier || []" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-table :data="poForm.lines" border size="small">
          <el-table-column prop="materialCode" label="物料" width="110" />
          <el-table-column label="数量" width="130"><template #default="{ row }"><el-input-number v-model="row.qty" :min="0.001" :precision="3" /></template></el-table-column>
          <el-table-column label="单价" width="140"><template #default="{ row }"><el-input-number v-model="row.price" :min="0" :precision="4" /></template></el-table-column>
          <el-table-column label="交期" width="150"><template #default="{ row }"><el-date-picker v-model="row.deliveryDate" type="date" value-format="YYYY-MM-DD" /></template></el-table-column>
        </el-table>
      </el-form>
      <template #footer>
        <el-button @click="poVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doToPo">生成订单</el-button>
      </template>
    </el-dialog>

    <!-- 转询价 -->
    <el-dialog v-model="rfqVisible" title="PR 转询价单" width="560px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="标题" required><el-input v-model="rfqForm.title" /></el-form-item>
        <el-form-item label="受邀供应商" required>
          <el-select v-model="rfqForm.supplierCodes" multiple style="width: 100%">
            <el-option v-for="o in options.supplier || []" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="报价截止"><el-date-picker v-model="rfqForm.deadline" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="rfqForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rfqVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doToRfq">生成询价</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { sourcing } from '../../api'
import { canWrite, fmt } from '../../auth'
import { useOptions } from '../../composables/useOptions'
import StatusTag from '../../components/StatusTag.vue'

const PR_STATUS = ['DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'ORDERED', 'CANCELLED']
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const poVisible = ref(false)
const rfqVisible = ref(false)
const form = ref({ lines: [] })
const poForm = reactive({ prId: null, supplierCode: '', lines: [] })
const rfqForm = reactive({ prId: null, title: '', supplierCodes: [], deadline: '', remark: '' })
const query = reactive({ current: 1, size: 20, status: '', keyword: '' })
const { options } = useOptions(['plant', 'material', 'supplier'])

async function load() {
  loading.value = true
  try {
    const p = await sourcing.prPage(query)
    rows.value = p.records
    total.value = p.total
  } finally { loading.value = false }
}

function openForm() {
  form.value = { plantCode: 'P001', requester: '', department: '', lines: [{ qty: 1 }] }
  visible.value = true
}

async function openDetail(row) {
  form.value = await sourcing.prGet(row.id)
  visible.value = true
}

async function save() {
  if (!form.value.lines?.length) return ElMessage.warning('请添加申请行')
  saving.value = true
  try {
    await sourcing.prCreate(form.value)
    ElMessage.success('已创建')
    visible.value = false
    load()
  } finally { saving.value = false }
}

async function act(row, action) {
  await sourcing[`pr${action[0].toUpperCase()}${action.slice(1)}`](row.id)
  ElMessage.success('操作成功')
  load()
}

function openToPo(row) {
  const pr = row
  sourcing.prGet(pr.id).then((full) => {
    poForm.prId = pr.id
    poForm.supplierCode = ''
    poForm.lines = full.lines.map((l) => ({ materialCode: l.materialCode, qty: l.qty, price: null, deliveryDate: l.requiredDate }))
    poVisible.value = true
  })
}

async function doToPo() {
  if (!poForm.supplierCode) return ElMessage.warning('请选择供应商')
  saving.value = true
  try {
    const po = await sourcing.prToPo(poForm.prId, { supplierCode: poForm.supplierCode, lines: poForm.lines })
    ElMessage.success(`已生成采购订单 ${po.code}`)
    poVisible.value = false
    load()
  } finally { saving.value = false }
}

function openToRfq(row) {
  rfqForm.prId = row.id
  rfqForm.title = `${row.code} 询价`
  rfqForm.supplierCodes = []
  rfqForm.deadline = ''
  rfqForm.remark = ''
  rfqVisible.value = true
}

async function doToRfq() {
  if (!rfqForm.title || !rfqForm.supplierCodes.length) return ElMessage.warning('请填写标题并选择供应商')
  saving.value = true
  try {
    const rfq = await sourcing.prToRfq(rfqForm.prId, {
      title: rfqForm.title,
      supplierCodes: rfqForm.supplierCodes.join(','),
      deadline: rfqForm.deadline || null,
      remark: rfqForm.remark
    })
    ElMessage.success(`已生成询价单 ${rfq.code}`)
    rfqVisible.value = false
    load()
  } finally { saving.value = false }
}

onMounted(load)
</script>
