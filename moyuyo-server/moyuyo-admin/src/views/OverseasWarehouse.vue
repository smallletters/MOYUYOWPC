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

    <!-- ===== 仓库选择 Tab（示例数据） ===== -->
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

    <!-- ===== 库存总览卡片（示例数据） ===== -->
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
      <!-- 左侧：仓间调拨列表（示例数据） -->
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
                <el-button v-if="row.status === '已到达'" type="success" link size="small" @click="confirmArrival(row)">确认入库</el-button>
                <el-button v-if="row.status === '差异处理'" type="danger" link size="small" @click="handleDiscrepancy(row)">处理差异</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </section>

      <!-- 右侧面板 -->
      <div class="side-panel">
        <!-- 智能分配建议（示例数据） -->
        <el-card shadow="never" class="side-card">
          <div class="card-title">
            <div class="title-icon brand">
              <el-icon><MagicStick /></el-icon>
            </div>
            <h4>智能分配建议</h4>
          </div>
          <p class="suggest-text">
            基于近30天销售数据，建议从美国西仓调拨
            <strong>200件 MILO胸背带</strong>
            到美国东仓，预计可降低东仓缺货率 15%，优化配送时效 0.5 天。
          </p>
          <!-- 按收货地址推荐最优仓库 + 理由 -->
          <div v-for="(s, idx) in allocationSuggestions" :key="idx" class="suggest-item">
            <div class="suggest-address">{{ s.address }}</div>
            <div class="suggest-wh">
              推荐仓库：<span class="suggest-wh-name">{{ s.warehouse }}</span>
            </div>
            <div class="suggest-reason">{{ s.reason }}</div>
          </div>
          <div class="suggest-actions">
            <el-button
              type="primary"
              size="small"
              style="flex: 1"
              @click="openTransferDialog({ fromWarehouse: '美国西仓', toWarehouse: '美国东仓', product: 'MILO胸背带', quantity: 200 })"
            >生成调拨单</el-button>
            <el-button size="small" style="flex: 1" @click="ignoreSuggestion">忽略</el-button>
          </div>
        </el-card>

        <!-- 本地发货统计（示例数据） -->
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
          <!-- 各仓本地发货量 / 占比（纯 CSS 条形图，复用全局 .bar-chart 样式） -->
          <div class="bar-head">
            <span class="bar-head-label">各仓本地发货量 / 占比</span>
          </div>
          <div class="bar-chart">
            <div v-for="item in shippingStats.byWarehouse" :key="item.name" class="bar-item">
              <span class="bar-value">{{ item.value }}</span>
              <div class="bar-fill" :style="{ height: barHeight(item.value) + '%' }"></div>
              <span class="bar-label">{{ item.name }} {{ item.ratio }}%</span>
            </div>
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

    <!-- ===== 新建调拨单对话框（示例数据，前端模拟，无后端 API） ===== -->
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
          <el-select v-model="transferForm.product" style="width: 100%">
            <el-option v-for="p in productOptions" :key="p" :label="p" :value="p" />
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
import { getOverseasWarehouse, createOverseasWarehouse, updateOverseasWarehouse, deleteOverseasWarehouse } from '../api/admin'

const pageTitle = '海外仓管理'

// ==================== 示例数据（无真实 API，仅用于页面展示） ====================

// 仓库总览数据（示例数据）
const warehouseOverview = ref([
  { id: 1, name: '美国西仓', skuTypes: 1234, availableStock: 45678, inTransit: 2300 },
  { id: 2, name: '美国东仓', skuTypes: 987, availableStock: 32100, inTransit: 1500 },
  { id: 3, name: '英国仓', skuTypes: 456, availableStock: 12300, inTransit: 800 },
  { id: 4, name: '德国仓', skuTypes: 321, availableStock: 8900, inTransit: 600 }
])

// 当前选中的仓库 Tab（示例数据，仅做视觉切换，不联动数据）
const activeWarehouse = ref('美国西仓')

// 仓间调拨单列表（示例数据）
const transferList = ref([
  { code: 'TR-20260708-001', fromWarehouse: '美国西仓', toWarehouse: '美国东仓', product: 'MILO胸背带', quantity: 12, status: '待审核', createdAt: '2026-07-08', eta: '--' },
  { code: 'TR-20260705-018', fromWarehouse: '美国西仓', toWarehouse: '英国仓', product: '尼龙牵引绳', quantity: 8, status: '运输中', createdAt: '2026-07-05', eta: '2026-07-15' },
  { code: 'TR-20260701-042', fromWarehouse: '美国东仓', toWarehouse: '美国西仓', product: '自动饮水机', quantity: 5, status: '已到达', createdAt: '2026-07-01', eta: '2026-07-06' },
  { code: 'TR-20260628-007', fromWarehouse: '英国仓', toWarehouse: '德国仓', product: '宠物零食罐', quantity: 3, status: '差异处理', createdAt: '2026-06-28', eta: '2026-07-03' }
])

