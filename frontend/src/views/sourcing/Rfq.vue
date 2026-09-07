<template>
  <div class="page">
    <div class="card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="单号/标题" clearable @keyup.enter="load" @clear="load" />
        <el-select v-model="query.status" placeholder="状态" clearable @change="load">
          <el-option v-for="s in RFQ_STATUS" :key="s" :label="s" :value="s" />
        </el-select>
        <el-button type="primary" @click="load"><el-icon><Search /></el-icon>查询</el-button>
        <el-button v-if="canWrite()" type="success" @click="openForm()"><el-icon><Plus /></el-icon>新建询价</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" border stripe size="small">
        <el-table-column prop="code" label="单号" width="170" />
        <el-table-column prop="title" label="标题" min-width="160" />
        <el-table-column prop="plantCode" label="工厂" width="80" />
        <el-table-column prop="supplierCodes" label="受邀供应商" min-width="140" />
        <el-table-column prop="deadline" label="报价截止" width="150"><template #default="{ row }">{{ fmt(row.deadline) }}</template></el-table-column>
        <el-table-column prop="status" label="状态" width="100"><template #default="{ row }"><StatusTag :value="row.status" /></template></el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
            <el-button v-if="canWrite() && row.status === 'DRAFT'" link type="success" size="small" @click="act(row, 'rfqPublish')">发布</el-button>
            <el-button v-if="isSupplier() && ['PUBLISHED','QUOTING'].includes(row.status)" link type="warning" size="small" @click="openDetail(row, true)">报价</el-button>
            <el-button v-if="canWrite() && ['PUBLISHED','QUOTING','DRAFT'].includes(row.status)" link type="danger" size="small" @click="act(row, 'rfqCancel')">取消</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total" layout="total, sizes, prev, pager, next" @change="load" />
      </div>
    </div>

    <!-- 新建 -->
    <el-dialog v-model="visible" title="新建询价单" width="780px" destroy-on-close>
      <el-form :model="form" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="标题" required><el-input v-model="form.title" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="工厂">
            <el-select v-model="form.plantCode" style="width: 100%"><el-option v-for="o in options.plant || []" :key="o.value" :label="o.label" :value="o.value" /></el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="受邀供应商" required>
            <el-select v-model="supplierSel" multiple style="width: 100%">
              <el-option v-for="o in options.supplier || []" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="报价截止"><el-date-picker v-model="form.deadline" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="询价行">
          <el-table :data="form.lines" border size="small" style="width: 100%">
            <el-table-column label="物料" min-width="170"><template #default="{ row }">
              <el-select v-model="row.materialCode" filterable><el-option v-for="o in options.material || []" :key="o.value" :label="o.label" :value="o.value" /></el-select>
            </template></el-table-column>
            <el-table-column label="数量" width="130"><template #default="{ row }"><el-input-number v-model="row.qty" :min="0.001" :precision="3" /></template></el-table-column>
            <el-table-column label="需求日期" width="160"><template #default="{ row }"><el-date-picker v-model="row.requiredDate" type="date" value-format="YYYY-MM-DD" /></template></el-table-column>
            <el-table-column width="60"><template #default="{ $index }"><el-button link type="danger" @click="form.lines.splice($index, 1)">删</el-button></template></el-table-column>
          </el-table>
          <el-button size="small" style="margin-top: 6px" @click="form.lines.push({ qty: 1 })">+ 添加行</el-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情：行 + 报价对比 + 定标 / 供应商报价 -->
    <el-drawer v-model="detailVisible" :title="`询价单 ${detail.code || ''}`" size="720px">
      <el-descriptions :column="2" border size="small" style="margin-bottom: 12px">
        <el-descriptions-item label="标题">{{ detail.title }}</el-descriptions-item>
        <el-descriptions-item label="状态"><StatusTag :value="detail.status" /></el-descriptions-item>
        <el-descriptions-item label="工厂">{{ detail.plantCode }}</el-descriptions-item>
        <el-descriptions-item label="截止">{{ fmt(detail.deadline) }}</el-descriptions-item>
        <el-descriptions-item label="受邀供应商" :span="2">{{ detail.supplierCodes }}</el-descriptions-item>
      </el-descriptions>

      <h4>询价行</h4>
      <el-table :data="detail.lines || []" border size="small">
        <el-table-column prop="lineNo" label="行号" width="60" />
        <el-table-column prop="materialCode" label="物料" width="110" />
        <el-table-column prop="qty" label="数量" width="90" />
        <el-table-column prop="requiredDate" label="需求日期" width="110" />
      </el-table>

      <template v-if="!isSupplier()">
        <h4>报价对比</h4>
        <el-table :data="compareRows" border size="small">
          <el-table-column prop="supplierCode" label="供应商" width="100" />
          <el-table-column prop="status" label="报价状态" width="90"><template #default="{ row }"><StatusTag :value="row.status" /></template></el-table-column>
          <el-table-column v-for="l in detail.lines || []" :key="l.id" :label="l.materialCode" min-width="110">
            <template #default="{ row }">
              <div v-if="row.priceMap[l.id]">¥{{ row.priceMap[l.id].price }} / {{ row.priceMap[l.id].leadTimeDays ?? '-' }}天</div>
              <span v-else style="color: #c0c4cc">—</span>
            </template>
          </el-table-column>
          <el-table-column prop="totalAmount" label="总价" width="100" />
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button v-if="canWrite() && row.status === 'SUBMITTED' && ['PUBLISHED','QUOTING'].includes(detail.status)"
                link type="success" size="small" @click="award(row)">定标</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!compareRows.length" description="暂无报价" :image-size="60" />
      </template>

      <!-- 供应商报价表单 -->
      <template v-if="isSupplier() && ['PUBLISHED','QUOTING'].includes(detail.status)">
        <h4>提交报价</h4>
        <el-table :data="quoteForm.lines" border size="small">
          <el-table-column prop="materialCode" label="物料" width="110" />
          <el-table-column prop="qty" label="数量" width="90" />
          <el-table-column label="单价" width="140"><template #default="{ row }"><el-input-number v-model="row.price" :min="0" :precision="4" /></template></el-table-column>
          <el-table-column label="交期(天)" width="120"><template #default="{ row }"><el-input-number v-model="row.leadTimeDays" :min="0" /></template></el-table-column>
        </el-table>
        <el-input v-model="quoteForm.remark" placeholder="报价备注" style="margin-top: 8px" />
        <el-button type="primary" style="margin-top: 10px" :loading="saving" @click="submitQuote">提交报价</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { sourcing } from '../../api'
