<template>
  <div>
    <section class="panel">
      <div class="panel-head">
        <h3>设备台账（继承体系多态查询）</h3>
        <span class="panel-sub">
          TABLE_PER_CLASS：钢轨 / 道岔 / 轨枕 / 桥梁 / 隧道 各一张完整表，查基类 Device 一次 UNION 返回
        </span>
        <div class="panel-extra">
          <el-select v-model="typeFilter" clearable placeholder="全部类型" style="width: 140px" @change="load">
            <el-option label="钢轨" value="RAIL" />
            <el-option label="道岔" value="SWITCH" />
            <el-option label="轨枕" value="SLEEPER" />
            <el-option label="桥梁" value="BRIDGE" />
            <el-option label="隧道" value="TUNNEL" />
          </el-select>
          <el-button size="small" @click="load">刷新</el-button>
          <el-button size="small" type="primary" @click="openCreate">新增设备</el-button>
        </div>
      </div>

      <el-table :data="list" border stripe size="small" :row-class-name="rowClass">
        <el-table-column prop="deviceNo" label="设备编号" width="150" />
        <el-table-column prop="typeName" label="类型" width="76" />
        <el-table-column prop="sectionNo" label="区段" width="90" />
        <el-table-column prop="lineName" label="线路" width="110" />
        <el-table-column prop="mileageText" label="里程" width="100" />
        <el-table-column prop="specText" label="专有参数" min-width="260" show-overflow-tooltip />
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
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openCheck(row)">检查登记</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="createVisible" title="新增设备（落到具体子类表）" width="520px">
      <el-form :model="form" label-width="96px">
        <el-form-item label="设备类型">
          <el-select v-model="form.typeCode" style="width: 100%">
            <el-option label="钢轨 RAIL" value="RAIL" />
            <el-option label="道岔 SWITCH" value="SWITCH" />
            <el-option label="轨枕 SLEEPER" value="SLEEPER" />
            <el-option label="桥梁 BRIDGE" value="BRIDGE" />
            <el-option label="隧道 TUNNEL" value="TUNNEL" />
          </el-select>
        </el-form-item>
        <el-form-item label="设备编号"><el-input v-model="form.deviceNo" placeholder="如 RAIL-GX-01-09" /></el-form-item>
        <el-form-item label="所属区段">
          <el-select v-model="form.sectionId" style="width: 100%">
            <el-option v-for="s in works.sectionList.value" :key="s.id" :label="s.sectionNo + ' ' + s.startMileText + '~' + s.endMileText" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="里程位置"><el-input-number v-model="form.mileage" :min="0" :step="0.1" :precision="3" /></el-form-item>
        <el-form-item label="安装日期"><el-date-picker v-model="form.installDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="上次检查日"><el-date-picker v-model="form.lastCheckDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status"><el-option label="良好" value="GOOD" /><el-option label="关注" value="WATCH" /><el-option label="失格" value="FAULT" /></el-select>
        </el-form-item>

        <template v-if="form.typeCode === 'RAIL'">
          <el-form-item label="轨型">
            <el-select v-model="form.railType"><el-option label="60kg/m" value="60KG" /><el-option label="75kg/m" value="75KG" /></el-select>
          </el-form-item>
          <el-form-item label="长度(m)"><el-input-number v-model="form.railLength" :min="1" :step="25" /></el-form-item>
          <el-form-item label="锁定轨温"><el-input-number v-model="form.lockTemp" :min="-20" :max="60" :step="0.5" :precision="1" /></el-form-item>
        </template>
        <template v-else-if="form.typeCode === 'SWITCH'">
          <el-form-item label="道岔型号"><el-input v-model="form.switchModel" placeholder="如 12号" /></el-form-item>
          <el-form-item label="开闭向">
            <el-select v-model="form.direction"><el-option label="左开" value="LEFT" /><el-option label="右开" value="RIGHT" /></el-select>
          </el-form-item>
          <el-form-item label="转辙机型"><el-input v-model="form.machineModel" placeholder="如 S700K" /></el-form-item>
        </template>
        <template v-else-if="form.typeCode === 'SLEEPER'">
          <el-form-item label="枕型">
            <el-select v-model="form.sleeperType"><el-option label="混凝土枕" value="CONCRETE" /><el-option label="木枕" value="WOOD" /></el-select>
          </el-form-item>
          <el-form-item label="根数"><el-input-number v-model="form.sleeperCount" :min="1" :step="10" /></el-form-item>
        </template>
        <template v-else-if="form.typeCode === 'BRIDGE'">
          <el-form-item label="桥长(m)"><el-input-number v-model="form.bridgeLength" :min="1" :step="10" :precision="2" /></el-form-item>
          <el-form-item label="孔跨"><el-input v-model="form.spanLayout" placeholder="如 4-32m" /></el-form-item>
          <el-form-item label="限重(t)"><el-input-number v-model="form.loadLimit" :min="1" :step="5" :precision="2" /></el-form-item>
        </template>
        <template v-else>
          <el-form-item label="全长(m)"><el-input-number v-model="form.tunnelLength" :min="1" :step="50" :precision="2" /></el-form-item>
          <el-form-item label="衬砌类型"><el-input v-model="form.liningType" placeholder="如 复合式衬砌" /></el-form-item>
          <el-form-item label="照明">
            <el-select v-model="form.lighting"><el-option label="正常" value="NORMAL" /><el-option label="故障" value="FAULT" /><el-option label="无照明" value="NONE" /></el-select>
          </el-form-item>
        </template>
        <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="doCreate">入账</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="checkVisible" title="设备定期检查登记" width="420px">
      <el-form label-width="96px">
        <el-form-item label="设备"><b>{{ actRow.deviceNo }}（{{ actRow.typeName }}）</b></el-form-item>
        <el-form-item label="检查日期"><el-date-picker v-model="checkDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="检查后状态">
          <el-select v-model="checkStatus"><el-option label="良好" value="GOOD" /><el-option label="关注" value="WATCH" /><el-option label="失格" value="FAULT" /></el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="checkVisible = false">取消</el-button>
        <el-button type="primary" @click="doCheck">登记</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { inject, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { deviceApi } from '../api'

const works = inject('works')
const list = ref([])
const typeFilter = ref('')

const createVisible = ref(false)
const checkVisible = ref(false)
const actRow = ref({})
const checkDate = ref('')
const checkStatus = ref('GOOD')
const form = ref(defaultForm())

function defaultForm() {
  return {
    typeCode: 'RAIL',
    deviceNo: '',
    sectionId: null,
    mileage: 0,
    installDate: null,
    lastCheckDate: null,
    status: 'GOOD',
    remark: '',
    railType: '60KG',
    railLength: 100,
    lockTemp: 25,
    switchModel: '12号',
    direction: 'LEFT',
    machineModel: 'S700K',
    sleeperType: 'CONCRETE',
    sleeperCount: 1760,
    bridgeLength: 100,
    spanLayout: '3-32m',
    loadLimit: 60,
    tunnelLength: 800,
    liningType: '复合式衬砌',
    lighting: 'NORMAL'
  }
}

function rowClass({ row }) {
  if (row.status === 'FAULT') return 'row-heavy'
  if (row.checkOverdue) return 'row-low'
  return ''
}

async function load() {
  list.value = await deviceApi.list(null, typeFilter.value || undefined)
}

function openCreate() {
  form.value = defaultForm()
  form.value.sectionId = works.sectionId.value
  createVisible.value = true
}

async function doCreate() {
  await deviceApi.create(form.value)
  ElMessage.success('设备已入账')
  createVisible.value = false
  await Promise.all([load(), works.reloadOverview()])
}

function openCheck(row) {
  actRow.value = row
  checkDate.value = new Date().toISOString().slice(0, 10)
  checkStatus.value = row.status
  checkVisible.value = true
}

async function doCheck() {
  await deviceApi.check(actRow.value.id, { checkDate: checkDate.value, status: checkStatus.value })
  ElMessage.success('检查已登记')
  checkVisible.value = false
  await load()
}

watch(() => works.lineId.value, load)
onMounted(load)
</script>
