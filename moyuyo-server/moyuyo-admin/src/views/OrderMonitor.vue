<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>订单监控</h2>
    </div>
    <!-- KPI 卡片 -->
    <el-row :gutter="16" class="kpi-row">
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card">
            <div class="kpi-label">今日订单</div>
            <div class="kpi-value" style="color:var(--brand-500)">{{ kpiData.todayOrders }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card">
            <div class="kpi-label">待发货</div>
            <div class="kpi-value" style="color:var(--state-warning)">{{ kpiData.pendingShip }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card">
            <div class="kpi-label">异常订单</div>
            <div class="kpi-value" style="color:var(--state-error)">{{ kpiData.abnormalOrders }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card">
            <div class="kpi-label">拦截中</div>
            <div class="kpi-value" style="color:var(--state-warning)">{{ kpiData.intercepting }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <!-- 异常订单列表 -->
    <el-card shadow="never">
      <!-- 异常类型筛选：与 KPI 统计口径一致，默认展示全部异常 -->
      <div class="filter-bar">
        <el-radio-group v-model="currentTab" @change="handleTabChange">
          <el-radio-button value="all">全部异常</el-radio-button>
          <el-radio-button value="timeoutPayment">超时未付款</el-radio-button>
          <el-radio-button value="timeoutShip">超时未发货</el-radio-button>
          <el-radio-button value="intercepted">已拦截</el-radio-button>
          <el-radio-button value="cancelled">已取消</el-radio-button>
        </el-radio-group>
      </div>
      <el-table :data="tableData" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="orderNo" label="订单编号" width="170" />
        <el-table-column prop="product" label="商品" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.product || '-' }}</template>
        </el-table-column>
        <el-table-column prop="amount" label="金额" width="110">
          <template #default="{ row }">{{ formatAmount(row.amount, row.currency) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="abnormalType" label="异常类型" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.abnormalType" :type="abnormalTagType(row.abnormalType)" size="small">
              {{ row.abnormalType }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="170">
          <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="display:flex;justify-content:flex-end;padding:16px 0 0">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @change="scheduleLoadList"
        />
      </div>
    </el-card>

    <!-- 自动处理规则 -->
    <el-card shadow="never" class="rules-card">
      <template #header>
        <div class="rules-card-header">
          <span class="rules-card-title">自动处理规则</span>
          <div class="header-actions">
            <el-button type="primary" size="small" @click="handleAddRule">新建规则</el-button>
          </div>
        </div>
      </template>
      <el-table :data="ruleList" stripe>
        <el-table-column prop="name" label="规则名称" min-width="160" />
        <el-table-column prop="sceneText" label="触发场景" min-width="170" />
        <el-table-column prop="condition" label="触发条件" min-width="200" show-overflow-tooltip />
        <el-table-column prop="action" label="自动动作" width="110">
          <template #default="{ row }">
            <el-tag :type="actionTagType(row.action)" size="small">{{ row.action }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="已执行" width="90">
          <template #default="{ row }">
            <el-tooltip v-if="row.lastExecTime" :content="'最近执行：' + row.lastExecTime" placement="top">
              <span>{{ row.execCount }} 次</span>
            </el-tooltip>
            <span v-else>{{ row.execCount }} 次</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" @change="handleToggleRule(row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEditRule(row)">编辑</el-button>
            <el-button link size="small" @click="handleShowLogs(row)">记录</el-button>
            <el-button type="danger" link size="small" @click="handleDeleteRule(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 规则执行记录 -->
    <el-dialog v-model="logDialogVisible" :title="'执行记录 - ' + logRuleName" width="760px">
      <div class="log-actions">
        <el-button type="warning" size="small" @click="handleResetRule">重新评估当前规则</el-button>
        <span class="log-actions-tip">重置后下一轮按当前条件重新处理订单</span>
      </div>
      <el-table :data="logList" size="small" stripe max-height="420">
        <el-table-column prop="orderNo" label="订单号" width="180" />
        <el-table-column prop="actionText" label="动作" width="100" />
        <el-table-column prop="resultText" label="结果" width="90">
          <template #default="{ row }">
            <el-tag :type="row.result === 'SUCCESS' ? 'success' : (row.result === 'FAILED' ? 'danger' : 'info')" size="small">
              {{ row.resultText }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="retryCount" label="重试" width="70" />
        <el-table-column prop="createTime" label="执行时间" width="160" />
        <el-table-column prop="message" label="说明" min-width="180" show-overflow-tooltip />
      </el-table>
      <div style="display:flex;justify-content:flex-end;padding:12px 0 0">
        <el-pagination
          v-model:current-page="logPage"
          v-model:page-size="logPageSize"
          :total="logTotal"
          layout="total, prev, pager, next"
          @change="loadLogs"
        />
      </div>
    </el-dialog>

    <!-- 新建/编辑规则对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEditing ? '编辑规则' : '新建规则'"
      width="600px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form :model="formData" label-width="90px" label-position="top">
        <el-form-item label="规则名称" required>
          <el-input v-model="formData.name" placeholder="输入规则名称" maxlength="50" show-word-limit />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="触发场景" required>
              <el-select v-model="formData.scene" style="width:100%" @change="handleSceneChange">
                <el-option v-for="s in SCENE_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="thresholdLabel" required>
              <el-input-number
                v-model="formData.threshold"
                :min="1"
                :max="isAmountScene ? 100 : 720"
                :precision="0"
                style="width:100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="条件说明">
          <el-input
            v-model="formData.condition"
            type="textarea"
            :rows="2"
            placeholder="选填，留空将按场景和阈值自动生成"
          />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="自动动作" required>
              <el-select v-model="formData.action" style="width:100%">
                <el-option v-for="act in actionOptions" :key="act" :label="act" :value="act" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="启用状态">
              <el-switch v-model="formData.enabled" active-text="启用" inactive-text="禁用" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveRule">
          {{ isEditing ? '保存修改' : '创建规则' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMonitorData, getAbnormalOrders, orderMonitorRuleApi, getOrderMonitorRuleLogs, resetOrderMonitorRule } from '../api/admin'
import { toArray } from '../utils/safeArray'
import { useRouter } from 'vue-router'

const router = useRouter()

const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
// 异常类型页签：all 与后端统计口径一致（超时未付 ∪ 超时未发 ∪ 已拦截）
const currentTab = ref('all')

const kpiData = reactive({
  todayOrders: 0,
  pendingShip: 0,
  abnormalOrders: 0,
  intercepting: 0
})

const tableData = ref([])

// 加载KPI统计（后端 Long 统一序列化为字符串，这里统一转 Number 展示）
async function loadKpi() {
  try {
    const stats = (await getMonitorData()) || {}
    kpiData.todayOrders = Number(stats.todayOrders) || 0
    kpiData.pendingShip = Number(stats.pendingShip) || 0
    kpiData.abnormalOrders = Number(stats.abnormalOrders) || 0
    kpiData.intercepting = Number(stats.intercepting) || 0
  } catch (error) {
    console.error('获取订单监控统计失败:', error)
    ElMessage.error('获取订单监控统计失败')
  }
}

// 加载异常订单列表（服务端分页）
async function loadList() {
  try {
    const listData = (await getAbnormalOrders({
      abnormalType: currentTab.value,
      page: currentPage.value,
      size: pageSize.value
    })) || {}
    tableData.value = toArray(listData)
    total.value = Number(listData.total) || 0
  } catch (error) {
    console.error('获取异常订单列表失败:', error)
    ElMessage.error('获取异常订单列表失败')
  }
}

// 合并同一轮内的重复触发（切页签改页码 + el-pagination 的 change 事件会同时触发）
let listTimer = null
function scheduleLoadList() {
  if (listTimer) return
  listTimer = setTimeout(() => {
    listTimer = null
    loadList()
  }, 50)
}

// 切换异常类型页签：回到第一页并重新拉取列表
function handleTabChange() {
  currentPage.value = 1
  scheduleLoadList()
}

// 币种符号映射：订单默认 USD，未知币种直接展示币种代码
const CURRENCY_SYMBOL = { USD: '$', CNY: '¥', EUR: '€', GBP: '£', JPY: '¥' }

// 金额展示：按订单币种渲染，缺失币种按 USD 兜底
function formatAmount(amount, currency) {
  if (amount == null) return '-'
  const code = String(currency || 'USD').toUpperCase()
  const symbol = CURRENCY_SYMBOL[code]
  return symbol ? symbol + amount : `${amount} ${code}`
}

// 时间格式化为 yyyy-MM-dd HH:mm，空值显示 -
function formatTime(t) {
  if (!t) return '-'
  try {
    const d = new Date(t)
    if (isNaN(d.getTime())) return String(t)
    const pad = n => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
  } catch {
    return String(t)
  }
}

// 异常类型标签颜色：拦截/超时为告警色，已取消为中性色
function abnormalTagType(type) {
  if (type === '已拦截') return 'danger'
  if (type === '超时未付款' || type === '超时未发货') return 'warning'
  return 'info'
}

async function handleDetail(row) {
  // 跳转到订单详情页：携带订单 ID。如果仅有订单号尝试按 orderNo 回退。
  const orderId = row.id || row.orderId
  if (orderId) {
    router.push('/orders/' + orderId)
    return
  }
  // 没有 ID 时尝试按订单号查询
  if (row.orderNo) {
    try {
      ElMessage.info('正在定位订单：' + row.orderNo)
      // 简化处理：提示用户从订单列表按订单号检索
      ElMessage.warning('请到订单管理页按订单号 ' + row.orderNo + ' 检索查看')
      router.push('/orders')
    } catch (e) {
      ElMessage.error('订单详情获取失败')
    }
  } else {
    ElMessage.warning('当前记录缺少订单标识，无法查看详情')
  }
}

// ==================== 自动处理规则 ====================
// 规则列表：从后端拉取真实规则，失败时展示空态
const ruleList = ref([])
const ruleLoaded = ref(false)

// 规则动作：后端编码 ↔ 前端中文
const ACTION_TEXT = { FLAG: '标记提醒', INTERCEPT: '拦截冻结', CANCEL: '自动取消' }
const ACTION_CODE = { '标记提醒': 'FLAG', '拦截冻结': 'INTERCEPT', '自动取消': 'CANCEL' }

// 触发场景：与后端 OrderMonitorRuleJob 支持的场景保持一致
const SCENE_OPTIONS = [
  { value: 'TIMEOUT_PAY', label: '超时未付款' },
  { value: 'TIMEOUT_SHIP', label: '超时未发货' },
  { value: 'LOGISTICS_STALL', label: '物流停滞' },
  { value: 'AMOUNT_DEVIATION', label: '金额偏差' }
]

// 场景支持的动作：与后端 SCENE_ACTIONS 校验保持一致
const SCENE_ACTIONS = {
  TIMEOUT_PAY: ['CANCEL', 'INTERCEPT', 'FLAG'],
  TIMEOUT_SHIP: ['INTERCEPT', 'FLAG'],
  LOGISTICS_STALL: ['FLAG'],
  AMOUNT_DEVIATION: ['INTERCEPT', 'FLAG']
}

// 场景默认阈值：超时类 24 小时，金额偏差 50%
function defaultThreshold(scene) {
  return scene === 'AMOUNT_DEVIATION' ? 50 : 24
}

function sceneLabel(value) {
  const scene = SCENE_OPTIONS.find(s => s.value === value)
  return scene ? scene.label : '未配置'
}

async function loadRules() {
  try {
    const res = await orderMonitorRuleApi.list()
    // 后端字段为 snake_case，需要映射为页面字段并转中文动作
    ruleList.value = toArray(res).map(r => {
      const scene = r.scene || ''
      const thresholdText = scene === 'AMOUNT_DEVIATION'
        ? `${r.threshold_percent}%`
        : `${r.threshold_hours}h`
      return {
        id: r.id,
        name: r.name || '-',
        scene,
        sceneText: scene ? `${sceneLabel(scene)} · ${thresholdText}` : '未配置',
        thresholdHours: r.threshold_hours == null ? null : Number(r.threshold_hours),
        thresholdPercent: r.threshold_percent == null ? null : Number(r.threshold_percent),
        condition: r.condition_text || r.condition || '-',
        action: ACTION_TEXT[r.action_type] || r.action || '标记提醒',
        enabled: r.enabled === true || r.enabled === 1,
        priority: r.priority == null ? 100 : r.priority,
        execCount: Number(r.exec_count) || 0,
        lastExecTime: r.last_exec_time || ''
      }
    })
    ruleLoaded.value = true
  } catch (e) {
    console.error('获取自动处理规则失败', e)
    ruleList.value = []
    ruleLoaded.value = true
  }
}

// 对话框状态
const dialogVisible = ref(false)
const isEditing = ref(false)
const editingId = ref(null)

// 规则表单数据
const formData = reactive({
  name: '',
  scene: 'TIMEOUT_PAY',
  threshold: 24,
  condition: '',
  action: '自动取消',
  enabled: true
})

// 金额偏差场景使用百分比阈值，其余场景使用小时
const isAmountScene = computed(() => formData.scene === 'AMOUNT_DEVIATION')
const thresholdLabel = computed(() => (isAmountScene.value ? '偏差阈值（%）' : '超时阈值（小时）'))

// 动作下拉随场景变化，避免配置出后端不支持的组合
const actionOptions = computed(() => (SCENE_ACTIONS[formData.scene] || []).map(code => ACTION_TEXT[code]))

// 切换场景：重置阈值，并修正不合法的动作
function handleSceneChange(scene) {
  formData.threshold = defaultThreshold(scene)
  const allowed = (SCENE_ACTIONS[scene] || []).map(code => ACTION_TEXT[code])
  if (!allowed.includes(formData.action)) {
    formData.action = allowed[0] || '标记提醒'
  }
}

// 动作对应的标签颜色映射
function actionTagType(action) {
  if (action === '拦截冻结') return 'danger'
  if (action === '自动取消') return 'warning'
  return 'info'
}

// 新建规则：打开空白表单
function handleAddRule() {
  isEditing.value = false
  editingId.value = null
  formData.name = ''
  formData.scene = 'TIMEOUT_PAY'
  formData.threshold = 24
  formData.condition = ''
  formData.action = '自动取消'
  formData.enabled = true
  dialogVisible.value = true
}

// 编辑规则：填充表单
function handleEditRule(row) {
  isEditing.value = true
  editingId.value = row.id
  formData.name = row.name
  formData.scene = row.scene || 'TIMEOUT_PAY'
  formData.threshold = row.scene === 'AMOUNT_DEVIATION'
    ? (row.thresholdPercent || 50)
    : (row.thresholdHours || 24)
  formData.condition = row.condition === '-' ? '' : row.condition
  formData.action = row.action
  formData.enabled = row.enabled
  dialogVisible.value = true
}

// 保存规则（新建或编辑）
async function handleSaveRule() {
  if (!formData.name.trim()) {
    ElMessage.warning('请输入规则名称')
    return
  }
  if (!formData.threshold || formData.threshold < 1) {
    ElMessage.warning('请输入有效的阈值')
    return
  }
  const isAmount = formData.scene === 'AMOUNT_DEVIATION'
  const payload = {
    name: formData.name.trim(),
    scene: formData.scene,
    thresholdHours: isAmount ? null : formData.threshold,
    thresholdPercent: isAmount ? formData.threshold : null,
    condition: formData.condition.trim(),
    // 后端只接受 FLAG/INTERCEPT/CANCEL，不能直接提交中文
    action: ACTION_CODE[formData.action] || 'FLAG',
    enabled: formData.enabled
  }
  try {
    if (isEditing.value) {
      await orderMonitorRuleApi.update(editingId.value, payload)
      ElMessage.success('规则已更新')
    } else {
      await orderMonitorRuleApi.create(payload)
      ElMessage.success('规则已创建')
    }
    await loadRules()
    dialogVisible.value = false
  } catch (e) {
    // 失败时不再伪造本地成功，保持数据与后端一致
    ElMessage.error('保存失败：' + (e.message || '未知错误'))
  }
}

// 启用/禁用规则
async function handleToggleRule(row) {
  const nextState = row.enabled
  try {
    await orderMonitorRuleApi.toggle(row.id, { enabled: nextState })
    ElMessage.success('规则「' + row.name + '」已' + (nextState ? '启用' : '禁用'))
  } catch (e) {
    // 失败回滚开关状态，避免界面与后端不一致
    row.enabled = !nextState
    ElMessage.error('操作失败：' + (e.message || '未知错误'))
  }
}

// 删除规则
async function handleDeleteRule(row) {
  try {
    await ElMessageBox.confirm('确定删除规则「' + row.name + '」吗？', '提示', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await orderMonitorRuleApi.remove(row.id)
    ElMessage.success('规则已删除')
    await loadRules()
  } catch (e) {
    ElMessage.error('删除失败：' + (e.message || '未知错误'))
  }
}

// ==================== 规则执行记录 ====================
const logDialogVisible = ref(false)
const logRuleId = ref(null)
const logRuleName = ref('')
const logList = ref([])
const logPage = ref(1)
const logPageSize = ref(10)
const logTotal = ref(0)

// 执行结果：后端编码 ↔ 前端中文
const RESULT_TEXT = { PENDING: '执行中', SUCCESS: '成功', FAILED: '失败' }

async function loadLogs() {
  try {
    const res = (await getOrderMonitorRuleLogs(logRuleId.value, {
      page: logPage.value,
      size: logPageSize.value
    })) || {}
    logList.value = toArray(res).map(item => ({
      id: item.id,
      orderNo: item.order_no || '-',
      actionText: ACTION_TEXT[item.action_type] || item.action_type || '-',
      result: item.result || '',
      resultText: RESULT_TEXT[item.result] || item.result || '-',
      retryCount: Number(item.retry_count) || 0,
      message: item.message || '-',
      createTime: item.create_time || '-'
    }))
    logTotal.value = Number(res.total) || 0
  } catch (e) {
    console.error('获取执行记录失败', e)
    ElMessage.error('获取执行记录失败')
  }
}

// 打开执行记录弹窗
function handleShowLogs(row) {
  logRuleId.value = row.id
  logRuleName.value = row.name
  logPage.value = 1
  logList.value = []
  logTotal.value = 0
  logDialogVisible.value = true
  loadLogs()
}

// 重新评估：规则版本 +1，下一轮定时任务按当前条件重新处理订单
async function handleResetRule() {
  try {
    await ElMessageBox.confirm(
      '重置后下一轮定时任务会按当前条件重新处理订单（可能再次执行取消/拦截动作），确定继续吗？',
      '重新评估规则',
      { type: 'warning' }
    )
  } catch (e) {
    return
  }
  try {
    await resetOrderMonitorRule(logRuleId.value)
    ElMessage.success('已重置，下一轮将按当前条件重新评估')
    logDialogVisible.value = false
    await loadRules()
  } catch (e) {
    ElMessage.error('重置失败：' + (e.message || '未知错误'))
  }
}

onMounted(() => {
  loadKpi()
  loadList()
  loadRules()
})
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.kpi-row { margin-bottom: 16px; }
.kpi-card { text-align: center; padding: 8px 0; }
.kpi-label { font-size: 14px; color: var(--text-400); margin-bottom: 8px; }
.kpi-value { font-size: 28px; font-weight: 700; color: var(--text-800); }
.filter-card { margin-bottom: 16px; }
.filter-bar { margin-bottom: 16px; }
.header-actions { display: flex; gap: 8px; }
.rules-card { margin-top: 16px; }
.rules-card-header { display: flex; justify-content: space-between; align-items: center; }
.rules-card-title { font-size: 14px; font-weight: 600; color: var(--text-800); }
.log-actions { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.log-actions-tip { font-size: 12px; color: var(--text-400); }
</style>
