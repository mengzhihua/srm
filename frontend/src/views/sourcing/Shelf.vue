<template>
  <div class="page">
    <div class="card">
      <div class="toolbar">
        <el-select v-model="supplierCode" placeholder="选供应商看协议价" clearable filterable @change="load">
          <el-option v-for="item in suppliers" :key="item.code" :label="`${item.code} ${item.name}`" :value="item.code" />
        </el-select>
        <el-input v-model="couponCode" placeholder="优惠券编码" style="width: 160px" @keyup.enter="load" />
        <el-input-number v-model="couponPercent" :min="0.01" :max="100" :precision="2" />
        <el-button :loading="saving" @click="saveCoupon">保存优惠券</el-button>
        <el-button type="primary" :loading="loading" @click="load">按券看价</el-button>
        <el-input-number v-model="qty" :min="0.001" :precision="3" />
      </div>
      <p style="margin: 0 0 12px; color: #667085">
        按品类浏览，不是完整商城。数量 1 显示协议价；满 10 件九五折，满 100 件九折。没到起订量或没有协议价时不编造牌价。优惠券是自己保存的百分比，没有有效期和次数。
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
            <p style="margin: 4px 0">数量 1 协议价：{{ money(card.agreementQty1) }}</p>
            <p style="margin: 4px 0">满 10 件九五折：{{ money(card.priceQty10) }}</p>
            <p style="margin: 4px 0">满 100 件九折：{{ money(card.priceQty100) }}</p>
            <p v-if="couponApplied" style="margin: 4px 0">券后 1 / 10 / 100：{{ money(card.couponQty1) }} / {{ money(card.couponQty10) }} / {{ money(card.couponQty100) }}</p>
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
      <el-tag v-for="item in coupons" :key="item.id" style="margin: 0 8px 8px 0; cursor: pointer" @click="useCoupon(item)">{{ item.code }} {{ item.percent }}%</el-tag>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { basic, sourcing } from '../../api'

const suppliers = ref([])
const supplierCode = ref('SUP01')
const couponCode = ref('')
const couponPercent = ref(10)
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
    const shelf = await sourcing.shelf({ supplierCode: supplierCode.value || undefined, coupon: couponCode.value || undefined })
    groups.value = shelf.groups || []
    couponApplied.value = !!shelf.couponApplied
    marks.value = await sourcing.shelfMarks()
    coupons.value = await sourcing.shelfCoupons()
  } finally {
    loading.value = false
  }
}

async function saveCoupon() {
  if (!couponCode.value.trim()) return ElMessage.warning('请填写优惠券编码')
  saving.value = true
  try {
    await sourcing.shelfSaveCoupon({ code: couponCode.value.trim(), percent: couponPercent.value })
    ElMessage.success('优惠券已保存')
    await load()
  } finally {
    saving.value = false
  }
}

function useCoupon(item) {
  couponCode.value = item.code
  couponPercent.value = Number(item.percent)
  load()
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
  await sourcing.demandAdd({ text: card.materialCode, qty: qty.value })
  ElMessage.success(`已加入需求清单 ${card.materialCode}`)
  await load()
}

onMounted(async () => {
  suppliers.value = await basic.supplier.list()
  await load()
})
</script>
