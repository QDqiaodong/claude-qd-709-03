import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  RECHECK_CYCLE_DAYS,
  buildRecheckParams,
  selectDueDevices,
  selectedTypeCodes,
  isItemRegistered,
  itemSubmitProblems,
  validateBatchBeforeSubmit,
  batchProgress,
  applyApprovedBatch,
  canResubmit,
  canReview
} from './recheckQuery.js'

/**
 * 定检复查批次业务规则测试，对应需求点：
 * - 跨类型选择：钢轨/道岔/轨枕/桥梁/隧道可在同一批次混合勾选
 * - 重复占用：被其他未完成批次占用的设备禁止勾选/提交
 * - 退回重提：退回保留明细与原因，补齐后再次提交
 * - 批量更新：复核通过后按计划日期与登记状态回写台账
 */

const sectionGX05 = { id: 5, sectionNo: 'GX-05', startMile: 41.6, endMile: 50.0 }

const dueDevices = [
  { deviceId: 11, deviceNo: 'RAIL-GX-05-01', deviceType: 'RAIL', sectionId: 5, ledgerOverdue: true, occupied: false },
  { deviceId: 31, deviceNo: 'SLP-GX-05-01', deviceType: 'SLEEPER', sectionId: 5, ledgerOverdue: true, occupied: false },
  { deviceId: 38, deviceNo: 'BRG-GX-05-01', deviceType: 'BRIDGE', sectionId: 5, ledgerOverdue: true, occupied: false },
  { deviceId: 44, deviceNo: 'TUN-GX-05-01', deviceType: 'TUNNEL', sectionId: 5, ledgerOverdue: true, occupied: false },
  { deviceId: 45, deviceNo: 'TUN-GX-05-02', deviceType: 'TUNNEL', sectionId: 5, ledgerOverdue: true,
    occupied: true, occupiedBatchId: 99, occupiedBatchNo: 'RB-202610-0009' },
  { deviceId: 21, deviceNo: 'SW-GX-05-01', deviceType: 'SWITCH', sectionId: 5, ledgerOverdue: false, occupied: false }
]

function item(over) {
  return {
    id: 1,
    deviceId: 11,
    deviceNo: 'RAIL-GX-05-01',
    sectionId: 5,
    planDate: '2026-10-05',
    conclusion: 'OK',
    measuredMileage: 43.9,
    deviceStatus: 'GOOD',
    remark: '',
    ...over
  }
}

// ---------------- 筛选参数 ----------------

test('buildRecheckParams 只下发非空的线路/区段/状态条件', () => {
  assert.deepEqual(buildRecheckParams({ lineId: 1, sectionId: 5, status: 'PENDING' }),
    { lineId: 1, sectionId: 5, status: 'PENDING' })
  assert.deepEqual(buildRecheckParams({ lineId: null, sectionId: null, status: '' }), {})
  assert.deepEqual(buildRecheckParams({ sectionId: 5 }), { sectionId: 5 })
})

test('定检周期固定为 90 天', () => {
  assert.equal(RECHECK_CYCLE_DAYS, 90)
})

// ---------------- 跨类型选择 ----------------

test('同一批次可跨钢轨/道岔/轨枕/桥梁/隧道五类混合勾选', () => {
  let selected = []
  for (const id of [11, 31, 38, 44, 21]) {
    selected = selectDueDevices(dueDevices, id, selected)
  }
  assert.deepEqual([...selected].sort((a, b) => a - b), [11, 21, 31, 38, 44])
  const codes = selectedTypeCodes(dueDevices, selected)
  assert.deepEqual(codes.sort(), ['BRIDGE', 'RAIL', 'SLEEPER', 'SWITCH', 'TUNNEL'])
})

test('再次点击已选设备可取消勾选', () => {
  let selected = selectDueDevices(dueDevices, 11, [])
  selected = selectDueDevices(dueDevices, 31, selected)
  selected = selectDueDevices(dueDevices, 11, selected)
  assert.deepEqual(selected, [31])
})

// ---------------- 重复占用 ----------------

test('已被其他未完成批次占用的设备禁止勾选（置灰来源批次号）', () => {
  const selected = selectDueDevices(dueDevices, 45, [11])
  assert.deepEqual(selected, [11])
  const occupied = dueDevices.find((d) => d.deviceId === 45)
  assert.equal(occupied.occupied, true)
  assert.equal(occupied.occupiedBatchNo, 'RB-202610-0009')
})

test('清单中未知设备 id 的勾选不产生副作用', () => {
  assert.deepEqual(selectDueDevices(dueDevices, 999, [11]), [11])
})

test('提交时设备被其他未完成批次占用会被拦截，但本批次自身占用不算冲突', () => {
  const section = sectionGX05
  const byOther = item({ occupiedBatchId: 99, occupiedBatchNo: 'RB-202610-0009' })
  const problems = itemSubmitProblems(byOther, section, 1)
  assert.equal(problems.length, 1)
  assert.match(problems[0], /已被未完成批次 RB-202610-0009 占用/)

  const bySelf = item({ occupiedBatchId: 1 })
  assert.deepEqual(itemSubmitProblems(bySelf, section, 1), [])
})

// ---------------- 逐台登记 / 里程越界 / 缺结论 ----------------

test('结论、实测里程、设备状态三项齐全才算登记完成', () => {
  assert.equal(isItemRegistered(item()), true)
  assert.equal(isItemRegistered(item({ conclusion: '' })), false)
  assert.equal(isItemRegistered(item({ measuredMileage: null })), false)
  assert.equal(isItemRegistered(item({ deviceStatus: '' })), false)
  assert.equal(isItemRegistered(null), false)
})

