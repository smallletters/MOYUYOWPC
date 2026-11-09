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
          <picker :value="companyIndex" :range="companyOptions" @change="onCompanyChange">
            <view class="picker">
              <text>{{ logisticsForm.company || $t('afterSalesDetail.companyPlaceholder') }}</text>
              <text class="arrow luc-chevron-right" />
            </view>
          </picker>
          <input
            v-model="logisticsForm.trackingNo"
            class="input"
            :placeholder="$t('afterSalesDetail.trackingPlaceholder')"
          >
          <view class="form-actions">
            <view class="btn-outline" @click="showLogisticsForm = false">
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
      companyIndex: 0,
      companyOptions: ['UPS', 'USPS', 'FedEx', 'DHL', '顺丰速运', '其他'],
      logisticsForm: {
        company: '',
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
      this.logisticsForm.company = this.companyOptions[this.companyIndex]
    },

    openLogistics() {
      this.showLogisticsForm = true
      this.logisticsForm.company = this.companyOptions[this.companyIndex]
    },

    async submitLogistics() {
      if (this.submitting) return
      if (!this.logisticsForm.company || !this.logisticsForm.trackingNo.trim()) {
        uni.showToast({ title: t('afterSalesDetail.logisticsRequired'), icon: 'none' })
        return
      }
      this.submitting = true
      try {
        await afterSalesApi.submitReturnLogistics(this.id, {
          company: this.logisticsForm.company,
          trackingNo: this.logisticsForm.trackingNo.trim(),
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
  min-height: 100vh;
  background: var(--color-background);
  padding-bottom: 160rpx;
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
  padding: 24rpx;
}

.status-card {
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
  margin-top: 12rpx;
}

.progress-label {
  font-size: 18rpx;
  color: var(--color-text-tertiary);
  white-space: nowrap;
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
  padding: 20rpx 16rpx;
  background: var(--color-background);
  border-radius: var(--radius-sm);
  font-size: var(--font-size-sm);
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
