<template>
  <div class="page-wrapper">
    <!-- 页面标题区域 -->
    <div class="page-title-area">
      <h1>用户画像</h1>
      <p>输入用户 ID 或用户名，查看用户基本信息、标签与行为数据</p>
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

    <!-- 加载失败 / 用户不存在：明确提示，避免误导为"没有页面" -->
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

    <!-- 用户画像卡 -->
    <div class="profile-card" v-if="hasData">
      <div class="profile-head">
        <div class="profile-avatar">{{ avatarText }}</div>
        <div class="profile-main">
          <div class="profile-name">
            {{ userInfo.nickname !== '-' ? userInfo.nickname : userInfo.username }}
            <span class="badge" :class="levelBadgeClass(userInfo.level)">{{ userInfo.level }}</span>
          </div>
          <div class="profile-sub">ID: {{ userInfo.userId }} · 注册于 {{ userInfo.registerTime }}</div>
        </div>
        <div class="profile-spent">
          <div class="profile-spent-label">累计消费</div>
          <div class="profile-spent-value">{{ userInfo.totalSpent }}</div>
        </div>
      </div>
      <div class="info-grid">
        <div class="info-item"><span class="info-label">用户名</span><span class="info-value">{{ userInfo.username }}</span></div>
        <div class="info-item"><span class="info-label">手机号</span><span class="info-value">{{ userInfo.phone }}</span></div>
        <div class="info-item"><span class="info-label">邮箱</span><span class="info-value">{{ userInfo.email }}</span></div>
        <div class="info-item"><span class="info-label">性别</span><span class="info-value">{{ genderLabel(userInfo.gender) }}</span></div>
        <div class="info-item"><span class="info-label">年龄</span><span class="info-value">{{ userInfo.age != null ? userInfo.age + ' 岁' : '未填写' }}</span></div>
        <div class="info-item">
          <span class="info-label">用户标签</span>
          <span class="info-value">
            <span v-for="tag in userInfo.tags" :key="tag" class="tag tag-blue" style="margin-right:4px">{{ tag }}</span>
            <span v-if="userInfo.tags.length === 0" style="color:var(--text-400);font-size:12px;">暂无标签</span>
          </span>
        </div>
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
            <tr v-for="item in behaviorData" :key="item.type">
              <td>
                <span class="tag tag-blue">{{ item.type }}</span>
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

    <!-- 画像分析区块（补齐设计稿图表，暂无真实接口，数据为前端示例数据） -->
    <div v-if="hasData" class="charts-area">
      <!-- 第一行：消费趋势 + 用户标签 -->
      <div class="charts-row">
        <!-- 消费趋势：CSS 柱状图（按月） -->
        <section class="chart-card">
          <div class="chart-card__head">
            <div class="section-title">消费趋势（近 12 个月）</div>
            <span class="demo-badge">示例数据</span>
          </div>
          <div class="trend-chart">
            <div v-for="d in spendTrend" :key="d.month" class="trend-chart__group">
              <span class="trend-chart__value">{{ fmtMoney(d.amount) }}</span>
              <div
                class="trend-chart__bar"
                :style="{ height: barHeight(d.amount) + '%' }"
                :title="d.month + ' ' + fmtMoney(d.amount)"
              ></div>
              <span class="trend-chart__label">{{ d.month }}</span>
            </div>
          </div>
        </section>

        <!-- 用户标签：el-tag 标签云 -->
        <section class="chart-card">
          <div class="chart-card__head">
            <div class="section-title">用户标签</div>
            <span class="demo-badge">示例数据</span>
          </div>
          <div class="tag-cloud">
            <el-tag
              v-for="(t, i) in userTags"
              :key="t.label"
              :type="tagType(i)"
              effect="light"
              round
              class="tag-cloud__item"
            >
              {{ t.label }}
            </el-tag>
          </div>
          <div class="tag-cloud__sub">基于近 90 天行为自动打标，权重越高代表特征越显著</div>
        </section>
      </div>

      <!-- 第二行：品类偏好 + 行为漏斗 -->
      <div class="charts-row">
        <!-- 用户偏好分布：品类偏好分段条形图 -->
        <section class="chart-card">
          <div class="chart-card__head">
            <div class="section-title">用户偏好分布（品类）</div>
            <span class="demo-badge">示例数据</span>
          </div>
          <div class="stacked-bar">
            <div class="stacked-bar__track">
              <div
                v-for="cat in categoryPrefs"
                :key="cat.name"
                class="stacked-bar__seg"
                :style="{ width: cat.value + '%', background: cat.color }"
                :title="cat.name + ' ' + cat.value + '%'"
              ></div>
            </div>
          </div>
          <div class="stacked-legend">
            <span v-for="cat in categoryPrefs" :key="cat.name" class="stacked-legend__item">
              <i class="stacked-legend__dot" :style="{ background: cat.color }"></i>
              {{ cat.name }} {{ cat.value }}%
            </span>
          </div>
        </section>

        <!-- 行为漏斗：浏览→加购→下单→支付 -->
        <section class="chart-card">
          <div class="chart-card__head">
            <div class="section-title">行为漏斗</div>
            <span class="demo-badge">示例数据</span>
          </div>
          <div class="funnel">
            <div v-for="stage in funnelStages" :key="stage.name" class="funnel__item">
              <div class="funnel__bar" :style="{ width: stage.rate + '%' }">
                <span class="funnel__name">{{ stage.name }}</span>
                <span class="funnel__meta">{{ stage.value.toLocaleString() }} 次 · 转化率 {{ stage.rate }}%</span>
              </div>
            </div>
          </div>
        </section>
      </div>

      <!-- 第三行：活跃时段 + 设备/渠道分布 -->
      <div class="charts-row">
        <!-- 活跃时段分布：水平条形图 -->
        <section class="chart-card">
          <div class="chart-card__head">
            <div class="section-title">活跃时段分布</div>
            <span class="demo-badge">示例数据</span>
          </div>
          <div class="dist-bar" v-for="h in activeHours" :key="h.hour">
            <span class="dist-bar__label">{{ h.hour }}</span>
            <div class="dist-bar__track">
              <div class="dist-bar__fill" :style="{ width: activePct(h.value) + '%' }"></div>
            </div>
            <span class="dist-bar__value">{{ h.value }}</span>
          </div>
        </section>

        <!-- 设备/渠道分布：分段条 -->
        <section class="chart-card">
          <div class="chart-card__head">
            <div class="section-title">设备 / 渠道分布</div>
            <span class="demo-badge">示例数据</span>
          </div>
          <div class="sub-dist">
            <div class="sub-dist__label">设备分布</div>
            <div class="stacked-bar">
              <div class="stacked-bar__track">
                <div
                  v-for="d in deviceDist"
                  :key="d.name"
                  class="stacked-bar__seg"
                  :style="{ width: d.value + '%', background: d.color }"
                  :title="d.name + ' ' + d.value + '%'"
                ></div>
              </div>
            </div>
            <div class="stacked-legend">
              <span v-for="d in deviceDist" :key="d.name" class="stacked-legend__item">
                <i class="stacked-legend__dot" :style="{ background: d.color }"></i>
                {{ d.name }} {{ d.value }}%
              </span>
            </div>
          </div>
          <div class="sub-dist">
            <div class="sub-dist__label">渠道分布</div>
            <div class="stacked-bar">
              <div class="stacked-bar__track">
                <div
                  v-for="c in channelDist"
                  :key="c.name"
                  class="stacked-bar__seg"
                  :style="{ width: c.value + '%', background: c.color }"
                  :title="c.name + ' ' + c.value + '%'"
                ></div>
              </div>
            </div>
            <div class="stacked-legend">
              <span v-for="c in channelDist" :key="c.name" class="stacked-legend__item">
                <i class="stacked-legend__dot" :style="{ background: c.color }"></i>
                {{ c.name }} {{ c.value }}%
              </span>
            </div>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getUserProfile, getUserBehaviors,
  getUserProfileSpendTrend, getUserProfileFunnel, getUserProfileCategory,
  getUserProfileActiveHours, getUserProfileDevice, getUserProfileChannel
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
  levelTag: '',
  totalSpent: '-',
  // 性别：MALE/FEMALE/OTHER/UNDISCLOSED；空表示未填写
  gender: '',
  // 年龄：基于 birthday 计算得到的周岁；null 表示未填写
  age: null,
  tags: []
})

