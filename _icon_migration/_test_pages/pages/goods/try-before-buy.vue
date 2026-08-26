<template>
  <view class="try-before-buy">
    <view class="nav-header">
      <view class="nav-back" @click="goBack">
        <text class="back-icon"><text class="luc luc-arrow-left"></text></text>
      </view>
      <text class="nav-title">先试后买</text>
      <view class="nav-placeholder" />
    </view>

    <scroll-view class="scroll" scroll-y>
      <view class="banner-card">
        <view class="banner-deco" />
        <view class="banner-content">
          <view class="banner-title-row">
            <text class="banner-icon"><text class="luc luc-sparkles"></text></text>
            <text class="banner-title">先试后买 0 风险试穿</text>
          </view>
          <text class="banner-desc">
            下单时仅预授权 $1，商品寄到家后试穿。7 天试用期，到期前选择留下或寄回。
          </text>
          <view class="banner-tags">
            <text class="banner-tag"><text class="luc luc-lock"></text> 预授权 $1</text>
            <text class="banner-tag">⏱ 7 天试用期</text>
            <text class="banner-tag">↩ 免费寄回</text>
          </view>
        </view>
      </view>

      <view class="section-header">
        <text class="section-title">试用中的商品</text>
        <text class="section-badge">{{ trialingProducts.length }} 件试用中</text>
      </view>

      <view v-for="product in trialingProducts" :key="product.id" class="trial-card">
        <view class="trial-top">
          <view class="trial-img" :style="{ background: product.color }">
            <text class="trial-img-text">{{ product.name.slice(0, 2) }}</text>
          </view>
          <view class="trial-info">
            <text class="trial-name">{{ product.name }}</text>
            <text class="trial-spec">{{ product.spec }}</text>
            <text class="trial-price">${{ product.price }}</text>
          </view>
        </view>
        <view class="trial-timer">
          <text class="timer-icon">⏱</text>
          <text class="timer-text">剩余试用时间</text>
          <text class="timer-count">{{ product.timeLeft }}</text>
        </view>
        <view class="trial-actions">
          <view class="trial-btn primary" @click="onKeep(product)"><text class="luc luc-check"></text> 确认留下</view>
          <view class="trial-btn secondary" @click="onReturn(product)"><text class="luc luc-package"></text> 预约寄回</view>
        </view>
      </view>

      <view class="info-bar">
        <text class="info-icon">ℹ</text>
        <text class="info-text">
          当前可试
          <text class="info-highlight">1/3</text>
          件，升级会员享更多
        </text>
        <text class="info-action">去升级</text>
      </view>

      <view class="rules-section">
        <view class="rules-header" @click="showRules = !showRules">
          <text class="rules-title"><text class="luc luc-scroll-text"></text> 试用规则说明</text>
          <text class="rules-arrow" :class="{ open: showRules }">▼</text>
        </view>
        <view v-if="showRules" class="rules-content">
          <view class="rule-item">
            <text class="rule-num">1</text>
            <text class="rule-text">
              下单时仅预授权 $1，不会实际扣款。试用期间商品所有权归 MOYUYO 所有。
            </text>
          </view>
          <view class="rule-item">
            <text class="rule-num">2</text>
            <text class="rule-text">商品寄到后享有 7 天试用期，从签收之日起计算。</text>
          </view>
          <view class="rule-item">
            <text class="rule-num">3</text>
            <text class="rule-text">
              选择「确认留下」后自动扣除商品全款；「预约寄回」提供免费退货标签。
            </text>
          </view>
          <view class="rule-item">
            <text class="rule-num">4</text>
            <text class="rule-text">普通会员单次最多可试 3 件，高级会员 5 件，VIP 会员 8 件。</text>
          </view>
        </view>
      </view>

      <view class="browse-btn" @click="onBrowse">
        <text>浏览可试用商品 <text class="luc luc-arrow-right"></text></text>
      </view>
    </scroll-view>
  </view>
</template>

<script>
export default {
  data() {
    return {
      showRules: false,
      trialingProducts: [
        {
          id: 1,
          name: 'MOYUYO 经典款宠物风衣',
          spec: 'M 码 / 藏青色',
          price: '89.00',
          timeLeft: '3天 12:30:00',
          color: 'linear-gradient(145deg, var(--color-primary-light), var(--color-primary))',
        },
        {
          id: 2,
          name: 'MOYUYO 皮革宠物项圈',
          spec: 'S 码 / 棕色',
          price: '45.00',
          timeLeft: '5天 08:15:22',
          color: 'linear-gradient(145deg, #f2f2f7, #d1d1d6)',
        },
      ],
    }
  },

  methods: {
    goBack() {
      uni.navigateBack()
    },

    onKeep(product) {
      uni.showToast({ title: `已确认留下 ${product.name}`, icon: 'success' })
    },

    onReturn(product) {
      uni.showToast({ title: '已预约寄回，请查收退货标签', icon: 'none' })
    },

    onBrowse() {
      uni.showToast({ title: '浏览可试用商品', icon: 'none' })
    },
  },
}
</script>

