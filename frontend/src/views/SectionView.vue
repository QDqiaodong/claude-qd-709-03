<template>
  <div>
    <section class="panel">
      <div class="panel-head">
        <h3>线路台账</h3>
        <span class="panel-sub">线路名称·起点站·终点站·运营里程·所属局</span>
        <div class="panel-extra">
          <el-button size="small" type="primary" @click="openLineCreate">新增线路</el-button>
        </div>
      </div>
      <el-table :data="works.lineList.value" border stripe size="small">
        <el-table-column prop="name" label="线路名称" width="140" />
        <el-table-column prop="directionName" label="方向" width="70" />
        <el-table-column prop="startStation" label="起点站" width="110" />
        <el-table-column prop="endStation" label="终点站" width="110" />
        <el-table-column prop="mileageKm" label="运营里程(km)" width="120" />
        <el-table-column prop="bureau" label="所属局" min-width="160" />
        <el-table-column prop="sectionCount" label="区段数" width="80" />
        <el-table-column label="病害 总/未销号" width="130">
          <template #default="{ row }">{{ row.defectCount }} / {{ row.openDefectCount }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="pickLine(row)">设为当前</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="panel">
      <div class="panel-head">
        <h3>区段台账</h3>
        <span class="panel-sub">当前线路 {{ lineName }} · 里程起止·轨型·道床·设计时速·状态</span>
        <div class="panel-extra">
          <el-button size="small" @click="loadSections">刷新</el-button>
          <el-button size="small" type="primary" @click="openCreate">新增区段</el-button>
        </div>
      </div>
      <el-table :data="sections" border stripe size="small">
        <el-table-column prop="sectionNo" label="区段编号" width="110" />
        <el-table-column label="里程起止" width="180">
          <template #default="{ row }">{{ row.startMileText }} ~ {{ row.endMileText }}</template>
        </el-table-column>
        <el-table-column prop="lengthKm" label="长度(km)" width="92" />
        <el-table-column prop="railTypeName" label="轨型" width="90" />
        <el-table-column prop="bedTypeName" label="道床" width="80" />
        <el-table-column prop="designSpeed" label="设计时速" width="92" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'NORMAL' ? 'success' : row.status === 'BLOCKED' ? 'info' : 'warning'">
              {{ row.statusName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="限速值" width="86">
          <template #default="{ row }">{{ row.speedLimit }} km/h</template>
        </el-table-column>
        <el-table-column prop="deviceCount" label="设备" width="70" />
        <el-table-column label="病害 总/未销号" width="130">
          <template #default="{ row }">{{ row.defectCount }} / {{ row.openDefectCount }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openStatus(row)">状态变更</el-button>
            <el-button link @click="works.sectionId.value = row.id">设为当前</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="createVisible" title="新增区段" width="480px">
      <el-form :model="form" label-width="96px">
        <el-form-item label="所属线路">
          <el-select v-model="form.lineId" style="width: 100%">
            <el-option v-for="l in works.lineList.value" :key="l.id" :label="l.name" :value="l.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="区段编号"><el-input v-model="form.sectionNo" placeholder="如 GX-07" /></el-form-item>
        <el-form-item label="里程起点"><el-input-number v-model="form.startMile" :min="0" :step="0.5" :precision="3" /></el-form-item>
        <el-form-item label="里程终点"><el-input-number v-model="form.endMile" :min="0" :step="0.5" :precision="3" /></el-form-item>
        <el-form-item label="轨型">
          <el-select v-model="form.railType"><el-option label="60kg/m" value="60KG" /><el-option label="75kg/m" value="75KG" /></el-select>
        </el-form-item>
        <el-form-item label="道床">
          <el-select v-model="form.bedType"><el-option label="有砟" value="BALLAST" /><el-option label="无砟" value="SLAB" /></el-select>
        </el-form-item>
        <el-form-item label="设计时速"><el-input-number v-model="form.designSpeed" :min="1" :step="10" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="doCreate">建档</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="statusVisible" title="区段状态变更" width="420px">
      <el-form label-width="96px">
        <el-form-item label="区段"><b>{{ actRow.sectionNo }}</b></el-form-item>
        <el-form-item label="目标状态">
          <el-select v-model="status" style="width: 100%">
            <el-option label="正常 NORMAL" value="NORMAL" />
            <el-option label="限速 SPEED_LIMIT" value="SPEED_LIMIT" />
            <el-option label="封锁施工 BLOCKED" value="BLOCKED" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="status === 'SPEED_LIMIT'" label="限速值">
          <el-input-number v-model="speedLimit" :min="1" :max="actRow.designSpeed" :step="5" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="statusVisible = false">取消</el-button>
        <el-button type="primary" @click="doStatus">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="lineVisible" title="新增线路" width="440px">
      <el-form :model="lineForm" label-width="96px">
        <el-form-item label="线路名称"><el-input v-model="lineForm.name" placeholder="如 京广上行" /></el-form-item>
        <el-form-item label="方向">
          <el-select v-model="lineForm.direction"><el-option label="上行" value="UP" /><el-option label="下行" value="DOWN" /></el-select>
        </el-form-item>
        <el-form-item label="起点站"><el-input v-model="lineForm.startStation" /></el-form-item>
        <el-form-item label="终点站"><el-input v-model="lineForm.endStation" /></el-form-item>
        <el-form-item label="运营里程"><el-input-number v-model="lineForm.mileageKm" :min="1" :step="10" /></el-form-item>
        <el-form-item label="所属局"><el-input v-model="lineForm.bureau" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="lineVisible = false">取消</el-button>
        <el-button type="primary" @click="doLineCreate">建档</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, inject, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { lineApi, sectionApi } from '../api'

const works = inject('works')
const sections = ref([])

const createVisible = ref(false)
const statusVisible = ref(false)
const lineVisible = ref(false)
const actRow = ref({})
const status = ref('NORMAL')
const speedLimit = ref(45)
const form = ref({ lineId: null, sectionNo: '', startMile: 0, endMile: 0, railType: '60KG', bedType: 'BALLAST', designSpeed: 160, remark: '' })
const lineForm = ref({ name: '', direction: 'UP', startStation: '', endStation: '', mileageKm: 100, bureau: '' })

const lineName = computed(() => {
  const hit = works.lineList.value.find((l) => l.id === works.lineId.value)
  return hit ? hit.name : ''
})

function pickLine(row) {
  works.lineId.value = row.id
  ElMessage.success(`已切换线路到 ${row.name}`)
}

async function loadSections() {
  sections.value = await sectionApi.list(works.lineId.value)
}

function openCreate() {
  form.value = {
    lineId: works.lineId.value,
    sectionNo: '',
    startMile: 0,
    endMile: 0,
    railType: '60KG',
    bedType: 'BALLAST',
    designSpeed: 160,
    remark: ''
  }
  createVisible.value = true
}

async function doCreate() {
  await sectionApi.create(form.value)
  ElMessage.success('区段已建档')
  createVisible.value = false
  await loadSections()
  await works.reloadBoard()
}

function openStatus(row) {
  actRow.value = row
  status.value = row.status
  speedLimit.value = row.speedLimit || 45
  statusVisible.value = true
}

async function doStatus() {
  await sectionApi.setStatus(actRow.value.id, status.value, speedLimit.value)
  ElMessage.success('区段状态已更新')
  statusVisible.value = false
  await loadSections()
  await works.reloadBoard()
}

function openLineCreate() {
  lineForm.value = { name: '', direction: 'UP', startStation: '', endStation: '', mileageKm: 100, bureau: '' }
  lineVisible.value = true
}

async function doLineCreate() {
  await lineApi.create(lineForm.value)
  ElMessage.success('线路已建档')
  lineVisible.value = false
  const lines = await lineApi.list()
  works.lineList.value = lines
}

watch(() => works.lineId.value, loadSections)
onMounted(loadSections)
</script>
