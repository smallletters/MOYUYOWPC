<template>
  <view class="terms-doc">
    <view class="header">
      <view class="header-btn" @click="goBack">
        <text class="back-icon luc-arrow-left" />
      </view>
      <text class="header-title">{{ document.title }}</text>
      <view class="header-btn" />
    </view>

    <scroll-view class="content" scroll-y>
      <view class="meta">
        <text class="meta-text">{{ updatedLabel }}</text>
      </view>
      <view class="body">
        <text v-for="(line, idx) in document.body" :key="idx" class="body-line">
          {{ line }}
        </text>
      </view>
      <view class="footer">
        <text class="footer-text">{{ footerNote }}</text>
      </view>
    </scroll-view>
  </view>
</template>

<script>
// 静态协议正文页:文案走 i18n 字典,按当前 locale 渲染
import { i18n } from '@/i18n'

/**
 * 当后端 / 配置文件中尚未提供正式协议 URL 时作为兜底展示。
 * 上线前请法务/产品替换为真实条款,或在 .env 中注入 VITE_TERMS_URL 等链接。
 */
export default {
  pageTitleKey: 'pageTitle.userTermsDocument',

  data() {
    return {
      type: '',
      document: {
        title: '',
        updatedAt: '',
        body: [],
      },
    }
  },

  computed: {
    // 最近更新提示:支持插值替换日期
    updatedLabel() {
      return i18n.t('documents.updatedAtLabel', { date: this.document.updatedAt })
    },
    // 页脚说明
    footerNote() {
      return i18n.t('documents.footerNote')
    },
  },

  onLoad(options) {
    this.type = options.type || 'terms'
    this.document = this.buildDocument(this.type)
    // 动态标题设置(走 i18n 后的 title)
    uni.setNavigationBarTitle({ title: this.document.title })
  },

  methods: {
    goBack() {
      uni.navigateBack()
    },

    /**
     * 从 i18n 字典中按 type 取协议。
     * 默认 fallback 到 terms。
     */
    buildDocument(type) {
      const types = i18n.t('documents.types') || {}
      const doc = types[type] || types.terms || {}
      return {
        title: doc.title || '',
        // 文案中统一收 updatedDate,避免字典与 script 字符串重复
        updatedAt: i18n.t('documents.updatedDate'),
        body: Array.isArray(doc.body) ? doc.body : [],
      }
    },
  },
}
</script>

<style lang="scss" scoped>
.terms-doc {
  min-height: 100vh;
  background: var(--color-background);
  display: flex;
  flex-direction: column;
}

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

.header-btn {
  position: absolute;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 72rpx;
  height: 72rpx;
}

.header-btn:first-child {
  left: 16rpx;
}

.header-btn:last-child {
  right: 16rpx;
}

.back-icon {
  font-size: 48rpx;
  color: var(--color-text);
  line-height: 1;
}

.header-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  letter-spacing: -0.02em;
}

.content {
  flex: 1;
  height: calc(100vh - 88rpx);
  padding: 32rpx 40rpx 80rpx;
}

.meta {
  margin-bottom: 24rpx;
}

.meta-text {
  font-size: 24rpx;
  color: var(--color-text-tertiary);
}

.body {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  padding: 32rpx;
  background: var(--color-surface);
  border-radius: var(--radius-md);
  border: 1rpx solid var(--color-divider);
}

.body-line {
  font-size: 28rpx;
  line-height: 1.7;
  color: var(--color-text);
  white-space: pre-wrap;
}

.footer {
  margin-top: 40rpx;
  text-align: center;
}

.footer-text {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}
</style>
