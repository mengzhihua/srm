import http from './request'

export const crud = (base) => ({
  page: (params) => http.get(`${base}/page`, { params }),
  list: (params) => http.get(`${base}/list`, { params }),
  get: (id) => http.get(`${base}/${id}`),
  create: (data) => http.post(base, data),
  update: (id, data) => http.put(`${base}/${id}`, data),
  remove: (id) => http.delete(`${base}/${id}`)
})

export const authApi = {
  login: (data) => http.post('/auth/login', data),
  me: () => http.get('/auth/me'),
  changePassword: (data) => http.post('/auth/password', data),
  logout: () => http.post('/auth/logout')
}

export const basic = {
  supplier: crud('/basic/supplier'),
  material: crud('/basic/material'),
  plant: crud('/basic/plant'),
  priceList: crud('/basic/pricelist')
}

export const sourcing = {
  prPage: (params) => http.get('/sourcing/pr/page', { params }),
  prGet: (id) => http.get(`/sourcing/pr/${id}`),
  prCreate: (data) => http.post('/sourcing/pr', data),
  prSubmit: (id) => http.post(`/sourcing/pr/${id}/submit`),
  prApprove: (id) => http.post(`/sourcing/pr/${id}/approve`),
  prReject: (id) => http.post(`/sourcing/pr/${id}/reject`),
  prCancel: (id) => http.post(`/sourcing/pr/${id}/cancel`),
  prToPo: (id, data) => http.post(`/sourcing/pr/${id}/to-po`, data),
  prToRfq: (id, data) => http.post(`/sourcing/pr/${id}/to-rfq`, data),
  rfqPage: (params) => http.get('/sourcing/rfq/page', { params }),
  rfqGet: (id) => http.get(`/sourcing/rfq/${id}`),
  rfqCreate: (data) => http.post('/sourcing/rfq', data),
  rfqPublish: (id) => http.post(`/sourcing/rfq/${id}/publish`),
  rfqCancel: (id) => http.post(`/sourcing/rfq/${id}/cancel`),
  rfqQuote: (id, data) => http.post(`/sourcing/rfq/${id}/quote`, data),
  rfqAward: (id, data) => http.post(`/sourcing/rfq/${id}/award`, data)
}

export const purchase = {
  page: (params) => http.get('/purchase/order/page', { params }),
  get: (id) => http.get(`/purchase/order/${id}`),
  create: (data) => http.post('/purchase/order', data),
  update: (id, data) => http.put(`/purchase/order/${id}`, data),
  approve: (id) => http.post(`/purchase/order/${id}/approve`),
  sendToSap: (id) => http.post(`/purchase/order/${id}/send-to-sap`),
  confirm: (id) => http.post(`/purchase/order/${id}/confirm`),
  cancel: (id) => http.post(`/purchase/order/${id}/cancel`),
  close: (id) => http.post(`/purchase/order/${id}/close`)
}

export const delivery = {
  page: (params) => http.get('/delivery/asn/page', { params }),
  get: (id) => http.get(`/delivery/asn/${id}`),
  create: (data) => http.post('/delivery/asn', data),
  sync: (id) => http.post(`/delivery/asn/${id}/sync`),
  retrySync: (id) => http.post(`/delivery/asn/${id}/retry-sync`),
  cancel: (id) => http.post(`/delivery/asn/${id}/cancel`),
  pullWms: (id) => http.post(`/delivery/asn/${id}/pull-wms`),
  manualReceipt: (id, data) => http.post(`/delivery/asn/${id}/manual-receipt`, data)
}

export const receipt = {
  page: (params) => http.get('/receipt/page', { params }),
  get: (id) => http.get(`/receipt/${id}`),
  retryPost: (id) => http.post(`/receipt/${id}/retry-post`)
}

export const evaluation = {
  page: (params) => http.get('/evaluation/page', { params }),
  records: (params) => http.get('/evaluation/records', { params }),
  syncSap: (id) => http.post(`/evaluation/${id}/sync-sap`)
}

export const invoice = {
  page: (params) => http.get('/invoice/page', { params }),
  get: (id) => http.get(`/invoice/${id}`),
  submit: (data) => http.post('/invoice', data),
  match: (id) => http.post(`/invoice/${id}/match`),
  approve: (id) => http.post(`/invoice/${id}/approve`),
  reject: (id, data) => http.post(`/invoice/${id}/reject`, data || {}),
  postToSap: (id) => http.post(`/invoice/${id}/post-to-sap`)
}

export const integration = {
  logsPage: (params) => http.get('/integration/logs/page', { params }),
  retry: (id) => http.post(`/integration/logs/${id}/retry`)
}

export const system = {
  user: crud('/system/user'),
  oplogPage: (params) => http.get('/system/oplog/page', { params })
}

export const dashboard = {
  summary: () => http.get('/dashboard')
}
