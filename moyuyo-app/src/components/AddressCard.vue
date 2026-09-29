<!--
  AddressCard 子组件

  单张地址卡。父组件 address.vue 负责列表 + 校验，AddressCard 仅负责渲染和点击事件转发。
  这样父组件可以保持小巧，所有展示细节（tag 颜色、unshippable 灰色、"使用"按钮位置）
  集中在一处，便于后续扩展地址收藏/常用地址等场景。

  Props:
    addr       - 必填，单条地址对象（参考 moyuyo-api AddressEntity）
    selected   - 选中态（结算场景下高亮）
    unshippable - 是否不可发货（命中置灰 + 徽标）
    fromCheckout - 是否来自结算页面（控制"使用"按钮显示）
  Events:
    tap        - 点击整张卡
    use        - 点击"使用"按钮（结算模式）
    edit       - 点击"编辑"
    delete     - 点击"删除"
    setDefault - 点击"设为默认"
-->
<template>
  <view
    class="card address-card"
    :class="{ active: selected, disabled: unshippable }"
    @click="$emit('tap', addr)"
  >
    <!-- 左侧色条 / 选中态视觉锚 -->
    <view class="address-card-rail" />

    <view class="card-body">
      <view class="name-row">
        <text class="name">{{ addr.receiver }}</text>
        <text class="phone">{{ formatPhone(addr.phone) }}</text>
        <view v-if="addr.isDefault" class="default-tag">{{ $t('address.isDefault') }}</view>
        <view v-if="addr.tag" class="tag" :class="`tag-${(addr.tag || '').toLowerCase()}`">
          {{ addr.tag }}
        </view>
        <!-- 不可发货：在姓名行右侧打上橙色徽标 -->
        <view v-if="unshippable" class="unshippable-tag">
          {{ $t('address.unshippable') }}
        </view>
        <!-- 结算场景:「使用」按钮内联在姓名行右侧,避免绝对定位浮在地址详情上 -->
        <view v-if="fromCheckout && !unshippable" class="use-btn" @click.stop="$emit('use', addr)">
          <text class="luc luc-check" />
          <text>{{ $t('address.use') }}</text>
        </view>
      </view>
      <text class="detail">
        {{ formatRegion(addr.country, addr.province, addr.city) }} {{ addr.detail }}
      </text>
      <text v-if="addr.zipCode" class="zip">
        {{ $t('address.zip', { code: addr.zipCode }) }}
      </text>

      <!-- 操作行：编辑 / 删除 / 设为默认 始终可见（满足增改删需求） -->
      <view class="actions">
        <view
          v-if="!addr.isDefault && !unshippable"
          class="action-btn"
          @click.stop="$emit('setDefault', addr)"
        >
          <text class="luc luc-star" />
          <text>{{ $t('address.setDefault') }}</text>
        </view>
        <view class="action-btn" @click.stop="$emit('edit', addr)">
          <text class="luc luc-pencil" />
          <text>{{ $t('address.edit') }}</text>
        </view>
        <view class="action-btn danger" @click.stop="$emit('delete', addr)">
          <text class="luc luc-trash-2" />
          <text>{{ $t('address.delete') }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { i18n } from '@/i18n'

export default {
  name: 'AddressCard',
  props: {
    addr: { type: Object, required: true },
    selected: { type: Boolean, default: false },
    unshippable: { type: Boolean, default: false },
    fromCheckout: { type: Boolean, default: false },
  },
  emits: ['tap', 'use', 'edit', 'delete', 'setDefault'],
  methods: {
    formatPhone(phone) {
      if (!phone) return ''
      const s = String(phone).replace(/\s+/g, '')
      if (s.length === 11) return s.replace(/(\d{3})(\d{4})(\d{4})/, '$1 $2 $3')
      return s
    },
    formatRegion(country, province, city) {
      return [country, province, city].filter(Boolean).join(' ')
    },
  },
}
</script>

<style lang="scss" scoped>
.address-card {
  position: relative;
  display: flex;
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  padding: var(--space-md) var(--space-md) 20rpx;
  margin-bottom: var(--space-sm);
  border: 2rpx solid transparent;
  box-shadow: var(--shadow-sm);
  overflow: hidden;
  transition:
    border-color 0.18s ease,
    background-color 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.14s ease;
}
.address-card:active {
  transform: scale(0.99);
}
/* 选中态：品牌主色描边 + 主色淡底，替代原先的蓝色底 */
.address-card.active {
  border-color: var(--color-primary);
  background: rgba(219, 201, 138, 0.12);
  box-shadow: var(--shadow-md);
}
/* 不可发货地址：灰底 + 降透明度 + 禁用点击反馈 */
.address-card.disabled {
  opacity: 0.55;
  filter: grayscale(0.4);
  background: var(--color-background);
  border-color: var(--color-divider);
  box-shadow: none;
}
.address-card.disabled:active {
  transform: none;
}
/* 不可发货橙色徽标 */
.unshippable-tag {
  padding: 4rpx 14rpx;
  border-radius: var(--radius-pill);
  font-size: var(--font-size-xs);
  line-height: 1.2;
  background: rgba(201, 110, 95, 0.18);
  color: var(--color-danger);
  border: 1rpx solid rgba(201, 110, 95, 0.4);
  font-weight: var(--font-weight-medium);
}

.address-card-rail {
  width: 8rpx;
  border-radius: var(--radius-pill);
  background: var(--color-divider);
  margin-right: var(--space-sm);
  flex-shrink: 0;
  transition: background-color 0.18s ease;
}
.address-card.active .address-card-rail {
  background: var(--color-primary);
}

.card-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}

