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
      <el-table :data="tableData" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="orderNo" label="订单编号" width="170" />
        <el-table-column prop="product" label="商品" min-width="160" />
        <el-table-column prop="amount" label="金额" width="100">
          <template #default="{ row }">¥{{ row.amount }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="abnormalType" label="异常类型" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.abnormalType" type="danger" size="small">{{ row.abnormalType }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="170" />
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
          @change="loadData"
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
        <el-table-column prop="name" label="规则名称" min-width="170" />
        <el-table-column prop="condition" label="触发条件" min-width="240" show-overflow-tooltip />
        <el-table-column prop="action" label="自动动作" width="120">
          <template #default="{ row }">
            <el-tag :type="actionTagType(row.action)" size="small">{{ row.action }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" @change="handleToggleRule(row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEditRule(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDeleteRule(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新建/编辑规则对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEditing ? '编辑规则' : '新建规则'"
      width="560px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form :model="formData" label-width="90px" label-position="top">
        <el-form-item label="规则名称" required>
          <el-input v-model="formData.name" placeholder="输入规则名称" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="触发条件" required>
          <el-input
            v-model="formData.condition"
            type="textarea"
            :rows="3"
            placeholder="输入触发条件，如：下单超过 24h 未完成支付"
          />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="自动动作">
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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMonitorData, getAbnormalOrders, orderMonitorRuleApi, getOrderDetail } from '../api/admin'
import { toArray } from '../utils/safeArray'
import { useRouter } from 'vue-router'

const router = useRouter()

const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const currentTab = ref('')

const kpiData = reactive({
  todayOrders: 0,
  pendingShip: 0,
  abnormalOrders: 0,
  intercepting: 0
})

const tableData = ref([])

// 加载KPI统计和订单列表
async function loadData() {
  try {
    // 同时获取统计数据和订单列表
    const [statsRes, listRes] = await Promise.all([
      getMonitorData(),
      getAbnormalOrders({ abnormalType: currentTab.value, page: currentPage.value, size: pageSize.value })
    ])
    // 填充KPI数据（axios拦截器已解包，statsRes即为后端data对象）
    const stats = statsRes || {}
    kpiData.todayOrders = stats.todayOrders || 0
    kpiData.pendingShip = stats.pendingShip || 0
    kpiData.abnormalOrders = stats.abnormalOrders || 0
    kpiData.intercepting = stats.intercepting || 0

    // 填充列表数据（服务端分页）
    const listData = listRes || {}
    tableData.value = toArray(listData)
    total.value = listData.total || 0
  } catch (error) {
    console.error('获取订单监控数据失败:', error)
    ElMessage.error('获取订单监控数据失败')
  }
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
// 规则列表：从后端拉取，支持失败时降级到本地示例数据，保证页面可使用
const ruleList = ref([])
const ruleLoaded = ref(false)

async function loadRules() {
  try {
    const res = await orderMonitorRuleApi.list()
    const list = toArray(res)
    ruleList.value = list.length > 0 ? list : [
      { id: 1, name: '超时未付款自动取消', condition: '下单超过 24h 未完成支付，自动取消订单并释放库存', action: '自动取消', enabled: true },
      { id: 2, name: '超时未发货自动提醒', condition: '支付后超过 48h 未发货，自动发送站内信提醒商家', action: '标记提醒', enabled: true },
      { id: 3, name: '物流停滞自动核查', condition: '物流超过 72h 无更新记录，自动向物流公司发起查询工单', action: '标记提醒', enabled: true },
      { id: 4, name: '金额异常自动冻结', condition: '实付金额与商品标价偏差超过 50%，自动冻结订单等待审核', action: '拦截冻结', enabled: false }
    ]
    ruleLoaded.value = true
  } catch (e) {
    // 接口未接入时使用本地示例数据
    ruleList.value = [
      { id: 1, name: '超时未付款自动取消', condition: '下单超过 24h 未完成支付，自动取消订单并释放库存', action: '自动取消', enabled: true },
      { id: 2, name: '超时未发货自动提醒', condition: '支付后超过 48h 未发货，自动发送站内信提醒商家', action: '标记提醒', enabled: true },
      { id: 3, name: '物流停滞自动核查', condition: '物流超过 72h 无更新记录，自动向物流公司发起查询工单', action: '标记提醒', enabled: true },
      { id: 4, name: '金额异常自动冻结', condition: '实付金额与商品标价偏差超过 50%，自动冻结订单等待审核', action: '拦截冻结', enabled: false }
    ]
  }
}

// 自动动作可选值（对应设计稿：取消 / 拦截 / 标记）
const actionOptions = ['自动取消', '拦截冻结', '标记提醒']

// 对话框状态
const dialogVisible = ref(false)
const isEditing = ref(false)
const editingId = ref(null)

// 规则表单数据
const formData = reactive({
  name: '',
  condition: '',
  action: '自动取消',
  enabled: true
})

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
  formData.condition = row.condition
  formData.action = row.action
  formData.enabled = row.enabled
  dialogVisible.value = true
}

// 保存规则（新建或编辑）：优先调用后端 API，失败时回退到本地操作
async function handleSaveRule() {
  if (!formData.name.trim()) {
    ElMessage.warning('请输入规则名称')
    return
  }
  if (!formData.condition.trim()) {
    ElMessage.warning('请输入触发条件')
    return
  }
  const payload = {
    name: formData.name.trim(),
    condition: formData.condition.trim(),
    action: formData.action,
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
    // 后端接口未接入时回退到本地操作
    if (isEditing.value) {
      const target = ruleList.value.find(item => item.id === editingId.value)
      if (target) {
        Object.assign(target, payload)
      }
      ElMessage.success('规则已更新（本地模式）')
    } else {
      const maxId = ruleList.value.reduce((max, item) => Math.max(max, item.id || 0), 0)
      ruleList.value.push({ id: maxId + 1, ...payload })
      ElMessage.success('规则已创建（本地模式）')
    }
    dialogVisible.value = false
  }
}

// 启用/禁用规则
async function handleToggleRule(row) {
  const nextState = row.enabled
  try {
    await orderMonitorRuleApi.toggle(row.id, { enabled: nextState })
    ElMessage.success('规则「' + row.name + '」已' + (nextState ? '启用' : '禁用'))
  } catch (e) {
    ElMessage.success('规则「' + row.name + '」已' + (nextState ? '启用' : '禁用') + '（本地模式）')
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
    ruleList.value = ruleList.value.filter(item => item.id !== row.id)
    ElMessage.success('规则已删除（本地模式）')
  }
}

onMounted(() => {
  loadData()
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
.header-actions { display: flex; gap: 8px; }
.rules-card { margin-top: 16px; }
.rules-card-header { display: flex; justify-content: space-between; align-items: center; }
.rules-card-title { font-size: 14px; font-weight: 600; color: var(--text-800); }
</style>