// 可调拨商品选项（示例数据）
const productOptions = ['MILO胸背带', '尼龙牵引绳', '自动饮水机', '宠物零食罐']

// 智能分配建议（示例数据：按收货地址推荐最优仓库 + 理由）
const allocationSuggestions = [
  { address: '纽约州 10001（美东）', warehouse: '美国东仓', reason: '收货地址位于美东区域，东仓发货可缩短配送时效约 0.5 天' },
  { address: '加州 90001（美西）', warehouse: '美国西仓', reason: '收货地址位于美西区域，西仓库存充足且时效最优' },
  { address: '伦敦 SW1A（英国）', warehouse: '英国仓', reason: '本地仓直发可避免跨境清关，物流成本降低约 30%' }
]

// 本地发货统计（示例数据）
const shippingStats = reactive({
  todayPending: 45,
  shippedToday: 120,
  slaRate: 98,
  // 各仓本地发货量/占比条形图数据
  byWarehouse: [
    { name: '美西', value: 520, ratio: 39 },
    { name: '美东', value: 430, ratio: 32 },
    { name: '英国', value: 210, ratio: 16 },
    { name: '德国', value: 180, ratio: 13 }
  ]
})

// 条形图最大发货量，用于计算柱子高度比例
const maxShipValue = computed(() => Math.max(...shippingStats.byWarehouse.map(item => item.value)))

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
  const map = { 待审核: 'warning', 运输中: 'primary', 已到达: 'success', 差异处理: 'danger' }
  return map[status] || 'info'
}

// ==================== 新增区块的交互（示例数据，前端模拟） ====================

// 查看全部调拨单
function viewAllTransfers() {
  ElMessage.info('调拨单列表为示例数据，仅展示最近 4 条')
}

// 查看调拨单详情
function viewTransferDetail(row) {
  ElMessage.info(`调拨单 ${row.code}：${row.fromWarehouse} → ${row.toWarehouse}，商品 ${row.product} x ${row.quantity}`)
}

// 确认入库（已到达状态）
function confirmArrival(row) {
  ElMessage.success(`调拨单 ${row.code} 已确认入库（示例数据）`)
}

// 处理差异（差异处理状态）
function handleDiscrepancy(row) {
  ElMessage.warning(`调拨单 ${row.code} 存在数量差异，请线下核实后处理（示例数据）`)
}

// 忽略智能分配建议
function ignoreSuggestion() {
  ElMessage.info('已忽略该建议（示例数据）')
}

// 打开新建调拨单对话框，preset 为智能分配建议的预填项
function openTransferDialog(preset = {}) {
  transferForm.fromWarehouse = preset.fromWarehouse || (warehouseOverview.value[0] && warehouseOverview.value[0].name) || ''
  transferForm.toWarehouse = preset.toWarehouse || (warehouseOverview.value[1] && warehouseOverview.value[1].name) || ''
  transferForm.product = preset.product || productOptions[0]
  transferForm.quantity = preset.quantity || 100
  transferDialogVisible.value = true
}

// 提交调拨单（前端本地模拟，无后端 API）
function handleSaveTransfer() {
  if (!transferForm.fromWarehouse || !transferForm.toWarehouse || !transferForm.product) {
    ElMessage.warning('请填写完整的调拨单信息')
    return
  }
  if (transferForm.fromWarehouse === transferForm.toWarehouse) {
    ElMessage.warning('源仓与目标仓不能相同')
    return
  }
  const now = new Date()
  const y = now.getFullYear()
  const m = String(now.getMonth() + 1).padStart(2, '0')
  const d = String(now.getDate()).padStart(2, '0')
  const seq = String(transferList.value.length + 1).padStart(3, '0')
  transferList.value.unshift({
    code: `TR-${y}${m}${d}-${seq}`,
    fromWarehouse: transferForm.fromWarehouse,
    toWarehouse: transferForm.toWarehouse,
    product: transferForm.product,
    quantity: transferForm.quantity,
    status: '待审核',
    createdAt: `${y}-${m}-${d}`,
    eta: '--'
  })
  transferDialogVisible.value = false
  ElMessage.success('调拨单已创建（示例数据，未提交后端）')
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
  product: '',
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
onMounted(() => loadData())
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
