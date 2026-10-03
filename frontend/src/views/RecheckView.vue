<template>
  <div>
    <!-- 筛选 + 批次列表 -->
    <section class="panel">
      <div class="panel-head">
        <h3>定检复查批次</h3>
        <span class="panel-sub">
          按区段汇总超 90 天周期的钢轨 / 道岔 / 轨枕 / 桥梁 / 隧道，跨类型勾选建批，逐台登记后提交复核，通过即回写台账
        </span>
        <div class="panel-extra">
          <el-button size="small" @click="load">刷新</el-button>
          <el-button size="small" type="primary" @click="openCreate">新建复查批次</el-button>
        </div>
      </div>

      <div class="filter-bar">
        <span class="filter-label">线路</span>
        <el-select v-model="filter.lineId" clearable placeholder="全部线路" style="width: 170px" @change="onFilter">
          <el-option v-for="l in lineOptions" :key="l.id" :label="l.name" :value="l.id" />
        </el-select>
        <span class="filter-label">区段</span>
        <el-select v-model="filter.sectionId" clearable filterable placeholder="全部区段" style="width: 160px" @change="onFilter">
          <el-option v-for="s in sectionOptions" :key="s.id" :label="s.sectionNo" :value="s.id" />
        </el-select>
        <span class="filter-label">批次状态</span>
        <el-radio-group v-model="filter.status" size="small" @change="onFilter">
          <el-radio-button label="">全部</el-radio-button>
          <el-radio-button label="DRAFT">草稿</el-radio-button>
          <el-radio-button label="PENDING">待复核</el-radio-button>
          <el-radio-button label="REJECTED">已退回</el-radio-button>
          <el-radio-button label="APPROVED">已通过</el-radio-button>
        </el-radio-group>
      </div>

      <el-table :data="list" border stripe size="small">
        <el-table-column prop="batchNo" label="批次编号" width="150" />
        <el-table-column label="线路 / 区段" min-width="150">
          <template #default="{ row }">
            {{ row.lineName }}<span class="muted"> · {{ row.sectionNo }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="inspector" label="检查人" width="90" />
        <el-table-column prop="reviewer" label="复核人" width="90">
          <template #default="{ row }">{{ row.reviewer || '—' }}</template>
        </el-table-column>
        <el-table-column prop="planDate" label="计划日期" width="110" />
        <el-table-column label="登记进度" width="200">
          <template #default="{ row }">
            <el-progress :percentage="row.progress" :stroke-width="14" :status="progressStatus(row)" />
            <span class="muted" style="font-size: 12px">
              {{ row.registeredCount }}/{{ row.totalCount }} 台 · 合格 {{ row.okCount }} / 不合格 {{ row.ngCount }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="逾期项" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.overdueCount > 0" size="small" type="danger">{{ row.overdueCount }}</el-tag>
            <span v-else class="muted">0</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)">{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row.id)">明细</el-button>
            <el-button
              v-if="row.status === 'PENDING'"
              link type="success" size="small" @click="openDetail(row.id, true)"
            >去复核</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- 新建批次：区段 + 到期设备勾选（跨五类）+ 检查人/计划日期 -->
    <el-dialog v-model="createVisible" title="新建定检复查批次" width="900px">
      <el-form :model="form" label-width="104px">
        <el-form-item label="汇总区段" required>
          <el-select v-model="form.sectionId" filterable placeholder="选择区段" style="width: 320px" @change="onPickSection">
            <el-option v-for="s in sectionOptions" :key="s.id"
              :label="`${s.sectionNo} · ${s.lineName || ''} · ${s.startMileText}~${s.endMileText}`" :value="s.id" />
          </el-select>
          <el-button link type="primary" size="small" style="margin-left: 8px" @click="useCurrentSection">用当前左侧区段</el-button>
        </el-form-item>
        <el-form-item label="检查人" required>
          <el-input v-model="form.inspector" placeholder="指定本批次检查人" style="width: 220px" />
        </el-form-item>
        <el-form-item label="计划日期" required>
          <el-date-picker v-model="form.planDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" placeholder="选填" style="width: 420px" />
        </el-form-item>
      </el-form>

      <div class="due-head">
        <h4>90 天周期到期设备（可跨钢轨/道岔/轨枕/桥梁/隧道勾选）</h4>
        <div>
          <el-checkbox
            :model-value="allDueChecked"
            :indeterminate="someDueChecked"
            :disabled="selectableDue.length === 0"
            @change="toggleSelectAll"
          >全选可选（{{ selectedDue.length }}/{{ selectableDue.length }}）</el-checkbox>
          <el-tag size="small" type="info" style="margin-left: 10px">到期 {{ dueList.length }} 台</el-tag>
          <el-tag size="small" type="danger" style="margin-left: 6px">占用 {{ occupiedDue.length }} 台</el-tag>
        </div>
      </div>
      <el-table
        ref="dueTableRef"
        :data="dueList" border size="small" max-height="320" row-key="deviceId"
        @select="onTableSelect" @select-all="onTableSelectAll"
      >
        <el-table-column type="selection" width="42" :selectable="(row) => !row.occupied" reserve-selection />
        <el-table-column prop="deviceNo" label="设备编号" width="160" />
        <el-table-column prop="deviceTypeName" label="类型" width="70" />
        <el-table-column prop="ledgerMileageText" label="台账里程" width="100" />
        <el-table-column label="上次检查" width="170">
          <template #default="{ row }">
            <span class="risk">{{ row.ledgerLastCheckDate || '从未检查' }}（{{ row.ledgerDaysSinceCheck }}天）</span>
          </template>
        </el-table-column>
        <el-table-column label="台账状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="deviceStatusTag(row.ledgerStatus)">{{ row.ledgerStatusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="占用情况" min-width="180">
          <template #default="{ row }">
            <el-tag v-if="row.occupied" size="small" type="danger">
              已被 {{ row.occupiedBatchNo }} 占用，禁选
            </el-tag>
            <span v-else class="muted">可选</span>
          </template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="doCreate">
          建批（已选 {{ selectedDue.length }} 台）
        </el-button>
      </template>
    </el-dialog>

    <!-- 批次详情 / 逐台登记 / 提交 / 复核入口 -->
    <el-drawer v-model="detailVisible" title="定检复查批次明细" size="900px">
      <div v-if="detail">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="批次编号">{{ detail.batchNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag size="small" :type="statusTag(detail.status)">{{ detail.statusName }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="计划日期">{{ detail.planDate }}</el-descriptions-item>
          <el-descriptions-item label="区段">{{ detail.lineName }} · {{ detail.sectionNo }}</el-descriptions-item>
          <el-descriptions-item label="检查人">{{ detail.inspector }}</el-descriptions-item>
          <el-descriptions-item label="复核人">{{ detail.reviewer || '—' }}</el-descriptions-item>
        </el-descriptions>

        <el-alert v-if="detail.status === 'REJECTED' && detail.reviewNote" type="error" :closable="false" show-icon
          :title="`退回原因：${detail.reviewNote}`" style="margin: 10px 0" />
        <el-alert v-if="detail.remark" type="info" :closable="false" :title="`建批备注：${detail.remark}`" style="margin: 10px 0" />

        <div class="due-head">
          <h4>逐台登记（{{ detail.registeredCount }}/{{ detail.totalCount }} 已登记，逾期 {{ detail.overdueCount }} 台）</h4>
          <el-button
            v-if="detail.status === 'DRAFT' || detail.status === 'REJECTED'"
            size="small" type="primary" :loading="submitting" @click="doSubmit(detail)"
          >{{ detail.status === 'REJECTED' ? '整改后重新提交' : '提交复核' }}</el-button>
        </div>

        <el-table :data="detail.items" border size="small" max-height="420" :row-class-name="itemRowClass">
          <el-table-column prop="deviceNo" label="设备编号" width="150" />
          <el-table-column prop="deviceTypeName" label="类型" width="66" />
          <el-table-column prop="planDate" label="计划日期" width="104" />
          <el-table-column label="结论" width="90">
            <template #default="{ row }">
              <el-tag v-if="row.conclusion" size="small" :type="row.conclusion === 'OK' ? 'success' : 'danger'">
                {{ row.conclusionName }}
              </el-tag>
              <el-tag v-else size="small" type="info">未登记</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="measuredMileageText" label="实测里程" width="96" />
          <el-table-column label="复查状态" width="80">
            <template #default="{ row }">
              <el-tag v-if="row.deviceStatus" size="small" :type="deviceStatusTag(row.deviceStatus)">
                {{ row.deviceStatusName }}
              </el-tag>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
          <el-table-column label="台账最新" min-width="150">
            <template #default="{ row }">
              <span class="muted">
                {{ row.ledgerLastCheckDate || '—' }} ·
                <el-tag size="small" :type="deviceStatusTag(row.ledgerStatus)">{{ row.ledgerStatusName }}</el-tag>
              </span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button
                link type="primary" size="small"
                :disabled="detail.status !== 'DRAFT' && detail.status !== 'REJECTED'"
                @click="openRegister(row)"
              >{{ row.registered ? '修改' : '登记' }}</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div v-if="submitHint" class="expire-banner" style="margin-top: 10px">{{ submitHint }}</div>

        <div style="margin: 16px 0; text-align: right">
          <el-button v-if="detail.status === 'PENDING'" type="success" @click="openReview(detail)">去复核</el-button>
        </div>
      </div>
    </el-drawer>

    <!-- 逐台登记 -->
    <el-dialog v-model="registerVisible" title="逐台登记复查结果" width="520px">
      <div v-if="actItem">
        <el-descriptions :column="2" border size="small" style="margin-bottom: 12px">
          <el-descriptions-item label="设备编号">{{ actItem.deviceNo }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ actItem.deviceTypeName }}</el-descriptions-item>
          <el-descriptions-item label="计划日期">{{ actItem.planDate }}</el-descriptions-item>
          <el-descriptions-item label="区段">{{ actItem.sectionNo }}</el-descriptions-item>
        </el-descriptions>
        <el-form :model="registerForm" label-width="96px">
          <el-form-item label="复查结论" required>
            <el-radio-group v-model="registerForm.conclusion">
              <el-radio label="OK">合格</el-radio>
              <el-radio label="NG">不合格</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="实测里程" required>
            <el-input-number v-model="registerForm.measuredMileage" :precision="3" :step="0.1" :min="0" />
            <span class="muted" style="margin-left: 8px">公里，须落在区段里程内</span>
          </el-form-item>
          <el-form-item label="设备状态" required>
            <el-radio-group v-model="registerForm.deviceStatus">
              <el-radio label="GOOD">良好</el-radio>
              <el-radio label="WATCH">关注</el-radio>
              <el-radio label="FAULT">失格</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="registerForm.remark" type="textarea" :rows="2" placeholder="选填" />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="registerVisible = false">取消</el-button>
        <el-button type="primary" :loading="registering" @click="doRegister">保存登记</el-button>
      </template>
    </el-dialog>

    <!-- 复核：通过 / 退回 -->
    <el-dialog v-model="reviewVisible" title="复核定检复查批次" width="560px">
      <div v-if="reviewData">
        <el-descriptions :column="2" border size="small" style="margin-bottom: 10px">
          <el-descriptions-item label="批次编号">{{ reviewData.batchNo }}</el-descriptions-item>
          <el-descriptions-item label="检查人">{{ reviewData.inspector }}</el-descriptions-item>
          <el-descriptions-item label="区段">{{ reviewData.sectionNo }}</el-descriptions-item>
          <el-descriptions-item label="计划日期">{{ reviewData.planDate }}</el-descriptions-item>
        </el-descriptions>
        <el-alert type="info" :closable="false" show-icon style="margin-bottom: 10px"
          :title="`已登记 ${reviewData.registeredCount}/${reviewData.totalCount} 台：合格 ${reviewData.okCount} 台、不合格 ${reviewData.ngCount} 台；逾期 ${reviewData.overdueCount} 台`" />
        <el-form label-width="86px">
          <el-form-item label="复核人" required>
            <el-input v-model="reviewForm.reviewer" placeholder="填写复核人姓名" style="width: 240px" />
          </el-form-item>
          <el-form-item label="复核说明">
            <el-input v-model="reviewForm.reviewNote" type="textarea" :rows="3"
              :placeholder="reviewAction === 'RETURN' ? '退回必须填写退回原因，明细保留供整改重提' : '复核说明（通过时可留空）'" />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="warning" :loading="reviewing" @click="doReview('RETURN')">退回（保留明细）</el-button>
        <el-button type="success" :loading="reviewing" @click="doReview('APPROVE')">通过并回写台账</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, inject, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { lineApi, recheckApi } from '../api'
import {
  buildRecheckParams,
  canResubmit,
  canReview,
  isItemRegistered,
  validateBatchBeforeSubmit
} from '../utils/recheckQuery'

const works = inject('works')

const list = ref([])
const lineOptions = ref([])
const sectionOptions = ref([])
const filter = ref({ lineId: null, sectionId: null, status: '' })

const createVisible = ref(false)
const creating = ref(false)
const dueList = ref([])
const selectedDue = ref([])
const dueTableRef = ref(null)
const form = ref(emptyForm())

const detailVisible = ref(false)
const detail = ref(null)
const detailAutoReview = ref(false)
const submitting = ref(false)
const submitHint = ref('')

const registerVisible = ref(false)
const registering = ref(false)
const actItem = ref(null)
const registerForm = ref({ conclusion: 'OK', measuredMileage: null, deviceStatus: 'GOOD', remark: '' })

const reviewVisible = ref(false)
const reviewing = ref(false)
const reviewData = ref(null)
const reviewForm = ref({ reviewer: '', reviewNote: '' })
const reviewAction = ref('APPROVE')

function emptyForm() {
  return { sectionId: null, inspector: '', planDate: '', remark: '' }
}

const selectableDue = computed(() => dueList.value.filter((d) => !d.occupied))
const occupiedDue = computed(() => dueList.value.filter((d) => d.occupied))
const allDueChecked = computed(() =>
  selectableDue.value.length > 0 && selectableDue.value.every((d) => selectedDue.value.includes(d.deviceId)))
const someDueChecked = computed(() => selectedDue.value.length > 0 && !allDueChecked.value)

function statusTag(s) {
  return { DRAFT: 'info', PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger' }[s] || 'info'
}
function deviceStatusTag(s) {
  return { GOOD: 'success', WATCH: 'warning', FAULT: 'danger' }[s] || 'info'
}
function progressStatus(row) {
  if (row.status === 'APPROVED') return 'success'
  if (row.progress === 100) return 'success'
  if (row.overdueCount > 0) return 'exception'
  return undefined
}
function itemRowClass({ row }) {
  if (row.planOverdue) return 'row-low'
  if (!row.registered) return ''
  return row.conclusion === 'NG' ? 'row-heavy' : ''
}

async function loadLinesAndSections() {
  if (!lineOptions.value.length) lineOptions.value = await lineApi.list()
  const sections = []
  for (const l of lineOptions.value) {
    const b = await lineApi.board(l.id).catch(() => ({ sections: [] }))
    sections.push(...(b.sections || []))
  }
  sectionOptions.value = sections
}

async function load() {
  list.value = await recheckApi.list(buildRecheckParams(filter.value))
}

function onFilter() {
  load()
}

function useCurrentSection() {
  if (works.sectionId.value) {
    form.value.sectionId = works.sectionId.value
    onPickSection(form.value.sectionId)
  }
}

async function openCreate() {
  await loadLinesAndSections()
  form.value = emptyForm()
  form.value.sectionId = works.sectionId.value || null
  dueList.value = []
  selectedDue.value = []
  createVisible.value = true
  if (form.value.sectionId) await onPickSection(form.value.sectionId)
}

async function onPickSection(sectionId) {
  selectedDue.value = []
  dueTableRef.value && dueTableRef.value.clearSelection()
  if (!sectionId) {
    dueList.value = []
    return
  }
  dueList.value = await recheckApi.dueDevices(sectionId)
}

function onTableSelect(selection, row) {
  // 占用行不可选（selectable 已拦截），直接以表格选中集为准
  selectedDue.value = selection.map((d) => d.deviceId)
  void row
}
function onTableSelectAll(selection) {
  selectedDue.value = selection.map((d) => d.deviceId)
}
function toggleSelectAll(checked) {
  if (!dueTableRef.value) return
  for (const d of selectableDue.value) {
    dueTableRef.value.toggleRowSelection(d, checked)
  }
  selectedDue.value = checked ? selectableDue.value.map((d) => d.deviceId) : []
}

async function doCreate() {
  if (!form.value.sectionId) return ElMessage.error('请选择汇总区段')
  if (!form.value.inspector.trim()) return ElMessage.error('请指定检查人')
  if (!form.value.planDate) return ElMessage.error('请指定计划日期')
  if (!selectedDue.value.length) return ElMessage.error('请至少勾选一台到期设备')
  creating.value = true
  try {
    const saved = await recheckApi.create({
      sectionId: form.value.sectionId,
      inspector: form.value.inspector.trim(),
      planDate: form.value.planDate,
      deviceIds: selectedDue.value,
      remark: form.value.remark
    })
    ElMessage.success('批次已建立，请逐台登记复查结果')
    createVisible.value = false
    await refreshAll()
    openDetail(saved.id)
  } finally {
    creating.value = false
  }
}

async function openDetail(id, autoReview = false) {
  detail.value = await recheckApi.detail(id)
  detailAutoReview.value = !!autoReview
  detailVisible.value = true
  if (autoReview && canReview(detail.value)) {
    openReview(detail.value)
  }
}

watch(detail, (d) => {
  if (!d) {
    submitHint.value = ''
    return
  }
  const check = validateBatchBeforeSubmit(batchForValidate(d))
  submitHint.value = check.ok ? '' : `提交前还需处理：${check.problems.join('；')}`
}, { deep: true })

/** 校验工具需要区段里程，详情接口未直接下发时从左侧区段清单补齐 */
function batchForValidate(d) {
  const section = sectionOptions.value.find((s) => s.id === d.sectionId)
  return {
    id: d.id,
    section: section
      ? { sectionNo: section.sectionNo, startMile: section.startMile, endMile: section.endMile }
      : { sectionNo: d.sectionNo, startMile: -Infinity, endMile: Infinity },
    items: d.items || []
  }
}

function openRegister(row) {
  actItem.value = row
  registerForm.value = {
    conclusion: row.conclusion || 'OK',
    measuredMileage: row.measuredMileage !== null && row.measuredMileage !== undefined ? Number(row.measuredMileage) : null,
    deviceStatus: row.deviceStatus || 'GOOD',
    remark: row.remark || ''
  }
  registerVisible.value = true
}

async function doRegister() {
  if (!isItemRegistered(registerForm.value)) {
    return ElMessage.error('请完整登记结论、实测里程和设备状态')
  }
  registering.value = true
  try {
    detail.value = await recheckApi.register(detail.value.id, actItem.value.id, { ...registerForm.value })
    ElMessage.success('复查结果已登记')
    registerVisible.value = false
  } finally {
    registering.value = false
  }
}

async function doSubmit(batch) {
  const check = validateBatchBeforeSubmit(batchForValidate(batch))
  if (!check.ok) {
    submitHint.value = `提交前还需处理：${check.problems.join('；')}`
    ElMessage.error('尚有未登记或不合规的设备，不能提交')
    return
  }
  submitting.value = true
  try {
    detail.value = await recheckApi.submit(batch.id)
    ElMessage.success(canResubmit(batch) ? '已整改重新提交，等待复核' : '批次已提交，等待复核')
    await load()
  } finally {
    submitting.value = false
  }
}

function openReview(batch) {
  if (!canReview(batch)) {
    ElMessage.error('只有待复核批次可以复核')
    return
  }
  reviewData.value = batch
  reviewForm.value = { reviewer: '', reviewNote: '' }
  reviewAction.value = 'APPROVE'
  detailVisible.value = false
  reviewVisible.value = true
}

async function doReview(action) {
  reviewAction.value = action
  if (!reviewForm.value.reviewer.trim()) return ElMessage.error('请填写复核人')
  if (action === 'RETURN' && !reviewForm.value.reviewNote.trim()) {
    return ElMessage.error('退回必须填写退回原因')
  }
  reviewing.value = true
  try {
    const saved = await recheckApi.review(reviewData.value.id, {
      reviewer: reviewForm.value.reviewer.trim(),
      action,
      reviewNote: reviewForm.value.reviewNote.trim()
    })
    ElMessage.success(action === 'APPROVE' ? '复核通过，设备台账已批量更新' : '已退回，登记明细与原因已保留')
    reviewVisible.value = false
    await refreshAll()
    openDetail(saved.id)
  } finally {
    reviewing.value = false
  }
}

async function refreshAll() {
  await Promise.all([load(), works.reloadBoard(), works.reloadOverview()])
}

watch(() => works.lineId.value, async () => {
  filter.value.lineId = works.lineId.value
  await loadLinesAndSections()
}, { immediate: true })

onMounted(() => {
  filter.value.lineId = works.lineId.value
  load()
})
</script>

<style scoped>
.filter-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin: 10px 0 12px;
}
.filter-label {
  font-size: 13px;
  color: #5a6a70;
}
.due-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 10px 0 8px;
}
.due-head h4 {
  margin: 0;
  font-size: 14px;
}
.risk {
  color: #e08700;
  font-weight: 600;
}
.expire-banner {
  background: #fdecea;
  border: 1px solid #f5c6c0;
  color: #a1261c;
  border-radius: 6px;
  padding: 8px 12px;
  font-size: 13px;
}
</style>
