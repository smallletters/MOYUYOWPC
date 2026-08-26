<template>
  <view class="about">
    <!-- 顶部导航栏 -->
    <view class="header">
      <view class="back-btn" @click="goBack">
        <text class="back-icon"><text class="luc luc-arrow-left"></text></text>
      </view>
      <text class="header-title">关于我们</text>
    </view>

    <scroll-view class="content" scroll-y>
      <!-- 品牌英雄区 -->
      <view class="hero">
        <view class="hero-logo">
          <text class="hero-logo-text">M</text>
        </view>
        <text class="hero-brand">MOYUYO ATELIER</text>
        <text class="hero-tagline">Every Journey Together</text>
        <text class="hero-version">v1.0.0</text>
      </view>

      <!-- 品牌故事 -->
      <view class="card">
        <text class="card-title">品牌故事</text>
        <text class="card-body">
          MOYUYO是一个高端宠物生活方式品牌，我们相信每一只宠物都值得最好的。从专业的户外装备到温和的日常护理，从有趣的互动玩具到舒适的家居空间，我们用匠心打造每一件产品，为TA创造更美好的生活。
        </text>
      </view>

      <!-- IP 角色介绍 -->
      <view class="characters">
        <view v-for="item in characters" :key="item.name" class="character-item">
          <view class="character-avatar" :style="{ background: item.bgColor }">
            <text class="character-initial" :style="{ color: item.color }">{{ item.initial }}</text>
          </view>
          <text class="character-name">{{ item.name }}</text>
          <text class="character-role">{{ item.role }}</text>
        </view>
      </view>

      <!-- 联系我们 -->
      <view class="card">
        <text class="card-title">联系我们</text>
        <view class="contact-list">
          <view v-for="item in contacts" :key="item.label" class="contact-item">
            <text class="contact-icon luc" :class="$luc(item.icon)"></text>
            <text class="contact-text">{{ item.label }}: {{ item.value }}</text>
          </view>
        </view>
      </view>

      <!-- 信息列表 -->
      <view class="info-list">
        <view
          v-for="item in infoLinks"
          :key="item.label"
          class="info-item"
          hover-class="item-hover"
          @click="onInfoClick(item)"
        >
          <text class="info-label">{{ item.label }}</text>
          <text class="chevron"><text class="luc luc-chevron-right"></text></text>
        </view>
      </view>

      <!-- 版权信息 -->
      <view class="footer">
        <text class="copyright">&copy; 2026 MOYUYO. All rights reserved.</text>
      </view>
    </scroll-view>
  </view>
</template><script>
export default {
  data() {
    return {
      characters: [
        {
          name: 'MILO',
          role: '探险家',
          initial: 'M',
          bgColor: 'var(--color-primary-light)',
          color: 'var(--color-primary)',
        },
        {
          name: 'LUNA',
          role: '生活家',
          initial: 'L',
          bgColor: 'var(--color-divider)',
          color: 'var(--color-text)',
        },
        { name: 'ATLAS', role: '守护者', initial: 'A', bgColor: '#e9f9ee', color: '#34c759' },
        {
          name: 'OLIVE',
          role: '创作者',
          initial: 'O',
          bgColor: 'var(--color-divider)',
          color: 'var(--color-text-secondary)',
        },
      ],
      contacts: [
        { icon: 'globe', label: '官方网站', value: 'www.moyuyo.com' },
        { icon: 'mail', label: '客服邮箱', value: 'support@moyuyo.com' },
        { icon: 'phone', label: '客服热线', value: '400-888-MOYU' },
      ],
      infoLinks: [
        { label: '用户协议', url: '' },
        { label: '隐私政策', url: '' },
        { label: '商户资质', url: '' },
        { label: '开源许可', url: '' },
      ],
    }
  },

  methods: {
    goBack() {
      uni.navigateBack()
    },

    onInfoClick(item) {
      uni.showToast({
        title: '即将跳转至' + item.label,
        icon: 'none',
      })
    },
  },
}
</script>

<style lang="scss" scoped>
.about {
  min-height: 100vh;
  background: var(--color-background);
}

/* 顶部导航栏 */
.header {
  position: sticky;
  top: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 88rpx;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
}

.back-btn {
  position: absolute;
  left: 16rpx;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 72rpx;
  height: 72rpx;
  border-radius: var(--radius-sm);
}

.back-icon {
  font-size: 48rpx;
  color: var(--color-primary);
  line-height: 1;
}

.header-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  letter-spacing: -0.02em;
}

/* 内容区 */
.content {
  height: calc(100vh - 88rpx);
}

/* 品牌英雄区 */
.hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 96rpx 48rpx 64rpx;
}

.hero-logo {
  width: 120rpx;
  height: 120rpx;
  border-radius: 32rpx;
  background: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 24rpx;
  box-shadow: 0 8rpx 32rpx rgba(0, 122, 255, 0.2);
}

.hero-logo-text {
  font-size: 56rpx;
  font-weight: 700;
  color: #ffffff;
  line-height: 1;
}

.hero-brand {
  font-size: 44rpx;
  font-weight: 700;
  color: var(--color-text);
  letter-spacing: -0.03em;
  text-align: center;
}

.hero-tagline {
  margin-top: 12rpx;
  font-size: 28rpx;
  color: var(--color-text-secondary);
}

.hero-version {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: var(--color-text-tertiary);
}

/* 卡片 */
.card {
  margin: 0 32rpx 24rpx;
  padding: 40rpx;
  background: var(--color-surface);
  border-radius: var(--radius-md);
  border: 1rpx solid var(--color-divider);
}

.card-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.card-body {
  display: block;
  margin-top: 16rpx;
  font-size: 26rpx;
  line-height: 1.6;
  color: var(--color-text-secondary);
}

/* IP 角色网格 */
.characters {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24rpx;
  margin: 0 32rpx 24rpx;
}

.character-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  padding: 32rpx 24rpx;
  background: var(--color-surface);
  border-radius: var(--radius-md);
  border: 1rpx solid var(--color-divider);
}

.character-avatar {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.character-initial {
  font-size: 40rpx;
  font-weight: 700;
  line-height: 1;
}

.character-name {
  font-size: 28rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.character-role {
  font-size: 24rpx;
  color: var(--color-text-tertiary);
}

/* 联系我们 */
.contact-list {
  margin-top: 24rpx;
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.contact-item {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.contact-icon {
  font-size: 28rpx;
  width: 32rpx;
  text-align: center;
  flex-shrink: 0;
  line-height: 1;
}

.contact-text {
  font-size: 26rpx;
  color: var(--color-text-secondary);
}

/* 信息列表 */
.info-list {
  margin: 0 32rpx 24rpx;
  background: var(--color-surface);
  border-radius: var(--radius-md);
  overflow: hidden;
  border: 1rpx solid var(--color-divider);
}

.info-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 32rpx;
  height: 96rpx;
  border-bottom: 1rpx solid var(--color-divider);
}

.info-item:last-child {
  border-bottom: none;
}

.info-label {
  font-size: var(--font-size-sm);
  color: var(--color-text);
}

.chevron {
  font-size: 36rpx;
  color: var(--color-text-tertiary);
  line-height: 1;
}

/* 底部版权 */
.footer {
  padding: 32rpx 32rpx 80rpx;
  text-align: center;
}

.copyright {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}

/* 点击态 */
.item-hover {
  background: var(--color-divider);
  opacity: 0.6;
}
</style>
