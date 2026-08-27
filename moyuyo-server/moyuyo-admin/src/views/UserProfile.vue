<template>
  <div class="page-wrapper">
    <!-- 页面标题区域 -->
    <div class="page-title-area">
      <h1>用户画像</h1>
      <p>输入用户 ID 或用户名，查看用户基本信息、积分、访问行为等真实数据</p>
    </div>

    <!-- 查询面板 -->
    <div class="query-panel">
      <div class="form-row">
        <div class="form-group">
          <label>用户名 / ID</label>
          <input v-model="searchForm.keyword" type="text" placeholder="请输入用户名或ID" @keyup.enter="handleSearch" />
        </div>
        <div class="form-actions">
          <button class="btn btn-primary" :disabled="loading" @click="handleSearch">搜索</button>
          <button class="btn btn-outline" @click="handleReset">重置</button>
        </div>
      </div>
    </div>

    <!-- 加载失败 / 用户不存在 -->
    <div v-if="loadError" class="error-state" style="background:var(--state-error-surface);border:1px solid rgba(255,59,48,0.18);border-radius:var(--radius);padding:18px 24px;margin-bottom:20px;">
      <div style="display:flex;align-items:center;gap:10px;">
        <span style="font-size:18px;">⚠️</span>
        <strong style="color:var(--state-error);">用户加载失败：</strong>
        <span style="color:var(--text-700);">{{ loadError }}</span>
      </div>
      <div style="margin-top:8px;font-size:12px;color:var(--text-400);">
        可能原因：用户已被删除 / 数据库无此 ID / 权限不足。请用上方搜索框重新输入有效用户 ID。
      </div>
    </div>

    <!-- 用户画像卡：基础信息 + 积分 + 会员 -->
    <div class="profile-card" v-if="hasData">
      <div class="profile-head">
        <div class="profile-avatar">{{ avatarText }}</div>
        <div class="profile-main">
          <div class="profile-name">
            {{ userInfo.nickname !== '-' ? userInfo.nickname : userInfo.username }}
            <span class="badge" :class="levelBadgeClass(userInfo.memberLevel)">{{ memberLabel }}</span>
            <span class="badge" :class="userInfo.status === 1 ? 'badge-active' : 'badge-inactive'">
              {{ userInfo.status === 1 ? '正常' : '禁用' }}
            </span>
          </div>
          <div class="profile-sub">
            ID: {{ userInfo.userId }} ·
            {{ userInfo.memberNo ? '卡号 ' + userInfo.memberNo : '非会员' }} ·
            注册于 {{ userInfo.registerTime }}
          </div>
        </div>
        <div class="profile-spent">
          <div class="profile-spent-label">累计消费</div>
          <div class="profile-spent-value">{{ userInfo.totalSpent }}</div>
        </div>
      </div>

      <!-- 积分区域：可手动调整 -->
      <div class="points-block">
        <div class="points-card">
          <div class="points-card__label">当前积分</div>
          <div class="points-card__value">{{ userInfo.points != null ? userInfo.points.toLocaleString() : 0 }}</div>
          <button class="btn btn-primary btn-sm" @click="openPointsDialog">手动调整</button>
        </div>
        <div class="points-card">
          <div class="points-card__label">成长值</div>
          <div class="points-card__value">{{ userInfo.growthValue != null ? userInfo.growthValue.toLocaleString() : 0 }}</div>
          <div class="points-card__sub">{{ growthValueHint }}</div>
        </div>
        <div class="points-card">
          <div class="points-card__label">已下单数</div>
          <div class="points-card__value">{{ userInfo.orderCount || 0 }}</div>
          <div class="points-card__sub">仅统计所有订单状态</div>
        </div>
      </div>

      <div class="info-grid">
        <div class="info-item"><span class="info-label">用户名</span><span class="info-value">{{ userInfo.username }}</span></div>
        <div class="info-item"><span class="info-label">手机号</span><span class="info-value">{{ userInfo.phone }}</span></div>
        <div class="info-item"><span class="info-label">邮箱</span><span class="info-value">{{ userInfo.email }}</span></div>
        <div class="info-item"><span class="info-label">性别</span><span class="info-value">{{ genderLabel(userInfo.gender) }}</span></div>
        <div class="info-item"><span class="info-label">年龄</span><span class="info-value">{{ userInfo.age != null ? userInfo.age + ' 岁' : '未填写' }}</span></div>
        <div class="info-item"><span class="info-label">最近登录</span><span class="info-value">{{ userInfo.lastLoginTime || '暂无' }}</span></div>
      </div>
    </div>
    <div v-else class="empty-state" style="background:var(--card);border:1px solid var(--border);border-radius:var(--radius);">
      <div class="empty-state-icon">👤</div>
      <div class="empty-state-text">输入用户 ID 或用户名，点击「搜索」查看画像</div>
    </div>

    <!-- 行为数据 -->
    <div v-if="hasData" style="margin-top:20px">
      <div class="section-title">用户行为数据</div>
      <div class="data-table-wrapper">
        <table class="data-table">
          <thead>
            <tr>
              <th>行为类型</th>
              <th>次数</th>
              <th>最近时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in behaviorData" :key="item.behaviorType">
              <td>
                <span class="tag tag-blue">{{ behaviorTypeLabel(item.behaviorType) }}</span>
              </td>
              <td class="count-cell">{{ item.count }}</td>
              <td class="time-cell">{{ item.lastTime }}</td>
            </tr>
            <tr v-if="behaviorData.length === 0">
              <td colspan="3">
                <div class="empty-state">
                  <div class="empty-state-icon">📊</div>
                  <div class="empty-state-text">暂无行为数据</div>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 访问过的商品 -->
    <div v-if="hasData" class="visited-section">
      <div class="section-title">访问过的商品 <span class="section-sub">来源 mo_browsing_history</span></div>
      <div v-if="visitedProducts.length > 0" class="product-grid">
        <div v-for="item in visitedProducts" :key="item.id || item.productId" class="product-item">
          <div class="product-thumb">
            <img v-if="item.mainImage" :src="item.mainImage" :alt="item.productName" />
            <div v-else class="product-thumb__placeholder">商品</div>
          </div>
          <div class="product-info">
            <div class="product-name" :title="item.productName">{{ item.productName || ('商品 #' + item.productId) }}</div>
            <div class="product-meta">
              <span class="price">{{ item.price ? '¥' + (item.price / 100).toFixed(2) : '-' }}</span>
              <span class="views">浏览 {{ item.viewCount }} 次</span>
            </div>
            <div class="product-time">最近 {{ item.lastVisitTime || '-' }}</div>
          </div>
        </div>
      </div>
      <div v-else class="empty-state" style="background:var(--card);border:1px solid var(--border);border-radius:var(--radius);">
        <div class="empty-state-icon">🛒</div>
        <div class="empty-state-text">该用户暂无商品浏览记录</div>
      </div>
    </div>

    <!-- 访问过的页面 -->
    <div v-if="hasData" class="visited-section">
      <div class="section-title">访问过的页面 <span class="section-sub">来源 mo_visit_log</span></div>
      <div v-if="visitedPages.length > 0" class="data-table-wrapper">
        <table class="data-table">
          <thead>
            <tr>
              <th>页面名称</th>
              <th>页面 URL</th>
              <th>访问次数</th>
              <th>累计停留</th>
              <th>最近访问</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in visitedPages" :key="item.pageUrl">
              <td>{{ item.pageName }}</td>
              <td class="url-cell" :title="item.pageUrl">{{ item.pageUrl }}</td>
              <td class="count-cell">{{ item.visitCount }}</td>
              <td>{{ formatStay(item.stayDuration) }}</td>
              <td class="time-cell">{{ formatDateTime(item.lastVisitTime) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty-state" style="background:var(--card);border:1px solid var(--border);border-radius:var(--radius);">
        <div class="empty-state-icon">🌐</div>
        <div class="empty-state-text">该用户暂无页面访问记录</div>
      </div>
    </div>

    <!-- 调整积分弹窗 -->
    <el-dialog v-model="pointsDialogVisible" title="手动调整积分" width="420px" :close-on-click-modal="false">
      <div class="points-form">
        <div class="points-form__current">
          当前积分：<strong>{{ userInfo.points != null ? userInfo.points : 0 }}</strong>
        </div>
        <div class="form-row">
          <label>调整类型</label>
          <el-radio-group v-model="pointsForm.direction">
            <el-radio-button value="add">增加</el-radio-button>
            <el-radio-button value="sub">扣减</el-radio-button>
          </el-radio-group>
        </div>
        <div class="form-row">
          <label>调整数量</label>
          <el-input-number v-model="pointsForm.amount" :min="0" :step="10" :max="100000" />
        </div>
        <div class="form-row">
          <label>调整原因</label>
          <el-input v-model="pointsForm.reason" placeholder="例如：售后补偿 / 活动奖励" maxlength="100" show-word-limit />
        </div>
        <div class="points-form__preview">
          调整后预计积分：<strong>{{ previewPoints }}</strong>
        </div>
      </div>
      <template #footer>
        <el-button @click="pointsDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="pointsSubmitting" @click="submitAdjustPoints">确认调整</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getUserProfile,
  getUserBehaviors,
  getUserVisitedProducts,
  getUserVisitedPages,
  adjustUserPoints
} from '../api/admin'
import { toArray } from '../utils/safeArray'

const searchForm = reactive({ keyword: '' })

// 用户基本信息
const userInfo = reactive({
  userId: '-',
  username: '-',
  nickname: '-',
  phone: '-',
  email: '-',
  registerTime: '-',
  level: '-',
  memberLevel: 'NORMAL',
  growthValue: 0,
  points: 0,
  memberNo: '',
  lastLoginTime: '',
  status: 1,
  totalSpent: '-',
  gender: '',
  age: null,
  tags: []
})

const GENDER_LABELS = {
  MALE: '男',
  FEMALE: '女',
  OTHER: '中性',
  UNDISCLOSED: '不透露'
}
function genderLabel(code) {
  if (!code) return '未填写'
  return GENDER_LABELS[code] || code
}

// 行为类型映射（mo_user_behavior.behaviorType 枚举）
const BEHAVIOR_TYPE_LABELS = {
  VIEW_PRODUCT: '浏览商品',
  SEARCH: '搜索',
  ADD_CART: '加购',
  PLACE_ORDER: '下单',
  FAVORITE: '收藏'
}
function behaviorTypeLabel(code) {
  return BEHAVIOR_TYPE_LABELS[code] || code || '-'
}

// 会员等级映射：与 C 端映射对齐（L1~L5），展示中文字符
const MEMBER_LEVEL_LABELS = {
  NORMAL: 'L1 普通会员',
  SILVER: 'L2 银卡会员',
  GOLD: 'L3 金卡会员',
  PLATINUM: 'L4 铂金会员',
  DIAMOND: 'L5 钻石会员'
}
const memberLabel = computed(() => MEMBER_LEVEL_LABELS[userInfo.memberLevel] || userInfo.memberLevel)

// 成长值提示：与当前等级下一档对比
const growthValueHint = computed(() => {
  const lv = userInfo.memberLevel || 'NORMAL'
  if (lv === 'DIAMOND') return '已达顶级会员'
  if (lv === 'NORMAL') return '入门会员（首单+签到可升级）'
  return '继续消费累积成长值'
})

// 用户行为数据
const behaviorData = ref([])

// 访问过的商品 / 页面
const visitedProducts = ref([])
const visitedPages = ref([])

const loading = ref(false)
const loadError = ref('')

const hasData = computed(() => userInfo.userId !== '-')

const avatarText = computed(() => {
  const name = userInfo.nickname !== '-' ? userInfo.nickname : userInfo.username
  return (name || '?').charAt(0).toUpperCase()
})

function levelBadgeClass(level) {
  const lv = String(level || '')
  if (lv === 'GOLD') return 'badge-gold'
  if (lv === 'SILVER') return 'badge-silver'
  if (lv === 'DIAMOND') return 'badge-diamond'
  if (lv === 'PLATINUM') return 'badge-platinum'
  return 'badge-regular'
}

async function handleSearch() {
  const keyword = searchForm.keyword.trim()
  if (!keyword) {
    ElMessage.warning('请输入用户名或ID')
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    const res = await getUserProfile(keyword)
    if (res && res.userId) {
      Object.assign(userInfo, {
        userId: res.userId || res.id || '-',
        username: res.username || '-',
        nickname: res.nickname || '-',
        phone: res.phone || '-',
        email: res.email || '-',
        registerTime: res.registerTime || '-',
        level: res.memberLevel || res.level || 'NORMAL',
        memberLevel: res.memberLevel || 'NORMAL',
        growthValue: typeof res.growthValue === 'number' ? res.growthValue : 0,
        points: typeof res.points === 'number' ? res.points : 0,
        memberNo: res.memberNo || '',
        lastLoginTime: res.lastLoginTime || '',
        status: typeof res.status === 'number' ? res.status : 1,
        totalSpent: res.totalSpent != null ? res.totalSpent : '-',
        gender: res.gender || '',
        age: typeof res.age === 'number' ? res.age : null,
        tags: res.tags || []
      })
    } else {
      loadError.value = '未找到用户「' + keyword + '」（可能已被删除）'
      handleReset()
      return
    }

    // 拉取行为数据、访问商品、访问页面（并发，互不依赖）
    const [behaviors, products, pages] = await Promise.all([
      getUserBehaviors(keyword).catch(() => []),
      getUserVisitedProducts(userInfo.userId, 50).catch(() => []),
      getUserVisitedPages(userInfo.userId, 50).catch(() => [])
    ])
    behaviorData.value = toArray(behaviors)
    visitedProducts.value = toArray(products)
    visitedPages.value = toArray(pages)

    ElMessage.success('加载完成')
  } catch (err) {
    console.error('获取用户数据失败:', err)
    const serverMsg = err?.response?.data?.message || err?.message || '未知错误'
    loadError.value = serverMsg + '（ID: ' + keyword + '）'
    handleReset()
  } finally {
    loading.value = false
  }
}

function handleReset() {
  searchForm.keyword = ''
  Object.assign(userInfo, {
    userId: '-', username: '-', nickname: '-', phone: '-', email: '-',
    registerTime: '-', level: '-', memberLevel: 'NORMAL',
    growthValue: 0, points: 0, memberNo: '', lastLoginTime: '', status: 1,
    totalSpent: '-', gender: '', age: null, tags: []
  })
  behaviorData.value = []
  visitedProducts.value = []
  visitedPages.value = []
}

// 积分调整弹窗
const pointsDialogVisible = ref(false)
const pointsSubmitting = ref(false)
const pointsForm = reactive({
  direction: 'add',
  amount: 0,
  reason: ''
})
const previewPoints = computed(() => {
  const base = userInfo.points != null ? userInfo.points : 0
  const delta = Number(pointsForm.amount || 0)
  return pointsForm.direction === 'add' ? base + delta : Math.max(0, base - delta)
})

function openPointsDialog() {
  if (userInfo.userId === '-') {
    ElMessage.warning('请先搜索并选中用户')
    return
  }
  pointsForm.direction = 'add'
  pointsForm.amount = 100
  pointsForm.reason = ''
  pointsDialogVisible.value = true
}

async function submitAdjustPoints() {
  const delta = Number(pointsForm.amount || 0)
  if (delta <= 0) {
    ElMessage.warning('调整数量必须大于 0')
    return
  }
  if (!pointsForm.reason || !pointsForm.reason.trim()) {
    ElMessage.warning('请填写调整原因（用于审计留痕）')
    return
  }
  const signedAmount = pointsForm.direction === 'add' ? delta : -delta
  pointsSubmitting.value = true
  try {
    await adjustUserPoints(userInfo.userId, signedAmount, pointsForm.reason.trim())
    ElMessage.success(`已${pointsForm.direction === 'add' ? '增加' : '扣减'} ${delta} 积分`)
    pointsDialogVisible.value = false
    // 刷新用户数据：积分实时同步
    const fresh = await getUserProfile(userInfo.userId)
    if (fresh && fresh.points != null) {
      userInfo.points = fresh.points
    }
  } catch (err) {
    console.error('调整积分失败:', err)
    const msg = err?.response?.data?.message || err?.message || '未知错误'
    ElMessage.error('调整失败：' + msg)
  } finally {
    pointsSubmitting.value = false
  }
}

function formatStay(seconds) {
  if (!seconds) return '0 秒'
  if (seconds < 60) return seconds + ' 秒'
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  if (m < 60) return m + ' 分 ' + s + ' 秒'
  const h = Math.floor(m / 60)
  return h + ' 时 ' + (m % 60) + ' 分'
}

function formatDateTime(value) {
  if (!value) return '-'
  // 后端 LocalDateTime 序列化为 "yyyy-MM-ddTHH:mm:ss"，去掉 T 以更易读
  return String(value).replace('T', ' ').slice(0, 19)
}

const route = useRoute()
onMounted(() => {
  const idFromQuery = route.query.id
  if (idFromQuery) {
    searchForm.keyword = String(idFromQuery)
    handleSearch()
  }
})

watch(
  () => route.query.id,
  (newId) => {
    if (newId && String(newId) !== searchForm.keyword) {
      searchForm.keyword = String(newId)
      handleSearch()
    }
  }
)
</script>

<style scoped>
.page-wrapper { padding: 20px; }

/* 画像卡 */
.profile-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-xs);
  padding: 20px 24px;
}
.profile-head {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 18px;
  flex-wrap: wrap;
}
.profile-avatar {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  background: var(--brand-50);
  color: var(--brand-600);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: 700;
  flex-shrink: 0;
}
.profile-main { flex: 1; min-width: 200px; }
.profile-name {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-800);
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.profile-sub {
  font-size: 12px;
  color: var(--text-400);
  margin-top: 4px;
}
.profile-spent { text-align: right; }
.profile-spent-label { font-size: 12px; color: var(--text-400); }
.profile-spent-value {
  font-size: 26px;
  font-weight: 700;
  color: var(--text-800);
  font-variant-numeric: tabular-nums;
}

