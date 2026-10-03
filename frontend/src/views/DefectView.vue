<template>
  <div>
    <section class="panel">
      <div class="panel-head">
        <h3>检查单</h3>
        <span class="panel-sub">检查单号·区段·检查人·检查日期·检查类型（静态/动态/添乘）</span>
        <div class="panel-extra">
          <el-button size="small" type="primary" @click="openInspectionCreate">新增检查单</el-button>
        </div>
      </div>
      <el-table :data="inspections" border stripe size="small" max-height="240">
        <el-table-column prop="inspectNo" label="检查单号" width="150" />
        <el-table-column prop="sectionNo" label="区段" width="90" />
        <el-table-column prop="lineName" label="线路" width="110" />
        <el-table-column prop="inspector" label="检查人" width="90" />
        <el-table-column prop="inspectDate" label="检查日期" width="110" />
        <el-table-column prop="inspectTypeName" label="检查类型" width="90" />
        <el-table-column label="病害 总/未销号" width="130">
          <template #default="{ row }">{{ row.defectCount }} / {{ row.openDefectCount }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip />
      </el-table>
    </section>

    <section class="panel">
      <div class="panel-head">
        <h3>病害清单</h3>
        <span class="panel-sub">重级未销号整行标红 · 整治时限：重 7 天 / 中 15 天 / 轻 30 天</span>
        <div class="panel-extra">
          <el-select v-model="severity" clearable placeholder="全部等级" style="width: 130px" @change="loadDefects">
            <el-option label="轻 LIGHT" value="LIGHT" />
            <el-option label="中 MIDDLE" value="MIDDLE" />
            <el-option label="重 HEAVY" value="HEAVY" />
          </el-select>
          <el-select v-model="closed" clearable placeholder="全部销号状态" style="width: 140px" @change="loadDefects">
            <el-option label="未销号" :value="false" />
            <el-option label="已销号" :value="true" />
          </el-select>
          <el-button size="small" @click="loadDefects">刷新</el-button>
          <el-button size="small" type="primary" @click="openDefectCreate">登记病害</el-button>
        </div>
      </div>
      <el-table :data="defects" border stripe size="small" :row-class-name="rowClass">
        <el-table-column prop="inspectNo" label="检查单号" width="140" />
        <el-table-column prop="sectionNo" label="区段" width="86" />
        <el-table-column prop="defectTypeName" label="病害类型" width="100" />
        <el-table-column label="严重等级" width="90">
          <template #default="{ row }">
            <span :style="{ color: row.severityColor, fontWeight: 700 }">
              <i class="tag-dot" :style="{ background: row.severityColor }"></i>{{ row.severityName }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="mileageText" label="里程" width="100" />
        <el-table-column prop="description" label="描述" min-width="230" show-overflow-tooltip />
        <el-table-column prop="foundDate" label="发现日期" width="110" />
        <el-table-column label="销号" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.closed ? 'success' : 'danger'">{{ row.closedName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="closeDate" label="销号日期" width="110" />
        <el-table-column prop="treatment" label="整治措施" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button v-if="!row.closed" link type="success" @click="openClose(row)">销号</el-button>
            <span v-else class="muted">已闭环</span>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="inspectionVisible" title="新增检查单" width="440px">
      <el-form :model="inspectionForm" label-width="96px">
        <el-form-item label="检查单号"><el-input v-model="inspectionForm.inspectNo" placeholder="如 INS-2026-0011" /></el-form-item>
        <el-form-item label="区段">
          <el-select v-model="inspectionForm.sectionId" style="width: 100%">
            <el-option v-for="s in works.sectionList.value" :key="s.id" :label="s.sectionNo" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="检查人"><el-input v-model="inspectionForm.inspector" /></el-form-item>
        <el-form-item label="检查日期"><el-date-picker v-model="inspectionForm.inspectDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="检查类型">
          <el-select v-model="inspectionForm.inspectType">
            <el-option label="静态" value="STATIC" /><el-option label="动态" value="DYNAMIC" /><el-option label="添乘" value="RIDE" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="inspectionForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="inspectionVisible = false">取消</el-button>
        <el-button type="primary" @click="doInspectionCreate">开单</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="closeVisible" title="病害销号" width="440px">
      <el-form label-width="96px">
        <el-form-item label="病害">
          <b>{{ actRow.defectTypeName }} · {{ actRow.mileageText }} · {{ actRow.severityName }}</b>
        </el-form-item>
        <el-form-item label="整治措施"><el-input v-model="treatment" type="textarea" :rows="3" placeholder="如：起道捣固后复检合格" /></el-form-item>
        <el-form-item label="销号日期"><el-date-picker v-model="closeDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeVisible = false">取消</el-button>
        <el-button type="success" @click="doClose">确认销号</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="defectVisible" title="登记病害" width="440px">
      <el-form :model="defectForm" label-width="96px">
        <el-form-item label="所属检查单">
          <el-select v-model="defectForm.inspectionId" style="width: 100%">
            <el-option v-for="i in inspections" :key="i.id" :label="i.inspectNo + ' · ' + i.sectionNo" :value="i.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="病害类型">
          <el-select v-model="defectForm.defectType" style="width: 100%">
            <el-option label="裂纹" value="CRACK" /><el-option label="磨耗" value="WEAR" />
            <el-option label="沉降" value="SETTLEMENT" /><el-option label="几何超限" value="GEOMETRY" />
            <el-option label="翻浆冒泥" value="MUD" /><el-option label="螺栓松动" value="BOLT" />
          </el-select>
        </el-form-item>
        <el-form-item label="严重等级">
          <el-select v-model="defectForm.severity" style="width: 100%">
            <el-option label="轻" value="LIGHT" /><el-option label="中" value="MIDDLE" /><el-option label="重" value="HEAVY" />
          </el-select>
        </el-form-item>
        <el-form-item label="里程位置"><el-input-number v-model="defectForm.mileage" :min="0" :step="0.1" :precision="3" /></el-form-item>
        <el-form-item label="发现日期"><el-date-picker v-model="defectForm.foundDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="defectForm.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="defectVisible = false">取消</el-button>
        <el-button type="primary" @click="doDefectCreate">登记</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { inject, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { defectApi, inspectionApi } from '../api'
import { buildDefectParams } from '../utils/defectQuery'

const works = inject('works')
const inspections = ref([])
const defects = ref([])
const severity = ref('')
const closed = ref('')

const inspectionVisible = ref(false)
const defectVisible = ref(false)
const closeVisible = ref(false)
const actRow = ref({})
const treatment = ref('')
const closeDate = ref('')
const inspectionForm = ref({ inspectNo: '', sectionId: null, inspector: '', inspectDate: '', inspectType: 'STATIC', remark: '' })
const defectForm = ref({ inspectionId: null, defectType: 'CRACK', severity: 'LIGHT', mileage: 0, foundDate: '', description: '' })

function rowClass({ row }) {
  if (!row.closed && row.severity === 'HEAVY') return 'row-heavy'
  if (row.closed) return 'row-done'
  return ''
}

async function loadInspections() {
  inspections.value = await inspectionApi.list(works.sectionId.value)
}

async function loadDefects() {
  // 区段（左侧全局上下文）+ 严重等级 + 销号状态组合筛选；未选区段查全线路
  const params = buildDefectParams({
    sectionId: works.sectionId.value,
    severity: severity.value,
    closed: closed.value
  })
  defects.value = await defectApi.list(params)
}

function openInspectionCreate() {
  inspectionForm.value = { inspectNo: '', sectionId: works.sectionId.value, inspector: '', inspectDate: '', inspectType: 'STATIC', remark: '' }
  inspectionVisible.value = true
}

async function doInspectionCreate() {
  await inspectionApi.create(inspectionForm.value)
  ElMessage.success('检查单已开立')
  inspectionVisible.value = false
  await loadInspections()
}

function openDefectCreate() {
  defectForm.value = {
    inspectionId: inspections.value.length ? inspections.value[0].id : null,
    defectType: 'CRACK',
    severity: 'LIGHT',
    mileage: 0,
    foundDate: '',
    description: ''
  }
  defectVisible.value = true
}

async function doDefectCreate() {
  await defectApi.create(defectForm.value)
  ElMessage.success('病害已登记')
  defectVisible.value = false
  await Promise.all([loadDefects(), loadInspections(), works.reloadBoard(), works.reloadOverview()])
}

function openClose(row) {
  actRow.value = row
  treatment.value = ''
  closeDate.value = new Date().toISOString().slice(0, 10)
  closeVisible.value = true
}

async function doClose() {
  await defectApi.close(actRow.value.id, { treatment: treatment.value, closeDate: closeDate.value })
  ElMessage.success('病害已销号')
  closeVisible.value = false
  await Promise.all([loadDefects(), loadInspections(), works.reloadBoard(), works.reloadOverview()])
}

watch(() => works.sectionId.value, () => {
  loadInspections()
  loadDefects()
})
onMounted(() => {
  loadInspections()
  loadDefects()
})
</script>
