<template>
  <div class="page-wrapper">
    <!-- 页面标题区域 -->
    <div class="page-title-area">
      <h1>商品报表</h1>
      <p>按商品维度查看销量、销售额与利润表现，并集中处理商品举报</p>
    </div>

    <!-- 双 Tab 切换 -->
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

    <!-- ============ Tab1: 商品报表 ============ -->
    <template v-if="activeTab === 'report'">
      <!-- KPI 概览 -->
      <div class="kpi-grid">
        <div class="kpi-card">
          <div class="kpi-card-header">
            <span class="kpi-card-icon">💰</span>
            <span class="kpi-card-label">总销售额</span>
          </div>
          <div class="kpi-card-value">¥{{ fmtNum(totalRevenue) }}</div>
          <div class="kpi-card-trend"><span class="kpi-trend-up">统计区间</span></div>
        </div>
        <div class="kpi-card">
          <div class="kpi-card-header">
            <span class="kpi-card-icon">📦</span>
            <span class="kpi-card-label">总销量</span>
          </div>
          <div class="kpi-card-value">{{ fmtNum(totalSales) }}</div>
          <div class="kpi-card-trend"><span class="kpi-trend-text">全部商品</span></div>
        </div>
        <div class="kpi-card">
          <div class="kpi-card-header">
            <span class="kpi-card-icon">📈</span>
            <span class="kpi-card-label">总利润</span>
          </div>
          <div class="kpi-card-value">¥{{ fmtNum(totalProfit) }}</div>
          <div class="kpi-card-trend"><span class="kpi-trend-up">毛利合计</span></div>
        </div>
        <div class="kpi-card">
          <div class="kpi-card-header">
            <span class="kpi-card-icon">⚖️</span>
            <span class="kpi-card-label">平均利润率</span>
          </div>
          <div class="kpi-card-value">{{ avgProfitRate }}%</div>
          <div class="kpi-card-trend"><span class="kpi-trend-text">加权平均</span></div>
        </div>
      </div>

      <!-- 查询面板 -->
      <div class="query-panel">
        <div class="form-row">
          <div class="form-group">
            <label>日期范围</label>
            <el-date-picker
              v-model="dateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              style="width: 100%"
            />
          </div>
          <div class="form-group">
            <label>商品名称</label>
            <input v-model="filters.keyword" type="text" placeholder="请输入关键词" @keyup.enter="handleSearch" />
          </div>
          <div class="form-actions">
            <button class="btn btn-primary" @click="handleSearch">搜索</button>
            <button class="btn btn-outline" @click="handleReset">重置</button>
            <button class="btn btn-outline" :disabled="exporting" @click="handleExport">
              {{ exporting ? '导出中…' : '⤓ 导出报表' }}
            </button>
          </div>
        </div>
      </div>

      <!-- 报表表格 -->
      <div class="data-table-wrapper">
        <table class="data-table">
          <thead>
            <tr>
              <th>商品名称</th>
              <th>SKU</th>
              <th>销量</th>
              <th>销售额</th>
              <th>利润</th>
              <th>利润率</th>
              <th style="min-width: 100px">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in tableData" :key="row.id">
              <td>{{ row.productName }}</td>
              <td class="mono-cell">{{ row.sku }}</td>
              <td class="num-cell">{{ fmtNum(row.sales) }}</td>
              <td class="money">¥{{ fmtNum(row.revenue) }}</td>
              <td :class="row.profit >= 0 ? 'profit-up' : 'profit-down'">¥{{ fmtNum(row.profit) }}</td>
              <td>
                <span :class="row.profitRate >= 0 ? 'tag tag-green' : 'tag tag-red'">{{ row.profitRate }}%</span>
              </td>
              <td>
                <div class="cell-actions">
                  <button class="btn btn-sm btn-outline" @click="handleDetail(row)">详情</button>
                </div>
              </td>
            </tr>
            <tr v-if="tableData.length === 0">
              <td colspan="7">
                <div class="empty-state">
                  <div class="empty-state-icon">📊</div>
                  <div class="empty-state-text">暂无报表数据</div>
                </div>
              </td>
            </tr>
          </tbody>
        </table>

        <!-- 分页 -->
        <div class="pagination" v-if="total > 0">
          <span class="pagination-info">共 {{ total }} 条 · 第 {{ currentPage }} / {{ totalPages }} 页</span>
          <div class="pagination-btns">
            <button class="pagination-btn" :disabled="currentPage <= 1" @click="currentPage--">‹ 上一页</button>
            <button class="pagination-btn" :disabled="currentPage >= totalPages" @click="currentPage++">下一页 ›</button>
          </div>
        </div>
      </div>
    </template>

    <!-- ============ Tab2: 商品举报管理 ============ -->
    <template v-else>
      <!-- 举报类型筛选 Tab -->
      <div class="report-tabs">
        <button
          v-for="t in reportTabs"
          :key="t.key"
          class="filter-tab"
          :class="{ active: activeReportTab === t.key }"
          @click="activeReportTab = t.key"
        >
          {{ t.label }}
        </button>
      </div>

      <!-- SLA 汇总卡片 -->
      <div class="sla-summary">
        <div class="sla-card">
          <div class="sla-number urgent">{{ slaStats.urgent }}</div>
          <div class="sla-label">24h 内需响应</div>
        </div>
        <div class="sla-card">
          <div class="sla-number warning">{{ slaStats.warning }}</div>
          <div class="sla-label">72h 内需处理</div>
        </div>
        <div class="sla-card">
          <div class="sla-number info">{{ slaStats.emergency }}</div>
          <div class="sla-label">紧急投诉</div>
        </div>
      </div>

      <!-- 举报列表 -->
      <div class="report-list">
        <div v-for="item in filteredReports" :key="item.id" class="report-item">
          <div class="report-product">
            <div class="report-product-thumb">📦</div>
            <div class="report-product-info">
              <div class="report-product-name">{{ item.productName }}</div>
              <a class="report-product-link" @click.prevent="viewProduct(item)">查看商品 ↗</a>
            </div>
            <span class="report-tag" :class="item.typeClass">{{ item.type }}</span>
          </div>
          <p class="report-reason">{{ item.reason }}</p>
          <div class="report-meta">
            <span class="report-reporter">👤 {{ item.reporter }}</span>
            <span class="report-time">{{ item.time }}</span>
            <span v-if="item.sla" class="report-sla-countdown">⏱ {{ item.sla }}</span>
            <span v-else class="report-time" style="color: var(--state-success)">已处理</span>
          </div>
          <div class="report-status-row">
            <span class="report-status" :class="item.statusClass">{{ item.statusLabel }}</span>
            <div class="report-actions">
              <button class="btn-detail" @click="viewReportDetail(item)">查看详情</button>
              <button v-if="item.status !== 'DONE'" class="btn-handle" @click="handleReport(item)">处理</button>
            </div>
          </div>
        </div>
        <div v-if="filteredReports.length === 0" class="empty-state">
          <div class="empty-state-icon">✅</div>
          <div class="empty-state-text">该分类下暂无举报</div>
        </div>
      </div>

      <!-- 举报详情弹窗 -->
      <el-dialog v-model="detailVisible" title="举报详情" width="560px">
        <div v-if="currentReport" class="report-detail">
          <div class="detail-row"><span>举报商品</span><b>{{ currentReport.productName }}</b></div>
          <div class="detail-row"><span>举报类型</span><b>{{ currentReport.type }}</b></div>
          <div class="detail-row"><span>举报人</span><b>{{ currentReport.reporter }}</b></div>
          <div class="detail-row"><span>举报时间</span><b>{{ currentReport.time }}</b></div>
          <div class="detail-row"><span>当前状态</span><b>{{ currentReport.statusLabel }}</b></div>
          <div class="detail-row reason"><span>举报原因</span><p>{{ currentReport.reason }}</p></div>
          <div class="detail-row reason"><span>处理建议</span><p>{{ currentReport.suggestion || '核实商品描述与资质，按平台规则判定是否下架或处罚。' }}</p></div>
        </div>
        <template #footer>
          <el-button @click="detailVisible = false">关闭</el-button>
          <el-button v-if="currentReport && currentReport.status !== 'DONE'" type="primary" @click="handleReport(currentReport)">去处理</el-button>
        </template>
      </el-dialog>

      <!-- 处理弹窗 -->
      <el-dialog v-model="handleVisible" title="处理举报" width="520px">
        <el-form label-position="top">
          <el-form-item label="处理结果" required>
            <el-select v-model="handleResult" style="width:100%" placeholder="请选择处理结果">
              <el-option label="确认违规，下架商品" value="下架商品" />
              <el-option label="确认违规，警告商家" value="警告商家" />
              <el-option label="核实后不成立，驳回举报" value="驳回举报" />
              <el-option label="移交人工复核" value="人工复核" />
            </el-select>
          </el-form-item>
          <el-form-item label="处理说明">
            <el-input v-model="handleComment" type="textarea" :rows="3" placeholder="请输入处理说明（可选）" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="handleVisible = false">取消</el-button>
          <el-button type="primary" @click="confirmHandle">确认处理</el-button>
        </template>
      </el-dialog>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getProductAnalysisReport, getProductAnalysisList, createOrderExport } from '../api/admin'
