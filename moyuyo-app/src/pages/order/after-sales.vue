<template>
  <view class="after-sales">
    <view class="header">
      <view class="header-btn" @click="goBack">
        <text class="back-icon luc-arrow-left" />
      </view>
      <text class="header-title">{{ $t('afterSales.title') }}</text>
      <view class="header-btn right" @click="onApply">
        <text class="apply-text">{{ $t('afterSales.apply') }}</text>
      </view>
    </view>

    <view class="tabs">
      <view
        v-for="t in tabs"
        :key="t.value"
        class="tab"
        :class="{ active: activeTab === t.value }"
        @click="activeTab = t.value"
      >
        <text>{{ t.label }}</text>
        <view v-if="activeTab === t.value" class="tab-indicator" />
      </view>
    </view>

    <scroll-view scroll-y class="scroll" @scrolltolower="onLoadMore">
      <view v-if="!loading && filteredList.length === 0" class="empty-state">
        <view class="empty-icon">
          <view class="empty-box">
            <view class="empty-file">
              <view class="empty-line short" />
              <view class="empty-line medium" />
              <view class="empty-line long" />
            </view>
          </view>
          <view class="empty-search">
            <text class="search-icon luc luc-search" />
          </view>
        </view>
        <text class="empty-title">{{ $t('afterSales.empty') }}</text>
        <text class="empty-desc">{{ $t('afterSales.emptyDesc') }}</text>
        <view class="empty-btn" @click="goShopping">{{ $t('afterSales.goShopping') }}</view>
      </view>

      <view
        v-for="item in filteredList"
        :key="item.id"
        class="card"
        @click="goDetail(item)">
        <view class="card-header">
          <view class="card-tags">
            <text class="tag-status" :class="statusClass(item.status)">
              {{ statusLabel(item) }}
            </text>
            <text class="tag-type">{{ typeLabel(item) }}</text>
          </view>
          <text class="card-arrow luc-chevron-right" />
        </view>

        <view class="card-body">
          <image :src="resolveItemImage(item.mainImage)" mode="aspectFill" class="card-img" />
          <view class="card-info">
            <text class="card-name">{{ item.productName || '-' }}</text>
            <text class="card-sku">{{ item.skuSpec || '' }}</text>
          </view>
        </view>

        <view class="card-divider" />

        <view class="card-footer">
          <view class="card-amount">
            <text class="amount-label">{{ $t('afterSales.refundAmount') }}</text>
            <text v-if="item.type !== 'EXCHANGE'" class="amount-value">-${{ item.amount }}</text>
            <text v-else class="amount-value">{{ $t('afterSales.noRefund') }}</text>
          </view>
          <text class="card-time">{{ formatDate(item.createTime) }}</text>
        </view>

        <view class="card-progress">
          <view class="progress-bar">
            <view
              v-for="(step, idx) in progressOf(item).steps"
              :key="idx"
              class="progress-step"
              :class="{
                filled: idx < progressOf(item).index,
                current: idx === progressOf(item).index,
              }"
            />
          </view>
          <view class="progress-labels">
            <text
              v-for="(label, idx) in progressOf(item).steps"
              :key="idx"
              class="progress-label"
              :class="{ active: idx <= progressOf(item).index }"
            >
              {{ label }}
            </text>
          </view>
        </view>

        <!-- 审核通过且需寄回：列表内直接填写退货运单号，少一级跳转 -->
        <view v-if="canFillLogistics(item)" class="card-action">
          <view class="mini-btn" @click.stop="openLogistics(item)">
            {{ $t('afterSalesDetail.fillLogistics') }}
          </view>
        </view>
      </view>

      <view v-if="loading" class="loading">{{ $t('common.loading') }}</view>
      <view v-else-if="noMore && filteredList.length > 0" class="loading">
        — {{ $t('afterSales.noMore') }} —
      </view>

      <view class="policy-section">
        <view class="policy-header" @click="showPolicy = !showPolicy">
          <text class="policy-title">{{ $t('afterSales.policyTitle') }}</text>
          <text class="policy-toggle">
            {{ showPolicy ? $t('afterSales.policyCollapse') : $t('afterSales.policyExpand') }}
          </text>
        </view>
        <view v-if="showPolicy" class="policy-content">
          <text class="policy-text">{{ $t('afterSales.policyText') }}</text>
          <view class="policy-contact">
            <text class="contact-text">{{ $t('afterSales.policyEmail') }}</text>
          </view>
        </view>
      </view>
    </scroll-view>

    <!-- 填写退货物流弹层（列表内直接操作） -->
    <view v-if="logisticsTarget" class="sheet-mask" @click="closeLogistics">
      <view class="sheet" @click.stop>
        <view class="sheet-header">
          <text class="sheet-title">{{ $t('afterSalesDetail.logisticsDialogTitle') }}</text>
          <text class="sheet-close luc luc-x" @click="closeLogistics" />
        </view>
        <text class="sheet-field-label">{{ $t('afterSalesDetail.logisticsCompanyLabel') }}</text>
        <picker :value="companyIndex" :range="companyOptions" @change="onCompanyChange">
          <view class="sheet-picker">
            <text :class="{ placeholder: !selectedCompany }">
              {{ selectedCompany || $t('afterSalesDetail.companyPlaceholder') }}
            </text>
            <text class="arrow luc-chevron-right" />
          </view>
        </picker>
        <!-- 选择"其他"时允许手动输入承运商名称 -->
        <input
          v-if="isOtherCarrier"
          v-model="customCompany"
          class="sheet-input"
          :placeholder="$t('afterSalesDetail.customCarrierPlaceholder')"
          :maxlength="50"
        >

        <text class="sheet-field-label">{{ $t('afterSalesDetail.trackingNoLabel') }}</text>
        <view class="sheet-input-wrap">
          <input
            v-model="logisticsForm.trackingNo"
            class="sheet-input"
            type="text"
            :maxlength="40"
            :placeholder="$t('afterSalesDetail.trackingPlaceholder')"
            @blur="onTrackingBlur"
          >
          <text
            v-if="logisticsForm.trackingNo"
            class="sheet-input-clear luc luc-x"
            @click="logisticsForm.trackingNo = ''"
          />
        </view>
        <text class="sheet-hint">{{ $t('afterSalesDetail.trackingHint') }}</text>
        <view class="sheet-actions">
          <view class="btn-outline" @click="closeLogistics">
            {{ $t('afterSalesDetail.cancel') }}
          </view>
          <view class="btn-primary" :class="{ disabled: submitting }" @click="submitLogistics">
            {{ submitting ? $t('afterSalesDetail.submitting') : $t('afterSalesDetail.submit') }}
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { afterSalesApi } from '@/api'
import { i18n, t } from '@/i18n'
import { toAbsoluteImageUrl } from '@/utils/imageUrl'

