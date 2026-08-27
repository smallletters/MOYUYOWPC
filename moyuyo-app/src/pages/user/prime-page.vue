<template>
  <view class="prime-page">
    <!-- 顶部品牌区（蓝色渐变） -->
    <view class="prime-header">
      <view class="nav-row">
        <view class="back-btn" @tap="goBack"><text class="luc luc-arrow-left" /></view>
        <text class="nav-title">MOYUYO Prime</text>
      </view>
      <view class="brand-row">
        <view class="brand-mark">
          <!-- Prime 星形 logo -->
          <text class="luc luc-crown brand-icon" />
        </view>
        <view class="brand-text">
          <text class="brand-name">MOYUYO Prime</text>
          <text class="brand-sub">专属于你的高端宠物生活</text>
        </view>
      </view>
    </view>

    <!-- 内容容器 -->
    <view class="content">
      <!-- 加载中 -->
      <view v-if="loading" class="state-block">
        <text class="state-text">加载中…</text>
      </view>

      <!-- 未开通视图 -->
      <view v-else-if="!primeStatus || !primeStatus.active">
        <!-- 价格对比卡 -->
        <view class="plan-card">
          <view class="plan-switch">
            <view
              v-for="p in plans"
              :key="p.code"
              class="plan-option"
              :class="{ selected: selectedPlanCode === p.code }"
              @tap="selectPlan(p.code)"
            >
              <view v-if="p.recommend" class="recommend-tag-wrap">
                <text class="recommend-tag">推荐</text>
              </view>
              <view class="plan-option-row1">
                <text class="plan-option-name">{{ p.name }}</text>
                <text v-if="p.code === 'YEARLY'" class="save-badge">省 ¥20.88</text>
              </view>
              <view class="plan-option-row2">
                <text class="plan-price">¥{{ p.price }}</text>
                <text class="plan-unit">/ {{ p.code === 'MONTHLY' ? '月' : '年' }}</text>
              </view>
              <text v-if="p.code === 'YEARLY'" class="plan-monthly-hint">
                约 ¥{{ (p.price / 12).toFixed(2) }}/月
              </text>
            </view>
          </view>

          <!-- 立即开通按钮 -->
          <view class="activate-btn" @tap="onActivate">
            <text class="luc luc-crown activate-icon" />
            <text class="activate-text">立即开通</text>
          </view>

          <!-- 30天免费试用提示 -->
          <view class="trial-row">
            <text class="luc luc-gift trial-icon" />
            <text class="trial-text">30天免费试用，随时可取消</text>
          </view>

          <!-- 合规提示 -->
          <view class="legal">
            <text class="legal-text">
              开通即表示你同意自动续费服务。试用期内免费，试用结束后将按所选方案自动扣费。你可以在「我的
              > 订阅管理」中随时取消自动续费，取消后服务将持续至当前周期结束。
            </text>
          </view>
        </view>

        <!-- 权益列表 -->
        <view class="benefits-card">
          <view class="benefits-head">
            <text class="benefits-title">会员专属权益</text>
            <text class="benefits-sub">开通 Prime 即享全部权益</text>
          </view>
          <view v-for="(b, i) in benefitList" :key="i" class="benefit-row">
            <view class="benefit-icon-wrap" :style="{ background: b.iconBg }">
              <text
                class="luc"
                :class="b.icon"
                :style="{ color: b.iconColor, fontSize: '18px' }"
              />
            </view>
            <view class="benefit-content">
              <text class="benefit-title">{{ b.title }}</text>
              <text class="benefit-desc">{{ b.desc }}</text>
            </view>
            <text class="luc luc-chevron-right benefit-arrow" />
          </view>
        </view>
      </view>

      <!-- 已开通视图 -->
      <view v-else>
        <!-- 状态卡 -->
        <view class="status-card">
          <view class="status-head">
            <view class="status-mark">
              <text class="luc luc-crown status-mark-icon" />
            </view>
            <view class="status-info">
              <view class="status-name-row">
                <text class="status-name">MOYUYO Prime 会员</text>
                <text class="status-tag">已开通</text>
              </view>
              <text class="status-plan">{{ primeStatus.planName }}方案 · 自动续费已开启</text>
            </view>
          </view>
          <view class="status-expire-row">
            <view class="status-expire-left">
              <text class="luc luc-calendar status-cal-icon" />
              <text class="status-expire-label">到期时间</text>
            </view>
            <text class="status-expire-val">{{ formatDate(primeStatus.expireAt) }}</text>
          </view>
        </view>

        <!-- 本月权益统计 -->
        <view class="stats-card">
          <text class="stats-title">本月已享权益</text>
          <view class="stats-grid">
            <view class="stat-card">
              <view class="stat-icon-wrap" style="background: #e8f2ff">
                <text class="luc luc-truck stat-icon" style="color: #007aff" />
              </view>
              <text class="stat-num">12</text>
              <text class="stat-label">免运费次数</text>
            </view>
            <view class="stat-card">
              <view class="stat-icon-wrap" style="background: #e9f9ee">
                <text class="luc luc-piggy-bank stat-icon" style="color: #34c759" />
              </view>
              <text class="stat-num">¥86</text>
              <text class="stat-label">累计节省</text>
            </view>
            <view class="stat-card">
              <view class="stat-icon-wrap" style="background: #e8f2ff">
                <text class="luc luc-zap stat-icon" style="color: #0064d6" />
              </view>
              <text class="stat-num">8</text>
              <text class="stat-label">优先发货单</text>
            </view>
          </view>
        </view>

        <!-- 管理订阅 -->
        <view class="manage-card">
          <view class="manage-row" @tap="onManageSubscription">
            <view class="manage-left">
              <text class="luc luc-credit-card manage-icon" />
              <text class="manage-label">管理订阅</text>
            </view>
            <text class="luc luc-chevron-right manage-arrow" />
          </view>
          <view class="manage-row" @tap="onViewBills">
            <view class="manage-left">
              <text class="luc luc-receipt manage-icon" />
              <text class="manage-label">查看账单</text>
            </view>
            <text class="luc luc-chevron-right manage-arrow" />
          </view>
          <view class="manage-row" @tap="onViewPoints">
            <view class="manage-left">
              <text class="luc luc-gift manage-icon" />
              <text class="manage-label">赠送积分</text>
            </view>
            <view class="manage-right">
              <text class="manage-meta">本月 $10</text>
              <text class="luc luc-chevron-right manage-arrow" />
            </view>
          </view>
        </view>

        <!-- 取消订阅（放底部，红字按钮） -->
        <view class="cancel-btn" @tap="onCancel">
          <text class="cancel-text">取消订阅</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { listPrimePlans, getPrimeStatus, subscribePrime, cancelPrime } from '@/api/marketing'

