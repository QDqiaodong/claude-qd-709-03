import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * 统一走 nginx 反代到后端 /api：
 *   成功 → 后端包成 { ok:true, message, data }，这里直接把 data 交给页面
 *   失败 → 后端包成 { ok:false, message:"中文业务提示" }，统一弹提示并 reject
 */
const http = axios.create({ baseURL: '/api', timeout: 15000 })

http.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && typeof body === 'object' && Object.prototype.hasOwnProperty.call(body, 'ok')) {
      if (body.ok) return body.data
      ElMessage.error(body.message || '操作失败')
      return Promise.reject(new Error(body.message || '操作失败'))
    }
    return body
  },
  (err) => {
    const body = err.response && err.response.data
    const message = (body && body.message) || err.message || '网络异常，请稍后再试'
    ElMessage.error(message)
    return Promise.reject(new Error(message))
  }
)

/** 模块一：线路与区段 */
export const lineApi = {
  list: () => http.get('/lines'),
  board: (lineId) => http.get(`/lines/${lineId}/board`),
  create: (data) => http.post('/lines', data)
}

export const sectionApi = {
  list: (lineId) => http.get('/sections', { params: lineId ? { lineId } : {} }),
  create: (data) => http.post('/sections', data),
  setStatus: (id, status, speedLimit) => http.post(`/sections/${id}/status`, { status, speedLimit })
}

/** 模块二：设备台账（TABLE_PER_CLASS 多态查询） */
export const deviceApi = {
  /** 不传 type：查基类 Device，返回钢轨/道岔/轨枕/桥梁/隧道全部子类 */
  list: (sectionId, type) =>
    http.get('/devices', { params: { ...(sectionId ? { sectionId } : {}), ...(type ? { type } : {}) } }),
  byStatus: (status) => http.get('/devices/by-status', { params: { status } }),
  create: (data) => http.post('/devices', data),
  check: (id, data) => http.post(`/devices/${id}/check`, data || {})
}

/** 模块三：检查与病害 */
export const inspectionApi = {
  list: (sectionId) => http.get('/inspections', { params: sectionId ? { sectionId } : {} }),
  create: (data) => http.post('/inspections', data)
}

export const defectApi = {
  list: (params) => http.get('/defects', { params: params || {} }),
  create: (data) => http.post('/defects', data),
  close: (id, data) => http.post(`/defects/${id}/close`, data)
}

/** 模块四：养修作业 */
export const workOrderApi = {
  list: (sectionId) => http.get('/work-orders', { params: sectionId ? { sectionId } : {} }),
  create: (data) => http.post('/work-orders', data),
  advance: (id) => http.post(`/work-orders/${id}/advance`)
}

/** 模块四-延伸：封锁解除交接台 */
export const handoverApi = {
  list: (params) => http.get('/handovers', { params: params || {} }),
  eligibleOrders: () => http.get('/handovers/eligible-orders'),
  detail: (id) => http.get(`/handovers/${id}`),
  submit: (data) => http.post('/handovers', data),
  review: (id, data) => http.post(`/handovers/${id}/review`, data)
}

/** 模块五：物资与机具 */
export const materialApi = {
  list: () => http.get('/materials'),
  create: (data) => http.post('/materials', data),
  adjustStock: (id, quantity) => http.post(`/materials/${id}/stock`, { quantity })
}

export const equipmentApi = {
  list: () => http.get('/equipments'),
  create: (data) => http.post('/equipments', data),
  maintain: (id, data) => http.post(`/equipments/${id}/maintain`, data || {})
}

/** 工区总览计数器 */
export const worksApi = {
  overview: () => http.get('/works/overview')
}

/** 模块六：定检复查批次 */
export const recheckApi = {
  list: (params) => http.get('/recheck-batches', { params: params || {} }),
  dueDevices: (sectionId) => http.get('/recheck-batches/due-devices', { params: { sectionId } }),
  detail: (id) => http.get(`/recheck-batches/${id}`),
  create: (data) => http.post('/recheck-batches', data),
  register: (id, itemId, data) => http.post(`/recheck-batches/${id}/items/${itemId}/register`, data),
  submit: (id) => http.post(`/recheck-batches/${id}/submit`),
  review: (id, data) => http.post(`/recheck-batches/${id}/review`, data)
}
