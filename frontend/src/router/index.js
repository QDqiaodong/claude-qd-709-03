import { createRouter, createWebHistory } from 'vue-router'

/**
 * 导航形态：左侧一栏是「当前线路的区段列表」，模块本身是右侧顶部一条横向胶囊条。
 * 线路上下文由顶栏下拉切换，区段由左侧列表点选，两个模块之间共享同一段线路。
 */
export const navItems = [
  { key: 'board', path: '/board', icon: '🛤️', label: '线路里程看板', hint: '公里标轴 + 区段色条 + 病害三角标记' },
  { key: 'sections', path: '/sections', icon: '📍', label: '线路与区段', hint: '线路里程·区段编号·轨型·道床·状态' },
  { key: 'devices', path: '/devices', icon: '🔩', label: '设备台账', hint: '钢轨/道岔/轨枕/桥隧 多态继承' },
  { key: 'defects', path: '/defects', icon: '🚧', label: '检查与病害', hint: '病害等级筛选·销号闭环' },
  { key: 'orders', path: '/orders', icon: '🗓️', label: '养修作业', hint: '天窗点网格日历·状态推进' },
  { key: 'handovers', path: '/handovers', icon: '🔓', label: '封锁解除交接台', hint: '已验收作业解除申请·逐项复核·失效重提' },
  { key: 'rechecks', path: '/rechecks', icon: '📋', label: '定检复查批次', hint: '90天到期设备跨类勾选·逐台登记·复核回写台账' },
  { key: 'materials', path: '/materials', icon: '📦', label: '物资与机具', hint: '低库存预警·检修倒计时' }
]

const routes = [
  { path: '/', redirect: '/board' },
  { path: '/board', name: 'board', component: () => import('../views/BoardView.vue') },
  { path: '/sections', name: 'sections', component: () => import('../views/SectionView.vue') },
  { path: '/devices', name: 'devices', component: () => import('../views/DeviceView.vue') },
  { path: '/defects', name: 'defects', component: () => import('../views/DefectView.vue') },
  { path: '/orders', name: 'orders', component: () => import('../views/WorkOrderView.vue') },
  { path: '/handovers', name: 'handovers', component: () => import('../views/HandoverView.vue') },
  { path: '/rechecks', name: 'rechecks', component: () => import('../views/RecheckView.vue') },
  { path: '/materials', name: 'materials', component: () => import('../views/MaterialView.vue') },
  { path: '/:pathMatch(.*)*', redirect: '/board' }
]

export default createRouter({
  history: createWebHistory(),
  routes
})
