<template>
  <div class="page-wrapper">
    <!-- 页面标题 -->
    <div class="page-title-area">
      <h1>活动创建</h1>
      <p>三步创建营销活动：填写基本信息、配置优惠规则、预览并提交审批</p>
    </div>

    <!-- 双 Tab：创建活动 / 活动管理 -->
    <div class="tab-switcher-custom" style="margin-bottom: 20px">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        class="tab-item"
        :class="{ active: activeTab === tab.key }"
        @click="activeTab = tab.key"
      >
        {{ tab.label }}
      </button>
    </div>

    <!-- ==================== Tab1: 创建活动（三步表单） ==================== -->
    <template v-if="activeTab === 'create'">
      <!-- 步骤条 -->
      <div class="steps-bar">
        <div
          v-for="(s, idx) in steps"
          :key="s.key"
          class="step-item"
          :class="{ active: currentStep === s.key, done: stepIndex(s.key) < stepIndex(currentStep) }"
        >
          <span class="step-dot">{{ stepIndex(s.key) < stepIndex(currentStep) ? '✓' : idx + 1 }}</span>
          <span class="step-label">{{ s.label }}</span>
        </div>
      </div>

      <!-- 第一步：基本信息 -->
      <div v-show="currentStep === 'basic'" class="form-card">
        <div class="form-card-title">📋 基本信息</div>
        <el-form :model="basicForm" label-width="110px">
          <el-form-item label="活动名称" required>
            <el-input v-model="basicForm.name" placeholder="请输入活动名称" maxlength="30" show-word-limit />
          </el-form-item>
          <el-form-item label="活动类型" required>
            <el-select v-model="basicForm.type" placeholder="请选择活动类型" style="width:100%">
              <el-option label="满减" value="满减" />
              <el-option label="折扣" value="折扣" />
              <el-option label="秒杀" value="秒杀" />
              <el-option label="拼团" value="拼团" />
            </el-select>
          </el-form-item>
          <el-form-item label="活动状态">
            <el-select v-model="basicForm.status" style="width:100%">
              <el-option label="未开始" value="UPCOMING" />
              <el-option label="进行中" value="ACTIVE" />
            </el-select>
          </el-form-item>
          <el-form-item label="活动时间范围" required>
            <el-date-picker
              v-model="basicForm.dateRange"
              type="datetimerange"
              range-separator="至"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              style="width:100%"
            />
          </el-form-item>
          <el-form-item label="活动描述">
            <el-input v-model="basicForm.description" type="textarea" :rows="3" placeholder="请输入活动描述信息，包括活动亮点和参与方式..." />
          </el-form-item>
          <el-form-item label="适用商品" required>
            <el-radio-group v-model="basicForm.productScope">
              <el-radio value="全部商品">全部商品</el-radio>
              <el-radio value="指定品类">指定品类</el-radio>
              <el-radio value="指定商品">指定商品</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="选择品类" v-if="basicForm.productScope === '指定品类'">
            <el-checkbox-group v-model="basicForm.categories">
              <el-checkbox value="CARE 洗护">CARE 洗护</el-checkbox>
              <el-checkbox value="GEAR 出行">GEAR 出行</el-checkbox>
              <el-checkbox value="PLAY 玩具">PLAY 玩具</el-checkbox>
              <el-checkbox value="STYLE 服饰">STYLE 服饰</el-checkbox>
              <el-checkbox value="HOME 家居">HOME 家居</el-checkbox>
            </el-checkbox-group>
          </el-form-item>
        </el-form>
        <div class="step-actions">
          <el-button type="primary" @click="nextStep('basic')">下一步</el-button>
        </div>
      </div>

      <!-- 第二步：优惠规则 -->
      <div v-show="currentStep === 'discount'" class="form-card">
        <div class="form-card-title">💰 优惠规则</div>
        <el-form :model="discountForm" label-width="110px">
          <el-form-item label="折扣类型" required>
            <el-select v-model="discountForm.discountType" style="width:100%">
              <el-option label="直降" value="直降" />
              <el-option label="百分比折扣" value="百分比折扣" />
              <el-option label="满减" value="满减" />
            </el-select>
          </el-form-item>
          <el-form-item label="折扣值" required>
            <el-input v-model="discountForm.discountValue" placeholder="例如：30 或 30%" />
          </el-form-item>
          <el-form-item label="每人限用次数">
            <el-input-number v-model="discountForm.perLimit" :min="0" style="width:200px" />
          </el-form-item>
          <el-form-item label="预算上限(USD)">
            <el-input-number v-model="discountForm.budget" :min="0" :precision="2" style="width:200px" />
          </el-form-item>
          <el-form-item label="库存限制">
            <el-switch v-model="discountForm.stockLimit" active-text="启用库存限制（活动优惠券总发放量上限）" />
          </el-form-item>
          <el-form-item label="发放总量" v-if="discountForm.stockLimit">
            <el-input-number v-model="discountForm.totalCount" :min="0" style="width:200px" />
          </el-form-item>
        </el-form>
        <div class="step-actions">
          <el-button @click="prevStep('basic')">上一步</el-button>
          <el-button type="primary" @click="nextStep('discount')">下一步</el-button>
        </div>
      </div>

      <!-- 第三步：预览发布 -->
      <div v-show="currentStep === 'publish'" class="form-card">
        <div class="form-card-title">👁️ 预览效果</div>
        <!-- 预览摘要 -->
        <div class="preview-box">
          <div class="preview-row"><span>活动名称</span><b>{{ basicForm.name || '（未填写）' }}</b></div>
          <div class="preview-row"><span>活动类型</span><b>{{ basicForm.type || '（未选择）' }}</b></div>
          <div class="preview-row"><span>折扣规则</span><b>{{ discountForm.discountType }} {{ discountForm.discountValue }}</b></div>
          <div class="preview-row"><span>适用商品</span><b>{{ basicForm.productScope }}</b></div>
          <div class="preview-row"><span>时间范围</span><b>{{ dateRangeText }}</b></div>
          <div class="preview-row" v-if="basicForm.description"><span>活动描述</span><p>{{ basicForm.description }}</p></div>
        </div>

        <div class="form-card-title" style="margin-top:20px">👤 审批信息</div>
        <el-form :model="publishForm" label-width="110px">
          <el-form-item label="审批人">
            <el-select v-model="publishForm.approver" style="width:100%">
              <el-option label="张经理 - 营销总监" value="张经理" />
              <el-option label="李主管 - 运营负责人" value="李主管" />
              <el-option label="王总监 - 市场部" value="王总监" />
            </el-select>
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="publishForm.remark" placeholder="提交审批备注（选填）" />
          </el-form-item>
        </el-form>
        <div class="step-actions">
          <el-button @click="prevStep('discount')">上一步</el-button>
          <el-button @click="saveDraft">保存草稿</el-button>
          <el-button type="primary" @click="submitCampaign">提交审批</el-button>
        </div>
      </div>
    </template>

    <!-- ==================== Tab2: 活动管理（保留原 CRUD） ==================== -->
    <template v-else>
      <el-card shadow="never" class="filter-card">
        <el-form :model="filters" inline>
          <el-form-item label="关键词">
            <el-input v-model="filters.keyword" placeholder="请输入活动名称" clearable />
          </el-form-item>
          <el-form-item label="活动类型">
            <el-select v-model="filters.type" placeholder="全部类型" clearable style="width:140px">
              <el-option label="全部" value="" />
              <el-option label="满减" value="满减" />
              <el-option label="折扣" value="折扣" />
              <el-option label="秒杀" value="秒杀" />
              <el-option label="拼团" value="拼团" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="filters.status" placeholder="全部状态" clearable style="width:140px">
              <el-option label="全部" value="" />
              <el-option label="未开始" value="未开始" />
              <el-option label="进行中" value="进行中" />
              <el-option label="已结束" value="已结束" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleSearch">搜索</el-button>
            <el-button @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
      </el-card>
      <el-card shadow="never">
        <el-table :data="tableData" stripe>
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="name" label="活动名称" min-width="160" />
          <el-table-column prop="type" label="活动类型" width="100">
            <template #default="{ row }">
              <el-tag :type="typeTag(row.type)" size="small">{{ row.type }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="startDate" label="开始时间" width="170" />
          <el-table-column prop="endDate" label="结束时间" width="170" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.status)" size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="handleEdit(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div style="display:flex;justify-content:flex-end;padding:16px 0 0">
          <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :total="total"
            layout="total, sizes, prev, pager, next"
            @change="loadData"
          />
        </div>
      </el-card>
      <!-- 新建/编辑活动对话框 -->
      <el-dialog v-model="dialogVisible" :title="dialogTitle" width="700px">
        <el-form :model="editForm" label-width="100px">
          <el-form-item label="活动名称" required>
            <el-input v-model="editForm.name" placeholder="请输入活动名称" />
          </el-form-item>
          <el-form-item label="活动类型" required>
            <el-select v-model="editForm.type" placeholder="请选择" style="width:100%">
              <el-option label="满减" value="满减" />
              <el-option label="折扣" value="折扣" />
              <el-option label="秒杀" value="秒杀" />
              <el-option label="拼团" value="拼团" />
            </el-select>
          </el-form-item>
          <el-form-item label="时间范围" required>
            <el-date-picker
              v-model="dateRange"
              type="datetimerange"
              range-separator="至"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              style="width:100%"
            />
          </el-form-item>
          <el-form-item label="活动描述">
            <el-input v-model="editForm.description" type="textarea" :rows="3" placeholder="请输入活动描述" />
          </el-form-item>
          <el-form-item label="活动预算">
            <el-input-number v-model="editForm.budget" :min="0" :precision="2" style="width:200px" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSave">保存</el-button>
        </template>
      </el-dialog>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCampaigns, createCampaign, updateCampaign, deleteCampaign, saveCampaignDraft } from '../api/admin'
