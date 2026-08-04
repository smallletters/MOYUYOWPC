<template>
  <div class="page-wrapper">
    <!-- 页面标题行 -->
    <div class="page-header">
      <div>
        <h2>内容审核详情</h2>
        <p class="page-desc">
          审核ID: {{ detail.id ?? '-' }}
          <template v-if="detail.createTime"> | 提交时间: {{ detail.createTime }}</template>
        </p>
      </div>
      <div class="header-actions">
        <el-button @click="goBack">返回列表</el-button>
      </div>
    </div>

    <!-- 加载中 / 无数据 -->
    <el-empty v-if="!loading && !detail.id" description="未找到该审核内容" :image-size="80">
      <el-button type="primary" @click="goBack">返回列表</el-button>
    </el-empty>

    <template v-if="detail.id">
      <!-- 左右两栏布局 -->
      <div class="detail-layout">
        <!-- 左栏 - 内容预览 -->
        <div class="content-panel">
          <el-card shadow="never">
            <!-- 发布者信息行 -->
            <div class="publisher-row">
              <div class="user-avatar">{{ publisherInitial }}</div>
              <div class="publisher-meta">
                <div class="publisher-name">
                  {{ publisherName }}
                  <el-tag size="small" type="primary" effect="plain">{{ contentTypeLabel }}</el-tag>
                </div>
                <div class="publisher-sub">
                  UID: {{ detail.userId ?? '-' }}
                </div>
              </div>
              <div class="publish-time">{{ detail.createTime }}</div>
            </div>

            <!-- 内容标题 -->
            <h3 class="content-title">{{ detail.contentExcerpt || '（无内容摘要）' }}</h3>

            <!-- 内容类型标识 -->
            <div class="content-meta">
              <el-tag v-if="detail.contentId" size="small">内容ID: {{ detail.contentId }}</el-tag>
              <el-tag v-if="detail.autoFlag" size="small" type="warning" effect="plain">机审标记</el-tag>
            </div>

            <!-- 图片预览区 -->
            <div v-if="imageList.length" class="image-grid">
              <div v-for="(img, idx) in imageList" :key="idx" class="preview-img">
                <img v-if="isHttpUrl(img)" :src="img" alt="内容图片" />
                <span v-else>{{ img }}</span>
              </div>
            </div>
            <el-empty v-else description="暂无图片" :image-size="60" />

            <!-- 互动数据（占位，与设计稿一致） -->
            <div class="stat-row">
              <span class="stat-item">❤️ <b>{{ detail.likeCount ?? 0 }}</b> 点赞</span>
              <span class="stat-item">💬 <b>{{ detail.commentCount ?? 0 }}</b> 评论</span>
              <span class="stat-item">🔖 <b>{{ detail.favoriteCount ?? 0 }}</b> 收藏</span>
            </div>
          </el-card>
        </div>

        <!-- 右栏 - 审核操作面板 -->
        <div class="action-panel">
          <!-- 审核状态卡片 -->
          <el-card shadow="never">
            <div class="panel-title">审核信息</div>
            <div class="status-line">
              <span class="status-label">当前状态</span>
              <el-tag :type="statusTagType" size="large">{{ statusLabel }}</el-tag>
            </div>
            <div class="info-line" v-if="detail.reviewTime">
              <span>审核时间</span><b>{{ detail.reviewTime }}</b>
            </div>
            <div class="info-line" v-if="detail.reviewComment">
              <span>审核备注</span><b>{{ detail.reviewComment }}</b>
            </div>
            <div class="info-line" v-if="detail.reason">
              <span>驳回原因</span><b>{{ detail.reason }}</b>
            </div>
            <div class="info-line" v-if="detail.autoScore">
              <span>AI 预审评分</span><b>{{ detail.autoScore }} / 100</b>
            </div>
          </el-card>

          <!-- 审核操作按钮组 -->
          <el-card shadow="never">
            <div class="panel-title">审核操作</div>
            <div class="action-btns">
              <el-button type="success" size="large" :disabled="!canOperate" @click="handleApprove">通过</el-button>
              <el-button type="warning" size="large" :disabled="!canOperate" @click="handleReject">驳回</el-button>
              <el-button size="large" :disabled="!canOperate" @click="handleHide">隐藏</el-button>
              <el-button type="danger" size="large" @click="handleDelete">删除</el-button>
              <el-button type="danger" plain size="large" :disabled="!canOperate" @click="handleBan">封禁</el-button>
            </div>
          </el-card>

          <!-- 驳回原因表单（仅驳回时展示） -->
          <el-card v-if="showRejectForm" shadow="never" class="reject-card">
            <div class="panel-title" style="color: #e67e22">驳回原因</div>
            <el-form label-position="top">
              <el-form-item label="违规类型" required>
                <el-select v-model="rejectReason" placeholder="请选择违规类型" style="width:100%">
                  <el-option label="广告营销" value="广告营销" />
                  <el-option label="不实信息" value="不实信息" />
                  <el-option label="低质内容" value="低质内容" />
                  <el-option label="侵权内容" value="侵权内容" />
                  <el-option label="其他" value="其他" />
                </el-select>
              </el-form-item>
              <el-form-item label="驳回说明">
                <el-input
                  v-model="rejectComment"
                  type="textarea"
                  :rows="3"
                  placeholder="请输入驳回原因说明..."
                />
              </el-form-item>
              <div class="reject-actions">
                <el-button type="warning" @click="confirmReject">确认驳回</el-button>
                <el-button @click="showRejectForm = false">取消</el-button>
              </div>
            </el-form>
          </el-card>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getContentReviewDetail,
  approveContentReview,
  rejectContentReview,
  hideContentReview,
  deleteContentReview,
  banContentReview
} from '../api/admin'

