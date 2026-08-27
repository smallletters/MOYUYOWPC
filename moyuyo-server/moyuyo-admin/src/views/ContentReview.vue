<template>
  <div class="content-review">
    <h2 class="page-title">内容审核</h2>

    <!-- 审核模式切换 -->
    <div class="mode-switcher">
      <button
        v-for="mode in reviewModes"
        :key="mode.key"
        class="mode-chip"
        :class="{ active: activeMode === mode.key }"
        @click="switchMode(mode.key)"
      >
        {{ mode.label }}
      </button>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-row">
      <div class="stat-card pending">
        <div class="stat-value">{{ reviewStats.pending }}</div>
        <div class="stat-label">待审核</div>
      </div>
      <div class="stat-card sla">
        <div class="stat-value">{{ reviewStats.slaRemaining }}</div>
        <div class="stat-label">SLA剩余</div>
      </div>
      <div class="stat-card done">
        <div class="stat-value">{{ reviewStats.todayReviewed }}</div>
        <div class="stat-label">今日已审</div>
      </div>
    </div>

    <!-- 违规类型标签 -->
    <div class="tab-switcher">
      <button
        v-for="tab in violationTabs"
        :key="tab.key"
        class="tab-switcher-item"
        :class="{ active: activeTab === tab.key }"
        @click="switchTab(tab.key)"
      >
        {{ tab.label }}
      </button>
      <!-- 灌入测试数据(仅 dev):便于演示违规类型 tab 筛选与封禁弹窗联动 -->
      <button
        class="tab-switcher-item tab-debug"
        :disabled="seeding"
        @click="onSeedTestData"
        title="插入 6 条 reason=各种违规类型的测试数据"
      >
        {{ seeding ? '灌入中...' : '+ 测试数据' }}
      </button>
    </div>

    <!-- 审核卡片列表 -->
    <div class="review-list">
      <div v-if="loading" class="review-status">加载中...</div>
      <div v-else-if="!reviewItems.length" class="review-status">
        <template v-if="activeTab === 'all'">暂无审核内容，点击右上角"+ 测试数据"可插入演示记录</template>
        <template v-else>当前 tab "{{ activeTab }}" 暂无审核记录</template>
      </div>
      <template v-else>
        <div class="review-card" v-for="item in reviewItems" :key="item.id">
          <div class="review-thumb">
            <!-- 优先展示帖子真实图片(后端 images[0]);无图时回退 emoji -->
            <img
              v-if="item.imageUrl"
              :src="item.imageUrl"
              class="review-thumb-img"
              @error="onThumbError(item)"
            />
            <span v-else>{{ item.thumb }}</span>
          </div>
          <div class="review-body">
            <div class="review-top">
              <div class="review-tags">
                <span class="tag" :class="item.contentTypeClass">{{ item.contentType }}</span>
                <!-- 状态标签：已删除用红色突出，其余已处理状态统一灰色 -->
                <span class="tag" :class="item.statusTagClass">{{ item.autoResult }}</span>
              </div>
              <span class="review-time">{{ item.submitTime }}</span>
            </div>
            <!-- 完整帖子内容(后端 content 字段,不再是 500 字摘要) -->
            <div class="review-desc" :title="item.description">{{ item.description }}</div>
            <div class="review-publisher">
              <div class="user-info-cell">
                <!-- 优先展示用户头像(后端 avatar);无头像时用昵称首字符 -->
                <img
                  v-if="item.avatar"
                  :src="item.avatar"
                  class="user-avatar user-avatar-img"
                  @error="onAvatarError(item)"
                />
                <div v-else class="user-avatar">{{ item.publisher.charAt(0) }}</div>
                <span>{{ item.publisher }}</span>
              </div>
            </div>
            <div class="review-actions">
              <!-- DELETED 状态的记录 4 个审核按钮全部禁用，仅保留"详情" -->
              <button class="btn btn-sm btn-primary" :disabled="item.processed" @click="handleReview(item.id, 'approve')">通过</button>
              <button class="btn btn-sm btn-outline" :disabled="item.processed" @click="handleReview(item.id, 'hide')">隐藏</button>
              <button class="btn btn-sm btn-outline" :disabled="item.processed" @click="handleReview(item.id, 'delete')">删除</button>
              <button class="btn btn-sm btn-danger" :disabled="item.processed" @click="handleReview(item.id, 'ban')">封禁</button>
              <button class="btn btn-sm btn-outline" @click="goDetail(item.id)">详情</button>
            </div>
          </div>
        </div>
      </template>
    </div>

    <!-- 分页:自实现 UI(不依赖 el-pagination 渲染,确保 SPA 路由切换时一定可见) -->
    <div class="pagination-bar">
      <span class="pg-total">共 <strong>{{ reviewTotal }}</strong> 条</span>
      <select class="pg-sizes" v-model.number="reviewSize" @change="onPageSizeChange">
        <option :value="10">10 条/页</option>
        <option :value="20">20 条/页</option>
        <option :value="50">50 条/页</option>
        <option :value="100">100 条/页</option>
      </select>
      <button
        class="pg-btn"
        :disabled="reviewPage <= 1"
        @click="goPrevPage"
      >上一页</button>
      <span class="pg-current">第 {{ reviewPage }} / {{ totalPages }} 页</span>
      <button
        class="pg-btn"
        :disabled="reviewPage >= totalPages"
        @click="goNextPage"
      >下一页</button>
      <span class="pg-jump">
        跳至 <input
          class="pg-jump-input"
          type="number"
          v-model.number="jumpPage"
          :min="1"
          :max="totalPages"
          @keyup.enter="onJumpPage"
        /> 页
      </span>
    </div>

    <!-- 封禁弹窗:必选违规类型 + 可选备注 + 确认/取消 -->
    <el-dialog
      v-model="banDialogVisible"
      title="封禁内容"
      width="480px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form :model="banForm" label-width="80px" size="default">
        <el-form-item label="违规类型" required>
          <el-select v-model="banForm.banType" placeholder="请选择违规类型" style="width: 100%">
            <el-option label="色情" value="色情" />
            <el-option label="暴力" value="暴力" />
            <el-option label="仇恨言论" value="仇恨言论" />
            <el-option label="侵权" value="侵权" />
            <el-option label="虚假信息" value="虚假信息" />
            <el-option label="虐待动物" value="虐待动物" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="banForm.comment"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="可填写封禁原因或补充说明"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="banDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="banSubmitting" @click="confirmBan">确认封禁</el-button>
      </template>
    </el-dialog>

    <!-- 审核趋势图 -->
    <div class="card">
      <div class="card-header">
        <h3>近7日审核趋势</h3>
      </div>
      <div class="card-body">
        <div class="trend-chart">
          <div class="bar-group" v-for="(day, idx) in trendData" :key="idx">
            <div class="bar-stack">
              <div class="bar bar-pass" :style="{ height: day.pass + '%' }"></div>
              <div class="bar bar-reject" :style="{ height: day.reject + '%' }"></div>
            </div>
            <div class="bar-label">{{ day.label }}</div>
          </div>
        </div>
        <div class="chart-legend">
          <span class="legend-item"><span class="legend-dot pass"></span>通过</span>
          <span class="legend-item"><span class="legend-dot reject"></span>违规</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getContentReviewList, approveContentReview, getContentReviewDetail, getContentReviewStats, getContentReviewTrend, hideContentReview, deleteContentReview, banContentReview, seedContentReview } from '../api/admin'
