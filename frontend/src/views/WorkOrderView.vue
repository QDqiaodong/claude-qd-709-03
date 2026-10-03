<template>
  <div>
    <section class="panel">
      <div class="panel-head">
        <h3>天窗点排布（日 × 时段网格）</h3>
        <span class="panel-sub">一行一日期、一格半小时；00:00 — 06:00 共 12 格，鼠标悬停看作业单</span>
      </div>

      <div class="window-grid">
        <div class="wg-cell wg-head">日期 \ 时段</div>
        <div v-for="s in slots" :key="'h' + s.index" class="wg-cell wg-head">{{ s.label }}</div>

        <template v-for="d in dates" :key="d">
          <div class="wg-cell wg-date">{{ d }}</div>
          <div v-for="s in slots" :key="d + '-' + s.index" class="wg-cell">
            <template v-if="hit(d, s)">
              <div class="wg-slot" :style="{ background: colorOf(hit(d, s).status), flex: 1 }" :title="titleOf(hit(d, s))">
                {{ labelOf(d, s) }}
              </div>
            </template>
            <span v-else class="wg-free">·</span>
          </div>
        </template>
      </div>

      <div class="mile-legend" style="margin-top: 10px">
        <span><i style="background: #90a4ae"></i>计划</span>
        <span><i style="background: #1565c0"></i>已批复</span>
        <span><i style="background: #00838f"></i>施工中</span>
        <span><i style="background: #2e9e5b"></i>已验收</span>
        <span><i style="background: #b0bec5"></i>已销号</span>
      </div>
    </section>

    <section class="panel">
      <div class="panel-head">
        <h3>养修作业单</h3>
        <span class="panel-sub">计划 → 已批复 → 施工中 → 已验收；已验收后须到「封锁解除交接台」复核销号</span>
        <div class="panel-extra">
          <el-button size="small" @click="load">刷新</el-button>
          <el-button size="small" type="primary" @click="openCreate">新增作业单</el-button>
        </div>
      </div>
      <el-table :data="list" border stripe size="small" :row-class-name="rowClass">
        <el-table-column prop="orderNo" label="作业单号" width="140" />
        <el-table-column prop="sectionNo" label="区段" width="86" />
        <el-table-column prop="lineName" label="线路" width="110" />
        <el-table-column prop="workItemName" label="作业项目" width="100" />
        <el-table-column prop="windowDate" label="天窗日期" width="110" />
        <el-table-column label="天窗时段" width="130">
          <template #default="{ row }">{{ row.startTime }} ~ {{ row.endTime }}</template>
        </el-table-column>
        <el-table-column prop="windowMinutes" label="时长(分)" width="90" />
        <el-table-column prop="leader" label="负责人" width="90" />
        <el-table-column prop="workerCount" label="人数" width="70" />
        <el-table-column prop="cooperateUnit" label="配合单位" width="150" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="tagType(row.status)">{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'CHECKED'">
              <el-button link type="warning" size="small" @click="goHandover(row)">
                {{ row.activeHandoverId ? '交接申请：' + row.activeHandoverStatusName : '发起封锁解除交接' }}
              </el-button>
              <span class="muted" style="font-size: 12px">已验收不可直接销号</span>
            </template>
            <el-button v-else-if="row.status !== 'CLOSED'" link type="primary" @click="advance(row)">
              推进至{{ row.nextStatusName }}
            </el-button>
            <span v-else class="muted">已终结</span>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="createVisible" title="新增养修作业单" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="作业单号"><el-input v-model="form.orderNo" placeholder="如 WO-2026-0013" /></el-form-item>
        <el-form-item label="区段">
          <el-select v-model="form.sectionId" style="width: 100%">
            <el-option v-for="s in works.sectionList.value" :key="s.id" :label="s.sectionNo" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="作业项目">
          <el-select v-model="form.workItem" style="width: 100%">
            <el-option label="起道" value="LIFT" /><el-option label="拨道" value="ALIGN" />
            <el-option label="捣固" value="TAMP" /><el-option label="换轨" value="RAIL_CHANGE" />
            <el-option label="清筛" value="SCREEN" /><el-option label="打磨" value="GRIND" />
            <el-option label="换枕" value="SLEEPER_CHANGE" /><el-option label="应力放散" value="STRESS" />
          </el-select>
        </el-form-item>
        <el-form-item label="天窗日期"><el-date-picker v-model="form.windowDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="起止时刻">
          <el-time-select v-model="form.startTime" start="00:00" step="00:30" end="23:30" style="width: 120px" />
          <span style="margin: 0 8px">~</span>
          <el-time-select v-model="form.endTime" start="00:30" step="00:30" end="23:59" style="width: 120px" />
        </el-form-item>
        <el-form-item label="负责人"><el-input v-model="form.leader" /></el-form-item>
        <el-form-item label="作业人数"><el-input-number v-model="form.workerCount" :min="1" :step="2" /></el-form-item>
        <el-form-item label="配合单位"><el-input v-model="form.cooperateUnit" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="doCreate">提报</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, inject, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { workOrderApi } from '../api'

const works = inject('works')
const router = useRouter()
const list = ref([])
const createVisible = ref(false)
const form = ref({
  orderNo: '',
  sectionId: null,
  workItem: 'TAMP',
  windowDate: '',
  startTime: '00:30',
  endTime: '04:00',
  leader: '',
  workerCount: 20,
  cooperateUnit: ''
})

/** 00:00 — 06:00，半小时一格，共 12 格 */
const slots = computed(() => {
  const out = []
  for (let i = 0; i < 12; i++) {
    const minutes = i * 30
    const h = String(Math.floor(minutes / 60)).padStart(2, '0')
    const m = String(minutes % 60).padStart(2, '0')
    out.push({ index: i, start: minutes, end: minutes + 30, label: `${h}:${m}` })
  }
  return out
})

const dates = computed(() => {
  const set = [...new Set(list.value.map((o) => o.windowDate))]
  return set.sort()
})

function minutesOf(time) {
  if (!time) return 0
  const [h, m] = String(time).split(':').map(Number)
  return (h || 0) * 60 + (m || 0)
}

function rangeOf(order) {
  let start = minutesOf(order.startTime)
  let end = minutesOf(order.endTime)
  if (end <= start) end += 24 * 60
  return { start, end }
}

/** 该日期该时段被哪张作业单占用 */
function hit(date, slot) {
  return list.value.find((o) => {
    if (o.windowDate !== date) return false
    const r = rangeOf(o)
    return r.start < slot.end && r.end > slot.start
  }) || null
}

function labelOf(date, slot) {
  const order = hit(date, slot)
  if (!order) return ''
  const prev = hit(date, slots.value[slot.index - 1])
  return slot.index === 0 || !prev || prev.id !== order.id ? order.workItemName : ''
}

function titleOf(order) {
  return `${order.orderNo} ${order.workItemName} ${order.startTime}~${order.endTime} ${order.leader} ${order.statusName}`
}

function colorOf(status) {
  return { PLAN: '#90a4ae', APPROVED: '#1565c0', WORKING: '#00838f', CHECKED: '#2e9e5b', CLOSED: '#b0bec5' }[status] || '#90a4ae'
}

function tagType(status) {
  return { PLAN: 'info', APPROVED: 'primary', WORKING: 'warning', CHECKED: 'success', CLOSED: 'info' }[status] || 'info'
}

function rowClass({ row }) {
  return row.status === 'CLOSED' ? 'row-done' : ''
}

async function load() {
  list.value = await workOrderApi.list(works.sectionId.value)
}

function openCreate() {
  form.value = {
    orderNo: '',
    sectionId: works.sectionId.value,
    workItem: 'TAMP',
    windowDate: '',
    startTime: '00:30',
    endTime: '04:00',
    leader: '',
    workerCount: 20,
    cooperateUnit: ''
  }
  createVisible.value = true
}

async function doCreate() {
  await workOrderApi.create(form.value)
  ElMessage.success('作业单已提报')
  createVisible.value = false
  await Promise.all([load(), works.reloadBoard(), works.reloadOverview()])
}

async function advance(row) {
  await workOrderApi.advance(row.id)
  ElMessage.success(`已推进至${row.nextStatusName}`)
  await Promise.all([load(), works.reloadBoard(), works.reloadOverview()])
}

/** 已验收 → 交接台；有在途申请时直达该申请详情，否则带作业单过去直接发起 */
function goHandover(row) {
  if (row.activeHandoverId) {
    router.push({ path: '/handovers', query: { handoverId: row.activeHandoverId } })
  } else {
    router.push({ path: '/handovers', query: { orderId: row.id } })
  }
}

watch(() => works.sectionId.value, load)
onMounted(load)
</script>
