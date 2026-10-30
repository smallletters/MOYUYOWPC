<template>
  <div class="page-wrapper">
    <div class="page-title-area">
      <h1>意见反馈</h1>
      <p>查看 App 内用户提交的建议、缺陷与投诉，跟进处理并回复</p>
    </div>

    <!-- KPI：来自 /api/admin/feedback/stats -->
    <div class="kpi-grid">
      <div class="kpi-card">
        <div class="kpi-card-header">
          <span class="kpi-card-icon">📥</span>
          <span class="kpi-card-label">反馈总数</span>
        </div>
        <div class="kpi-card-value">{{ stats.total }}</div>
        <div class="kpi-card-trend"><span class="kpi-trend-text">累计提交</span></div>
      </div>
      <div class="kpi-card">
        <div class="kpi-card-header">
          <span class="kpi-card-icon">🔔</span>
          <span class="kpi-card-label">待处理</span>
        </div>
        <div class="kpi-card-value">{{ stats.pending }}</div>
        <div class="kpi-card-trend"><span class="kpi-trend-down">需尽快响应</span></div>
      </div>
      <div class="kpi-card">
        <div class="kpi-card-header">
          <span class="kpi-card-icon">⏳</span>
          <span class="kpi-card-label">处理中</span>
        </div>
        <div class="kpi-card-value">{{ stats.processing }}</div>
        <div class="kpi-card-trend"><span class="kpi-trend-text">进行中</span></div>
      </div>
      <div class="kpi-card">
        <div class="kpi-card-header">
          <span class="kpi-card-icon">✅</span>
          <span class="kpi-card-label">已解决</span>
        </div>
        <div class="kpi-card-value">{{ stats.resolved }}</div>
        <div class="kpi-card-trend"><span class="kpi-trend-up">已回复用户</span></div>
      </div>
    </div>

    <!-- 查询面板 -->
    <div class="query-panel">
      <div class="form-row">
        <div class="form-group">
          <label>关键词</label>
          <input
            v-model="filters.keyword"
            type="text"
            placeholder="反馈内容 / 联系方式 / 用户ID"
            @keyup.enter="handleSearch"
          />
        </div>
        <div class="form-group">
          <label>反馈类型</label>
          <select v-model="filters.type" class="select-wrapper">
            <option value="">全部类型</option>
            <option v-for="t in typeOptions" :key="t.value" :value="t.value">{{ t.label }}</option>
          </select>
        </div>
        <div class="form-group">
          <label>状态</label>
          <select v-model="filters.status" class="select-wrapper">
            <option value="">全部状态</option>
            <option v-for="s in statusOptions" :key="s.value" :value="s.value">{{ s.label }}</option>
          </select>
        </div>
        <div class="form-actions">
          <button class="btn btn-primary" @click="handleSearch">搜索</button>
          <button class="btn btn-outline" @click="handleReset">重置</button>
        </div>
      </div>
    </div>

    <!-- 列表 -->
    <div class="action-bar">
      <span class="list-title">反馈列表</span>
      <span class="list-hint">共 {{ total }} 条</span>
    </div>

    <div class="data-table-wrapper">
      <table class="data-table">
        <thead>
          <tr>
            <th style="width: 190px">用户</th>
            <th style="width: 110px">类型</th>
            <th>反馈内容</th>
            <th style="width: 160px">联系方式</th>
            <th style="width: 100px">状态</th>
            <th style="width: 170px">提交时间</th>
            <th style="width: 140px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in list" :key="row.id">
            <td>
              <div class="user-cell">
                <div class="user-name">{{ row.nickname || ('用户 ' + row.userId) }}</div>
                <div class="user-sub">{{ row.email || row.userId }}</div>
              </div>
            </td>
            <td>
              <span class="tag" :class="typeTagClass(row.type)">{{ typeLabel(row.type) }}</span>
            </td>
            <td>
              <div class="content-cell" :title="row.content">{{ row.content }}</div>
            </td>
            <td class="mono-cell">{{ row.contact || '—' }}</td>
            <td>
              <span class="tag" :class="statusTagClass(row.status)">{{ statusLabel(row.status) }}</span>
            </td>
            <td class="time-cell">{{ formatTime(row.createTime) }}</td>
            <td>
              <div class="cell-actions">
                <button class="btn btn-sm btn-primary" @click="openDetail(row)">查看/处理</button>
              </div>
            </td>
          </tr>
          <tr v-if="!loading && list.length === 0">
            <td colspan="7">
              <div class="empty-state">
                <div class="empty-state-icon">📭</div>
                <div class="empty-state-text">暂无反馈记录</div>
              </div>
            </td>
          </tr>
        </tbody>
      </table>

      <div class="pagination" v-if="total > 0">
        <span class="pagination-info">共 {{ total }} 条 · 第 {{ page }} / {{ totalPages }} 页</span>
        <div class="pagination-btns">
          <button class="pagination-btn" :disabled="page <= 1" @click="changePage(page - 1)">‹ 上一页</button>
          <button class="pagination-btn" :disabled="page >= totalPages" @click="changePage(page + 1)">
            下一页 ›
          </button>
        </div>
      </div>
    </div>

    <!-- 详情 / 处理弹窗 -->
    <el-dialog v-model="detailVisible" title="反馈详情" width="720px" @closed="resetDetail">
      <div v-if="detail" class="detail-body" v-loading="detailLoading">
        <div class="detail-row">
          <span class="detail-label">用户</span>
          <span class="detail-value">
            {{ detail.nickname || ('用户 ' + detail.userId) }}
            <span class="detail-muted" v-if="detail.email">（{{ detail.email }}）</span>
          </span>
        </div>
        <div class="detail-row">
          <span class="detail-label">类型</span>
          <span class="detail-value">
            <span class="tag" :class="typeTagClass(detail.type)">{{ typeLabel(detail.type) }}</span>
          </span>
        </div>
        <div class="detail-row">
          <span class="detail-label">联系方式</span>
          <span class="detail-value">{{ detail.contact || '未填写' }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">提交时间</span>
          <span class="detail-value">{{ formatTime(detail.createTime) }}</span>
        </div>
        <div class="detail-row detail-row--block">
          <span class="detail-label">反馈内容</span>
          <div class="detail-content">{{ detail.content }}</div>
        </div>
        <div class="detail-row detail-row--block" v-if="detail.images && detail.images.length">
          <span class="detail-label">截图</span>
          <div class="shot-list">
            <el-image
              v-for="(img, i) in detail.images"
              :key="i"
              :src="img"
              :preview-src-list="detail.images"
              :initial-index="i"
              fit="cover"
              class="shot"
              preview-teleported
            />
          </div>
        </div>

        <el-divider />

        <div class="detail-row">
          <span class="detail-label">处理状态</span>
          <el-select v-model="handleForm.status" style="width: 200px">
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </div>
        <div class="detail-row detail-row--block">
          <span class="detail-label">回复内容</span>
          <el-input
            v-model="handleForm.replyContent"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="填写给用户的回复（保存后记录回复时间，状态默认置为已解决）"
          />
        </div>
        <div class="detail-hint" v-if="detail.replyTime">
          上次回复时间：{{ formatTime(detail.replyTime) }}
        </div>
      </div>

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button type="primary" :loading="saving" @click="saveHandle">保存处理结果</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getFeedbackList, getFeedbackDetail, handleFeedback, getFeedbackStats } from '../api/admin'

