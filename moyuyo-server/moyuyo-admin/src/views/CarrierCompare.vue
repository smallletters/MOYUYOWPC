<template>
  <div class="page-wrapper">
    <!-- 页面标题 -->
    <div class="page-header">
      <div>
        <h2>承运商对比与优选</h2>
        <p class="page-desc">多承运商时效 / 价格 / 妥投率评分对比，智能推荐最优物流方案</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd">新建</el-button>
      </div>
    </div>

    <!-- ====== 承运商对比表（可勾选 2-3 家高亮对比，最优值高亮） ====== -->
    <el-card shadow="never" class="block-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">承运商对比</span>
          <span class="card-subtitle">勾选 2-3 家高亮对比 · 绿色为各指标最优值 · 示例数据</span>
        </div>
      </template>
      <el-table
        ref="compareTableRef"
        :data="compareData"
        border
        :row-class-name="compareRowClass"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="46" />
        <el-table-column label="承运商" min-width="150">
          <template #default="{ row }">
            <div class="carrier-cell">
              <div class="carrier-avatar">{{ row.name.charAt(0) }}</div>
              <div>
                <div class="carrier-name">{{ row.name }}</div>
                <div class="carrier-mode">{{ row.transportMode }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="平均时效(天)" width="110" align="center">
          <template #default="{ row }">
            <span class="cell-value" :class="{ 'cell-best': row.avgDays === bestValues.avgDays }">{{ row.avgDays }}</span>
          </template>
        </el-table-column>
        <el-table-column label="时效评分" width="96" align="center">
          <template #default="{ row }">
            <span class="score-text" :class="scoreClass(row.timeScore)">{{ row.timeScore }}</span>
          </template>
        </el-table-column>
        <el-table-column label="首重价格(元)" width="110" align="center">
          <template #default="{ row }">
            <span class="cell-value" :class="{ 'cell-best': row.firstWeightPrice === bestValues.firstWeightPrice }">￥{{ row.firstWeightPrice }}</span>
          </template>
        </el-table-column>
        <el-table-column label="价格评分" width="96" align="center">
          <template #default="{ row }">
            <span class="score-text" :class="scoreClass(row.priceScore)">{{ row.priceScore }}</span>
          </template>
        </el-table-column>
        <el-table-column label="妥投率" width="100" align="center">
          <template #default="{ row }">
            <span class="cell-value" :class="{ 'cell-best': row.deliverRate === bestValues.deliverRate }">{{ row.deliverRate }}%</span>
          </template>
        </el-table-column>
        <el-table-column label="破损率" width="100" align="center">
          <template #default="{ row }">
            <span class="cell-value" :class="{ 'cell-best': row.damageRate === bestValues.damageRate }">{{ row.damageRate }}%</span>
          </template>
        </el-table-column>
        <el-table-column label="综合评分" width="120" align="center">
          <template #default="{ row }">
            <span class="total-score" :class="scoreClass(row.totalScore)">{{ row.totalScore }}</span>
            <span v-if="row.totalScore === bestCarrier.totalScore" class="tag tag-green best-tag">最优</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="compare-tip">
        <span class="tip-dot"></span>
        已勾选 {{ selectedRows.length }} 家（最多 {{ MAX_COMPARE }} 家），勾选后该行高亮便于对比
      </div>
    </el-card>

    <!-- ====== 承运商绩效统计（KPI + 近30天趋势柱状图 + 各承运商水平条形对比） ====== -->
    <el-card shadow="never" class="block-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">承运商绩效统计</span>
          <span class="card-subtitle">近 30 天趋势 · 示例数据</span>
        </div>
      </template>

      <!-- KPI 概览 -->
      <div class="kpi-grid">
        <div class="kpi-card">
          <div class="kpi-card-header">
            <span class="kpi-card-label">承运商数量</span>
          </div>
          <div class="kpi-card-value">{{ perfKpi.carrierCount }}<span class="kpi-unit"> 家</span></div>
          <div class="kpi-card-trend">
            <span class="kpi-trend-text">覆盖 {{ perfKpi.transportTypes }} 种运输方式</span>
          </div>
        </div>
        <div class="kpi-card">
          <div class="kpi-card-header">
            <span class="kpi-card-label">平均时效</span>
          </div>
          <div class="kpi-card-value">{{ perfKpi.avgDays }}<span class="kpi-unit"> 天</span></div>
          <div class="kpi-card-trend">
            <span class="kpi-trend-down">最快 {{ bestValues.avgDays }} 天</span>
          </div>
        </div>
        <div class="kpi-card">
          <div class="kpi-card-header">
            <span class="kpi-card-label">平均妥投率</span>
          </div>
          <div class="kpi-card-value">{{ perfKpi.avgDeliver }}<span class="kpi-unit"> %</span></div>
          <div class="kpi-card-trend">
            <span class="kpi-trend-up">最高 {{ bestValues.deliverRate }}%</span>
          </div>
        </div>
        <div class="kpi-card">
          <div class="kpi-card-header">
            <span class="kpi-card-label">最优综合评分</span>
          </div>
          <div class="kpi-card-value" style="color: var(--brand-500)">{{ perfKpi.bestScore }}<span class="kpi-unit"> 分</span></div>
          <div class="kpi-card-trend">
            <span class="kpi-trend-text">{{ bestCarrier.name }}</span>
          </div>
        </div>
      </div>

      <!-- 近30天妥投率 / 平均时效 CSS 柱状图 -->
      <el-row :gutter="16">
        <el-col :span="12">
          <div class="chart-box">
            <div class="chart-title">近 30 天妥投率趋势（%）</div>
            <div class="trend-chart">
              <div v-for="(v, i) in DELIVER_TREND" :key="i" class="trend-col">
                <div
                  class="trend-bar"
                  :class="{ 'trend-bar-best': v >= 98 }"
                  :style="{ height: barHeight(v, deliverRange.min, deliverRange.max) }"
                  :title="'第 ' + (i + 1) + ' 天: ' + v + '%'"
                ></div>
              </div>
            </div>
            <div class="chart-axis">
              <span>1天</span>
              <span>15天</span>
              <span>30天</span>
            </div>
          </div>
        </el-col>
        <el-col :span="12">
          <div class="chart-box">
            <div class="chart-title">近 30 天平均时效趋势（天）</div>
            <div class="trend-chart">
              <div v-for="(v, i) in DAYS_TREND" :key="i" class="trend-col">
                <div
                  class="trend-bar trend-bar-warn"
                  :class="{ 'trend-bar-good': v <= 4.2 }"
                  :style="{ height: barHeight(v, daysRange.min, daysRange.max) }"
                  :title="'第 ' + (i + 1) + ' 天: ' + v + ' 天'"
                ></div>
              </div>
            </div>
            <div class="chart-axis">
              <span>1天</span>
              <span>15天</span>
              <span>30天</span>
            </div>
          </div>
        </el-col>
      </el-row>

      <!-- 各承运商妥投率水平条形对比 -->
      <div class="hbar-section">
        <div class="chart-title">各承运商妥投率对比（%）</div>
        <div class="hbar-list">
          <div v-for="c in compareData" :key="c.id" class="hbar-item">
            <span class="hbar-name">{{ c.name }}</span>
            <div class="hbar-track">
              <div
                class="hbar-fill"
                :class="{ 'hbar-best': c.deliverRate === bestValues.deliverRate }"
                :style="{ width: hbarWidth(c.deliverRate, bestValues.deliverRate) }"
              ></div>
            </div>
            <span class="hbar-value" :class="{ 'hbar-value-best': c.deliverRate === bestValues.deliverRate }">{{ c.deliverRate }}%</span>
          </div>
        </div>
      </div>
    </el-card>

    <!-- ====== 优选建议（综合评分最高承运商 + 推荐理由） ====== -->
    <el-card shadow="never" class="block-card recommend-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">优选建议</span>
          <span class="card-subtitle">基于综合评分自动推荐</span>
        </div>
      </template>
      <div class="recommend-body">
        <div class="recommend-badge">
          <div class="recommend-avatar">{{ bestCarrier.name.charAt(0) }}</div>
          <span class="tag tag-green">综合评分最高</span>
        </div>
        <div class="recommend-info">
          <div class="recommend-name">
            {{ bestCarrier.name }}
            <span class="tag tag-blue">推荐</span>
          </div>
          <div class="recommend-meta">
            {{ bestCarrier.transportMode }} · 平均时效 {{ bestCarrier.avgDays }} 天 · 首重 ￥{{ bestCarrier.firstWeightPrice }} · 妥投率 {{ bestCarrier.deliverRate }}% · 综合评分 {{ bestCarrier.totalScore }}
          </div>
          <div class="recommend-reason-title">推荐理由</div>
          <ul class="recommend-reasons">
            <li v-for="(r, i) in recommendationReasons" :key="i">{{ r }}</li>
          </ul>
        </div>
      </div>
    </el-card>

    <!-- ====== 承运商管理（基础信息维护，保留原有 CRUD 功能） ====== -->
    <el-card shadow="never" class="filter-card">
      <el-form :model="filters" inline>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" placeholder="请输入承运商名称" clearable />
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
        <el-table-column prop="name" label="承运商名称" width="140" />
        <el-table-column prop="transportMode" label="运输方式" width="120" />
        <el-table-column prop="avgDeliveryDays" label="平均时效(天)" width="120" />
        <el-table-column prop="firstWeightPrice" label="首重价格" width="100">
          <template #default="{ row }">￥{{ row.firstWeightPrice }}</template>
        </el-table-column>
        <el-table-column prop="renewWeightPrice" label="续重价格" width="100">
          <template #default="{ row }">￥{{ row.renewWeightPrice }}</template>
        </el-table-column>
        <el-table-column prop="praiseRate" label="好评率" width="100">
          <template #default="{ row }">{{ row.praiseRate }}%</template>
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
        <el-form-item label="承运商名称">
          <el-input v-model="editForm.name" placeholder="请输入承运商名称" />
        </el-form-item>
        <el-form-item label="运输方式">
          <el-select v-model="editForm.transportMode">
            <el-option label="快递" value="快递" />
            <el-option label="海运" value="海运" />
            <el-option label="空运" value="空运" />
            <el-option label="陆运" value="陆运" />
          </el-select>
        </el-form-item>
        <el-form-item label="平均时效(天)">
          <el-input-number v-model="editForm.avgDeliveryDays" :min="1" :max="60" />
        </el-form-item>
        <el-form-item label="首重价格(元)">
          <el-input-number v-model="editForm.firstWeightPrice" :min="0" :step="0.5" />
        </el-form-item>
        <el-form-item label="续重价格(元)">
          <el-input-number v-model="editForm.renewWeightPrice" :min="0" :step="0.5" />
        </el-form-item>
        <el-form-item label="好评率(%)">
          <el-input-number v-model="editForm.praiseRate" :min="0" :max="100" :step="0.1" />
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
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCarriers, createCarrier, updateCarrier, deleteCarrier } from '../api/admin'

