import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

/**
 * RecheckView 接线回归：定检复查批次页面必须把四条核心规则接到后端动作上。
 *
 * 不挂载 Vue（环境无 node_modules），直接校验 SFC 源码关键接线：
 * - 到期清单来自 /recheck-batches/due-devices，勾选设备跨五类建批
 * - 提交/通过前走 validateBatchBeforeSubmit（缺结论、里程越界、重复占用拦截）
 * - 退回必须带原因；退回后可在同一批次整改重提
 * - 通过调用 review APPROVE，回写台账后刷新列表与总览
 */
const here = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(join(here, '..', 'views', 'RecheckView.vue'), 'utf8')

test('页面引入 recheckApi 与规则工具，且路由/导航已挂载', () => {
  assert.match(source, /import\s*\{[^}]*recheckApi[^}]*\}\s*from\s*['"]\.\.\/api['"]/)
  assert.match(source, /validateBatchBeforeSubmit/)
  const router = readFileSync(join(here, '..', 'router', 'index.js'), 'utf8')
  assert.match(router, /path:\s*'\/rechecks'/)
  assert.match(router, /RecheckView\.vue/)
})

test('到期设备走 due-devices 汇总，并按区段加载（跨类型勾选）', () => {
  assert.match(source, /recheckApi\.dueDevices\(/)
  assert.match(source, /onPickSection/)
  // 勾选集合用于建批入参 deviceIds
  assert.match(source, /deviceIds:\s*selectedDue\.value/)
})

test('提交前必须通过 validateBatchBeforeSubmit 校验（缺结论/里程越界/重复占用）', () => {
  assert.match(source, /const check = validateBatchBeforeSubmit\(batchForValidate\(batch\)\)/)
  assert.match(source, /if\s*\(!check\.ok\)/)
})

test('逐台登记调用 register，提交与整改重提都走 submit 接口', () => {
  assert.match(source, /recheckApi\.register\(detail\.value\.id,\s*actItem\.value\.id/)
  assert.match(source, /recheckApi\.submit\(batch\.id\)/)
  // 已退回状态仍显示提交按钮（整改重提）
  assert.match(source, /detail\.status === 'DRAFT' \|\| detail\.status === 'REJECTED'/)
})

test('退回强制填写原因，通过与退回都调用 review 接口并在通过后刷新台账/总览', () => {
  assert.match(source, /action === 'RETURN'[\s\S]*?reviewNote[\s\S]*?退回必须填写退回原因/)
  assert.match(source, /recheckApi\.review\(reviewData\.value\.id/)
  assert.match(source, /refreshAll\(\)/)
  const api = readFileSync(join(here, '..', 'api', 'index.js'), 'utf8')
  assert.match(api, /\/recheck-batches\/\$\{id\}\/items\/\$\{itemId\}\/register/)
  assert.match(api, /\/recheck-batches\/\$\{id\}\/submit/)
  assert.match(api, /\/recheck-batches\/\$\{id\}\/review/)
})

test('列表展示进度与逾期项（progress / overdueCount），并提供待复核快捷入口', () => {
  assert.match(source, /row\.progress/)
  assert.match(source, /row\.overdueCount/)
  assert.match(source, /row\.status === 'PENDING'[\s\S]*?去复核/)
})
