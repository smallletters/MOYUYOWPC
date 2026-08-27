<template>
  <view class="address">
    <!-- 顶部导航栏：标题 + 新增收货地址（始终可见） -->
    <view class="header-bar">
      <view
        v-if="!fromCheckout"
        class="header-back"
        aria-label="返回"
        @click="goBack">
        <text class="luc luc-arrow-left" />
      </view>
      <view
        v-else
        class="header-back"
        aria-label="返回"
        @click="goBack">
        <text class="luc luc-x" />
      </view>
      <text class="title">收货地址</text>
      <view class="header-btn" @click="goEdit(null)">
        <text class="luc luc-plus" />
        <text class="header-btn-text">新增</text>
      </view>
    </view>

    <!-- 列表区 -->
    <scroll-view scroll-y class="list">
      <!-- 加载中 -->
      <view v-if="loading && addressList.length === 0" class="state-state">
        <text class="state-text">加载中…</text>
      </view>

      <!-- 空态 -->
      <view v-else-if="addressList.length === 0" class="empty">
        <text class="luc luc-map-pin empty-icon" />
        <text class="empty-title">还没有收货地址</text>
        <text class="empty-desc">添加地址后即可下单</text>
        <view class="btn btn-primary empty-btn" @click="goEdit(null)">+ 新增收货地址</view>
      </view>

      <!-- 地址卡 -->
      <view
        v-for="addr in addressList"
        :key="addr.id"
        class="card address-card"
        :class="{
          active: selectedId === addr.id,
          'from-checkout': fromCheckout,
        }"
        @click="onCardTap(addr)"
      >
        <!-- 左侧色条 / 选中态视觉锚 -->
        <view class="address-card-rail" />

        <view class="card-body">
          <view class="name-row">
            <text class="name">{{ addr.receiver }}</text>
            <text class="phone">{{ formatPhone(addr.phone) }}</text>
            <view v-if="addr.isDefault" class="default-tag">默认</view>
            <view v-if="addr.tag" class="tag" :class="`tag-${(addr.tag || '').toLowerCase()}`">
              {{ addr.tag }}
            </view>
          </view>
          <text class="detail">
            {{ formatRegion(addr.country, addr.province, addr.city) }} {{ addr.detail }}
          </text>
          <text v-if="addr.zipCode" class="zip">邮编 {{ addr.zipCode }}</text>

          <!-- 操作行：编辑 / 删除 / 设为默认 始终可见（满足增改删需求） -->
          <view class="actions">
            <text v-if="!addr.isDefault" class="action-btn" @click.stop="onSetDefault(addr)">
              设为默认
            </text>
            <text class="action-btn" @click.stop="goEdit(addr)">编辑</text>
            <text class="action-btn danger" @click.stop="onDelete(addr)">删除</text>
          </view>
        </view>

        <!-- 结算场景：右上角"使用此地址"按钮（点整张卡也会触发） -->
        <view v-if="fromCheckout" class="use-btn" @click.stop="onUseAddress(addr)">
          <text class="luc luc-check" />
          <text>使用</text>
        </view>
      </view>
    </scroll-view>

    <!-- 底部固定新增按钮（结算场景下便利触达） -->
    <view v-if="fromCheckout" class="footer-bar safe-area-bottom">
      <view class="btn btn-secondary footer-btn" @click="goEdit(null)">
        <text class="luc luc-plus footer-btn-icon" />
        <text>新增收货地址</text>
      </view>
    </view>
  </view>
</template>

<script>
import { addressApi } from '@/api'