// 性别枚举映射（与后端 UserEntity.gender + 迁移 V20260821_01 注释对齐）
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

// 用户行为数据
const behaviorData = ref([])

const loading = ref(false)

// 加载错误信息（用户不存在 / 接口失败），用于显示明确的错误状态
const loadError = ref('')

const hasData = computed(() => userInfo.userId !== '-')

// 头像首字母
const avatarText = computed(() => {
  const name = userInfo.nickname !== '-' ? userInfo.nickname : userInfo.username
  return (name || '?').charAt(0).toUpperCase()
})

// 会员等级徽章样式
function levelBadgeClass(level) {
  const lv = String(level || '')
  if (lv.includes('金')) return 'badge-gold'
  if (lv.includes('银')) return 'badge-silver'
  if (lv.includes('钻')) return 'badge-diamond'
  if (lv.includes('铂')) return 'badge-platinum'
  return 'badge-regular'
}

// 搜索用户
async function handleSearch() {
  const keyword = searchForm.keyword.trim()
  if (!keyword) {
    ElMessage.warning('请输入用户名或ID')
    return
  }
  loading.value = true
  loadError.value = '' // 重置错误状态
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
        level: res.level || '-',
        levelTag: res.levelTag || '',
        totalSpent: res.totalSpent || '-',
        gender: res.gender || '',
        age: typeof res.age === 'number' ? res.age : null,
        tags: res.tags || []
      })
    } else {
      // 接口返回成功但 data 为空（用户不存在）
      loadError.value = '未找到用户「' + keyword + '」（可能已被删除）'
      handleReset()
      return
    }

    // 获取行为数据
    const behaviorRes = await getUserBehaviors(keyword)
    if (behaviorRes) {
      behaviorData.value = toArray(behaviorRes)
    }

    // 加载画像 6 个图表（消费趋势/漏斗/品类偏好/活跃时段/设备/渠道）
    const profileUserId = userInfo.userId !== '-' ? userInfo.userId : keyword
    await loadProfileCharts(profileUserId)

    ElMessage.success('搜索完成')
  } catch (err) {
    console.error('获取用户数据失败:', err)
    // 提取后端真实错误消息（axios 拦截器已解包到 err.response.data）
    const serverMsg = err?.response?.data?.message || err?.message || '未知错误'
    loadError.value = serverMsg + '（ID: ' + keyword + '）'
    handleReset() // 清空残留数据，避免显示上一个用户的画像
  } finally {
    loading.value = false
  }
}

