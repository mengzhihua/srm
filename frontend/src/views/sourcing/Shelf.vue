<template>
  <div class="page">
    <div class="card">
      <div class="toolbar">
        <el-select v-model="supplierCode" placeholder="选供应商看协议价" clearable filterable @change="load">
          <el-option v-for="item in suppliers" :key="item.code" :label="`${item.code} ${item.name}`" :value="item.code" />
        </el-select>
        <el-select v-model="plantCode" placeholder="工厂可买范围" clearable filterable @change="load">
          <el-option v-for="item in plants" :key="item.code" :label="`${item.code} ${item.name}`" :value="item.code" />
        </el-select>
        <el-input v-model="couponCode" placeholder="优惠券编码" style="width: 140px" @keyup.enter="load" />
        <el-input-number v-model="couponPercent" :min="0.01" :max="100" :precision="2" />
        <el-date-picker v-model="validFrom" type="date" value-format="YYYY-MM-DD" placeholder="生效日" style="width: 140px" />
        <el-date-picker v-model="validTo" type="date" value-format="YYYY-MM-DD" placeholder="失效日" style="width: 140px" />
        <el-input-number v-model="maxUses" :min="1" :step="1" />
        <el-button :loading="saving" @click="saveCoupon">保存优惠券</el-button>
        <el-button type="primary" :loading="loading" @click="load">按券看价</el-button>
        <el-input-number v-model="qty" :min="0.001" :precision="3" />
      </div>
      <p style="margin: 0 0 12px; color: #667085">
        按品类浏览，选了工厂就只显示该厂可买的物料。折扣档写在协议上，没有协议价不编造牌价。优惠券要填生效日、失效日和可用次数，加入需求清单时扣一次，还不写进采购订单单价。
      </p>
      <div style="display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 12px">
        <el-button size="small" :type="category === '' ? 'primary' : ''" @click="category = ''">全部</el-button>
        <el-button v-for="name in categories" :key="name" size="small" :type="category === name ? 'primary' : ''" @click="category = name">{{ name }}</el-button>
      </div>
      <div v-for="group in visibleGroups" :key="group.category" style="margin-bottom: 16px">
        <h3 style="margin: 8px 0; font-size: 16px">{{ group.category }}</h3>
        <div style="display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 12px">
          <article v-for="card in group.cards" :key="card.materialCode" style="border: 1px solid #e4e7ed; border-radius: 8px; padding: 12px">
            <strong>{{ card.name }}</strong>
            <p style="margin: 4px 0; color: #667085">{{ card.materialCode }} · {{ card.brand || '无品牌' }} · {{ card.mfrPartNo || '无制造商料号' }}</p>
            <p style="margin: 4px 0">数量 1 协议价：{{ money(card.agreementQty1) }}<span v-if="couponApplied">，券后 {{ money(card.couponQty1) }}</span></p>
            <p v-for="band in card.bands || []" :key="band.minQty" style="margin: 4px 0">满 {{ band.minQty }} 件 × {{ band.rate }}：{{ money(band.price) }}<span v-if="couponApplied">，券后 {{ money(band.couponPrice) }}</span></p>
            <p v-if="!(card.bands || []).length" style="margin: 4px 0; color: #667085">这份协议没有折扣档</p>
            <div style="display: flex; gap: 8px; flex-wrap: wrap; margin-top: 8px">
              <el-button size="small" @click="toggleFavorite(card)">{{ card.favorite ? '取消收藏' : '收藏' }}</el-button>
              <el-button size="small" @click="remember(card)">记下浏览</el-button>
              <el-button size="small" type="primary" @click="addDemand(card)">加入需求清单</el-button>
            </div>
          </article>
        </div>
      </div>
    </div>
    <div class="card" style="margin-top: 12px">
      <h3 style="margin-top: 0">收藏</h3>
      <p v-if="!marks.favorites.length" style="color: #667085">还没有收藏。</p>
      <el-tag v-for="item in marks.favorites" :key="item.id" style="margin: 0 8px 8px 0" closable @close="unfavorite(item.materialCode)">{{ item.materialCode }}</el-tag>
      <h3>最近浏览</h3>
      <p v-if="!marks.history.length" style="color: #667085">还没有浏览记录。每人保留最近 20 条。</p>
      <el-tag v-for="item in marks.history" :key="item.id" style="margin: 0 8px 8px 0">{{ item.materialCode }}</el-tag>
      <h3>已存优惠券</h3>
      <p v-if="!coupons.length" style="color: #667085">还没有自己保存的优惠券。</p>
      <el-tag v-for="item in coupons" :key="item.id" style="margin: 0 8px 8px 0; cursor: pointer" @click="useCoupon(item)">{{ item.code }} {{ item.percent }}% {{ item.validFrom }} 至 {{ item.validTo }} 已用 {{ item.usedCount || 0 }}/{{ item.maxUses }}</el-tag>
      <h3>工厂可买</h3>
      <div class="toolbar">
        <el-select v-model="poolMaterial" placeholder="选择物料" clearable filterable>
          <el-option v-for="item in materials" :key="item.code" :label="`${item.code} ${item.name}`" :value="item.code" />
        </el-select>
        <el-button :disabled="!plantCode" @click="addPool">加入该厂可买</el-button>
      </div>
      <p v-if="!plantCode" style="color: #667085">先选工厂，再维护这个工厂允许购买的物料。</p>
      <p v-else-if="!pool.length" style="color: #667085">这个工厂还没有可买物料。</p>
      <el-tag v-for="item in pool" :key="item.id" style="margin: 0 8px 8px 0" closable @close="removePool(item.materialCode)">{{ item.materialCode }}</el-tag>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { basic, sourcing } from '../../api'