const plans = ref([])
const selectedPlanCode = ref('YEARLY') // 默认选中年付推荐方案
const primeStatus = ref(null) // 当前用户 Prime 状态；null = 未开通
const loading = ref(false)
const submitting = ref(false)

// 设计稿中的 9 项权益（图标 / 标题 / 描述 / 配色）
const benefitList = [
  {
    icon: 'luc-truck',
    title: '全场免运费',
    desc: '无门槛，全品类包邮',
    iconBg: '#e8f2ff',
    iconColor: '#007aff',
  },
  {
    icon: 'luc-badge-percent',
    title: '专属会员价',
    desc: '额外 5-10% off 折扣',
    iconBg: '#e9f9ee',
    iconColor: '#34c759',
  },
  {
    icon: 'luc-zap',
    title: '优先发货',
    desc: '24小时内极速发货',
    iconBg: '#e8f2ff',
    iconColor: '#0064d6',
  },
  {
    icon: 'luc-refresh-cw',
    title: '免费退换货',
    desc: '退货运费全免',
    iconBg: '#e9f9ee',
    iconColor: '#34c759',
  },
  {
    icon: 'luc-headphones',
    title: '专属客服',
    desc: '1v1 优先响应',
    iconBg: '#e8f2ff',
    iconColor: '#007aff',
  },
  {
    icon: 'luc-flame',
    title: 'Prime Day 专属大促',
    desc: '会员限定大促专场',
    iconBg: '#ffecea',
    iconColor: '#ff3b30',
  },
  {
    icon: 'luc-coins',
    title: '每月赠送 $10 积分',
    desc: '自动到账，购物抵扣',
    iconBg: '#e8f2ff',
    iconColor: '#0064d6',
  },
  {
    icon: 'luc-sparkles',
    title: '新品优先购',
    desc: '抢先体验新品',
    iconBg: '#e9f9ee',
    iconColor: '#34c759',
  },
  {
    icon: 'luc-lock-open',
    title: 'Pet Hub 全部场景解锁',
    desc: '健康、社交、护理全场景',
    iconBg: '#e8f2ff',
    iconColor: '#007aff',
  },
]

