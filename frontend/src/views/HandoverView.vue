<template>
  <div>
    <!-- 筛选 + 列表 -->
    <section class="panel">
      <div class="panel-head">
        <h3>封锁解除交接台</h3>
        <span class="panel-sub">
          已验收养修作业的解除申请：四项现场逐项填报、复核人逐项确认；现场变化即失效，必须基于最新现场重提
        </span>
        <div class="panel-extra">
          <el-button size="small" @click="load">刷新</el-button>
          <el-button size="small" type="primary" @click="openCreate()">发起封锁解除申请</el-button>
        </div>
      </div>

      <div class="filter-bar">
        <span class="filter-label">线路</span>
        <el-select v-model="filter.lineId" clearable placeholder="全部线路" style="width: 180px" @change="onFilter">
          <el-option v-for="l in lineOptions" :key="l.id" :label="l.name" :value="l.id" />
        </el-select>
        <span class="filter-label">区段</span>
        <el-select v-model="filter.sectionId" clearable filterable placeholder="全部区段" style="width: 170px" @change="onFilter">
          <el-option v-for="s in sectionOptions" :key="s.id" :label="s.sectionNo + ' · ' + s.statusName" :value="s.id" />
        </el-select>
        <span class="filter-label">交接状态</span>
        <el-radio-group v-model="filter.status" size="small" @change="onFilter">
          <el-radio-button label="">全部</el-radio-button>
          <el-radio-button label="PENDING">待复核</el-radio-button>
          <el-radio-button label="APPROVED">已通过</el-radio-button>
          <el-radio-button label="REJECTED">已退回</el-radio-button>
          <el-radio-button label="EXPIRED">已失效</el-radio-button>
        </el-radio-group>
      </div>

      <el-table :data="list" border stripe size="small">
        <el-table-column prop="handoverNo" label="交接单号" width="140" />
        <el-table-column label="线路 / 区段" min-width="150">
          <template #default="{ row }">
            {{ row.lineName }}<span class="muted"> · {{ row.sectionNo }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="orderNo" label="作业单号" width="140" />
        <el-table-column prop="workItemName" label="作业项目" width="90" />
        <el-table-column prop="applicant" label="施工负责人" width="100" />
        <el-table-column label="解除建议" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="recTag(row.recommendation)">{{ row.recommendationName }}</el-tag>
            <span v-if="row.recommendation === 'SPEED_LIMIT'" class="muted" style="margin-left: 4px">{{ row.speedLimit }}</span>
          </template>
        </el-table-column>
        <el-table-column label="最终结论" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.conclusion" size="small" :type="recTag(row.conclusion)">
              {{ row.conclusionName }}<span v-if="row.conclusion === 'SPEED_LIMIT'"> {{ row.conclusionSpeedLimit }}</span>
            </el-tag>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="96">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)">{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="submittedAt" label="提交时间" width="170">
          <template #default="{ row }">{{ fmt(row.submittedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row.id)">详情/轨迹</el-button>
            <el-button
              v-if="row.status === 'PENDING' || row.status === 'REJECTED'"
              link
              :type="row.status === 'PENDING' ? 'success' : 'warning'"
              size="small"
              @click="openDetail(row.id, true)"
            >{{ row.status === 'PENDING' ? '去复核' : '查看退回' }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- 发起 / 重提 申请 -->
    <el-dialog v-model="submitVisible" :title="submitForm.reapplyOfId ? '基于最新现场重新提交封锁解除申请' : '发起封锁解除申请'" width="760px" @closed="resetSubmit">
      <div v-if="submitForm.reapplyOfId" class="expire-banner">
        原申请已<span v-if="resubmitSource === 'REJECTED'">被退回整改</span><span v-else>失效</span>，
        请核对下方最新现场后重新逐项填报，旧结论不会自动套用。
      </div>

      <el-form :model="submitForm" label-width="110px">
        <el-form-item label="养修作业" required>
          <el-select
            v-model="submitForm.workOrderId"
            filterable
            :disabled="!!submitForm.reapplyOfId"
            placeholder="仅可选择已验收且无在途申请的作业"
            style="width: 100%"
            @change="onPickOrder"
          >
            <el-option
              v-for="o in eligibleOrders"
              :key="o.id"
              :label="`${o.orderNo} · ${o.sectionNo} · ${o.workItemName} · 负责人 ${o.leader}`"
              :value="o.id"
            />
          </el-select>
        </el-form-item>

        <div v-if="selectedOrder" class="snapshot-box">
          <div class="kv-grid">
            <div class="kv"><span>作业单状态</span><b>{{ selectedOrder.statusName }}</b></div>
            <div class="kv"><span>区段状态</span><b>{{ selectedSection ? selectedSection.statusName : '' }}</b></div>
            <div class="kv"><span>设计时速</span><b>{{ selectedSection ? selectedSection.designSpeed : '' }} km/h</b></div>
            <div class="kv"><span>未销号重级病害</span>
              <b :class="{ 'risk': heavyDefects.length > 0 }">{{ heavyDefects.length }} 处</b>
            </div>
          </div>
          <el-table v-if="heavyDefects.length" :data="heavyDefects" border size="small" style="margin-top: 8px">
            <el-table-column prop="mileageText" label="里程" width="100" />
            <el-table-column prop="defectTypeName" label="类型" width="90" />
            <el-table-column prop="description" label="病害描述" show-overflow-tooltip />
            <el-table-column prop="foundDate" label="发现日期" width="110" />
          </el-table>
        </div>

        <el-form-item label="发起人" required>
          <el-input v-model="submitForm.applicant" placeholder="必须是该作业的施工负责人本人" style="width: 260px" />
          <span class="muted" style="margin-left: 10px">复核人将被要求不是同一人</span>
        </el-form-item>

        <div class="check-block">
          <h4>现场四项（逐项填报）</h4>
          <div v-for="item in checkItems" :key="item.key" class="check-row">
            <span class="check-name">{{ item.label }}</span>
            <el-radio-group v-model="submitForm[item.key + 'Result']">
              <el-radio label="OK">合格</el-radio>
              <el-radio label="NG">不达标</el-radio>
            </el-radio-group>
            <el-input v-model="submitForm[item.key + 'Remark']" :placeholder="item.label + '情况说明'" size="small" style="flex: 1" />
          </div>
        </div>

        <el-form-item label="解除建议" required>
          <el-radio-group v-model="submitForm.recommendation">
            <el-radio label="NORMAL">恢复正常</el-radio>
            <el-radio label="SPEED_LIMIT">继续限速</el-radio>
            <el-radio label="BLOCKED">继续封锁</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="submitForm.recommendation === 'SPEED_LIMIT'" label="限速值/原因" required>
          <el-input-number v-model="submitForm.speedLimit" :min="1" :max="selectedSection ? selectedSection.designSpeed : 300" />
          <span class="muted" style="margin: 0 8px">km/h（不得高于设计时速）</span>
          <el-input v-model="submitForm.limitReason" placeholder="限速原因（必填）" style="width: 300px" />
        </el-form-item>
        <el-alert
          v-if="submitForm.recommendation === 'NORMAL' && heavyDefects.length > 0"
          type="error"
          :closable="false"
          show-icon
          title="该区段仍有未销号重级病害，绝不能恢复正常，只能选择继续限速或继续封锁"
          style="margin-bottom: 10px"
        />
        <el-form-item label="申请说明">
          <el-input v-model="submitForm.applyNote" type="textarea" :rows="2" placeholder="补充说明（选填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="submitVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="doSubmit">
          {{ submitForm.reapplyOfId ? '重新提交' : '提交申请' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 复核 -->
    <el-dialog v-model="reviewVisible" title="复核封锁解除申请" width="760px">
      <div v-if="reviewData" class="review-head">
        <el-tag :type="statusTag(reviewData.status)">{{ reviewData.statusName }}</el-tag>
        <b style="margin: 0 8px">{{ reviewData.handoverNo }}</b>
        <span class="muted">{{ reviewData.orderNo }} · {{ reviewData.sectionNo }} · 施工负责人 {{ reviewData.applicant }}</span>
      </div>

      <el-alert
        v-if="reviewData.changes && reviewData.changes.length"
        type="error"
        :closable="false"
        show-icon
        style="margin: 10px 0"
        title="申请已失效：现场发生如下变化，旧结论不能套用，请退回并要求基于最新现场重新提交"
      >
        <div v-for="(c, i) in reviewData.changes" :key="i">· [{{ c.typeName }}] {{ c.detail }}</div>
      </el-alert>

      <el-descriptions :column="2" border size="small" style="margin: 10px 0">
        <el-descriptions-item label="发起建议">
          {{ reviewData.recommendationName }}
          <span v-if="reviewData.recommendation === 'SPEED_LIMIT'">（{{ reviewData.speedLimit }} km/h，{{ reviewData.limitReason }}）</span>
        </el-descriptions-item>
        <el-descriptions-item label="提交时间">{{ fmt(reviewData.submittedAt) }}</el-descriptions-item>
      </el-descriptions>

      <div class="check-block">
        <h4>四项逐项确认（任一项退回即整体退回整改）</h4>
        <div v-for="item in checkItems" :key="item.key" class="check-row">
          <span class="check-name">{{ item.label }}</span>
          <el-tag size="small" :type="reviewData[item.key + 'Result'] === 'OK' ? 'success' : 'danger'">
            申报：{{ reviewData[item.key + 'ResultName'] }}
          </el-tag>
          <el-radio-group v-model="reviewForm[item.key + 'Check']" :disabled="reviewData.status !== 'PENDING'">
            <el-radio label="CONFIRM">确认合格</el-radio>
            <el-radio label="RETURN">退回整改</el-radio>
          </el-radio-group>
          <span class="muted">{{ reviewData[item.key + 'Remark'] || '无说明' }}</span>
        </div>
      </div>

      <el-form label-width="100px" style="margin-top: 6px">
        <el-form-item label="复核人" required>
          <el-input v-model="reviewForm.reviewer" placeholder="必须与施工负责人不是同一人" style="width: 260px" />
        </el-form-item>
        <el-form-item label="最终结论" required>
          <el-radio-group v-model="reviewForm.conclusion">
            <el-radio label="NORMAL">恢复正常</el-radio>
            <el-radio label="SPEED_LIMIT">继续限速</el-radio>
            <el-radio label="BLOCKED">继续封锁</el-radio>
          </el-radio-group>
          <span class="muted" style="margin-left: 8px">最终结论由复核人承担，系统不自动放行</span>
        </el-form-item>
        <el-form-item v-if="reviewForm.conclusion === 'SPEED_LIMIT'" label="限速值/原因" required>
          <el-input-number v-model="reviewForm.conclusionSpeedLimit" :min="1" :max="reviewData.designSpeed || 300" />
          <span class="muted" style="margin: 0 8px">km/h（≤ 设计 {{ reviewData.designSpeed }}）</span>
          <el-input v-model="reviewForm.conclusionLimitReason" placeholder="限速原因（必填）" style="width: 280px" />
        </el-form-item>
        <el-alert
          v-if="reviewForm.conclusion === 'NORMAL' && reviewData.currentHeavyDefects && reviewData.currentHeavyDefects.length"
          type="error"
          :closable="false"
          show-icon
          title="当前仍有未销号重级病害，恢复正常的结论无法提交"
          style="margin-bottom: 10px"
        />
        <el-form-item label="复核说明">
          <el-input v-model="reviewForm.reviewNote" type="textarea" :rows="2"
            :placeholder="reviewForm.action === 'RETURN' ? '退回整改必须填写整改要求' : '复核说明（可留空）'" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="reviewVisible = false">关闭</el-button>
        <template v-if="reviewData.status === 'PENDING'">
          <el-button type="warning" :loading="reviewing" @click="doReview('RETURN')">退回整改</el-button>
          <el-button type="success" :loading="reviewing" @click="doReview('APPROVE')">复核通过并交接</el-button>
        </template>
        <el-button v-else-if="reviewData.status === 'REJECTED'" type="primary" @click="resubmit(reviewData)">
          按退回意见重提
        </el-button>
        <el-button v-else-if="reviewData.status === 'EXPIRED'" type="primary" @click="resubmit(reviewData)">
          基于最新现场重提
        </el-button>
      </template>
    </el-dialog>

    <!-- 申请详情 + 完整轨迹 -->
    <el-drawer v-model="detailVisible" title="交接申请详情与处理轨迹" size="620px">
      <div v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="交接单号">{{ detail.handoverNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag size="small" :type="statusTag(detail.status)">{{ detail.statusName }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="作业单">{{ detail.orderNo }} · {{ detail.workItemName }}</el-descriptions-item>
          <el-descriptions-item label="区段">{{ detail.lineName }} · {{ detail.sectionNo }}</el-descriptions-item>
          <el-descriptions-item label="发起人（施工负责人）">{{ detail.applicant }}</el-descriptions-item>
          <el-descriptions-item label="复核人">{{ detail.reviewer || '—' }}</el-descriptions-item>
          <el-descriptions-item label="解除建议">
            {{ detail.recommendationName }}<span v-if="detail.speedLimit"> · {{ detail.speedLimit }}km/h</span>
          </el-descriptions-item>
          <el-descriptions-item label="最终结论">
            <span v-if="detail.conclusion">{{ detail.conclusionName }}<span v-if="detail.conclusionSpeedLimit"> · {{ detail.conclusionSpeedLimit }}km/h</span></span>
            <span v-else class="muted">未结论</span>
          </el-descriptions-item>
        </el-descriptions>

        <el-alert
          v-if="detail.changes && detail.changes.length"
          type="error"
          :closable="false"
          show-icon
          style="margin: 12px 0"
          title="复核失效的变化项（须基于最新现场重新提交）"
        >
          <div v-for="(c, i) in detail.changes" :key="i">· [{{ c.typeName }}] {{ c.detail }}</div>
        </el-alert>
        <div v-if="detail.status === 'EXPIRED' && detail.expiredReason" class="expire-banner">
          失效原因：{{ detail.expiredReason }}
        </div>

        <h4 class="block-title">提交时固化的现场快照 vs 当前现场</h4>
        <el-table :data="snapshotRows(detail)" border size="small">
          <el-table-column prop="name" label="核对项" width="150" />
          <el-table-column prop="snap" label="提交时快照" />
          <el-table-column prop="current" label="当前最新现场">
            <template #default="{ row }">
              <span :class="{ 'risk': row.changed }">{{ row.current }}</span>
            </template>
          </el-table-column>
        </el-table>

        <h4 class="block-title">现场四项填报</h4>
        <el-table :data="fieldRows(detail)" border size="small">
          <el-table-column prop="label" label="项目" width="100" />
          <el-table-column label="申报结果" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="row.result === 'OK' ? 'success' : 'danger'">{{ row.resultName }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="现场说明" prop="remark" />
          <el-table-column label="复核" width="110">
            <template #default="{ row }">
              <span v-if="row.checkName">{{ row.checkName }}</span>
              <span v-else class="muted">待复核</span>
            </template>
          </el-table-column>
        </el-table>

        <div v-if="detail.conclusionLimitReason || detail.limitReason" style="margin: 10px 0">
          <span class="muted">限速原因：</span>{{ detail.conclusionLimitReason || detail.limitReason }}
        </div>

        <h4 class="block-title">完整处理轨迹</h4>
        <el-timeline>
          <el-timeline-item
            v-for="t in detail.timeline"
            :key="t.id"
            :timestamp="fmt(t.createdAt) + ' · ' + (t.actor || '系统')"
            :type="timelineType(t.action)"
          >
            <b>{{ t.actionName }}</b>
            <div class="muted" style="white-space: pre-wrap">{{ t.note }}</div>
          </el-timeline-item>
        </el-timeline>

        <h4 v-if="detail.chain && detail.chain.length > 1" class="block-title">同条作业的交接链路</h4>
        <el-table v-if="detail.chain && detail.chain.length > 1" :data="detail.chain" border size="small">
          <el-table-column prop="handoverNo" label="申请单号" width="140" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }"><el-tag size="small" :type="statusTag(row.status)">{{ row.statusName }}</el-tag></template>
          </el-table-column>
          <el-table-column label="提交时间" width="170">
            <template #default="{ row }">{{ fmt(row.submittedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openDetail(row.id)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div style="margin: 16px 0; text-align: right">
          <el-button v-if="detail.status === 'REJECTED' || detail.status === 'EXPIRED'" type="primary" @click="resubmit(detail)">
            {{ detail.status === 'REJECTED' ? '按退回意见重新提交' : '基于最新现场重新提交' }}
          </el-button>
          <el-button v-if="detail.status === 'PENDING'" type="success" @click="goReview(detail)">去复核</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, inject, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { handoverApi, lineApi, defectApi, workOrderApi } from '../api'

const works = inject('works')
const route = useRoute()
const router = useRouter()

const list = ref([])
const lineOptions = ref([])
const sectionOptions = ref([])
const filter = ref({ lineId: null, sectionId: null, status: '' })

const submitVisible = ref(false)
const submitting = ref(false)
const eligibleOrders = ref([])
const submitForm = ref(emptySubmit())
const resubmitSource = ref('')

const reviewVisible = ref(false)
const reviewing = ref(false)
const reviewData = ref(null)
const reviewForm = ref(emptyReview())

const detailVisible = ref(false)
const detail = ref(null)
const detailAutoReview = ref(false)

const checkItems = [
  { key: 'clearance', label: '现场清障' },
  { key: 'evacuation', label: '人员撤离' },
  { key: 'equipment', label: '机具撤出' },
  { key: 'retest', label: '线路复测' }
]

function emptySubmit() {
  return {
    workOrderId: null,
    applicant: '',
    clearanceResult: 'OK',
    evacuationResult: 'OK',
    equipmentResult: 'OK',
    retestResult: 'OK',
    clearanceRemark: '',
    evacuationRemark: '',
    equipmentRemark: '',
    retestRemark: '',
    applyNote: '',
    recommendation: 'SPEED_LIMIT',
    speedLimit: 45,
    limitReason: '',
    reapplyOfId: null
  }
}

function emptyReview() {
  return {
    reviewer: '',
    clearanceCheck: 'CONFIRM',
    evacuationCheck: 'CONFIRM',
    equipmentCheck: 'CONFIRM',
    retestCheck: 'CONFIRM',
    conclusion: 'SPEED_LIMIT',
    conclusionSpeedLimit: 45,
    conclusionLimitReason: '',
    reviewNote: '',
    action: 'APPROVE'
  }
}

const selectedOrder = computed(() => eligibleOrders.value.find((o) => o.id === submitForm.value.workOrderId) || null)
const selectedSection = computed(() => {
  const sid = selectedOrder.value ? selectedOrder.value.sectionId : null
  return sectionOptions.value.find((s) => s.id === sid) || null
})
const heavyDefects = ref([])

async function loadHeavyDefects(sectionId) {
  if (!sectionId) {
    heavyDefects.value = []
    return
  }
  const all = await defectApi.list({ sectionId, severity: 'HEAVY', closed: false })
  heavyDefects.value = all.filter((d) => d.severity === 'HEAVY' && !d.closed)
}

function statusTag(s) {
  return { PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger', EXPIRED: 'info', SUPERSEDED: 'info' }[s] || 'info'
}
function recTag(s) {
  return { NORMAL: 'success', SPEED_LIMIT: 'warning', BLOCKED: 'info' }[s] || 'info'
}
function timelineType(a) {
  return { SUBMIT: 'primary', RESUBMIT: 'primary', RETURN: 'warning', APPROVE: 'success', EXPIRE: 'info' }[a] || ''
}
function fmt(t) {
  return t ? String(t).replace('T', ' ').slice(0, 19) : ''
}

async function loadLinesAndSections() {
  if (!lineOptions.value.length) lineOptions.value = await lineApi.list()
  const sections = []
  for (const l of lineOptions.value) {
    const ss = await lineApi.board(l.id).catch(() => ({ sections: [] }))
    sections.push(...(ss.sections || []))
  }
  sectionOptions.value = sections
}

async function load() {
  const params = {}
  if (filter.value.lineId) params.lineId = filter.value.lineId
  if (filter.value.sectionId) params.sectionId = filter.value.sectionId
  if (filter.value.status) params.status = filter.value.status
  list.value = await handoverApi.list(params)
}

function onFilter() {
  load()
}

async function openCreate(presetOrderId) {
  await loadLinesAndSections()
  eligibleOrders.value = await handoverApi.eligibleOrders()
  submitForm.value = emptySubmit()
  resubmitSource.value = ''
  const target = presetOrderId && eligibleOrders.value.find((o) => o.id === presetOrderId)
  if (target) {
    submitForm.value.workOrderId = target.id
    onPickOrder(target.id)
  }
  submitVisible.value = true
}

function onPickOrder(id) {
  const o = eligibleOrders.value.find((x) => x.id === id)
  if (o) {
    submitForm.value.applicant = o.leader || ''
    const sec = sectionOptions.value.find((s) => s.id === o.sectionId)
    submitForm.value.speedLimit = sec ? Math.min(45, sec.designSpeed) : 45
    loadHeavyDefects(o.sectionId)
  }
}

async function doSubmit() {
  submitting.value = true
  try {
    const saved = await handoverApi.submit(submitForm.value)
    ElMessage.success(submitForm.value.reapplyOfId ? '已重新提交，等待复核' : '申请已提交，等待复核')
    submitVisible.value = false
    await refreshAll()
    openDetail(saved.id)
  } finally {
    submitting.value = false
  }
}

function resetSubmit() {
  submitForm.value = emptySubmit()
  resubmitSource.value = ''
  heavyDefects.value = []
}

async function openDetail(id, autoReview = false) {
  detail.value = await handoverApi.detail(id)
  detailVisible.value = true
  detailAutoReview.value = !!autoReview
  if (autoReview && detail.value.status === 'PENDING') {
    goReview(detail.value)
  }
}

function goReview(row) {
  reviewData.value = row
  reviewForm.value = emptyReview()
  reviewForm.value.conclusion = row.recommendation || 'SPEED_LIMIT'
  reviewForm.value.conclusionSpeedLimit = row.speedLimit || 45
  reviewForm.value.conclusionLimitReason = row.limitReason || ''
  detailVisible.value = false
  reviewVisible.value = true
}

async function doReview(action) {
  if (!reviewForm.value.reviewer.trim()) {
    ElMessage.error('请填写复核人')
    return
  }
  if (reviewForm.value.reviewer.trim() === reviewData.value.applicant) {
    ElMessage.error('复核人必须与施工负责人不是同一人')
    return
  }
  const payload = { ...reviewForm.value, action }
  if (action === 'RETURN') {
    const anyReturn = checkItems.some((it) => reviewForm.value[it.key + 'Check'] === 'RETURN')
    if (!anyReturn) {
      ElMessage.error('退回整改必须至少一个复核项选择退回')
      return
    }
    if (!reviewForm.value.reviewNote.trim()) {
      ElMessage.error('退回整改必须填写整改说明')
      return
    }
  } else {
    const allConfirm = checkItems.every((it) => reviewForm.value[it.key + 'Check'] === 'CONFIRM')
    if (!allConfirm) {
      ElMessage.error('复核通过要求四项全部逐项确认合格')
      return
    }
    if (reviewForm.value.conclusion === 'SPEED_LIMIT') {
      if (!reviewForm.value.conclusionSpeedLimit || reviewForm.value.conclusionSpeedLimit > reviewData.value.designSpeed) {
        ElMessage.error(`限速值必须为 1 ~ ${reviewData.value.designSpeed} km/h`)
        return
      }
      if (!reviewForm.value.conclusionLimitReason.trim()) {
        ElMessage.error('限速必须填写原因')
        return
      }
    }
  }
  reviewing.value = true
  try {
    const saved = await handoverApi.review(reviewData.value.id, payload)
    ElMessage.success(action === 'RETURN' ? '已退回整改' : '复核通过，作业已销号、区段已更新')
    reviewVisible.value = false
    await refreshAll()
    openDetail(saved.id)
  } finally {
    reviewing.value = false
  }
}

async function resubmit(row) {
  reviewVisible.value = false
  detailVisible.value = false
  await loadLinesAndSections()
  // 重提的旧单可能仍是"已退回"（不在可发起列表里），取全部作业单定位该作业
  const [eligible, allOrders] = await Promise.all([
    handoverApi.eligibleOrders(),
    workOrderApi.list()
  ])
  const sourceOrder = allOrders.find((o) => o.id === row.workOrderId)
  const inEligible = eligible.some((o) => o.id === row.workOrderId)
  eligibleOrders.value = inEligible || !sourceOrder ? eligible : [...eligible, sourceOrder]
  const f = emptySubmit()
  f.reapplyOfId = row.id
  f.workOrderId = row.workOrderId
  f.applicant = row.applicant
  f.recommendation = row.recommendation
  f.speedLimit = row.speedLimit || 45
  f.limitReason = row.limitReason || ''
  f.clearanceResult = row.clearanceResult
  f.evacuationResult = row.evacuationResult
  f.equipmentResult = row.equipmentResult
  f.retestResult = row.retestResult
  submitForm.value = f
  resubmitSource.value = row.status
  await loadHeavyDefects(row.sectionId)
  submitVisible.value = true
}

async function refreshAll() {
  await Promise.all([load(), works.reloadBoard(), works.reloadOverview()])
}

function snapshotRows(d) {
  const changed = (a, b) => String(a || '') !== String(b || '')
  const heavySnap = (d.snapHeavyDefects || []).map((x) => x.mileageText + ' ' + x.description).join('；') || '无'
  const heavyNow = (d.currentHeavyDefects || []).map((x) => x.mileageText + ' ' + x.description).join('；') || '无'
  return [
    {
      name: '作业单状态',
      snap: d.snapWorkStatusName,
      current: d.currentWorkStatusName || '—',
      changed: changed(d.snapWorkStatus, d.currentWorkStatus)
    },
    {
      name: '区段状态',
      snap: `${d.snapSectionStatusName}${d.snapSectionSpeed ? ' ' + d.snapSectionSpeed + 'km/h' : ''}`,
      current: d.currentSectionStatus ? `${d.currentSectionStatusName} ${d.currentSectionSpeed}km/h` : '—',
      changed: changed(d.snapSectionStatus, d.currentSectionStatus) || changed(d.snapSectionSpeed, d.currentSectionSpeed)
    },
    {
      name: '未销号重级病害',
      snap: heavySnap,
      current: heavyNow,
      changed: heavySnap !== heavyNow
    }
  ]
}

function fieldRows(d) {
  return checkItems.map((it) => ({
    label: it.label,
    result: d[it.key + 'Result'],
    resultName: d[it.key + 'ResultName'],
    remark: d[it.key + 'Remark'] || '—',
    checkName: d[it.key + 'CheckName']
  }))
}

// 从养修作业页跳转过来：?orderId=xx 直接发起；?handoverId=xx 直达在途申请详情
watch(() => route.query.orderId, (q) => {
  if (route.name === 'handovers' && q) {
    openCreate(Number(q))
    router.replace({ path: '/handovers' })
  }
}, { immediate: true })

watch(() => route.query.handoverId, (q) => {
  if (route.name === 'handovers' && q) {
    openDetail(Number(q))
    router.replace({ path: '/handovers' })
  }
})

watch(() => works.lineId.value, async () => {
  await loadLinesAndSections()
}, { immediate: true })

onMounted(load)
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
.check-block {
  border: 1px solid #e3e8ea;
  border-radius: 6px;
  padding: 10px 12px;
  margin-bottom: 12px;
  background: #fafcfc;
}
.check-block h4 {
  margin: 0 0 8px;
  font-size: 14px;
}
.check-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 8px 0;
}
.check-name {
  width: 76px;
  font-weight: 600;
  font-size: 13px;
}
.snapshot-box {
  border: 1px dashed #b9c6ca;
  border-radius: 6px;
  padding: 10px;
  margin-bottom: 12px;
  background: #f6f9f9;
}
.block-title {
  margin: 16px 0 8px;
  font-size: 14px;
  color: #2d4146;
}
.expire-banner {
  background: #fdecea;
  border: 1px solid #f5c6c0;
  color: #a1261c;
  border-radius: 6px;
  padding: 8px 12px;
  font-size: 13px;
  margin-bottom: 12px;
}
.review-head {
  display: flex;
  align-items: center;
}
.risk {
  color: #c62828;
  font-weight: 700;
}
</style>