export default {
  data() {
    return {
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
    this.loadAddresses()
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
        uni.showToast({ title: '已设为默认', icon: 'success' })
      } catch (e) {
        console.warn('[address] set default failed', e)
        uni.showToast({ title: '操作失败', icon: 'none' })
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
        title: '删除地址',
        content: `确认删除「${addr.receiver}」的收货地址？删除后不可恢复。`,
        confirmText: '删除',
        confirmColor: '#ff3b30',
        success: async (res) => {
          if (!res.confirm) return
          try {
            await addressApi.deleteAddress(addr.id)
            this.addressList = this.addressList.filter((a) => a.id !== addr.id)
            if (this.selectedId === addr.id) this.selectedId = ''
            uni.showToast({ title: '已删除', icon: 'success' })
          } catch (e) {
            console.warn('[address] delete failed', e)
            uni.showToast({ title: '删除失败', icon: 'none' })
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
}

/* ============ 顶部 ============ */
.header-bar {
  display: flex;
  align-items: center;
  height: 88rpx;
  padding: 0 24rpx;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
  position: sticky;
  top: 0;
  z-index: 10;
}
.header-back {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40rpx;
  color: var(--color-text);
}
.title {
  flex: 1;
  text-align: center;
  font-size: 32rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}
.header-btn {
  height: 56rpx;
  padding: 0 20rpx;
  display: inline-flex;
  align-items: center;
  gap: 6rpx;
  border-radius: 999px;
  background: var(--color-primary);
  color: #fff;
  font-size: 24rpx;
  font-weight: var(--font-weight-medium);
}
.header-btn .luc {
  font-size: 24rpx;
}
.header-btn-text {
  color: #fff;
  line-height: 1;
}

/* ============ 列表 ============ */
.list {
  /* 显式声明全宽，避免 h5 编译后内层 .uni-scroll-view 容器因没有宽度而塌缩 */
  display: block;
  width: 100%;
  flex: 1;
  padding: 16rpx;
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
  padding: 96rpx 0;
  text-align: center;
}
.state-text {
  font-size: 26rpx;
  color: var(--color-text-tertiary);
}

.empty {
  padding: 120rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
}
.empty-icon {
  font-size: 96rpx;
  color: var(--color-divider);
}
.empty-title {
  font-size: 30rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}
.empty-desc {
  font-size: 24rpx;
  color: var(--color-text-tertiary);
}
.empty-btn {
  margin-top: 16rpx;
  padding: 20rpx 56rpx;
  font-size: 28rpx;
}

/* ============ 地址卡 ============ */
.address-card {
  position: relative;
  display: flex;
  background: var(--color-surface);
  border-radius: var(--radius-lg, 24rpx);
  padding: 28rpx 28rpx 24rpx;
  margin-bottom: 16rpx;
  border: 2rpx solid transparent;
  transition:
    border-color 0.18s ease,
    transform 0.18s ease;
}
.address-card.active {
  border-color: var(--color-primary);
  background: #f5faff;
}
.address-card.from-checkout {
  padding-right: 132rpx; // 给右侧"使用"按钮留位
}

.address-card-rail {
  width: 6rpx;
  border-radius: 3rpx;
  background: var(--color-divider);
  margin-right: 20rpx;
  flex-shrink: 0;
}
.address-card.active .address-card-rail {
  background: var(--color-primary);
}

.card-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.name-row {
  display: flex;
  align-items: center;
  gap: 14rpx;
  flex-wrap: wrap;
}
.name {
  font-size: 30rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}
.phone {
  font-size: 24rpx;
  color: var(--color-text-tertiary);
}
.default-tag {
  padding: 4rpx 12rpx;
  background: var(--color-primary);
  color: #fff;
  font-size: 20rpx;
  border-radius: 999px;
  line-height: 1.2;
}
.tag {
  padding: 4rpx 12rpx;
  border-radius: 999px;
  font-size: 20rpx;
  line-height: 1.2;
  background: var(--color-background);
  color: var(--color-text-secondary);
}
.tag-home {
  background: #fff4e5;
  color: #ff9500;
}
.tag-company {
  background: #e8f2ff;
  color: #007aff;
}
.tag-other {
  background: #f2f2f7;
  color: #6e6e73;
}

.detail {
  font-size: 26rpx;
  color: var(--color-text-secondary);
  line-height: 1.5;
  word-break: break-all;
}
.zip {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}

.actions {
  display: flex;
  gap: 24rpx;
  margin-top: 16rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid var(--color-divider);
}
.action-btn {
  font-size: 24rpx;
  color: var(--color-primary-dark);
  padding: 6rpx 0;
}
.action-btn.danger {
  color: var(--color-danger);
}

.use-btn {
  position: absolute;
  top: 50%;
  right: 24rpx;
  transform: translateY(-50%);
  display: inline-flex;
  align-items: center;
  gap: 6rpx;
  padding: 12rpx 24rpx;
  border-radius: 999px;
  background: var(--color-primary);
  color: #fff;
  font-size: 24rpx;
  font-weight: var(--font-weight-medium);
}
.address-card.active .use-btn {
  background: var(--color-primary);
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
  padding: 16rpx 24rpx;
  background: var(--color-surface);
  border-top: 1rpx solid var(--color-divider);
  display: flex;
  align-items: center;
  justify-content: center;
}
.footer-btn {
  width: 100%;
  height: 88rpx;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  font-size: 28rpx;
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
  font-size: 26rpx;
  border: 1rpx solid transparent;
  background: var(--color-surface);
  color: var(--color-text);
}
.btn-primary {
  background: var(--color-primary);
  color: #fff;
  border-color: var(--color-primary);
}
.btn-secondary {
  background: #f2f2f7;
  color: var(--color-text);
  border-color: var(--color-divider);
}
</style>
