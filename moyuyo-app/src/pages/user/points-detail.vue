<template>
  <view class="page">
    <!-- 顶部摘要卡：余额 + 概览统计 -->
    <view class="hero">
      <view class="hero-balance">
        <text class="hero-label">当前积分</text>
        <view class="hero-num-row">
          <text class="hero-num">{{ balance }}</text>
          <text class="hero-unit">分</text>
        </view>
      </view>
      <view class="hero-stats">
        <view class="hero-stat">
          <text class="hero-stat-val">{{ stats.income }}</text>
          <text class="hero-stat-label">累计获得</text>
        </view>
        <view class="hero-stat-divider" />
        <view class="hero-stat">
          <text class="hero-stat-val">{{ stats.spent }}</text>
          <text class="hero-stat-label">累计使用</text>
        </view>
      </view>
    </view>

    <!-- 列表区 -->
    <view class="list">
      <!-- 加载中 -->
      <view v-if="loading && list.length === 0" class="state-block">
        <text class="state-text">加载中…</text>
      </view>

      <!-- 空态 -->
      <view v-else-if="list.length === 0" class="empty">
        <text class="luc luc-inbox empty-icon" />
        <text class="empty-text">暂无积分记录</text>
      </view>

      <!-- 按月分组的流水列表 -->
      <template v-else>
        <view v-for="group in groupedList" :key="group.month" class="group">
          <view class="group-head">
            <text class="group-title">{{ group.month }}</text>
            <text class="group-sub">共 {{ group.items.length }} 笔</text>
          </view>
          <view class="group-list">
            <view
              v-for="(it, idx) in group.items"
              :key="`${group.month}-${idx}-${it.createdAt}`"
              class="log-row"
            >
              <view class="log-icon-wrap" :style="{ background: typeMeta(it.type).bg }">
                <text
                  class="luc"
                  :class="typeMeta(it.type).icon"
                  :style="{ color: typeMeta(it.type).color, fontSize: '20px' }"
                />
              </view>
              <view class="log-body">
                <view class="log-row1">
                  <text class="log-title">{{ typeMeta(it.type).title }}</text>
                  <text class="log-change" :class="it.changeValue >= 0 ? 'plus' : 'minus'">
                    {{ it.changeValue >= 0 ? '+' : '' }}{{ it.changeValue }}
                  </text>
                </view>
                <!--
                  视觉规范：获得积分（changeValue >= 0）显示绿色，消耗积分显示红色。
                  前端文案 `+5/-100` 已能区分正负，颜色再次强化让用户 1 秒看懂。
                -->
                <view class="log-row2">
                  <text v-if="it.remark" class="log-remark">{{ it.remark }}</text>
                  <text class="log-time">{{ formatTime(it.createdAt) }}</text>
                </view>
              </view>
            </view>
          </view>
        </view>
      </template>

      <!-- 加载更多 -->
      <view v-if="hasMore && list.length > 0" class="loadmore" @tap="loadMore">
        <text class="loadmore-text">{{ loadingMore ? '加载中…' : '加载更多' }}</text>
      </view>
      <view v-else-if="list.length > 0" class="loadmore">
        <text class="loadmore-text">— 已经到底了 —</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getPointsLog, getPointsBalance } from '@/api/points'

const balance = ref(0)
const stats = ref({ income: 0, spent: 0 })
const list = ref([])
const page = ref(1)
const pageSize = 20
const total = ref(0)
const loading = ref(false)
const loadingMore = ref(false)

const hasMore = computed(() => list.value.length < total.value)

// 按月分组（YYYY-MM）
const groupedList = computed(() => {
  const map = new Map()
  for (const it of list.value) {
    const month = (it.createdAt || '').slice(0, 7)
    if (!map.has(month)) map.set(month, [])
    map.get(month).push(it)
  }
  // 保持月份倒序（接口已按 created_at DESC 排），无需二次排序
  return Array.from(map.entries()).map(([month, items]) => ({ month, items }))
})

// 类型映射：后端 mo_points_log.type 枚举
const TYPE_META = {
  CHECKIN: { title: '每日签到', icon: 'luc-calendar-check', bg: '#e8f2ff', color: '#007aff' },
  ORDER: { title: '购物返积分', icon: 'luc-shopping-bag', bg: '#e9f9ee', color: '#34c759' },
  REFUND: { title: '退款扣减', icon: 'luc-undo-2', bg: '#ffecea', color: '#ff3b30' },
  EXCHANGE: { title: '积分兑换', icon: 'luc-gift', bg: '#f3e8ff', color: '#af52de' },
  EXPIRE: { title: '积分过期', icon: 'luc-clock', bg: '#fff4e5', color: '#ff9500' },
  INVITE: { title: '邀请奖励', icon: 'luc-user-plus', bg: '#e9f9ee', color: '#34c759' },
  ACTIVITY: { title: '活动奖励', icon: 'luc-sparkles', bg: '#fff4e5', color: '#ff9500' },
  ADJUST: { title: '管理员调整', icon: 'luc-settings', bg: '#e8f2ff', color: '#0064d6' },
  REVIEW: { title: '评价奖励', icon: 'luc-message-square', bg: '#e9f9ee', color: '#34c759' },
  MISSION: { title: '任务奖励', icon: 'luc-target', bg: '#f3e8ff', color: '#af52de' },
  SIGN_IN: { title: '每日签到', icon: 'luc-calendar-check', bg: '#e8f2ff', color: '#007aff' },
}
function typeMeta(type) {
  return (
    TYPE_META[type] || {
      title: type || '其他变动',
      icon: 'luc-coins',
      bg: '#f2f2f7',
      color: '#8e8e93',
    }
  )
}

