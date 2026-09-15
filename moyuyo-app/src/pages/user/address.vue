<template>
  <view class="address">
    <!-- 顶部导航栏：标题 + 新增收货地址（始终可见） -->
    <view class="navbar">
      <view class="header-back" :aria-label="$t('common.back')" @click="goBack">
        <text class="luc luc-x" />
      </view>
      <text class="title">{{ $t('address.title') }}</text>
      <view class="header-btn" @click="goEdit(null)">
        <text class="luc luc-plus" />
        <text class="header-btn-text">{{ $t('address.new') }}</text>
      </view>
    </view>

    <!-- 列表区 -->
    <scroll-view scroll-y class="list">
      <!-- 加载中 -->
      <view v-if="loading && addressList.length === 0" class="state-state">
        <text class="state-text">{{ $t('address.loading') }}</text>
      </view>

      <!-- 空态 -->
      <view v-else-if="addressList.length === 0" class="empty">
        <view class="empty-icon">
          <text class="luc luc-map-pin" />
        </view>
        <text class="empty-title">{{ $t('address.emptyTitle') }}</text>
        <text class="empty-desc">{{ $t('address.emptyDesc') }}</text>
        <view class="btn btn-primary empty-btn" @click="goEdit(null)">
          {{ $t('address.addButton') }}
        </view>
      </view>

      <!-- 地址卡 -->
      <view
        v-for="addr in addressList"
        :key="addr.id"
        class="card address-card"
        :class="{ active: selectedId === addr.id }"
        @click="onCardTap(addr)"
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
            <!-- 结算场景:「使用」按钮内联在姓名行右侧,避免绝对定位浮在地址详情上 -->
            <view v-if="fromCheckout" class="use-btn" @click.stop="onUseAddress(addr)">
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
            <view v-if="!addr.isDefault" class="action-btn" @click.stop="onSetDefault(addr)">
              <text class="luc luc-star" />
              <text>{{ $t('address.setDefault') }}</text>
            </view>
            <view class="action-btn" @click.stop="goEdit(addr)">
              <text class="luc luc-pencil" />
              <text>{{ $t('address.edit') }}</text>
            </view>
            <view class="action-btn danger" @click.stop="onDelete(addr)">
              <text class="luc luc-trash-2" />
              <text>{{ $t('address.delete') }}</text>
            </view>
          </view>
        </view>
      </view>
    </scroll-view>

    <!-- 底部固定新增按钮（结算场景下便利触达） -->
    <view v-if="fromCheckout" class="footer-bar safe-area-bottom">
      <view class="btn btn-secondary footer-btn" @click="goEdit(null)">
        <text class="luc luc-plus footer-btn-icon" />
        <text>{{ $t('address.add') }}</text>
      </view>
    </view>
  </view>
</template>

<script>
import { addressApi } from '@/api'
import { i18n } from '@/i18n'

