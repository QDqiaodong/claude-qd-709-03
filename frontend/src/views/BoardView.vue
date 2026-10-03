<template>
  <div>
    <!-- 主视觉：线路里程状态条 -->
    <section class="panel">
      <div class="panel-head">
        <h3>线路里程状态条</h3>
        <span class="panel-sub">
          横轴为公里标 {{ board.rangeStartText }} ~ {{ board.rangeEndText }}，色条为区段状态/病害严重程度，▲ 为病害位置（点击看详情）
        </span>
      </div>

      <div class="kv-grid" style="margin-bottom: 12px">
        <div class="kv"><span>线路</span><b>{{ line.name }}</b></div>
        <div class="kv"><span>起终点</span><b>{{ line.startStation }} — {{ line.endStation }}</b></div>
        <div class="kv"><span>运营里程</span><b>{{ line.mileageKm }} km</b></div>
        <div class="kv"><span>区段 / 病害</span><b>{{ sections.length }} 个 / {{ defects.length }} 处</b></div>
      </div>

      <div class="mileage-bar">
        <svg viewBox="0 0 1000 150">
          <!-- 公里标轴 -->
          <line :x1="PAD" :x2="1000 - PAD" y1="96" y2="96" stroke="#8fa9ac" stroke-width="1.5" />
          <g v-for="t in ticks" :key="t.value">
            <line :x1="xOf(t.value)" :x2="xOf(t.value)" y1="88" y2="100" stroke="#8fa9ac" stroke-width="1" />
            <text :x="xOf(t.value)" y="114" text-anchor="middle" class="mile-tick-txt">K{{ t.value }}</text>
          </g>

          <!-- 区段色条 -->
          <g v-for="s in sections" :key="s.id" @click="works.sectionId.value = s.id" style="cursor: pointer">
            <rect
              :x="xOfNum(s.startMile)"
              :y="44"
              :width="Math.max(4, widthOf(s))"
              height="34"
              rx="4"
              :fill="works.barColor(s)"
              :stroke="s.id === works.sectionId.value ? '#005f68' : 'rgba(255,255,255,0.65)'"
              :stroke-width="s.id === works.sectionId.value ? 3 : 1"
            />
            <text
              v-if="widthOf(s) > 46"
              :x="xOfNum(s.startMile) + widthOf(s) / 2"
              y="65"
              text-anchor="middle"
              class="mile-sec-txt"
            >{{ s.sectionNo }}</text>
          </g>

          <!-- 病害三角标记 -->
          <g v-for="d in markers" :key="d.id" @click="openDefect(d)" style="cursor: pointer">
            <polygon
              :points="triangle(d.mileage)"
              :fill="d.closed ? '#ffffff' : d.severityColor"
              :stroke="d.severityColor"
              stroke-width="1.5"
            />
            <title>{{ d.defectTypeName }} · {{ d.severityName }} · {{ d.mileageText }}</title>
          </g>

          <text :x="PAD" y="136" class="mile-tick-txt">
            里程轴 {{ board.rangeStartText }} → {{ board.rangeEndText }}（{{ span }} km）
          </text>
        </svg>

        <div class="mile-legend">
          <span><i style="background: #2e9e5b"></i>正常</span>
          <span><i style="background: #e8c33d"></i>轻微病害</span>
          <span><i style="background: #e08700"></i>中级病害</span>
          <span><i style="background: #c62828"></i>严重病害</span>
          <span><i style="background: #1565c0"></i>限速</span>
          <span><i style="background: #4a5b63"></i>封锁施工</span>
          <span><i style="background: #ffffff"></i>▲ 空心 = 已销号</span>
        </div>
      </div>
    </section>

    <!-- 选中区段的设备明细（多态数据按类型 Tab 展开） -->
    <section class="panel">
      <div class="panel-head">
        <h3>区段设备明细</h3>
        <span class="panel-sub">
          {{ current ? current.sectionNo + '（' + current.startMileText + ' ~ ' + current.endMileText + '）' : '请先在左侧选择区段' }}
          · 一次多态查询返回 {{ devices.length }} 条，按类型 Tab 分拣
        </span>
        <div class="panel-extra">
          <el-button size="small" @click="loadDevices">刷新</el-button>
        </div>
      </div>

      <el-tabs v-model="tab" type="card">
        <el-tab-pane v-for="t in tabs" :key="t.key" :name="t.key">
          <template #label>{{ t.label }}（{{ countOf(t.key) }}）</template>
        </el-tab-pane>
      </el-tabs>

      <el-table :data="filtered" border stripe size="small" height="330">
        <el-table-column prop="deviceNo" label="设备编号" width="150" />
        <el-table-column prop="typeName" label="类型" width="76" />
        <el-table-column prop="mileageText" label="里程" width="100" />
        <el-table-column v-if="tab === 'RAIL'" prop="railType" label="轨型" width="90">
          <template #default="{ row }">{{ row.railType === '75KG' ? '75kg/m' : '60kg/m' }}</template>
        </el-table-column>
        <el-table-column v-if="tab === 'RAIL'" prop="railLength" label="长度(m)" width="86" />
        <el-table-column v-if="tab === 'RAIL'" prop="lockTemp" label="锁定轨温(℃)" width="110" />

        <el-table-column v-if="tab === 'SWITCH'" prop="switchModel" label="道岔型号" width="96" />
        <el-table-column v-if="tab === 'SWITCH'" prop="direction" label="开闭向" width="86">
          <template #default="{ row }">{{ row.direction === 'RIGHT' ? '右开' : '左开' }}</template>
        </el-table-column>
        <el-table-column v-if="tab === 'SWITCH'" prop="machineModel" label="转辙机型" width="110" />

        <el-table-column v-if="tab === 'SLEEPER'" prop="sleeperType" label="枕型" width="96">
          <template #default="{ row }">{{ row.sleeperType === 'WOOD' ? '木枕' : '混凝土枕' }}</template>
        </el-table-column>
        <el-table-column v-if="tab === 'SLEEPER'" prop="sleeperCount" label="根数" width="90" />

        <el-table-column v-if="tab === 'STRUCT'" prop="spanLayout" label="孔跨" width="110">
          <template #default="{ row }">{{ row.typeCode === 'BRIDGE' ? row.spanLayout : '—' }}</template>
        </el-table-column>
        <el-table-column v-if="tab === 'STRUCT'" label="长(m)" width="86">
          <template #default="{ row }">{{ row.typeCode === 'BRIDGE' ? row.bridgeLength : row.tunnelLength }}</template>
        </el-table-column>
        <el-table-column v-if="tab === 'STRUCT'" label="限重/衬砌" min-width="140">
          <template #default="{ row }">{{ row.typeCode === 'BRIDGE' ? row.loadLimit + ' t' : row.liningType }}</template>
        </el-table-column>
        <el-table-column v-if="tab === 'STRUCT'" label="照明" width="90">
          <template #default="{ row }">
            <span v-if="row.typeCode === 'TUNNEL'">{{ lightingName(row.lighting) }}</span>
            <span v-else>—</span>
          </template>
        </el-table-column>

        <el-table-column prop="specText" label="参数摘要" min-width="200" show-overflow-tooltip />
        <el-table-column prop="installDate" label="安装日期" width="110" />
        <el-table-column label="上次检查" width="150">
          <template #default="{ row }">
            <span :style="{ color: row.checkOverdue ? '#e08700' : '' }">
              {{ row.lastCheckDate }}（{{ row.daysSinceCheck }}天）
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'GOOD' ? 'success' : row.status === 'WATCH' ? 'warning' : 'danger'">
              {{ row.statusName }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- 病害详情抽屉 -->
    <el-drawer v-model="drawerVisible" title="病害详情" size="380px">
      <div v-if="actDefect">
        <div class="kv-grid">
          <div class="kv"><span>病害类型</span><b>{{ actDefect.defectTypeName }}</b></div>
          <div class="kv"><span>严重等级</span><b :style="{ color: actDefect.severityColor }">{{ actDefect.severityName }}</b></div>
          <div class="kv"><span>里程位置</span><b>{{ actDefect.mileageText }}</b></div>
          <div class="kv"><span>所属区段</span><b>{{ actDefect.sectionNo }}</b></div>
          <div class="kv"><span>检查单号</span><b>{{ actDefect.inspectNo }}</b></div>
          <div class="kv"><span>发现日期</span><b>{{ actDefect.foundDate }}</b></div>
          <div class="kv"><span>整治时限</span><b>{{ actDefect.limitDays }} 天</b></div>
          <div class="kv">
            <span>销号状态</span>
            <b :style="{ color: actDefect.closed ? '#2e9e5b' : '#c62828' }">
              {{ actDefect.closed ? '已销号 ' + actDefect.closeDate : '未销号（逾期 ' + actDefect.overdueDays + ' 天）' }}
            </b>
          </div>
        </div>
        <p style="margin-top: 14px"><span class="muted">病害描述：</span>{{ actDefect.description }}</p>
        <p v-if="actDefect.treatment"><span class="muted">整治措施：</span>{{ actDefect.treatment }}</p>
        <div v-if="!actDefect.closed" style="margin-top: 10px">
          <el-button type="success" size="small" @click="closeDefect">病害销号</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, inject, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { defectApi, deviceApi } from '../api'

const works = inject('works')
const board = works.board
const PAD = 40

const tab = ref('RAIL')
const devices = ref([])
const drawerVisible = ref(false)
const actDefect = ref(null)

const tabs = [
  { key: 'RAIL', label: '钢轨' },
  { key: 'SWITCH', label: '道岔' },
  { key: 'SLEEPER', label: '轨枕' },
  { key: 'STRUCT', label: '桥隧' }
]

const line = computed(() => board.value.line || {})
const sections = computed(() => board.value.sections || [])
const defects = computed(() => board.value.defects || [])
const current = computed(() => sections.value.find((s) => s.id === works.sectionId.value) || null)
const span = computed(() => {
  const s = board.value.rangeStart
  const e = board.value.rangeEnd
  return s == null || e == null ? 0 : Number((Number(e) - Number(s)).toFixed(3))
})

/** 只画落在里程范围内的病害 */
const markers = computed(() =>
  defects.value.filter((d) => Number(d.mileage) >= Number(board.value.rangeStart) && Number(d.mileage) <= Number(board.value.rangeEnd))
)

const filtered = computed(() => {
  if (tab.value === 'STRUCT') return devices.value.filter((d) => d.typeCode === 'BRIDGE' || d.typeCode === 'TUNNEL')
  return devices.value.filter((d) => d.typeCode === tab.value)
})

function countOf(key) {
  if (key === 'STRUCT') return devices.value.filter((d) => d.typeCode === 'BRIDGE' || d.typeCode === 'TUNNEL').length
  return devices.value.filter((d) => d.typeCode === key).length
}

/** 公里标刻度：最多 12 个 */
const ticks = computed(() => {
  const start = Number(board.value.rangeStart || 0)
  const end = Number(board.value.rangeEnd || 1)
  const total = Math.max(0.5, end - start)
  const step = Math.max(1, Math.ceil(total / 12))
  const list = []
  for (let v = Math.ceil(start / step) * step; v <= end; v += step) {
    list.push({ value: v })
  }
  return list
})

function ratio(mileage) {
  const start = Number(board.value.rangeStart || 0)
  const end = Number(board.value.rangeEnd || 1)
  const spanKm = Number(end) - Number(start) || 1
  return (Number(mileage) - Number(start)) / spanKm
}

function xOfNum(mileage) {
  return PAD + ratio(mileage) * (1000 - PAD * 2)
}

function xOf(value) {
  return xOfNum(value)
}

function widthOf(section) {
  return Math.abs(xOfNum(section.endMile) - xOfNum(section.startMile))
}

/** 病害三角：顶点朝下，压在色条上方 */
function triangle(mileage) {
  const x = xOfNum(mileage)
  return `${x - 6},12 ${x + 6},12 ${x},24`
}

function lightingName(code) {
  return code === 'NORMAL' ? '正常' : code === 'FAULT' ? '故障' : '无照明'
}

function openDefect(row) {
  actDefect.value = row
  drawerVisible.value = true
}

async function closeDefect() {
  try {
    const { value } = await ElMessageBox.prompt('填写整治措施后销号', '病害销号', {
      inputPlaceholder: '如：起道捣固后复检合格',
      inputValidator: (v) => (v && v.trim().length > 0) || '整治措施不能为空'
    })
    await defectApi.close(actDefect.value.id, { treatment: value })
    ElMessage.success('病害已销号')
    drawerVisible.value = false
    await works.reloadBoard()
  } catch (e) {
    if (e !== 'cancel' && e.message) ElMessage.error(e.message)
  }
}

async function loadDevices() {
  if (!works.sectionId.value) {
    devices.value = []
    return
  }
  // 不传 type：走基类 Device 的多态查询
  devices.value = await deviceApi.list(works.sectionId.value)
}

watch(() => works.sectionId.value, loadDevices, { immediate: true })
watch(() => works.lineId.value, loadDevices)
</script>
