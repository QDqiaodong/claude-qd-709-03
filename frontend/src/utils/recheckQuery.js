/**
 * 定检复查批次：纯前端业务规则（与后端 RecheckService 同一套口径）。
 *
 * 抽成无副作用函数，既能给页面复用，也能在 node:test 下直接覆盖：
 * - 跨类型勾选：到期清单可同时选钢轨/道岔/轨枕/桥梁/隧道，占用设备禁选
 * - 重复占用：一台设备同时只能被一个未完成（草稿/待复核/已退回）批次占用
 * - 退回重提：退回保留明细，补齐后可再次提交
 * - 批量更新：复核通过后按各台计划日期回写台账上次检查日期与登记状态
 */

export const RECHECK_ACTIVE_STATUS = ['DRAFT', 'PENDING', 'REJECTED']
export const RECHECK_CYCLE_DAYS = 90

export function isActiveBatch(status) {
  return RECHECK_ACTIVE_STATUS.includes(status)
}

/** 列表筛选参数拼装：线路 / 区段 / 批次状态，空值不下发 */
export function buildRecheckParams({ lineId = null, sectionId = null, status = '' } = {}) {
  const params = {}
  if (lineId !== null && lineId !== undefined && lineId !== '') params.lineId = lineId
  if (sectionId !== null && sectionId !== undefined && sectionId !== '') params.sectionId = sectionId
  if (status !== null && status !== undefined && status !== '') params.status = status
  return params
}

/**
 * 到期设备勾选口径（跨类型）：
 * - 只允许勾选“未被其他未完成批次占用”的设备
 * - 已占用设备在清单中置灰，并用 occupiedBatchNo 提示占用来源
 * 传入已选 id 集合时保持其勾选态（用于回显），返回最终应勾选的 id 列表
 */
export function selectDueDevices(dueDevices, nextId, selectedIds = []) {
  const selected = new Set(selectedIds)
  const device = dueDevices.find((d) => d.deviceId === nextId)
  if (!device) return [...selected]
  if (device.occupied) return [...selected] // 被其他未完成批次占用，禁止勾选
  if (selected.has(nextId)) selected.delete(nextId)
  else selected.add(nextId)
  return [...selected]
}

/** 已选设备跨了哪几种类型（去重，返回类型码） */
export function selectedTypeCodes(dueDevices, selectedIds) {
  const ids = new Set(selectedIds)
  const codes = new Set()
  for (const d of dueDevices) {
    if (ids.has(d.deviceId)) codes.add(d.deviceType)
  }
  return [...codes]
}

/**
 * 逐台登记完整性：结论/实测里程/设备状态三项齐全才算登记完成。
 * 缺任一项的设备不允许提交。
 */
export function isItemRegistered(item) {
  return !!item
    && !!item.conclusion
    && item.measuredMileage !== null && item.measuredMileage !== undefined && item.measuredMileage !== ''
    && !!item.deviceStatus
}

/**
 * 单台设备提交阻断问题：
 * 1) 缺少结论（实测里程/状态缺失一并提示，登记接口要求三项齐全）
 * 2) 实测里程超出所属区段里程 [startMile, endMile]
 * 3) 设备被其他未完成批次占用（占用批次 id 不等于当前批次）
 * 返回问题文案数组，空数组表示该台可提交。
 */
export function itemSubmitProblems(item, section, currentBatchId) {
  const problems = []
  const label = item.deviceNo || `设备#${item.deviceId}`
  if (!item.conclusion) problems.push(`${label} 缺少复查结论`)
  if (item.measuredMileage === null || item.measuredMileage === undefined || item.measuredMileage === '') {
    problems.push(`${label} 缺少实测里程`)
  }
  if (!item.deviceStatus) problems.push(`${label} 缺少复查后设备状态`)
  if (section && item.measuredMileage !== null && item.measuredMileage !== undefined && item.measuredMileage !== '') {
    const m = Number(item.measuredMileage)
    if (Number.isFinite(m) && (m < Number(section.startMile) || m > Number(section.endMile))) {
      problems.push(`${label} 实测里程超出区段 ${section.sectionNo}`)
    }
  }
  if (item.occupiedBatchId && item.occupiedBatchId !== currentBatchId) {
    problems.push(`${label} 已被未完成批次 ${item.occupiedBatchNo || '#' + item.occupiedBatchId} 占用，不能重复提交`)
  }
  return problems
}

/**
 * 批次提交前整体校验：明细不能为空，且逐台都没有阻断问题。
 * 返回 { ok, problems }。
 */
export function validateBatchBeforeSubmit(batch) {
  const items = (batch && batch.items) || []
  if (!items.length) return { ok: false, problems: ['批次内没有设备明细，无法提交'] }
  const section = batch.section ? batch.section : {
    sectionNo: batch.sectionNo,
    startMile: batch.startMile,
    endMile: batch.endMile
  }
  const problems = []
  for (const item of items) {
    problems.push(...itemSubmitProblems(item, section, batch.id))
  }
  return { ok: problems.length === 0, problems }
}

/** 批次进度：已登记（有结论）台数 / 总台数，返回 0~100 */
export function batchProgress(batch) {
  const total = (batch && batch.totalCount) || ((batch && batch.items) || []).length
  if (!total) return 0
  const registered = batch.registeredCount !== undefined
    ? batch.registeredCount
    : (batch.items || []).filter((i) => !!i.conclusion).length
  return Math.round((registered * 100) / total)
}

/**
 * 复核通过后的台账批量更新（纯函数，返回更新后的设备列表，不改入参）：
 * 每台设备 lastCheckDate = 该台计划日期，status = 登记状态。
 * 用于前端通过后即时刷新台账视图，真正持久化以后端事务为准。
 */
export function applyApprovedBatch(devices, batch) {
  const byId = new Map((batch.items || []).map((i) => [i.deviceId, i]))
  return devices.map((d) => {
    const item = byId.get(d.id)
    if (!item) return d
    return { ...d, lastCheckDate: item.planDate, status: item.deviceStatus }
  })
}

/** 退回重提：仅允许已退回批次重提；提交动作清空旧复核结论但保留全部登记明细 */
export function canResubmit(batch) {
  return !!batch && batch.status === 'REJECTED'
}

/** 复核动作是否允许（仅待复核批次可通过/退回） */
export function canReview(batch) {
  return !!batch && batch.status === 'PENDING'
}