const suppliers = ref([])
const plants = ref([])
const materials = ref([])
const supplierCode = ref('SUP01')
const plantCode = ref('')
const poolMaterial = ref('')
const pool = ref([])
const couponCode = ref('')
const couponPercent = ref(10)
const validFrom = ref('2026-01-01')
const validTo = ref('2026-12-31')
const maxUses = ref(5)
const qty = ref(1)
const category = ref('')
const groups = ref([])
const couponApplied = ref(false)
const marks = ref({ favorites: [], history: [] })
const coupons = ref([])
const loading = ref(false)
const saving = ref(false)

const categories = computed(() => groups.value.map((group) => group.category))
const visibleGroups = computed(() => groups.value.filter((group) => !category.value || group.category === category.value))

function money(value) {
  if (value === null || value === undefined || value === '') return '—'
  return Number(value).toFixed(4)
}

async function load() {
  loading.value = true
  try {
    const shelf = await sourcing.shelf({
      supplierCode: supplierCode.value || undefined,
      coupon: couponCode.value || undefined,
      plantCode: plantCode.value || undefined
    })
    groups.value = shelf.groups || []
    couponApplied.value = !!shelf.couponApplied
    marks.value = await sourcing.shelfMarks()
    coupons.value = await sourcing.shelfCoupons()
    pool.value = plantCode.value ? await sourcing.shelfPool({ plantCode: plantCode.value }) : []
  } finally {
    loading.value = false
  }
}

async function saveCoupon() {
  if (!couponCode.value.trim()) return ElMessage.warning('请填写优惠券编码')
  saving.value = true
  try {
    await sourcing.shelfSaveCoupon({
      code: couponCode.value.trim(),
      percent: couponPercent.value,
      validFrom: validFrom.value,
      validTo: validTo.value,
      maxUses: maxUses.value
    })
    ElMessage.success('优惠券已保存')
    await load()
  } finally {
    saving.value = false
  }
}

function useCoupon(item) {
  couponCode.value = item.code
  couponPercent.value = Number(item.percent)
  validFrom.value = item.validFrom
  validTo.value = item.validTo
  maxUses.value = item.maxUses
  load()
}

async function addPool() {
  if (!plantCode.value || !poolMaterial.value) return ElMessage.warning('请选择工厂和物料')
  await sourcing.shelfAddPool({ plantCode: plantCode.value, materialCode: poolMaterial.value })
  await load()
}

async function removePool(materialCode) {
  await sourcing.shelfRemovePool(plantCode.value, materialCode)
  await load()
}

async function toggleFavorite(card) {
  if (card.favorite) await sourcing.shelfUnfavorite(card.materialCode)
  else await sourcing.shelfFavorite({ materialCode: card.materialCode })
  await load()
}

async function unfavorite(materialCode) {
  await sourcing.shelfUnfavorite(materialCode)
  await load()
}

async function remember(card) {
  await sourcing.shelfHistory({ materialCode: card.materialCode })
  await load()
}

async function addDemand(card) {
  await sourcing.shelfHistory({ materialCode: card.materialCode })
  await sourcing.demandAdd({
    text: card.materialCode,
    qty: qty.value,
    couponCode: couponApplied.value ? couponCode.value.trim() : undefined
  })
  ElMessage.success(`已加入需求清单 ${card.materialCode}`)
  await load()
}

onMounted(async () => {
  suppliers.value = await basic.supplier.list()
  plants.value = await basic.plant.list()
  materials.value = await basic.material.list()
  await load()
})
</script>