export default {
  pageTitleKey: 'pageTitle.userAddress',

  data() {
    return {
      localeVersion: 0,
      addressList: [],
      selectedId: '',
      fromCheckout: false,
      loading: false,
    }
  },

  onLoad(query) {
    this.fromCheckout = query.from === 'checkout'
    // 结算场景：如果有上次选中地址，预先标记
    if (this.fromCheckout) {
      try {
        const cached = uni.getStorageSync('moyuyo_selected_address')
        if (cached && cached.id) this.selectedId = cached.id
      } catch (e) {
        // ignore
      }
    }
    // 订阅语言切换（当前页面文案均在模板里直读 $t,这里订阅是为了扩展一致性）
    this._unsubLocale = i18n.subscribe(() => {
      this.localeVersion += 1
    })
    this.loadAddresses()
  },
  onUnload() {
    if (this._unsubLocale) this._unsubLocale()
  },

  methods: {
    async loadAddresses() {
      this.loading = true
      try {
        this.addressList = (await addressApi.getAddressList()) || []
      } catch (e) {
        console.warn('[address] load failed', e)
        this.addressList = []
      } finally {
        this.loading = false
      }
    },

    /** 点击整张卡：结算模式直接使用；管理模式仅高亮 */
    onCardTap(addr) {
      if (this.fromCheckout) {
        this.onUseAddress(addr)
      } else {
        this.selectedId = addr.id
      }
    },

    /** 结算模式：把选中地址写入 storage 并返回上一页 */
    onUseAddress(addr) {
      try {
        uni.setStorageSync('moyuyo_selected_address', addr)
      } catch (e) {
        console.warn('[address] save selected failed', e)
      }
      uni.navigateBack({ delta: 1, fail: () => uni.switchTab({ url: '/pages/tabbar/user' }) })
    },

    /** 设为默认地址 */
    async onSetDefault(addr) {
      try {
        await addressApi.setDefaultAddress(addr.id)
        this.addressList = this.addressList.map((a) => ({ ...a, isDefault: a.id === addr.id }))
        uni.showToast({ title: i18n.t('address.setDefaultSuccess'), icon: 'success' })
      } catch (e) {
        console.warn('[address] set default failed', e)
        uni.showToast({ title: i18n.t('address.setDefaultFailed'), icon: 'none' })
      }
    },

    /** 进入新增 / 编辑页 */
    goEdit(addr) {
      const url = addr ? `/pages/user/address-edit?id=${addr.id}` : '/pages/user/address-edit'
      uni.navigateTo({ url })
    },

    /** 删除地址 */
    onDelete(addr) {
      uni.showModal({
        title: i18n.t('address.deleteModalTitle'),
        content: i18n.t('address.deleteModalContent', { name: addr.receiver }),
        confirmText: i18n.t('address.deleteConfirm'),
        confirmColor: '#ff3b30',
        success: async (res) => {
          if (!res.confirm) return
          try {
            await addressApi.deleteAddress(addr.id)
            this.addressList = this.addressList.filter((a) => a.id !== addr.id)
            if (this.selectedId === addr.id) this.selectedId = ''
            uni.showToast({ title: i18n.t('address.deleted'), icon: 'success' })
          } catch (e) {
            console.warn('[address] delete failed', e)
            uni.showToast({ title: i18n.t('address.deleteFailed'), icon: 'none' })
          }
        },
      })
    },

    goBack() {
      const pages = getCurrentPages()
      if (pages.length > 1) {
        uni.navigateBack({ delta: 1 })
      } else {
        uni.switchTab({ url: '/pages/tabbar/user' })
      }
    },

    formatPhone(phone) {
      if (!phone) return ''
      // 简单分组：138 1234 5678（11 位中国大陆手机号）
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
.address {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: var(--color-background);
  padding-bottom: 160rpx; // 给底部"新增收货地址"按钮留出空间
  box-sizing: border-box;
}

/* ============ 顶部导航栏 ============ */
.navbar {
  position: relative;
  z-index: 10;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  /* 高度含顶部安全区（H5 下 --status-bar-height 为 0，可安全兜底） */
  min-height: calc(96rpx + env(safe-area-inset-top, 0px) + var(--status-bar-height, 0px));
  padding: calc(env(safe-area-inset-top, 0px) + var(--status-bar-height, 0px)) var(--space-md) 0;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
  box-shadow: var(--shadow-sm);
  box-sizing: border-box;
}
.header-back {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  font-size: 40rpx;
  color: var(--color-text);
  transition:
    background-color 0.18s ease,
    transform 0.12s ease;
}
.header-back:active {
  background: var(--color-divider);
  transform: scale(0.94);
}
.title {
  flex: 1;
  text-align: center;
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  letter-spacing: 1rpx;
}
/* 新增按钮：主色淡底 + 主色深字，规避浅金底白字的对比度不足 */
.header-btn {
  height: 56rpx;
  padding: 0 22rpx;
  display: inline-flex;
  align-items: center;
  gap: 6rpx;
  border-radius: var(--radius-pill);
  background: rgba(219, 201, 138, 0.16);
  border: 1rpx solid rgba(219, 201, 138, 0.6);
  color: var(--color-primary-dark);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
  transition:
    background-color 0.18s ease,
    transform 0.12s ease;
}
.header-btn:active {
  background: rgba(219, 201, 138, 0.3);
  transform: scale(0.96);
}
.header-btn .luc {
  font-size: 24rpx;
}
.header-btn-text {
  color: var(--color-primary-dark);
  line-height: 1;
}

/* ============ 列表 ============ */
.list {
  /* 显式声明全宽，避免 h5 编译后内层 .uni-scroll-view 容器因没有宽度而塌缩 */
  display: block;
  width: 100%;
  flex: 1;
  padding: var(--space-sm);
  box-sizing: border-box;
}
/* uni-app h5 编译后会在 scroll-view 内嵌套一层 .uni-scroll-view 容器，需要让它也撑满 */
.list ::v-deep .uni-scroll-view,
.list ::v-deep .uni-scroll-view-content {
  display: block;
  width: 100%;
  min-height: 100%;
  box-sizing: border-box;
}
.state-state {
  padding: 120rpx 0;
  text-align: center;
}
/* 加载态：纯 CSS 旋转指示器，无需新增模板节点 */
.state-state::before {
  content: '';
  display: block;
  width: 48rpx;
  height: 48rpx;
  margin: 0 auto var(--space-sm);
  border-radius: 50%;
  border: 4rpx solid var(--color-divider);
  border-top-color: var(--color-primary);
  animation: addr-spin 0.8s linear infinite;
}
@keyframes addr-spin {
  to {
    transform: rotate(360deg);
  }
}
.state-text {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.empty {
  padding: 120rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-xs);
}
/* 空态图标：柔和圆形底，与页面层级拉开 */
.empty-icon {
  width: 160rpx;
  height: 160rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: var(--color-primary-dark);
  background: rgba(219, 201, 138, 0.14);
  margin-bottom: var(--space-xs);
}
.empty-icon .luc {
  font-size: 72rpx;
}
.empty-title {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}
.empty-desc {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
  text-align: center;
  padding: 0 var(--space-xl);
}
.empty-btn {
  margin-top: var(--space-sm);
  padding: 20rpx 56rpx;
  font-size: var(--font-size-base);
}

/* ============ 地址卡 ============ */
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

/* ============ 底部 ============ */
.footer-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 20;
  /* 底部安全区在此显式计算，避免被 padding 简写覆盖 */
  padding: var(--space-sm) var(--space-md) calc(var(--space-sm) + env(safe-area-inset-bottom, 0px));
  background: var(--color-surface);
  border-top: 1rpx solid var(--color-divider);
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
}
.footer-btn {
  width: 100%;
  height: 88rpx;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
}
.footer-btn-icon {
  font-size: 26rpx;
}

/* 通用按钮 */
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 12rpx 32rpx;
  border-radius: var(--radius-pill);
  font-size: var(--font-size-sm);
  border: 1rpx solid transparent;
  background: var(--color-surface);
  color: var(--color-text);
  transition:
    background-color 0.18s ease,
    transform 0.12s ease;
}
.btn:active {
  transform: scale(0.97);
}
/* 主 CTA：改为主色淡底 + 主色深字，替换浅金底白字 */
.btn-primary {
  background: rgba(219, 201, 138, 0.18);
  border-color: rgba(219, 201, 138, 0.6);
  color: var(--color-primary-dark);
}
.btn-primary:active {
  background: rgba(219, 201, 138, 0.32);
}
.btn-secondary {
  background: var(--color-background);
  color: var(--color-text);
  border-color: var(--color-divider);
}
.btn-secondary:active {
  background: var(--color-divider);
}
</style>
