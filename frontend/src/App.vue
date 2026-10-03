<template>
  <div class="shell">
    <!-- 顶部：品牌 + 全局线路下拉 + 工区计数器 -->
    <header class="topbar">
      <div class="brand">
        <span class="brand-mark">🛤️</span>
        <div>
          铁路工务工区线路养修管理平台
          <div class="brand-sub">
            {{ todayText }} · 区段 {{ sectionList.length }} 个 · 未销号病害 {{ overview.defectOpen || 0 }} 处
          </div>
        </div>
      </div>

      <div class="line-picker">
        <span class="line-label">当前线路</span>
        <el-select v-model="lineId" filterable placeholder="选择线路" @change="onPickLine">
          <el-option v-for="l in lineList" :key="l.id" :label="l.name + ' · ' + l.startStation + '-' + l.endStation" :value="l.id">
            <span>{{ l.name }}</span>
            <span style="float: right; font-size: 12px; opacity: 0.7">{{ l.mileageKm }}km</span>
          </el-option>
        </el-select>
      </div>

      <div class="top-stats">
        <div class="stat-chip"><b>{{ overview.deviceCount || 0 }}</b><span>设备</span></div>
        <div class="stat-chip"><b>{{ overview.defectOpen || 0 }}</b><span>未销号</span></div>
        <div class="stat-chip" :class="{ 'is-alert': (overview.defectHeavy || 0) > 0 }">
          <b>{{ overview.defectHeavy || 0 }}</b><span>重级病害</span>
        </div>
        <div class="stat-chip"><b>{{ overview.workOrderActive || 0 }}</b><span>在修作业</span></div>
        <div class="stat-chip" :class="{ 'is-alert': (overview.materialLow || 0) > 0 }">
          <b>{{ overview.materialLow || 0 }}</b><span>低库存</span>
        </div>
        <div class="stat-chip" :class="{ 'is-alert': (overview.equipmentDue || 0) > 0 }">
          <b>{{ overview.equipmentDue || 0 }}</b><span>待检修</span>
        </div>
      </div>
    </header>

    <div class="body-row">
      <!-- 左侧：当前线路的区段列表，点击即切换全局区段上下文 -->
      <aside class="sidenav">
        <h4>区段列表</h4>
        <button
          v-for="s in sectionList"
          :key="s.id"
          class="sec-item"
          :class="{ 'is-on': s.id === sectionId }"
          type="button"
          @click="sectionId = s.id"
        >
          <span class="sec-no">
            <i class="sec-dot" :style="{ background: barColor(s) }"></i>{{ s.sectionNo }}
            <span class="muted" style="font-weight: 400; margin-left: auto">{{ s.statusName }}</span>
          </span>
          <span class="sec-mile">{{ s.startMileText }} ~ {{ s.endMileText }} · {{ s.lengthKm }}km</span>
          <span class="sec-mile">病害 {{ s.defectCount }} 处 / 未销号 {{ s.openDefectCount }} 处</span>
        </button>
        <div v-if="!sectionList.length" class="muted" style="font-size: 12px">该线路暂无区段</div>
      </aside>

      <div class="main-col">
        <nav class="navbar">
          <button
            v-for="item in navItems"
            :key="item.key"
            class="nav-btn"
            :class="{ 'is-on': activeKey === item.key }"
            type="button"
            :title="item.hint"
            @click="go(item.path)"
          >
            <span>{{ item.icon }}</span>{{ item.label }}
          </button>
        </nav>

        <main class="stage">
          <router-view v-if="lineId" />
        </main>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, getCurrentInstance, onMounted, provide, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import router, { navItems } from './router'
import { lineApi, worksApi } from './api'

// 模板自带的 main.js 没装 router（那份文件不动），在这里装到根实例上
getCurrentInstance().appContext.app.use(router)

const route = useRoute()
const nav = useRouter()

const lineList = ref([])
const lineId = ref(null)
const sectionList = ref([])
const sectionId = ref(null)
const board = ref({ sections: [], defects: [] })
const overview = ref({})

/**
 * 全局上下文：顶栏切线路、左栏点区段，六个模块共享同一段线路/区段。
 */
const works = {
  lineId,
  lineList,
  sectionId,
  sectionList,
  board,
  overview,
  reloadBoard: loadBoard,
  reloadOverview: loadOverview,
  barColor
}
provide('works', works)

const todayText = computed(() => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
})

const activeKey = computed(() => {
  const hit = navItems.find((item) => route.path.startsWith(item.path))
  return hit ? hit.key : 'board'
})

function go(path) {
  nav.push(path)
}

/** 色条取色：封锁深灰 > 施工中蓝 > 病害等级（重红/中橙/轻黄） > 正常绿 */
function barColor(section) {
  if (section.status === 'BLOCKED') return '#4a5b63'
  if (section.status === 'SPEED_LIMIT') return '#1565c0'
  const level = section.worstLevel
  if (level === 'HEAVY') return '#c62828'
  if (level === 'MIDDLE') return '#e08700'
  if (level === 'LIGHT') return '#e8c33d'
  return '#2e9e5b'
}

function onPickLine(id) {
  localStorage.setItem('rw-line-id', String(id == null ? '' : id))
}

async function loadLines() {
  lineList.value = await lineApi.list()
  if (!lineId.value) {
    const saved = Number(localStorage.getItem('rw-line-id'))
    const hit = lineList.value.find((l) => l.id === saved && l.sectionCount > 0)
    const first = lineList.value.find((l) => l.sectionCount > 0)
    lineId.value = hit ? hit.id : first ? first.id : lineList.value.length ? lineList.value[0].id : null
  }
}

async function loadBoard() {
  if (!lineId.value) return
  board.value = await lineApi.board(lineId.value)
  sectionList.value = board.value.sections || []
  if (!sectionId.value || !sectionList.value.some((s) => s.id === sectionId.value)) {
    sectionId.value = sectionList.value.length ? sectionList.value[0].id : null
  }
}

async function loadOverview() {
  overview.value = await worksApi.overview()
}

watch(lineId, async () => {
  sectionId.value = null
  await loadBoard()
  await loadOverview()
})

onMounted(async () => {
  await loadLines()
  await loadBoard()
  await loadOverview()
})
</script>