.name-row {
  display: flex;
  align-items: center;
  column-gap: 14rpx;
  row-gap: 8rpx;
  flex-wrap: wrap;
}
.name {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}
.phone {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  letter-spacing: 1rpx;
}
/* 默认标签：主色淡底 + 主色深字，替代原「浅金底白字」保证可读性 */
.default-tag {
  padding: 4rpx 14rpx;
  background: rgba(219, 201, 138, 0.24);
  border: 1rpx solid rgba(219, 201, 138, 0.6);
  color: var(--color-primary-dark);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
  border-radius: var(--radius-pill);
  line-height: 1.2;
}
.tag {
  padding: 4rpx 14rpx;
  border-radius: var(--radius-pill);
  font-size: var(--font-size-xs);
  line-height: 1.2;
  background: var(--color-background);
  color: var(--color-text-secondary);
}
/* 分类标签统一使用品牌色系淡色底，替换原硬编码橙/蓝/灰 */
.tag-home {
  background: rgba(217, 180, 176, 0.22);
  color: var(--color-accent);
}
.tag-company {
  background: rgba(143, 168, 182, 0.22);
  color: var(--color-text-secondary);
}
.tag-other {
  background: var(--color-divider);
  color: var(--color-text-secondary);
}

.detail {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  line-height: 1.5;
  word-break: break-all;
}
.zip {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.actions {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: var(--space-xs);
  margin-top: var(--space-sm);
  padding-top: var(--space-sm);
  border-top: 1rpx solid var(--color-divider);
}
/* 操作项：胶囊幽灵按钮，热区更大、反馈更清晰 */
.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 6rpx;
  padding: 8rpx 20rpx;
  border-radius: var(--radius-pill);
  font-size: var(--font-size-xs);
  color: var(--color-primary-dark);
  background: var(--color-background);
  border: 1rpx solid transparent;
  line-height: 1.3;
  transition:
    background-color 0.18s ease,
    transform 0.12s ease;
}
.action-btn .luc {
  font-size: 22rpx;
}
.action-btn:active {
  background: var(--color-divider);
  transform: scale(0.95);
}
.action-btn.danger {
  color: var(--color-danger);
  background: rgba(201, 110, 95, 0.1);
}
.action-btn.danger:active {
  background: rgba(201, 110, 95, 0.2);
}

/* 结算场景「使用」按钮：内联在姓名行右侧，主色淡底 + 主色深字 */
.use-btn {
  margin-left: auto;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 6rpx;
  padding: 8rpx 22rpx;
  border-radius: var(--radius-pill);
  background: rgba(219, 201, 138, 0.18);
  border: 1rpx solid rgba(219, 201, 138, 0.6);
  color: var(--color-primary-dark);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
  transition:
    background-color 0.18s ease,
    transform 0.12s ease;
}
.use-btn:active {
  background: rgba(219, 201, 138, 0.32);
  transform: scale(0.95);
}
.use-btn .luc {
  font-size: 22rpx;
}
</style>