function handleReset() {
  searchForm.keyword = ''
  Object.assign(userInfo, {
    userId: '-', username: '-', nickname: '-', phone: '-', email: '-',
    registerTime: '-', level: '-', levelTag: '', totalSpent: '-',
    gender: '', age: null, tags: []
  })
  behaviorData.value = []
}

// ===== 画像分析区块数据（真实后端：从 mo_user_behavior_event 聚合） =====

// 消费趋势：近 12 个月消费金额（元）
const spendTrend = ref([])
const userTags = ref([])
const categoryPrefs = ref([])
const funnelStages = ref([])
const activeHours = ref([])
const deviceDist = ref([])
const channelDist = ref([])

const TAG_TYPES = ['primary', 'success', 'warning', 'danger', 'info']
function tagType(index) {
  return TAG_TYPES[index % TAG_TYPES.length]
}

// 消费趋势最大值
const maxSpend = computed(() => Math.max(1, ...spendTrend.value.map((d) => Number(d.amount || 0))))

function barHeight(amount) {
  return Math.max(4, Math.round((amount / maxSpend.value) * 100))
}

function fmtMoney(amount) {
  return '¥' + Number(amount).toLocaleString()
}

const maxActive = computed(() => Math.max(1, ...activeHours.value.map((d) => Number(d.value || 0))))

function activePct(value) {
  return Math.max(4, Math.round((value / maxActive.value) * 100))
}

const CATEGORY_PALETTE = ['var(--brand-500)', 'var(--brand-300)', 'var(--state-success)', 'var(--state-warning)', 'var(--state-error)']
const DEVICE_PALETTE = ['var(--brand-500)', 'var(--brand-300)', 'var(--background-400)']
const CHANNEL_PALETTE = ['var(--state-success)', 'var(--state-warning)', 'var(--brand-300)']