import { toArray } from '../utils/safeArray'

const router = useRouter()

const activeMode = ref('auto_manual')
const activeTab = ref('all')

// 审核统计数据
const reviewStats = ref({
  pending: 0,
  slaRemaining: '0h',
  todayReviewed: 0
})

const reviewModes = [
  { key: 'auto', label: '机审自动通过' },
  { key: 'auto_manual', label: '机审+人审' },
  { key: 'manual', label: '纯人审' }
]

const violationTabs = [
  { key: 'all', label: '全部' },
  { key: 'porn', label: '色情' },
  { key: 'violence', label: '暴力' },
  { key: 'hate', label: '仇恨言论' },
  { key: 'infringement', label: '侵权' },
  { key: 'misinfo', label: '虚假信息' },
  { key: 'abuse', label: '虐待动物' }
]

const reviewItems = ref([])
// 分页状态(参考商品页:每页 10/20/50/100 可选)
const reviewPage = ref(1)
// 默认每页 100 条,贴合"全部选项 = 看完全部数据"的直觉;
// 用户可手动切换 10/20/50/100。
const reviewSize = ref(100)
const reviewTotal = ref(0)
const loading = ref(false)

// 封禁弹窗状态
const banDialogVisible = ref(false)
const banSubmitting = ref(false)
const banForm = reactive({
  id: null,
  banType: '',
  comment: '',
})

