<template>
  <div class="page-wrapper">
    <!-- ===== 页面标题行 ===== -->
    <div class="page-header">
      <div class="page-header-left">
        <h2>{{ pageTitle }}</h2>
        <p>多仓库存视图 / 智能分配 / 仓间调拨 / 本地发货</p>
      </div>
      <div class="header-actions">
        <el-button @click="handleAdd">新建海外仓</el-button>
        <el-button type="primary" @click="openTransferDialog()">
          <el-icon><Plus /></el-icon>
          新建调拨单
        </el-button>
      </div>
    </div>

    <!-- ===== 仓库选择 Tab ===== -->
    <div class="warehouse-tabs">
      <button
        v-for="wh in warehouseOverview"
        :key="wh.name"
        class="wh-tab"
        :class="{ active: activeWarehouse === wh.name }"
        @click="activeWarehouse = wh.name"
      >
        {{ wh.name }}
      </button>
    </div>

    <!-- ===== 库存总览卡片（真实库存数据） ===== -->
    <section class="stock-grid">
      <div v-for="wh in warehouseOverview" :key="wh.id" class="stock-card">
        <div class="stock-card-head">
          <span class="stock-name">{{ wh.name }}</span>
          <div class="stock-icon">
            <el-icon><Van /></el-icon>
          </div>
        </div>
        <div class="stock-metric">
          <span class="stock-label">SKU 种类</span>
          <div class="stock-value">{{ fmtNum(wh.skuTypes) }}</div>
        </div>
        <div class="stock-sub">
          <div>
            <span class="stock-label">可用库存</span>
            <div class="stock-sub-value success">{{ fmtNum(wh.availableStock) }}</div>
          </div>
          <div>
            <span class="stock-label">在途</span>
            <div class="stock-sub-value primary">{{ fmtNum(wh.inTransit) }}</div>
          </div>
        </div>
      </div>
    </section>

    <!-- ===== 两列布局：仓间调拨 + 侧边面板 ===== -->
    <div class="transfer-layout">
      <!-- 左侧：仓间调拨列表（真实调拨单） -->
      <section class="transfer-section">
        <div class="section-head">
          <h3 class="section-title">仓间调拨</h3>
          <button class="view-all" @click="viewAllTransfers">
            查看全部
            <el-icon><ArrowRight /></el-icon>
          </button>
        </div>
        <el-card shadow="never" class="table-card">
          <el-table :data="transferList" stripe>
            <el-table-column prop="code" label="调拨单号" min-width="150" />
            <el-table-column prop="fromWarehouse" label="源仓" min-width="100" />
            <el-table-column prop="toWarehouse" label="目标仓" min-width="100" />
            <el-table-column prop="product" label="商品" min-width="130" />
            <el-table-column prop="quantity" label="数量" min-width="80" align="center" />
            <el-table-column label="状态" min-width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" min-width="110" />
            <el-table-column prop="eta" label="预计到达" min-width="110" />
            <el-table-column label="操作" min-width="130" align="center">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="viewTransferDetail(row)">查看详情</el-button>
                <el-button v-if="row.dbStatus === 'PENDING'" type="primary" link size="small" @click="approveTransfer(row)">审核通过</el-button>
                <el-button v-if="row.dbStatus === 'IN_TRANSIT'" type="success" link size="small" @click="confirmArrival(row)">确认入库</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </section>

      <!-- 右侧面板 -->
      <div class="side-panel">
        <!-- 智能分配建议（真实数据） -->
        <el-card shadow="never" class="side-card">
          <div class="card-title">
            <div class="title-icon brand">
              <el-icon><MagicStick /></el-icon>
            </div>
            <h4>智能分配建议</h4>
          </div>
          <p class="suggest-text">
            基于各仓库存与调拨需求自动生成，共
            <strong>{{ allocationSuggestions.length }}</strong> 条待处理建议。
          </p>
          <!-- 按建议条目展示调拨路线 + 理由 -->
          <div v-for="s in allocationSuggestions" :key="s.id" class="suggest-item">
            <div class="suggest-address">{{ s.productName }} × {{ s.qty }}</div>
            <div class="suggest-wh">
              推荐路线：<span class="suggest-wh-name">{{ s.fromWarehouse }} → {{ s.toWarehouse }}</span>
            </div>
            <div class="suggest-reason">{{ s.reason }}</div>
            <div class="suggest-actions">
              <el-button
                type="primary"
                size="small"
                style="flex: 1"
                @click="openTransferDialog({ fromWarehouse: s.fromWarehouse, toWarehouse: s.toWarehouse, quantity: s.qty })"
              >生成调拨单</el-button>
              <el-button size="small" style="flex: 1" @click="applySuggestion(s)">采纳建议</el-button>
            </div>
          </div>
          <div v-if="allocationSuggestions.length === 0" class="suggest-reason">暂无待处理建议</div>
        </el-card>

        <!-- 本地发货统计（真实数据） -->
        <el-card shadow="never" class="side-card">
          <div class="card-title">
            <div class="title-icon success">
              <el-icon><Box /></el-icon>
            </div>
            <h4>本地发货统计</h4>
          </div>
          <div class="kpi-duo">
            <div class="kpi-box">
              <div class="kpi-num primary">{{ shippingStats.todayPending }}</div>
              <div class="kpi-label">今日待发货</div>
            </div>
            <div class="kpi-box">
              <div class="kpi-num success">{{ shippingStats.shippedToday }}</div>
              <div class="kpi-label">已发货</div>
            </div>
          </div>
          <!-- SLA 达标率 -->
          <div class="sla-row">
            <div class="sla-label">
              <span>SLA达标率</span>
              <span class="sla-value">{{ shippingStats.slaRate }}%</span>
            </div>
            <div class="sla-track">
              <div class="sla-fill" :style="{ width: shippingStats.slaRate + '%' }"></div>
            </div>
          </div>
          <!-- 各承运商今日发货量 / 占比（纯 CSS 条形图，复用全局 .bar-chart 样式） -->
          <div class="bar-head">
            <span class="bar-head-label">各承运商今日发货量 / 占比</span>
          </div>
          <div class="bar-chart">
            <div v-for="item in shippingStats.byWarehouse" :key="item.name" class="bar-item">
              <span class="bar-value">{{ item.value }}</span>
              <div class="bar-fill" :style="{ height: barHeight(item.value) + '%' }"></div>
              <span class="bar-label">{{ item.name }} {{ item.ratio }}%</span>
            </div>
            <div v-if="shippingStats.byWarehouse.length === 0" class="bar-head-label">今日暂无发货记录</div>
          </div>
          <!-- 波次拣货 / 批量打单入口 -->
          <div class="ops-row">
            <el-button size="small" style="flex: 1"><el-icon><List /></el-icon>波次拣货</el-button>
            <el-button size="small" style="flex: 1"><el-icon><Printer /></el-icon>批量打单</el-button>
          </div>
        </el-card>
      </div>
    </div>

    <!-- ===== 海外仓基础管理（保留原有 CRUD） ===== -->
    <div class="section-head sub-heading">
      <h3 class="section-title">海外仓列表</h3>
    </div>
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
        <el-table-column prop="name" label="仓库名称" width="150" />
        <el-table-column prop="country" label="所在国家/地区" width="140" />
        <el-table-column prop="skuCount" label="库存SKU数" width="100" />
        <el-table-column prop="totalStock" label="库存总量" width="100" />
        <el-table-column label="仓容使用率" width="100">
          <template #default="{ row }">{{ row.usageRate }}%</template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === '正常' ? 'success' : row.status === '满仓' ? 'danger' : 'warning'">{{ row.status }}</el-tag>
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
      <el-form :model="editForm" label-width="120px">
        <el-form-item label="仓库名称">
          <el-input v-model="editForm.name" placeholder="请输入仓库名称" />
        </el-form-item>
        <el-form-item label="所在国家/地区">
          <el-input v-model="editForm.country" placeholder="请输入国家/地区" />
        </el-form-item>
        <el-form-item label="库存SKU数">
          <el-input-number v-model="editForm.skuCount" :min="0" />
        </el-form-item>
        <el-form-item label="库存总量">
          <el-input-number v-model="editForm.totalStock" :min="0" />
        </el-form-item>
        <el-form-item label="仓容使用率(%)">
          <el-input-number v-model="editForm.usageRate" :min="0" :max="100" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="editForm.status">
            <el-option label="正常" value="正常" />
            <el-option label="满仓" value="满仓" />
            <el-option label="维护中" value="维护中" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- ===== 新建调拨单对话框（真实提交） ===== -->
    <el-dialog v-model="transferDialogVisible" title="新建调拨单" width="560px">
      <el-form :model="transferForm" label-width="90px">
        <el-form-item label="源仓">
          <el-select v-model="transferForm.fromWarehouse" style="width: 100%">
            <el-option
              v-for="wh in warehouseOverview"
              :key="wh.name"
              :label="wh.name"
              :value="wh.name"
              :disabled="wh.name === transferForm.toWarehouse"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="目标仓">
          <el-select v-model="transferForm.toWarehouse" style="width: 100%">
            <el-option
              v-for="wh in warehouseOverview"
              :key="wh.name"
              :label="wh.name"
              :value="wh.name"
              :disabled="wh.name === transferForm.fromWarehouse"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="商品">
          <el-select v-model="transferForm.skuId" style="width: 100%" filterable placeholder="请选择 SKU">
            <el-option v-for="p in productOptions" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="数量">
          <el-input-number v-model="transferForm.quantity" :min="1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveTransfer">提交调拨单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Van, MagicStick, Box, ArrowRight, List, Printer } from '@element-plus/icons-vue'
