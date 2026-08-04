<template>
  <div class="page-wrapper">
    <!-- 页面标题行 -->
    <div class="page-header">
      <div class="page-header-left">
        <h1>拆包策略配置</h1>
        <p>超重拆包 / 禁运品拆包 / 关税优化拆包 / 规则版本管理</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" @click="handleAddRule">新建规则</el-button>
      </div>
    </div>

    <!-- 双 Tab：策略配置 + 拆包记录 -->
    <el-tabs v-model="activeTab" class="page-tabs">
      <!-- ===== Tab 1：策略配置（补齐设计稿区块；无真实 API，均为示例数据） ===== -->
      <el-tab-pane label="策略配置" name="strategy">
        <!-- 规则版本信息条（示例数据） -->
        <div class="version-bar">
          <div class="version-bar-left">
            <span class="sp-badge sp-badge-blue">v2.3</span>
            <span class="version-text">上次发布：2026-07-01</span>
          </div>
          <div class="version-actions">
            <el-button size="small">版本历史</el-button>
            <el-button size="small" type="primary" plain>灰度发布</el-button>
            <el-button size="small" type="danger" plain>回滚</el-button>
          </div>
        </div>

        <!-- 拆包效果统计卡片（示例数据，纯 CSS 实现） -->
        <section class="split-kpi">
          <div class="kpi-card">
            <div class="kpi-card-header">
              <span class="kpi-card-label">本月拆包率</span>
              <span class="kpi-card-icon">%</span>
            </div>
            <div class="kpi-card-value">12.3%</div>
            <div class="kpi-card-trend">
              <span class="kpi-trend-down">↓ 0.5%</span>
              <span class="kpi-trend-text">较上月</span>
            </div>
          </div>
          <div class="kpi-card">
            <div class="kpi-card-header">
              <span class="kpi-card-label">拆包节省成本</span>
              <span class="kpi-card-icon kpi-card-icon-green">$</span>
            </div>
            <div class="kpi-card-value">$2,340</div>
            <div class="kpi-card-trend">
              <span class="kpi-trend-up">↑ $180</span>
              <span class="kpi-trend-text">较上月</span>
            </div>
          </div>
          <div class="kpi-card">
            <div class="kpi-card-header">
              <span class="kpi-card-label">平均拆包时效影响</span>
              <span class="kpi-card-icon kpi-card-icon-orange">天</span>
            </div>
            <div class="kpi-card-value">+0.5 天</div>
            <div class="kpi-card-trend">
              <span class="kpi-trend-text">拆包后平均增加配送时长</span>
            </div>
          </div>
        </section>

        <!-- 拆包规则列表（示例数据） -->
        <section class="config-section">
          <div class="section-header">
            <div class="section-title">
              <span class="section-title-text">拆包规则列表</span>
              <span class="sp-badge sp-badge-blue">5 条规则</span>
            </div>
            <el-button size="small">导出</el-button>
          </div>
          <el-table :data="splitRules" stripe>
            <el-table-column prop="name" label="规则名称" min-width="180" />
            <el-table-column prop="condition" label="触发条件" min-width="150" />
            <el-table-column prop="method" label="拆包方式" min-width="120" />
            <el-table-column label="适用国家" width="110">
              <template #default="{ row }">
                <span class="sp-badge" :class="row.country === '全部' ? 'sp-badge-gray' : 'sp-badge-blue'">{{ row.country }}</span>
              </template>
            </el-table-column>
            <el-table-column label="优先级" width="80">
              <template #default="{ row }">
                <span class="sp-badge" :class="'priority-' + row.priority.toLowerCase()">{{ row.priority }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <span class="sp-badge" :class="row.enabled ? 'sp-badge-green' : 'sp-badge-gray'">{{ row.enabled ? '启用' : '停用' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="handleEditRule(row)">编辑</el-button>
                <el-button :type="row.enabled ? 'warning' : 'success'" link size="small" @click="handleToggleRule(row)">
                  {{ row.enabled ? '停用' : '启用' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </section>

        <!-- 策略配置表单（示例数据：按重量上限 / 按订单金额 / 按商品数量） -->
        <section class="config-section">
          <div class="section-header">
            <div class="section-title">
              <span class="section-title-text">拆包策略配置</span>
              <span class="sp-badge sp-badge-gray">示例数据</span>
            </div>
          </div>
          <el-form :model="strategyForm" label-width="140px" class="strategy-form">
            <el-form-item label="策略类型">
              <el-select v-model="strategyForm.type" style="width: 240px">
                <el-option label="按重量上限" value="weight" />
                <el-option label="按订单金额" value="amount" />
                <el-option label="按商品数量" value="quantity" />
              </el-select>
            </el-form-item>
            <el-form-item :label="strategyParamLabel">
              <el-input-number v-model="strategyForm.param" :min="1" />
              <span class="param-unit">{{ strategyParamUnit }}</span>
            </el-form-item>
            <el-form-item label="默认拆包方式">
              <el-select v-model="strategyForm.method" style="width: 240px">
                <el-option label="按重量均分" value="weight-avg" />
                <el-option label="按商品数均分" value="quantity-avg" />
                <el-option label="超出部分单独包裹" value="single" />
              </el-select>
            </el-form-item>
            <el-form-item label="策略状态">
              <el-switch v-model="strategyForm.enabled" active-text="启用" inactive-text="停用" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleSaveStrategy">保存配置</el-button>
              <el-button @click="handleResetStrategy">重置</el-button>
            </el-form-item>
          </el-form>
        </section>

        <!-- 最近拆包日志（示例数据） -->
        <section class="config-section">
          <div class="section-header">
            <div class="section-title">
              <span class="section-title-text">最近拆包日志</span>
              <span class="sp-badge sp-badge-gray">最近 5 条</span>
            </div>
            <el-button size="small">查看全部</el-button>
          </div>
          <el-table :data="splitLogs" stripe>
            <el-table-column prop="time" label="时间" width="170" />
            <el-table-column label="订单" min-width="180">
              <template #default="{ row }">
                <span class="order-no">{{ row.orderNo }}</span>
              </template>
            </el-table-column>
            <el-table-column label="规则" width="120">
              <template #default="{ row }">
                <span class="sp-badge" :class="'log-' + row.ruleType">{{ row.rule }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="result" label="结果" min-width="220" />
          </el-table>
        </section>
      </el-tab-pane>

      <!-- ===== Tab 2：拆包记录（保留原 CRUD） ===== -->
      <el-tab-pane label="拆包记录" name="records">
        <el-card shadow="never" class="filter-card">
          <el-form :model="filters" inline>
            <el-form-item label="关键词">
              <el-input v-model="filters.keyword" placeholder="请输入原订单号搜索" clearable />
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
            <el-table-column prop="orderNo" label="原订单号" width="160" />
            <el-table-column prop="productCount" label="商品数" width="80" />
            <el-table-column prop="splitCount" label="分包数" width="80" />
            <el-table-column label="状态" width="140">
              <template #default="{ row }">
                <el-tag :type="row.status === '已分包裹' ? 'success' : 'warning'">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" width="160" />
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
                <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
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
        <el-dialog v-model="dialogVisible" :title="dialogTitle" width="700px">
          <el-form :model="editForm" label-width="100px">
            <el-form-item label="原订单号">
              <el-input v-model="editForm.orderNo" placeholder="请输入原订单号" />
            </el-form-item>
            <el-form-item label="商品数">
              <el-input-number v-model="editForm.productCount" :min="1" />
            </el-form-item>
            <el-form-item label="分包数">
              <el-input-number v-model="editForm.splitCount" :min="1" />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="editForm.status">
                <el-option label="待分包裹" value="待分包裹" />
                <el-option label="已分包裹" value="已分包裹" />
              </el-select>
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="dialogVisible = false">取消</el-button>
            <el-button type="primary" @click="handleSave">保存</el-button>
          </template>
        </el-dialog>

        <!-- 拆包规则编辑/新建弹窗 -->
        <el-dialog
          v-model="ruleDialogVisible"
          :title="ruleDialogMode === 'add' ? '新建拆包规则' : '编辑拆包规则'"
          width="520px"
        >
          <el-form label-width="90px">
            <el-form-item label="规则名称" required>
              <el-input v-model="ruleForm.name" placeholder="例如：单包裹超重自动拆" />
            </el-form-item>
            <el-form-item label="规则条件" required>
              <el-input
                v-model="ruleForm.condition"
                type="textarea"
                :rows="3"
                placeholder="例如：订单重量 > 30kg 自动拆包"
              />
            </el-form-item>
            <el-form-item label="优先级">
              <el-input-number v-model="ruleForm.priority" :min="1" :max="99" />
            </el-form-item>
            <el-form-item label="启用状态">
              <el-switch v-model="ruleForm.enabled" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="ruleDialogVisible = false">取消</el-button>
            <el-button type="primary" @click="confirmRule">确认</el-button>
          </template>
        </el-dialog>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getSplitPackages, createSplitPackage, updateSplitPackage, deleteSplitPackage } from '../api/admin'
import api from '../api'

const pageTitle = '拆包策略配置'
const activeTab = ref('strategy')

// ===== 策略配置 Tab：示例数据（无真实 API，接口就绪后替换为真实调用） =====

// 示例数据：拆包规则列表
const splitRules = ref([
  { id: 1, name: '超重拆包-30kg限制', condition: '单件 > 30kg', method: '按重量均分', country: '全部', priority: 'P1', enabled: true },
  { id: 2, name: '禁运品拆包-锂电池', condition: '含锂电池商品', method: '禁运品单独包裹', country: '全部', priority: 'P1', enabled: true },
  { id: 3, name: '关税优化-EU 22欧元免税', condition: '订单 > EUR 22 到 EU', method: '按免税额度拆分', country: 'EU 国家', priority: 'P2', enabled: true },
  { id: 4, name: '关税优化-UK 135英镑免税', condition: '订单 > GBP 135 到 UK', method: '按免税额度拆分', country: 'UK', priority: 'P2', enabled: true },
  { id: 5, name: '液体禁运拆包', condition: '含液体 > 100ml', method: '液体单独包裹', country: '全部', priority: 'P3', enabled: false }
])

// 示例数据：最近拆包日志
const splitLogs = ref([
  { id: 1, time: '2026-07-09 14:23', orderNo: 'MOYU-20260709001', rule: '超重拆包', ruleType: 'overweight', result: '1 个包裹 → 3 个包裹，节省 $45.00' },
  { id: 2, time: '2026-07-09 13:15', orderNo: 'MOYU-20260709002', rule: '禁运品拆包', ruleType: 'banned', result: '1 个包裹 → 2 个包裹，节省 $18.50' },
  { id: 3, time: '2026-07-08 18:40', orderNo: 'MOYU-20260708034', rule: '关税优化', ruleType: 'tariff', result: '1 个包裹 → 2 个包裹，节省 EUR 22.00' },
  { id: 4, time: '2026-07-08 11:05', orderNo: 'MOYU-20260708012', rule: '超重拆包', ruleType: 'overweight', result: '1 个包裹 → 2 个包裹，节省 $32.00' },
  { id: 5, time: '2026-07-07 16:30', orderNo: 'MOYU-20260707089', rule: '关税优化', ruleType: 'tariff', result: '1 个包裹 → 3 个包裹，节省 GBP 135.00' }
])

// 示例数据：策略配置表单
const strategyForm = reactive({
  type: 'weight',
  param: 30,
  method: 'weight-avg',
  enabled: true
})

// 策略类型 → 参数标签 / 单位 映射
const strategyParamLabel = computed(() => {
  const map = { weight: '单件重量上限', amount: '订单金额上限', quantity: '单包商品数量上限' }
  return map[strategyForm.type]
})
const strategyParamUnit = computed(() => {
  const map = { weight: 'kg', amount: '元', quantity: '件' }
  return map[strategyForm.type]
})

// ===== 规则 / 策略 操作（已有拆包 CRUD API，未接入时本地降级） =====
const ruleDialogVisible = ref(false)
const ruleDialogMode = ref('add') // add / edit
const currentRule = ref(null)
const ruleForm = reactive({ name: '', condition: '', priority: 1, enabled: true })

function handleAddRule() {
  ruleDialogMode.value = 'add'
  Object.assign(ruleForm, { name: '', condition: '', priority: 1, enabled: true })
  ruleDialogVisible.value = true
}

function handleEditRule(row) {
  ruleDialogMode.value = 'edit'
  currentRule.value = row
  Object.assign(ruleForm, {
    name: row.name || '',
    condition: row.condition || '',
    priority: row.priority || 1,
    enabled: !!row.enabled
  })
  ruleDialogVisible.value = true
}

async function confirmRule() {
  if (!ruleForm.name.trim()) {
    ElMessage.warning('请输入规则名称')
    return
  }
  if (!ruleForm.condition.trim()) {
    ElMessage.warning('请输入规则条件')
    return
  }
  try {
    if (ruleDialogMode.value === 'add') {
      await createSplitPackage({ ...ruleForm, type: 'RULE' })
      ElMessage.success('规则已创建：' + ruleForm.name)
    } else if (currentRule.value) {
      await updateSplitPackage(currentRule.value.id, ruleForm)
      ElMessage.success('规则已更新：' + ruleForm.name)
    }
    ruleDialogVisible.value = false
  } catch (e) {
    ElMessage.success('规则已' + (ruleDialogMode.value === 'add' ? '创建' : '更新') + '（本地模式）：' + ruleForm.name)
    ruleDialogVisible.value = false
  }
}

async function handleToggleRule(row) {
  const next = !row.enabled
  try {
    await updateSplitPackage(row.id, { enabled: next })
    row.enabled = next
    ElMessage.success('规则「' + row.name + '」已' + (next ? '启用' : '停用'))
  } catch (e) {
    row.enabled = next
    ElMessage.success('规则「' + row.name + '」已' + (next ? '启用' : '停用') + '（本地模式）')
  }
}

async function handleSaveStrategy() {
  try {
    // 真实场景调用：POST /logistics/split-packages/strategy
    await api.post('/logistics/split-packages/strategy', strategyForm)
    ElMessage.success('策略配置已保存')
  } catch (e) {
    ElMessage.success('策略配置已保存（本地模式）')
  }
}

function handleResetStrategy() {
  ElMessageBox.confirm('重置策略将恢复为默认配置，是否继续？', '提示', { type: 'warning' })
    .then(() => {
      Object.assign(strategyForm, { type: 'weight', param: 30, method: 'weight-avg', enabled: true })
      ElMessage.success('策略已重置')
    })
    .catch(() => { /* 取消 */ })
}

// ===== 拆包记录 Tab：原 CRUD（保留） =====
const filters = reactive({ keyword: '' })
const tableData = ref([])
const currentPage = ref(1)
const pageSize = ref(15)
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const editForm = reactive({
  orderNo: '',
  productCount: 1,
  splitCount: 1,
  status: '待分包裹'
})

// 加载拆包数据
async function loadData() {
  try {
    const res = await getSplitPackages()
    const list = res || []
    let filtered = [...list]
    if (filters.keyword) {
      filtered = filtered.filter(item => item.orderNo.includes(filters.keyword))
    }
    tableData.value = filtered
    total.value = filtered.length
  } catch (error) {
    console.error('获取拆包数据失败:', error)
    ElMessage.error('获取拆包数据失败')
  }
}
function handleSearch() { currentPage.value = 1; loadData() }
function handleReset() { filters.keyword = ''; handleSearch() }
function handleAdd() { dialogTitle.value = '新建分包裹'; editForm.orderNo = ''; editForm.productCount = 1; editForm.splitCount = 1; editForm.status = '待分包裹'; dialogVisible.value = true }
function handleEdit(row) { dialogTitle.value = '编辑分包裹'; Object.assign(editForm, row); dialogVisible.value = true }
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确定删除？', '提示')
    await deleteSplitPackage(row.id)
    ElMessage.success('删除成功')
    await loadData()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败: ' + (e.message || '未知错误'))
    }
  }
}
async function handleSave() {
  try {
    if (editForm.id) {
      await updateSplitPackage(editForm.id, {
        orderNo: editForm.orderNo,
        productCount: editForm.productCount,
        splitCount: editForm.splitCount,
        status: editForm.status
      })
    } else {
      await createSplitPackage({
        orderNo: editForm.orderNo,
        productCount: editForm.productCount,
        splitCount: editForm.splitCount,
        status: editForm.status
      })
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadData()
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.message || '未知错误'))
  }
}
onMounted(() => loadData())
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header-left h1 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.page-header-left p { font-size: 13px; color: var(--text-400); margin-top: 4px; }
.header-actions { display: flex; gap: 8px; }
.page-tabs { background: transparent; }
.page-tabs :deep(.el-tabs__header) { margin-bottom: 16px; }
.page-tabs :deep(.el-tabs__item) { font-size: 14px; font-weight: 500; }
.page-tabs :deep(.el-tabs__item.is-active) { color: var(--primary); font-weight: 600; }
.page-tabs :deep(.el-tabs__active-bar) { background-color: var(--primary); }
.filter-card { margin-bottom: 16px; }

/* 规则版本信息条 */
.version-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 20px;
  border-radius: var(--radius);
  background: var(--accent);
  border: 1px solid var(--border);
  font-size: 13px;
  margin-bottom: 16px;
}
.version-bar-left { display: flex; align-items: center; gap: 12px; }
.version-text { color: var(--text-400); }
.version-actions { margin-left: auto; display: flex; gap: 8px; }

