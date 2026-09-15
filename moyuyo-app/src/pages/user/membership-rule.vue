<template>
  <view class="rule-page">
    <!-- 顶部摘要卡：当前等级 + 积分 -->
    <view class="hero-card">
      <view class="hero-icon-wrap">
        <text class="luc luc-book-open hero-icon" />
      </view>
      <view class="hero-info">
        <text class="hero-title">{{ t('membershipRule.heroTitle') }}</text>
        <text class="hero-sub">{{ t('membershipRule.heroSub') }}</text>
      </view>
    </view>

    <!-- 内容区 -->
    <view class="content">
      <!-- 1. 等级阶梯 -->
      <view class="section-card">
        <view class="section-head">
          <text class="section-title">{{ t('membershipRule.ladderTitle') }}</text>
          <text class="section-tag">{{ t('membershipRule.ladderLevels') }}</text>
        </view>
        <view class="ladder">
          <view
            v-for="(lv, idx) in levels"
            :key="lv.code"
            class="ladder-row"
            :class="`ladder-${lv.code}`"
          >
            <view class="ladder-badge">
              <text class="ladder-badge-num">{{ idx + 1 }}</text>
            </view>
            <view class="ladder-body">
              <view class="ladder-head">
                <text class="ladder-name">{{ lv.name }}</text>
                <text class="ladder-threshold">
                  {{ t('membershipRule.thresholdFrom', { points: lv.threshold }) }}
                </text>
              </view>
              <text class="ladder-desc">{{ t(lv.descKey) }}</text>
              <text class="ladder-rate">
                {{ t('membershipRule.rateMultiple', { rate: lv.rate }) }}
              </text>
            </view>
          </view>
        </view>
      </view>

      <!-- 2. 积分获取 -->
      <view class="section-card">
        <view class="section-head">
          <text class="section-title">{{ t('membershipRule.earnTitle') }}</text>
        </view>
        <view class="rule-list">
          <view v-for="r in earnRules" :key="r.titleKey" class="rule-row">
            <view class="rule-icon-wrap" :style="{ background: r.bg }">
              <text class="luc" :class="r.icon" :style="{ color: r.color, fontSize: '20px' }" />
            </view>
            <view class="rule-body">
              <view class="rule-row-head">
                <text class="rule-title">{{ t(r.titleKey) }}</text>
                <text class="rule-value">+{{ t(r.valueKey) }}</text>
              </view>
              <text class="rule-desc">{{ t(r.descKey) }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 3. 积分使用 -->
      <view class="section-card">
        <view class="section-head">
          <text class="section-title">{{ t('membershipRule.useTitle') }}</text>
        </view>
        <view class="rule-list">
          <view v-for="r in useRules" :key="r.titleKey" class="rule-row">
            <view class="rule-icon-wrap" :style="{ background: r.bg }">
              <text class="luc" :class="r.icon" :style="{ color: r.color, fontSize: '20px' }" />
            </view>
            <view class="rule-body">
              <view class="rule-row-head">
                <text class="rule-title">{{ t(r.titleKey) }}</text>
                <text class="rule-value">-{{ t(r.valueKey) }}</text>
              </view>
              <text class="rule-desc">{{ t(r.descKey) }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 4. 重要条款 -->
      <view class="section-card">
        <view class="section-head">
          <text class="section-title">{{ t('membershipRule.termsTitle') }}</text>
        </view>
        <view class="terms">
          <view v-for="(termKey, i) in terms" :key="termKey" class="term-row">
            <text class="term-num">{{ i + 1 }}.</text>
            <text class="term-text">{{ t(termKey) }}</text>
          </view>
        </view>
      </view>

      <!-- 温馨提示 -->
      <view class="tips">
        <text class="tips-text">{{ t('membershipRule.tips') }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { i18n } from '@/i18n'
import { usePageTitle } from '@/utils/i18nPageMixin'
usePageTitle('pageTitle.userMembershipRule')

// 轻量翻译函数（响应 localeVersion 变化，刷新依赖本地化的模板）
const localeVersion = ref(0)
let _unsubLocale = null
function t(key, params) {
  void localeVersion.value // 触发依赖追踪
  return i18n.t(key, params)
}

// 等级阶梯（与后端 listLevels 字段对齐）
const levels = [
  { code: 'L1', name: 'Member', threshold: 0, descKey: 'membershipRule.levelDesc.L1', rate: 1.0 },
  { code: 'L2', name: 'Silver', threshold: 500, descKey: 'membershipRule.levelDesc.L2', rate: 1.1 },
  { code: 'L3', name: 'Gold', threshold: 2000, descKey: 'membershipRule.levelDesc.L3', rate: 1.2 },
  {
    code: 'L4',
    name: 'Platinum',
    threshold: 8000,
    descKey: 'membershipRule.levelDesc.L4',
    rate: 1.5,
  },
  {
    code: 'L5',
    name: 'Black',
    threshold: 25000,
    descKey: 'membershipRule.levelDesc.L5',
    rate: 2.0,
  },
]

// 积分获取规则（文案走 i18n，key 指向 membershipRule.earn.*）
const earnRules = [
  {
    titleKey: 'membershipRule.earn.checkinTitle',
    valueKey: 'membershipRule.earn.checkinValue',
    descKey: 'membershipRule.earn.checkinDesc',
    icon: 'luc-calendar-check',
    bg: '#e8f2ff',
    color: '#007aff',
  },
  {
    titleKey: 'membershipRule.earn.purchaseTitle',
    valueKey: 'membershipRule.earn.purchaseValue',
    descKey: 'membershipRule.earn.purchaseDesc',
    icon: 'luc-shopping-bag',
    bg: '#e9f9ee',
    color: '#34c759',
  },
  {
    titleKey: 'membershipRule.earn.orderTitle',
    valueKey: 'membershipRule.earn.orderValue',
    descKey: 'membershipRule.earn.orderDesc',
    icon: 'luc-package-check',
    bg: '#e8f2ff',
    color: '#0064d6',
  },
  {
    titleKey: 'membershipRule.earn.reviewTitle',
    valueKey: 'membershipRule.earn.reviewValue',
    descKey: 'membershipRule.earn.reviewDesc',
    icon: 'luc-message-square',
    bg: '#fff4e5',
    color: '#ff9500',
  },
  {
    titleKey: 'membershipRule.earn.inviteTitle',
    valueKey: 'membershipRule.earn.inviteValue',
    descKey: 'membershipRule.earn.inviteDesc',
    icon: 'luc-user-plus',
    bg: '#e9f9ee',
    color: '#34c759',
  },
  {
    titleKey: 'membershipRule.earn.missionTitle',
    valueKey: 'membershipRule.earn.missionValue',
    descKey: 'membershipRule.earn.missionDesc',
    icon: 'luc-target',
    bg: '#f3e8ff',
    color: '#af52de',
  },
]

// 积分使用规则（文案走 i18n，key 指向 membershipRule.use.*）
const useRules = [
  {
    titleKey: 'membershipRule.use.deductTitle',
    valueKey: 'membershipRule.use.deductValue',
    descKey: 'membershipRule.use.deductDesc',
    icon: 'luc-coins',
    bg: '#ffecea',
    color: '#ff3b30',
  },
  {
    titleKey: 'membershipRule.use.redeemTitle',
    valueKey: 'membershipRule.use.redeemValue',
    descKey: 'membershipRule.use.redeemDesc',
    icon: 'luc-gift',
    bg: '#e8f2ff',
    color: '#007aff',
  },
  {
    titleKey: 'membershipRule.use.makeupTitle',
    valueKey: 'membershipRule.use.makeupValue',
    descKey: 'membershipRule.use.makeupDesc',
    icon: 'luc-calendar-off',
    bg: '#fff4e5',
    color: '#ff9500',
  },
]

// 重要条款（存 i18n key，按顺序渲染）
const terms = [
  'membershipRule.term1',
  'membershipRule.term2',
  'membershipRule.term3',
  'membershipRule.term4',
  'membershipRule.term5',
  'membershipRule.term6',
]

onMounted(() => {
  // 订阅语言切换，触发模板与本页 t() 依赖重新求值
  _unsubLocale = i18n.subscribe(() => {
    localeVersion.value += 1
  })
})
onBeforeUnmount(() => {
  if (_unsubLocale) _unsubLocale()
})
</script>

<style lang="scss" scoped>
.rule-page {
  min-height: 100vh;
  background: var(--color-background, #f6f2ee);
  padding-bottom: 64rpx;
}

/* ========== 顶部摘要 ========== */
.hero-card {
  display: flex;
  align-items: center;
  gap: 24rpx;
  margin: 32rpx 32rpx 24rpx;
  padding: 32rpx;
  background: linear-gradient(135deg, #2e2b29 0%, #4a4541 100%);
  border-radius: 28rpx;
  box-shadow: 0 8rpx 32rpx rgba(46, 43, 41, 0.18);
}
.hero-icon-wrap {
  width: 96rpx;
  height: 96rpx;
  border-radius: 24rpx;
  background: rgba(219, 201, 138, 0.18);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.hero-icon {
  font-size: 44rpx;
  color: #dbc98a;
}
.hero-info {
  flex: 1;
  min-width: 0;
}
.hero-title {
  display: block;
  font-size: 36rpx;
  font-weight: 800;
  color: #dbc98a;
  letter-spacing: 1rpx;
}
.hero-sub {
  display: block;
  font-size: 24rpx;
  color: rgba(246, 242, 238, 0.75);
  margin-top: 6rpx;
}

/* ========== 通用区块 ========== */
.content {
  padding: 0 32rpx;
}
.section-card {
  background: var(--color-surface, #ffffff);
  border-radius: 24rpx;
  padding: 32rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
}
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20rpx;
}
.section-title {
  font-size: 30rpx;
  font-weight: 800;
  color: var(--color-text, #1d1d1f);
  letter-spacing: 1rpx;
}
.section-tag {
  padding: 4rpx 12rpx;
  border-radius: 999px;
  background: rgba(219, 201, 138, 0.18);
  color: #8c7a4a;
  font-size: 20rpx;
  font-weight: 700;
}

/* ========== 等级阶梯 ========== */
.ladder {
  display: flex;
  flex-direction: column;
}
.ladder-row {
  display: flex;
  gap: 20rpx;
  padding: 20rpx 0;
  border-bottom: 1rpx solid var(--color-divider, #f2f2f7);
}
.ladder-row:last-child {
  border-bottom: none;
}
.ladder-badge {
  width: 56rpx;
  height: 56rpx;
  border-radius: 16rpx;
  background: var(--color-background, #f6f2ee);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.ladder-badge-num {
  font-size: 26rpx;
  font-weight: 800;
  color: var(--color-text-secondary, #48484a);
}
/* 五种等级配色调与 C 端一致 */
.ladder-L1 .ladder-badge {
  background: linear-gradient(135deg, #e8ddb5, #dbc98a);
  color: #2e2b29;
}
.ladder-L2 .ladder-badge {
  background: linear-gradient(135deg, #d9dee0, #b8c0c4);
  color: #2e2b29;
}
.ladder-L3 .ladder-badge {
  background: linear-gradient(135deg, #f0d68a, #d4af37);
  color: #2e2b29;
}
.ladder-L4 .ladder-badge {
  background: linear-gradient(135deg, #d8dde2, #9aa1a8);
  color: #2e2b29;
}
.ladder-L5 .ladder-badge {
  background: linear-gradient(135deg, #2e2b29, #1a1816);
  color: #dbc98a;
}

.ladder-body {
  flex: 1;
  min-width: 0;
}
.ladder-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 6rpx;
}
.ladder-name {
  font-size: 28rpx;
  font-weight: 700;
  color: var(--color-text, #1d1d1f);
}
.ladder-threshold {
  font-size: 22rpx;
  font-weight: 700;
  color: #8c7a4a;
}
.ladder-desc {
  display: block;
  font-size: 24rpx;
  color: var(--color-text-secondary, #6e6e73);
  line-height: 1.5;
  margin-bottom: 6rpx;
}
.ladder-rate {
  display: inline-block;
  padding: 4rpx 12rpx;
  border-radius: 999px;
  background: rgba(219, 201, 138, 0.18);
  color: #8c7a4a;
  font-size: 20rpx;
  font-weight: 700;
}

/* ========== 规则列表（获取/使用共用） ========== */
.rule-list {
  display: flex;
  flex-direction: column;
}
.rule-row {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 20rpx 0;
  border-bottom: 1rpx solid var(--color-divider, #f2f2f7);
}
.rule-row:last-child {
  border-bottom: none;
}
.rule-icon-wrap {
  width: 64rpx;
  height: 64rpx;
  border-radius: 18rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.rule-body {
  flex: 1;
  min-width: 0;
}
.rule-row-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 4rpx;
}
.rule-title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text, #1d1d1f);
}
.rule-value {
  font-size: 24rpx;
  font-weight: 800;
  color: var(--color-primary-dark, #0064d6);
  font-variant-numeric: tabular-nums;
}
.rule-desc {
  display: block;
  font-size: 22rpx;
  color: var(--color-text-secondary, #6e6e73);
  line-height: 1.5;
}

/* ========== 重要条款 ========== */
.terms {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}
.term-row {
  display: flex;
  gap: 12rpx;
  align-items: flex-start;
}
.term-num {
  font-size: 24rpx;
  font-weight: 800;
  color: #8c7a4a;
  flex-shrink: 0;
  line-height: 1.55;
}
.term-text {
  flex: 1;
  font-size: 24rpx;
  color: var(--color-text-secondary, #3c4e6e73);
  line-height: 1.55;
}
.term-row .term-text {
  color: var(--color-text-secondary, #48484a);
}

/* ========== 温馨提示 ========== */
.tips {
  margin: 24rpx 32rpx 0;
  padding: 20rpx 24rpx;
  border-radius: 16rpx;
  background: rgba(219, 201, 138, 0.1);
}
.tips-text {
  display: block;
  font-size: 22rpx;
  color: #8c7a4a;
  line-height: 1.55;
  text-align: center;
}
</style>
