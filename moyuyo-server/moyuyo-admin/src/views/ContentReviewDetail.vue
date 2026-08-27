<template>
  <div class="page-wrapper">
    <!-- 页面标题 -->
    <div class="page-title-area">
      <h1>内容审核详情</h1>
      <p>查看待审核内容的完整信息、原内容预览与机审评分</p>
    </div>

    <!-- 加载失败 -->
    <div v-if="loadError" class="error-banner">
      <div class="error-banner-inner">
        <span class="error-banner-icon">⚠️</span>
        <div>
          <strong class="error-banner-title">内容加载失败：</strong>
          <span class="error-banner-msg">{{ loadError }}</span>
        </div>
      </div>
      <div class="error-banner-tip">
        可能原因：内容已被删除 / ID 无效 / 权限不足。
        <button class="btn btn-outline btn-sm" style="margin-left:12px" @click="handleBack">← 返回审核列表</button>
      </div>
    </div>

    <!-- 加载中 -->
    <div v-else-if="loading && !hasData" class="loading-state">
      <div class="loading-spinner"></div>
      <div>正在加载内容详情...</div>
    </div>

    <!-- 详情内容 -->
    <div v-else-if="hasData">
      <!-- 顶部横幅：内容类型 + 状态 + 返回按钮 -->
      <div class="detail-banner">
        <div class="detail-banner-left">
          <div class="detail-icon">{{ contentTypeIcon }}</div>
          <div class="detail-info">
            <div class="detail-meta-row">
              <el-tag :type="statusTagType(detail.status)" size="default" effect="dark">
                {{ statusLabel(detail.status) }}
              </el-tag>
              <span class="content-type-tag">{{ contentTypeLabel(detail.contentType) }}</span>
              <span v-if="detail.autoFlag === 1" class="auto-flag">⚠️ 机审标记</span>
            </div>
            <h2 class="detail-title">内容 ID: {{ detail.contentId }}</h2>
            <div class="detail-subtitle">审核记录 ID: {{ detail.id }} · 创建于 {{ formatTime(detail.createTime) }}</div>
          </div>
        </div>
        <div class="detail-banner-right">
          <button class="btn btn-outline" @click="handleBack">← 返回</button>
        </div>
      </div>

      <!-- KPI 4 列 -->
      <div class="kpi-grid">
        <div class="kpi-card">
          <div class="kpi-card-header">
            <span class="kpi-card-icon">🆔</span>
            <span class="kpi-card-label">内容 ID</span>
          </div>
          <div class="kpi-card-value">#{{ detail.contentId }}</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-card-header">
            <span class="kpi-card-icon">👤</span>
            <span class="kpi-card-label">发布用户</span>
          </div>
          <div class="kpi-card-value">#{{ detail.userId }}</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-card-header">
            <span class="kpi-card-icon">🤖</span>
            <span class="kpi-card-label">机审评分</span>
          </div>
          <div class="kpi-card-value">
            {{ detail.autoScore != null ? detail.autoScore : '-' }}
          </div>
          <div class="kpi-card-trend" v-if="detail.autoFlag === 1">
            <span class="kpi-trend-text">机审标记异常</span>
          </div>
          <div class="kpi-card-trend" v-else>
            <span class="kpi-trend-text">机审标记正常</span>
          </div>
        </div>
        <div class="kpi-card">
          <div class="kpi-card-header">
            <span class="kpi-card-icon">📝</span>
            <span class="kpi-card-label">违规类型</span>
          </div>
          <div class="kpi-card-value" style="font-size:16px">
            {{ detail.reason || '暂无标记' }}
          </div>
        </div>
      </div>

      <!-- 内容预览卡片：完整内容 + 多图九宫格 -->
      <div class="detail-panel">
        <div class="detail-panel-header">
          <h3 class="detail-panel-title">内容预览</h3>
          <span class="content-stats" v-if="detail.imageList.length">
            共 {{ detail.imageList.length }} 张图片
          </span>
        </div>
        <div class="detail-panel-body">
          <!-- 多图预览：1张大图 / 2列 / 3列 -->
          <div
            v-if="detail.imageList.length"
            class="detail-image-grid"
            :class="getDetailImageGridClass(detail.imageList.length)"
          >
            <div
              v-for="(url, idx) in detail.imageList"
              :key="idx"
              class="detail-image-cell"
              @click="openImagePreview(url)"
            >
              <img :src="url" class="detail-image-img" @error="onDetailImageError(idx)" />
            </div>
          </div>

          <!-- 完整内容：优先用后端 content，缺失则用 excerpt -->
          <div class="content-preview">
            {{ detail.content || detail.contentExcerpt || '（无内容）' }}
          </div>
        </div>
      </div>

      <!-- 图片大图预览弹层 -->
      <el-dialog
        v-model="previewVisible"
        :show-close="true"
        width="90%"
        center
        @close="previewVisible = false"
      >
        <img :src="previewUrl" class="preview-fullscreen" />
      </el-dialog>

      <!-- 审核操作卡片 -->
      <div class="detail-panel">
        <div class="detail-panel-header">
          <h3 class="detail-panel-title">审核操作</h3>
        </div>
        <div class="detail-panel-body">
          <div class="review-actions">
            <button class="btn btn-primary btn-lg" :disabled="busy" @click="handleAction('approve')">
              ✓ 通过
            </button>
            <button class="btn btn-outline btn-lg" :disabled="busy" @click="handleAction('hide')">
              👁 隐藏
            </button>
            <button class="btn btn-outline btn-lg" :disabled="busy" @click="handleAction('delete')">
              🗑 删除
            </button>
            <button class="btn btn-danger btn-lg" :disabled="busy" @click="handleAction('ban')">
              🚫 封禁
            </button>
          </div>
          <div class="review-tip">
            操作执行后会自动刷新当前审核状态。
          </div>
        </div>
      </div>

      <!-- 审核记录 -->
      <div v-if="detail.reviewTime || detail.reviewerId" class="detail-panel">
        <div class="detail-panel-header">
          <h3 class="detail-panel-title">审核记录</h3>
        </div>
        <div class="detail-panel-body">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="审核人">#{{ detail.reviewerId || '—' }}</el-descriptions-item>
            <el-descriptions-item label="审核时间">{{ formatTime(detail.reviewTime) }}</el-descriptions-item>
            <el-descriptions-item v-if="detail.reviewComment" label="审核意见" :span="2">
              {{ detail.reviewComment }}
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </div>
    </div>

    <!-- 未加载到任何数据 -->
    <div v-else class="empty-state">
      <div class="empty-state-icon">📭</div>
      <div class="empty-state-text">内容 ID: {{ contentId }}</div>
      <button class="btn btn-outline" style="margin-top:12px" @click="handleBack">返回列表</button>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getContentReviewDetail,
  approveContentReview,
  hideContentReview,
  deleteContentReview,
  banContentReview
} from '../api/admin'

