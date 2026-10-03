<template>
  <div>
    <section class="panel">
      <div class="panel-head">
        <h3>物资库存</h3>
        <span class="panel-sub">低于安全库存整行标红，缺口 = 安全库存 − 现有库存</span>
        <div class="panel-extra">
          <el-button size="small" @click="loadMaterials">刷新</el-button>
          <el-button size="small" type="primary" @click="openMaterialCreate">新增物资</el-button>
        </div>
      </div>
      <el-table :data="materials" border stripe size="small" :row-class-name="materialRowClass">
        <el-table-column prop="name" label="物资名称" width="150" />
        <el-table-column prop="spec" label="规格" min-width="180" />
        <el-table-column prop="unit" label="单位" width="70" />
        <el-table-column prop="stockQty" label="库存数量" width="100" />
        <el-table-column prop="safetyStock" label="安全库存" width="100" />
        <el-table-column label="缺口" width="90">
          <template #default="{ row }">
            <span v-if="row.low" style="color: #c62828; font-weight: 700">-{{ row.gap }}</span>
            <span v-else class="muted">充足</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openStock(row, 1)">入库</el-button>
            <el-button link type="warning" @click="openStock(row, -1)">出库</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="panel">
      <div class="panel-head">
        <h3>机具与检修</h3>
        <span class="panel-sub">距下次检修 ≤ 30 天标黄，已逾期标红；报废机具不再排检修</span>
        <div class="panel-extra">
          <el-button size="small" @click="loadEquipments">刷新</el-button>
          <el-button size="small" type="primary" @click="openEquipmentCreate">新增机具</el-button>
        </div>
      </div>
      <el-table :data="equipments" border stripe size="small" :row-class-name="equipRowClass">
        <el-table-column prop="equipNo" label="机具编号" width="110" />
        <el-table-column prop="name" label="名称" width="140" />
        <el-table-column prop="model" label="型号" width="110" />
        <el-table-column prop="lastMaintainDate" label="上次检修日" width="120" />
        <el-table-column prop="nextMaintainDate" label="下次检修日" width="120" />
        <el-table-column label="检修倒计时" width="150">
          <template #default="{ row }">
            <span v-if="row.status === 'SCRAP'" class="muted">—</span>
            <span v-else-if="row.daysToMaintain < 0" style="color: #c62828; font-weight: 700">
              已逾期 {{ -row.daysToMaintain }} 天
            </span>
            <span v-else :style="{ color: row.daysToMaintain <= 30 ? '#e08700' : '', fontWeight: row.daysToMaintain <= 30 ? 700 : 400 }">
              剩 {{ row.daysToMaintain }} 天
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="equipTagType(row.status)">{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status !== 'SCRAP'" link type="primary" @click="maintain(row)">检修登记</el-button>
            <span v-else class="muted">已报废</span>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="materialVisible" title="新增物资" width="420px">
      <el-form :model="materialForm" label-width="96px">
        <el-form-item label="物资名称"><el-input v-model="materialForm.name" /></el-form-item>
        <el-form-item label="规格"><el-input v-model="materialForm.spec" /></el-form-item>
        <el-form-item label="单位"><el-input v-model="materialForm.unit" style="width: 120px" /></el-form-item>
        <el-form-item label="库存数量"><el-input-number v-model="materialForm.stockQty" :min="0" :step="10" /></el-form-item>
        <el-form-item label="安全库存"><el-input-number v-model="materialForm.safetyStock" :min="0" :step="10" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="materialVisible = false">取消</el-button>
        <el-button type="primary" @click="doMaterialCreate">入账</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="stockVisible" :title="(stockQty > 0 ? '入库：' : '出库：') + actMaterial.name" width="380px">
      <el-form label-width="96px">
        <el-form-item label="现有库存"><b>{{ actMaterial.stockQty }} {{ actMaterial.unit }}</b></el-form-item>
        <el-form-item label="数量"><el-input-number v-model="absQty" :min="1" :step="10" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="stockVisible = false">取消</el-button>
        <el-button type="primary" @click="doStock">确认</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="equipmentVisible" title="新增机具" width="420px">
      <el-form :model="equipmentForm" label-width="100px">
        <el-form-item label="机具编号"><el-input v-model="equipmentForm.equipNo" placeholder="如 EQ-0011" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="equipmentForm.name" /></el-form-item>
        <el-form-item label="型号"><el-input v-model="equipmentForm.model" /></el-form-item>
        <el-form-item label="上次检修日"><el-date-picker v-model="equipmentForm.lastMaintainDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="下次检修日"><el-date-picker v-model="equipmentForm.nextMaintainDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="equipmentForm.status">
            <el-option label="可用" value="AVAILABLE" /><el-option label="在用" value="IN_USE" />
            <el-option label="检修" value="MAINTAIN" /><el-option label="报废" value="SCRAP" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="equipmentVisible = false">取消</el-button>
        <el-button type="primary" @click="doEquipmentCreate">入账</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { inject, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { equipmentApi, materialApi } from '../api'

const works = inject('works')
const materials = ref([])
const equipments = ref([])

const materialVisible = ref(false)
const equipmentVisible = ref(false)
const stockVisible = ref(false)
const actMaterial = ref({})
const stockQty = ref(1)
const absQty = ref(10)
const materialForm = ref({ name: '', spec: '', unit: '件', stockQty: 0, safetyStock: 0 })
const equipmentForm = ref({ equipNo: '', name: '', model: '', lastMaintainDate: '', nextMaintainDate: '', status: 'AVAILABLE' })

function materialRowClass({ row }) {
  return row.low ? 'row-heavy' : ''
}

function equipRowClass({ row }) {
  if (row.status === 'SCRAP') return 'row-done'
  if (row.daysToMaintain < 0) return 'row-heavy'
  if (row.daysToMaintain <= 30) return 'row-low'
  return ''
}

function equipTagType(status) {
  return { AVAILABLE: 'success', IN_USE: 'primary', MAINTAIN: 'warning', SCRAP: 'info' }[status] || 'info'
}

async function loadMaterials() {
  materials.value = await materialApi.list()
}

async function loadEquipments() {
  equipments.value = await equipmentApi.list()
}

function openMaterialCreate() {
  materialForm.value = { name: '', spec: '', unit: '件', stockQty: 0, safetyStock: 0 }
  materialVisible.value = true
}

async function doMaterialCreate() {
  await materialApi.create(materialForm.value)
  ElMessage.success('物资已入账')
  materialVisible.value = false
  await Promise.all([loadMaterials(), works.reloadOverview()])
}

function openStock(row, direction) {
  actMaterial.value = row
  stockQty.value = direction
  absQty.value = 10
  stockVisible.value = true
}

async function doStock() {
  await materialApi.adjustStock(actMaterial.value.id, stockQty.value > 0 ? absQty.value : -absQty.value)
  ElMessage.success(stockQty.value > 0 ? '入库完成' : '出库完成')
  stockVisible.value = false
  await Promise.all([loadMaterials(), works.reloadOverview()])
}

function openEquipmentCreate() {
  equipmentForm.value = { equipNo: '', name: '', model: '', lastMaintainDate: '', nextMaintainDate: '', status: 'AVAILABLE' }
  equipmentVisible.value = true
}

async function doEquipmentCreate() {
  await equipmentApi.create(equipmentForm.value)
  ElMessage.success('机具已入账')
  equipmentVisible.value = false
  await Promise.all([loadEquipments(), works.reloadOverview()])
}

async function maintain(row) {
  await equipmentApi.maintain(row.id, { maintainDate: new Date().toISOString().slice(0, 10) })
  ElMessage.success('检修已登记，已排下次检修日')
  await Promise.all([loadEquipments(), works.reloadOverview()])
}

onMounted(() => {
  loadMaterials()
  loadEquipments()
})
</script>