// 分页-跳转输入框
const jumpPage = ref(1)
// 测试数据灌入按钮(仅 dev):灌入 6 条 reason=各种违规类型的演示记录
const seeding = ref(false)

const trendData = reactive([
  { label: '...', pass: 0, reject: 0 },
  { label: '...', pass: 0, reject: 0 },
  { label: '...', pass: 0, reject: 0 },
  { label: '...', pass: 0, reject: 0 },
  { label: '...', pass: 0, reject: 0 },
  { label: '...', pass: 0, reject: 0 },
  { label: '...', pass: 0, reject: 0 }
])

// 切换审核模式
function switchMode(mode) {
  activeMode.value = mode
  reviewPage.value = 1
  loadReviewItems()
}

// 切换违规类型标签
function switchTab(tab) {
  activeTab.value = tab
  reviewPage.value = 1
  loadReviewItems()
}

/**
 * 灌入测试数据(仅 dev):点击后插入 6 条 reason=各种违规类型的演示记录
 * 用于演示 tab 筛选/封禁弹窗联动/联动隐藏逻辑
 */
async function onSeedTestData() {
  if (seeding.value) return
  seeding.value = true
  try {
    const count = await seedContentReview()
    ElMessage.success(`已插入 ${count} 条测试审核记录`)
    await loadReviewItems()
  } catch (e) {
    ElMessage.error('灌入失败: ' + (e.message || '未知错误'))
  } finally {
    seeding.value = false
  }
}

// 分页:页码变化
function onPageChange(page) {
  reviewPage.value = page
  loadReviewItems()
}

// 分页:每页大小变化
function onPageSizeChange() {
  reviewPage.value = 1
  loadReviewItems()
}

// 分页:总页数(计算属性)
const totalPages = computed(() => {
  if (!reviewTotal.value) return 1
  return Math.max(1, Math.ceil(reviewTotal.value / reviewSize.value))
})

// 分页:上一页
function goPrevPage() {
  if (reviewPage.value > 1) {
    reviewPage.value -= 1
    loadReviewItems()
  }
}

// 分页:下一页
function goNextPage() {
  if (reviewPage.value < totalPages.value) {
    reviewPage.value += 1
    loadReviewItems()
  }
}

// 分页:跳转到指定页
function onJumpPage() {
  let p = Number(jumpPage.value)
  if (!Number.isFinite(p) || p < 1) p = 1
  if (p > totalPages.value) p = totalPages.value
  reviewPage.value = p
  loadReviewItems()
}

// 加载审核统计
async function loadReviewStats() {
  try {
    const res = await getContentReviewStats()
    if (res) {
      reviewStats.value = {
        pending: res.pending || 0,
        slaRemaining: res.slaRemaining || '0h',
        todayReviewed: res.todayReviewed || 0
      }
    }
  } catch (e) {
    console.error('获取审核统计失败', e)
  }
}

