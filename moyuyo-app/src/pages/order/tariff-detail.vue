<template>
  <view class="tariff-page">
    <!-- 顶部导航栏 -->


    <scroll-view class="tariff-body" scroll-y>
      <!-- 模式选择卡片 -->
      <view class="section-block">
        <text class="section-label">选择关税模式</text>
        <view class="mode-grid">
          <!-- DDP 包税模式 -->
          <view
            class="mode-card"
            :class="{ 'mode-selected': selectedMode === 'ddp' }"
            @tap="selectMode('ddp')"
          >
            <view class="mode-header">
              <view class="mode-icon mode-icon-ddp">
                <text class="mode-emoji luc-shield" />
              </view>
              <text class="mode-badge">DDP</text>
            </view>
            <text class="mode-title">商家包税</text>
            <text class="mode-desc">总价含关税，省心无忧</text>
            <view class="mode-indicator">
              <view v-if="selectedMode === 'ddp'" class="indicator-selected">
                <text class="indicator-check luc-check" />
                <text class="indicator-text-active">已选择</text>
              </view>
              <view v-else class="indicator-unselected">
                <view class="indicator-circle" />
                <text class="indicator-text">未选择</text>
              </view>
            </view>
          </view>

          <!-- DDU 到付模式 -->
          <view
            class="mode-card"
            :class="{ 'mode-selected': selectedMode === 'ddu' }"
            @tap="selectMode('ddu')"
          >
            <view class="mode-header">
              <view class="mode-icon mode-icon-ddu">
                <text class="mode-emoji luc-banknote" />
              </view>
              <text class="mode-badge mode-badge-ddu">DDU</text>
            </view>
            <text class="mode-title">到付关税</text>
            <text class="mode-desc">收货时自行缴纳，可能更低</text>
            <view class="mode-indicator">
              <view v-if="selectedMode === 'ddu'" class="indicator-selected">
                <text class="indicator-check luc-check" />
                <text class="indicator-text-active">已选择</text>
              </view>
              <view v-else class="indicator-unselected">
                <view class="indicator-circle" />
                <text class="indicator-text">未选择</text>
              </view>
            </view>
          </view>
        </view>
      </view>

      <!-- 模式价格对比 -->
      <view class="card-block">
        <view class="card-header">
          <text class="card-emoji luc-bar-chart" />
          <text class="card-title">模式价格对比</text>
        </view>
        <view class="compare-grid">
          <view class="compare-item compare-ddp">
            <text class="compare-label compare-label-ddp">DDP 包税价</text>
            <text class="compare-price">$196.17</text>
            <text class="compare-sub">含关税 $40.17</text>
          </view>
          <view class="compare-item compare-ddu">
            <text class="compare-label compare-label-ddu">DDU 商品价</text>
            <text class="compare-price">$156.00</text>
            <text class="compare-sub">关税到付</text>
          </view>
        </view>
        <view class="compare-note">
          <text class="note-icon">ℹ</text>
          <text class="note-text">
            DDP 模式下商家代缴关税，总价较高但无需额外操作；DDU
            模式下收货时需自行向海关缴纳关税，实际税率以海关核定为准。
          </text>
        </view>
      </view>

      <!-- 关税明细列表 -->
      <view class="card-block">
        <view class="card-header">
          <text class="card-emoji luc-receipt" />
          <text class="card-title">关税明细</text>
        </view>
        <text class="card-subtitle">基于当前订单商品计算</text>

        <view class="tariff-list">
          <view class="tariff-row">
            <text class="tariff-label">商品完税价格</text>
            <text class="tariff-value">$156.00</text>
          </view>
          <view class="tariff-row">
            <view class="tariff-label-group">
              <text class="tariff-label">关税税率</text>
              <view class="origin-tag">
                <text class="origin-tag-text">来源国：美国</text>
              </view>
            </view>
            <text class="tariff-value">12%</text>
          </view>
          <view class="tariff-row">
            <text class="tariff-label">关税金额</text>
            <text class="tariff-value">$18.72</text>
          </view>
          <view class="tariff-row">
            <view class="tariff-label-group">
              <text class="tariff-label">增值税（VAT）</text>
              <view class="origin-tag">
                <text class="origin-tag-text">13.75%</text>
              </view>
            </view>
            <text class="tariff-value">$21.45</text>
          </view>

          <view class="tariff-divider" />

          <view class="tariff-row-total">
            <text class="tariff-total-label">关税合计</text>
            <text class="tariff-total-value">$40.17</text>
          </view>
        </view>
      </view>

      <!-- 税率依据折叠面板 -->
      <view class="card-block">
        <view class="collapse-trigger" @tap="toggleTaxDetail">
          <view class="collapse-left">
            <text class="card-emoji luc-book" />
            <text class="collapse-title">点击查看税率依据</text>
          </view>
          <text class="collapse-chevron" :class="{ rotated: taxDetailOpen }">
            <text class="luc luc-arrow-left" />
          </text>
        </view>
        <view class="collapse-content" :class="{ open: taxDetailOpen }">
          <view class="tax-detail-inner">
            <text class="detail-section-title">税率来源</text>
            <text class="detail-text">
              根据中国海关总署 2024 年最新税率表，宠物用品类（HS 编码：4201.00）适用关税税率
              12%，增值税率 13.75%。
            </text>

            <text class="detail-section-title">计算公式</text>
            <view class="formula-box">
              <text class="formula-text">
                关税 = 完税价格
                <text class="luc luc-x" />
                关税税率
              </text>
              <text class="formula-text">
                = $156.00
                <text class="luc luc-x" />
                12% = $18.72
              </text>
              <text class="formula-text">
                增值税 = (完税价格 + 关税)
                <text class="luc luc-x" />
                增值税率
              </text>
              <text class="formula-text">
                = ($156.00 + $18.72)
                <text class="luc luc-x" />
                13.75% = $21.45
              </text>
              <text class="formula-text">关税合计 = 关税 + 增值税 = $40.17</text>
            </view>

            <view class="info-box">
              <text class="info-icon">ℹ</text>
              <text class="info-text">
                以上税费为预估值，实际税费以目的地海关核定为准。完税价格可能因汇率波动而调整。
              </text>
            </view>
          </view>
        </view>
      </view>

      <!-- 保证金信息（DDP 模式下显示） -->
      <view v-if="selectedMode === 'ddp'" class="card-block">
        <view class="card-header">
          <text class="card-emoji luc-building-2" />
          <text class="card-title">关税保证金</text>
          <view class="ddp-tag">
            <text class="ddp-tag-text">DDP 专属</text>
          </view>
        </view>

        <view class="deposit-info">
          <view class="deposit-left">
            <text class="deposit-sub">预估关税保证金</text>
            <text class="deposit-amount">$48.20</text>
          </view>
          <view class="deposit-right">
            <text class="deposit-sub">关税合计</text>
            <text class="deposit-small">$40.17</text>
          </view>
        </view>

        <view class="deposit-note">
          <text class="deposit-note-icon luc-trending-up" />
          <text class="deposit-note-text">
            保证金含 20% 浮动缓冲，用于覆盖汇率波动及海关核定差异。
          </text>
        </view>
      </view>

      <!-- 多退少补说明 -->
      <view class="card-block">
        <view class="card-header">
          <text class="card-emoji luc-scale" />
          <text class="card-title">多退少补说明</text>
        </view>
        <view class="refund-rules">
          <view class="rule-item">
            <view class="rule-icon rule-icon-success">
              <text class="rule-emoji luc luc-arrow-down-left" />
            </view>
            <view class="rule-content">
              <text class="rule-title">多退</text>
              <text class="rule-desc">
                实际关税低于预估时，差额将在清关完成后 3-5 个工作日内原路退回。
              </text>
            </view>
          </view>
          <view class="rule-item">
            <view class="rule-icon rule-icon-error">
              <text class="rule-emoji luc luc-arrow-up-right" />
            </view>
            <view class="rule-content">
              <text class="rule-title">少补</text>
              <text class="rule-desc">
                实际关税高于预估时，需在收到通知后 7 个工作日内补缴差额，保证金将自动抵扣。
              </text>
            </view>
          </view>
        </view>
      </view>

      <!-- 底部按钮 -->
      <view class="bottom-action">
        <view class="back-checkout-btn" @tap="goBack">
          <text class="back-btn-icon luc-arrow-left" />
          <text class="back-btn-text">返回结算</text>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { usePageTitle } from '@/utils/i18nPageMixin'