import {
  getOverseasWarehouse, createOverseasWarehouse, updateOverseasWarehouse, deleteOverseasWarehouse,
  getWarehouseStockOverview, getWarehouseShippingStats, getInventoryTransferList,
  getWarehouseAllocationSuggest, applyWarehouseAllocation, getInventoryList,
  createInventoryTransfer, approveInventoryTransfer, completeInventoryTransfer
} from '../api/admin'

const pageTitle = '海外仓管理'

// ==================== 真实数据：仓库总览 / 调拨单 / 分配建议 / 发货统计 ====================

// 仓库库存总览（来自 /logistics/warehouse/stock-overview）
const warehouseOverview = ref([])

// 当前选中的仓库 Tab
const activeWarehouse = ref('')

// 仓间调拨单列表（来自 /inventory-transfer/list）
const transferList = ref([])

// 可调拨 SKU 选项（来自 /inventory/list）
const productOptions = ref([])

// 智能分配建议（来自 /logistics/warehouse/allocation-suggest）
const allocationSuggestions = ref([])

// 本地发货统计（来自 /logistics/warehouse/shipping-stats）
const shippingStats = reactive({
  todayPending: 0,
  shippedToday: 0,
  slaRate: 0,
  byWarehouse: []
})

// 条形图最大发货量，用于计算柱子高度比例（空数据显示 1 避免除零）
const maxShipValue = computed(() => Math.max(...shippingStats.byWarehouse.map(item => item.value), 1))

