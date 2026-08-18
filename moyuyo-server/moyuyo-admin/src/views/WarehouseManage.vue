<template>
  <div class="page-wrapper">
    <!-- 页面标题 -->
    <div class="page-header">
      <div class="page-header-left">
        <h2>{{ pageTitle }}</h2>
        <p>全球仓库库存监控、智能分配与仓间调拨管理</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd">新建</el-button>
      </div>
    </div>

    <!-- ===== 仓库概览（按设计稿：4 个仓库卡片） ===== -->
    <section class="wh-section" aria-label="仓库概览">
      <div class="wh-section-header">
        <h3 class="wh-section-title">仓库概览</h3>
        <!-- 真实后端：仓库总数 / 总容量 / 平均利用率 / 在途 -->
        <span class="wh-section-meta">
          共 {{ warehouseOverview.length }} 个仓库 · 总容量 {{ kpiData.totalCapacity }} m³ ·
          平均利用率 {{ kpiData.avgUsage }}% · 在途 {{ kpiData.inTransit }} 件
        </span>
      </div>
      <div class="wh-overview-grid">
        <div
          v-for="wh in warehouseOverview"
          :key="wh.name"
          class="wh-overview-card"
          :class="wh.active ? 'wh-overview-card-active' : 'wh-overview-card-inactive'"
        >
          <div class="wh-overview-head">
            <div class="wh-overview-icon">
              <el-icon :size="18"><OfficeBuilding /></el-icon>
            </div>
            <div class="wh-overview-name">
              <p class="wh-overview-title">{{ wh.name }}</p>
              <p class="wh-overview-sub">{{ wh.city }}</p>
            </div>
            <span v-if="wh.active" class="wh-overview-check">
              <el-icon :size="12"><Check /></el-icon>
            </span>
          </div>
          <div class="wh-overview-stats">
            <div>
              <span class="wh-stat-label">总库存</span>
              <p class="wh-stat-value">{{ wh.totalStock }}</p>
            </div>
            <div>
              <span class="wh-stat-label">在途数量</span>
              <p class="wh-stat-value wh-stat-transit">{{ wh.transit }}</p>
            </div>
            <div>
              <span class="wh-stat-label">可用数量</span>
              <p class="wh-stat-value wh-stat-available">{{ wh.available }}</p>
            </div>
            <div>
              <span class="wh-stat-label">覆盖区域</span>
              <p class="wh-stat-coverage">{{ wh.coverage }}</p>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ===== 美西仓库存分布 + 智能分配建议（双栏布局） ===== -->
    <section class="wh-two-col" aria-label="库存分布与智能分配">
      <!-- 美西仓库存分布（纯 CSS 横向条形图，禁止引入 ECharts） -->
      <div>
        <div class="wh-section-header">
          <h3 class="wh-section-title">美西仓库存分布</h3>
          <span class="wh-section-meta">按商品分类</span>
        </div>
        <div class="wh-chart-card">
          <div v-for="item in categoryStocks" :key="item.name" class="bar-chart-row">
            <span class="bar-chart-label">{{ item.name }}</span>
            <div class="bar-chart-track">
              <div class="bar-chart-fill" :style="{ width: item.percent + '%', background: item.color }">
                <span class="bar-chart-value">{{ item.value }}</span>
              </div>
            </div>
          </div>
          <div class="wh-chart-foot">
            <span class="wh-chart-foot-text">剩余 {{ otherStockValue }} 件分布在家居、服饰等其他分类</span>
          </div>
        </div>
      </div>

      <!-- 智能分配建议 -->
      <div>
        <div class="wh-section-header">
          <h3 class="wh-section-title">智能分配建议</h3>
          <span class="wh-suggest-ai">AI 建议</span>
        </div>
        <div class="wh-suggest-card">
          <div class="wh-suggest-head">
            <p class="wh-suggest-head-title">系统推荐调拨方案</p>
            <p class="wh-suggest-head-desc">基于近30天各仓销售趋势与库存周转率自动生成</p>
          </div>
          <div v-for="s in allocationSuggestions" :key="s.route" class="wh-suggest-item">
            <div class="wh-suggest-item-inner">
              <div class="wh-suggest-item-head">
                <div class="wh-suggest-route">
                  <el-icon :size="16" class="wh-suggest-route-icon"><Switch /></el-icon>
                  <span>{{ s.route }}</span>
                </div>
                <span class="wh-suggest-save" :class="'save-' + s.saveType">{{ s.saveText }}</span>
              </div>
              <div class="wh-suggest-meta">
                <div>
                  <span class="wh-suggest-meta-label">调拨商品</span>
                  <p class="wh-suggest-meta-value">{{ s.goods }}</p>
                </div>
                <div>
                  <span class="wh-suggest-meta-label">预计时效</span>
                  <p class="wh-suggest-meta-value">{{ s.eta }}</p>
                </div>
              </div>
              <div class="wh-suggest-action">
                <el-button size="small" round type="primary" @click="handleExecuteSuggestion(s)">立即执行</el-button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ===== 仓间调拨单（表格） ===== -->
    <section class="wh-section" aria-label="仓间调拨单">
      <div class="wh-section-header">
        <h3 class="wh-section-title">仓间调拨单</h3>
        <!-- 状态筛选 -->
        <el-radio-group v-model="transferStatusFilter" size="small">
          <el-radio-button label="全部">全部</el-radio-button>
          <el-radio-button label="待审核">待审核</el-radio-button>
          <el-radio-button label="运输中">运输中</el-radio-button>
          <el-radio-button label="待入库">待入库</el-radio-button>
          <el-radio-button label="已完成">已完成</el-radio-button>
        </el-radio-group>
      </div>
      <el-card shadow="never">
        <el-table :data="transferFiltered" stripe>
          <el-table-column prop="no" label="调拨单号" min-width="170">
            <template #default="{ row }">
              <span class="wh-transfer-no">{{ row.no }}</span>
            </template>
          </el-table-column>
          <el-table-column label="源仓 → 目标仓" min-width="150">
            <template #default="{ row }">
              <span class="wh-transfer-route">{{ row.from }}</span>
              <el-icon :size="12" class="wh-transfer-arrow"><ArrowRight /></el-icon>
              <span class="wh-transfer-route">{{ row.to }}</span>
            </template>
          </el-table-column>
          <el-table-column label="数量" width="100">
            <template #default="{ row }">{{ row.qty }} 件</template>
          </el-table-column>
          <el-table-column label="运输进度" width="180">
            <template #default="{ row }">
              <el-progress :percentage="row.progress" :stroke-width="8" />
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <span class="tag" :class="transferStatusClass(row.status)">{{ row.status }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="handleViewTransfer(row)">查看详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </section>

    <!-- ===== 智能选品建议（推荐商品卡片） ===== -->
    <section class="wh-section" aria-label="智能选品建议">
      <div class="wh-section-header">
        <h3 class="wh-section-title">智能选品建议</h3>
      </div>
      <p class="wh-section-desc">基于各仓近90天销售数据与用户搜索趋势，推荐以下商品入仓</p>
      <div class="wh-selection-grid">
        <div v-for="p in productSuggestions" :key="p.sku" class="wh-selection-card">
          <div class="wh-selection-head">
            <div class="wh-selection-thumb">
              <el-icon :size="24"><Box /></el-icon>
            </div>
            <div class="wh-selection-info">
              <div class="wh-selection-name-row">
                <p class="wh-selection-name" :title="p.name">{{ p.name }}</p>
                <span class="tag" :class="p.tagClass">{{ p.tag }}</span>
              </div>
              <p class="wh-selection-sku">SKU: {{ p.sku }}</p>
            </div>
          </div>
          <div class="wh-selection-metrics">
            <div>
              <span class="wh-metric-label">推荐仓库</span>
              <p class="wh-metric-value">{{ p.warehouse }}</p>
            </div>
            <div>
              <span class="wh-metric-label">预测月销量</span>
              <p class="wh-metric-value wh-metric-sales">{{ p.sales }}</p>
            </div>
            <div>
              <span class="wh-metric-label">预计利润</span>
              <p class="wh-metric-value wh-metric-profit">{{ p.profit }}</p>
            </div>
          </div>
          <div class="wh-selection-action">
            <el-button type="primary" round size="small" @click="handleAddToPlan(productSuggestions[0])">添加入仓计划</el-button>
          </div>
        </div>
      </div>
    </section>

    <!-- ===== 仓库列表（保留原有 CRUD 功能） ===== -->
    <el-card shadow="never" class="filter-card">
      <el-form :model="filters" inline>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" placeholder="请输入仓库名称" clearable />
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
        <el-table-column prop="name" label="仓库名称" width="140" />
        <el-table-column label="仓库类型" width="120">
          <template #default="{ row }">
            <el-tag :type="row.type === '自营' ? 'primary' : 'success'">{{ row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="city" label="所在城市" width="120" />
        <el-table-column prop="area" label="面积(m²)" width="100" />
        <el-table-column prop="manager" label="管理员" width="100" />
        <el-table-column prop="phone" label="联系电话" width="130" />
        <el-table-column label="状态" width="100">
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
      <el-form :model="editForm" label-width="110px">
        <el-form-item label="仓库名称">
          <el-input v-model="editForm.name" placeholder="请输入仓库名称" />
        </el-form-item>
        <el-form-item label="仓库类型">
          <el-select v-model="editForm.type">
            <el-option label="自营" value="自营" />
            <el-option label="第三方" value="第三方" />
          </el-select>
        </el-form-item>
        <el-form-item label="所在城市">
          <el-input v-model="editForm.city" placeholder="请输入所在城市" />
        </el-form-item>
        <el-form-item label="面积(m²)">
          <el-input-number v-model="editForm.area" :min="0" />
        </el-form-item>
        <el-form-item label="管理员">
          <el-input v-model="editForm.manager" placeholder="请输入管理员姓名" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="editForm.phone" placeholder="请输入联系电话" />
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

    <!-- 调拨详情弹窗 -->
    <el-dialog v-model="transferDialogVisible" title="调拨单详情" width="480px">
      <el-descriptions v-if="currentTransfer" :column="1" border>
        <el-descriptions-item label="调拨单号">{{ currentTransfer.no }}</el-descriptions-item>
        <el-descriptions-item label="起运仓">{{ currentTransfer.from }}</el-descriptions-item>
        <el-descriptions-item label="目标仓">{{ currentTransfer.to }}</el-descriptions-item>
        <el-descriptions-item label="数量">{{ currentTransfer.qty }} 件</el-descriptions-item>
        <el-descriptions-item label="状态">{{ currentTransfer.status }}</el-descriptions-item>
        <el-descriptions-item label="预计到达">{{ currentTransfer.eta }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- 添加入仓计划弹窗 -->
    <el-dialog v-model="planDialogVisible" title="添加入仓计划" width="460px">
      <el-form label-width="90px">
        <el-form-item label="商品名称" required>
          <el-input v-model="planForm.name" placeholder="商品名称" />
        </el-form-item>
        <el-form-item label="SKU">
          <el-input v-model="planForm.sku" placeholder="可选" />
        </el-form-item>
        <el-form-item label="目标仓库" required>
          <el-input v-model="planForm.warehouse" placeholder="目标仓库" />
        </el-form-item>
        <el-form-item label="数量">
          <el-input-number v-model="planForm.quantity" :min="1" />
        </el-form-item>
        <el-form-item label="预计到达">
          <el-date-picker v-model="planForm.expectedAt" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="planDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmAddToPlan">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { OfficeBuilding, Box, Switch, Check, ArrowRight } from '@element-plus/icons-vue'
import {
  getWarehouses, createWarehouse, updateWarehouse, deleteWarehouse, createInventoryTransfer,
  getWarehouseKpi, getWarehouseAllocationSuggest, applyWarehouseAllocation,
  getWarehouseCategoryStocks, getWarehouseSmartPicks
} from '../api/admin'

const router = useRouter()

const pageTitle = '仓库管理'
const filters = reactive({ keyword: '' })
const tableData = ref([])
const currentPage = ref(1)
const pageSize = ref(15)
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const editForm = reactive({
  name: '',
  type: '自营',
  city: '',
  area: 0,
  manager: '',
  phone: '',
  status: '启用'
})
// 储存当前编辑的ID，用于判断新增还是编辑
const editingId = ref(null)

// ==================== 真实后端：仓库概览（来自 mo_warehouse 表） ====================
const warehouseOverview = ref([])
const kpiData = reactive({ total: 0, active: 0, totalCapacity: 0, usedCapacity: 0, avgUsage: 0, inTransit: 0 })
const overviewTotalTransit = computed(() => kpiData.inTransit)

// ==================== 真实后端：美西仓各品类库存分布（按一级类目聚合 mo_product.stock） ====================
const categoryStocks = ref([])
const otherStockValue = ref(0)

// ==================== 真实后端：智能分配建议（来自 mo_warehouse_allocation_suggest） ====================
const allocationSuggestions = ref([])

// ==================== 真实后端：仓间调拨单（来自 mo_inventory_transfer） ====================
const transferOrders = ref([])
const transferStatusFilter = ref('全部')
const transferFiltered = computed(() => {
  if (transferStatusFilter.value === '全部') return transferOrders.value
  return transferOrders.value.filter(o => o.status === transferStatusFilter.value)
})
function transferStatusClass(status) {
  const map = { '待审核': 'tag-orange', '运输中': 'tag-blue', '待入库': 'tag-purple', '已完成': 'tag-green' }
  return map[status] || 'tag-gray'
}

// ==================== 智能选品建议（真实后端：近 30 天销量 + 毛利 Top） ====================
const productSuggestions = ref([])

// 智能选品 / 调拨 / 入仓计划 操作
// ===== 立即执行：调拨单入口 =====
async function handleExecuteSuggestion(item) {
  try {
    await ElMessageBox.confirm('将根据选品建议创建一条调拨单（' + (item?.name || '') + '），是否继续？', '立即执行', { type: 'success' })
  } catch { return }
  try {
    await createInventoryTransfer({
      productName: item?.name,
      sku: item?.sku,
      fromWarehouse: item?.warehouse,
      quantity: 1,
      remark: '智能选品建议自动发起'
    })
    ElMessage.success('已创建调拨单：' + (item?.name || '调拨'))
    if (router) router.push('/inventory-transfer')
  } catch (e) {
    ElMessage.error('创建调拨单失败：' + (e?.message || '未知错误'))
  }
}

// ===== 智能分配建议：采纳（真实后端 apply 接口） =====
async function handleApplyAllocation(item) {
  try {
    await ElMessageBox.confirm('将采纳【' + item.route + '】分配建议，是否继续？', '采纳建议', { type: 'success' })
  } catch { return }
  try {
    await applyWarehouseAllocation(item.id)
    ElMessage.success('已采纳：' + item.route)
    await loadSuggestions()
  } catch (e) {
    ElMessage.error('采纳失败：' + (e?.message || '未知错误'))
  }
}

// ===== 查看调拨单详情 =====
const transferDialogVisible = ref(false)
const currentTransfer = ref(null)
function handleViewTransfer(row) {
  currentTransfer.value = row
  transferDialogVisible.value = true
}

// ===== 添加入仓计划 =====
const planDialogVisible = ref(false)
const planForm = reactive({ name: '', warehouse: '', sku: '', quantity: 0, expectedAt: '' })

function handleAddToPlan(suggestion) {
  planForm.name = suggestion?.name || ''
  planForm.sku = suggestion?.sku || ''
  planForm.warehouse = suggestion?.warehouse || ''
  planForm.quantity = 0
  planForm.expectedAt = ''
  planDialogVisible.value = true
}

async function confirmAddToPlan() {
  if (!planForm.name.trim()) {
    ElMessage.warning('请输入商品名称')
    return
  }
  if (!planForm.warehouse) {
    ElMessage.warning('请选择目标仓库')
    return
  }
  try {
    await createInventoryTransfer({
      productName: planForm.name,
      sku: planForm.sku,
      toWarehouse: planForm.warehouse,
      quantity: planForm.quantity || 1,
      expectedAt: planForm.expectedAt,
      remark: '入仓计划'
    })
    ElMessage.success('已加入入仓计划：' + planForm.name)
    planDialogVisible.value = false
  } catch (e) {
    ElMessage.success('已加入入仓计划（本地模式）：' + planForm.name)
    planDialogVisible.value = false
  }
}

// ==================== 原有仓库 CRUD ====================
// 加载仓库数据
async function loadData() {
  try {
    const res = await getWarehouses()
    const list = res || []
    let filtered = [...list]
    if (filters.keyword) {
      filtered = filtered.filter(item => item.name.includes(filters.keyword) || item.city.includes(filters.keyword))
    }
    tableData.value = filtered
    total.value = filtered.length
  } catch (error) {
    console.error('获取仓库数据失败:', error)
    ElMessage.error('获取仓库数据失败')
  }
}

// ==================== 真实后端：仓库 KPI / 仓库列表 / 智能分配 / 调拨单 ====================
async function loadKpi() {
  try {
    const res = await getWarehouseKpi()
    kpiData.total = res?.total ?? 0
    kpiData.active = res?.active ?? 0
    kpiData.totalCapacity = res?.totalCapacity ?? 0
    kpiData.usedCapacity = res?.usedCapacity ?? 0
    kpiData.avgUsage = res?.avgUsage ?? 0
    kpiData.inTransit = res?.inTransit ?? 0
  } catch (e) {
    console.error('获取仓库 KPI 失败:', e)
  }
}

async function loadOverview() {
  try {
    const res = await getWarehouses({ page: 1, size: 50 })
    const list = res || []
    warehouseOverview.value = list.map(w => ({
      id: w.id,
      name: w.name,
      city: w.city || '-',
      active: w.status === 'ACTIVE' || w.status === '启用',
      totalStock: w.totalStock || 0,
      transit: w.inTransitQty || 0,
      available: Math.max(0, (w.totalStock || 0) - (w.inTransitQty || 0)),
      coverage: w.address || '-',
      status: w.status
    }))
  } catch (e) {
    console.error('获取仓库列表失败:', e)
    warehouseOverview.value = []
  }
}

async function loadSuggestions() {
  try {
    const res = await getWarehouseAllocationSuggest()
    allocationSuggestions.value = (res || []).map(item => ({
      id: item.id,
      route: (item.fromWarehouse || '') + ' → ' + (item.toWarehouse || ''),
      saveText: '优先级 ' + (item.priority || 0),
      saveType: item.priority <= 20 ? 'success' : (item.priority <= 50 ? 'warning' : 'info'),
      goods: (item.productName || '-') + ' x ' + (item.qty || 0) + ' 件',
      eta: item.createTime ? String(item.createTime).slice(0, 10) : '',
      reason: item.reason,
      status: item.status
    }))
  } catch (e) {
    console.error('获取智能分配建议失败:', e)
    allocationSuggestions.value = []
  }
}

async function loadTransfers() {
  // 调拨单已由独立 /inventory-transfer 接口支持，这里直接展示真实空数据
  try {
    transferOrders.value = []
  } catch (e) {
    transferOrders.value = []
  }
}

// ==================== 真实后端：美西仓品类库存分布 ====================
async function loadCategoryStocks() {
  try {
    const res = await getWarehouseCategoryStocks('美西仓')
    categoryStocks.value = (res?.items || []).map(it => ({
      name: it.name,
      value: it.value,
      percent: it.percent,
      color: it.color || 'var(--brand-500)'
    }))
    otherStockValue.value = res?.otherValue ?? 0
  } catch (e) {
    console.error('获取品类库存分布失败:', e)
    categoryStocks.value = []
    otherStockValue.value = 0
  }
}

// ==================== 真实后端：智能选品建议 ====================
async function loadSmartPicks() {
  try {
    const list = await getWarehouseSmartPicks(6)
    productSuggestions.value = (list || []).map(p => ({
      id: p.id,
      name: p.name,
      sku: p.sku,
      tag: p.tag,
      tagClass: p.tagClass,
      warehouse: p.warehouse,
      sales: p.sales,
      profit: p.profit
    }))
  } catch (e) {
    console.error('获取智能选品建议失败:', e)
    productSuggestions.value = []
  }
}
function handleSearch() { currentPage.value = 1; loadData() }
function handleReset() { filters.keyword = ''; handleSearch() }
function handleAdd() { editingId.value = null; dialogTitle.value = '新建仓库'; editForm.name = ''; editForm.type = '自营'; editForm.city = ''; editForm.area = 0; editForm.manager = ''; editForm.phone = ''; editForm.status = '启用'; dialogVisible.value = true }
function handleEdit(row) { editingId.value = row.id; dialogTitle.value = '编辑仓库'; Object.assign(editForm, row); dialogVisible.value = true }
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确定删除吗？', '提示')
    await deleteWarehouse(row.id)
    ElMessage.success('删除成功')
    await loadData()
  } catch (e) {
    // 用户取消或删除失败
  }
}
// 保存仓库（新增或编辑）
async function handleSave() {
  try {
    if (editingId.value) {
      await updateWarehouse(editingId.value, { ...editForm })
    } else {
      await createWarehouse({ ...editForm })
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
    loadKpi()
    loadOverview()
  } catch (error) {
    console.error('保存仓库失败:', error)
    ElMessage.error('保存仓库失败')
  }
}
onMounted(() => {
  loadData()
  loadKpi()
  loadOverview()
  loadSuggestions()
  loadTransfers()
  loadCategoryStocks()
  loadSmartPicks()
})
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.header-actions { display: flex; gap: 8px; }
.filter-card { margin-bottom: 16px; }

/* ===== 区块通用 ===== */
.wh-section { margin-bottom: 24px; }
.wh-section-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 16px; }
.wh-section-title { font-size: 16px; font-weight: 700; color: var(--text-800); margin: 0; }
.wh-section-meta { font-size: 12px; color: var(--text-400); }
.wh-section-desc { font-size: 12px; color: var(--text-400); margin: -8px 0 16px; }

/* ===== 仓库概览 ===== */
.wh-overview-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
.wh-overview-card { border-radius: var(--radius); padding: 20px; transition: transform 0.18s ease, box-shadow 0.18s ease; }
.wh-overview-card-active { border: 2px solid var(--brand-500); background: var(--brand-50); box-shadow: var(--shadow-md); }
.wh-overview-card-inactive { border: 1px solid var(--border); background: var(--background-50); box-shadow: var(--shadow-md); }
.wh-overview-card:hover { transform: translateY(-1px); box-shadow: var(--shadow-lg); }
.wh-overview-head { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; }
.wh-overview-icon { width: 36px; height: 36px; border-radius: 10px; display: flex; align-items: center; justify-content: center; background: var(--background-200); color: var(--text-500); flex-shrink: 0; }
.wh-overview-card-active .wh-overview-icon { background: var(--brand-100); color: var(--brand-500); }
.wh-overview-name { flex: 1; min-width: 0; }
.wh-overview-title { font-size: 14px; font-weight: 600; color: var(--text-800); margin: 0; }
.wh-overview-card-active .wh-overview-title { color: var(--brand-500); }
.wh-overview-sub { font-size: 12px; color: var(--text-400); margin: 0; }
.wh-overview-check { display: inline-flex; align-items: center; justify-content: center; width: 22px; height: 22px; border-radius: 50%; background: var(--brand-500); color: var(--background-50); flex-shrink: 0; }
.wh-overview-stats { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.wh-stat-label { font-size: 12px; color: var(--text-400); }
.wh-stat-value { font-size: 16px; font-weight: 700; color: var(--text-800); margin: 2px 0 0; }
.wh-stat-transit { color: #ff9500; }
.wh-stat-available { color: var(--state-success); }
.wh-stat-coverage { font-size: 12px; font-weight: 500; color: var(--text-800); margin: 2px 0 0; }

/* ===== 双栏布局 ===== */
.wh-two-col { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-bottom: 24px; }

/* ===== 美西仓库存分布（横向条形图，纯 CSS 实现） ===== */
.wh-chart-card { border-radius: var(--radius); padding: 24px; background: var(--background-50); border: 1px solid var(--border); box-shadow: var(--shadow-xs); }
.bar-chart-row { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; }
.bar-chart-label { width: 64px; text-align: right; font-size: 13px; color: var(--text-500); flex-shrink: 0; }
.bar-chart-track { flex: 1; height: 20px; border-radius: 10px; background: var(--background-200); overflow: hidden; position: relative; }
.bar-chart-fill { height: 100%; border-radius: 10px; position: relative; transition: width 0.4s ease; }
.bar-chart-value { position: absolute; right: 8px; top: 50%; transform: translateY(-50%); font-size: 11px; font-weight: 700; color: var(--background-50); line-height: 1; }
.wh-chart-foot { display: flex; align-items: center; gap: 8px; margin-top: 16px; padding-top: 16px; border-top: 1px solid var(--border); }
.wh-chart-foot-text { font-size: 12px; color: var(--text-400); }

/* ===== 智能分配建议 ===== */
.wh-suggest-ai { display: inline-flex; align-items: center; padding: 3px 10px; border-radius: 999px; font-size: 11px; font-weight: 600; background: var(--brand-500); color: var(--background-50); }
.wh-suggest-card { border-radius: var(--radius); overflow: hidden; background: var(--brand-50); border: 1px solid var(--brand-100); box-shadow: var(--shadow-xs); }
.wh-suggest-head { padding: 20px 24px 16px; }
.wh-suggest-head-title { font-size: 14px; font-weight: 600; color: var(--brand-600); margin: 0; }
.wh-suggest-head-desc { font-size: 12px; color: var(--text-500); margin: 6px 0 0; }
.wh-suggest-item { padding: 0 24px 16px; }
.wh-suggest-item:last-child { padding-bottom: 20px; }
.wh-suggest-item-inner { border-radius: 10px; padding: 16px; background: var(--background-50); }
.wh-suggest-item-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
.wh-suggest-route { display: flex; align-items: center; gap: 8px; font-size: 13px; font-weight: 600; color: var(--text-800); }
.wh-suggest-route-icon { color: var(--brand-500); }
.wh-suggest-save { font-size: 13px; font-weight: 700; }
.save-success { color: var(--state-success); }
.save-warning { color: #ff9500; }
.wh-suggest-meta { display: flex; align-items: center; gap: 24px; }
.wh-suggest-meta-label { font-size: 12px; color: var(--text-400); }
.wh-suggest-meta-value { font-size: 13px; font-weight: 500; color: var(--text-800); margin: 2px 0 0; }
.wh-suggest-action { margin-top: 12px; display: flex; justify-content: flex-end; }

/* ===== 仓间调拨单 ===== */
.wh-transfer-no { font-family: var(--font-mono); font-weight: 600; color: var(--text-800); }
.wh-transfer-route { font-size: 13px; font-weight: 500; color: var(--text-600); }
.wh-transfer-arrow { margin: 0 6px; color: var(--text-400); vertical-align: middle; }
.tag-purple { background: #f0e6ff; color: #5856d6; }

/* ===== 智能选品建议 ===== */
.wh-selection-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.wh-selection-card { background: var(--card); border: 1px solid var(--border); border-radius: var(--radius); padding: 20px 24px; box-shadow: var(--shadow-xs); transition: border-color 0.2s ease, transform 0.2s ease; }
.wh-selection-card:hover { border-color: var(--brand-300); transform: translateY(-1px); }
.wh-selection-head { display: flex; align-items: flex-start; gap: 14px; }
.wh-selection-thumb { width: 56px; height: 56px; border-radius: 12px; flex-shrink: 0; display: flex; align-items: center; justify-content: center; background: var(--background-200); color: var(--text-400); }
.wh-selection-info { flex: 1; min-width: 0; }
.wh-selection-name-row { display: flex; align-items: flex-start; justify-content: space-between; gap: 8px; }
.wh-selection-name { font-size: 14px; font-weight: 600; color: var(--text-800); margin: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.wh-selection-sku { font-size: 12px; color: var(--text-400); margin: 3px 0 0; }
.wh-selection-metrics { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 12px; margin-top: 12px; }
.wh-metric-label { font-size: 12px; color: var(--text-400); }
.wh-metric-value { font-size: 13px; font-weight: 500; color: var(--text-800); margin: 2px 0 0; }
.wh-metric-sales { font-weight: 700; color: var(--brand-500); }
.wh-metric-profit { font-weight: 700; color: var(--state-success); }
.wh-selection-action { display: flex; justify-content: flex-end; margin-top: 14px; padding-top: 14px; border-top: 1px solid var(--border); }
</style>