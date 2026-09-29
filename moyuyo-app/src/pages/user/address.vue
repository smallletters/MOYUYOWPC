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

      <!-- 地址卡：抽到 AddressCard.vue 子组件，父组件只负责列表 + 状态 -->
      <AddressCard
        v-for="addr in addressList"
        :key="addr.id"
        :addr="addr"
        :selected="selectedId === addr.id"
        :unshippable="isUnshippable(addr)"
        :from-checkout="fromCheckout"
        @tap="onCardTap"
        @use="onUseAddress"
        @edit="goEdit"
        @delete="onDelete"
        @set-default="onSetDefault"
      />
    </scroll-view>

    <!-- 底部固定新增按钮（结算场景下便利触达） -->
    <view v-if="fromCheckout" class="footer-bar safe-area-bottom">
      <view class="btn btn-secondary footer-btn" @click="goEdit(null)">
        <text class="luc luc-plus footer-btn-icon" />
        <text>{{ $t('address.add') }}</text>
      </view>
    </view>
    <!-- 全部不可发货时给结算场景一个明确提示 -->
    <view
      v-else-if="!loading && addressList.length > 0 && unshippableIds.length === addressList.length"
      class="footer-bar safe-area-bottom footer-warn"
    >
      <text class="footer-warn-text">{{ $t('address.allUnshippableHint') }}</text>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { addressApi } from '@/api'
import AddressCard from '@/components/AddressCard.vue'
import { i18n } from '@/i18n'

defineOptions({ name: 'UserAddress' })

// ==================== 状态 ====================

const addressList = ref([])
// 不可发的地址 id 集合（命中后置灰禁用）
const unshippableIds = ref([])
const selectedId = ref('')
const fromCheckout = ref(false)
const loading = ref(false)

/** 至少有一个可发货地址时，下单流程才允许继续 */
const hasShippable = computed(() =>
  addressList.value.some((a) => !unshippableIds.value.includes(a.id)),
)

function isUnshippable(addr) {
  return unshippableIds.value.includes(addr.id)
}

// ==================== 生命周期 ====================

onMounted(async () => {
  // 读 URL ?from=checkout
  try {
    const pages = getCurrentPages()
    const cur = pages[pages.length - 1]
    const query = (cur && cur.options) || {}
    fromCheckout.value = query.from === 'checkout'
  } catch (e) {
    console.warn('[address] read page query failed', e)
  }
  // 结算场景：如果有上次选中地址，预先标记
  if (fromCheckout.value) {
    try {
      const cached = uni.getStorageSync('moyuyo_selected_address')
      if (cached && cached.id) selectedId.value = cached.id
    } catch (e) {
      // ignore
    }
  }
  await loadAddresses()
})

async function loadAddresses() {
  loading.value = true
  try {
    const list = (await addressApi.getAddressList()) || []
    addressList.value = list
    // 批量校验：1 次请求替代 N 次 N+1；接口失败时保守认为全部可发货，由后端 checkout 兜底
    unshippableIds.value = await batchValidate(list)
  } catch (e) {
    console.warn('[address] load failed', e)
    // 出错时同时清空列表与不可发集合，避免下次校验前 isUnshippable(addr) 仍以旧数据为准
    addressList.value = []
    unshippableIds.value = []
  } finally {
    loading.value = false
  }
}

/**
 * 批量校验地址可发货性。后端 /api/v1/addresses/batch-validate 替代
 * 原先 N 次 Promise.all(validateAddress) 的 N+1 调用方式。
 * - 失败时保守视作全部可发货（依赖后端 checkout 兜底）
 */
async function batchValidate(list) {
  if (!list || list.length === 0) return []
  try {
    const items = await addressApi.batchValidateAddresses(list.map((a) => a.id))
    return (items || []).filter((it) => !it.shippable).map((it) => it.addressId)
  } catch (e) {
    console.warn('[address] batch validate failed, fallback to lenient', e)
    return []
  }
}

// ==================== 事件 ====================

/** 点击整张卡：结算模式直接使用；管理模式仅高亮 */
function onCardTap(addr) {
  if (isUnshippable(addr)) {
    uni.showToast({ title: i18n.t('address.unshippable'), icon: 'none' })
    return
  }
  if (fromCheckout.value) {
    onUseAddress(addr)
  } else {
    selectedId.value = addr.id
  }
}

/** 结算模式：把选中地址写入 storage 并返回上一页 */
function onUseAddress(addr) {
  if (isUnshippable(addr)) {
    uni.showToast({ title: i18n.t('address.unshippable'), icon: 'none' })
    return
  }
  try {
    uni.setStorageSync('moyuyo_selected_address', addr)
  } catch (e) {
    console.warn('[address] save selected failed', e)
  }
  uni.navigateBack({ delta: 1, fail: () => uni.switchTab({ url: '/pages/tabbar/user' }) })
}

/** 设为默认地址 */
async function onSetDefault(addr) {
  try {
    await addressApi.setDefaultAddress(addr.id)
    addressList.value = addressList.value.map((a) => ({ ...a, isDefault: a.id === addr.id }))
    uni.showToast({ title: i18n.t('address.setDefaultSuccess'), icon: 'success' })
  } catch (e) {
    console.warn('[address] set default failed', e)
    uni.showToast({ title: i18n.t('address.setDefaultFailed'), icon: 'none' })
  }
}

/** 进入新增 / 编辑页 */
function goEdit(addr) {
  const url = addr ? `/pages/user/address-edit?id=${addr.id}` : '/pages/user/address-edit'
  uni.navigateTo({ url })
}

/** 删除地址 */
function onDelete(addr) {
  uni.showModal({
    title: i18n.t('address.deleteModalTitle'),
    content: i18n.t('address.deleteModalContent', { name: addr.receiver }),
    confirmText: i18n.t('address.deleteConfirm'),
    confirmColor: '#ff3b30',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await addressApi.deleteAddress(addr.id)
        addressList.value = addressList.value.filter((a) => a.id !== addr.id)
        if (selectedId.value === addr.id) selectedId.value = ''
        uni.showToast({ title: i18n.t('address.deleted'), icon: 'success' })
      } catch (e) {
        console.warn('[address] delete failed', e)
        uni.showToast({ title: i18n.t('address.deleteFailed'), icon: 'none' })
      }
    },
  })
}

function goBack() {
  const pages = getCurrentPages()
  if (pages.length > 1) {
    uni.navigateBack({ delta: 1 })
  } else {
    uni.switchTab({ url: '/pages/tabbar/user' })
  }
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

/* 地址卡样式已搬到 AddressCard.vue 子组件（.address-card / .use-btn / .action-btn / .unshippable-tag / ...）*/

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
/* 全部不可发货时的红色提示条 */
.footer-warn {
  background: rgba(201, 110, 95, 0.08);
  border-top-color: rgba(201, 110, 95, 0.3);
}
.footer-warn-text {
  font-size: var(--font-size-sm);
  color: var(--color-danger);
  line-height: 1.4;
  text-align: center;
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
