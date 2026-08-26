<template>
  <view class="crowdfunding">
    <!-- 顶部深色导航栏 -->
    <view class="nav-header">
      <view class="nav-back" @tap="goBack">
        <text class="luc luc-arrow-left" />
      </view>
      <text class="nav-title">新品众筹</text>
      <view class="nav-placeholder" />
    </view>

    <scroll-view scroll-y class="scroll">
      <!-- 众筹主图区域 -->
      <view class="banner-area">
        <view class="banner-image" />
        <view class="banner-tag">
          <text class="luc luc-flame" />
          <text>众筹进行中</text>
        </view>
      </view>

      <!-- 商品名称 + 简介 -->
      <view class="product-intro">
        <text class="product-title">PawSync Pro 智能宠物健康监测项圈</text>
        <text class="product-desc">
          全天候追踪宠物活动量、心率与睡眠质量，AI 生成每日健康报告。轻量防水设计，续航长达 14 天。
        </text>
        <view class="feature-tags">
          <view class="feature-tag">
            <text class="luc luc-battery-full" />
            <text>14天续航</text>
          </view>
          <view class="feature-tag">
            <text class="luc luc-droplet" />
            <text>IP68 防水</text>
          </view>
          <view class="feature-tag">
            <text class="luc luc-brain" />
            <text>AI 健康</text>
          </view>
        </view>
      </view>

      <!-- 众筹进度区 -->
      <view class="progress-card">
        <view class="amount-row">
          <view>
            <text class="amount-label">已筹金额</text>
            <view class="amount-value-row">
              <text class="amount-value">$12,580</text>
              <text class="amount-target">/ $20,000</text>
            </view>
          </view>
          <text class="percent-value">63%</text>
        </view>
        <view class="progress-track">
          <view class="progress-fill" style="width: 63%" />
        </view>
        <view class="stats-row">
          <view class="stat-item">
            <text class="luc luc-users" />
            <text>156 人已支持</text>
          </view>
          <view class="stat-item">
            <text class="iconfont icon-clock stat-clock" />
            <text>剩余 18天 06:32:15</text>
          </view>
        </view>
      </view>

      <!-- 支持档位标题 -->
      <view class="tier-header">
        <text class="tier-title">选择支持档位</text>
        <text class="tier-hint">达成目标后启动生产，未达成将全额退款</text>
      </view>

      <!-- 早鸟档（已抢完） -->
      <view class="tier-card sold-out">
        <view class="tier-sold-tag">已抢完</view>
        <view class="tier-top">
          <view class="tier-icon">
            <text class="luc luc-zap" />
          </view>
          <view>
            <text class="tier-name">早鸟档</text>
            <text class="tier-limit">限量 50 人</text>
          </view>
        </view>
        <view class="tier-price-row">
          <text class="tier-price">$39</text>
          <text class="tier-original">$59</text>
          <text class="tier-discount">省 $20</text>
        </view>
        <view class="tier-perks">
          <text class="perk-item">PawSync Pro 项圈 x1</text>
          <text class="perk-item">充电线 + 说明书</text>
          <text class="perk-item">预计发货：众筹结束后 30 天</text>
        </view>
        <view class="tier-btn disabled">已抢完</view>
      </view>

      <!-- 标准档（推荐） -->
      <view class="tier-card featured">
        <view class="tier-featured-tag">推荐</view>
        <view class="tier-top">
          <view class="tier-icon">
            <text class="luc luc-package" />
          </view>
          <view>
            <text class="tier-name">标准档</text>
            <text class="tier-limit">不限量</text>
          </view>
        </view>
        <view class="tier-price-row">
          <text class="tier-price primary">$49</text>
          <text class="tier-original">$69</text>
        </view>
        <view class="tier-perks">
          <text class="perk-item">PawSync Pro 项圈 x1</text>
          <text class="perk-item">充电线 + 说明书 + 保护套</text>
          <text class="perk-item">12 个月质保</text>
          <text class="perk-item">预计发货：众筹结束后 30 天</text>
        </view>
        <view class="tier-btn primary" @tap="handleSupport('标准档', 49)">支持 $49</view>
      </view>

      <!-- 尊享档 -->
      <view class="tier-card">
        <view class="tier-top">
          <view class="tier-icon gold">
            <text class="luc luc-crown" />
          </view>
          <view>
            <text class="tier-name">尊享档</text>
            <text class="tier-limit">限量 30 人</text>
          </view>
        </view>
        <view class="tier-price-row">
          <text class="tier-price">$69</text>
          <text class="tier-original">$99</text>
          <text class="tier-discount">省 $30</text>
        </view>
        <view class="tier-perks">
          <text class="perk-item">PawSync Pro 项圈 x1（镭刻编号）</text>
          <text class="perk-item">全套配件 + 限定收纳包</text>
          <text class="perk-item">MOYUYO 联名宠物项圈挂饰</text>
          <text class="perk-item">24 个月质保 + VIP 专属客服</text>
          <text class="perk-item">优先发货（首批 10 名）</text>
        </view>
        <view class="tier-btn primary" @tap="handleSupport('尊享档', 69)">支持 $69</view>
      </view>

      <!-- 分隔线 -->
      <view class="divider" />

      <!-- 项目详情 -->
      <view class="detail-section">
        <text class="section-title">项目详情</text>
        <view class="detail-image" />
        <text class="detail-text">
          PawSync Pro 是我们历时 18
          个月研发的下一代宠物健康监测设备。搭载高精度三轴加速度传感器与光学心率模组，配合自研 PetAI
          算法，能够精准识别宠物的行走、奔跑、睡眠等 12 种行为状态。
        </text>
        <view class="detail-image" />
        <text class="detail-subtitle">核心技术亮点</text>
        <view class="tech-list">
          <view class="tech-item">
            <text class="luc luc-cpu" />
            <text>超低功耗芯片，待机功耗低于 0.5mA，一次充电续航 14 天</text>
          </view>
          <view class="tech-item">
            <text class="luc luc-shield" />
            <text>IP68 级防水，游泳、洗澡全程无忧</text>
          </view>
          <view class="tech-item">
            <text class="luc luc-bar-chart" />
            <text>App 实时查看健康数据，AI 生成周报 / 月报</text>
          </view>
          <view class="tech-item">
            <text class="luc luc-phone" />
            <text>兼容 iOS / Android，支持蓝牙 5.3 低功耗连接</text>
          </view>
        </view>
      </view>

      <!-- 分隔线 -->
      <view class="divider" />

      <!-- 风险说明折叠面板 -->
      <view class="risk-section">
        <view class="risk-header" @tap="toggleRisk">
          <view class="risk-left">
            <text class="luc luc-alert-triangle" />
            <text class="risk-label">风险说明与退款政策</text>
          </view>
          <text class="luc luc-chevron-down" :class="{ rotated: riskOpen }" />
        </view>
        <view class="risk-content" :class="{ open: riskOpen }">
          <view class="risk-card">
            <text class="risk-card-title">众筹风险</text>
            <text class="risk-card-desc">
              众筹产品处于研发量产阶段，可能因供应链、生产工艺等因素导致交付延迟。我们会通过 App
              和邮件及时同步进展。
            </text>
          </view>
          <view class="risk-card">
            <text class="risk-card-title">未达成退款</text>
            <text class="risk-card-desc">
              若本众筹在截止日期内未达到目标金额 $20,000，系统将在结束后 7
              个工作日内自动将所有支持款项原路全额退款，无需额外操作。
            </text>
          </view>
          <view class="risk-card">
            <text class="risk-card-title">售后保障</text>
            <text class="risk-card-desc">
              产品上市后享受正常售后服务。标准档 12 个月质保，尊享档 24 个月质保 + VIP
              专属客服通道。7 天无理由退换（未拆封）。
            </text>
          </view>
        </view>
      </view>

      <view class="bottom-safe" />
    </scroll-view>

    <!-- 底部固定栏 -->
    <view class="bottom-bar">
      <view class="bar-share" @tap="handleShare">
        <text class="luc luc-share-2" />
      </view>
      <view class="bar-support" @tap="handleSupportNow">
        <text>立即支持</text>
      </view>
    </view>
  </view>
