<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>评价管理</h2>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd">回复评价</el-button>
      </div>
    </div>
    <!-- ====== 今日审核统计 ====== -->
    <section class="today-stats">
      <div class="stats-title">
        <el-icon :size="18" color="var(--primary)"><DataAnalysis /></el-icon>
        <h3>今日审核统计</h3>
      </div>
      <div class="stats-grid">
        <div v-for="item in todayStatCards" :key="item.label" class="stat-card">
          <div class="stat-card-header">
            <span class="stat-card-label">{{ item.label }}</span>
            <span class="stat-card-icon" :class="item.iconClass">
              <el-icon :size="14"><component :is="item.icon" /></el-icon>
            </span>
          </div>
          <div class="stat-card-value" :class="item.valueClass">{{ item.value }}</div>
          <div class="stat-card-sub">
            <span :class="item.trendClass">{{ item.trend }}</span>
          </div>
        </div>
      </div>
    </section>
    <el-card shadow="never" class="filter-card">
      <el-form :model="filters" inline>
        <el-form-item label="商品名称">
          <el-input v-model="filters.keyword" placeholder="请输入商品名称" clearable />
        </el-form-item>
        <el-form-item label="评分">
          <el-select v-model="filters.rating" placeholder="全部" clearable style="width:120px">
            <el-option label="全部" value="" />
            <el-option label="5星" value="5" />
            <el-option label="4星" value="4" />
            <el-option label="3星" value="3" />
            <el-option label="2星" value="2" />
            <el-option label="1星" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width:140px">
            <!--
              value 与后端 ReviewStatusEnum 枚举值对齐(PENDING/APPROVED/REJECTED),
              label 仅展示文案。这样 row.status === filters.status 才能正确过滤。
            -->
            <el-option label="全部" value="" />
            <el-option label="已审核" value="APPROVED" />
            <el-option label="待审核" value="PENDING" />
            <el-option label="已驳回" value="REJECTED" />
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
        <el-table-column prop="productName" label="商品名称" min-width="160" />
        <el-table-column prop="userName" label="用户" width="120" />
        <el-table-column prop="rating" label="评分" width="100">
          <template #default="{ row }">
            <span :style="{ color: '#f59e0b' }">{{ '★'.repeat(row.rating) + '☆'.repeat(5 - row.rating) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="内容摘要" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.content ? (row.content.length > 30 ? row.content.slice(0, 30) + '...' : row.content) : '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="图片" width="120">
          <template #default="{ row }">
            <div v-if="row.images && row.images.length" class="thumb-list">
              <el-image
                v-for="(img, idx) in row.images.slice(0, 3)"
                :key="idx"
                :src="img"
                :preview-src-list="row.images"
                :initial-index="idx"
                fit="cover"
                class="thumb-image"
              >
                <template #error>
                  <div class="image-error">!</div>
                </template>
              </el-image>
              <span v-if="row.images.length > 3" class="thumb-more">+{{ row.images.length - 3 }}</span>
            </div>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <!-- row.status 是后端枚举值(PENDING/APPROVED/REJECTED),展示成中文 -->
            <el-tag :type="row.status === 'APPROVED' ? 'success' : (row.status === 'REJECTED' ? 'danger' : 'warning')" size="small">{{ reviewStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reviewTime" label="评价时间" width="170" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleEdit(row)">审核</el-button>
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
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="700px">
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="商品名称">
          <el-input v-model="editForm.productName" disabled />
        </el-form-item>
        <el-form-item label="用户">
          <el-input v-model="editForm.userName" disabled />
        </el-form-item>
        <el-form-item label="评分">
          <el-rate v-model="editForm.rating" disabled />
        </el-form-item>
        <el-form-item label="评价内容">
          <el-input v-model="editForm.content" type="textarea" :rows="3" disabled />
        </el-form-item>
        <el-form-item v-if="editForm.tags && editForm.tags.length" label="评价标签">
          <el-tag v-for="tag in editForm.tags" :key="tag" size="small" style="margin-right:6px">{{ tag }}</el-tag>
        </el-form-item>
        <el-form-item v-if="editForm.images && editForm.images.length" label="晒图">
          <div class="evidence-grid">
            <el-image
              v-for="(img, idx) in editForm.images"
              :key="idx"
              :src="img"
              :preview-src-list="editForm.images"
              :initial-index="idx"
              fit="cover"
              class="evidence-image"
            >
              <template #error>
                <div class="image-error">加载失败</div>
              </template>
            </el-image>
          </div>
        </el-form-item>
        <el-form-item label="审核状态">
          <el-select v-model="editForm.status" style="width:100%">
            <!-- value 与后端 ReviewStatusEnum 对齐,label 仅展示 -->
            <el-option label="待审核" value="PENDING" />
            <el-option label="已审核" value="APPROVED" />
            <el-option label="已驳回" value="REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item label="回复内容">
          <el-input v-model="editForm.reply" type="textarea" :rows="3" placeholder="请输入回复内容" />
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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Clock, CircleCheck, CircleClose, TrendCharts, DataAnalysis } from '@element-plus/icons-vue'
import { getReviewList, approveReview, replyReview, rejectReview, getReviewTodayStats } from '../api/admin'
import { toArray } from '../utils/safeArray'

// 今日审核统计：默认占位（接口失败 / 首次加载时使用），成功后再用真实数据替换
const todayStatCards = ref([
  { label: '今日待审核', value: 0, icon: Clock, iconClass: 'icon-pending', valueClass: 'value-pending', trend: '较昨日 +0', trendClass: 'trend-up' },
  { label: '今日已通过', value: 0, icon: CircleCheck, iconClass: 'icon-approved', valueClass: 'value-approved', trend: '较昨日 +0', trendClass: 'trend-up' },
  { label: '今日已驳回', value: 0, icon: CircleClose, iconClass: 'icon-rejected', valueClass: 'value-rejected', trend: '较昨日 +0', trendClass: 'trend-down' },
  { label: '今日通过率', value: '0%', icon: TrendCharts, iconClass: 'icon-passrate', valueClass: 'value-passrate', trend: '较昨日 +0%', trendClass: 'trend-up' }
])

// 数值差值转趋势文案：正值加"+"，0保持"+0"，负值保留负号
function buildTrend(current, yesterday) {
  const diff = Number(current) - Number(yesterday || 0)
  if (diff > 0) return { text: `较昨日 +${diff}`, up: true }
  if (diff < 0) return { text: `较昨日 ${diff}`, up: false }
  return { text: '较昨日 +0', up: true }
}

// 加载今日审核统计
async function loadTodayStats() {
  try {
    const res = await getReviewTodayStats()
    const data = res?.data || res || {}
    const pending = Number(data.pending) || 0
    const approved = Number(data.approved) || 0
    const rejected = Number(data.rejected) || 0
    const passRate = Number(data.passRate) || 0
    const yesterdayPending = Number(data.yesterdayPending) || 0
    const yesterdayApproved = Number(data.yesterdayApproved) || 0
    const yesterdayRejected = Number(data.yesterdayRejected) || 0
    const yesterdayPassRate = Number(data.yesterdayPassRate) || 0

    const tPending = buildTrend(pending, yesterdayPending)
    const tApproved = buildTrend(approved, yesterdayApproved)
    // 已驳回减少是好事：显示"+差"但 trend 类仍按"好"的语义着色（绿）
    const tRejected = buildTrend(rejected, yesterdayRejected)
    const passRateDiff = passRate - yesterdayPassRate
    const tPassRate = passRateDiff > 0
      ? { text: `较昨日 +${passRateDiff.toFixed(1)}%`, up: true }
      : passRateDiff < 0
        ? { text: `较昨日 ${passRateDiff.toFixed(1)}%`, up: false }
        : { text: '较昨日 +0%', up: true }

    todayStatCards.value = [
      { label: '今日待审核', value: pending, icon: Clock, iconClass: 'icon-pending', valueClass: 'value-pending', trend: tPending.text, trendClass: tPending.up ? 'trend-up' : 'trend-down' },
      { label: '今日已通过', value: approved, icon: CircleCheck, iconClass: 'icon-approved', valueClass: 'value-approved', trend: tApproved.text, trendClass: tApproved.up ? 'trend-up' : 'trend-down' },
      // 已驳回"减少"为好：颜色反向（差↓→绿；差↑→红）
      { label: '今日已驳回', value: rejected, icon: CircleClose, iconClass: 'icon-rejected', valueClass: 'value-rejected', trend: tRejected.text, trendClass: tRejected.up ? 'trend-down' : 'trend-up' },
      { label: '今日通过率', value: `${passRate.toFixed(1)}%`, icon: TrendCharts, iconClass: 'icon-passrate', valueClass: 'value-passrate', trend: tPassRate.text, trendClass: tPassRate.up ? 'trend-up' : 'trend-down' }
    ]
  } catch (e) {
    // 失败时保留默认占位数据，不影响页面其他功能
    ElMessage.error('获取今日审核统计失败')
  }
}

const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)

const filters = reactive({
  keyword: '',
  rating: '',
  status: ''
})

const editForm = reactive({
  id: null,
  productName: '',
  userName: '',
  rating: 5,
  content: '',
  tags: [],
  images: [],
  // 与后端 ReviewStatusEnum 对齐,而不是中文文案
  status: 'PENDING',
  reply: '',
  reviewTime: ''
})

const tableData = ref([])

// 加载评价列表
async function loadData() {
  try {
    // 后端返回 {list, total, page, size} 结构,优先用服务端分页(支持 status 过滤),
    // 客户端再做 keyword / rating 二次筛选;客户端筛选不影响 total。
    const res = await getReviewList({
      page: currentPage.value,
      size: pageSize.value,
      // 后端按 ReviewStatusEnum 枚举值(PENDING/APPROVED/REJECTED)精确过滤。
      // 前端 status 文案("待审核"/"已审核")与枚举不对齐,这里不传给后端,
      // 全部由前端 filters.status 过滤。
    })
    const records = toArray(res)
    // total 必须用后端返回的真实总数,而不是当前页条数
    total.value = Number(res?.total) || records.length
    // 客户端筛选
    let list = [...records]
    const kw = filters.keyword.toLowerCase()
    if (kw) {
      list = list.filter(d => (d.productName || '').toLowerCase().includes(kw))
    }
    if (filters.rating) {
      list = list.filter(d => d.rating === Number(filters.rating))
    }
    if (filters.status) {
      list = list.filter(d => d.status === filters.status)
    }
    // 客户端筛选会导致当前页条数减少,但 total 仍应是后端的真实总数,
    // 分页器才能正确显示"共 N 条"
    tableData.value = list
  } catch (e) {
    ElMessage.error('获取评价列表失败')
  }
}

function handleSearch() { currentPage.value = 1; loadData() }

// 评价状态枚举 → 中文文案(后端 ReviewStatusEnum)
function reviewStatusText(status) {
  const map = { PENDING: '待审核', APPROVED: '已审核', REJECTED: '已驳回' }
  return map[status] || status
}

function handleReset() { filters.keyword = ''; filters.rating = ''; filters.status = ''; handleSearch() }

function handleAdd() {
  isEdit.value = false
  dialogTitle.value = '回复评价'
  editForm.id = null
  editForm.productName = ''
  editForm.userName = ''
  editForm.rating = 5
  editForm.content = ''
  editForm.tags = []
  editForm.images = []
  editForm.status = 'APPROVED'
  editForm.reply = ''
  editForm.reviewTime = ''
  dialogVisible.value = true
}

function handleEdit(row) {
  isEdit.value = true
  dialogTitle.value = '审核评价'
  Object.assign(editForm, row)
  dialogVisible.value = true
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确认删除该评价吗？', '提示', { type: 'warning' })
    await rejectReview(row.id)
    ElMessage.success('删除成功')
    // 列表与统计互不依赖，并行刷新缩短操作反馈延迟
    await Promise.all([loadData(), loadTodayStats()])
  } catch (e) {
    // 用户取消不处理
  }
}

async function handleSave() {
  try {
    if (isEdit.value) {
      // 审核评价：通过或驳回。editForm.status 已经是后端枚举值
      if (editForm.status === 'APPROVED') {
        await approveReview(editForm.id)
      } else if (editForm.status === 'REJECTED') {
        await rejectReview(editForm.id)
      }
      // PENDING 仅作为展示状态,不调任何审核接口
      // 有回复内容则保存回复
      if (editForm.reply) {
        await replyReview(editForm.id, { content: editForm.reply })
      }
      ElMessage.success('审核完成')
    } else {
      // 回复评价（选择一条已有评价进行回复）
      ElMessage.success('回复成功')
    }
    dialogVisible.value = false
    // 列表与统计互不依赖，并行刷新缩短操作反馈延迟
    await Promise.all([loadData(), loadTodayStats()])
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

// 首屏同时拉取列表和今日统计，互不依赖可并行缩短首屏延迟
onMounted(() => { Promise.all([loadData(), loadTodayStats()]) })
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.filter-card { margin-bottom: 16px; }
.header-actions { display: flex; gap: 8px; }

/* 今日审核统计 */
.today-stats { margin-bottom: 20px; }
.stats-title { display: flex; align-items: center; gap: 8px; margin-bottom: 16px; }
.stats-title h3 { font-size: 16px; font-weight: 700; color: var(--text-800); margin: 0; }
.stats-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
.stat-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 18px 20px;
  box-shadow: var(--shadow-xs);
  transition: border-color 0.2s ease, transform 0.2s ease;
}
.stat-card:hover { border-color: var(--primary); transform: translateY(-1px); }
.stat-card-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.stat-card-label { font-size: 12px; font-weight: 500; color: var(--text-400); }
.stat-card-icon {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.icon-pending { background: var(--brand-50); color: var(--brand-500); }
.icon-approved { background: var(--state-success-surface); color: var(--state-success); }
.icon-rejected { background: var(--state-error-surface); color: var(--state-error); }
.icon-passrate { background: var(--state-success-surface); color: var(--state-success); }
.stat-card-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-800);
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}
.value-pending { color: var(--brand-500); }
.value-approved { color: var(--state-success); }
.value-rejected { color: var(--state-error); }
.value-passrate { color: var(--state-success); }
.stat-card-sub { font-size: 11px; color: var(--text-400); margin-top: 4px; }
.stat-card-sub .trend-up { color: var(--state-success); }
.stat-card-sub .trend-down { color: var(--state-error); }

/* 评价图片：列表缩略图 + 详情举证 */
.thumb-list { display: flex; align-items: center; gap: 6px; }
.thumb-image { width: 40px; height: 40px; border-radius: 4px; border: 1px solid var(--border); cursor: pointer; }
/* 强制内部 <img> 撑满,避免 fit cover 时只显示一角 */
.thumb-image :deep(.el-image__inner) { width: 100%; height: 100%; }
.thumb-more { font-size: 12px; color: var(--text-400); }
.evidence-grid { display: flex; flex-wrap: wrap; gap: 8px; }
.evidence-image { width: 100px; height: 100px; border-radius: 4px; border: 1px solid var(--border); }
.evidence-image :deep(.el-image__inner) { width: 100%; height: 100%; }
.image-error { display: flex; align-items: center; justify-content: center; width: 100%; height: 100%; font-size: 12px; color: var(--text-400); background: var(--bg-50); }
.text-muted { color: var(--text-400); }
</style>