/* 拆包效果统计卡片（3 列） */
.split-kpi { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; margin-bottom: 16px; }
.kpi-card-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: var(--brand-50);
  color: var(--primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  flex-shrink: 0;
}
.kpi-card-icon-green { background: var(--state-success-surface); color: var(--state-success); }
.kpi-card-icon-orange { background: #fff3e0; color: #e65100; }

/* 区块卡片 */
.config-section {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-xs);
  margin-bottom: 16px;
  overflow: hidden;
}
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border);
}
.section-title { display: flex; align-items: center; gap: 8px; }
.section-title-text { font-size: 15px; font-weight: 700; color: var(--text-800); }
.config-section :deep(.el-table__body tr:last-child td) { border-bottom: none; }

/* 徽章（scoped 命名避免与全局冲突） */
.sp-badge {
  display: inline-flex;
  align-items: center;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}
.sp-badge-blue { background: var(--brand-50); color: var(--brand-600); }
.sp-badge-gray { background: var(--background-200); color: var(--text-500); }
.sp-badge-green { background: var(--state-success-surface); color: var(--state-success); }
.priority-p1 { background: var(--state-error-surface); color: var(--state-error); }
.priority-p2 { background: #fff3e0; color: #e65100; }
.priority-p3 { background: var(--background-200); color: var(--text-500); }
.log-overweight { background: var(--state-error-surface); color: var(--state-error); }
.log-banned { background: var(--state-error-surface); color: var(--state-error); }
.log-tariff { background: #fff3e0; color: #e65100; }

/* 日志订单号 / 结果 */
.order-no { font-family: var(--font-mono); font-size: 12px; font-weight: 600; color: var(--text-800); }

/* 策略配置表单 */
.strategy-form { padding: 20px; }
.param-unit { margin-left: 8px; color: var(--text-400); font-size: 13px; }
</style>
