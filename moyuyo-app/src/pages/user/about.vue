<template>
  <view class="about">
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
        <text class="card-title">{{ $t('about.brandStory') }}</text>
        <text class="card-body">
          {{ $t('about.brandStoryBody') }}
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
        <text class="card-title">{{ $t('about.contact') }}</text>
        <view class="contact-list">
          <view v-for="item in contacts" :key="item.label" class="contact-item">
            <text class="contact-icon luc" :class="$luc(item.icon)" />
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
          <text class="chevron luc-chevron-right" />
        </view>
      </view>

      <!-- 版权信息 -->
      <view class="footer">
        <text class="copyright">&copy; 2026 MOYUYO. All rights reserved.</text>
      </view>
    </scroll-view>
  </view>
</template>
<script>
import { config } from '@/utils/config'
import { isUrlAllowed } from '@/utils/webview-guard'
import { i18n } from '@/i18n'

export default {
  pageTitleKey: 'pageTitle.userAbout',

  data() {
    return {
      localeVersion: 0,
      unsubLocale: null,
    }
  },

  computed: {
    // IP 角色介绍（随语言切换刷新）
    characters() {
      void this.localeVersion
      return [
        {
          name: 'MILO',
          role: i18n.t('about.roleExplorer'),
          initial: 'M',
          bgColor: 'var(--color-primary-light)',
          color: 'var(--color-primary)',
        },
        {
          name: 'LUNA',
          role: i18n.t('about.roleLifestyle'),
          initial: 'L',
          bgColor: 'var(--color-divider)',
          color: 'var(--color-text)',
        },
        {
          name: 'ATLAS',
          role: i18n.t('about.roleGuardian'),
          initial: 'A',
          bgColor: '#e9f9ee',
          color: '#34c759',
        },
        {
          name: 'OLIVE',
          role: i18n.t('about.roleCreator'),
          initial: 'O',
          bgColor: 'var(--color-divider)',
          color: 'var(--color-text-secondary)',
        },
      ]
    },
    // 联系方式从环境变量读取(.env 中 VITE_CONTACT_*),上线前必须替换
    contacts() {
      void this.localeVersion
      return [
        { icon: 'globe', label: i18n.t('about.contactWebsite'), value: config.contactWebsite },
        { icon: 'mail', label: i18n.t('about.contactEmail'), value: config.contactEmail },
      ]
    },
    // 协议链接:有 VITE_*_URL 时优先走外链,否则走内置兜底页
    infoLinks() {
      void this.localeVersion
      return [
        { label: i18n.t('about.terms'), type: 'terms', url: config.termsUrl },
        { label: i18n.t('about.privacy'), type: 'privacy', url: config.privacyUrl },
        {
          label: i18n.t('about.linkQualification'),
          type: 'qualification',
          url: config.qualificationUrl,
        },
        { label: i18n.t('about.linkLicense'), type: 'license', url: config.licenseUrl },
      ]
    },
  },

  created() {
    // 订阅语言变化，刷新上方依赖本地化的 computed
    this.unsubLocale = i18n.subscribe(() => {
      this.localeVersion += 1
    })
  },
  beforeUnmount() {
    if (this.unsubLocale) this.unsubLocale()
  },

  methods: {
    onInfoClick(item) {
      // 已配置外链:用通用 web-view 容器打开
      if (item.url) {
        // 兜底校验:URL 不在白名单则提示,避免引入 XSS/钓鱼面
        if (!isUrlAllowed(item.url)) {
          uni.showToast({ title: i18n.t('about.externalBlocked'), icon: 'none' })
          return
        }
        uni.navigateTo({
          url: `/pages/webview/document?url=${encodeURIComponent(item.url)}&title=${encodeURIComponent(item.label)}`,
        })
        return
      }
      // 未配置外链:跳到内置兜底页(terms-document)
      uni.navigateTo({
        url: `/pages/user/terms-document?type=${item.type}`,
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
