<template>
  <view class="invoice-page">
    <!-- 顶部导航栏 -->
    <view class="nav-header">
      <view class="nav-back" @tap="goBack">
        <text class="luc luc-arrow-left" />
      </view>
      <text class="nav-title">发票管理</text>
    </view>

    <scroll-view scroll-y class="scroll">
      <!-- 发票抬头管理 -->
      <view class="section-label">
        <text class="label-text">发票抬头</text>
        <text class="label-action" @tap="showInvoiceForm = true">添加新抬头</text>
      </view>

      <!-- 当前抬头卡片 -->
      <view class="header-card">
        <view class="header-top">
          <view class="header-type company">
            <text class="luc luc-building-2" />
            <text>增值税专用发票</text>
          </view>
          <view class="header-actions">
            <view class="action-btn" @tap="showInvoiceForm = true">
              <text class="luc luc-pencil" />
              <text>编辑</text>
            </view>
          </view>
        </view>
        <text class="header-name">上海某某宠物用品有限公司</text>
        <view class="header-detail">
          <view class="detail-row">
            <text class="detail-label">税号</text>
            <text class="detail-value">91310000MA1FL8XQ30</text>
          </view>
          <view class="detail-row">
            <text class="detail-label">地址</text>
            <text class="detail-value">上海市浦东新区张江高科技园区</text>
          </view>
          <view class="detail-row">
            <text class="detail-label">电话</text>
            <text class="detail-value">021-5888XXXX</text>
          </view>
          <view class="detail-row">
            <text class="detail-label">开户行</text>
            <text class="detail-value">中国工商银行上海张江支行</text>
          </view>
          <view class="detail-row">
            <text class="detail-label">银行账号</text>
            <text class="detail-value">1001 2088 0920 0158 XXX</text>
          </view>
        </view>
      </view>

      <!-- 发票类型切换 -->
      <view class="invoice-type-selector">
        <view
          class="type-option"
          :class="{ active: invoiceType === '普通' }"
          @tap="invoiceType = '普通'"
        >
          增值税普通发票
        </view>
        <view
          class="type-option"
          :class="{ active: invoiceType === '专用' }"
          @tap="invoiceType = '专用'"
        >
          增值税专用发票
        </view>
      </view>

      <!-- 开票统计 -->
      <view class="stats-card">
        <view class="stat-item">
          <text class="stat-label">本月开票金额</text>
          <view class="stat-value-row">
            <text class="stat-value">
              {{ stats.monthlyAmount.split('.')[0] || stats.monthlyAmount }}
            </text>
            <text class="stat-unit">
              {{
                stats.monthlyAmount.includes('.')
                  ? '.' + stats.monthlyAmount.split('.')[1] + ' 元'
                  : ' 元'
              }}
            </text>
          </view>
        </view>
        <view class="stat-divider" />
        <view class="stat-item">
          <text class="stat-label">累计开票金额</text>
          <view class="stat-value-row">
            <text class="stat-value">
              {{ stats.totalAmount.split('.')[0] || stats.totalAmount }}
            </text>
            <text class="stat-unit">
              {{
                stats.totalAmount.includes('.')
                  ? '.' + stats.totalAmount.split('.')[1] + ' 元'
                  : ' 元'
              }}
            </text>
          </view>
        </view>
      </view>

      <!-- Tab 切换 -->
      <view class="tab-bar">
        <view
          v-for="(tab, i) in tabs"
          :key="i"
          class="tab-item"
          :class="{ active: currentTab === i }"
          @tap="switchTab(i)"
        >
          <text>{{ tab.name }}</text>
          <view v-if="tab.badge" class="tab-badge">{{ tab.badge }}</view>
          <view v-if="currentTab === i" class="tab-indicator" />
        </view>
      </view>

      <!-- 可开票面板 -->
      <view v-show="currentTab === 0" class="tab-panel">
        <view v-for="(order, i) in availableOrders" :key="i" class="order-card">
          <view class="order-top">
            <view>
              <text class="order-id">订单号：{{ order.id }}</text>
              <text class="order-time">下单时间：{{ order.time }}</text>
            </view>
            <text class="luc luc-chevron-right" />
          </view>
          <view class="order-amount-row">
            <text class="amount-label">订单金额</text>
            <text class="amount-value">{{ order.amount }}</text>
          </view>
          <view class="amount-sub">
            <text class="sub-item">
              已开票
              <text class="invoiced">{{ order.invoiced }}</text>
            </text>
            <text class="sub-item">
              未开票
              <text class="uninvoiced">{{ order.uninvoiced }}</text>
            </text>
          </view>
          <view class="order-bottom">
            <view class="invoice-type-select">
              <view class="type-btn selected">
                <text class="luc luc-file-text" />
                <text>电子发票</text>
              </view>
              <view class="type-btn">
                <text class="luc luc-mail" />
                <text>纸质发票</text>
              </view>
            </view>
            <view v-if="order.canApply" class="apply-btn" @tap="handleApply(order)">申请开票</view>
            <view v-else class="apply-btn disabled">已开完</view>
          </view>
        </view>
      </view>

      <!-- 已开票面板 -->
      <view v-show="currentTab === 1" class="tab-panel">
        <view v-for="(inv, i) in invoicedList" :key="i" class="invoiced-card">
          <view class="invoiced-top">
            <view>
              <text class="invoice-number">{{ inv.number }}</text>
              <text class="invoiced-date">开票时间：{{ inv.date }}</text>
            </view>
            <view class="status-badge" :class="inv.statusClass">
              <view class="status-dot" />
              <text>{{ inv.statusText }}</text>
            </view>
          </view>
          <view class="invoiced-info">
            <view class="info-row">
              <text class="info-label">关联订单</text>
              <text class="info-value">{{ inv.orderId }}</text>
            </view>
            <view class="info-row">
              <text class="info-label">发票金额</text>
              <text class="info-value amount-highlight">{{ inv.amount }}</text>
            </view>
            <view class="info-row">
              <text class="info-label">发票类型</text>
              <text class="info-value">{{ inv.type }}</text>
            </view>
          </view>
          <view class="invoiced-actions">
            <view class="action-detail" @tap="handleViewDetail(inv)">
              <text class="luc luc-eye" />
              <text>查看详情</text>
            </view>
            <view class="action-primary" @tap="handleDownload(inv)">
              <text class="luc luc-download" />
              <text>下载 PDF</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 申请失败面板 -->
      <view v-show="currentTab === 2" class="tab-panel">
        <view class="failed-card">
          <view class="failed-top">
            <view>
              <text class="invoice-number">FP20250603003</text>
              <text class="invoiced-date">申请时间：2025-06-03 16:20</text>
            </view>
            <view class="failed-reason">
              <text class="luc luc-x-circle" />
              <text>抬头信息不完整</text>
            </view>
          </view>
          <view class="failed-info">
            <view class="info-row">
              <text class="info-label">关联订单</text>
              <text class="info-value">MY20250601001</text>
            </view>
            <view class="info-row">
              <text class="info-label">发票金额</text>
              <text class="info-value">1,280.00</text>
            </view>
            <view class="info-row">
              <text class="info-label">失败原因</text>
              <text class="info-value" style="color: var(--state-error)">
                请补充开户行及银行账号信息后重新申请
              </text>
            </view>
          </view>
          <view class="failed-actions">
            <view class="btn-retry" @tap="handleRetry">
              <text class="luc luc-rotate-ccw" />
              <text>重新申请</text>
            </view>
            <view class="btn-contact" @tap="handleContact">
              <text class="luc luc-headphones" />
              <text>联系客服</text>
            </view>
          </view>
        </view>
      </view>

      <view class="bottom-safe" />
    </scroll-view>

    <!-- 申请开票表单弹窗 -->
    <view class="form-overlay" :class="{ active: showInvoiceForm }" @tap="showInvoiceForm = false">
      <view class="form-sheet" @tap.stop>
        <view class="sheet-handle" />
        <text class="form-title">申请开票</text>

        <!-- 个人/企业切换 -->
        <view class="form-type-toggle">
          <view
            class="type-option"
            :class="{ active: formType === '个人' }"
            @tap="formType = '个人'"
          >
            个人
          </view>
          <view
            class="type-option"
            :class="{ active: formType === '企业' }"
            @tap="formType = '企业'"
          >
            企业
          </view>
        </view>

        <view class="form-content">
          <view class="form-group">
            <text class="form-label">发票抬头</text>
            <input v-model="formData.title" class="form-input" placeholder="请输入发票抬头">
          </view>
          <view class="form-group">
            <text class="form-label">税号</text>
            <input v-model="formData.taxId" class="form-input" placeholder="请输入纳税人识别号">
          </view>
          <view class="form-group">
            <text class="form-label">开票金额</text>
            <input
              v-model="formData.amount"
              class="form-input"
              placeholder="请输入开票金额"
              type="number"
            >
          </view>
          <view class="form-group">
            <text class="form-label">电子邮箱</text>
            <input
              v-model="formData.email"
              class="form-input"
              placeholder="接收电子发票的邮箱"
              type="email"
            >
          </view>
        </view>

        <view class="form-actions">
          <view class="form-cancel" @tap="showInvoiceForm = false">取消</view>
          <view class="form-submit" @tap="handleSubmitInvoice">提交申请</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { invoiceApi } from '@/api'