import { toArray } from '../utils/safeArray'

// ===== Tab 状态 =====
const tabs = [
  { key: 'create', label: '创建活动' },
  { key: 'manage', label: '活动管理' }
]
const activeTab = ref('create')

// ===== 步骤条 =====
const steps = [
  { key: 'basic', label: '基本信息' },
  { key: 'discount', label: '优惠规则' },
  { key: 'publish', label: '预览发布' }
]
const currentStep = ref('basic')

function stepIndex(key) {
  return steps.findIndex(s => s.key === key)
}
function nextStep(from) {
  // 基本信息校验
  if (from === 'basic') {
    if (!basicForm.name) { ElMessage.warning('请输入活动名称'); return }
    if (!basicForm.type) { ElMessage.warning('请选择活动类型'); return }
    if (!basicForm.dateRange || basicForm.dateRange.length !== 2) { ElMessage.warning('请选择活动时间范围'); return }
  }
  currentStep.value = steps[stepIndex(from) + 1].key
}
function prevStep(to) {
  currentStep.value = to
}

// ===== 第一步：基本信息 =====
const basicForm = reactive({
  name: '',
  type: '',
  status: 'UPCOMING',
  dateRange: null,
  description: '',
  productScope: '全部商品',
  categories: ['CARE 洗护', 'GEAR 出行', 'PLAY 玩具']
})

