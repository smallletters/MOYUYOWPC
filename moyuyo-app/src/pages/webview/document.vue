<template>
  <view class="webview-doc">
    <!-- URL 不在白名单:展示拦截提示,绝不加载 -->
    <view v-if="!allowed" class="block-screen">
      <text class="block-icon"><text class="luc luc-shield" /></text>
      <text class="block-title">外链不在白名单,已拦截</text>
      <text class="block-desc">为保护账户安全,仅允许打开可信域名。</text>
      <view class="block-btn" @tap="goBack">返回上一页</view>
    </view>
    <web-view v-else-if="url" :src="url" />
  </view>
</template>

<script>
import { isUrlAllowed } from '@/utils/webview-guard'

/**
 * 通用外链展示容器：用于 about 页等需要打开 H5 协议/资质页的场景。
 * 仅承载 web-view,不轮询支付状态,避免和 pages/webview/pay.vue 混淆。
 * 安全：通过 webview-guard 白名单校验 host,避免任意 URL 被加载。
 */
export default {
  data() {
    return {
      url: '',
      allowed: false,
    }
  },

  onLoad(query) {
    const raw = query.url ? decodeURIComponent(query.url) : ''
    // 校验 host 是否在白名单
    this.url = raw
    this.allowed = isUrlAllowed(raw)
    const title = query.title ? decodeURIComponent(query.title) : ''
    if (title) uni.setNavigationBarTitle({ title })
    if (!this.allowed) {
      console.warn('[webview-doc] blocked url:', raw)
    }
  },

  methods: {
    goBack() {
      uni.navigateBack({ delta: 1, fail: () => uni.switchTab({ url: '/pages/tabbar/home' }) })
    },
  },
}
</script>

<style lang="scss" scoped>
.webview-doc {
  width: 100%;
  height: 100vh;
}

.block-screen {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100vh;
  padding: 80rpx 48rpx;
  text-align: center;
  background: var(--color-background);
}

.block-icon {
  font-size: 96rpx;
  color: var(--color-error);
  margin-bottom: 32rpx;
}

.block-title {
  display: block;
  font-size: 32rpx;
  font-weight: 600;
  color: var(--color-text);
  margin-bottom: 16rpx;
}

.block-desc {
  display: block;
  font-size: 26rpx;
  line-height: 1.5;
  color: var(--color-text-secondary);
  margin-bottom: 48rpx;
  max-width: 480rpx;
}

.block-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 72rpx;
  padding: 0 48rpx;
  border-radius: 36rpx;
  background: var(--color-primary);
  color: #ffffff;
  font-size: 28rpx;
  font-weight: 600;
}
</style>