<style lang="scss" scoped>
.try-before-buy {
  min-height: 100vh;
  background: var(--color-background);
}

.nav-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 88rpx;
  padding: 0 24rpx;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
  position: sticky;
  top: 0;
  z-index: 10;
}

.nav-back {
  width: 60rpx;
  height: 60rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.back-icon {
  font-size: 48rpx;
  color: var(--color-primary);
  line-height: 1;
}

.nav-title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.nav-placeholder {
  width: 60rpx;
}

.scroll {
  height: calc(100vh - 88rpx);
  padding: 24rpx;
}

.banner-card {
  background: linear-gradient(135deg, var(--color-primary-dark), var(--color-primary));
  border-radius: var(--radius-lg);
  padding: 32rpx;
  position: relative;
  overflow: hidden;
  margin-bottom: 32rpx;
}

.banner-deco {
  position: absolute;
  top: -48rpx;
  right: -48rpx;
  width: 192rpx;
  height: 192rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
}

.banner-content {
  position: relative;
  z-index: 1;
}

.banner-title-row {
  display: flex;
  align-items: center;
  gap: 8rpx;
  margin-bottom: 12rpx;
}

.banner-icon {
  font-size: 32rpx;
}

.banner-title {
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-bold);
  color: #fff;
}

.banner-desc {
  font-size: var(--font-size-sm);
  color: rgba(255, 255, 255, 0.88);
  line-height: 1.6;
  margin-bottom: 16rpx;
}

.banner-tags {
  display: flex;
  gap: 16rpx;
}

.banner-tag {
  font-size: var(--font-size-xs);
  color: rgba(255, 255, 255, 0.85);
  font-weight: var(--font-weight-medium);
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16rpx;
}

.section-title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.section-badge {
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
  padding: 6rpx 16rpx;
  border-radius: var(--radius-pill);
  background: var(--color-primary);
  color: var(--color-text);
}

.trial-card {
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: 24rpx;
  margin-bottom: 16rpx;
  border: 1rpx solid var(--color-divider);
}

.trial-top {
  display: flex;
  gap: 16rpx;
  margin-bottom: 16rpx;
}

.trial-img {
  width: 168rpx;
  height: 168rpx;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.trial-img-text {
  font-size: 28rpx;
  color: var(--color-text-secondary);
  font-weight: var(--font-weight-semibold);
}

.trial-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.trial-name {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.trial-spec {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.trial-price {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-bold);
  color: var(--color-primary-dark);
}

.trial-timer {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 12rpx 16rpx;
  border-radius: var(--radius-sm);
  background: var(--color-background);
  margin-bottom: 16rpx;
}

.timer-icon {
  font-size: 24rpx;
}

.timer-text {
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}

.timer-count {
  margin-left: auto;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-bold);
  color: var(--color-primary-dark);
  font-variant-numeric: tabular-nums;
}

.trial-actions {
  display: flex;
  gap: 16rpx;
}

.trial-btn {
  flex: 1;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-sm);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
}

.trial-btn.primary {
  background: var(--color-primary);
  color: var(--color-text);
}

.trial-btn.secondary {
  background: var(--color-surface);
  color: var(--color-text-secondary);
  border: 1rpx solid var(--color-divider);
}

.info-bar {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 16rpx 20rpx;
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  margin-bottom: 24rpx;
}

.info-icon {
  font-size: 24rpx;
}

.info-text {
  flex: 1;
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}

.info-highlight {
  color: var(--color-text);
  font-weight: var(--font-weight-semibold);
}

.info-action {
  font-size: var(--font-size-xs);
  color: var(--color-primary);
  font-weight: var(--font-weight-semibold);
}

.rules-section {
  background: var(--color-surface);
  border-radius: var(--radius-md);
  overflow: hidden;
  border: 1rpx solid var(--color-divider);
  margin-bottom: 24rpx;
}

.rules-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx;
}

.rules-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.rules-arrow {
  font-size: 20rpx;
  color: var(--color-text-tertiary);
  transition: transform 0.2s;
}

.rules-arrow.open {
  transform: rotate(180deg);
}

.rules-content {
  padding: 0 24rpx 24rpx;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.rule-item {
  display: flex;
  gap: 12rpx;
}

.rule-num {
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  background: var(--color-primary);
  color: var(--color-text);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-bold);
  flex-shrink: 0;
}

.rule-text {
  flex: 1;
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
  line-height: 1.6;
}

.browse-btn {
  height: 88rpx;
  border-radius: var(--radius-md);
  background: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}
</style>