// 加载审核趋势
async function loadReviewTrend() {
  try {
    const res = await getContentReviewTrend({ days: 7 })
    const trendList = toArray(res)
    if (trendList.length > 0) {
      trendData.length = 0
      trendData.push(...trendList.map(d => ({
        label: d.label || d.date || '...',
        pass: d.pass || d.approve || 0,
        reject: d.reject || d.violation || 0
      })))
    }
  } catch (e) {
    console.error('获取审核趋势失败', e)
  }
}

// 审核状态枚举 -> 中文标签 / 标签颜色 / 是否已处理（DELETED 时禁用审核按钮）
const STATUS_LABEL_MAP = {
  PENDING: '待审核',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  HIDDEN: '已隐藏',
  BANNED: '已封禁',
  DELETED: '已删除'
}
const STATUS_TAG_CLASS_MAP = {
  DELETED: 'tag-red',
  BANNED: 'tag-red',
  REJECTED: 'tag-red',
  APPROVED: 'tag-green',
  HIDDEN: 'tag-gray',
  PENDING: 'tag-gray'
}

// 加载待审核内容列表(分页)
async function loadReviewItems() {
  loading.value = true
  try {
    const params = {
      page: reviewPage.value,
      size: reviewSize.value,
    }
    // 传递审核模式参数
    if (activeMode.value) params.mode = activeMode.value
    // 违规类型 tab(key=色情/暴力...)不再误传给后端 contentType(那是内容种类)
    // 而是让后端按 reason LIKE '%违规类型%' 过滤(由 controller 决定;此处不再传 contentType)
    if (activeTab.value !== 'all') {
      params.reasonLike = activeTab.value
    }
    const res = await getContentReviewList(params)
    // 后端 Result<Map> 解包后字段:list / total / page / size / mode
    const records = toArray(res?.list)
    reviewTotal.value = res?.total || 0
    // 映射为前端需要的格式
      reviewItems.value = records.map((item, index) => {
        // 后端返回英文状态枚举（PENDING/APPROVED/REJECTED/HIDDEN/BANNED/DELETED），
        // 统一翻译成中文标签用于列表展示；并把"已处理"状态（包括 DELETED）的
        // 通过/隐藏/删除/封禁按钮置灰，避免重复操作造成状态机漂移
        const rawStatus = item.status || 'PENDING'
        // 后端 images 字段为 List<String>（已 JSON.parse）,取第一张作为缩略图
        const imageList = Array.isArray(item.images) ? item.images : []
        const imageUrl = imageList.length > 0 ? resolveMediaUrl(imageList[0]) : ''
        const avatarUrl = item.avatar ? resolveMediaUrl(item.avatar) : ''
        // 后端返回完整 content（contentType=POST 时取自原帖）,不再是 500 字摘要
        const fullContent = item.content || item.contentExcerpt || ''
        return {
          id: item.id,
          // emoji 仅作为无图时的回退缩略图
          thumb: item.contentType === '视频' ? '🎬' : item.contentType === '图片' ? '📷' : '📝',
          imageUrl,
          contentType: item.contentType || (item.rating ? '评论' : '图文'),
          contentTypeClass: item.contentType === '视频' ? 'tag-orange' : item.contentType === '图片' ? 'tag-blue' : 'tag-green',
          autoResult: STATUS_LABEL_MAP[rawStatus] || rawStatus || '待审核',
          statusTagClass: STATUS_TAG_CLASS_MAP[rawStatus] || 'tag-gray',
          // DELETED 状态的记录视为"已处理"，4 个审核按钮全部禁用（仅保留"详情"）
          processed: rawStatus === 'DELETED',
          // 显示用完整内容;title 提示查看完整原文
          description: fullContent,
          // 优先用后端返回的 username(nickname);缺失时回退 "用户 + userId"
          publisher: item.username || ('用户' + (item.userId || '')),
          avatar: avatarUrl,
          submitTime: item.reviewTime || item.createTime || ''
        }
      })
  } catch (e) {
    ElMessage.error('获取审核内容失败')
  } finally {
    loading.value = false
  }
}

