<!--
  社交数据宫格组件
  用途：用户中心展示"关注 / 粉丝 / 收藏 / 足迹"四宫格
  通过 easycom 自动注册为 <social-grid />,无需手动 import
-->
<template>
  <view class="social-grid-card card">
    <view class="sg-grid">
      <view
        v-for="(item, idx) in items"
        :key="item.key || idx"
        class="sg-item"
        :class="{ 'sg-divider-right': !isLastCol(idx) }"
        @tap="onTap(item, idx)"
      >
        <view v-if="item.icon" class="sg-icon-wrap" :style="{ background: item.bg || defaultBg }">
          <text class="sg-icon luc" :class="iconClass(item.icon)" />
        </view>
        <text class="sg-num">{{ formatNum(item.value) }}</text>
        <text class="sg-label">{{ item.label }}</text>
      </view>
    </view>
  </view>
</template>

<script>
/**
 * 通用社交宫格
 * - items: [{ key, label, value, icon, bg, url }]
 * - 点击 item 会触发 @tap,回调参数为整条 item,父组件决定如何跳转
 * - 未传 url 的项不会响应点击,父组件可借此控制"待开发"项
 */
export default {
  name: 'SocialGrid',
  props: {
    items: {
      type: Array,
      default: () => [],
    },
    // 列数：默认 4,适配其它场景(如 2/3 列)
    cols: {
      type: Number,
      default: 4,
    },
    // 数字默认图标背景色(柔和金棕)
    defaultBg: {
      type: String,
      default: 'rgba(219, 201, 138, 0.18)',
    },
  },
  emits: ['tap'],
  methods: {
    isLastCol(idx) {
      // 仅最右侧不加右边框,避免与卡片右边距重复
      return (idx + 1) % this.cols === 0
    },
    iconClass(name) {
      // icon 字符串可直接喂给 lucide 的 $luc(),这里直接拼接类名即可
      return `luc-${name}`
    },
    formatNum(v) {
      const n = Number(v) || 0
      if (n >= 10000) return `${(n / 10000).toFixed(1)}w`
      return n.toLocaleString()
    },
    onTap(item) {
      if (!item || !item.url) {
        uni.showToast({ title: '功能即将上线', icon: 'none' })
        return
      }
      this.$emit('tap', item)
    },
  },
}
</script>

<style lang="scss" scoped>
/* 卡片外壳：与项目里其他 card 风格一致(白底+圆角+阴影+24rpx 边距) */
.social-grid-card {
  margin: 0 24rpx 16rpx;
  padding: 8rpx 0;
}

/* 4 列网格；cols 不为 4 时通过 sg-cols-N 类动态覆盖 */
.sg-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
}

.sg-item {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  padding: 24rpx 0;
  transition:
    transform 0.15s ease,
    background 0.15s ease;

  &:active {
    transform: scale(0.97);
    background: var(--color-background);
  }
}

/* 仅给非最右列画 1px 细分隔线,比 border-right 更精准 */
.sg-divider-right::after {
  content: '';
  position: absolute;
  right: 0;
  top: 25%;
  bottom: 25%;
  width: 1rpx;
  background: var(--color-divider);
}

/* 图标徽章：圆角方块背景 + 居中图标,提升视觉层次 */
.sg-icon-wrap {
  width: 56rpx;
  height: 56rpx;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 4rpx;
}

.sg-icon {
  font-size: 32rpx;
  color: var(--color-primary-dark, #8a6d2a);
}

.sg-num {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-bold);
  color: var(--color-text);
  line-height: 1.1;
}

.sg-label {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

/* 用户系统减少降级动画 */
@media (prefers-reduced-motion: reduce) {
  .sg-item {
    transition: none;
  }
  .sg-item:active {
    transform: none;
  }
}
</style>
