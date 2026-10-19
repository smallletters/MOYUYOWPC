<template>
  <view class="try-before-buy">
    <scroll-view class="scroll" scroll-y>
      <view class="banner-card">
        <view class="banner-deco" />
        <view class="banner-content">
          <view class="banner-title-row">
            <text class="banner-icon luc-sparkles" />
            <text class="banner-title">先试后买 0 风险试穿</text>
          </view>
          <text class="banner-desc">
            下单时仅预授权 $1，商品寄到家后试穿。7 天试用期，到期前选择留下或寄回。
          </text>
          <view class="banner-tags">
            <text class="banner-tag">
              <text class="luc luc-lock" />
              预授权 $1
            </text>
            <text class="banner-tag">
              <text class="luc luc-clock" />
              7 天试用期
            </text>
            <text class="banner-tag">
              <text class="luc luc-undo" />
              免费寄回
            </text>
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
          <view class="trial-btn primary" @click="onKeep(product)">
            <text class="luc luc-check" />
            确认留下
          </view>
          <view class="trial-btn secondary" @click="onReturn(product)">
            <text class="luc luc-package" />
            预约寄回
          </view>
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
          <text class="rules-title">
            <text class="luc luc-scroll-text" />
            试用规则说明
          </text>
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
        <text>
          浏览可试用商品
          <text class="luc luc-arrow-right" />
        </text>
      </view>
    </scroll-view>
  </view>
</template>

<script>
export default {
  pageTitleKey: 'pageTitle.goodsTryBeforeBuy',

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
          color: 'linear-gradient(145deg, var(--color-fill, #f2f2f7), var(--color-fill-dim))',
        },
      ],
    }
  },

  methods: {
    onKeep(product) {
      uni.showToast({
        title: this.$t('tryBeforeBuy.kept', { name: product.name }),
        icon: 'success',
      })
    },

    onReturn(product) {
      uni.showToast({ title: this.$t('tryBeforeBuy.returnScheduled'), icon: 'none' })
    },

    onBrowse() {
      uni.showToast({ title: this.$t('tryBeforeBuy.browseTryable'), icon: 'none' })
    },
  },
}
</script>

<style lang="scss" scoped>
.try-before-buy {
  /* 视口锁定外壳：唯一滚动权威是内部 <scroll-view class="scroll">（flex:1）。 */
  display: flex;
  flex-direction: column;
  /* 视口高度：三层渐进增强（100vh 兜底 → 100dvh → 扣除 --window-top 顶部窗口偏移）。
     非 custom 页 --window-top = 导航栏高 + 状态栏高，必须扣除，否则顶部内容顶到导航栏下。
     末尾 0px 兜底：避免变量缺失时整条声明 invalid-at-computed-value → height 退化为 auto。 */
  height: 100vh;
  height: 100dvh;
  height: calc(100dvh - var(--window-top, 0px));
  overflow: hidden;
  background: var(--color-background);
}

/* 说明：原 .nav-header / .nav-back / .back-icon / .nav-title / .nav-placeholder 为死代码
   （模板中不存在对应元素，本页导航栏由原生 titleNView 提供），已移除。 */

.scroll {
  /* 滚动区是根的唯一在流子项：flex:1 占满整个视口外壳，min-height:0 允许内部滚动。
     原 calc(100vh - 88rpx) 硬扣 88rpx 是脆弱写法（依赖具体导航栏高度、两端语义不一致）；
     改用 flex 后滚动区高度交由布局决定，与平台/导航栏实现无关。 */
  flex: 1;
  min-height: 0;
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