/**
 * 媒体 URL 适配：
 *  - 已是 http(s):// 或 data: 直接返回
 *  - 相对路径 /uploads/... 走 vite proxy / nginx 同源,直接返回
 *  - 其他协议（blob:/wxfile:/...）视为无效,返回空字符串,触发 fallback
 */
function resolveMediaUrl(url) {
  if (!url) return ''
  if (url.startsWith('http://') || url.startsWith('https://') || url.startsWith('data:')) return url
  if (url.startsWith('/uploads/')) return url
  return ''
}

/** 缩略图加载失败：清空 imageUrl,显示 emoji fallback */
function onThumbError(item) {
  if (item) item.imageUrl = ''
}

/** 头像加载失败：清空 avatar,显示首字符 fallback */
function onAvatarError(item) {
  if (item) item.avatar = ''
}

async function handleReview(id, action) {
  // 封禁:走单独的 confirmBan 流程(打开违规类型下拉弹窗)
  if (action === 'ban') {
    banForm.id = id
    banForm.banType = ''
    banForm.comment = ''
    banDialogVisible.value = true
    return
  }
  const actionLabels = {
    approve: '已通过',
    hide: '已隐藏',
    delete: '已删除',
    ban: '已封禁'
  }
  try {
    if (action === 'approve') {
      await approveContentReview(id)
    } else if (action === 'hide') {
      await hideContentReview(id)
    } else if (action === 'delete') {
      await deleteContentReview(id)
    } else if (action === 'ban') {
      await banContentReview(id, { banType: '其他违规', comment: '快速封禁' })
    }
    ElMessage.success(`内容 #${id} ${actionLabels[action]}`)
    // 保留当前分页位置刷新(避免审核后跳回第一页影响管理员操作连续性)
    await loadReviewItems()
  } catch (e) {
    ElMessage.error('操作失败: ' + (e.message || '未知错误'))
  }
}

/**
 * 确认封禁:校验必填项,调用 banContentReview 传 banType/comment
 * 后端联动把对应 mo_community_post.status 置 0 → C 端列表自然不再返回
 */
async function confirmBan() {
  if (!banForm.banType) {
    ElMessage.warning('请选择违规类型')
    return
  }
  banSubmitting.value = true
  try {
    await banContentReview(banForm.id, {
      banType: banForm.banType,
      comment: banForm.comment || '',
    })
    ElMessage.success(`内容 #${banForm.id} 已封禁(原因:${banForm.banType})`)
    banDialogVisible.value = false
    // 保留当前分页位置刷新
    await loadReviewItems()
  } catch (e) {
    ElMessage.error('封禁失败: ' + (e.message || '未知错误'))
  } finally {
    banSubmitting.value = false
  }
}

// 跳转到审核详情页：使用 window.location.href 强制整页跳转，避免 SPA chunk 缓存
function goDetail(id) {
  window.location.href = `/admin/content-review-detail?id=${id}`
}

onMounted(() => {
  loadReviewStats()
  loadReviewTrend()
  loadReviewItems()
})
</script>

<style scoped lang="css">
.page-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-800);
  margin: 0 0 20px;
}

/* 审核模式 */
.mode-switcher {
  display: flex;
  gap: 8px;
  margin-bottom: 20px;
}

.mode-chip {
  padding: 6px 18px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--card);
  color: var(--text-600);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
}

.mode-chip.active {
  background: var(--primary);
  color: #fff;
  border-color: var(--primary);
}

.mode-chip:hover:not(.active) {
  border-color: var(--primary);
  color: var(--primary);
}

/* 统计 */
.stats-row {
  display: flex;
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  flex: 1;
  padding: 20px;
  border-radius: var(--radius);
  background: var(--card);
  border: 1px solid var(--border);
  box-shadow: var(--shadow-xs);
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 4px;
}

.stat-label {
  font-size: 13px;
  color: var(--text-400);
}