// 计算条形图柱子高度百分比（纯 CSS 图表）
function barHeight(value) {
  return Math.round((value / maxShipValue.value) * 100)
}

// 数字千分位格式化
function fmtNum(num) {
  return Number(num).toLocaleString()
}

// 调拨单状态 → el-tag 类型映射
function statusTagType(status) {
  const map = { 待审核: 'warning', 运输中: 'primary', 已到达: 'success', 已驳回: 'danger' }
  return map[status] || 'info'
}

// ==================== 数据加载 ====================

// 各仓库存总览
async function loadStockOverview() {
  try {
    const list = await getWarehouseStockOverview()
    warehouseOverview.value = (list || []).map(w => ({
      id: w.id,
      name: w.name,
      skuTypes: w.skuTypes || 0,
      availableStock: w.availableStock || 0,
      inTransit: w.inTransit || 0
    }))
    // 默认选中第一个仓库
    if (!activeWarehouse.value && warehouseOverview.value.length) {
      activeWarehouse.value = warehouseOverview.value[0].name
    }
  } catch (e) {
    console.error('获取仓库库存总览失败', e)
    warehouseOverview.value = []
  }
}

// 仓间调拨单列表
async function loadTransfers() {
  try {
    const res = await getInventoryTransferList({ page: 1, size: 10 })
    const records = (res && (res.records || res)) || []
    const statusMap = { PENDING: '待审核', IN_TRANSIT: '运输中', COMPLETED: '已到达', REJECTED: '已驳回' }
    transferList.value = (Array.isArray(records) ? records : []).map(r => {
      const dbStatus = String(r.dbStatus || r.status || '').toUpperCase()
      return {
        id: r.id,
        code: 'TR' + String(r.id || '').padStart(8, '0'),
        fromWarehouse: r.fromWarehouse || '-',
        toWarehouse: r.toWarehouse || '-',
        product: r.productName || '-',
        quantity: r.quantity || 0,
        status: statusMap[dbStatus] || '待审核',
        dbStatus,
        createdAt: r.createTime ? String(r.createTime).slice(0, 10) : '-',
        eta: r.completeTime ? String(r.completeTime).slice(0, 10) : '--'
      }
    })
  } catch (e) {
    console.error('获取调拨单列表失败', e)
    transferList.value = []
  }
}

