<template>
  <view class="after-sales-detail">
    <view class="page-header">
      <view class="back" aria-label="返回" @click="goBack">
        <text class="luc luc-arrow-left" />
      </view>
      <text class="title">{{ $t('afterSalesDetail.title') }}</text>
    </view>

    <scroll-view scroll-y class="content">
      <view v-if="loading" class="loading">{{ $t('common.loading') }}</view>

      <block v-else-if="detail">
        <!-- 状态总览 -->
        <view class="status-card">
          <view class="status-top">
            <text class="status-title">{{ statusLabel }}</text>
            <text class="status-tag">{{ typeLabel }}</text>
          </view>
          <view class="progress-bar">
            <view
              v-for="(step, idx) in progress.steps"
              :key="idx"
              class="progress-step"
              :class="{ filled: idx < progress.index, current: idx === progress.index }"
            />
          </view>
          <view class="progress-labels">
            <text
              v-for="(label, idx) in progress.steps"
              :key="idx"
              class="progress-label"
              :class="{ active: idx <= progress.index }"
            >
              {{ label }}
            </text>
          </view>
          <text v-if="detail.status === 'REJECTED' && detail.rejectReason" class="reject-reason">
            {{ $t('afterSalesDetail.rejectReason') }}: {{ detail.rejectReason }}
          </text>
        </view>

        <!-- 商品信息 -->
        <view class="card goods-card">
          <image mode="aspectFill" :src="resolveItemImage(detail.mainImage)" class="goods-image" />
          <view class="goods-info">
            <text class="goods-name">{{ detail.productName || '-' }}</text>
            <text class="goods-spec">{{ detail.skuSpec || '' }}</text>
            <text class="goods-qty">×{{ detail.quantity }}</text>
          </view>
        </view>

        <!-- 售后信息 -->
        <view class="card info-card">
          <view class="info-row">
            <text class="info-label">{{ $t('afterSalesDetail.afterSaleNo') }}</text>
            <text class="info-value">{{ detail.afterSaleNo || '-' }}</text>
          </view>
          <view class="info-row">
            <text class="info-label">{{ $t('afterSalesDetail.orderNo') }}</text>
            <text class="info-value">{{ detail.orderNo || detail.orderId }}</text>
          </view>
          <view class="info-row">
            <text class="info-label">{{ $t('afterSalesDetail.reason') }}</text>
            <text class="info-value">{{ detail.reason || '-' }}</text>
          </view>
          <view class="info-row">
            <text class="info-label">{{ $t('afterSalesDetail.amount') }}</text>
            <text v-if="detail.type !== 'EXCHANGE'" class="info-value amount">
              ${{ detail.amount }}
            </text>
            <text v-else class="info-value">{{ $t('afterSales.noRefund') }}</text>
          </view>
          <view class="info-row">
            <text class="info-label">{{ $t('afterSalesDetail.applyTime') }}</text>
            <text class="info-value">{{ formatDate(detail.createTime) }}</text>
          </view>
          <view v-if="detail.refundNo" class="info-row">
            <text class="info-label">{{ $t('afterSalesDetail.refundNo') }}</text>
            <text class="info-value">{{ detail.refundNo }}</text>
          </view>
          <view v-if="detail.completeTime" class="info-row">
            <text class="info-label">{{ $t('afterSalesDetail.completeTime') }}</text>
            <text class="info-value">{{ formatDate(detail.completeTime) }}</text>
          </view>
        </view>

        <!-- 问题描述 -->
        <view v-if="detail.description" class="card">
          <text class="block-title">{{ $t('afterSalesDetail.desc') }}</text>
          <text class="block-text">{{ detail.description }}</text>
        </view>

        <!-- 凭证 -->
        <view v-if="imageList.length" class="card">
          <text class="block-title">{{ $t('afterSalesDetail.evidence') }}</text>
          <view class="image-grid">
            <image
              v-for="(img, idx) in imageList"
              :key="idx"
              :src="resolveItemImage(img)"
              mode="aspectFill"
              class="evidence-image"
              @click="previewImage(idx)"
            />
          </view>
        </view>

        <!-- 退货物流：审核通过后展示指引与填写入口 -->
        <view v-if="detail.deliveryType === 'RETURN_SHIP'" class="card">
          <text class="block-title">{{ $t('afterSalesDetail.returnLogistics') }}</text>
          <view v-for="(tip, idx) in returnInstructions" :key="idx" class="instruction-row">
            <text class="instruction-dot">•</text>
            <text class="instruction-text">{{ tip }}</text>
          </view>

          <view v-if="detail.returnTrackingNo" class="info-row logistics-row">
            <text class="info-label">{{ $t('afterSalesDetail.company') }}</text>
            <text class="info-value">{{ detail.returnCompany }}</text>
          </view>
          <view v-if="detail.returnTrackingNo" class="info-row">
            <text class="info-label">{{ $t('afterSalesDetail.trackingNo') }}</text>
            <text class="info-value">{{ detail.returnTrackingNo }}</text>
          </view>
        </view>

        <!-- 填写退货物流表单 -->
        <view v-if="showLogisticsForm" class="card">
          <text class="block-title">{{ $t('afterSalesDetail.logisticsDialogTitle') }}</text>

          <text class="field-label">{{ $t('afterSalesDetail.logisticsCompanyLabel') }}</text>
          <picker :value="companyIndex" :range="companyOptions" @change="onCompanyChange">
            <view class="picker">
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
            class="input"
            :placeholder="$t('afterSalesDetail.customCarrierPlaceholder')"
            :maxlength="50"
          >

          <text class="field-label">{{ $t('afterSalesDetail.trackingNoLabel') }}</text>
          <view class="input-wrap">
            <input
              v-model="logisticsForm.trackingNo"
              class="input"
              type="text"
              :maxlength="40"
              :placeholder="$t('afterSalesDetail.trackingPlaceholder')"
              @blur="onTrackingBlur"
            >
            <text
              v-if="logisticsForm.trackingNo"
              class="input-clear luc luc-x"
              @click="logisticsForm.trackingNo = ''"
            />
          </view>
          <text class="field-hint">{{ $t('afterSalesDetail.trackingHint') }}</text>

          <view class="form-actions">
            <view class="btn-outline" @click="closeLogisticsForm">
              {{ $t('afterSalesDetail.cancel') }}
            </view>
            <view
              class="btn-primary small"
              :class="{ disabled: submitting }"
              @click="submitLogistics"
            >
              {{ submitting ? $t('afterSalesDetail.submitting') : $t('afterSalesDetail.submit') }}
            </view>
          </view>
        </view>

        <view class="bottom-bar safe-area-bottom">
          <view class="btn-outline" @click="contactCS">{{ $t('afterSalesDetail.contactCS') }}</view>
          <view v-if="canCancel" class="btn-outline" @click="cancelApply">
            {{ $t('afterSalesDetail.cancelApply') }}
          </view>
          <view v-if="canFillLogistics" class="btn-primary" @click="openLogistics">
            {{ logisticsActionLabel }}
          </view>
        </view>
      </block>
    </scroll-view>
  </view>