const isActivated = computed(() => primeStatus.value && primeStatus.value.active === true)

function selectPlan(code) {
  selectedPlanCode.value = code
}

async function loadAll() {
  loading.value = true
  try {
    // 并行拉取套餐列表 + 状态（后者鉴权要求登录）
    const tasks = [listPrimePlans().catch(() => [])]
    tasks.push(getPrimeStatus().catch(() => ({ active: false })))
    const [list, status] = await Promise.all(tasks)
    plans.value = list || []
    primeStatus.value = status || { active: false }
    // 若用户当前已有 ACTIVE 订阅，默认选中对应套餐
    if (isActivated.value && primeStatus.value.plan) {
      const code = primeStatus.value.plan === 'ANNUAL' ? 'YEARLY' : 'MONTHLY'
      selectedPlanCode.value = code
    }
  } catch (e) {
    console.warn('[prime-page] load failed', e)
  } finally {
    loading.value = false
  }
}

async function onActivate() {
  if (submitting.value) return
  const selected = plans.value.find((p) => p.code === selectedPlanCode.value)
  if (!selected) {
    uni.showToast({ title: '套餐信息加载中', icon: 'none' })
    return
  }
  uni.showModal({
    title: '确认开通',
    content: `开通 ${selected.name} 方案 ¥${selected.price}（dev 环境直接激活）`,
    success: async (res) => {
      if (!res.confirm) return
      submitting.value = true
      try {
        const result = await subscribePrime(selected.code, 'STRIPE')
        primeStatus.value = result
        uni.showToast({ title: '已开通', icon: 'success' })
      } catch (e) {
        console.warn('[prime-page] subscribe failed', e)
        uni.showToast({ title: e?.message || '开通失败', icon: 'none' })
      } finally {
        submitting.value = false
      }
    },
  })
}

async function onCancel() {
  uni.showModal({
    title: '确认取消订阅',
    content: '取消后当前周期仍可使用，到期后将不再自动续费',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await cancelPrime()
        await loadAll() // 重新拉取状态，未开通视图会自动恢复
        uni.showToast({ title: '已取消', icon: 'none' })
      } catch (e) {
        uni.showToast({ title: '取消失败', icon: 'none' })
      }
    },
  })
}