/* 积分 / 成长值 / 订单 三联卡 */
.points-block {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}
.points-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 16px 20px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--background-100);
}
.points-card__label {
  font-size: 12px;
  color: var(--text-400);
}
.points-card__value {
  font-size: 26px;
  font-weight: 700;
  color: var(--text-800);
  font-variant-numeric: tabular-nums;
}
.points-card__sub {
  font-size: 11px;
  color: var(--text-400);
}

/* 信息网格 */
.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px 24px;
  border-top: 1px solid var(--background-100);
  padding-top: 16px;
}
.info-item { display: flex; gap: 12px; font-size: 13px; }
.info-label {
  color: var(--text-400);
  width: 60px;
  flex-shrink: 0;
}
.info-value { color: var(--text-700); font-weight: 500; word-break: break-all; }

/* 分区标题 */
.section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-600);
  margin: 24px 0 12px;
  display: flex;
  align-items: center;
  gap: 10px;
}
.section-sub {
  font-size: 11px;
  color: var(--text-400);
  background: var(--background-200);
  border-radius: 999px;
  padding: 2px 8px;
  font-weight: 400;
}

.count-cell {
  font-weight: 600;
  color: var(--text-800);
  font-variant-numeric: tabular-nums;
}
.time-cell { font-size: 12px; color: var(--text-500); }
.url-cell {
  max-width: 360px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: var(--font-mono, monospace);
  font-size: 12px;
  color: var(--text-500);
}