const dateRangeText = computed(() => {
  if (!basicForm.dateRange || basicForm.dateRange.length !== 2) return '（未选择）'
  return basicForm.dateRange[0].toLocaleString() + ' 至 ' + basicForm.dateRange[1].toLocaleString()
})

// ===== 第二步：优惠规则 =====
const discountForm = reactive({
  discountType: '百分比折扣',
  discountValue: '30%',
  perLimit: 1,
  budget: 50000,
  stockLimit: true,
  totalCount: 10000
})

// ===== 第三步：预览发布 =====
const publishForm = reactive({
  approver: '张经理',
  remark: ''
})

// 提交审批：调用 createCampaign
async function submitCampaign() {
  if (!basicForm.name || !basicForm.type) {
    ElMessage.warning('请填写完整基本信息')
    activeTab.value = 'create'
    currentStep.value = 'basic'
    return
  }
  try {
    await createCampaign({
      name: basicForm.name,
      type: basicForm.type,
      description: basicForm.description,
      budget: discountForm.budget,
      startDate: basicForm.dateRange ? basicForm.dateRange[0].toISOString() : '',
      endDate: basicForm.dateRange ? basicForm.dateRange[1].toISOString() : ''
    })
    ElMessage.success('活动已创建并提交审批')
    resetForm()
    await loadCampaigns()
  } catch (e) {
    ElMessage.error('提交失败，请重试')
  }
}

// 保存草稿（真实后端：写入 marketing_campaign 表，状态 DRAFT）
async function saveDraft() {
  if (!basicForm.name) { ElMessage.warning('请输入活动名称'); return }
  try {
    const res = await saveCampaignDraft({
      name: basicForm.name,
      type: basicForm.type,
      description: basicForm.description,
      startDate: basicForm.dateRange && basicForm.dateRange[0] || undefined,
      endDate: basicForm.dateRange && basicForm.dateRange[1] || undefined,
      budget: basicForm.budget
    })
    ElMessage.success('草稿已保存：' + (res?.id ? '#' + res.id : basicForm.name))
  } catch (e) {
    ElMessage.error('草稿保存失败：' + (e?.message || '未知错误'))
  }
}

// 重置创建表单
function resetForm() {
  basicForm.name = ''
  basicForm.type = ''
  basicForm.status = 'UPCOMING'
  basicForm.dateRange = null
  basicForm.description = ''
  basicForm.productScope = '全部商品'
  basicForm.categories = ['CARE 洗护', 'GEAR 出行', 'PLAY 玩具']
  discountForm.discountType = '百分比折扣'
  discountForm.discountValue = '30%'
  discountForm.perLimit = 1
  discountForm.budget = 50000
  discountForm.stockLimit = true
  discountForm.totalCount = 10000
  publishForm.approver = '张经理'
  publishForm.remark = ''
  currentStep.value = 'basic'
}

// ===== 活动管理（保留原功能） =====
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const dateRange = ref(null)

const filters = reactive({
  keyword: '',
  type: '',
  status: ''
})