// ============================================================
// 示例数据：对比优选 / 绩效统计区块暂无独立接口，
// 以下为结构化示例数据（含时效、价格、妥投率、破损率、综合评分），
// 接入真实 API 后替换 COMPARE_SAMPLE 数据源即可。
// ============================================================
const COMPARE_SAMPLE = [
  { id: 1, name: '顺丰速运', transportMode: '空运', avgDays: 2.5, timeScore: 4.8, firstWeightPrice: 23, priceScore: 3.2, deliverRate: 99.1, damageRate: 0.1, totalScore: 4.6 },
  { id: 2, name: '京东物流', transportMode: '陆运', avgDays: 3, timeScore: 4.5, firstWeightPrice: 12, priceScore: 4.0, deliverRate: 98.6, damageRate: 0.2, totalScore: 4.4 },
  { id: 3, name: '中通快递', transportMode: '陆运', avgDays: 3.8, timeScore: 4.0, firstWeightPrice: 8, priceScore: 4.6, deliverRate: 97.4, damageRate: 0.3, totalScore: 4.1 },
  { id: 4, name: '圆通速递', transportMode: '陆运', avgDays: 4.5, timeScore: 3.5, firstWeightPrice: 7, priceScore: 4.8, deliverRate: 96.8, damageRate: 0.4, totalScore: 3.9 },
  { id: 5, name: '韵达快递', transportMode: '陆运', avgDays: 4.8, timeScore: 3.2, firstWeightPrice: 7, priceScore: 4.8, deliverRate: 96.2, damageRate: 0.5, totalScore: 3.7 },
  { id: 6, name: 'EMS 国际', transportMode: '空运', avgDays: 5.2, timeScore: 3.0, firstWeightPrice: 35, priceScore: 2.5, deliverRate: 95.1, damageRate: 0.6, totalScore: 3.3 }
]