// tab 与服务端状态筛选参数一一对应，避免前端本地过滤导致分页数据丢失
const TAB_STATUS = {
  all: 'ALL',
  pending: 'PENDING',
  processing: 'PROCESSING',
  completed: 'COMPLETED',
  closed: 'CLOSED',
}

export default {
  pageTitleKey: 'pageTitle.orderAfterSales',

  data() {
    return {
      activeTab: 'all',
      showPolicy: false,
      loading: false,
      noMore: false,
      page: 1,
      // 订单维度筛选：从订单详情进入时只展示该订单的售后记录
      orderId: null,
      records: [],
      // 退货物流弹层
      logisticsTarget: null,
      submitting: false,
      // 常用承运商；首项占位与末尾"其他"由 computed 组装，选中后可手动输入公司名
      carriers: ['UPS', 'USPS', 'FedEx', 'DHL', '顺丰速运'],
      // 0 指向占位项"请选择物流公司"，避免用户不操作时被默认记成 UPS
      companyIndex: 0,
      customCompany: '',
      logisticsForm: {
        trackingNo: '',
      },
    }
  },

  computed: {
    /** tab 文案走 i18n，切换语言时随 locale 响应式刷新 */
    tabs() {
      return [
        { label: t('afterSales.tabs.all'), value: 'all' },
        { label: t('afterSales.tabs.pending'), value: 'pending' },
        { label: t('afterSales.tabs.processing'), value: 'processing' },
        { label: t('afterSales.tabs.completed'), value: 'completed' },
        { label: t('afterSales.tabs.closed'), value: 'closed' },
      ]
    },

    filteredList() {
      // 状态筛选已下推到服务端，此处直接展示当前页数据
      return this.records
    },

    /** 当前 tab 对应的服务端筛选值 */
    currentStatus() {
      return TAB_STATUS[this.activeTab] || 'ALL'
    },

    /** 承运商选项：首项为占位项，避免 picker 默认选中某个真实承运商 */
    companyOptions() {
      return [
        t('afterSalesDetail.companyPlaceholder'),
        ...this.carriers,
        t('afterSalesDetail.otherCarrier'),
      ]
    },

    /** "其他"在选项列表中的下标（占位项占用了 0） */
    isOtherCarrier() {
      return this.companyIndex === this.carriers.length + 1
    },

    selectedCompany() {
      if (this.companyIndex <= 0) return ''
      if (this.isOtherCarrier) return this.customCompany.trim()
      return this.carriers[this.companyIndex - 1] || ''
    },
  },

  watch: {
    activeTab() {
      // 切换 tab 时先清空旧数据，避免加载期间展示上一状态的数据
      this.records = []
      this.reload()
    },
  },

  onLoad(query) {
    this.orderId = (query && query.orderId) || null
  },

  onShow() {
    // 首次进入与从申请页/详情页返回时都刷新，避免新提交的售后不显示
    this.reload()
  },

  methods: {
    /**
     * 商品图：后端返回相对路径(/uploads/...)，APP 端 <image> 不走 Vite proxy，
     * 必须补成绝对地址，否则真机上商品图空白。
     */
    resolveItemImage(url) {
      return toAbsoluteImageUrl(url) || ''
    },

    goBack() {
      uni.navigateBack()
    },

    formatDate(value) {
      if (!value) return ''
      const d = new Date(value)
      if (Number.isNaN(d.getTime())) return String(value)
      return d.toLocaleDateString(i18n.locale)
    },

    statusLabel(item) {
      // 仅退款没有寄回/收货环节，审核通过后统一显示"待退款"
      if (
        item.type === 'REFUND_ONLY' &&
        (item.status === 'RECEIVED' ||
          (item.status === 'APPROVED' && item.deliveryType === 'NO_RETURN'))
      ) {
        return t('afterSales.refundPending')
      }
      const key = `afterSales.status.${item.status}`
      const label = t(key)
      return label && label !== key ? label : item.statusLabel || item.status
    },

    typeLabel(item) {
      const key = `afterSales.types.${item.type}`
      const label = t(key)
      return label && label !== key ? label : item.typeLabel || item.type
    },

    statusClass(status) {
      if (status === 'PENDING') return 'tag-pending'
      if (status === 'COMPLETED') return 'tag-completed'
      if (status === 'REJECTED' || status === 'CANCELLED') return 'tag-closed'
      return 'tag-processing'
    },

    /**
     * 进度节点：仅退款 3 步（申请→审核→退款），退货退款/换货 5 步
     * index 来自后端 progressIndex，拒绝/取消为 -1 不点亮任何节点
     */
    progressOf(item) {
      const submit = t('afterSalesDetail.progress.submit')
      const review = t('afterSalesDetail.progress.review')
      const refund = t('afterSalesDetail.progress.refund')
      const ship = t('afterSalesDetail.progress.ship')
      const receive = t('afterSalesDetail.progress.receive')
      const finish = t('afterSalesDetail.progress.finish')
      const steps =
        item.type === 'REFUND_ONLY'
          ? [submit, review, refund]
          : [submit, review, ship, receive, item.type === 'EXCHANGE' ? finish : refund]
      return { steps, index: item.progressIndex == null ? 0 : Number(item.progressIndex) }
    },

    /** 重置分页并重新加载（切 tab / 页面重新显示时使用） */
    reload() {
      this.page = 1
      this.noMore = false
      this.loadAfterSales()
    },

    async loadAfterSales() {
      this.loading = true
      try {
        const size = 10
        const res = await afterSalesApi.getAfterSales({
          page: this.page,
          size,
          status: this.currentStatus,
          orderId: this.orderId || undefined,
        })
        // 后端返回 MyBatis-Plus 分页对象 { records, total, current, size }
        const data = (res && res.records) || []
        this.records = this.page === 1 ? data : [...this.records, ...data]
        this.noMore = data.length < size
      } catch (err) {
        uni.showToast({ title: this.$t('afterSales.loadFailed'), icon: 'none' })
      } finally {
        this.loading = false
      }
    },

    onApply() {
      // 从售后记录进入时没有订单上下文，申请页会先让用户选择订单；
      // 若当前已按订单筛选，则直接带入该订单
      const url = this.orderId
        ? `/pages/order/returns?orderId=${this.orderId}`
        : '/pages/order/returns'
      uni.navigateTo({ url })
    },

    onLoadMore() {
      if (this.loading || this.noMore) return
      this.page++
      this.loadAfterSales()
    },

    goDetail(item) {
      uni.navigateTo({ url: `/pages/order/after-sales-detail?id=${item.id}` })
    },

    /** 待寄回、已寄回都可填写/更正运单号 */
    canFillLogistics(item) {
      return Boolean(
        item &&
        item.deliveryType === 'RETURN_SHIP' &&
        ['APPROVED', 'RETURNING'].includes(item.status),
      )
    },

    openLogistics(item) {
      this.logisticsTarget = item
      const company = item.returnCompany || ''
      const idx = this.carriers.indexOf(company)
      // 已填过物流时预填原值，便于修正填错的运单号
      if (company && idx === -1) {
        // 历史物流不在常用列表：落到"其他"并回填名称
        this.companyIndex = this.carriers.length + 1
        this.customCompany = company
      } else {
        // 无历史物流保持 0（占位项），有历史则映射到对应常用承运商
        this.companyIndex = idx >= 0 ? idx + 1 : 0
        this.customCompany = ''
      }
      this.logisticsForm.trackingNo = item.returnTrackingNo || ''
    },

    closeLogistics() {
      if (this.submitting) return
      this.logisticsTarget = null
    },

    onCompanyChange(e) {
      this.companyIndex = Number(e.detail.value)
      if (!this.isOtherCarrier) this.customCompany = ''
    },

    /**
     * 运单号失焦归一化：统一转大写并去空格。
     * 不能放在 @input 里实时改写——uni-app H5 输入框对 value 有 100ms 防抖回写，
     * 父组件改写后的值会晚于用户输入到达，把刚输入的内容覆盖成旧值，表现为"无法输入"。
     */
    onTrackingBlur(e) {
      const raw = String((e && e.detail && e.detail.value) || this.logisticsForm.trackingNo || '')
      this.logisticsForm.trackingNo = raw.replace(/\s+/g, '').toUpperCase()
    },

    async submitLogistics() {
      if (!this.logisticsTarget || this.submitting) return
      const company = this.selectedCompany
      // 与后端保持一致：去空格、转大写后再校验
      const trackingNo = (this.logisticsForm.trackingNo || '').replace(/\s+/g, '').toUpperCase()
      if (!company || !trackingNo) {
        uni.showToast({ title: t('afterSalesDetail.logisticsRequired'), icon: 'none' })
        return
      }
      // 与后端保持一致：6-40 位字母、数字或连字符
      if (!/^[A-Z0-9-]{6,40}$/.test(trackingNo)) {
        uni.showToast({ title: t('afterSalesDetail.invalidTrackingNo'), icon: 'none' })
        return
      }
      this.submitting = true
      try {
        await afterSalesApi.submitReturnLogistics(this.logisticsTarget.id, {
          company,
          trackingNo,
        })
        uni.showToast({ title: t('afterSalesDetail.logisticsSubmitted'), icon: 'success' })
        this.logisticsTarget = null
        // 重新拉取列表，状态更新为"已寄回待收货"
        this.reload()
      } catch (e) {
        uni.showToast({ title: e.message || t('afterSalesDetail.logisticsFailed'), icon: 'none' })
      } finally {
        this.submitting = false
      }
    },

    goShopping() {
      uni.switchTab({ url: '/pages/tabbar/home' })
    },
  },
}
</script>