import { toArray } from '../utils/safeArray'

// ===== Tab 状态 =====
const tabs = [
  { key: 'report', label: '商品报表' },
  { key: 'reports', label: '商品举报' }
]
const activeTab = ref('report')

// ===== Tab1: 商品报表 =====
const currentPage = ref(1)
const pageSize = 10
const total = ref(0)
const dateRange = ref(null)

const filters = reactive({
  keyword: ''
})

const tableData = ref([])
const allMapped = ref([])

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

// KPI 汇总（基于全量报表数据）
const totalRevenue = computed(() => allMapped.value.reduce((s, d) => s + d.revenue, 0))
const totalSales = computed(() => allMapped.value.reduce((s, d) => s + d.sales, 0))
const totalProfit = computed(() => allMapped.value.reduce((s, d) => s + d.profit, 0))
const avgProfitRate = computed(() => {
  const rev = totalRevenue.value
  if (rev <= 0) return '0.0'
  return (totalProfit.value / rev * 100).toFixed(1)
})

function fmtNum(n) {
  return Number(n || 0).toLocaleString('en-US', { maximumFractionDigits: 2 })
}

// 将后端字段映射到表格所需字段，缺失字段补 0/空
function mapRow(d) {
  const sales = Number(d.sales || 0)
  const price = Number(d.price || 0)
  const revenue = d.revenue != null ? Number(d.revenue) : sales * price
  // 利润：缺少成本字段时按销售额 30% 估算
  const profit = d.profit != null ? Number(d.profit) : Math.round(revenue * 0.3 * 100) / 100
  const profitRate = d.profitRate != null
    ? Number(d.profitRate)
    : (revenue > 0 ? Math.round((profit / revenue) * 10000) / 100 : 0)
  return {
    id: d.id,
    productName: d.name || d.productName || '-',
    sku: d.sku || '-',
    sales,
    revenue,
    profit,
    profitRate
  }
}