// 智能分配建议
async function loadSuggestions() {
  try {
    const res = await getWarehouseAllocationSuggest()
    allocationSuggestions.value = (res || [])
      // 已采纳的建议不再展示
      .filter(s => String(s.status || '').toUpperCase() !== 'APPLIED')
      .map(s => ({
      id: s.id,
      productName: s.productName || '-',
      fromWarehouse: s.fromWarehouse || '-',
      toWarehouse: s.toWarehouse || '-',
      qty: s.qty || 0,
      reason: s.reason || '',
      priority: s.priority,
      status: s.status
    }))
  } catch (e) {
    console.error('获取智能分配建议失败', e)
    allocationSuggestions.value = []
  }
}

// 本地发货统计
async function loadShippingStats() {
  try {
    const res = await getWarehouseShippingStats()
    if (res) {
      shippingStats.todayPending = res.todayPending || 0
      shippingStats.shippedToday = res.shippedToday || 0
      shippingStats.slaRate = res.slaRate || 0
      shippingStats.byWarehouse = res.byWarehouse || []
    }
  } catch (e) {
    console.error('获取本地发货统计失败', e)
  }
}

// 可调拨 SKU 选项
async function loadProductOptions() {
  try {
    const res = await getInventoryList({ page: 1, size: 200 })
    const list = (res && (res.records || res)) || []
    productOptions.value = (Array.isArray(list) ? list : []).map(i => ({
      id: i.id,
      name: i.name ? `${i.name}${i.sku ? ' / ' + i.sku : ''}` : 'SKU-' + i.id
    }))
  } catch (e) {
    console.error('获取可调拨商品失败', e)
    productOptions.value = []
  }
}

// ==================== 交互 ====================

// 查看全部调拨单
function viewAllTransfers() {
  ElMessage.info('当前展示最近 10 条调拨单')
}

// 查看调拨单详情
function viewTransferDetail(row) {
  ElMessage.info(`调拨单 ${row.code}：${row.fromWarehouse} → ${row.toWarehouse}，商品 ${row.product} x ${row.quantity}`)
}

// 审核通过（PENDING → IN_TRANSIT）
async function approveTransfer(row) {
  try {
    await approveInventoryTransfer(row.id)
    ElMessage.success(`调拨单 ${row.code} 已审核通过`)
    await loadTransfers()
  } catch (e) {
    ElMessage.error('审核失败：' + (e.message || '未知错误'))
  }
}

// 确认入库（IN_TRANSIT → COMPLETED）
async function confirmArrival(row) {
  try {
    await completeInventoryTransfer(row.id)
    ElMessage.success(`调拨单 ${row.code} 已确认入库`)
    await loadTransfers()
    await loadStockOverview()
  } catch (e) {
    ElMessage.error('确认入库失败：' + (e.message || '未知错误'))
  }
}

// 采纳智能分配建议
async function applySuggestion(item) {
  try {
    await applyWarehouseAllocation(item.id)
    ElMessage.success('已采纳该分配建议')
    await loadSuggestions()
  } catch (e) {
    ElMessage.error('采纳失败：' + (e.message || '未知错误'))
  }
}