export default {
  data() {
    return {
      currentTab: 0,
      invoiceType: '普通',
      showInvoiceForm: false,
      formType: '企业',
      formData: {
        title: '上海某某宠物用品有限公司',
        taxId: '91310000MA1FL8XQ30',
        amount: '',
        email: '',
      },
      tabs: [
        { name: '可开票', badge: null },
        { name: '已开票', badge: null },
        { name: '申请失败', badge: null },
      ],
      availableOrders: [],
      invoicedList: [],
      stats: {
        monthlyAmount: '0.00',
        totalAmount: '0.00',
      },
    }
  },
  onLoad() {
    this.loadInvoices()
  },
  methods: {
    async loadInvoices() {
      try {
        const res = await invoiceApi.getInvoices()
        const data = res.data || res
        if (data.availableOrders) {
          this.availableOrders = data.availableOrders
        }
        if (data.invoicedList) {
          this.invoicedList = data.invoicedList
        }
        if (data.stats) {
          this.stats = data.stats
        }
        if (data.failedCount) {
          this.tabs[2].badge = String(data.failedCount)
        }
      } catch (e) {
        // 静默失败
      }
    },
    goBack() {
      uni.navigateBack()
    },
    switchTab(i) {
      this.currentTab = i
    },
    handleApply(order) {
      this.showInvoiceForm = true
    },
    handleViewDetail(inv) {
      uni.showToast({ title: `查看发票详情：${inv.number}`, icon: 'none' })
    },
    handleDownload(inv) {
      uni.showToast({ title: '下载中...', icon: 'none' })
    },
    handleRetry() {
      uni.showToast({ title: '重新申请已提交', icon: 'none' })
    },
    handleContact() {
      uni.showToast({ title: '联系客服中...', icon: 'none' })
    },
    async handleSubmitInvoice() {
      if (!this.formData.title || !this.formData.taxId) {
        uni.showToast({ title: '请填写完整信息', icon: 'none' })
        return
      }
      try {
        await invoiceApi.applyInvoice({
          title: this.formData.title,
          taxId: this.formData.taxId,
          amount: this.formData.amount,
          email: this.formData.email,
          type: this.invoiceType,
        })
        uni.showToast({ title: '开票申请已提交', icon: 'success' })
        this.showInvoiceForm = false
        this.loadInvoices()
      } catch (e) {
        uni.showToast({ title: '提交失败，请重试', icon: 'none' })
      }
    },
  },
}
</script>