import { auth, canWrite, fmt, isSupplier } from '../../auth'
import { useOptions } from '../../composables/useOptions'
import StatusTag from '../../components/StatusTag.vue'

const RFQ_STATUS = ['DRAFT', 'PUBLISHED', 'QUOTING', 'AWARDED', 'CANCELLED']
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const detailVisible = ref(false)
const form = ref({ lines: [] })
const supplierSel = ref([])
const detail = ref({})
const quoteForm = reactive({ lines: [], remark: '' })
const query = reactive({ current: 1, size: 20, status: '', keyword: '' })
const { options } = useOptions(['plant', 'material', 'supplier'])

const compareRows = computed(() =>
  (detail.value.quotes || []).map((q) => {
    const priceMap = {}
    ;(q.lines || []).forEach((ql) => { if (ql.rfqLineId) priceMap[ql.rfqLineId] = ql })
    return { ...q, priceMap }
  })
)

async function load() {
  loading.value = true
  try {
    const p = await sourcing.rfqPage(query)
    rows.value = p.records
    total.value = p.total
  } finally { loading.value = false }
}

function openForm() {
  form.value = { title: '', plantCode: 'P001', lines: [{ qty: 1 }] }
  supplierSel.value = []
  visible.value = true
}

async function save() {
  if (!form.value.lines?.length) return ElMessage.warning('请添加询价行')
  saving.value = true
  try {
    await sourcing.rfqCreate({ ...form.value, supplierCodes: supplierSel.value.join(',') })
    ElMessage.success('已创建')
    visible.value = false
    load()
  } finally { saving.value = false }
}

async function openDetail(row) {
  detail.value = await sourcing.rfqGet(row.id)
  quoteForm.lines = (detail.value.lines || []).map((l) => ({
    rfqLineId: l.id, materialCode: l.materialCode, qty: l.qty, price: null, leadTimeDays: null
  }))
  quoteForm.remark = ''
  detailVisible.value = true
}

async function act(row, fn) {
  await sourcing[fn](row.id)
  ElMessage.success('操作成功')
  load()
}

async function award(row) {
  const { value } = await ElMessageBox.prompt('期望到货日期(可选, YYYY-MM-DD)', `定标给 ${row.supplierCode}`, {
    confirmButtonText: '定标', cancelButtonText: '取消', inputPattern: /^$|^\d{4}-\d{2}-\d{2}$/, inputErrorMessage: '日期格式不正确', inputValue: ''
  })
  const po = await sourcing.rfqAward(detail.value.id, { quoteId: row.id, expectedDate: value || null })
  ElMessage.success(`已生成采购订单 ${po.code}`)
  detailVisible.value = false
  load()
}

async function submitQuote() {
  saving.value = true
  try {
    await sourcing.rfqQuote(detail.value.id, { supplierCode: auth.user?.supplierCode, remark: quoteForm.remark, lines: quoteForm.lines })
    ElMessage.success('报价已提交')
    detail.value = await sourcing.rfqGet(detail.value.id)
  } finally { saving.value = false }
}

onMounted(load)
</script>