<style lang="scss" scoped>
.after-sales {
  width: 100%;
  box-sizing: border-box;
  min-height: 100vh;
  background: var(--color-background);
  padding-bottom: 48rpx;
  /* 禁止整页横向滚动：任何超宽子元素都不能把容器撑出屏幕 */
  overflow-x: hidden;
}

.header {
  position: sticky;
  top: 0;
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
  left: 16rpx;
  /* 绝对定位元素吃不到父级 padding，需自行下移状态栏高度 */
  top: var(--status-bar-height, 0px);
  top: max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px));
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 72rpx;
  height: 72rpx;
  border-radius: var(--radius-sm);
}

.header-btn.right {
  left: auto;
  right: 16rpx;
  width: auto;
}

.back-icon {
  font-size: 48rpx;
  color: var(--color-text);
  line-height: 1;
}

.apply-text {
  font-size: 26rpx;
  color: var(--color-primary);
  font-weight: var(--font-weight-medium);
}

.header-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  letter-spacing: -0.02em;
}

.tabs {
  position: sticky;
  /* 吸附点需跟随 .header 的实际高度（header 已加状态栏安全区），否则 iOS 上会被加高后的 header 盖住 */
  top: calc(88rpx + var(--status-bar-height, 0px));
  top: calc(88rpx + max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px)));
  z-index: 20;
  display: flex;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
}