<style scoped>
.invoice-page {
  min-height: 100vh;
  background: var(--background-200);
}
.nav-header {
  position: sticky;
  top: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 88rpx;
  padding: 0 32rpx;
  background: var(--background);
  border-bottom: 2rpx solid var(--border);
}
.nav-back {
  position: absolute;
  left: 32rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  color: var(--primary);
}
.nav-title {
  font-size: 34rpx;
  font-weight: 600;
  color: var(--foreground);
}
.scroll {
  padding-bottom: 32rpx;
}

/* 发票抬头 */
.section-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 32rpx 32rpx 24rpx;
}
.label-text {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--foreground);
}
.label-action {
  font-size: 26rpx;
  font-weight: 500;
  color: var(--primary);
}
.header-card {
  margin: 0 32rpx 32rpx;
  padding: 32rpx;
  background: var(--card);
  border: 2rpx solid var(--border);
  border-radius: var(--radius);
}
.header-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24rpx;
}
.header-type {
  display: inline-flex;
  align-items: center;
  gap: 8rpx;
  padding: 8rpx 20rpx;
  border-radius: 999px;
  font-size: 24rpx;
  font-weight: 600;
}
.header-type.company {
  background: var(--state-success-surface);
  color: var(--state-success);
}
.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 8rpx;
  padding: 12rpx 24rpx;
  border: 2rpx solid var(--border);
  border-radius: 999px;
  font-size: 24rpx;
  font-weight: 500;
  color: var(--text-600);
}
.header-name {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--foreground);
  display: block;
  margin-bottom: 12rpx;
}
.detail-row {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
  margin-bottom: 8rpx;
}
.detail-label {
  flex-shrink: 0;
  min-width: 112rpx;
  font-size: 26rpx;
  color: var(--text-400);
}
.detail-value {
  font-size: 26rpx;
  color: var(--text-500);
}