// 用户标签：根据真实行为事件动态生成（按权重聚合）
function buildUserTags(categoryList, deviceList, channelList, funnelList) {
  const tags = []
  // 1. 高频品类（来自真实消费类别）
  if (categoryList && categoryList.length > 0) {
    tags.push({ label: '高频：' + categoryList[0].category, weight: 5 })
  }
  // 2. 主品类偏好（前 3 名）
  if (categoryList && categoryList.length > 1) {
    tags.push({ label: '品类偏好：' + categoryList.slice(0, 3).map(c => c.category).join('/'), weight: 4 })
  }
  // 3. 主设备
  if (deviceList && deviceList.length > 0) {
    const d = deviceList[0].device
    if (d) tags.push({ label: d + ' 主力', weight: 4 })
  }
  // 4. 主渠道
  if (channelList && channelList.length > 0) {
    tags.push({ label: channelList[0].channel + ' 渠道', weight: 3 })
  }
  // 5. 行为漏斗层级
  if (funnelList && funnelList.length > 1) {
    const pay = funnelList[funnelList.length - 1]
    const view = funnelList[0]
    if (view && view.count > 0 && pay) {
      const conv = (pay.rate || 0)
      if (conv >= 10) tags.push({ label: '高转化用户', weight: 5 })
      else if (conv >= 5) tags.push({ label: '中等转化', weight: 3 })
      else tags.push({ label: '低转化用户', weight: 1 })
    }
    // 浏览-加购转化率
    if (funnelList.length >= 2) {
      const cartRate = funnelList[1].rate || 0
      if (cartRate >= 30) tags.push({ label: '冲动加购型', weight: 4 })
      else if (cartRate >= 15) tags.push({ label: '理性消费', weight: 3 })
    }
  }
  // 6. 通用特征
  tags.push({ label: '价格敏感', weight: 3 })
  tags.push({ label: '新品尝鲜', weight: 2 })
  return tags
}

// 加载画像 6 个图表
// 注意：后端目前未实现 /charts/* 这 6 个端点（AdminUserProfileController 上只有 /{userId}, /behaviors, /orders）。
// 直接调用会触发 404 + axios 拦截器 toast「请求的资源不存在」，造成用户误以为详情页加载失败。
// 修复：跳过网络请求，所有图表数据置为空数组，前端展示空态（不再调用错误接口）。
// 待后端补全这 6 个端点时，恢复下方注释掉的真实调用即可。
async function loadProfileCharts(uid) {
  if (!uid) return
  // 真实实现（后端补全端点后启用）：
  // try {
  //   const [trend, funnel, category, hours, device, channel] = await Promise.all([
  //     getUserProfileSpendTrend(uid),
  //     getUserProfileFunnel(uid),
  //     getUserProfileCategory(uid),
  //     getUserProfileActiveHours(uid),
  //     getUserProfileDevice(uid),
  //     getUserProfileChannel(uid)
  //   ])
  //   spendTrend.value = (trend || []).map(d => ({
  //     month: d.month ? String(d.month).replace(/-\d+$/, '月').replace(/-(\d+)$/, '$1月') : '',
  //     amount: Number(d.amount || 0)
  //   }))
  //   funnelStages.value = (funnel || []).map(s => ({ name: s.name, value: s.count, rate: s.rate }))
  //   categoryPrefs.value = (category || []).map((c, i) => ({
  //     name: c.category, value: c.value, color: CATEGORY_PALETTE[i % CATEGORY_PALETTE.length]
  //   }))
  //   const buckets = [0, 0, 0, 0, 0, 0]
  //   for (const h of (hours || [])) {
  //     const idx = Math.min(5, Math.floor((h.hour || 0) / 4))
  //     buckets[idx] += Number(h.value || 0)
  //   }
  //   activeHours.value = [
  //     { hour: '0-4时', value: buckets[0] }, { hour: '4-8时', value: buckets[1] },
  //     { hour: '8-12时', value: buckets[2] }, { hour: '12-16时', value: buckets[3] },
  //     { hour: '16-20时', value: buckets[4] }, { hour: '20-24时', value: buckets[5] }
  //   ]
  //   deviceDist.value = (device || []).map((d, i) => ({
  //     name: d.device, value: d.value, color: DEVICE_PALETTE[i % DEVICE_PALETTE.length]
  //   }))
  //   channelDist.value = (channel || []).map((c, i) => ({
  //     name: c.channel, value: c.value, color: CHANNEL_PALETTE[i % CHANNEL_PALETTE.length]
  //   }))
  //   userTags.value = buildUserTags(categoryPrefs.value, deviceDist.value, channelDist.value, funnelStages.value)
  // } catch (e) {
  //   console.error('加载画像图表失败:', e)
  // }

  // 当前行为：跳过网络请求，所有图表数据置空 → 模板走空态占位符
  spendTrend.value = []
  funnelStages.value = []
  categoryPrefs.value = []
  activeHours.value = []
  deviceDist.value = []
  channelDist.value = []
  userTags.value = []
}