const editForm = reactive({
  id: null,
  name: '',
  type: '',
  description: '',
  budget: 0
})

// 活动数据列表
const allCampaigns = ref([])
const tableData = ref([])

function typeTag(type) {
  const map = { '满减': 'success', '折扣': 'warning', '秒杀': 'danger', '拼团': 'primary' }
  return map[type] || ''
}

function statusTag(status) {
  const map = { '未开始': 'info', '进行中': 'success', '已结束': 'danger' }
  return map[status] || ''
}

// 从API加载活动列表
async function loadCampaigns() {
  try {
    const data = await getCampaigns()
    const rawList = toArray(data)
    allCampaigns.value = rawList.map(item => ({
      id: item.id,
      name: item.name || '',
      type: item.type || '',
      startDate: item.startDate || '',
      endDate: item.endDate || '',
      status: item.status || '未开始',
      description: item.description || '',
      budget: item.budget ?? 0
    }))
    applyFilters()
  } catch (e) {
    console.error('获取活动列表失败', e)
  }
}

// 根据筛选条件过滤并分页
function applyFilters() {
  const kw = filters.keyword.toLowerCase()
  const filtered = allCampaigns.value.filter(d => {
    if (kw && !d.name.toLowerCase().includes(kw)) return false
    if (filters.type && d.type !== filters.type) return false
    if (filters.status && d.status !== filters.status) return false
    return true
  })
  total.value = filtered.length
  const start = (currentPage.value - 1) * pageSize.value
  tableData.value = filtered.slice(start, start + pageSize.value)
}

function loadData() {
  applyFilters()
}

function handleSearch() {
  currentPage.value = 1
  loadData()
}

function handleReset() {
  filters.keyword = ''
  filters.type = ''
  filters.status = ''
  handleSearch()
}

function handleEdit(row) {
  isEdit.value = true
  dialogTitle.value = '编辑活动'
  Object.assign(editForm, row)
  dateRange.value = [new Date(row.startDate), new Date(row.endDate)]
  dialogVisible.value = true
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确认删除该活动吗？', '提示', { type: 'warning' })
    await deleteCampaign(row.id)
    ElMessage.success('删除成功')
    await loadCampaigns()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败: ' + (e.message || '未知错误'))
    }
  }
}

async function handleSave() {
  if (!editForm.name || !editForm.type) {
    ElMessage.warning('请填写必要信息')
    return
  }
  try {
    if (isEdit.value) {
      await updateCampaign(editForm.id, {
        name: editForm.name,
        type: editForm.type,
        description: editForm.description,
        budget: editForm.budget,
        startDate: dateRange.value ? dateRange.value[0].toISOString() : '',
        endDate: dateRange.value ? dateRange.value[1].toISOString() : ''
      })
      ElMessage.success('编辑成功')
    } else {
      await createCampaign({
        name: editForm.name,
        type: editForm.type,
        description: editForm.description,
        budget: editForm.budget,
        startDate: dateRange.value ? dateRange.value[0].toISOString() : '',
        endDate: dateRange.value ? dateRange.value[1].toISOString() : ''
      })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    await loadCampaigns()
  } catch (e) {
    ElMessage.error('保存失败，请重试')
  }
}

onMounted(() => { loadCampaigns() })
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-title-area { margin-bottom: 24px; }
.page-title-area h1 { font-size: 22px; font-weight: 700; color: var(--text-800); margin: 0 0 6px; }
.page-title-area p { font-size: 13px; color: var(--text-400); margin: 0; }

/* 步骤条 */
.steps-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 24px;
  padding: 16px 24px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
}
.step-item {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
}
.step-dot {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  background: var(--background-200);
  color: var(--text-400);
  flex-shrink: 0;
}
.step-item.active .step-dot {
  background: var(--primary);
  color: #fff;
}
.step-item.done .step-dot {
  background: var(--state-success);
  color: #fff;
}
.step-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-500);
  white-space: nowrap;
}
.step-item.active .step-label {
  color: var(--text-800);
  font-weight: 600;
}

/* 表单卡片 */
.form-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 24px;
  box-shadow: var(--shadow-xs);
}
.form-card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-800);
  margin-bottom: 20px;
}
.step-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 8px;
}

/* 预览摘要 */
.preview-box {
  border: 1px dashed var(--border);
  border-radius: var(--radius);
  padding: 16px 20px;
}
.preview-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 6px 0;
  font-size: 13px;
}
.preview-row span {
  width: 90px;
  flex-shrink: 0;
  color: var(--text-400);
}
.preview-row b {
  color: var(--text-700);
  font-weight: 500;
}
.preview-row p {
  margin: 0;
  color: var(--text-600);
  line-height: 1.6;
}

/* 活动管理 */
.filter-card { margin-bottom: 16px; }
</style>