const route = useRoute()

// 内容详情数据
const detail = reactive({
  id: '',
  contentType: '',
  contentId: '',
  userId: '',
  contentExcerpt: '',
  content: '',   // 后端返回的完整内容（contentType=POST 时来自原帖）
  images: '',    // 后端 images 原始字段（String 或 Array）
  imageList: [], // 解析后的图片 URL 列表（用于多图预览）
  reason: '',
  status: '',
  reviewerId: '',
  reviewComment: '',
  reviewTime: '',
  autoFlag: 0,
  autoScore: null,
  createTime: ''
})

// 图片大图预览弹层
const previewVisible = ref(false)
const previewUrl = ref('')

/** 打开图片大图预览 */
function openImagePreview(url) {
  previewUrl.value = url
  previewVisible.value = true
}

/** 单张图加载失败：从 imageList 中移除 */
function onDetailImageError(idx) {
  if (Array.isArray(detail.imageList)) {
    detail.imageList.splice(idx, 1)
  }
}

/**
 * 多图网格列数（与列表页/APP 端统一）
 *  - 1 张：大图（单列）
 *  - 2-3 张：2 列
 *  - 4+ 张：3 列
 */
function getDetailImageGridClass(count) {
  if (count <= 1) return 'grid-single'
  if (count <= 3) return 'grid-col-2'
  return 'grid-col-3'
}

const loading = ref(false)
const busy = ref(false) // 任意操作进行中（防重复点击）
const loadError = ref('')

const contentId = computed(() => route.query.id || route.params.id || '')
const hasData = computed(() => !!detail.id)

const contentTypeIcon = computed(() => {
  const map = { POST: '📝', COMMENT: '💬', IMAGE: '🖼', VIDEO: '🎬' }
  return map[detail.contentType] || '📄'
})

// 状态 -> el-tag type
function statusTagType(status) {
  const map = {
    PENDING: 'warning',
    APPROVED: 'success',
    REJECTED: 'danger',
    HIDDEN: 'info',
    BANNED: 'danger',
    DELETED: 'info'
  }
  return map[status] || 'info'
}

