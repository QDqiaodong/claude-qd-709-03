import { test } from 'node:test'
import assert from 'node:assert/strict'

import { buildDefectParams } from './defectQuery.js'

/**
 * 检查与病害页 - 病害清单筛选联动回归验证。
 *
 * 覆盖：区段切换、三条件组合、清空单项互不影响、未选区段查全线路、
 * 以及“未销号(false) 不能被当作空条件丢掉”这一历史坑点。
 */

test('未选择区段且无其他筛选：不带任何参数，展示全线路病害', () => {
  assert.deepEqual(buildDefectParams({ sectionId: null, severity: '', closed: '' }), {})
  assert.deepEqual(buildDefectParams({ sectionId: undefined }), {})
  assert.deepEqual(buildDefectParams(), {})
})

test('切换到区段：带上 sectionId，病害清单只查当前区段', () => {
  assert.deepEqual(buildDefectParams({ sectionId: 7, severity: '', closed: '' }), { sectionId: 7 })
})

test('区段 + 严重等级 + 销号状态三条件组合查询（AND 交集）', () => {
  assert.deepEqual(
    buildDefectParams({ sectionId: 7, severity: 'HEAVY', closed: false }),
    { sectionId: 7, severity: 'HEAVY', closed: false }
  )
  assert.deepEqual(
    buildDefectParams({ sectionId: 7, severity: 'LIGHT', closed: true }),
    { sectionId: 7, severity: 'LIGHT', closed: true }
  )
})

test('未选区段时仍可按严重等级/销号状态组合筛选全线路病害', () => {
  assert.deepEqual(
    buildDefectParams({ sectionId: null, severity: 'MIDDLE', closed: false }),
    { severity: 'MIDDLE', closed: false }
  )
})

test('清空区段只移除 sectionId，不影响严重等级与销号状态', () => {
  const before = buildDefectParams({ sectionId: 7, severity: 'HEAVY', closed: false })
  const after = buildDefectParams({ sectionId: null, severity: before.severity, closed: before.closed })
  assert.deepEqual(after, { severity: 'HEAVY', closed: false })
})

test('清空严重等级只移除 severity，区段与销号状态保持不变', () => {
  const after = buildDefectParams({ sectionId: 7, severity: '', closed: true })
  assert.deepEqual(after, { sectionId: 7, closed: true })
})

test('清空销号状态只移除 closed，区段与严重等级保持不变', () => {
  const after = buildDefectParams({ sectionId: 7, severity: 'HEAVY', closed: '' })
  assert.deepEqual(after, { sectionId: 7, severity: 'HEAVY' })
  assert.deepEqual(
    buildDefectParams({ sectionId: 7, severity: 'HEAVY', closed: null }),
    { sectionId: 7, severity: 'HEAVY' }
  )
})

test('销号状态切到“未销号”时 false 必须作为有效条件下发', () => {
  const params = buildDefectParams({ sectionId: 3, severity: '', closed: false })
  assert.equal(Object.hasOwn(params, 'closed'), true)
  assert.equal(params.closed, false)
})

test('切换区段后登记病害/销号刷新：重新拼装的参数沿用当前区段与筛选条件', () => {
  // 模拟用户：区段 3 + 重级 + 未销号，操作后复用原条件刷新
  const conditions = { sectionId: 3, severity: 'HEAVY', closed: false }
  assert.deepEqual(buildDefectParams(conditions), { sectionId: 3, severity: 'HEAVY', closed: false })
  // 再切到区段 9，其余条件保持
  assert.deepEqual(
    buildDefectParams({ ...conditions, sectionId: 9 }),
    { sectionId: 9, severity: 'HEAVY', closed: false }
  )
})