// 从API加载商品报表数据
async function loadData() {
  try {
    // 并行加载报表和列表数据
    const [reportRes, listRes] = await Promise.all([
      getProductAnalysisReport(),
      getProductAnalysisList()
    ])
    const rawList = toArray(listRes)
    allMapped.value = rawList.map(mapRow)
    applyFilters()
  } catch (e) {
    console.error('加载商品报表数据失败:', e)
    ElMessage.error('加载商品报表数据失败')
  }
}

// 前端过滤 + 分页
function applyFilters() {
  const filtered = allMapped.value.filter(d => {
    const kw = (filters.keyword || '').toLowerCase()
    if (kw && !(d.productName || '').toLowerCase().includes(kw)) return false
    return true
  })
  total.value = filtered.length
  const start = (currentPage.value - 1) * pageSize
  tableData.value = filtered.slice(start, start + pageSize)
}

function handleSearch() { currentPage.value = 1; applyFilters() }

function handleReset() { filters.keyword = ''; dateRange.value = null; handleSearch() }

const exporting = ref(false)

async function handleExport() {
  exporting.value = true
  try {
    // 创建报表导出任务：后端只读取 taskName/orderScope/format，type/keyword 不提交
    await createOrderExport({
      taskName: '商品报表导出',
      orderScope: 'all',
      format: 'excel'
    })
    ElMessage.success('报表导出任务创建成功，请稍后到导出管理页面下载')
  } catch (e) {
    console.error('导出失败:', e)
    ElMessage.error('导出失败: ' + (e.message || '未知错误'))
  } finally {
    exporting.value = false
  }
}