// 状态 -> 中文标签
function statusLabel(status) {
  const map = {
    PENDING: '待审核',
    APPROVED: '已通过',
    REJECTED: '已驳回',
    HIDDEN: '已隐藏',
    BANNED: '已封禁',
    DELETED: '已删除'
  }
  return map[status] || status || '未知'
}

// 内容类型 -> 中文
function contentTypeLabel(type) {
  const map = { POST: '社区帖子', COMMENT: '评论', IMAGE: '图片', VIDEO: '视频' }
  return map[type] || type || '其他'
}

// 时间格式化
function formatTime(value) {
  if (!value) return '—'
  return String(value).replace('T', ' ').replace(/\..*$/, '') || '—'
}

// 加载内容详情
async function loadDetail() {
  const id = contentId.value
  if (!id) {
    loadError.value = '缺少内容 ID'
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    const res = await getContentReviewDetail(id)
    if (res && res.id) {
      // 解析后端 images 字段：可能是 String(JSON) 或 Array
      let imgList = []
      const rawImages = res.images ?? ''
      if (Array.isArray(rawImages)) {
        imgList = rawImages
      } else if (typeof rawImages === 'string' && rawImages.trim()) {
        try {
          const parsed = JSON.parse(rawImages)
          if (Array.isArray(parsed)) imgList = parsed
        } catch (e) { /* 忽略解析错误，留空数组 */ }
      }
      // 仅保留合法 URL（http(s):// 与 /uploads/ 相对路径）
      imgList = imgList
        .map(u => (u && (u.startsWith('http://') || u.startsWith('https://') || u.startsWith('/uploads/')) ? u : ''))
        .filter(Boolean)

      Object.assign(detail, {
        id: res.id || '',
        contentType: res.contentType || '',
        contentId: res.contentId || '',
        userId: res.userId || '',
        contentExcerpt: res.contentExcerpt || '',
        content: res.content || '',
        images: rawImages,
        imageList: imgList,
        reason: res.reason || '',
        status: res.status || '',
        reviewerId: res.reviewerId || '',
        reviewComment: res.reviewComment || '',
        reviewTime: res.reviewTime || '',
        autoFlag: res.autoFlag ?? 0,
        autoScore: res.autoScore ?? null,
        createTime: res.createTime || ''
      })
    } else {
      loadError.value = `内容「${id}」不存在或已被删除`
    }
  } catch (err) {
    console.error('加载内容详情失败:', err)
    const serverMsg = err?.response?.data?.message || err?.message || '未知错误'
    loadError.value = `${serverMsg}（ID: ${id}）`
  } finally {
    loading.value = false
  }
}

// 单个审核操作（通过 / 隐藏 / 删除 / 封禁）
async function handleAction(action) {
  if (busy.value) return
  const labels = {
    approve: { title: '通过审核', text: '确认通过此内容？' },
    hide: { title: '隐藏内容', text: '确认隐藏此内容？（内容对普通用户不可见，但管理员可见）' },
    delete: { title: '删除内容', text: '确认删除此内容？此操作不可恢复！', danger: true },
    ban: { title: '封禁内容', text: '确认封禁此内容？（作者将被一并封禁）', danger: true }
  }
  const cfg = labels[action]
  if (!cfg) return
  try {
    await ElMessageBox.confirm(cfg.text, cfg.title, {
      type: cfg.danger ? 'error' : 'warning',
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      dangerouslyUseHTMLString: false
    })
  } catch {
    return // 用户取消
  }
  busy.value = true
  try {
    if (action === 'approve') await approveContentReview(detail.id)
    else if (action === 'hide') await hideContentReview(detail.id)
    else if (action === 'delete') await deleteContentReview(detail.id)
    else if (action === 'ban') await banContentReview(detail.id, { banType: '其他违规', comment: '详情页快速封禁' })
    ElMessage.success(`${cfg.title}成功`)
    await loadDetail() // 重新加载详情，更新状态
  } catch (err) {
    console.error('审核操作失败:', err)
    const serverMsg = err?.response?.data?.message || err?.message || '未知错误'
    ElMessage.error(`操作失败：${serverMsg}`)
  } finally {
    busy.value = false
  }
}

// 返回内容审核列表（整页跳转，避免 SPA chunk 缓存导致组件未加载）
function handleBack() {
  window.location.href = '/admin/content-review'
}

// 路由 id 变化时重新加载
watch(
  () => route.query.id,
  (newId) => {
    if (newId && newId !== detail.id) loadDetail()
  }
)

