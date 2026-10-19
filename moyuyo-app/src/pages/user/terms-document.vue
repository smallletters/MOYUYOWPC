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
  /* 视口锁定外壳：min-height 改为固定高度并裁剪溢出，唯一滚动权威是内部 scroll-view。
     100vh 写在 100dvh 之前做兜底。约定见 goods/detail.vue。 */
  display: flex;
  flex-direction: column;
  /* 视口高度：三层渐进增强。
     1) 100vh  —— 兜底，所有环境合法
     2) 100dvh —— H5 移动浏览器动态视口（地址栏）
     3) var(--window-top) —— uni-app 顶部窗口偏移：非 custom 页 = 导航栏高 + 状态栏高，custom 页 = 0。
        必须扣除，否则顶部内容会顶到导航栏/状态栏底下。
     只减 --window-top 不减 --window-bottom：后者在 H5 被归一化为 env(safe-area-inset-bottom)，
     会与页面自身底栏的 safe-area padding 重复预留。本批无 tabBar 页，故安全。 */
  height: 100vh;
  height: 100dvh;
  height: calc(100dvh - var(--window-top, 0px));
  overflow: hidden;
  background: var(--color-background);
}

/* 顶部导航栏：常流 flex 子项，天然吸顶（sticky 已删除）。
   flex-shrink:0 防止被压缩塌陷；position:relative 为两个 .header-btn 的绝对定位保留包含块。 */
.header {
  position: relative;
  flex-shrink: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: center;
  /* 顶部状态栏安全区：取 --status-bar-height 与 env(safe-area-inset-top) 较大值，避免 iOS 双重计算；第二条为老 WebView 兜底 */
  height: calc(88rpx + var(--status-bar-height, 0px));
  height: calc(88rpx + max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px)));
  padding-top: var(--status-bar-height, 0px);
  padding-top: max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px));
  box-sizing: border-box;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
}

.header-btn {
  position: absolute;
  /* 绝对定位元素吃不到父级 padding，需自行下移状态栏高度 */
  top: var(--status-bar-height, 0px);
  top: max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px));
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
  /* 唯一滚动权威：已有 flex:1；删除语义含糊且冗余的 height 硬算，补 min-height:0
     使其可收缩到小于内容高度以产生内部滚动（约定见 goods/detail.vue）。 */
  flex: 1;
  min-height: 0;
  padding: 32rpx 40rpx 80rpx;
  /* 兼容 H5:uni-app 的 scroll-view 在 H5 下会渲染多层嵌套 div,
     这里强制让最内层内容容器占满屏幕宽度,避免内容缩到一侧 */
  width: 100%;
  box-sizing: border-box;
}
/* H5 下 scroll-view 内部包裹 div,显式撑满宽度 */
.content :deep(.uni-scroll-view),
.content :deep(.uni-scroll-view-content) {
  width: 100% !important;
  min-width: 0;
  box-sizing: border-box;
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
