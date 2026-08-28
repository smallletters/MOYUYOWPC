<!--
  通用状态展示组件(空 / 加载 / 错误 / 网络异常)
  用法：<empty-state type="empty" title="..." desc="..." btnText="..." @action="onAction" />
-->
<template>
  <view class="empty-state" :class="['es-' + type]">
    <view v-if="type !== 'loading'" class="es-icon" :style="{ background: iconBg }">
      <text class="es-icon-text luc" :class="$luc(icon)" />
    </view>
    <!-- 加载类型特殊渲染 -->
    <view v-else class="es-spinner">
      <view class="es-dot" />
      <view class="es-dot" />
      <view class="es-dot" />
    </view>
    <text v-if="title" class="es-title">{{ title }}</text>
    <text v-if="desc" class="es-desc">{{ desc }}</text>
    <view v-if="btnText" class="es-btn" @tap="onAction">
      <text v-if="btnIcon" class="luc" :class="$luc(btnIcon)" />
      <text class="es-btn-text">{{ btnText }}</text>
    </view>
  </view>
</template>

<script>
/**
 * 通用状态展示组件。
 * - empty:   空状态（如购物车为空）
 * - loading: 加载中（dot pulse spinner）
 * - error:   错误状态（重试按钮）
 * - network: 网络异常
 *
 * 通过 easycom 自动注册为 <empty-state />,无需手动 import
 */
export default {
  name: 'EmptyState',
  props: {
    type: { type: String, default: 'empty' }, // empty / loading / error / network
    title: { type: String, default: '' },
    desc: { type: String, default: '' },
    icon: { type: String, default: 'inbox' },
    iconBg: { type: String, default: 'rgba(219, 201, 138, 0.15)' },
    btnText: { type: String, default: '' },
    btnIcon: { type: String, default: '' },
  },
  emits: ['action'],
  methods: {
    onAction() {
      this.$emit('action')
    },
  },
}
</script>

<style lang="scss" scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80rpx 40rpx;
  text-align: center;
}

.es-icon {
  width: 144rpx;
  height: 144rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 40rpx;
}

.es-icon-text {
  font-size: 56rpx;
  color: var(--color-text-secondary);
}

.es-title {
  display: block;
  font-size: 32rpx;
  font-weight: 600;
  color: var(--color-text);
  margin-bottom: 16rpx;
}

.es-desc {
  display: block;
  font-size: 26rpx;
  line-height: 1.5;
  color: var(--color-text-secondary);
  margin-bottom: 40rpx;
  max-width: 480rpx;
}

.es-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  height: 72rpx;
  padding: 0 40rpx;
  border-radius: 36rpx;
  background: var(--color-primary);
}

.es-btn-text {
  font-size: 28rpx;
  font-weight: 600;
  color: #ffffff;
}

/* 加载 spinner：3 个 dot pulse */
.es-spinner {
  display: flex;
  gap: 16rpx;
  margin-bottom: 32rpx;
}

.es-dot {
  width: 20rpx;
  height: 20rpx;
  border-radius: 50%;
  background: var(--color-primary);
  animation: esDotPulse 1.4s ease-in-out infinite both;
}
.es-dot:nth-child(1) {
  animation-delay: -0.32s;
}
.es-dot:nth-child(2) {
  animation-delay: -0.16s;
}

@keyframes esDotPulse {
  0%,
  80%,
  100% {
    transform: scale(0.6);
    opacity: 0.4;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}

/* 错误/网络异常的红色提示 */
.es-error .es-icon {
  background: rgba(201, 110, 95, 0.15);
}
.es-error .es-icon-text {
  color: #c96e5f;
}
.es-network .es-icon {
  background: rgba(255, 149, 0, 0.15);
}
.es-network .es-icon-text {
  color: #ff9500;
}

/* 用户系统减少降级动画:适配 prefers-reduced-motion */
@media (prefers-reduced-motion: reduce) {
  .es-dot {
    animation: none !important;
  }
}
</style>
