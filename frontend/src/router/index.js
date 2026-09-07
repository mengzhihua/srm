import { createRouter, createWebHistory } from 'vue-router'
import Layout from '../layout/Layout.vue'
import { auth, isAdmin, isSupplier } from '../auth'

export const menus = [
  { path: '/dashboard', name: '工作台', icon: 'Odometer', buyerSide: true, component: () => import('../views/Dashboard.vue') },
  {
    path: '/basic', name: '基础数据', icon: 'Setting', buyerSide: true,
    children: [
      { path: 'supplier', name: '供应商', component: () => import('../views/basic/Supplier.vue') },
      { path: 'material', name: '物料', component: () => import('../views/basic/Material.vue') },
      { path: 'plant', name: '工厂/收货地', component: () => import('../views/basic/Plant.vue') },
      { path: 'pricelist', name: '价格/合同价', component: () => import('../views/basic/PriceList.vue') }
    ]
  },
  {
    path: '/sourcing', name: '寻源管理', icon: 'Aim', buyerSide: true,
    children: [
      { path: 'pr', name: '采购申请', component: () => import('../views/sourcing/PurchaseRequisition.vue') },
      { path: 'rfq', name: '询价单(RFQ)', component: () => import('../views/sourcing/Rfq.vue') }
    ]
  },
  {
    path: '/purchase', name: '采购执行', icon: 'ShoppingCart', buyerSide: true,
    children: [
      { path: 'order', name: '采购订单', component: () => import('../views/purchase/Order.vue') },
      { path: 'asn', name: '发货通知(ASN)', component: () => import('../views/delivery/Asn.vue') },
      { path: 'receipt', name: '收货记账(GR)', component: () => import('../views/receipt/GoodsReceipt.vue') }
    ]
  },
  {
    path: '/settle', name: '对账考核', icon: 'DataAnalysis', buyerSide: true,
    children: [
      { path: 'invoice', name: '发票对账', component: () => import('../views/invoice/Invoice.vue') },
      { path: 'evaluation', name: '供应商考核', component: () => import('../views/evaluation/Evaluation.vue') }
    ]
  },
  { path: '/integration', name: '集成日志', icon: 'Connection', buyerSide: true, component: () => import('../views/integration/Logs.vue') },
  {
    path: '/system', name: '系统管理', icon: 'Tools', adminOnly: true, buyerSide: true,
    children: [
      { path: 'user', name: '用户管理', component: () => import('../views/system/User.vue') },
      { path: 'oplog', name: '操作日志', component: () => import('../views/system/OpLog.vue') }
    ]
  },
  // ---- 供应商门户（SUPPLIER 角色仅见此组）----
  {
    path: '/portal', name: '供应商门户', icon: 'OfficeBuilding', supplierOnly: true,
    children: [
      { path: 'rfq', name: '我的询价', component: () => import('../views/sourcing/Rfq.vue') },
      { path: 'order', name: '我的订单', component: () => import('../views/purchase/Order.vue') },
      { path: 'asn', name: '我的发货', component: () => import('../views/delivery/Asn.vue') },
      { path: 'receipt', name: '我的收货', component: () => import('../views/receipt/GoodsReceipt.vue') },
      { path: 'invoice', name: '我的发票', component: () => import('../views/invoice/Invoice.vue') },
      { path: 'evaluation', name: '我的考核', component: () => import('../views/evaluation/Evaluation.vue') }
    ]
  }
]

/** 当前用户可见菜单：SUPPLIER 仅供应商门户组，adminOnly 仅管理员 */
export const visibleMenus = () =>
  menus.filter((m) => {
    if (m.adminOnly && !isAdmin()) return false
    return isSupplier() ? !!m.supplierOnly : !m.supplierOnly
  })

export const homePath = () => (isSupplier() ? '/portal/order' : '/dashboard')

const routes = [
  { path: '/login', name: '登录', component: () => import('../views/Login.vue') },
  {
    path: '/',
    component: Layout,
    redirect: () => homePath(),
    children: menus.flatMap((m) =>
      m.children
        ? m.children.map((c) => ({ path: `${m.path}/${c.path}`, name: c.name, component: c.component }))
        : [{ path: m.path, name: m.name, component: m.component }]
    )
  }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to) => {
  if (to.path === '/login') return auth.token ? homePath() : true
  if (!auth.token) return { path: '/login', query: { redirect: to.fullPath } }
  if (to.path.startsWith('/system') && !isAdmin()) return homePath()
  if (isSupplier() && !to.path.startsWith('/portal')) return '/portal/order'
  if (!isSupplier() && to.path.startsWith('/portal')) return '/dashboard'
  return true
})

export default router