function handleDetail(row) {
  ElMessage.info('查看报表详情：' + row.productName)
}

// ===== Tab2: 商品举报管理 =====
const reportTabs = [
  { key: 'all', label: '全部' },
  { key: 'fake-ad', label: '虚假宣传' },
  { key: 'quality', label: '质量问题' },
  { key: 'infringement', label: '侵权投诉' },
  { key: 'banned', label: '违禁品' }
]
const activeReportTab = ref('all')

// 举报数据（与设计稿一致）
const reports = ref([
  {
    id: 1, productName: '进口天然有机猫粮 成猫专用', type: '虚假宣传', typeClass: 'fake-ad',
    reason: '商品描述标注"进口天然有机"，但实际配料表含有多种人工添加剂，涉嫌虚假宣传误导消费者。',
    reporter: '用户 1**8', time: '2026-07-07 14:30', sla: '剩余 6h 12m',
    status: 'PENDING', statusLabel: '待处理', statusClass: 'pending',
    suggestion: '核查商品配料表与认证证书，若描述不符应下架并整改。'
  },
  {
    id: 2, productName: '宠物自动饮水机 静音循环过滤', type: '质量问题', typeClass: 'quality',
    reason: '饮水机使用不到一周出现漏水问题，且过滤芯存在异味，怀疑材料不达标存在安全隐患。',
    reporter: '用户 3**2', time: '2026-07-06 09:15', sla: '剩余 18h 45m',
    status: 'PROCESSING', statusLabel: '处理中', statusClass: 'processing',
    suggestion: '联系商家提供质检报告，安排抽检并跟踪退换货处理。'
  },
  {
    id: 3, productName: 'MOYUYO联名宠物服饰 设计师限定款', type: '侵权投诉', typeClass: 'infringement',
    reason: '该商品未经授权使用我方注册商标"MOYUYO"，且商品图片盗用官方宣传素材，严重侵犯商标权及著作权。',
    reporter: '用户 6**5', time: '2026-07-07 18:42', sla: '剩余 2h 08m',
    status: 'PENDING', statusLabel: '待处理', statusClass: 'pending',
    suggestion: '核实授权文件，确认侵权后立即下架并通知商家。'
  },
  {
    id: 4, productName: '进口宠物保健药品 免处方驱虫', type: '违禁品', typeClass: 'banned',
    reason: '该商品为处方类兽药，需持兽医处方购买，商家无资质销售处方药涉嫌违规，且部分成分属于管制药物。',
    reporter: '用户 9**1', time: '2026-07-05 11:20', sla: '',
    status: 'DONE', statusLabel: '已完成', statusClass: 'done',
    suggestion: '已核实为处方药违规销售，商品已下架并处罚商家。'
  },
  {
    id: 5, productName: '宠物智能 GPS 定位项圈 防丢防水', type: '虚假宣传', typeClass: 'fake-ad',
    reason: '宣传"全球精准定位"实际定位误差超过500米，且防水等级虚假标注，正常淋水即损坏。',
    reporter: '用户 2**7', time: '2026-07-08 08:05', sla: '剩余 22h 55m',
    status: 'PENDING', statusLabel: '待处理', statusClass: 'pending',
    suggestion: '对定位与防水功能进行实测，虚假宣传应整改详情页并处罚。'
  }
])

// SLA 汇总统计
const slaStats = computed(() => {
  const pending = reports.value.filter(r => r.status === 'PENDING')
  const urgent = pending.filter(r => r.sla && r.sla.includes('2h')).length
  const emergency = pending.filter(r => r.type === '侵权投诉').length
  return {
    urgent: Math.max(urgent, 1),
    warning: pending.length,
    emergency: Math.max(emergency, 1)
  }
})

// 按类型筛选举报
const filteredReports = computed(() => {
  if (activeReportTab.value === 'all') return reports.value
  return reports.value.filter(r => r.typeClass === activeReportTab.value)
})

// 举报详情弹窗
const detailVisible = ref(false)
const handleVisible = ref(false)
const currentReport = ref(null)
const handleResult = ref('')
const handleComment = ref('')

function viewProduct(item) {
  ElMessage.info('查看商品：' + item.productName)
}

function viewReportDetail(item) {
  currentReport.value = item
  detailVisible.value = true
}