async function loadBalance() {
  try {
    const v = await getPointsBalance()
    balance.value = v ?? 0
  } catch (e) {
    console.warn('[points-detail] load balance failed', e)
  }
}

async function loadList(reset = false) {
  if (reset) {
    page.value = 1
    list.value = []
  }
  const setLoading = reset
    ? (v) => {
        loading.value = v
      }
    : (v) => {
        loadingMore.value = v
      }
  setLoading(true)
  try {
    const res = await getPointsLog({ page: page.value, size: pageSize })
    const records = res?.records || []
    list.value = reset ? records : [...list.value, ...records]
    total.value = res?.total ?? records.length
    // 累计获得 / 累计使用：第一页拉够 100 条用于粗略统计（接口未提供汇总）
    if (reset && records.length > 0) {
      const sum = list.value.reduce(
        (acc, x) => {
          const v = Number(x.changeValue || 0)
          if (v >= 0) acc.income += v
          else acc.spent += -v
          return acc
        },
        { income: 0, spent: 0 },
      )
      stats.value = sum
    }
  } catch (e) {
    console.warn('[points-detail] load list failed', e)
  } finally {
    setLoading(false)
  }
}

function loadMore() {
  if (loadingMore.value || !hasMore.value) return
  page.value += 1
  loadList(false)
}

function formatTime(iso) {
  if (!iso) return ''
  // 兼容 "yyyy-MM-ddTHH:mm:ss"
  const s = iso.replace(' ', 'T').slice(0, 16)
  return s.replace('T', ' ')
}

onMounted(async () => {
  loading.value = true
  await Promise.all([loadBalance(), loadList(true)])
  loading.value = false
})
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: var(--color-background, #f2f2f7);
  padding-bottom: 64rpx;
}

/* ========== 顶部摘要 ========== */
.hero {
  margin: 32rpx 32rpx 24rpx;
  padding: 32rpx;
  background: linear-gradient(135deg, #0064d6 0%, #007aff 40%, #2e8dff 100%);
  border-radius: 28rpx;
  color: #ffffff;
  box-shadow: 0 8rpx 24rpx rgba(0, 122, 255, 0.25);
}
.hero-balance {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  margin-bottom: 28rpx;
}
.hero-label {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.85);
  letter-spacing: 1rpx;
}
.hero-num-row {
  display: flex;
  align-items: baseline;
  gap: 8rpx;
}
.hero-num {
  font-size: 72rpx;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  letter-spacing: 1rpx;
}
.hero-unit {
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.85);
}

.hero-stats {
  display: flex;
  align-items: center;
  padding-top: 20rpx;
  border-top: 1rpx solid rgba(255, 255, 255, 0.18);
}
.hero-stat {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}
.hero-stat-val {
  font-size: 28rpx;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}
.hero-stat-label {
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.8);
}
.hero-stat-divider {
  width: 1rpx;
  height: 56rpx;
  background: rgba(255, 255, 255, 0.22);
  margin: 0 16rpx;
}

/* ========== 列表 ========== */
.list {
  padding: 0 32rpx;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}
.state-block {
  padding: 120rpx 0;
  text-align: center;
}
.state-text {
  font-size: 28rpx;
  color: #8e8e93;
}
.empty {
  padding: 120rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
}
.empty-icon {
  font-size: 96rpx;
  color: #c7c7cc;
}
.empty-text {
  font-size: 26rpx;
  color: #8e8e93;
}

/* 月份分组 */
.group {
  background: var(--color-surface, #ffffff);
  border-radius: 24rpx;
  overflow: hidden;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
}
.group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 28rpx;
  background: var(--color-background, #f2f2f7);
}
.group-title {
  font-size: 24rpx;
  font-weight: 700;
  color: var(--color-text, #1d1d1f);
  font-variant-numeric: tabular-nums;
}
.group-sub {
  font-size: 20rpx;
  color: var(--color-text-tertiary, #8e8e93);
}
.group-list {
  padding: 0 28rpx;
}
.log-row {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 24rpx 0;
  border-bottom: 1rpx solid var(--color-divider, #f2f2f7);
}
.log-row:last-child {
  border-bottom: none;
}
.log-icon-wrap {
  width: 64rpx;
  height: 64rpx;
  border-radius: 18rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.log-body {
  flex: 1;
  min-width: 0;
}
.log-row1 {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 4rpx;
}
.log-title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text, #1d1d1f);
}
.log-change {
  font-size: 30rpx;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}
.log-change.plus {
  color: #34c759;
} /* 获得积分（正向）显示绿色 */
.log-change.minus {
  color: #ff3b30;
} /* 消耗积分（负向）显示红色 */
.log-row2 {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12rpx;
}
.log-remark {
  flex: 1;
  font-size: 22rpx;
  color: var(--color-text-tertiary, #8e8e93);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}
.log-time {
  font-size: 22rpx;
  color: var(--color-text-tertiary, #8e8e93);
  font-variant-numeric: tabular-nums;
  flex-shrink: 0;
}

/* ========== 加载更多 ========== */
.loadmore {
  padding: 24rpx 0;
  text-align: center;
}
.loadmore-text {
  font-size: 24rpx;
  color: var(--color-text-tertiary, #8e8e93);
}
</style>