/* 发票类型选择 */
.invoice-type-selector {
  margin: 0 32rpx 32rpx;
  padding: 6rpx;
  display: flex;
  background: var(--background-200);
  border-radius: 20rpx;
}
.type-option {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 72rpx;
  border-radius: 16rpx;
  font-size: 26rpx;
  font-weight: 500;
  color: var(--text-500);
}
.type-option.active {
  background: var(--card);
  color: var(--foreground);
  font-weight: 600;
}

/* 统计卡片 */
.stats-card {
  margin: 0 32rpx;
  padding: 32rpx;
  display: flex;
  align-items: center;
  gap: 24rpx;
  background: var(--background-200);
  border-radius: var(--radius);
}
.stat-item {
  flex: 1;
}
.stat-label {
  font-size: 24rpx;
  color: var(--text-400);
  display: block;
  margin-bottom: 8rpx;
}
.stat-value-row {
  display: flex;
  align-items: baseline;
  gap: 4rpx;
}
.stat-value {
  font-size: 40rpx;
  font-weight: 700;
  color: var(--foreground);
}
.stat-unit {
  font-size: 24rpx;
  font-weight: 500;
  color: var(--text-500);
}
.stat-divider {
  width: 2rpx;
  height: 72rpx;
  background: var(--background-300);
}

/* Tab 栏 */
.tab-bar {
  margin: 32rpx 32rpx 0;
  display: flex;
  border-bottom: 2rpx solid var(--border);
}
.tab-item {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 88rpx;
  font-size: 28rpx;
  font-weight: 500;
  color: var(--text-400);
  position: relative;
}
.tab-item.active {
  color: var(--primary);
  font-weight: 600;
}
.tab-indicator {
  position: absolute;
  bottom: 0;
  width: 48rpx;
  height: 4rpx;
  border-radius: 2rpx;
  background: var(--primary);
}
.tab-badge {
  position: absolute;
  top: 12rpx;
  right: calc(50% - 60rpx);
  min-width: 32rpx;
  height: 32rpx;
  padding: 0 8rpx;
  border-radius: 16rpx;
  background: var(--state-error);
  color: var(--background);
  font-size: 20rpx;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 订单卡片 */
.order-card {
  margin: 24rpx 32rpx;
  padding: 32rpx;
  background: var(--card);
  border: 2rpx solid var(--border);
  border-radius: var(--radius);
}
.order-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 24rpx;
}
.order-id {
  font-size: 26rpx;
  font-weight: 500;
  color: var(--text-800);
}
.order-time {
  font-size: 24rpx;
  color: var(--text-400);
  margin-top: 4rpx;
}
.order-amount-row {
  display: flex;
  align-items: baseline;
  gap: 32rpx;
  margin-bottom: 20rpx;
  padding-bottom: 20rpx;
  border-bottom: 2rpx solid var(--background-200);
}
.amount-label {
  font-size: 24rpx;
  color: var(--text-400);
}
.amount-value {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--foreground);
}
.amount-sub {
  display: flex;
  gap: 32rpx;
}
.sub-item {
  font-size: 24rpx;
  color: var(--text-500);
}
.invoiced {
  color: var(--state-success);
  font-weight: 600;
}
.uninvoiced {
  color: #ff9500;
  font-weight: 600;
}
.order-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 24rpx;
}
.type-btn {
  display: inline-flex;
  align-items: center;
  gap: 8rpx;
  padding: 12rpx 24rpx;
  border: 2rpx solid var(--border);
  border-radius: 999px;
  font-size: 24rpx;
  font-weight: 500;
  color: var(--text-500);
}
.type-btn.selected {
  border-color: var(--primary);
  background: var(--brand-50);
  color: var(--primary);
}
.apply-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 16rpx 32rpx;
  border-radius: 999px;
  background: var(--primary);
  color: var(--primary-foreground);
  font-size: 26rpx;
  font-weight: 600;
}
.apply-btn.disabled {
  opacity: 0.42;
  background: var(--primary);
}