// 打开新建调拨单对话框，preset 为智能分配建议的预填项
function openTransferDialog(preset = {}) {
  transferForm.fromWarehouse = preset.fromWarehouse || (warehouseOverview.value[0] && warehouseOverview.value[0].name) || ''
  transferForm.toWarehouse = preset.toWarehouse || (warehouseOverview.value[1] && warehouseOverview.value[1].name) || ''
  transferForm.skuId = preset.skuId || (productOptions.value[0] && productOptions.value[0].id) || null
  transferForm.quantity = preset.quantity || 100
  transferDialogVisible.value = true
}

// 提交调拨单（真实写入 mo_inventory_transfer）
async function handleSaveTransfer() {
  if (!transferForm.fromWarehouse || !transferForm.toWarehouse || !transferForm.skuId) {
    ElMessage.warning('请填写完整的调拨单信息')
    return
  }
  if (transferForm.fromWarehouse === transferForm.toWarehouse) {
    ElMessage.warning('源仓与目标仓不能相同')
    return
  }
  try {
    await createInventoryTransfer({
      skuId: transferForm.skuId,
      fromWarehouse: transferForm.fromWarehouse,
      toWarehouse: transferForm.toWarehouse,
      quantity: transferForm.quantity,
      reason: '仓间调拨'
    })
    transferDialogVisible.value = false
    ElMessage.success('调拨单已提交，等待审核')
    await loadTransfers()
  } catch (e) {
    ElMessage.error('提交失败：' + (e.message || '未知错误'))
  }
}

// ==================== 海外仓 CRUD（原有功能，保留） ====================

const filters = reactive({ keyword: '' })
const tableData = ref([])
const currentPage = ref(1)
const pageSize = ref(15)
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const editForm = reactive({
  name: '',
  country: '',
  skuCount: 0,
  totalStock: 0,
  usageRate: 0,
  status: '正常'
})
// 新建调拨单对话框状态
const transferDialogVisible = ref(false)
const transferForm = reactive({
  fromWarehouse: '',
  toWarehouse: '',
  skuId: null,
  quantity: 100
})