.tab {
  flex: 1 1 0;
  /* min-width:0 允许 flex 子项收缩，5 个 tab 在窄屏 + 英文文案下不会撑宽容器 */
  min-width: 0;
  text-align: center;
  padding: 24rpx 0;
  font-size: 26rpx;
  color: var(--color-text-tertiary);
  position: relative;

  &.active {
    color: var(--color-primary);
    font-weight: var(--font-weight-semibold);
  }
}

.tab-indicator {
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 48rpx;
  height: 4rpx;
  background: var(--color-primary);
  border-radius: 2rpx;
}

.scroll {
  /* 内容区宽度跟随屏幕：box-sizing 让 padding 不额外撑宽，横向溢出内容被裁剪 */
  width: 100%;
  box-sizing: border-box;
  padding: 20rpx;
  overflow-x: hidden;
}

.card {
  width: 100%;
  box-sizing: border-box;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
  overflow: hidden;
  margin-bottom: 20rpx;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 24rpx 16rpx;
}

.card-tags {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.tag-status {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 36rpx;
  padding: 0 14rpx;
  border-radius: 8rpx;
  font-size: 22rpx;
  font-weight: var(--font-weight-semibold);
  white-space: nowrap;

  &.tag-pending {
    background: #e8f2ff;
    color: var(--color-primary);
  }

  &.tag-processing {
    background: #fff3e0;
    color: #ff9500;
  }

  &.tag-completed {
    background: #e9f9ee;
    color: #34c759;
  }

  &.tag-closed {
    background: var(--color-divider);
    color: var(--color-text-tertiary);
  }
}

.tag-type {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 36rpx;
  padding: 0 14rpx;
  border-radius: 8rpx;
  font-size: 22rpx;
  background: var(--color-divider);
  color: var(--color-text-tertiary);
  white-space: nowrap;
}

.card-arrow {
  font-size: 36rpx;
  color: var(--color-text-tertiary);
  line-height: 1;
}

.card-body {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 0 24rpx 20rpx;
}

.card-img {
  width: 128rpx;
  height: 128rpx;
  border-radius: var(--radius-sm);
  border: 1rpx solid var(--color-divider);
  flex-shrink: 0;
}

.card-info {
  flex: 1;
  min-width: 0;
}

.card-name {
  display: block;
  font-size: 28rpx;
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-sku {
  display: block;
  font-size: 24rpx;
  color: var(--color-text-tertiary);
  margin-top: 6rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-divider {
  height: 1rpx;
  background: var(--color-divider);
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 24rpx;
}

.card-amount {
  display: flex;
  align-items: baseline;
  gap: 8rpx;
}

.amount-label {
  font-size: 24rpx;
  color: var(--color-text-tertiary);
}

.amount-value {
  font-size: 28rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-danger);
}

.card-time {
  font-size: 24rpx;
  color: var(--color-text-tertiary);
}

.card-progress {
  padding: 0 24rpx 24rpx;
}

.progress-bar {
  display: flex;
  gap: 6rpx;
}

.progress-step {
  flex: 1;
  height: 6rpx;
  border-radius: 3rpx;
  background: var(--color-divider);

  &.filled {
    background: var(--color-primary);
  }

  &.current {
    background: var(--color-primary);
  }
}

.progress-labels {
  display: flex;
  justify-content: space-between;
  margin-top: 12rpx;
}

.progress-label {
  font-size: 18rpx;
  color: var(--color-text-tertiary);
  white-space: nowrap;

  &.active {
    color: var(--color-primary);
    font-weight: var(--font-weight-medium);
  }
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 160rpx 40rpx 80rpx;
}

.empty-icon {
  position: relative;
  width: 192rpx;
  height: 192rpx;
  margin-bottom: 48rpx;
}

.empty-box {
  position: absolute;
  inset: 0;
  border-radius: var(--radius-md);
  background: var(--color-divider);
  border: 1rpx solid var(--color-divider);
}

.empty-file {
  position: absolute;
  top: 40rpx;
  left: 50%;
  transform: translateX(-50%);
  width: 80rpx;
  height: 96rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-text-tertiary);
  border-radius: var(--radius-sm);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10rpx;
  padding-top: 24rpx;
}

.empty-line {
  height: 4rpx;
  border-radius: 2rpx;
  background: var(--color-fill-dim);

  &.short {
    width: 48rpx;
  }
  &.medium {
    width: 40rpx;
  }
  &.long {
    width: 56rpx;
  }
}

.empty-search {
  position: absolute;
  bottom: -8rpx;
  right: -8rpx;
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background: var(--color-divider);
  border: 4rpx solid var(--color-surface);
  display: flex;
  align-items: center;
  justify-content: center;
}

.search-icon {
  font-size: 32rpx;
  color: var(--color-text-tertiary);
  line-height: 1;
}

.empty-title {
  font-size: 28rpx;
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
  margin-bottom: 12rpx;
}

.empty-desc {
  font-size: 24rpx;
  color: var(--color-text-tertiary);
  margin-bottom: 48rpx;
}

.empty-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 80rpx;
  padding: 0 64rpx;
  border-radius: 999rpx;
  background: var(--color-primary);
  color: #ffffff;
  font-size: 28rpx;
  font-weight: var(--font-weight-semibold);
}