// 示例数据：近 30 天妥投率（%），固定波动模拟
const DELIVER_TREND = Array.from({ length: 30 }, (_, i) => +(98.1 - Math.sin(i / 3.2) * 0.6 - (i % 5) * 0.12).toFixed(1))
// 示例数据：近 30 天平均时效（天）
const DAYS_TREND = Array.from({ length: 30 }, (_, i) => +(4.4 + Math.sin(i / 4) * 0.45 + (i % 6) * 0.06).toFixed(1))

// ============================================================
// 承运商对比与优选（示例数据驱动）
// ============================================================
const MAX_COMPARE = 3
const compareData = ref([...COMPARE_SAMPLE])
const compareTableRef = ref(null)
const selectedRows = ref([])

// 各指标最优值（时效/价格越低越好，妥投率越高越好，破损率越低越好）
const bestValues = computed(() => {
  const list = compareData.value
  return {
    avgDays: Math.min(...list.map(i => i.avgDays)),
    firstWeightPrice: Math.min(...list.map(i => i.firstWeightPrice)),
    deliverRate: Math.max(...list.map(i => i.deliverRate)),
    damageRate: Math.min(...list.map(i => i.damageRate))
  }
})

// 综合评分最高的承运商（优选建议对象）
const bestCarrier = computed(() => compareData.value.reduce((a, b) => (b.totalScore > a.totalScore ? b : a)))