function formatDate(iso) {
  if (!iso) return '-'
  // 兼容 "yyyy-MM-ddTHH:mm:ss" 与 "yyyy-MM-dd HH:mm:ss"
  const d = new Date(iso.replace(' ', 'T'))
  if (isNaN(d.getTime())) return iso
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`
}

function goBack() {
  uni.navigateBack({ delta: 1, fail: () => uni.switchTab({ url: '/pages/tabbar/user' }) })
}

function onManageSubscription() {
  uni.navigateTo({ url: '/pages/user/subscription-manage' })
}

function onViewBills() {
  uni.showToast({ title: '账单详情开发中', icon: 'none' })
}

function onViewPoints() {
  uni.navigateTo({ url: '/pages/user/points-detail' })
}

onMounted(loadAll)
</script>

<style lang="scss" scoped>
.prime-page {
  min-height: 100vh;
  background: #f2f2f7;
  padding-bottom: 80rpx;
}

/* ============ 顶部品牌区 ============ */
.prime-header {
  position: relative;
  padding: 0 32rpx 56rpx;
  background:
    radial-gradient(120% 80% at 80% 0%, rgba(0, 122, 255, 0.18) 0%, transparent 55%),
    linear-gradient(135deg, #0064d6 0%, #007aff 40%, #2e8dff 100%);
  color: #ffffff;
}
.nav-row {
  display: flex;
  align-items: center;
  height: 88rpx;
}
.back-btn {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.back-btn .luc {
  font-size: 40rpx;
  color: #ffffff;
}
.nav-title {
  flex: 1;
  text-align: center;
  font-size: 28rpx;
  font-weight: 500;
  color: #ffffff;
}
.brand-row {
  display: flex;
  align-items: center;
  gap: 24rpx;
  margin-top: 8rpx;
}
.brand-mark {
  width: 96rpx;
  height: 96rpx;
  border-radius: 28rpx;
  background: rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1rpx solid rgba(255, 255, 255, 0.18);
}
.brand-icon {
  font-size: 52rpx;
  color: #ffffff;
}
.brand-text {
  flex: 1;
}
.brand-name {
  display: block;
  font-size: 44rpx;
  font-weight: 800;
  letter-spacing: 1rpx;
}
.brand-sub {
  display: block;
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.85);
  margin-top: 6rpx;
}

/* ============ 通用卡片 ============ */
.content {
  padding: -36rpx 32rpx 0;
  margin-top: -36rpx;
}
.plan-card,
.benefits-card,
.status-card,
.stats-card,
.manage-card {
  background: #ffffff;
  border-radius: 24rpx;
  padding: 32rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.06);
  margin-bottom: 24rpx;
}

.state-block {
  padding: 120rpx 0;
  text-align: center;
}
.state-text {
  font-size: 28rpx;
  color: #8e8e93;
}

/* ============ 未开通：套餐切换 ============ */
.plan-switch {
  display: flex;
  gap: 16rpx;
  margin-bottom: 36rpx;
}
.plan-option {
  flex: 1;
  position: relative;
  padding: 28rpx 24rpx;
  border: 2rpx solid #e5e5ea;
  border-radius: 24rpx;
  transition:
    border-color 0.2s ease,
    background-color 0.2s ease;
}
.plan-option.selected {
  border-color: #007aff;
  background: #e8f2ff;
}
.recommend-tag-wrap {
  position: absolute;
  top: -16rpx;
  right: 20rpx;
}
.recommend-tag {
  display: inline-block;
  padding: 4rpx 12rpx;
  border-radius: 999px;
  background: #007aff;
  color: #ffffff;
  font-size: 18rpx;
  font-weight: 700;
  letter-spacing: 1rpx;
}
.plan-option-row1 {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12rpx;
}
.plan-option-name {
  font-size: 28rpx;
  font-weight: 700;
  color: #1d1d1f;
}
.save-badge {
  display: inline-flex;
  align-items: center;
  padding: 4rpx 10rpx;
  border-radius: 999px;
  background: #e9f9ee;
  color: #34c759;
  font-size: 18rpx;
  font-weight: 700;
}
.plan-option-row2 {
  display: flex;
  align-items: baseline;
  gap: 4rpx;
}
.plan-price {
  font-size: 44rpx;
  font-weight: 800;
  color: #1d1d1f;
}
.plan-unit {
  font-size: 22rpx;
  color: #8e8e93;
}
.plan-monthly-hint {
  display: block;
  font-size: 22rpx;
  color: #8e8e93;
  margin-top: 4rpx;
}

/* ============ 未开通：开通按钮 ============ */
.activate-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  height: 112rpx;
  border-radius: 32rpx;
  background: #007aff;
  color: #ffffff;
  font-size: 32rpx;
  font-weight: 700;
}
.activate-btn:active {
  opacity: 0.85;
}
.activate-icon {
  font-size: 36rpx;
}

/* 30天试用提示 */
.trial-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  margin-top: 20rpx;
}
.trial-icon {
  font-size: 26rpx;
  color: #007aff;
}
.trial-text {
  font-size: 22rpx;
  color: #007aff;
  font-weight: 500;
}

/* 合规文案 */
.legal {
  margin-top: 24rpx;
  padding-top: 20rpx;
  border-top: 1rpx solid #f2f2f7;
}
.legal-text {
  display: block;
  font-size: 20rpx;
  line-height: 1.55;
  color: #8e8e93;
}

/* ============ 权益列表 ============ */
.benefits-head {
  margin-bottom: 12rpx;
}
.benefits-title {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
  color: #1d1d1f;
}
.benefits-sub {
  display: block;
  font-size: 22rpx;
  color: #8e8e93;
  margin-top: 4rpx;
  margin-bottom: 8rpx;
}
.benefit-row {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 24rpx 0;
  border-bottom: 1rpx solid #f2f2f7;
}
.benefit-row:last-child {
  border-bottom: none;
}
.benefit-icon-wrap {
  width: 64rpx;
  height: 64rpx;
  border-radius: 18rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.benefit-content {
  flex: 1;
  min-width: 0;
}
.benefit-title {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: #1d1d1f;
}
.benefit-desc {
  display: block;
  font-size: 22rpx;
  color: #8e8e93;
  margin-top: 4rpx;
}
.benefit-arrow {
  font-size: 32rpx;
  color: #c7c7cc;
}

/* ============ 已开通：状态卡 ============ */
.status-head {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin-bottom: 24rpx;
}
.status-mark {
  width: 88rpx;
  height: 88rpx;
  border-radius: 24rpx;
  background: rgba(0, 122, 255, 0.12);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.status-mark-icon {
  font-size: 44rpx;
  color: #007aff;
}
.status-info {
  flex: 1;
}
.status-name-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}
.status-name {
  font-size: 30rpx;
  font-weight: 700;
  color: #1d1d1f;
}
.status-tag {
  display: inline-flex;
  align-items: center;
  padding: 4rpx 12rpx;
  border-radius: 999px;
  background: #e9f9ee;
  color: #34c759;
  font-size: 20rpx;
  font-weight: 700;
}
.status-plan {
  display: block;
  font-size: 24rpx;
  color: #8e8e93;
  margin-top: 8rpx;
}
.status-expire-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 28rpx;
  border-radius: 24rpx;
  background: #f2f2f7;
}
.status-expire-left {
  display: flex;
  align-items: center;
  gap: 8rpx;
}
.status-cal-icon {
  font-size: 28rpx;
  color: #8e8e93;
}
.status-expire-label {
  font-size: 26rpx;
  color: #1d1d1f;
}
.status-expire-val {
  font-size: 28rpx;
  font-weight: 700;
  color: #1d1d1f;
}

/* ============ 已开通：权益统计 ============ */
.stats-title {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
  color: #1d1d1f;
  margin-bottom: 24rpx;
}
.stats-grid {
  display: flex;
  gap: 16rpx;
}
.stat-card {
  flex: 1;
  padding: 24rpx 16rpx;
  border-radius: 24rpx;
  background: #f2f2f7;
  text-align: center;
}
.stat-icon-wrap {
  width: 56rpx;
  height: 56rpx;
  border-radius: 16rpx;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12rpx;
}
.stat-icon {
  font-size: 30rpx;
}
.stat-num {
  display: block;
  font-size: 36rpx;
  font-weight: 800;
  color: #1d1d1f;
  font-variant-numeric: tabular-nums;
}
.stat-label {
  display: block;
  font-size: 22rpx;
  color: #8e8e93;
  margin-top: 4rpx;
}

/* ============ 已开通：管理订阅 ============ */
.manage-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 0;
  border-bottom: 1rpx solid #f2f2f7;
}
.manage-row:first-child {
  padding-top: 0;
}
.manage-row:last-child {
  border-bottom: none;
  padding-bottom: 0;
}
.manage-left {
  display: flex;
  align-items: center;
  gap: 20rpx;
}
.manage-icon {
  font-size: 40rpx;
  color: #007aff;
}
.manage-label {
  font-size: 28rpx;
  font-weight: 500;
  color: #1d1d1f;
}
.manage-right {
  display: flex;
  align-items: center;
  gap: 12rpx;
}
.manage-meta {
  font-size: 26rpx;
  font-weight: 700;
  color: #007aff;
}
.manage-arrow {
  font-size: 30rpx;
  color: #c7c7cc;
}

/* ============ 取消按钮 ============ */
.cancel-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 88rpx;
  border-radius: 999px;
  background: #ffffff;
  border: 1rpx solid #ffecea;
  margin-top: 16rpx;
}
.cancel-text {
  font-size: 28rpx;
  font-weight: 500;
  color: #ff3b30;
}
</style>
