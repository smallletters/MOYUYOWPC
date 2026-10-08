<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>{{ pageTitle }}</h2>
    </div>
    <el-card shadow="never">
      <el-tabs v-model="activeTab" type="card">
        <!-- Tab 1: 发货策略（兼容老 mo_shipping_strategy 列表，只读） -->
        <el-tab-pane label="发货策略" name="strategies">
          <div class="tab-toolbar">
            <el-button type="primary" @click="handleAdd">新建策略</el-button>
          </div>
          <el-table :data="strategyTable" stripe>
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="strategyName" label="策略名称" width="160" />
            <el-table-column prop="region" label="适用区域" width="130" />
            <el-table-column prop="zoneName" label="发货区域" width="120">
              <template #default="{ row }">
                <el-tag size="small" type="info">{{ row.zoneName }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="发货方式" width="120">
              <template #default="{ row }">
                <el-tag :type="row.shippingMethod === '快递' ? 'primary' : row.shippingMethod === '海运' ? 'warning' : 'success'">{{ row.shippingMethod }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="feeRule" label="运费计算规则" width="200" show-overflow-tooltip />
            <el-table-column prop="priority" label="优先级" width="80" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === '启用' ? 'success' : 'danger'">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
                <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- Tab 2: 配送方式字典（mo_shipping_method，只读展示） -->
        <el-tab-pane label="配送方式" name="methods">
          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="配送方式由后端字典表维护，目前包含 4 种：标准/快递/优先/当日达。如需新增，请联系后端开发。"
            style="margin-bottom: 12px"
          />
          <el-table :data="methodTable" stripe>
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="code" label="编码" width="120" />
            <el-table-column prop="nameZh" label="中文名" width="160" />
            <el-table-column prop="nameEn" label="英文名" width="180" />
            <el-table-column label="预计送达" width="160">
              <template #default="{ row }">
                {{ row.etaMinDays }}-{{ row.etaMaxDays }} 天
              </template>
            </el-table-column>
            <el-table-column prop="sortOrder" label="排序" width="80" />
          </el-table>
        </el-tab-pane>

        <!-- Tab 3: 运费规则（核心 CRUD） -->
        <el-tab-pane label="运费规则" name="rates">
          <div class="tab-toolbar">
            <el-select
              v-model="rateFilterZoneId"
              placeholder="筛选：发货区域"
              clearable
              style="width: 200px"
              @change="loadRates"
            >
              <el-option
                v-for="z in zoneOptions"
                :key="z.id"
                :label="`${z.name} (${z.countryCodes})`"
                :value="z.id"
              />
            </el-select>
            <el-select
              v-model="rateFilterStatus"
              placeholder="筛选：状态"
              clearable
              style="width: 140px"
              @change="loadRates"
            >
              <el-option label="启用" value="ACTIVE" />
              <el-option label="停用" value="INACTIVE" />
            </el-select>
            <el-button type="primary" @click="handleAddRate">新建运费规则</el-button>
            <el-button @click="loadRates">刷新</el-button>
          </div>
          <el-table :data="rateTable" stripe v-loading="rateLoading">
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column label="发货区域" min-width="160">
              <template #default="{ row }">
                <el-tag size="small" type="info">{{ zoneMap.get(row.zoneId)?.name || row.zoneId }}</el-tag>
                <span style="margin-left:6px;color:#909399;font-size:12px">{{ zoneMap.get(row.zoneId)?.countryCodes }}</span>
              </template>
            </el-table-column>
            <el-table-column label="配送方式" width="120">
              <template #default="{ row }">
                <el-tag size="small">{{ methodMap.get(row.methodId)?.nameZh || methodMap.get(row.methodId)?.code || row.methodId }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="计费方式" width="100">
              <template #default="{ row }">
                <el-tag :type="chargeTypeColor(row.chargeType)" size="small">{{ chargeTypeLabel(row.chargeType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="首费" width="100">
              <template #default="{ row }">
                <span>${{ Number(row.firstCharge || 0).toFixed(2) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="续费/单位" width="160">
              <template #default="{ row }">
                <span v-if="row.chargeType === 2">${{ Number(row.continueCharge || 0).toFixed(2) }} / {{ row.continueUnit }}g</span>
                <span v-else>${{ Number(row.continueCharge || 0).toFixed(2) }} / {{ row.continueUnit }}件</span>
              </template>
            </el-table-column>
            <el-table-column label="免邮门槛" width="120">
              <template #default="{ row }">
                <span v-if="row.freeThreshold != null" style="color:#067d62;font-weight:600">满 ${{ Number(row.freeThreshold).toFixed(2) }} 免邮</span>
                <span v-else style="color:#909399">—</span>
              </template>
            </el-table-column>
            <el-table-column prop="priority" label="优先级" width="80" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'">{{ row.status === 'ACTIVE' ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="handleEditRate(row)">编辑</el-button>
                <el-button type="danger" link size="small" @click="handleDeleteRate(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 老策略编辑弹窗（保留兼容） -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="700px">
      <el-form :model="editForm" label-width="120px">
        <el-form-item label="策略名称">
          <el-input v-model="editForm.strategyName" placeholder="请输入策略名称" />
        </el-form-item>
        <el-form-item label="适用区域">
          <el-input v-model="editForm.region" placeholder="如：华东地区" />
        </el-form-item>
        <el-form-item label="发货区域">
          <el-select v-model="editForm.zoneId" placeholder="选择发货区域" style="width:100%">
            <el-option
              v-for="z in zoneOptions"
              :key="z.id"
              :label="`${z.name} (${z.countryCodes})`"
              :value="z.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="发货方式">
          <el-select v-model="editForm.shippingMethod">
            <el-option label="快递" value="快递" />
            <el-option label="海运" value="海运" />
            <el-option label="空运" value="空运" />
          </el-select>
        </el-form-item>
        <el-form-item label="运费计算规则">
          <el-input v-model="editForm.feeRule" placeholder="如：首重10元，续重5元/kg" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="editForm.priority" :min="1" :max="99" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="editForm.status">
            <el-option label="启用" value="启用" />
            <el-option label="停用" value="停用" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 运费规则编辑弹窗 -->
    <el-dialog v-model="rateDialogVisible" :title="rateDialogTitle" width="780px" @closed="onRateDialogClosed">
      <el-form :model="rateForm" :rules="rateRules" ref="rateFormRef" label-width="140px">
        <el-form-item label="发货区域" prop="zoneId">
          <el-select v-model="rateForm.zoneId" placeholder="选择发货区域" style="width:100%" :disabled="!!rateForm.id">
            <el-option
              v-for="z in zoneOptions"
              :key="z.id"
              :label="`${z.name} (${z.countryCodes})`"
              :value="z.id" />
          </el-select>
          <div style="font-size:12px;color:#909399;margin-top:4px">区域一旦设定不可修改；如需更换请先停用再新建</div>
        </el-form-item>
        <el-form-item label="配送方式" prop="methodId">
          <el-select v-model="rateForm.methodId" placeholder="选择配送方式" style="width:100%" :disabled="!!rateForm.id">
            <el-option
              v-for="m in methodTable"
              :key="m.id"
              :label="`${m.nameZh} / ${m.nameEn}（${m.code}）`"
              :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="计费方式" prop="chargeType">
          <el-radio-group v-model="rateForm.chargeType">
            <el-radio :value="1">按件</el-radio>
            <el-radio :value="2">按重（克）</el-radio>
            <el-radio :value="3">按金额</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="首费（USD）" prop="firstCharge">
          <el-input-number v-model="rateForm.firstCharge" :min="0" :precision="2" :step="0.5" style="width:200px" />
        </el-form-item>
        <el-form-item :label="unitLabel(rateForm.chargeType)">
          <el-input-number v-model="rateForm.firstUnit" :min="1" :max="9999" :step="1" style="width:200px" />
        </el-form-item>
        <el-form-item label="续费（USD）" prop="continueCharge">
          <el-input-number v-model="rateForm.continueCharge" :min="0" :precision="2" :step="0.5" style="width:200px" />
        </el-form-item>
        <el-form-item :label="continueUnitLabel(rateForm.chargeType)">
          <el-input-number v-model="rateForm.continueUnit" :min="1" :max="99999" :step="1" style="width:200px" />
        </el-form-item>
        <el-form-item label="满额免邮门槛">
          <el-input-number v-model="rateForm.freeThreshold" :min="0" :precision="2" :step="1" style="width:200px" />
          <span style="margin-left:8px;color:#909399;font-size:12px">留空 = 不包邮；填 0 = 全场包邮</span>
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="rateForm.priority" :min="0" :max="999" :step="1" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="rateForm.status">
            <el-radio value="ACTIVE">启用</el-radio>
            <el-radio value="INACTIVE">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="rateForm.remark" type="textarea" :rows="2" placeholder="如：北美标准配送：满 $59 免邮" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rateDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="rateSaving" @click="handleSaveRate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getShippingStrategies, createShippingStrategy, updateShippingStrategy, deleteShippingStrategy,
  getShippingZones, createShippingZone, updateShippingZone, deleteShippingZone,
  getShippingRates, createShippingRate, updateShippingRate, deleteShippingRate,
  getShippingMethods, createShippingMethod, updateShippingMethod, deleteShippingMethod
} from '../api/admin'

const pageTitle = '发货策略与运费规则'
const activeTab = ref('rates') // 默认进入核心 tab

// ============ Tab 1: 发货策略（兼容老数据） ============
const strategyTable = ref([])
const zoneOptions = ref([])
// zoneId -> zone 实体（运费规则页要用）
const zoneMap = computed(() => {
  const m = new Map()
  for (const z of zoneOptions.value || []) m.set(z.id, z)
  return m
})
const dialogVisible = ref(false)
const dialogTitle = ref('')
const editForm = reactive({
  strategyName: '', region: '', zoneId: 1, shippingMethod: '快递', feeRule: '', priority: 1, status: '启用'
})

async function loadStrategyData() {
  try {
    // Bug X 修复：只拉策略列表；zones 在 onMounted 已加载过，这里复用避免覆盖
    const list = await getShippingStrategies().catch(() => [])
    const zoneMapLocal = new Map((zoneOptions.value || []).map(z => [z.id, z]))
    strategyTable.value = (list || []).map(r => ({
      ...r,
      zoneName: zoneMapLocal.get(r.zoneId)?.name || '-'
    }))
  } catch (err) {
    console.error('获取发货策略失败', err)
  }
}
function handleSearch() { /* 老 tab 内已无搜索栏，no-op */ }
function handleReset() { /* no-op */ }
function handleAdd() {
  dialogTitle.value = '新建策略'
  editForm.strategyName = ''
  editForm.region = ''
  editForm.zoneId = (zoneOptions.value[0] && zoneOptions.value[0].id) || 1
  editForm.shippingMethod = '快递'
  editForm.feeRule = ''
  editForm.priority = 1
  editForm.status = '启用'
  dialogVisible.value = true
}
function handleEdit(row) {
  dialogTitle.value = '编辑策略'
  Object.assign(editForm, row)
  if (!editForm.zoneId) editForm.zoneId = (zoneOptions.value[0] && zoneOptions.value[0].id) || 1
  dialogVisible.value = true
}
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确定删除？', '提示', { type: 'warning' })
    await deleteShippingStrategy(row.id)
    ElMessage.success('删除成功')
    await loadStrategyData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败: ' + (e.message || '未知错误'))
  }
}
async function handleSave() {
  try {
    const payload = {
      strategyName: editForm.strategyName,
      region: editForm.region,
      zoneId: editForm.zoneId,
      shippingMethod: editForm.shippingMethod,
      feeRule: editForm.feeRule,
      priority: editForm.priority,
      status: editForm.status
    }
    if (editForm.id) {
      await updateShippingStrategy(editForm.id, payload)
    } else {
      await createShippingStrategy(payload)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadStrategyData()
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.message || '未知错误'))
  }
}

// ============ Tab 2: 配送方式字典 ============
// 后端提供 GET /shipping-methods 接口；前端动态拉取
const methodTable = ref([])
// methodId -> method 实体（运费规则页要用）
const methodMap = computed(() => {
  const m = new Map()
  for (const it of methodTable.value || []) m.set(it.id, it)
  return m
})

async function loadMethods() {
  try {
    methodTable.value = (await getShippingMethods()) || []
  } catch (e) {
    ElMessage.error('获取配送方式失败: ' + (e.message || '未知错误'))
    methodTable.value = []
  }
}

// ============ Tab 3: 运费规则（核心） ============
const rateTable = ref([])
const rateLoading = ref(false)
const rateSaving = ref(false)
const rateFilterZoneId = ref(null)
const rateFilterStatus = ref(null)
const rateDialogVisible = ref(false)
const rateDialogTitle = ref('')
const rateFormRef = ref(null)
const rateForm = reactive({
  id: null,
  zoneId: null,
  methodId: null,
  chargeType: 1,
  firstCharge: 0,
  firstUnit: 1,
  continueCharge: 0,
  continueUnit: 1,
  freeThreshold: null,
  currency: 'USD',
  priority: 10,
  status: 'ACTIVE',
  remark: ''
})
const rateRules = {
  zoneId: [{ required: true, message: '请选择发货区域', trigger: 'change' }],
  methodId: [{ required: true, message: '请选择配送方式', trigger: 'change' }],
  chargeType: [{ required: true, message: '请选择计费方式', trigger: 'change' }],
  firstCharge: [{ required: true, message: '请填写首费', trigger: 'blur' }],
  continueCharge: [{ required: true, message: '请填写续费', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

async function loadRates() {
  rateLoading.value = true
  try {
    const params = {}
    if (rateFilterZoneId.value != null) params.zoneId = rateFilterZoneId.value
    if (rateFilterStatus.value) params.status = rateFilterStatus.value
    const list = await getShippingRates(params)
    rateTable.value = list || []
  } catch (e) {
    ElMessage.error('获取运费规则失败: ' + (e.message || '未知错误'))
    rateTable.value = []
  } finally {
    rateLoading.value = false
  }
}

function chargeTypeLabel(t) {
  return { 1: '按件', 2: '按重', 3: '按金额' }[t] || '-'
}
function chargeTypeColor(t) {
  return { 1: 'primary', 2: 'warning', 3: 'success' }[t] || 'info'
}
function unitLabel(t) { return t === 2 ? '首重数量（克）' : '首件/首单位数量' }
function continueUnitLabel(t) { return t === 2 ? '续重单位（克）' : '续件单位' }

function handleAddRate() {
  rateDialogTitle.value = '新建运费规则'
  // Bug U 修复：清掉筛选（避免用户误以为新规则只对当前筛选区域生效）
  rateFilterZoneId.value = null
  rateFilterStatus.value = null
  Object.assign(rateForm, {
    id: null,
    zoneId: (zoneOptions.value[0] && zoneOptions.value[0].id) || null,
    methodId: null,
    chargeType: 1,
    firstCharge: 0,
    firstUnit: 1,
    continueCharge: 0,
    continueUnit: 1,
    freeThreshold: null,
    currency: 'USD',
    priority: 10,
    status: 'ACTIVE',
    remark: ''
  })
  rateDialogVisible.value = true
  // 下一帧清空校验状态
  setTimeout(() => rateFormRef.value && rateFormRef.value.clearValidate(), 0)
}
function handleEditRate(row) {
  rateDialogTitle.value = '编辑运费规则'
  Object.assign(rateForm, {
    id: row.id,
    zoneId: row.zoneId,
    methodId: row.methodId,
    chargeType: row.chargeType || 1,
    firstCharge: Number(row.firstCharge || 0),
    firstUnit: Number(row.firstUnit || 1),
    continueCharge: Number(row.continueCharge || 0),
    continueUnit: Number(row.continueUnit || 1),
    freeThreshold: row.freeThreshold == null ? null : Number(row.freeThreshold),
    currency: row.currency || 'USD',
    priority: Number(row.priority ?? 10),
    status: row.status || 'ACTIVE',
    remark: row.remark || ''
  })
  rateDialogVisible.value = true
  setTimeout(() => rateFormRef.value && rateFormRef.value.clearValidate(), 0)
}
function onRateDialogClosed() {
  rateFormRef.value && rateFormRef.value.resetFields()
}
async function handleSaveRate() {
  if (!rateFormRef.value) return
  try {
    await rateFormRef.value.validate()
  } catch (e) {
    return // 校验失败
  }
  // Bug S 修复：el-input-number 在用户清空时 model 为 null，Number(null) = 0
  // 会导致"用户没填首费"被当成 0 元首费提交 → 等于全场包邮。显式校验非空。
  if (rateForm.firstCharge === null || rateForm.firstCharge === undefined
      || rateForm.continueCharge === null || rateForm.continueCharge === undefined) {
    ElMessage.error('首费和续费不能为空')
    return
  }
  if (rateForm.firstUnit === null || rateForm.firstUnit === undefined
      || rateForm.continueUnit === null || rateForm.continueUnit === undefined) {
    ElMessage.error('首件/续件单位不能为空')
    return
  }
  rateSaving.value = true
  try {
    // BigDecimal 序列化兼容性：保证 2 位小数
    const payload = {
      zoneId: rateForm.zoneId,
      methodId: rateForm.methodId,
      chargeType: rateForm.chargeType,
      firstCharge: Number(rateForm.firstCharge).toFixed(2),
      firstUnit: rateForm.firstUnit,
      continueCharge: Number(rateForm.continueCharge).toFixed(2),
      continueUnit: rateForm.continueUnit,
      // freeThreshold: 显式 null 表示"不包邮"；空字符串当作 null 处理
      freeThreshold: rateForm.freeThreshold == null || rateForm.freeThreshold === ''
        ? null
        : Number(rateForm.freeThreshold).toFixed(2),
      currency: rateForm.currency,
      priority: rateForm.priority,
      status: rateForm.status,
      remark: rateForm.remark || ''
    }
    if (rateForm.id) {
      // partial update：后端只接收部分字段
      const updatePayload = {
        chargeType: payload.chargeType,
        firstCharge: payload.firstCharge,
        firstUnit: payload.firstUnit,
        continueCharge: payload.continueCharge,
        continueUnit: payload.continueUnit,
        freeThreshold: payload.freeThreshold,
        priority: payload.priority,
        status: payload.status,
        remark: payload.remark
      }
      await updateShippingRate(rateForm.id, updatePayload)
    } else {
      await createShippingRate(payload)
    }
    ElMessage.success(rateForm.id ? '更新成功' : '创建成功')
    rateDialogVisible.value = false
    await loadRates()
  } catch (e) {
    // 提取后端 message：axios 错误结构是 e.response.data.message
    const msg = (e && e.response && e.response.data && e.response.data.message)
      || (e && e.message)
      || '未知错误'
    ElMessage.error((rateForm.id ? '更新失败: ' : '创建失败: ') + msg)
  } finally {
    rateSaving.value = false
  }
}
async function handleDeleteRate(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除 (zone=${row.zoneId}, method=${row.methodId}) 的运费规则？此操作不可恢复。`,
      '提示',
      { type: 'warning' }
    )
    await deleteShippingRate(row.id)
    ElMessage.success('删除成功')
    await loadRates()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败: ' + (e.message || '未知错误'))
  }
}

// ============ 初始化 ============
onMounted(async () => {
  // zones 一次拉取给所有 tab 共享
  try {
    const zones = await getShippingZones()
    zoneOptions.value = zones || []
    // Bug W 修复：拉取失败/为空时给用户明确提示（之前 try/catch 静默吞了）
    if (zoneOptions.value.length === 0) {
      ElMessage.warning('未获取到任何发货区域，请先在"区域管理"中创建')
    }
  } catch (e) {
    ElMessage.error('获取发货区域失败: ' + (e.message || '未知错误'))
    zoneOptions.value = []
  }
  // 配送方式字典（运费规则 tab 需要 methodMap 解析 nameZh）
  await loadMethods()
  // 默认 tab 是 rates
  await loadRates()
  await loadStrategyData()
})

// 切到 strategies tab 时刷新（避免初次挂载时 zoneOptions 还没就绪）
watch(activeTab, (val) => {
  if (val === 'strategies' && strategyTable.value.length === 0) loadStrategyData()
})
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
/* tab 顶部筛选 + 操作按钮栏：使用 gap 控制间距（避免依赖子元素各自写 margin-right） */
.tab-toolbar { display: flex; align-items: center; margin-bottom: 12px; gap: 12px; flex-wrap: wrap; }
</style>