const route = useRoute()
const router = useRouter()

const detail = ref({})
const loading = ref(true)
const showRejectForm = ref(false)
const rejectReason = ref('')
const rejectComment = ref('')

// 审核状态映射
const STATUS_MAP = {
  PENDING: { label: '待审核', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' },
  HIDDEN: { label: '已隐藏', type: 'info' },
  BANNED: { label: '已封禁', type: 'danger' }
}

const statusLabel = computed(() => STATUS_MAP[detail.value.status]?.label || detail.value.status || '待审核')
const statusTagType = computed(() => STATUS_MAP[detail.value.status]?.type || 'warning')
const canOperate = computed(() => detail.value.status === 'PENDING')
const contentTypeLabel = computed(() => detail.value.contentType || '图文')
const publisherName = computed(() => '用户' + (detail.value.userId ?? ''))
const publisherInitial = computed(() => publisherName.value.slice(-1) || 'U')

// 图片列表：兼容字符串/JSON数组/逗号分隔
const imageList = computed(() => {
  const raw = detail.value.images
  if (!raw) return []
  if (Array.isArray(raw)) return raw
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : [raw]
  } catch (e) {
    return String(raw).split(',').map(s => s.trim()).filter(Boolean)
  }
})

function isHttpUrl(str) {
  return typeof str === 'string' && /^https?:\/\//i.test(str)
}

// 加载审核详情
async function loadDetail() {
  loading.value = true
  try {
    const id = route.params.id
    // 无 id 时不做请求，直接展示空状态
    if (!id) {
      detail.value = {}
      ElMessage.warning('缺少审核记录 ID')
      return
    }
    const res = await getContentReviewDetail(id)
    detail.value = res || {}
    if (!detail.value.id) {
      ElMessage.warning('未找到该审核记录')
    }
  } catch (e) {
    console.error('获取审核详情失败:', e)
    ElMessage.error('获取审核详情失败')
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.push('/reviews')
}

async function handleApprove() {
  try {
    await approveContentReview(detail.value.id)
    ElMessage.success('已通过')
    await loadDetail()
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

function handleReject() {
  showRejectForm.value = true
}

async function confirmReject() {
  if (!rejectReason.value) {
    ElMessage.warning('请选择违规类型')
    return
  }
  try {
    await rejectContentReview(detail.value.id, {
      reason: rejectReason.value,
      comment: rejectComment.value
    })
    ElMessage.warning('已驳回')
    showRejectForm.value = false
    await loadDetail()
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

async function handleHide() {
  try {
    await hideContentReview(detail.value.id)
    ElMessage.success('已隐藏')
    await loadDetail()
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

async function handleDelete() {
  try {
    await ElMessageBox.confirm('确定删除该内容吗？删除后不可恢复。', '删除确认', {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteContentReview(detail.value.id)
    ElMessage.success('已删除')
    goBack()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('操作失败')
  }
}

async function handleBan() {
  try {
    await ElMessageBox.confirm('确定封禁该内容吗？', '封禁确认', {
      confirmButtonText: '确定封禁',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await banContentReview(detail.value.id)
    ElMessage.success('已封禁')
    await loadDetail()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('操作失败')
  }
}

onMounted(loadDetail)
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 20px; }
.page-header h2 { font-size: 22px; font-weight: 700; color: var(--text-800); margin: 0 0 4px; }
.page-desc { font-size: 13px; color: var(--text-400); margin: 0; }
.header-actions { display: flex; gap: 8px; }

/* 左右两栏布局 */
.detail-layout {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}
.content-panel {
  flex: 1;
  min-width: 0;
}
.action-panel {
  width: 380px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 发布者信息 */
.publisher-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.publisher-meta {
  flex: 1;
  min-width: 0;
}
.publisher-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-800);
  display: flex;
  align-items: center;
  gap: 8px;
}
.publisher-sub {
  font-size: 12px;
  color: var(--text-400);
  margin-top: 2px;
}
.publish-time {
  font-size: 12px;
  color: var(--text-400);
  flex-shrink: 0;
}

/* 内容标题 */
.content-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-800);
  margin: 0 0 8px;
}
.content-meta {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

/* 图片预览 */
.image-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}
.preview-img {
  aspect-ratio: 2/3;
  border-radius: 8px;
  overflow: hidden;
  background: linear-gradient(135deg, var(--background-200), var(--background-300));
  border: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  color: var(--text-400);
  padding: 8px;
}
.preview-img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* 互动数据 */
.stat-row {
  display: flex;
  gap: 24px;
  padding-top: 12px;
  border-top: 1px solid var(--border);
  font-size: 13px;
  color: var(--text-500);
}
.stat-item b {
  color: var(--text-700);
}

/* 右侧面板 */
.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-800);
  margin-bottom: 14px;
}
.status-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.status-label {
  font-size: 13px;
  color: var(--text-500);
}
.info-line {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  padding: 8px 0;
  border-bottom: 1px solid var(--border);
  gap: 12px;
}
.info-line span { color: var(--text-400); white-space: nowrap; }
.info-line b { color: var(--text-600); font-weight: 500; text-align: right; word-break: break-all; }

/* 操作按钮 */
.action-btns {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.action-btns .el-button {
  width: 100%;
  margin-left: 0;
}

/* 驳回表单 */
.reject-card { border-color: #e67e22; }
.reject-actions {
  display: flex;
  gap: 10px;
}
.reject-actions .el-button {
  flex: 1;
  margin-left: 0;
}
</style>
