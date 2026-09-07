<template>
  <el-tag :type="meta.type" size="small" effect="light">{{ meta.text }}</el-tag>
</template>

<script setup>
import { computed } from 'vue'

const DICT = {
  // PR / RFQ / PO / ASN / GR / 发票 / 考核 通用状态
  DRAFT: ['草稿', 'info'],
  SUBMITTED: ['已提交', 'primary'],
  APPROVED: ['已审批', 'success'],
  REJECTED: ['已驳回', 'danger'],
  ORDERED: ['已下单', 'success'],
  CANCELLED: ['已取消', 'danger'],
  PUBLISHED: ['已发布', 'primary'],
  QUOTING: ['报价中', 'warning'],
  AWARDED: ['已定标', 'success'],
  LOST: ['未中标', 'info'],
  SENT: ['已下发SAP', 'primary'],
  CONFIRMED: ['已确认', 'success'],
  PARTIALLY_RECEIVED: ['部分收货', 'warning'],
  RECEIVED: ['已收货', 'success'],
  CLOSED: ['已关闭', 'info'],
  CREATED: ['已创建', 'info'],
  SYNCED: ['已同步WMS', 'primary'],
  SYNC_FAILED: ['同步失败', 'danger'],
  RECEIVING: ['收货中', 'warning'],
  POSTED: ['已过账', 'success'],
  POST_FAILED: ['过账失败', 'danger'],
  PENDING: ['待处理', 'info'],
  MATCHED: ['对账相符', 'success'],
  MISMATCH: ['对账不符', 'danger'],
  SUCCESS: ['成功', 'success'],
  FAILED: ['失败', 'danger'],
  OUT: ['发出', 'primary'],
  IN: ['接入', 'warning'],
  MANUAL: ['手工', 'info'],
  PR: ['采购申请', 'primary'],
  RFQ: ['询价', 'warning']
}

const props = defineProps({ value: String })
const meta = computed(() => {
  const d = DICT[props.value]
  return d ? { text: d[0], type: d[1] } : { text: props.value, type: 'info' }
})
</script>