function handleReport(item) {
  currentReport.value = item
  handleResult.value = ''
  handleComment.value = ''
  handleVisible.value = true
}

function confirmHandle() {
  if (!handleResult.value) {
    ElMessage.warning('请选择处理结果')
    return
  }
  const item = currentReport.value
  item.status = 'DONE'
  item.statusLabel = '已完成'
  item.statusClass = 'done'
  item.sla = ''
  item.reason += '（已处理：' + handleResult.value + (handleComment.value ? '，' + handleComment.value : '') + '）'
  handleVisible.value = false
  detailVisible.value = false
  ElMessage.success('举报已处理完成')
}

onMounted(() => { loadData() })
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.mono-cell {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--text-500);
}
.num-cell {
  font-weight: 600;
  color: var(--text-800);
  font-variant-numeric: tabular-nums;
}
.profit-up { color: var(--state-success); font-weight: 600; }
.profit-down { color: var(--state-error); font-weight: 600; }

/* ===== 举报管理样式 ===== */
.report-tabs {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
.filter-tab {
  padding: 6px 18px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--card);
  color: var(--text-500);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
}
.filter-tab:hover {
  border-color: var(--primary);
  color: var(--primary);
}
.filter-tab.active {
  background: var(--primary);
  border-color: var(--primary);
  color: #fff;
}

/* SLA 汇总 */
.sla-summary {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}
.sla-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 16px 20px;
  box-shadow: var(--shadow-xs);
  display: flex;
  align-items: center;
  gap: 14px;
}
.sla-number {
  font-size: 26px;
  font-weight: 700;
  min-width: 40px;
}
.sla-number.urgent { color: var(--state-error); }
.sla-number.warning { color: var(--state-warning); }
.sla-number.info { color: var(--brand-500); }
.sla-label {
  font-size: 13px;
  color: var(--text-500);
}

/* 举报列表 */
.report-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.report-item {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 16px 20px;
  box-shadow: var(--shadow-xs);
}
.report-product {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}
.report-product-thumb {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  background: var(--background-200);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-400);
  font-size: 22px;
}
.report-product-info {
  flex: 1;
  min-width: 0;
}
.report-product-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-800);
  margin-bottom: 4px;
}
.report-product-link {
  font-size: 12px;
  color: var(--primary);
  cursor: pointer;
}
.report-tag {
  padding: 4px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}
.report-tag.fake-ad { background: #fff4e5; color: #e67e22; }
.report-tag.quality { background: var(--brand-50); color: var(--brand-600); }
.report-tag.infringement { background: var(--state-error-surface); color: var(--state-error); }
.report-tag.banned { background: var(--background-200); color: var(--text-600); }

.report-reason {
  font-size: 13px;
  color: var(--text-600);
  line-height: 1.6;
  margin: 0 0 10px;
}

.report-meta {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 12px;
  color: var(--text-400);
  margin-bottom: 10px;
}
.report-reporter {
  color: var(--text-500);
}
.report-sla-countdown {
  color: var(--state-warning);
  font-weight: 500;
}

.report-status-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 10px;
  border-top: 1px solid var(--border);
}
.report-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}
.report-status.pending { background: #fff4e5; color: #e67e22; }
.report-status.processing { background: var(--brand-50); color: var(--brand-600); }
.report-status.done { background: var(--state-success-surface); color: var(--state-success); }

.report-actions {
  display: flex;
  gap: 8px;
}
.btn-detail, .btn-handle {
  padding: 6px 14px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
}
.btn-detail {
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--text-600);
}
.btn-detail:hover {
  border-color: var(--primary);
  color: var(--primary);
}
.btn-handle {
  border: 1px solid var(--primary);
  background: var(--primary);
  color: #fff;
}
.btn-handle:hover {
  filter: brightness(0.92);
}

/* 举报详情弹窗 */
.report-detail .detail-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid var(--border);
  font-size: 13px;
}
.report-detail .detail-row span {
  width: 80px;
  flex-shrink: 0;
  color: var(--text-400);
}
.report-detail .detail-row b {
  color: var(--text-700);
  font-weight: 500;
}
.report-detail .detail-row.reason p {
  margin: 0;
  color: var(--text-600);
  line-height: 1.6;
}
</style>