.policy-section {
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
  margin-top: 32rpx;
  padding: 24rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.policy-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.policy-title {
  font-size: 26rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.policy-toggle {
  font-size: 24rpx;
  color: var(--color-primary);
}

.policy-content {
  margin-top: 16rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid var(--color-divider);
}

.policy-text {
  display: block;
  font-size: 24rpx;
  color: var(--color-text-secondary);
  line-height: 1.6;
  /* 长英文/连续字符强制换行，避免撑破政策容器 */
  word-break: break-word;
  overflow-wrap: break-word;
}

.policy-contact {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
  margin-top: 16rpx;
}

.contact-text {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}

.loading {
  text-align: center;
  padding: 40rpx;
  color: var(--color-text-tertiary);
  font-size: 26rpx;
}

.card-action {
  padding: 0 24rpx 24rpx;
  display: flex;
  justify-content: flex-end;
}

.mini-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 64rpx;
  padding: 0 32rpx;
  border-radius: 999rpx;
  background: var(--color-primary);
  color: #ffffff;
  font-size: 24rpx;
  font-weight: var(--font-weight-semibold);
}

.sheet-mask {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  z-index: 100;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: flex-end;
}

.sheet {
  width: 100%;
  box-sizing: border-box;
  padding: 32rpx 24rpx calc(32rpx + env(safe-area-inset-bottom));
  background: var(--color-surface);
  border-radius: var(--radius-md) var(--radius-md) 0 0;
}

.sheet-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24rpx;
}