// 根据最优承运商动态生成推荐理由
const recommendationReasons = computed(() => {
  const carrier = bestCarrier.value
  const best = bestValues.value
  const reasons = [`综合评分 ${carrier.totalScore} 分，在全部承运商中排名第一`]
  if (carrier.deliverRate === best.deliverRate) reasons.push(`妥投率 ${carrier.deliverRate}% 领先所有承运商，履约最稳定`)
  if (carrier.damageRate === best.damageRate) reasons.push(`破损率仅 ${carrier.damageRate}%，货物损耗风险最低`)
  if (carrier.avgDays === best.avgDays) reasons.push(`平均时效 ${carrier.avgDays} 天，配送速度最快`)
  if (reasons.length < 3) reasons.push('时效达成率与综合评分表现均衡，适合作为默认发货渠道')
  return reasons
})

// KPI 指标汇总
const perfKpi = computed(() => {
  const list = compareData.value
  const avgDays = list.reduce((s, i) => s + i.avgDays, 0) / list.length
  const avgDeliver = list.reduce((s, i) => s + i.deliverRate, 0) / list.length
  return {
    carrierCount: list.length,
    transportTypes: new Set(list.map(i => i.transportMode)).size,
    avgDays: avgDays.toFixed(1),
    avgDeliver: avgDeliver.toFixed(1),
    bestScore: bestCarrier.value.totalScore
  }
})

// 趋势柱状图数值范围
const deliverRange = {
  min: Math.min(...DELIVER_TREND),
  max: Math.max(...DELIVER_TREND)
}
const daysRange = {
  min: Math.min(...DAYS_TREND),
  max: Math.max(...DAYS_TREND)
}

// 勾选处理：最多勾选 3 家，超出部分自动取消
function handleSelectionChange(rows) {
  if (rows.length > MAX_COMPARE) {
    ElMessage.warning(`最多勾选 ${MAX_COMPARE} 家承运商进行高亮对比`)
    // 取消超出部分的勾选（保留前 3 家）
    rows.slice(MAX_COMPARE).forEach(row => compareTableRef.value.toggleRowSelection(row, false))
    return
  }
  selectedRows.value = rows
}

// 勾选行高亮样式（作用于 el-table 行）
function compareRowClass({ row }) {
  return selectedRows.value.includes(row) ? 'compare-row-highlight' : ''
}

// 评分着色：>=4.5 优（绿），>=3.5 中（蓝），其余弱（灰）
function scoreClass(score) {
  if (score >= 4.5) return 'score-high'
  if (score >= 3.5) return 'score-mid'
  return 'score-low'
}

