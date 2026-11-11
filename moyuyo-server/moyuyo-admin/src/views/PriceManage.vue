<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>价格管理</h2>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd">调整价格</el-button>
      </div>
    </div>

    <!-- ===== 价格 CRUD 区域（原有功能保留） ===== -->
    <el-card shadow="never" class="filter-card">
      <el-form :model="filters" inline>
        <el-form-item label="商品名称">
          <el-input v-model="filters.keyword" placeholder="请输入关键词" clearable />
        </el-form-item>
        <el-form-item label="价格区间">
          <el-input-number v-model="filters.priceMin" :min="0" placeholder="最低价" style="width:130px" />
          <span style="margin:0 8px">-</span>
          <el-input-number v-model="filters.priceMax" :min="0" placeholder="最高价" style="width:130px" />
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
        <el-table-column prop="productName" label="商品名称" min-width="160" />
        <el-table-column prop="sku" label="SKU" width="140" />
        <el-table-column prop="originalPrice" label="原价" width="100">
          <template #default="{ row }">¥{{ row.originalPrice }}</template>
        </el-table-column>
        <el-table-column prop="sellingPrice" label="销售价" width="100">
          <template #default="{ row }">¥{{ row.sellingPrice }}</template>
        </el-table-column>
        <el-table-column prop="costPrice" label="成本价" width="100">
          <template #default="{ row }">¥{{ row.costPrice }}</template>
        </el-table-column>
        <el-table-column prop="margin" label="毛利率" width="100">
          <template #default="{ row }">{{ row.margin }}%</template>
        </el-table-column>
        <el-table-column prop="lastModifyTime" label="最后修改时间" width="170" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleEdit(row)">调价</el-button>
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

    <!-- ===== 价格优先级 + 营销活动互斥矩阵 并排展示 ===== -->
    <div class="dual-col">

      <!-- 价格优先级说明（业务规则） -->
      <el-card shadow="never" class="config-card">
        <template #header>
          <div class="card-title-row">
            <span class="section-icon" style="color:var(--brand-500)">◎</span>
            <span class="section-title">价格优先级</span>
          </div>
        </template>
        <p class="section-desc">结算时先命中秒杀价 / 活动价，会员折扣在此基础上叠加，均未命中时使用销售价</p>
        <div class="priority-ladder">
          <div
            v-for="(item, idx) in priorityLevels"
            :key="item.name"
            class="priority-step"
            :style="{ width: item.width, background: item.background }"
          >
            <div class="priority-left">
              <span class="priority-dot" :style="{ background: item.color }"></span>
              <span class="priority-name" :style="{ color: item.color }">{{ item.name }}</span>
            </div>
            <span class="priority-level" :style="{ color: item.color }">{{ item.level }}</span>
          </div>
        </div>
      </el-card>

      <!-- 营销活动互斥矩阵（业务规则） -->
      <el-card shadow="never" class="config-card">
        <template #header>
          <div class="card-title-row">
            <span class="section-icon" style="color:var(--brand-500)">▦</span>
            <span class="section-title">营销活动互斥矩阵</span>
          </div>
        </template>
        <p class="section-desc">交叉项表示两种活动是否可同时叠加使用</p>
        <div class="matrix-wrap">
          <table class="matrix-table">
            <thead>
              <tr>
                <th>活动</th>
                <th v-for="name in activityNames" :key="name">{{ name }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in matrixRows" :key="row.name">
                <td>{{ row.name }}</td>
                <td
                  v-for="(cell, j) in row.cells"
                  :key="j"
                  :class="cell === 'yes' ? 'matrix-yes' : cell === 'no' ? 'matrix-no' : ''"
                >
                  <span v-if="cell === '-'">--</span>
                  <span v-else class="matrix-icon">{{ cell === 'yes' ? '✓' : '✗' }}</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="matrix-legend">
          <span class="legend-item"><span class="matrix-icon matrix-yes">✓</span> 可叠加</span>
          <span class="legend-item"><span class="matrix-icon matrix-no">✗</span> 互斥</span>
        </div>
      </el-card>
    </div>

    <!-- ===== 会员价配置（按会员等级统一折扣，真实接口） ===== -->
    <el-card shadow="never" class="config-card">
      <template #header>
        <div class="card-title-row card-title-between">
          <div class="card-title-row">
            <span class="section-icon" style="color:var(--brand-500)">★</span>
            <span class="section-title">会员价配置</span>
          </div>
          <el-button
            type="primary"
            size="small"
            :loading="memberSaving"
            @click="handleSaveMemberConfig"
          >保存会员价配置</el-button>
        </div>
      </template>
      <el-table :data="memberConfigList" border>
        <el-table-column label="会员等级" width="180">
          <template #default="{ row }">
            <span class="member-badge" :class="row.badgeClass">{{ row.badgeText }}</span>
            <span class="member-name">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="等级说明" min-width="200" />
        <el-table-column label="折扣率（折）" width="220">
          <template #default="{ row }">
            <el-input-number
              v-model="row.discount"
              :min="5"
              :max="10"
              :step="0.1"
              :precision="1"
              size="small"
              style="width:140px"
            />
          </template>
        </el-table-column>
        <el-table-column label="启用" width="90" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" />
          </template>
        </el-table-column>
        <el-table-column label="说明" min-width="220">
          <template #default="{ row }">
            <span style="font-size:13px;color:var(--text-500)">
              结算价 = 商品价 × {{ row.discount }} 折{{ row.discount === 10 ? '（无折扣）' : '' }}
            </span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- ===== 最近改价记录（真实数据） ===== -->
    <el-card shadow="never" class="config-card">
      <template #header>
        <div class="card-title-row card-title-between">
          <div class="card-title-row">
            <span class="section-icon" style="color:var(--brand-500)">☑</span>
            <span class="section-title">最近改价记录</span>
          </div>
          <el-tag type="primary" effect="light">{{ priceModifyList.length }} 条</el-tag>
        </div>
      </template>
      <el-table :data="priceModifyList">
        <el-table-column prop="orderNo" label="订单编号" min-width="160" />
        <el-table-column prop="product" label="商品" min-width="150" show-overflow-tooltip />
        <el-table-column label="原金额" width="110" align="center">
          <template #default="{ row }">
            <span style="color:var(--text-400)">¥{{ row.originalAmount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="调整金额" width="110" align="center">
          <template #default="{ row }">
            <span class="change-badge" :class="row.adjustAmount >= 0 ? 'change-up' : 'change-down'">
              {{ row.adjustAmount >= 0 ? '+' : '' }}{{ row.adjustAmount }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="最终金额" width="110" align="center">
          <template #default="{ row }">
            <span style="font-weight:700;color:var(--primary)">¥{{ row.finalAmount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="改价原因" min-width="160" show-overflow-tooltip />
        <el-table-column prop="operator" label="操作人" width="110" align="center" />
        <el-table-column prop="createTime" label="改价时间" width="170" align="center" />
      </el-table>
    </el-card>

    <!-- ===== 调价弹窗（原有功能保留） ===== -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="700px">
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="商品名称">
          <el-input v-model="editForm.productName" disabled />
        </el-form-item>
        <el-form-item label="SKU">
          <el-input v-model="editForm.sku" disabled />
        </el-form-item>
        <el-form-item label="原价">
          <el-input-number v-model="editForm.originalPrice" :min="0" :precision="2" style="width:100%" disabled />
        </el-form-item>
        <el-form-item label="销售价" required>
          <el-input-number v-model="editForm.sellingPrice" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="成本价">
          <el-input-number v-model="editForm.costPrice" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="调价原因">
          <el-input v-model="editForm.reason" type="textarea" :rows="3" placeholder="请输入调价原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getPriceList, createPrice, updatePrice,
  getMemberPriceConfigs, saveMemberPriceConfigs, getPriceModifyList, getPriceRules
} from '../api/admin'

// ===== 价格 CRUD（原有逻辑保留） =====
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)

const filters = reactive({
  keyword: '',
  priceMin: null,
  priceMax: null
})

const editForm = reactive({
  id: null,
  productName: '',
  sku: '',
  originalPrice: 0,
  sellingPrice: 0,
  costPrice: 0,
  margin: 0,
  reason: '',
  lastModifyTime: ''
})

const tableData = ref([])

// 从API加载价格列表数据（服务端分页）
async function loadData() {
  try {
    const params = { page: currentPage.value, size: pageSize.value }
    if (filters.keyword) params.keyword = filters.keyword
    // 价格区间下沉到后端，避免前端过滤导致分页页数/总数不准
    if (filters.priceMin !== null && filters.priceMin !== undefined) params.priceMin = filters.priceMin
    if (filters.priceMax !== null && filters.priceMax !== undefined) params.priceMax = filters.priceMax
    const res = await getPriceList(params)
    const list = (res && res.records) || res || []
    tableData.value = Array.isArray(list) ? list : []
    total.value = Number(res && res.total) || tableData.value.length
  } catch (e) {
    console.error('加载价格列表失败:', e)
    ElMessage.error('加载价格列表失败')
  }
}

function handleSearch() { currentPage.value = 1; loadData() }
function handleReset() { filters.keyword = ''; filters.priceMin = null; filters.priceMax = null; handleSearch() }

function handleAdd() {
  isEdit.value = false
  dialogTitle.value = '调整价格'
  editForm.id = null
  editForm.productName = ''
  editForm.sku = ''
  editForm.originalPrice = 0
  editForm.sellingPrice = 0
  editForm.costPrice = 0
  editForm.margin = 0
  editForm.reason = ''
  editForm.lastModifyTime = ''
  dialogVisible.value = true
}

function handleEdit(row) {
  isEdit.value = true
  dialogTitle.value = '调整价格'
  Object.assign(editForm, row)
  editForm.reason = ''
  dialogVisible.value = true
}

// 保存调价信息（调用API）
async function handleSave() {
  if (editForm.sellingPrice <= 0) {
    ElMessage.warning('请输入有效的销售价')
    return
  }
  try {
    if (isEdit.value) {
      await updatePrice({
        id: editForm.id,
        sellingPrice: editForm.sellingPrice,
        costPrice: editForm.costPrice,
        reason: editForm.reason
      })
      ElMessage.success('调价成功')
    } else {
      await createPrice({
        productName: editForm.productName,
        sku: editForm.sku,
        sellingPrice: editForm.sellingPrice,
        costPrice: editForm.costPrice
      })
      ElMessage.success('新增价格成功')
    }
    dialogVisible.value = false
    loadData()
  } catch (e) {
    console.error('保存价格失败:', e)
    ElMessage.error('保存失败')
  }
}

// ===== 价格优先级与营销叠加矩阵（真实口径：GET /price/rules） =====
// 仅保留阶梯的视觉样式，优先级内容与叠加关系全部由后端返回
const PRIORITY_STYLES = [
  { width: '100%', color: 'var(--state-error)', background: 'var(--state-error-surface)' },
  { width: '92%', color: 'var(--brand-500)', background: 'var(--brand-50)' },
  { width: '84%', color: 'var(--chart-3)', background: '#fff8e8' },
  { width: '76%', color: 'var(--text-400)', background: 'var(--background-200)' }
]
const priorityLevels = ref([])
const activityNames = ref([])
const matrixRows = ref([])

/** 加载后端计价规则（优先级 + 叠加矩阵） */
async function loadPriceRules() {
  try {
    const res = await getPriceRules()
    const list = Array.isArray(res?.priority) ? res.priority : []
    priorityLevels.value = list.map((item, idx) => ({
      ...item,
      ...(PRIORITY_STYLES[idx] || PRIORITY_STYLES[PRIORITY_STYLES.length - 1])
    }))
    activityNames.value = Array.isArray(res?.activityNames) ? res.activityNames : []
    matrixRows.value = Array.isArray(res?.matrixRows) ? res.matrixRows : []
  } catch (e) {
    console.error('获取价格规则失败:', e)
    ElMessage.error('获取价格规则失败')
  }
}

// ===== 会员价配置（五档会员等级统一折扣，真实接口） =====
// 后端 discountRate 为折扣系数（1.00 = 原价），页面展示为"折"（0.95 → 9.5 折）
const MEMBER_LEVEL_META = {
  NORMAL: { name: '普通会员', badgeText: '普通', badgeClass: 'badge-normal', description: '注册即获得' },
  SILVER: { name: '银卡会员', badgeText: '银卡', badgeClass: 'badge-silver', description: '累计消费满 500 元' },
  GOLD: { name: '金卡会员', badgeText: '金卡', badgeClass: 'badge-gold', description: '累计消费满 2000 元' },
  PLATINUM: { name: '白金会员', badgeText: '白金', badgeClass: 'badge-platinum', description: '累计消费满 8000 元' },
  DIAMOND: { name: '钻石会员', badgeText: '钻石', badgeClass: 'badge-diamond', description: '累计消费满 25000 元' }
}
const memberConfigList = ref([])
const memberSaving = ref(false)

/** 后端配置 → 页面行（折扣系数换算为"折"） */
function mapMemberConfig(row) {
  const meta = MEMBER_LEVEL_META[row.level] || {
    name: row.level, badgeText: row.level, badgeClass: 'badge-normal', description: ''
  }
  const rate = Number(row.discountRate != null ? row.discountRate : 1)
  return {
    level: row.level,
    name: meta.name,
    badgeText: meta.badgeText,
    badgeClass: meta.badgeClass,
    description: row.description || meta.description,
    discount: Number((rate * 10).toFixed(1)),
    enabled: row.enabled !== false
  }
}

async function loadMemberConfigs() {
  try {
    const list = await getMemberPriceConfigs()
    memberConfigList.value = (Array.isArray(list) ? list : []).map(mapMemberConfig)
  } catch (e) {
    ElMessage.error('获取会员价配置失败')
  }
}

// 保存会员价配置（真实接口：PUT /price/member-configs）
async function handleSaveMemberConfig() {
  if (memberConfigList.value.length === 0) {
    ElMessage.warning('暂无可保存的会员价配置')
    return
  }
  memberSaving.value = true
  try {
    const payload = memberConfigList.value.map(row => ({
      level: row.level,
      // 页面"折" → 后端折扣系数，保留两位小数（9.5 折 → 0.95）
      discountRate: Number((Number(row.discount || 10) / 10).toFixed(2)),
      description: row.description,
      enabled: row.enabled !== false
    }))
    const saved = await saveMemberPriceConfigs(payload)
    if (Array.isArray(saved)) {
      memberConfigList.value = saved.map(mapMemberConfig)
    }
    ElMessage.success('会员价配置已保存，新折扣将在下单时生效')
  } catch (e) {
    ElMessage.error((e && e.message) || '保存会员价配置失败')
  } finally {
    memberSaving.value = false
  }
}

// ===== 最近改价记录（真实数据：/order-ops/price-modify/list） =====
const priceModifyList = ref([])

// 加载最近改价记录
async function loadPriceModifyList() {
  try {
    const res = await getPriceModifyList({ page: 1, size: 10 })
    priceModifyList.value = Array.isArray(res) ? res : (res?.records || [])
  } catch (e) {
    console.error('获取改价记录失败', e)
    priceModifyList.value = []
  }
}

onMounted(() => {
  loadData()
  loadMemberConfigs()
  loadPriceModifyList()
  loadPriceRules()
})
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 22px; font-weight: 700; color: var(--text-800); margin: 0; }
.filter-card { margin-bottom: 16px; }
.header-actions { display: flex; gap: 8px; }
.config-card { margin-bottom: 20px; }

/* 区块标题 */
.card-title-row { display: flex; align-items: center; gap: 8px; }
.card-title-between { justify-content: space-between; }
.section-icon { font-size: 16px; }
.section-title { font-size: 15px; font-weight: 700; color: var(--text-800); }
.section-desc { font-size: 13px; color: var(--text-500); margin: 0 0 16px; }

/* 双列布局：价格优先级 + 互斥矩阵 */
.dual-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin-bottom: 4px;
}

/* ===== 价格优先级阶梯 ===== */
.priority-ladder { display: flex; flex-direction: column; align-items: center; }
.priority-step {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  border-radius: 8px;
  margin-bottom: 6px;
}
/* 阶梯之间连接线 */
.priority-step::after {
  content: '';
  position: absolute;
  bottom: -6px;
  left: 50%;
  transform: translateX(-50%);
  width: 2px;
  height: 6px;
  background: var(--background-400);
}
.priority-step:last-child::after { display: none; }
.priority-left { display: flex; align-items: center; gap: 10px; }
.priority-dot { width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0; }
.priority-name { font-size: 14px; font-weight: 600; }
.priority-level { font-size: 12px; font-weight: 500; }

/* ===== 营销活动互斥矩阵 ===== */
.matrix-wrap { overflow-x: auto; }
.matrix-table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  border: 1px solid var(--border);
  border-radius: 8px;
  overflow: hidden;
}
.matrix-table thead th {
  background: var(--background-100);
  padding: 10px 14px;
  font-size: 12px;
  font-weight: 700;
  color: var(--text-500);
  text-align: center;
  border-bottom: 1px solid var(--border);
  border-right: 1px solid var(--border);
}
.matrix-table thead th:first-child { text-align: left; }
.matrix-table thead th:last-child { border-right: none; }
.matrix-table tbody td {
  padding: 10px 14px;
  font-size: 13px;
  color: var(--text-700);
  text-align: center;
  border-bottom: 1px solid var(--border);
  border-right: 1px solid var(--border);
  vertical-align: middle;
}
.matrix-table tbody td:first-child { text-align: left; font-weight: 600; color: var(--text-800); }
.matrix-table tbody td:last-child { border-right: none; }
.matrix-table tbody tr:last-child td { border-bottom: none; }
.matrix-table tbody tr:hover { background: var(--background-100); }
.matrix-icon { font-size: 14px; font-weight: 700; }
.matrix-yes { color: var(--state-success); }
.matrix-no { color: var(--state-error); }
.matrix-legend { display: flex; gap: 20px; margin-top: 12px; font-size: 12px; color: var(--text-500); }
.legend-item { display: inline-flex; align-items: center; gap: 6px; }

/* ===== 会员价配置 ===== */
.member-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  margin-right: 10px;
}
.badge-normal { background: var(--background-200); color: var(--text-500); }
.badge-silver { background: #f0f0f5; color: #8e8e93; }
.badge-gold { background: #fff8e8; color: #ff9500; }
.badge-platinum { background: #eef3f8; color: #5a7d9a; }
.badge-diamond { background: #f0e8ff; color: #5856d6; }
.member-name { font-size: 14px; font-weight: 600; color: var(--text-800); }

/* ===== 调价审批队列 ===== */
.change-badge {
  display: inline-flex;
  align-items: center;
  padding: 3px 10px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 700;
}
.change-down { background: var(--state-success-surface); color: var(--state-success); }
.change-up { background: var(--state-error-surface); color: var(--state-error); }
</style>