// 加载海外仓数据
async function loadData() {
  try {
    const res = await getOverseasWarehouse()
    const list = res || []
    let filtered = [...list]
    if (filters.keyword) {
      filtered = filtered.filter(item => item.name.includes(filters.keyword) || item.country.includes(filters.keyword))
    }
    tableData.value = filtered
    total.value = filtered.length
  } catch (error) {
    console.error('获取海外仓数据失败:', error)
    ElMessage.error('获取海外仓数据失败')
  }
}
function handleSearch() { currentPage.value = 1; loadData() }
function handleReset() { filters.keyword = ''; handleSearch() }
function handleAdd() { dialogTitle.value = '新建海外仓'; editForm.name = ''; editForm.country = ''; editForm.skuCount = 0; editForm.totalStock = 0; editForm.usageRate = 0; editForm.status = '正常'; dialogVisible.value = true }
function handleEdit(row) { dialogTitle.value = '编辑海外仓'; Object.assign(editForm, row); dialogVisible.value = true }
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确定删除？', '提示')
    await deleteOverseasWarehouse(row.id)
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
      await updateOverseasWarehouse(editForm.id, {
        name: editForm.name,
        country: editForm.country,
        skuCount: editForm.skuCount,
        totalStock: editForm.totalStock,
        usageRate: editForm.usageRate,
        status: editForm.status
      })
    } else {
      await createOverseasWarehouse({
        name: editForm.name,
        country: editForm.country,
        skuCount: editForm.skuCount,
        totalStock: editForm.totalStock,
        usageRate: editForm.usageRate,
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
onMounted(() => {
  loadData()
  loadStockOverview()
  loadTransfers()
  loadSuggestions()
  loadShippingStats()
  loadProductOptions()
})
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
.page-header h2 { font-size: 22px; font-weight: 700; color: var(--text-800); margin: 0; }
.page-header-left p { font-size: 13px; color: var(--text-400); margin-top: 4px; }
.filter-card { margin-bottom: 16px; }
.header-actions { display: flex; gap: 8px; }

/* ===== 仓库选择 Tab ===== */
.warehouse-tabs { display: flex; gap: 8px; margin-bottom: 24px; flex-wrap: wrap; }
.wh-tab {
  padding: 8px 20px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
  border: none;
  cursor: pointer;
  background: var(--background-200);
  color: var(--text-500);
  transition: all 0.15s ease;
}
.wh-tab:hover { color: var(--text-700); }
.wh-tab.active { background: var(--primary); color: var(--primary-foreground); }

/* ===== 库存总览卡片（4 列网格） ===== */
.stock-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 24px; }
.stock-card { background: var(--card); border: 1px solid var(--border); border-radius: var(--radius); padding: 20px; box-shadow: var(--shadow-xs); }
.stock-card-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.stock-name { font-size: 14px; font-weight: 600; color: var(--text-800); }
.stock-icon { width: 32px; height: 32px; border-radius: 8px; background: var(--brand-50); display: flex; align-items: center; justify-content: center; color: var(--primary); }
.stock-label { font-size: 12px; color: var(--text-400); }
.stock-value { font-size: 22px; font-weight: 700; color: var(--text-800); margin-top: 2px; }
.stock-sub { display: flex; gap: 16px; margin-top: 10px; }
.stock-sub-value { font-size: 14px; font-weight: 600; }
.stock-sub-value.success { color: var(--state-success); }
.stock-sub-value.primary { color: var(--brand-500); }

/* ===== 两列布局：调拨列表 + 侧边面板 ===== */
.transfer-layout { display: grid; grid-template-columns: 1fr 340px; gap: 20px; margin-bottom: 24px; align-items: start; }
.section-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.section-title { font-size: 16px; font-weight: 700; color: var(--text-800); margin: 0; }
.view-all { display: inline-flex; align-items: center; gap: 4px; height: 32px; font-size: 12px; color: var(--primary); border: none; background: transparent; cursor: pointer; }
.table-card { border: 1px solid var(--border); border-radius: var(--radius); box-shadow: var(--shadow-xs); }
.sub-heading { margin-top: 8px; }

/* ===== 侧边面板 ===== */
.side-panel { display: flex; flex-direction: column; gap: 16px; }
.side-card { border: 1px solid var(--border); border-radius: var(--radius); box-shadow: var(--shadow-xs); }
.card-title { display: flex; align-items: center; gap: 8px; margin-bottom: 14px; }
.title-icon { width: 32px; height: 32px; border-radius: 8px; display: flex; align-items: center; justify-content: center; }
.title-icon.brand { background: var(--brand-50); color: var(--primary); }
.title-icon.success { background: var(--state-success-surface); color: var(--state-success); }
.card-title h4 { font-size: 14px; font-weight: 700; color: var(--text-800); margin: 0; }

/* ===== 智能分配建议 ===== */
.suggest-text { font-size: 13px; color: var(--text-600); line-height: 1.6; margin: 0 0 16px; }
.suggest-text strong { color: var(--text-800); }
.suggest-item { background: var(--background-100); border-radius: 8px; padding: 12px; margin-bottom: 10px; }
.suggest-address { font-size: 13px; font-weight: 600; color: var(--text-800); }
.suggest-wh { font-size: 12px; color: var(--text-500); margin-top: 4px; }
.suggest-wh-name { color: var(--primary); font-weight: 600; }
.suggest-reason { font-size: 12px; color: var(--text-400); margin-top: 4px; line-height: 1.5; }
.suggest-actions { display: flex; gap: 8px; margin-top: 14px; }

/* ===== 本地发货统计 ===== */
.kpi-duo { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.kpi-box { background: var(--background-100); border-radius: 8px; padding: 12px; text-align: center; }
.kpi-num { font-size: 24px; font-weight: 700; }
.kpi-num.primary { color: var(--brand-500); }
.kpi-num.success { color: var(--state-success); }
.kpi-label { font-size: 12px; color: var(--text-400); margin-top: 2px; }
.sla-row { margin-top: 12px; }
.sla-label { display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px; font-size: 12px; color: var(--text-500); }
.sla-value { font-size: 13px; font-weight: 700; color: var(--state-success); }
.sla-track { width: 100%; height: 6px; border-radius: 3px; background: var(--background-200); overflow: hidden; }
.sla-fill { height: 100%; border-radius: 3px; background: var(--state-success); }
.bar-head { margin-top: 16px; margin-bottom: 4px; }
.bar-head-label { font-size: 12px; color: var(--text-500); }
.ops-row { display: flex; gap: 8px; margin-top: 16px; }
</style>