</template>

<script>
export default {
  data() {
    return {
      riskOpen: false,
    }
  },
  methods: {
    goBack() {
      uni.navigateBack()
    },
    toggleRisk() {
      this.riskOpen = !this.riskOpen
    },
    handleSupport(tier, price) {
      uni.showToast({ title: `支持 ${tier} $${price}`, icon: 'none' })
    },
    handleShare() {
      uni.showToast({ title: '分享功能开发中', icon: 'none' })
    },
    handleSupportNow() {
      uni.showToast({ title: '请先选择支持档位', icon: 'none' })
    },
  },
}
</script>

<style scoped>
.crowdfunding {
  min-height: 100vh;
  background: var(--background);
}
.nav-header {
  position: sticky;
  top: 0;
  z-index: 30;
  background: var(--background-800);
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 88rpx;
  padding: 0 32rpx;
}
.nav-back {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64rpx;
  height: 64rpx;
  color: var(--background-50);
}
.nav-title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--background-50);
}
.nav-placeholder {
  width: 64rpx;
  height: 64rpx;
}
.banner-area {
  position: relative;
}
.banner-image {
  width: 100%;
  aspect-ratio: 4 / 3;
  background: linear-gradient(
    145deg,
    var(--brand-100) 0%,
    var(--brand-200) 30%,
    var(--background-100) 70%,
    var(--secondary) 100%
  );
}
.banner-tag {
  position: absolute;
  top: 32rpx;
  left: 32rpx;
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 12rpx 24rpx;
  border-radius: 999px;
  background: var(--destructive);
  color: var(--destructive-foreground);
  font-size: 24rpx;
  font-weight: 600;
}
.product-intro {
  padding: 40rpx 32rpx 24rpx;
}
.product-title {
  font-size: 36rpx;
  font-weight: 700;
  color: var(--foreground);
  line-height: 1.4;
}
.product-desc {
  font-size: 28rpx;
  margin-top: 16rpx;
  color: var(--muted-foreground);
  line-height: 1.6;
}
.feature-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-top: 24rpx;
}
.feature-tag {
  display: inline-flex;
  align-items: center;
  gap: 8rpx;
  padding: 8rpx 20rpx;
  border-radius: 999px;
  font-size: 24rpx;
  font-weight: 500;
  background: var(--brand-50);
  color: var(--primary);
}
.progress-card {
  margin: 0 32rpx;
  padding: 32rpx;
  border-radius: var(--radius);
  background: var(--card);
  border: 2rpx solid var(--border);
}
.amount-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}
.amount-label {
  font-size: 24rpx;
  font-weight: 500;
  color: var(--muted-foreground);
}
.amount-value-row {
  display: flex;
  align-items: baseline;
  gap: 8rpx;
  margin-top: 4rpx;
}
.amount-value {
  font-size: 48rpx;
  font-weight: 700;
  color: var(--primary);
}
.amount-target {
  font-size: 28rpx;
  color: var(--muted-foreground);
}
.percent-value {
  font-size: 48rpx;
  font-weight: 700;
  color: var(--primary);
}
.progress-track {
  width: 100%;
  height: 16rpx;
  border-radius: 999px;
  margin-top: 24rpx;
  overflow: hidden;
  background: var(--secondary);
}
.progress-fill {
  height: 100%;
  border-radius: 999px;
  background: var(--primary);
  transition: width 1s ease-out;
}
.stats-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 24rpx;
  padding-top: 24rpx;
  border-top: 2rpx solid var(--border);
}
.stat-item {
  display: flex;
  align-items: center;
  gap: 12rpx;
  font-size: 24rpx;
  font-weight: 500;
  color: var(--foreground);
}
.stat-item .iconfont {
  color: var(--muted-foreground);
}
.stat-clock {
  color: var(--destructive);
}
.tier-header {
  padding: 48rpx 32rpx 16rpx;
}
.tier-title {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--foreground);
}
.tier-hint {
  font-size: 24rpx;
  margin-top: 8rpx;
  color: var(--muted-foreground);
}
.tier-card {
  margin: 0 32rpx 24rpx;
  padding: 32rpx;
  border-radius: var(--radius);
  background: var(--card);
  border: 2rpx solid var(--border);
  position: relative;
}
.tier-card.featured {
  border: 4rpx solid var(--primary);
}
.tier-card.sold-out {
  opacity: 0.5;
  pointer-events: none;
}
.tier-sold-tag {
  position: absolute;
  top: 24rpx;
  right: 24rpx;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
  font-size: 20rpx;
  font-weight: 700;
  background: var(--background-300);
  color: var(--text-500);
}
.tier-featured-tag {
  position: absolute;
  top: 24rpx;
  right: 24rpx;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
  font-size: 20rpx;
  font-weight: 700;
  background: var(--primary);
  color: var(--primary-foreground);
}
.tier-top {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 8rpx;
}
.tier-icon {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--brand-50);
  color: var(--primary);
}
.tier-icon.gold {
  background: linear-gradient(135deg, #ffd700, #ff9500);
  color: #fff;
}
.tier-name {
  font-size: 28rpx;
  font-weight: 700;
  color: var(--foreground);
}
.tier-limit {
  font-size: 20rpx;
  font-weight: 500;
  margin-left: 12rpx;
  color: var(--muted-foreground);
}
.tier-price-row {
  display: flex;
  align-items: baseline;
  gap: 12rpx;
  margin-top: 16rpx;
}
.tier-price {
  font-size: 40rpx;
  font-weight: 700;
  color: var(--foreground);
}
.tier-price.primary {
  color: var(--primary);
}
.tier-original {
  font-size: 24rpx;
  color: var(--text-300);
  text-decoration: line-through;
}
.tier-discount {
  font-size: 20rpx;
  font-weight: 700;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
  background: var(--destructive);
  color: var(--destructive-foreground);
  margin-left: 8rpx;
}
.tier-perks {
  margin-top: 24rpx;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}
.perk-item {
  font-size: 24rpx;
  color: var(--muted-foreground);
  display: flex;
  align-items: center;
  gap: 12rpx;
}
.perk-item::before {
  content: '';
  width: 28rpx;
  height: 28rpx;
  background: var(--state-success);
  border-radius: 50%;
  flex-shrink: 0;
}
.tier-btn {
  width: 100%;
  height: 80rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 24rpx;
  margin-top: 24rpx;
  font-size: 28rpx;
  font-weight: 600;
}
.tier-btn.primary {
  background: var(--primary);
  color: var(--primary-foreground);
}
.tier-btn.disabled {
  background: var(--background-300);
  color: var(--text-400);
  cursor: not-allowed;
}
.divider {
  height: 16rpx;
  background: var(--secondary);
  margin: 32rpx 0;
}
.detail-section {
  padding: 0 32rpx 48rpx;
}
.section-title {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--foreground);
  display: block;
  margin-bottom: 32rpx;
}
.detail-image {
  width: 100%;
  aspect-ratio: 16 / 10;
  border-radius: var(--radius);
  margin-bottom: 32rpx;
  background: linear-gradient(
    160deg,
    var(--brand-50) 0%,
    var(--secondary) 50%,
    var(--background-200) 100%
  );
}
.detail-text {
  font-size: 28rpx;
  color: var(--foreground);
  line-height: 1.7;
  display: block;
  margin-bottom: 24rpx;
}
.detail-subtitle {
  font-size: 28rpx;
  font-weight: 700;
  color: var(--foreground);
  display: block;
  margin-bottom: 16rpx;
}
.tech-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}
.tech-item {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
  font-size: 28rpx;
  color: var(--muted-foreground);
  line-height: 1.5;
}
.tech-item .iconfont {
  color: var(--primary);
  flex-shrink: 0;
  margin-top: 4rpx;
}
.risk-section {
  padding: 0 32rpx 48rpx;
}
.risk-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.risk-left {
  display: flex;
  align-items: center;
  gap: 16rpx;
}
.risk-left .iconfont {
  color: var(--chart-3);
}
.risk-label {
  font-size: 28rpx;
  font-weight: 700;
  color: var(--foreground);
}
.risk-header .iconfont {
  color: var(--muted-foreground);
  transition: transform 0.3s ease;
}
.risk-header .iconfont.rotated {
  transform: rotate(180deg);
}
.risk-content {
  max-height: 0;
  overflow: hidden;
  transition: max-height 0.3s ease-out;
}
.risk-content.open {
  max-height: 800rpx;
}
.risk-card {
  margin-top: 32rpx;
  padding: 24rpx;
  border-radius: var(--radius);
  background: var(--accent);
  border: 2rpx solid var(--border);
}
.risk-card-title {
  font-size: 24rpx;
  font-weight: 700;
  color: var(--foreground);
  display: block;
  margin-bottom: 8rpx;
}
.risk-card-desc {
  font-size: 24rpx;
  color: var(--muted-foreground);
  line-height: 1.6;
  display: block;
}
.bottom-safe {
  height: 140rpx;
}
.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 40;
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 16rpx 32rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background: rgba(255, 255, 255, 0.92);
  border-top: 2rpx solid var(--border);
}
.bar-share {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 96rpx;
  height: 80rpx;
  border-radius: var(--radius);
  background: var(--secondary);
  border: 2rpx solid var(--border);
  color: var(--foreground);
  flex-shrink: 0;
}
.bar-support {
  flex: 1;
  height: 80rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius);
  background: var(--primary);
  color: var(--primary-foreground);
  font-size: 28rpx;
  font-weight: 700;
}
</style>