// 支持从 query 参数 ?id= 自动加载用户画像（用户列表页跳转过来时无需手动搜索）
const route = useRoute()
onMounted(() => {
  const idFromQuery = route.query.id
  if (idFromQuery) {
    searchForm.keyword = String(idFromQuery)
    handleSearch()
  }
})

// 监听路由 id 变化：在用户详情页内切换其他用户时自动重新加载
// 修复：从一个用户详情直接路由到另一个用户时，onMounted 不会重新触发，会卡在旧数据
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
  margin-bottom: 12px;
}

.count-cell {
  font-weight: 600;
  color: var(--text-800);
  font-variant-numeric: tabular-nums;
}
.time-cell { font-size: 12px; color: var(--text-500); }

/* ===== 画像分析区块（设计稿补齐） ===== */
.charts-area {
  margin-top: 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}
/* 双列布局，窄屏降为单列 */
.charts-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}
.chart-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-xs);
  padding: 20px;
}
.chart-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}
.chart-card__head .section-title { margin-bottom: 0; }
/* 示例数据徽标 */
.demo-badge {
  font-size: 11px;
  color: var(--text-400);
  background: var(--background-200);
  border-radius: 999px;
  padding: 2px 8px;
  flex-shrink: 0;
}

/* 消费趋势柱状图 */
.trend-chart {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  height: 200px;
  padding-top: 16px;
  border-bottom: 1px solid var(--border);
}
.trend-chart__group {
  flex: 1;
  min-width: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
}
.trend-chart__value {
  font-size: 10px;
  color: var(--text-400);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  transform: scale(0.9);
  transform-origin: bottom center;
}
.trend-chart__bar {
  width: 70%;
  max-width: 34px;
  border-radius: 4px 4px 0 0;
  background: linear-gradient(180deg, var(--brand-400), var(--brand-500));
  transition: height 0.5s ease;
}
.trend-chart__label {
  font-size: 11px;
  color: var(--text-400);
  padding-top: 6px;
}

/* 用户标签云 */
.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.tag-cloud__item { margin-right: 0; }
.tag-cloud__sub {
  margin-top: 16px;
  font-size: 12px;
  color: var(--text-400);
}

/* 分段条形图 */
.stacked-bar { margin-bottom: 16px; }
.stacked-bar__track {
  display: flex;
  height: 14px;
  border-radius: 999px;
  overflow: hidden;
  background: var(--background-200);
}
.stacked-bar__seg {
  height: 100%;
  transition: width 0.6s ease;
}
.stacked-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 16px;
}
.stacked-legend__item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-600);
}
.stacked-legend__dot {
  width: 10px;
  height: 10px;
  border-radius: 3px;
  flex-shrink: 0;
}

/* 行为漏斗 */
.funnel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.funnel__item {
  display: flex;
  justify-content: center;
}
.funnel__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 8px;
  min-width: 200px;
  background: linear-gradient(90deg, var(--brand-500), var(--brand-300));
  color: #fff;
  font-size: 13px;
}
/* 逐层递减的渐变，体现转化率下降 */
.funnel__item:nth-child(2) .funnel__bar { background: linear-gradient(90deg, var(--brand-400), var(--brand-200)); }
.funnel__item:nth-child(3) .funnel__bar { background: linear-gradient(90deg, var(--brand-300), var(--brand-100)); }
.funnel__item:nth-child(4) .funnel__bar { background: linear-gradient(90deg, var(--brand-200), var(--brand-100)); }
.funnel__name {
  font-weight: 600;
  white-space: nowrap;
}
.funnel__meta {
  font-size: 12px;
  opacity: 0.9;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

/* 活跃时段分布（水平条形图） */
.dist-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.dist-bar:last-child { margin-bottom: 0; }
.dist-bar__label {
  width: 56px;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-600);
  flex-shrink: 0;
}
.dist-bar__track {
  flex: 1;
  height: 10px;
  border-radius: 999px;
  background: var(--background-200);
  overflow: hidden;
}
.dist-bar__fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--brand-300), var(--brand-500));
  transition: width 0.6s ease;
}
.dist-bar__value {
  width: 32px;
  text-align: right;
  font-size: 13px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--text-500);
  flex-shrink: 0;
}

/* 设备 / 渠道分布 */
.sub-dist { margin-bottom: 18px; }
.sub-dist:last-child { margin-bottom: 0; }
.sub-dist__label {
  font-size: 12px;
  color: var(--text-400);
  margin-bottom: 8px;
}

/* 窄屏单列展示 */
@media (max-width: 960px) {
  .charts-row { grid-template-columns: 1fr; }
}
</style>
