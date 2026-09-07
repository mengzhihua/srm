import { reactive } from 'vue'

const TOKEN_KEY = 'srm_token'
const USER_KEY = 'srm_user'

/** 登录态：令牌与当前用户，持久化到 localStorage */
export const auth = reactive({
  token: localStorage.getItem(TOKEN_KEY) || '',
  user: JSON.parse(localStorage.getItem(USER_KEY) || 'null')
})

export function setAuth(token, user) {
  auth.token = token
  auth.user = user
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function clearAuth() {
  auth.token = ''
  auth.user = null
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export const isAdmin = () => auth.user?.role === 'ADMIN'
export const isBuyer = () => auth.user?.role === 'BUYER'
export const isSupplier = () => auth.user?.role === 'SUPPLIER'
/** 采购侧写权限：ADMIN + BUYER */
export const canWrite = () => ['ADMIN', 'BUYER'].includes(auth.user?.role)
/** 供应商门户写权限：ADMIN + BUYER + SUPPLIER（各自动作再由后端校验数据范围） */
export const canWritePortal = () => !!auth.user && auth.user.role !== 'VIEWER'
export const canEditMaster = canWrite

export const ROLE_LABEL = { ADMIN: '管理员', BUYER: '采购员', SUPPLIER: '供应商', VIEWER: '只读' }
export const fmt = (v) => (v ? String(v).replace('T', ' ').substring(0, 19) : '')
export const today = () => new Date().toISOString().substring(0, 10)