// 柱状图高度映射（范围 6% - 100%）
function barHeight(value, min, max) {
  const pct = ((value - min) / (max - min)) * 100
  return Math.max(6, Math.min(100, pct)) + '%'
}

// 水平条形宽度映射（相对最大值，最小 4% 便于展示）
function hbarWidth(value, max) {
  return Math.max(4, (value / max) * 100) + '%'
}

// ============================================================
// 承运商管理（原有 CRUD 功能，保留）
// ============================================================
const filters = reactive({ keyword: '' })
const tableData = ref([])
const currentPage = ref(1)
const pageSize = ref(15)
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const editForm = reactive({
  name: '',
  transportMode: '快递',
  avgDeliveryDays: 3,
  firstWeightPrice: 0,
  renewWeightPrice: 0,
  praiseRate: 95
})

// 加载承运商数据
async function loadData() {
  try {
    const res = await getCarriers()
    const list = res || []
    let filtered = [...list]
    if (filters.keyword) {
      filtered = filtered.filter(item => item.name.includes(filters.keyword))
    }
    tableData.value = filtered
    total.value = filtered.length
  } catch (error) {
    console.error('获取承运商数据失败:', error)
    ElMessage.error('获取承运商数据失败')
  }
}
function handleSearch() { currentPage.value = 1; loadData() }
function handleReset() { filters.keyword = ''; handleSearch() }
function handleAdd() { dialogTitle.value = '新建承运商'; editForm.name = ''; editForm.transportMode = '快递'; editForm.avgDeliveryDays = 3; editForm.firstWeightPrice = 0; editForm.renewWeightPrice = 0; editForm.praiseRate = 95; dialogVisible.value = true }
function handleEdit(row) { dialogTitle.value = '编辑承运商'; Object.assign(editForm, row); dialogVisible.value = true }
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确定删除？', '提示')
    await deleteCarrier(row.id)
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
      await updateCarrier(editForm.id, {
        name: editForm.name,
        transportMode: editForm.transportMode,
        avgDeliveryDays: editForm.avgDeliveryDays,
        firstWeightPrice: editForm.firstWeightPrice,
        renewWeightPrice: editForm.renewWeightPrice,
        praiseRate: editForm.praiseRate
      })
    } else {
      await createCarrier({
        name: editForm.name,
        transportMode: editForm.transportMode,
        avgDeliveryDays: editForm.avgDeliveryDays,
        firstWeightPrice: editForm.firstWeightPrice,
        renewWeightPrice: editForm.renewWeightPrice,
        praiseRate: editForm.praiseRate
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
  // 默认勾选综合评分最高的 2 家，直观展示高亮对比效果
  nextTick(() => {
    ;[...compareData.value]
      .sort((a, b) => b.totalScore - a.totalScore)
      .slice(0, 2)
      .forEach(row => compareTableRef.value.toggleRowSelection(row, true))
  })
})
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.page-desc { font-size: 13px; color: var(--text-400); margin: 6px 0 0; }
.filter-card { margin-bottom: 16px; }
.header-actions { display: flex; gap: 8px; }

/* ===== 区块卡片 ===== */
.block-card { margin-bottom: 16px; }
.card-header { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.card-title { font-size: 15px; font-weight: 600; color: var(--text-800); }
.card-subtitle { font-size: 12px; color: var(--text-400); }

/* ===== 对比表承运商单元格 ===== */
.carrier-cell { display: flex; align-items: center; gap: 10px; }
.carrier-avatar { width: 32px; height: 32px; border-radius: 8px; background: var(--brand-50); color: var(--brand-600); display: flex; align-items: center; justify-content: center; font-size: 14px; font-weight: 700; flex-shrink: 0; }
.carrier-name { font-size: 13px; font-weight: 600; color: var(--text-800); }
.carrier-mode { font-size: 11px; color: var(--text-400); }

/* 最优值高亮 */
.cell-value { font-size: 13px; color: var(--text-700); font-variant-numeric: tabular-nums; }
.cell-best { color: var(--state-success); font-weight: 700; background: var(--state-success-surface); border-radius: 6px; padding: 2px 8px; }

/* 评分着色 */
.score-text { font-size: 13px; font-weight: 600; font-variant-numeric: tabular-nums; }
.score-high { color: var(--state-success); }
.score-mid { color: var(--brand-500); }
.score-low { color: var(--text-400); }
.total-score { font-size: 14px; font-weight: 700; font-variant-numeric: tabular-nums; }
.best-tag { margin-left: 6px; }

/* 勾选行高亮（作用于 el-table 行） */
:deep(.compare-row-highlight) { background: var(--brand-50) !important; }
:deep(.compare-row-highlight td:first-child) { box-shadow: inset 3px 0 0 var(--brand-500); }

/* 勾选提示条 */
.compare-tip { display: flex; align-items: center; gap: 6px; margin-top: 12px; font-size: 12px; color: var(--text-400); }
.tip-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--brand-500); }