.sheet-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.sheet-close {
  font-size: 36rpx;
  color: var(--color-text-tertiary);
}

.sheet-picker {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20rpx 16rpx;
  background: var(--color-background);
  border-radius: var(--radius-sm);
  font-size: var(--font-size-sm);
  color: var(--color-text);
  margin-bottom: 16rpx;
}

.arrow {
  font-size: var(--font-size-lg);
  color: var(--color-text-tertiary);
}

.sheet-input {
  width: 100%;
  /* 必须给出确定高度：uni-input 默认仅 1.4em，border-box 下垂直 padding 会把内容区压成 0 高度，导致无法点击/输入 */
  height: 88rpx;
  box-sizing: border-box;
  padding: 0 16rpx;
  background: var(--color-background);
  border-radius: var(--radius-sm);
  font-size: var(--font-size-sm);
}

/* 表单字段标签与提示 */
.sheet-field-label {
  display: block;
  margin: 20rpx 0 10rpx;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.sheet-hint {
  display: block;
  margin-top: 8rpx;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  line-height: 1.5;
}

.placeholder {
  color: var(--color-text-tertiary);
}

/* 运单号输入框：右侧预留清空按钮位置 */
.sheet-input-wrap {
  position: relative;
  width: 100%;
}

.sheet-input-wrap .sheet-input {
  padding-right: 64rpx;
}

.sheet-input-clear {
  position: absolute;
  right: 16rpx;
  top: 50%;
  transform: translateY(-50%);
  width: 40rpx;
  height: 40rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.sheet-actions {
  display: flex;
  gap: 16rpx;
  margin-top: 24rpx;
}

.btn-outline {
  flex: 1;
  height: 80rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1rpx solid var(--color-divider);
  border-radius: 999rpx;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  background: var(--color-surface);
}

.btn-primary {
  flex: 1;
  height: 80rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-primary);
  color: #ffffff;
  border-radius: 999rpx;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
}

.btn-primary.disabled {
  opacity: 0.6;
}
</style>