usePageTitle('pageTitle.orderTariffDetail')


// 选中的关税模式


const selectedMode = ref('ddp')

// 税率依据折叠状态
const taxDetailOpen = ref(false)

// 切换关税模式
const selectMode = (mode) => {
  selectedMode.value = mode
}

// 切换税率依据折叠
const toggleTaxDetail = () => {
  taxDetailOpen.value = !taxDetailOpen.value
}

// 返回
const goBack = () => {
  uni.navigateBack()
}
</script>

<style lang="scss" scoped>
.tariff-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--color-background);
}

/* 导航栏 */
.nav-back {
  width: 72rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #f2f2f7;
}
.nav-title {
  font-size: 36rpx;
  font-weight: 600;
  color: var(--color-text);
}

/* 内容区域 */
.tariff-body {
  flex: 1;
  padding: 32rpx;
}

.section-block {
  margin-bottom: 32rpx;
}

.section-label {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text);
  margin-bottom: 24rpx;
}

/* 模式选择卡片 */
.mode-grid {
  display: flex;
  gap: 24rpx;
}

.mode-card {
  flex: 1;
  padding: 32rpx;
  border-radius: 24rpx;
  background: var(--color-card);
  border: 2rpx solid var(--color-border);
  transition: border-color 0.2s ease;
}

