<!--
  通用图片组件:封装 lazy-load + 加载失败兜底图。
  用法:<image-fallback :src="item.image" mode="aspectFill" />
-->
<template>
  <view class="img-fallback" :class="{ 'is-loading': loading, 'is-error': loadError }">
    <image
      v-if="src"
      :src="src"
      :mode="mode"
      lazy-load
      class="img"
      @load="onLoad"
      @error="onError"
    />
    <!-- 加载中骨架 -->
    <view v-if="loading && !loadError" class="img-placeholder">
      <text class="placeholder-text">加载中…</text>
    </view>
    <!-- 加载失败兜底:显示 SVG 灰底 -->
    <view v-if="loadError" class="img-placeholder img-error-placeholder">
      <text class="placeholder-error-text">图片加载失败</text>
    </view>
  </view>
</template>

<script>
/**
 * 图片懒加载 + 加载失败兜底。
 * 通过 easycom 自动注册为 <image-fallback />。
 *
 * - lazy-load: 启用原生 lazy-load(uni-app 编译为小程序/H5 都支持 IntersectionObserver 懒加载)
 * - loadError: @error 触发后置 true,展示灰底 SVG
 * - 重试:onShow 触发 @load/onError 重新走一遍;业务侧也可以传 :key="src" 强制重渲染
 */
export default {
  name: 'ImageFallback',
  props: {
    src: { type: String, default: '' },
    mode: { type: String, default: 'aspectFill' }, // uni-app image mode
  },
  data() {
    return {
      loading: true,
      loadError: false,
    }
  },
  watch: {
    // src 变化时重置状态
    src() {
      this.loading = true
      this.loadError = false
    },
  },
  methods: {
    onLoad() {
      this.loading = false
      this.loadError = false
    },
    onError() {
      this.loading = false
      this.loadError = true
    },
  },
}
</script>

<style lang="scss" scoped>
.img-fallback {
  position: relative;
  display: block;
  width: 100%;
  height: 100%;
  overflow: hidden;
  background: var(--color-divider);
}

.img {
  width: 100%;
  height: 100%;
  display: block;
}

.img-placeholder {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(90deg, #f2f2f7 25%, #f7f7fa 50%, #f2f2f7 75%);
  background-size: 200% 100%;
  animation: imgShimmer 1.5s ease-in-out infinite;
}

.img-error-placeholder {
  animation: none;
  background: #f2f2f7;
}

.placeholder-text {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}

.placeholder-error-text {
  font-size: 22rpx;
  color: var(--color-text-secondary);
}

@keyframes imgShimmer {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .img-placeholder {
    animation: none !important;
  }
}
</style>
