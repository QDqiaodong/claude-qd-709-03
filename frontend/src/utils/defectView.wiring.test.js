import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

/**
 * DefectView 接线回归：防止病害查询再次与左侧区段脱钩。
 *
 * 不挂载 Vue（环境无 node_modules），直接校验 SFC 源码中的关键接线：
 * - loadDefects 必须把全局区段上下文 works.sectionId 传给参数拼装
 * - 切换区段的 watcher 必须同时刷新检查单与病害清单（两者联动）
 * - 登记病害、销号后的刷新必须走同一个 loadDefects（保持当前筛选条件）
 */
const here = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(join(here, '..', 'views', 'DefectView.vue'), 'utf8')

test('loadDefects 引入并使用 buildDefectParams 拼装筛选参数', () => {
  assert.match(source, /import\s*\{\s*buildDefectParams\s*\}\s*from\s*['"]\.\.\/utils\/defectQuery['"]/)
  assert.match(source, /buildDefectParams\(/)
})

test('病害查询携带当前区段、严重等级、销号状态三个条件', () => {
  const call = source.match(/buildDefectParams\(\{[\s\S]*?\}\)/)
  assert.ok(call, '应通过 buildDefectParams({...}) 传参')
  assert.match(call[0], /sectionId:\s*works\.sectionId\.value/)
  assert.match(call[0], /severity:\s*severity\.value/)
  assert.match(call[0], /closed:\s*closed\.value/)
})

test('切换左侧区段时检查单与病害清单一并刷新（联动回归）', () => {
  const watcher = source.match(/watch\(\s*\(\)\s*=>\s*works\.sectionId\.value[\s\S]*?\}\)/)
  assert.ok(watcher, '应 watch works.sectionId')
  assert.match(watcher[0], /loadInspections\(\)/)
  assert.match(watcher[0], /loadDefects\(\)/)
})

test('登记病害与销号后通过 loadDefects 刷新，沿用当前区段与筛选条件', () => {
  assert.match(source, /doDefectCreate[\s\S]*?loadDefects\(\)/)
  assert.match(source, /doClose[\s\S]*?loadDefects\(\)/)
})