/* ===== KPI（复用全局 .kpi-grid / .kpi-card 设计令牌） ===== */
.kpi-row { margin-bottom: 4px; }

/* ===== 近30天趋势柱状图（纯 CSS） ===== */
.chart-box { padding: 4px; }
.chart-title { font-size: 13px; font-weight: 600; color: var(--text-600); margin-bottom: 10px; }
.trend-chart { display: flex; align-items: flex-end; gap: 2px; height: 140px; padding: 4px 2px 0; }
.trend-col { flex: 1; display: flex; flex-direction: column; justify-content: flex-end; height: 100%; }
.trend-bar { width: 100%; border-radius: 2px 2px 0 0; background: linear-gradient(180deg, var(--brand-300), var(--brand-600)); transition: height 0.3s ease; }
.trend-bar-best { background: linear-gradient(180deg, var(--state-success), #1f9e43); }
.trend-bar-warn { background: linear-gradient(180deg, #ffd08a, var(--state-warning)); }
.trend-bar-good { background: linear-gradient(180deg, var(--state-success), #1f9e43); }
.chart-axis { display: flex; justify-content: space-between; font-size: 10px; color: var(--text-400); margin-top: 6px; }

/* ===== 各承运商水平条形对比 ===== */
.hbar-section { margin-top: 20px; padding: 4px; }
.hbar-list { display: flex; flex-direction: column; gap: 10px; }
.hbar-item { display: flex; align-items: center; gap: 12px; }
.hbar-name { width: 90px; font-size: 13px; font-weight: 600; color: var(--text-700); flex-shrink: 0; }
.hbar-track { flex: 1; height: 10px; background: var(--background-200); border-radius: 999px; overflow: hidden; }
.hbar-fill { height: 100%; border-radius: 999px; background: linear-gradient(90deg, var(--brand-400), var(--brand-500)); transition: width 0.6s ease; }
.hbar-best { background: linear-gradient(90deg, var(--state-success), #1f9e43); }
.hbar-value { width: 60px; text-align: right; font-size: 12px; font-weight: 600; color: var(--text-500); font-variant-numeric: tabular-nums; }
.hbar-value-best { color: var(--state-success); }

/* ===== 优选建议卡片 ===== */
.recommend-card { border-color: var(--brand-300); }
.recommend-body { display: flex; align-items: flex-start; gap: 20px; }
.recommend-badge { display: flex; flex-direction: column; align-items: center; gap: 8px; flex-shrink: 0; }
.recommend-avatar { width: 56px; height: 56px; border-radius: 14px; background: linear-gradient(135deg, var(--brand-400), var(--brand-600)); color: #fff; display: flex; align-items: center; justify-content: center; font-size: 24px; font-weight: 700; box-shadow: var(--shadow-md); }
.recommend-info { flex: 1; min-width: 0; }
.recommend-name { font-size: 18px; font-weight: 700; color: var(--text-800); display: flex; align-items: center; gap: 8px; }
.recommend-meta { font-size: 12px; color: var(--text-400); margin: 6px 0 10px; }
.recommend-reason-title { font-size: 12px; font-weight: 600; color: var(--text-500); margin-bottom: 6px; }
.recommend-reasons { list-style: none; margin: 0; padding: 0; }
.recommend-reasons li { position: relative; padding-left: 16px; font-size: 13px; color: var(--text-600); line-height: 1.9; }
.recommend-reasons li::before { content: ''; position: absolute; left: 2px; top: 11px; width: 6px; height: 6px; border-radius: 50%; background: var(--brand-500); }
</style>