</template>

<script>
import { afterSalesApi } from '@/api'
import { i18n, t } from '@/i18n'
import { toAbsoluteImageUrl } from '@/utils/imageUrl'

export default {
  pageTitleKey: 'pageTitle.orderAfterSalesDetail',

  data() {
    return {
      id: null,
      detail: null,
      loading: false,
      showLogisticsForm: false,
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
    typeLabel() {
      if (!this.detail) return ''
      const key = `afterSales.types.${this.detail.type}`
      const label = t(key)
      return label && label !== key ? label : this.detail.typeLabel || this.detail.type
    },

    statusLabel() {
      if (!this.detail) return ''
      // 仅退款没有寄回/收货环节，审核通过后统一显示"待退款"
      if (
        this.detail.type === 'REFUND_ONLY' &&
        (this.detail.status === 'RECEIVED' ||
          (this.detail.status === 'APPROVED' && this.detail.deliveryType === 'NO_RETURN'))
      ) {
        return t('afterSales.refundPending')
      }
      const key = `afterSales.status.${this.detail.status}`
      const label = t(key)
      return label && label !== key ? label : this.detail.statusLabel || this.detail.status
    },

    progress() {
      const submit = t('afterSalesDetail.progress.submit')
      const review = t('afterSalesDetail.progress.review')
      const refund = t('afterSalesDetail.progress.refund')
      const ship = t('afterSalesDetail.progress.ship')
      const receive = t('afterSalesDetail.progress.receive')
      const finish = t('afterSalesDetail.progress.finish')
      const type = this.detail && this.detail.type
      const steps =
        type === 'REFUND_ONLY'
          ? [submit, review, refund]
          : [submit, review, ship, receive, type === 'EXCHANGE' ? finish : refund]
      const index =
        this.detail && this.detail.progressIndex != null ? Number(this.detail.progressIndex) : 0
      return { steps, index }
    },

    /** 退货指引使用前端文案，跟随当前语言，避免后端中文提示在英文界面显示 */
    returnInstructions() {
      if (!this.detail || this.detail.deliveryType !== 'RETURN_SHIP') return []
      if (!['APPROVED', 'RETURNING'].includes(this.detail.status)) return []
      return t('afterSalesDetail.instructions')
    },

    imageList() {
      if (!this.detail || !this.detail.images) return []
      try {
        const parsed = JSON.parse(this.detail.images)
        return Array.isArray(parsed) ? parsed : []
      } catch (e) {
        return []
      }
    },

    canFillLogistics() {
      return Boolean(
        this.detail &&
        this.detail.deliveryType === 'RETURN_SHIP' &&
        ['APPROVED', 'RETURNING'].includes(this.detail.status),
      )
    },

    /** 已寄回时按钮语义是"修改"，避免用户以为只能填一次 */
    logisticsActionLabel() {
      return this.detail && this.detail.status === 'RETURNING'
        ? t('afterSalesDetail.updateLogistics')
        : t('afterSalesDetail.fillLogistics')
    },

    canCancel() {
      return Boolean(this.detail && ['PENDING', 'APPROVED'].includes(this.detail.status))
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

  onLoad(query) {
    this.id = query.id
    this.loadDetail()
  },

  methods: {
    resolveItemImage(url) {
      return toAbsoluteImageUrl(url) || ''
    },

    formatDate(value) {
      if (!value) return ''
      const d = new Date(value)
      if (Number.isNaN(d.getTime())) return String(value)
      return d.toLocaleString(i18n.locale)
    },

    goBack() {
      uni.navigateBack()
    },

    async loadDetail() {
      this.loading = true
      try {
        this.detail = await afterSalesApi.getAfterSaleDetail(this.id)
      } catch (e) {
        uni.showToast({ title: t('afterSalesDetail.loadFailed'), icon: 'none' })
      } finally {
        this.loading = false
      }
    },

    previewImage(index) {
      uni.previewImage({
        current: index,
        urls: this.imageList.map((url) => this.resolveItemImage(url)),
      })
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

    openLogistics() {
      const d = this.detail || {}
      const company = d.returnCompany || ''
      const idx = this.carriers.indexOf(company)
      this.showLogisticsForm = true
      // 已寄回再次打开时预填原物流信息，便于修正填错的运单号
      if (company && idx === -1) {
        // 历史物流不在常用列表：落到"其他"并回填名称
        this.companyIndex = this.carriers.length + 1
        this.customCompany = company
      } else {
        // 无历史物流保持 0（占位项），有历史则映射到对应常用承运商
        this.companyIndex = idx >= 0 ? idx + 1 : 0
        this.customCompany = ''
      }
      this.logisticsForm.trackingNo = d.returnTrackingNo || ''
    },

    /** 关闭表单：提交中禁止关闭，避免请求已成功但表单看起来被取消 */
    closeLogisticsForm() {
      if (this.submitting) return
      this.showLogisticsForm = false
    },

    async submitLogistics() {
      if (this.submitting) return
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
        await afterSalesApi.submitReturnLogistics(this.id, {
          company,
          trackingNo,
        })
        uni.showToast({ title: t('afterSalesDetail.logisticsSubmitted'), icon: 'success' })
        this.showLogisticsForm = false
        this.loadDetail()
      } catch (e) {
        uni.showToast({ title: e.message || t('afterSalesDetail.logisticsFailed'), icon: 'none' })
      } finally {
        this.submitting = false
      }
    },

    cancelApply() {
      uni.showModal({
        title: t('afterSalesDetail.cancelConfirmTitle'),
        content: t('afterSalesDetail.cancelConfirmContent'),
        success: async (res) => {
          if (!res.confirm) return
          try {
            await afterSalesApi.cancelAfterSale(this.id)
            uni.showToast({ title: t('afterSalesDetail.cancelled'), icon: 'success' })
            this.loadDetail()
          } catch (e) {
            uni.showToast({ title: e.message || t('afterSalesDetail.cancelFailed'), icon: 'none' })
          }
        },
      })
    },

    contactCS() {
      const params = [
        `orderNo=${encodeURIComponent((this.detail && this.detail.orderNo) || '')}`,
        `productName=${encodeURIComponent((this.detail && this.detail.productName) || '')}`,
      ]
      uni.navigateTo({ url: `/pages/user/customer-service?${params.join('&')}` })
    },
  },
}
</script>

<style lang="scss" scoped>
.after-sales-detail {
  width: 100%;
  min-height: 100vh;
  background: var(--color-background);
  padding-bottom: 160rpx;
  overflow-x: hidden;
}

.page-header {
  display: flex;
  align-items: center;
  height: 88rpx;
  padding: 0 24rpx;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
}

.back {
  width: 60rpx;
  height: 60rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 44rpx;
  color: var(--color-text);
}

.title {
  flex: 1;
  text-align: center;
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  margin-right: 60rpx;
}

.content {
  width: 100%;
  box-sizing: border-box;
  padding: 24rpx;
  overflow-x: hidden;
}

.status-card {
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
  padding: 24rpx;
  margin-bottom: 20rpx;
}

.status-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24rpx;
}

.status-title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.status-tag {
  font-size: var(--font-size-xs);
  color: var(--color-primary);
  background: var(--color-primary-light);
  padding: 6rpx 16rpx;
  border-radius: 999rpx;
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
}

.progress-step.filled,
.progress-step.current {
  background: var(--color-primary);
}

.progress-labels {
  display: flex;
  justify-content: space-between;
  gap: 8rpx;
  margin-top: 12rpx;
}

.progress-label {
  flex: 1 1 0;
  min-width: 0;
  text-align: center;
  font-size: 18rpx;
  color: var(--color-text-tertiary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.progress-label.active {
  color: var(--color-primary);
  font-weight: var(--font-weight-medium);
}

.reject-reason {
  display: block;
  margin-top: 20rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid var(--color-divider);
  font-size: var(--font-size-sm);
  color: var(--color-danger);
  line-height: 1.5;
}

.card {
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
  padding: 24rpx;
  margin-bottom: 20rpx;
}

.goods-card {
  display: flex;
  gap: 16rpx;
}

.goods-image {
  width: 150rpx;
  height: 150rpx;
  border-radius: var(--radius-sm);
  background: var(--color-background);
  flex-shrink: 0;
}

.goods-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.goods-name {
  font-size: var(--font-size-sm);
  color: var(--color-text);
  word-break: break-all;
}

.goods-spec {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.goods-qty {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin-top: auto;
}

.info-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24rpx;
  padding: 10rpx 0;
}

.info-label {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
  flex-shrink: 0;
}

.info-value {
  flex: 1;
  min-width: 0;
  font-size: var(--font-size-sm);
  color: var(--color-text);
  text-align: right;
  word-break: break-all;
}

.info-value.amount {
  color: var(--color-danger);
  font-weight: var(--font-weight-semibold);
}

.block-title {
  display: block;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  margin-bottom: 12rpx;
}

.block-text {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  line-height: 1.6;
}

.image-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.evidence-image {
  width: 160rpx;
  height: 160rpx;
  border-radius: var(--radius-sm);
  background: var(--color-background);
}

.instruction-row {
  display: flex;
  gap: 8rpx;
  margin-bottom: 8rpx;
}

.instruction-dot {
  font-size: var(--font-size-sm);
  color: var(--color-primary);
}

.instruction-text {
  flex: 1;
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
  line-height: 1.6;
}

.logistics-row {
  margin-top: 16rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid var(--color-divider);
}

.picker {
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

.input {
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
.field-label {
  display: block;
  margin: 20rpx 0 10rpx;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.field-hint {
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
.input-wrap {
  position: relative;
  width: 100%;
}

.input-wrap .input {
  padding-right: 64rpx;
}

.input-clear {
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

.form-actions {
  display: flex;
  gap: 16rpx;
  margin-top: 20rpx;
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
  color: #fff;
  border-radius: 999rpx;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
}

.btn-primary.small {
  height: 72rpx;
  font-size: var(--font-size-xs);
}

.btn-primary.disabled {
  opacity: 0.6;
}

.bottom-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  gap: 16rpx;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background: var(--color-surface);
  border-top: 1rpx solid var(--color-divider);
}

.bottom-bar .btn-outline,
.bottom-bar .btn-primary {
  height: 88rpx;
}

.loading {
  text-align: center;
  padding: 80rpx;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}
</style>