/* 访问过的商品网格 */
.product-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 16px;
}
.product-item {
  display: flex;
  gap: 12px;
  padding: 12px;
  background: var(--background-100);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}
.product-item:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}
.product-thumb {
  width: 64px;
  height: 64px;
  border-radius: 8px;
  overflow: hidden;
  background: var(--background-200);
  flex-shrink: 0;
}
.product-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.product-thumb__placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: var(--text-400);
}
.product-info { flex: 1; min-width: 0; }
.product-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-800);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 4px;
}
.product-meta {
  display: flex;
  gap: 8px;
  font-size: 12px;
  color: var(--text-500);
  margin-bottom: 4px;
}
.product-meta .price {
  color: var(--state-error);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.product-time {
  font-size: 11px;
  color: var(--text-400);
}

/* 调整积分弹窗 */
.points-form { display: flex; flex-direction: column; gap: 14px; }
.points-form__current {
  padding: 10px 14px;
  background: var(--background-100);
  border-radius: var(--radius);
  font-size: 13px;
  color: var(--text-600);
}
.points-form__preview {
  padding: 10px 14px;
  background: var(--brand-50);
  border-radius: var(--radius);
  color: var(--brand-700);
  font-size: 13px;
}
.points-form .form-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.points-form .form-row label {
  width: 72px;
  flex-shrink: 0;
  color: var(--text-500);
  font-size: 13px;
}

/* 响应式：窄屏单列 */
@media (max-width: 960px) {
  .points-block { grid-template-columns: 1fr; }
  .info-grid { grid-template-columns: 1fr; }
}
</style>