const typeOptions = [
  { value: 'SUGGESTION', label: '产品建议' },
  { value: 'BUG', label: '功能异常' },
  { value: 'COMPLAINT', label: '投诉' },
  { value: 'OTHER', label: '其他' }
]

const statusOptions = [
  { value: 'PENDING', label: '待处理' },
  { value: 'PROCESSING', label: '处理中' },
  { value: 'RESOLVED', label: '已解决' },
  { value: 'CLOSED', label: '已关闭' }
]

const filters = reactive({ keyword: '', type: '', status: '' })
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(15)
const loading = ref(false)
const stats = reactive({ total: 0, pending: 0, processing: 0, resolved: 0 })

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const handleForm = reactive({ status: 'PENDING', replyContent: '' })
const saving = ref(false)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

function typeLabel(v) {
  return typeOptions.find((t) => t.value === v)?.label || v || '—'
}

function statusLabel(v) {
  return statusOptions.find((s) => s.value === v)?.label || v || '—'
}

function typeTagClass(v) {
  if (v === 'BUG') return 'tag-red'
  if (v === 'COMPLAINT') return 'tag-warning'
  if (v === 'SUGGESTION') return 'tag-blue'
  return 'tag-gray'
}

function statusTagClass(v) {
  if (v === 'PENDING') return 'tag-red'
  if (v === 'PROCESSING') return 'tag-warning'
  if (v === 'RESOLVED') return 'tag-green'
  return 'tag-gray'
}

