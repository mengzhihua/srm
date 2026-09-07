import { ref } from 'vue'
import { basic } from '../api'

/** Loads master-data drop-down options once per page. */
export function useOptions(kinds) {
  const options = ref({})
  const loaders = {
    supplier: async () => (await basic.supplier.list()).map((s) => ({ label: `${s.code} ${s.name}`, value: s.code })),
    material: async () => (await basic.material.list()).map((m) => ({ label: `${m.code} ${m.name}`, value: m.code, unit: m.unit, wmsItemCode: m.wmsItemCode })),
    plant: async () => (await basic.plant.list()).map((p) => ({ label: `${p.code} ${p.name}`, value: p.code, wmsWarehouseCode: p.wmsWarehouseCode }))
  }
  async function reload() {
    const out = {}
    await Promise.all(kinds.map(async (k) => { out[k] = await loaders[k]() }))
    options.value = out
  }
  reload()
  return { options, reload }
}

export const statusCol = { prop: 'status', label: '状态', type: 'status', width: 80, default: 1 }
