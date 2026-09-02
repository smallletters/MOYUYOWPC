<template>
  <view class="rule-page">
    <!-- 顶部摘要卡：当前等级 + 积分 -->
    <view class="hero-card">
      <view class="hero-icon-wrap">
        <text class="luc luc-book-open hero-icon" />
      </view>
      <view class="hero-info">
        <text class="hero-title">MOYUYO 会员规则</text>
        <text class="hero-sub">清晰透明的成长体系，每一分投入都被看见</text>
      </view>
    </view>

    <!-- 内容区 -->
    <view class="content">
      <!-- 1. 等级阶梯 -->
      <view class="section-card">
        <view class="section-head">
          <text class="section-title">等级阶梯</text>
          <text class="section-tag">5 级</text>
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
                <text class="ladder-threshold">{{ lv.threshold }} 积分起</text>
              </view>
              <text class="ladder-desc">{{ lv.desc }}</text>
              <text class="ladder-rate">积分倍率 {{ lv.rate }}x</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 2. 积分获取 -->
      <view class="section-card">
        <view class="section-head">
          <text class="section-title">积分获取</text>
        </view>
        <view class="rule-list">
          <view v-for="r in earnRules" :key="r.title" class="rule-row">
            <view class="rule-icon-wrap" :style="{ background: r.bg }">
              <text class="luc" :class="r.icon" :style="{ color: r.color, fontSize: '20px' }" />
            </view>
            <view class="rule-body">
              <view class="rule-row-head">
                <text class="rule-title">{{ r.title }}</text>
                <text class="rule-value">+{{ r.value }}</text>
              </view>
              <text class="rule-desc">{{ r.desc }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 3. 积分使用 -->
      <view class="section-card">
        <view class="section-head">
          <text class="section-title">积分使用</text>
        </view>
        <view class="rule-list">
          <view v-for="r in useRules" :key="r.title" class="rule-row">
            <view class="rule-icon-wrap" :style="{ background: r.bg }">
              <text class="luc" :class="r.icon" :style="{ color: r.color, fontSize: '20px' }" />
            </view>
            <view class="rule-body">
              <view class="rule-row-head">
                <text class="rule-title">{{ r.title }}</text>
                <text class="rule-value">-{{ r.value }}</text>
              </view>
              <text class="rule-desc">{{ r.desc }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 4. 重要条款 -->
      <view class="section-card">
        <view class="section-head">
          <text class="section-title">重要条款</text>
        </view>
        <view class="terms">
          <view v-for="(t, i) in terms" :key="i" class="term-row">
            <text class="term-num">{{ i + 1 }}.</text>
            <text class="term-text">{{ t }}</text>
          </view>
        </view>
      </view>

      <!-- 温馨提示 -->
      <view class="tips">
        <text class="tips-text">本规则最终解释权归 MOYUYO 所有。如有疑问请联系在线客服。</text>
      </view>
    </view>
  </view>
</template>

<script setup>

import { usePageTitle } from '@/utils/i18nPageMixin'
usePageTitle('pageTitle.userMembershipRule')





// 等级阶梯（与后端 listLevels 字段对齐）
const levels = [
  { code: 'L1', name: 'Member', threshold: 0, desc: '注册即获得，享受基础会员权益', rate: 1.0 },
  { code: 'L2', name: 'Silver', threshold: 500, desc: '完成首单 + 几次签到即可升级', rate: 1.1 },
  { code: 'L3', name: 'Gold', threshold: 2000, desc: '活跃用户专属，解锁进阶特权', rate: 1.2 },
  { code: 'L4', name: 'Platinum', threshold: 8000, desc: '高频消费用户，享高级权益', rate: 1.5 },
  { code: 'L5', name: 'Black', threshold: 25000, desc: '顶级 VIP，享受最高级别礼遇', rate: 2.0 },
]

// 积分获取规则
const earnRules = [
  {
    title: '每日签到',
    value: '5/日',
    desc: '连续 7 天签到，奖励翻倍',
    icon: 'luc-calendar-check',
    bg: '#e8f2ff',
    color: '#007aff',
  },
  {
    title: '购物消费',
    value: '1x',
    desc: '按会员等级倍率返还积分（最低1倍）',
    icon: 'luc-shopping-bag',
    bg: '#e9f9ee',
    color: '#34c759',
  },
  {
    title: '订单完成',
    value: '+50',
    desc: '每完成一单赠送 50 积分',
    icon: 'luc-package-check',
    bg: '#e8f2ff',
    color: '#0064d6',
  },
  {
    title: '撰写评价',
    value: '+20',
    desc: '订单首次评价得 20 积分',
    icon: 'luc-message-square',
    bg: '#fff4e5',
    color: '#ff9500',
  },
  {
    title: '邀请好友',
    value: '+100',
    desc: '好友注册 +100，好友首单再加 200',
    icon: 'luc-user-plus',
    bg: '#e9f9ee',
    color: '#34c759',
  },
  {
    title: '完成任务',
    value: '不定',
    desc: '每日 / 每周 / 成就任务奖励',
    icon: 'luc-target',
    bg: '#f3e8ff',
    color: '#af52de',
  },
]

// 积分使用规则
const useRules = [
  {
    title: '下单抵扣',
    value: '100积分/元',
    desc: '结算时勾选积分抵扣，最高抵扣订单金额 30%',
    icon: 'luc-coins',
    bg: '#ffecea',
    color: '#ff3b30',
  },
  {
    title: '积分商城兑换',
    value: '实物/券',
    desc: '兑换实物礼品、宠物用品、优惠券等',
    icon: 'luc-gift',
    bg: '#e8f2ff',
    color: '#007aff',
  },
  {
    title: '漏签补签',
    value: '50/次',
    desc: '每月第 1 次免费，之后每次消耗 50 积分',
    icon: 'luc-calendar-off',
    bg: '#fff4e5',
    color: '#ff9500',
  },
]

// 重要条款
const terms = [
  '积分仅在 MOYUYO 注册账户内有效，不支持跨账户转移或提现为法定货币。',
  '积分有效期为获得之日起 12 个月，到期未使用积分将自动清零，请及时使用。',
  '发生退款时，已抵扣积分原路返还，已发放积分按比例扣回。',
  '会员等级根据历史累计积分判定，积分减少（如退款扣减）不会主动降级。',
  '刷单、恶意刷积分、利用漏洞等违规行为，一经查实将冻结账户并清零积分。',
  '本规则最终解释权归 MOYUYO 所有，运营活动奖励以活动页文案为准。',
]
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