.mode-selected {
  border-color: var(--color-primary);
}

.mode-header {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 16rpx;
}

.mode-icon {
  width: 64rpx;
  height: 64rpx;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.mode-icon-ddp {
  background: rgba(219, 201, 138, 0.15);
}

.mode-icon-ddu {
  background: #f2f2f7;
}

.mode-emoji {
  font-size: 28rpx;
}

.mode-badge {
  font-size: 22rpx;
  font-weight: 700;
  color: var(--color-primary);
  letter-spacing: 0.06em;
}

.mode-badge-ddu {
  color: var(--color-text-secondary);
}

.mode-title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text);
  margin-bottom: 8rpx;
}

.mode-desc {
  font-size: 24rpx;
  color: var(--color-text-secondary);
  line-height: 1.4;
}

.mode-indicator {
  margin-top: 24rpx;
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.indicator-selected {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.indicator-check {
  width: 32rpx;
  height: 32rpx;
  border-radius: 50%;
  background: var(--color-primary);
  color: #ffffff;
  font-size: 20rpx;
  text-align: center;
  line-height: 32rpx;
}

.indicator-text-active {
  font-size: 24rpx;
  font-weight: 500;
  color: var(--color-primary);
}

.indicator-unselected {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.indicator-circle {
  width: 32rpx;
  height: 32rpx;
  border-radius: 50%;
  border: 3rpx solid #d1d1d6;
}

.indicator-text {
  font-size: 24rpx;
  color: var(--color-text-secondary);
}

/* 通用卡片 */
.card-block {
  margin-bottom: 32rpx;
  border-radius: 24rpx;
  background: var(--color-card);
  border: 1rpx solid var(--color-border);
  padding: 32rpx;
  overflow: hidden;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 8rpx;
}

.card-emoji {
  font-size: 28rpx;
}

.card-title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text);
}

.card-subtitle {
  font-size: 24rpx;
  color: var(--color-text-secondary);
  margin-bottom: 24rpx;
}

/* 价格对比 */
.compare-grid {
  display: flex;
  gap: 24rpx;
  margin-bottom: 24rpx;
}

.compare-item {
  flex: 1;
  border-radius: 16rpx;
  padding: 24rpx;
}

.compare-ddp {
  background: rgba(219, 201, 138, 0.1);
}

.compare-ddu {
  background: #f2f2f7;
}

.compare-label {
  font-size: 24rpx;
  font-weight: 500;
  margin-bottom: 8rpx;
}

.compare-label-ddp {
  color: var(--color-primary);
}

.compare-label-ddu {
  color: var(--color-text-secondary);
}

.compare-price {
  font-size: 36rpx;
  font-weight: 700;
  color: var(--color-text);
}

.compare-sub {
  font-size: 24rpx;
  color: var(--color-text-secondary);
  margin-top: 8rpx;
}

.compare-note {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
  padding-top: 24rpx;
  border-top: 1rpx dashed var(--color-border);
}

.note-icon {
  font-size: 28rpx;
  flex-shrink: 0;
  margin-top: 4rpx;
}

.note-text {
  font-size: 24rpx;
  line-height: 1.5;
  color: var(--color-text-secondary);
}

/* 关税明细列表 */
.tariff-list {
  margin-top: 16rpx;
}

.tariff-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24rpx 0;
  border-bottom: 1rpx solid #f2f2f7;
}