/* 已开票卡片 */
.invoiced-card {
  margin: 24rpx 32rpx;
  padding: 32rpx;
  background: var(--card);
  border: 2rpx solid var(--border);
  border-radius: var(--radius);
}
.invoiced-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 24rpx;
}
.invoice-number {
  font-size: 26rpx;
  font-weight: 600;
  color: var(--text-800);
}
.invoiced-date {
  font-size: 24rpx;
  color: var(--text-400);
  margin-top: 4rpx;
}
.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 8rpx;
  padding: 8rpx 20rpx;
  border-radius: 999px;
  font-size: 22rpx;
  font-weight: 600;
}
.status-badge.issued {
  background: var(--state-success-surface);
  color: var(--state-success);
}
.status-badge.sent {
  background: var(--brand-50);
  color: var(--brand-500);
}
.status-dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
}
.status-badge.issued .status-dot {
  background: var(--state-success);
}
.status-badge.sent .status-dot {
  background: var(--brand-500);
}
.invoiced-info {
  margin-bottom: 24rpx;
  padding-bottom: 24rpx;
  border-bottom: 2rpx solid var(--background-200);
}
.info-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8rpx;
}
.info-label {
  font-size: 24rpx;
  color: var(--text-400);
}
.info-value {
  font-size: 26rpx;
  color: var(--text-600);
  font-weight: 500;
}
.info-value.amount-highlight {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--foreground);
}
.invoiced-actions {
  display: flex;
  gap: 16rpx;
}
.action-detail,
.action-primary {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  height: 72rpx;
  border-radius: 999px;
  font-size: 26rpx;
  font-weight: 500;
}
.action-detail {
  border: 2rpx solid var(--border);
  color: var(--text-600);
}
.action-primary {
  background: var(--primary);
  color: var(--primary-foreground);
  font-weight: 600;
}

/* 失败卡片 */
.failed-card {
  margin: 24rpx 32rpx;
  padding: 32rpx;
  background: var(--card);
  border: 2rpx solid var(--border);
  border-radius: var(--radius);
}
.failed-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 20rpx;
}
.failed-reason {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 8rpx 20rpx;
  border-radius: 999px;
  background: var(--state-error-surface);
  color: var(--state-error);
  font-size: 22rpx;
  font-weight: 600;
}
.failed-info {
  margin-bottom: 24rpx;
}
.failed-actions {
  display: flex;
  gap: 16rpx;
}
.btn-retry,
.btn-contact {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  height: 72rpx;
  border-radius: 999px;
  font-size: 26rpx;
  font-weight: 500;
}
.btn-retry {
  background: var(--primary);
  color: var(--primary-foreground);
  font-weight: 600;
}
.btn-contact {
  border: 2rpx solid var(--border);
  color: var(--text-600);
}

/* 申请表单弹窗 */
.form-overlay {
  position: fixed;
  inset: 0;
  z-index: 100;
  background: rgba(0, 0, 0, 0.4);
  opacity: 0;
  visibility: hidden;
  transition:
    opacity 0.25s ease,
    visibility 0.25s ease;
}
.form-overlay.active {
  opacity: 1;
  visibility: visible;
}
.form-sheet {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: var(--card);
  border-radius: 32rpx 32rpx 0 0;
  padding: 24rpx 32rpx;
  padding-bottom: env(safe-area-inset-bottom);
  max-height: 80vh;
  overflow-y: auto;
}
.sheet-handle {
  width: 72rpx;
  height: 8rpx;
  border-radius: 999px;
  background: var(--background-400);
  margin: 0 auto 24rpx;
}
.form-title {
  font-size: 34rpx;
  font-weight: 600;
  color: var(--foreground);
  display: block;
  text-align: center;
  margin-bottom: 32rpx;
}
.form-type-toggle {
  display: flex;
  padding: 6rpx;
  background: var(--background-200);
  border-radius: 20rpx;
  margin-bottom: 32rpx;
}
.form-type-toggle .type-option {
  flex: 1;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 16rpx;
  font-size: 26rpx;
  font-weight: 500;
  color: var(--text-500);
}
.form-type-toggle .type-option.active {
  background: var(--card);
  color: var(--foreground);
  font-weight: 600;
}
.form-group {
  margin-bottom: 24rpx;
}
.form-label {
  font-size: 26rpx;
  font-weight: 500;
  color: var(--foreground);
  display: block;
  margin-bottom: 8rpx;
}
.form-input {
  width: 100%;
  height: 88rpx;
  padding: 0 24rpx;
  border: 2rpx solid var(--input);
  border-radius: 16rpx;
  font-size: 28rpx;
  color: var(--foreground);
  background: var(--background);
  box-sizing: border-box;
}
.form-actions {
  display: flex;
  gap: 24rpx;
  margin-top: 32rpx;
}
.form-cancel,
.form-submit {
  flex: 1;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 24rpx;
  font-size: 28rpx;
  font-weight: 600;
}
.form-cancel {
  background: var(--background-200);
  color: var(--foreground);
}
.form-submit {
  background: var(--primary);
  color: var(--primary-foreground);
}
.bottom-safe {
  height: 32rpx;
}
</style>
