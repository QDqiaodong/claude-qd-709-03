/**
 * 病害清单查询参数拼装（检查与病害页）。
 *
 * 三个条件彼此独立、可任意组合（AND 交集）：
 * - sectionId：当前左侧区段；未选区段（null/undefined）时不带该参数，后端返回全线路病害
 * - severity：严重等级；清空（'' / null / undefined）只移除该条件
 * - closed：销号状态；只接受布尔值，未选择或清空时移除——
 *           注意不能用真值判断，false（未销号）是合法且必须下发的条件
 */
export function buildDefectParams({ sectionId = null, severity = '', closed = '' } = {}) {
  const params = {}
  if (sectionId !== null && sectionId !== undefined && sectionId !== '') {
    params.sectionId = sectionId
  }
  if (severity !== null && severity !== undefined && severity !== '') {
    params.severity = severity
  }
  if (closed === true || closed === false) {
    params.closed = closed
  }
  return params
}