onMounted(() => {
  if (contentId.value) loadDetail()
})
</script>

<style scoped>
.page-wrapper { padding: 20px; }

/* 错误横幅 */
.error-banner {
  background: var(--state-error-surface);
  border: 1px solid rgba(255, 59, 48, 0.18);
  border-radius: var(--radius);
  padding: 18px 24px;
  margin-bottom: 20px;
}
.error-banner-inner {
  display: flex;
  align-items: center;
  gap: 10px;
}
.error-banner-icon { font-size: 18px; }
.error-banner-title { color: var(--state-error); margin-right: 6px; }
.error-banner-msg { color: var(--text-700); }
.error-banner-tip {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-400);
}

/* 加载状态 */
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
  color: var(--text-400);
  font-size: 14px;
  gap: 16px;
}
.loading-spinner {
  width: 36px;
  height: 36px;
  border: 3px solid var(--background-200);
  border-top-color: var(--brand-500);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* 顶部横幅 */
.detail-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24px;
  border-radius: var(--radius);
  margin-bottom: 20px;
  border: 1px solid var(--border);
  background: linear-gradient(135deg, #fff4e5 0%, #f0fdf4 100%);
}
.detail-banner-left {
  display: flex;
  align-items: center;
  gap: 16px;
}
.detail-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  background: var(--card);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  box-shadow: var(--shadow-xs);
}
.detail-meta-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}
.content-type-tag {
  font-size: 13px;
  color: var(--text-500);
  padding: 2px 8px;
  background: var(--background-200);
  border-radius: 4px;
}
.auto-flag {
  font-size: 13px;
  color: var(--state-warning);
  padding: 2px 8px;
  background: var(--state-warning-surface);
  border-radius: 4px;
}
.detail-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-800);
  margin: 0 0 4px;
}
.detail-subtitle {
  font-size: 12px;
  color: var(--text-400);
}

/* KPI 4 列 */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}
@media (max-width: 1100px) {
  .kpi-grid { grid-template-columns: repeat(2, 1fr); }
}

/* 详情面板 */
.detail-panel {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-xs);
  overflow: hidden;
  margin-bottom: 20px;
}
.detail-panel-header {
  padding: 14px 18px;
  border-bottom: 1px solid var(--border);
  background: var(--background-50);
}
.detail-panel-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-800);
  margin: 0;
}
.detail-panel-body {
  padding: 18px;
}

/* 内容预览 */
.content-preview {
  font-size: 14px;
  line-height: 1.7;
  color: var(--text-700);
  white-space: pre-wrap;
  word-break: break-word;
  padding: 16px;
  background: var(--background-50);
  border-radius: var(--radius-sm);
  border: 1px solid var(--border);
}

/* 详情页：多图九宫格预览（审核员查看完整内容） */
.detail-image-grid {
  display: grid;
  gap: 10px;
  margin-bottom: 16px;
}
.detail-image-grid.grid-single {
  grid-template-columns: 1fr;
}
.detail-image-grid.grid-single .detail-image-cell {
  height: 320px;
  border-radius: 8px;
}
.detail-image-grid.grid-col-2 {
  grid-template-columns: repeat(2, 1fr);
}
.detail-image-grid.grid-col-3 {
  grid-template-columns: repeat(3, 1fr);
}
.detail-image-grid.grid-col-2 .detail-image-cell,
.detail-image-grid.grid-col-3 .detail-image-cell {
  aspect-ratio: 1 / 1;
  border-radius: 8px;
}
.detail-image-cell {
  position: relative;
  background: var(--background-200);
  overflow: hidden;
  cursor: pointer;
  border: 1px solid var(--border);
}
.detail-image-cell .detail-image-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform 0.2s ease;
}
.detail-image-cell:hover .detail-image-img {
  transform: scale(1.03);
}
.content-stats {
  font-size: 12px;
  color: var(--text-500);
  margin-left: auto;
}
.preview-fullscreen {
  width: 100%;
  height: auto;
  max-height: 80vh;
  object-fit: contain;
  display: block;
}

/* 审核操作按钮组 */
.review-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
.review-tip {
  margin-top: 12px;
  font-size: 12px;
  color: var(--text-400);
}

/* 空态 */
.empty-state {
  text-align: center;
  padding: 60px 20px;
  color: var(--text-400);
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
}
.empty-state-icon { font-size: 48px; margin-bottom: 16px; }
.empty-state-text { font-size: 14px; }
</style>