.tariff-label {
  font-size: 28rpx;
  color: var(--color-text);
}

.tariff-label-group {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.origin-tag {
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
  background: #f2f2f7;
}

.origin-tag-text {
  font-size: 22rpx;
  color: var(--color-text-secondary);
}

.tariff-value {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text);
}

.tariff-divider {
  height: 1rpx;
  background: var(--color-border);
  margin: 16rpx 0;
}

.tariff-row-total {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 28rpx;
}

.tariff-total-label {
  font-size: 30rpx;
  font-weight: 700;
  color: var(--color-text);
}

.tariff-total-value {
  font-size: 36rpx;
  font-weight: 700;
  color: var(--color-primary);
}

/* 折叠面板 */
.collapse-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0;
}

.collapse-left {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.collapse-title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text);
}

.collapse-chevron {
  font-size: 28rpx;
  color: var(--color-text-secondary);
  transition: transform 0.3s ease;
  transform: rotate(-90deg);
}

.collapse-chevron.rotated {
  transform: rotate(-270deg);
}

.collapse-content {
  max-height: 0;
  overflow: hidden;
  transition: max-height 0.3s ease;
}

.collapse-content.open {
  max-height: 1000rpx;
}

.tax-detail-inner {
  padding-top: 24rpx;
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.detail-section-title {
  font-size: 24rpx;
  font-weight: 600;
  color: var(--color-text);
}

.detail-text {
  font-size: 24rpx;
  line-height: 1.5;
  color: var(--color-text-secondary);
}

.formula-box {
  padding: 24rpx;
  border-radius: 16rpx;
  background: #f2f2f7;
}

.formula-text {
  font-size: 24rpx;
  line-height: 2;
  color: #48484a;
  font-family: monospace;
}

.info-box {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
  padding: 24rpx;
  border-radius: 16rpx;
  background: rgba(219, 201, 138, 0.1);
}

.info-icon {
  font-size: 28rpx;
  flex-shrink: 0;
  margin-top: 4rpx;
}

.info-text {
  font-size: 24rpx;
  line-height: 1.5;
  color: var(--color-primary);
}

/* 保证金 */
.ddp-tag {
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
  background: rgba(219, 201, 138, 0.15);
}

.ddp-tag-text {
  font-size: 22rpx;
  font-weight: 500;
  color: var(--color-primary);
}

.deposit-info {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 24rpx;
}

.deposit-sub {
  font-size: 24rpx;
  color: var(--color-text-secondary);
}

.deposit-amount {
  font-size: 48rpx;
  font-weight: 700;
  color: var(--color-text);
  margin-top: 8rpx;
}

.deposit-small {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text);
  margin-top: 8rpx;
}

.deposit-note {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 24rpx;
  border-radius: 16rpx;
  background: #f2f2f7;
}

.deposit-note-icon {
  font-size: 28rpx;
  flex-shrink: 0;
}

.deposit-note-text {
  font-size: 24rpx;
  line-height: 1.5;
  color: var(--color-text-secondary);
}

/* 多退少补 */
.refund-rules {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.rule-item {
  display: flex;
  align-items: flex-start;
  gap: 24rpx;
}

.rule-icon {
  width: 48rpx;
  height: 48rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-top: 4rpx;
}

.rule-icon-success {
  background: rgba(171, 185, 173, 0.2);
}

.rule-icon-error {
  background: rgba(201, 110, 95, 0.15);
}

.rule-emoji {
  font-size: 22rpx;
}

.rule-content {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.rule-title {
  font-size: 28rpx;
  font-weight: 500;
  color: var(--color-text);
}

.rule-desc {
  font-size: 24rpx;
  line-height: 1.5;
  color: var(--color-text-secondary);
}

/* 底部按钮 */
.bottom-action {
  margin-top: 16rpx;
  margin-bottom: 64rpx;
}

.back-checkout-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
  height: 100rpx;
  border-radius: 48rpx;
  background: var(--color-primary);
}

.back-btn-icon {
  font-size: 32rpx;
  color: #ffffff;
}

.back-btn-text {
  font-size: 32rpx;
  font-weight: 600;
  color: #ffffff;
}
</style>