/** LocalDateTime 后端序列化为 "2026-10-19T15:02:03"，表格里显示成更习惯的空格分隔 */
function formatTime(v) {
  if (!v) return '—'
  return String(v).replace('T', ' ').replace(/\.\d+$/, '').slice(0, 19)
}

async function loadList() {
  loading.value = true
  try {
    const res = await getFeedbackList({
      page: page.value,
      size: pageSize.value,
      keyword: filters.keyword || undefined,
      type: filters.type || undefined,
      status: filters.status || undefined
    })
    list.value = res?.list || []
    total.value = Number(res?.total || 0)
  } catch (e) {
    console.error('[feedback] load failed', e)
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  try {
    const res = await getFeedbackStats()
    stats.total = Number(res?.total || 0)
    stats.pending = Number(res?.pending || 0)
    stats.processing = Number(res?.processing || 0)
    stats.resolved = Number(res?.resolved || 0)
  } catch (e) {
    console.error('[feedback] stats failed', e)
  }
}

function handleSearch() {
  page.value = 1
  loadList()
}

function handleReset() {
  filters.keyword = ''
  filters.type = ''
  filters.status = ''
  page.value = 1
  loadList()
}

function changePage(p) {
  if (p < 1 || p > totalPages.value) return
  page.value = p
  loadList()
}

async function openDetail(row) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = row
  handleForm.status = row.status || 'PENDING'
  handleForm.replyContent = ''
  try {
    const res = await getFeedbackDetail(row.id)
    detail.value = res
    handleForm.status = res?.status || 'PENDING'
    handleForm.replyContent = res?.replyContent || ''
  } catch (e) {
    console.error('[feedback] detail failed', e)
  } finally {
    detailLoading.value = false
  }
}

function resetDetail() {
  detail.value = null
  handleForm.status = 'PENDING'
  handleForm.replyContent = ''
}

async function saveHandle() {
  if (!detail.value) return
  if (!handleForm.replyContent.trim() && handleForm.status === detail.value.status) {
    ElMessage.warning('请填写回复内容或修改状态')
    return
  }
  saving.value = true
  try {
    await handleFeedback(detail.value.id, {
      status: handleForm.status,
      replyContent: handleForm.replyContent.trim() || null
    })
    ElMessage.success('已保存')
    detailVisible.value = false
    await Promise.all([loadList(), loadStats()])
  } catch (e) {
    console.error('[feedback] handle failed', e)
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadList()
  loadStats()
})
</script>

<style scoped>
.list-hint {
  margin-left: 12px;
  font-size: 13px;
  color: var(--text-tertiary, #8a8578);
}

.user-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.user-name {
  font-weight: 500;
}

.user-sub {
  font-size: 12px;
  color: var(--text-tertiary, #8a8578);
}

.content-cell {
  max-width: 420px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.detail-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.detail-row--block {
  flex-direction: column;
  gap: 6px;
}

.detail-label {
  width: 88px;
  flex-shrink: 0;
  color: var(--text-tertiary, #8a8578);
  font-size: 13px;
}

.detail-value {
  color: var(--text-primary, #2c2c2a);
  font-size: 14px;
}

.detail-muted {
  color: var(--text-tertiary, #8a8578);
}

.detail-content {
  width: 100%;
  padding: 12px 14px;
  border-radius: 8px;
  background: var(--surface-subtle, #f7f5f1);
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.shot-list {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.shot {
  width: 88px;
  height: 88px;
  border-radius: 8px;
  cursor: zoom-in;
}

.detail-hint {
  font-size: 12px;
  color: var(--text-tertiary, #8a8578);
}
</style>