.stat-card.pending .stat-value { color: var(--state-warning); }
.stat-card.sla .stat-value { color: var(--primary); }
.stat-card.done .stat-value { color: var(--state-success); }

/* 审核卡片 */
.review-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 24px;
}

/* 加载/空状态 */
.review-status {
  text-align: center;
  padding: 60rpx 0;
  font-size: 14px;
  color: var(--text-400);
}

/* 分页栏:与商品页风格保持一致,自实现 UI(不依赖 el-pagination 渲染) */
.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 16rpx;
  padding: 24rpx 16rpx;
  margin-bottom: 24rpx;
  flex-wrap: wrap;
  background: var(--card);
  border: 1rpx solid var(--border);
  border-radius: var(--radius);
  font-size: 28rpx;
  color: var(--text-600);
}
.pagination-bar .pg-total strong {
  color: var(--primary);
  font-weight: 600;
  margin: 0 4rpx;
}
.pagination-bar .pg-sizes {
  height: 56rpx;
  padding: 0 16rpx;
  border: 1rpx solid var(--border);
  border-radius: 8rpx;
  background: var(--card);
  color: var(--text-700);
  font-size: 26rpx;
  cursor: pointer;
}
.pagination-bar .pg-btn {
  height: 56rpx;
  padding: 0 20rpx;
  border: 1rpx solid var(--border);
  border-radius: 8rpx;
  background: var(--card);
  color: var(--text-700);
  font-size: 26rpx;
  cursor: pointer;
  transition: all 0.15s ease;
}
.pagination-bar .pg-btn:hover:not(:disabled) {
  border-color: var(--primary);
  color: var(--primary);
}
.pagination-bar .pg-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.pagination-bar .pg-current {
  padding: 0 16rpx;
  font-weight: 500;
}
.pagination-bar .pg-jump {
  display: inline-flex;
  align-items: center;
  gap: 8rpx;
}
.pagination-bar .pg-jump-input {
  width: 100rpx;
  height: 56rpx;
  padding: 0 12rpx;
  border: 1rpx solid var(--border);
  border-radius: 8rpx;
  text-align: center;
  font-size: 26rpx;
  background: var(--card);
  color: var(--text-700);
}

.review-card {
  display: flex;
  gap: 16px;
  padding: 16px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-xs);
}

.review-thumb {
  width: 80px;
  height: 80px;
  border-radius: 8px;
  background: var(--background-200);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  flex-shrink: 0;
  overflow: hidden;
}

/* 真实图片缩略图(与 emoji fallback 共用容器尺寸) */
.review-thumb-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

/* 用户头像真实图片:与 .user-avatar 共用容器尺寸,scoped 防止与全局样式冲突 */
.user-avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.review-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.review-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.review-tags {
  display: flex;
  gap: 6px;
}

.review-time {
  font-size: 12px;
  color: var(--text-400);
}

.review-desc {
  font-size: 13px;
  color: var(--text-600);
  line-height: 1.5;
}

.review-publisher {
  font-size: 12px;
  color: var(--text-500);
}

.review-actions {
  display: flex;
  gap: 8px;
  margin-top: 4px;
}

/* 趋势图 */
.trend-chart {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  height: 160px;
  padding: 0 8px;
}

.bar-group {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.bar-stack {
  width: 32px;
  height: 120px;
  display: flex;
  flex-direction: column-reverse;
  gap: 2px;
  border-radius: 4px;
  background: var(--background-100);
  overflow: hidden;
}

.bar {
  width: 100%;
  border-radius: 2px;
  transition: height 0.3s ease;
}

.bar-pass {
  background: var(--state-success);
}

.bar-reject {
  background: var(--state-error);
}

.chart-legend {
  display: flex;
  justify-content: center;
  gap: 24px;
  margin-top: 16px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-500);
}

.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 2px;
}

.legend-dot.pass { background: var(--state-success); }
.legend-dot.reject { background: var(--state-error); }
</style>