test('实测里程超出所属区段里程区间时禁止提交（边界值含端点）', () => {
  const over = item({ measuredMileage: 50.001 })
  const problems = itemSubmitProblems(over, sectionGX05, 1)
  assert.equal(problems.length, 1)
  assert.match(problems[0], /实测里程超出区段 GX-05/)

  assert.deepEqual(itemSubmitProblems(item({ measuredMileage: 41.6 }), sectionGX05, 1), [])
  assert.deepEqual(itemSubmitProblems(item({ measuredMileage: 50.0 }), sectionGX05, 1), [])
})

test('缺少结论/实测里程/设备状态逐台给出阻断原因', () => {
  const problems = itemSubmitProblems(item({ conclusion: '', measuredMileage: null, deviceStatus: '' }), sectionGX05, 1)
  assert.equal(problems.length, 3)
  assert.match(problems.join('；'), /缺少复查结论/)
  assert.match(problems.join('；'), /缺少实测里程/)
  assert.match(problems.join('；'), /缺少复查后设备状态/)
})

test('批次明细为空或存在问题台时整体不可提交', () => {
  const empty = validateBatchBeforeSubmit({ id: 1, items: [] })
  assert.equal(empty.ok, false)
  assert.match(empty.problems[0], /没有设备明细/)

  const bad = validateBatchBeforeSubmit({
    id: 1,
    sectionNo: 'GX-05',
    startMile: 41.6,
    endMile: 50.0,
    items: [item(), item({ id: 2, deviceId: 12, deviceNo: 'X', conclusion: '', measuredMileage: null, deviceStatus: '' })]
  })
  assert.equal(bad.ok, false)
  assert.equal(bad.problems.length, 3) // 缺结论/里程/状态
})

test('全部明细登记合规时批次可提交', () => {
  const res = validateBatchBeforeSubmit({
    id: 1,
    section: sectionGX05,
    items: [
      item(),
      item({ id: 2, deviceId: 44, deviceNo: 'TUN-GX-05-01', conclusion: 'NG', measuredMileage: 44.8, deviceStatus: 'FAULT' })
    ]
  })
  assert.equal(res.ok, true)
  assert.deepEqual(res.problems, [])
})

// ---------------- 进度 / 逾期 ----------------

test('batchProgress 按已登记台数计算百分比', () => {
  assert.equal(batchProgress({ totalCount: 4, registeredCount: 1 }), 25)
  assert.equal(batchProgress({ totalCount: 0 }), 0)
  assert.equal(batchProgress({ items: [item(), item({ conclusion: '' })] }), 50)
})

// ---------------- 退回重提 ----------------

test('只有已退回批次允许重提，待复核/已通过/草稿均不允许', () => {
  assert.equal(canResubmit({ status: 'REJECTED' }), true)
  for (const s of ['DRAFT', 'PENDING', 'APPROVED']) {
    assert.equal(canResubmit({ status: s }), false)
  }
})

test('退回保留全部登记明细：退回批次的明细仍带结论/里程/状态与退回原因', () => {
  const rejected = {
    id: 7,
    status: 'REJECTED',
    reviewNote: '隧道实测里程与台账偏差大，重新测量',
    items: [
      item(),
      item({ id: 2, conclusion: 'NG', measuredMileage: 44.8, deviceStatus: 'FAULT', remark: '照明故障' })
    ]
  }
  assert.equal(canResubmit(rejected), true)
  assert.equal(rejected.items.length, 2)
  assert.ok(rejected.items.every((i) => i.conclusion && i.measuredMileage && i.deviceStatus))
  assert.match(rejected.reviewNote, /重新测量/)
  // 整改其中一台后整体应可再次提交
  rejected.items[1].measuredMileage = 44.82
  const res = validateBatchBeforeSubmit({ ...rejected, section: sectionGX05 })
  assert.equal(res.ok, true)
})

test('复核动作仅对待复核批次开放', () => {
  assert.equal(canReview({ status: 'PENDING' }), true)
  assert.equal(canReview({ status: 'REJECTED' }), false)
  assert.equal(canReview({ status: 'APPROVED' }), false)
})

// ---------------- 通过后批量更新台账 ----------------

test('复核通过后批量回写每台设备的上次检查日期与状态（按各台计划日期）', () => {
  const devices = [
    { id: 11, deviceNo: 'RAIL-GX-05-01', lastCheckDate: '2026-05-20', status: 'WATCH' },
    { id: 44, deviceNo: 'TUN-GX-05-01', lastCheckDate: '2026-05-20', status: 'WATCH' },
    { id: 99, deviceNo: 'NOT-IN-BATCH', lastCheckDate: '2026-01-01', status: 'FAULT' }
  ]
  const batch = {
    id: 1,
    status: 'APPROVED',
    items: [
      { deviceId: 11, planDate: '2026-10-03', deviceStatus: 'GOOD', conclusion: 'OK' },
      { deviceId: 44, planDate: '2026-10-03', deviceStatus: 'FAULT', conclusion: 'NG' }
    ]
  }
  const updated = applyApprovedBatch(devices, batch)
  assert.deepEqual(updated[0], { id: 11, deviceNo: 'RAIL-GX-05-01', lastCheckDate: '2026-10-03', status: 'GOOD' })
  assert.deepEqual(updated[1], { id: 44, deviceNo: 'TUN-GX-05-01', lastCheckDate: '2026-10-03', status: 'FAULT' })
  // 不在批次内的设备台账保持不变
  assert.deepEqual(updated[2], devices[2])
  // 纯函数：不改原数组
  assert.equal(devices[0].lastCheckDate, '2026-05-20')
  assert.equal(devices[0].status, 'WATCH')
})

test('空批次通过时台账无任何变化', () => {
  const devices = [{ id: 1, status: 'GOOD' }]
  assert.deepEqual(applyApprovedBatch(devices, { items: [] }), devices)
})
