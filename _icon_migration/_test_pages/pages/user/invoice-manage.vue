<template>
  <view class="invoice-manage">
    <view class="page-header">
      <view class="back" @click="goBack" aria-label="返回"><text class="luc luc-arrow-left"></text></view>
      <text class="title">发票管理</text>
      <view class="add-btn" @click="onAdd">+ 新增</view>
    </view>

    <scroll-view scroll-y class="content">
      <view v-if="invoices.length === 0" class="empty">
        <text class="empty-icon"><text class="luc luc-receipt"></text></text>
        <text class="empty-text">暂无发票信息</text>
        <view class="btn-primary" @click="onAdd">添加发票抬头</view>
      </view>

      <view v-else class="invoice-list">
        <view v-for="inv in invoices" :key="inv.id" class="invoice-card">
          <view class="invoice-header">
            <text class="invoice-type">{{ inv.type === 'company' ? '企业' : '个人' }}</text>
            <text v-if="inv.isDefault" class="default-tag">默认</text>
          </view>
          <text class="invoice-name">{{ inv.title }}</text>
          <text v-if="inv.taxNo" class="invoice-tax">税号：{{ inv.taxNo }}</text>
          <view class="invoice-actions">
            <view v-if="!inv.isDefault" class="action-text" @click="onSetDefault(inv)">设为默认</view>
            <view class="action-text" @click="onEdit(inv)">编辑</view>
            <view class="action-text danger" @click="onDelete(inv)">删除</view>
          </view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
export default {
  data() {
    return {
      invoices: [],
    }
  },

  onShow() {
    this.loadInvoices()
  },

  methods: {
    async loadInvoices() {
      // mock
      this.invoices = [
        { id: 1, type: 'personal', title: '张三', isDefault: true },
        { id: 2, type: 'company', title: '某某科技有限公司', taxNo: '91110000123456789X', isDefault: false },
      ]
    },

    goBack() {
      uni.navigateBack()
    },

    onAdd() {
      uni.showToast({ title: '新增发票', icon: 'none' })
    },

    onSetDefault(inv) {
      this.invoices.forEach((i) => (i.isDefault = false))
      inv.isDefault = true
      uni.showToast({ title: '已设为默认', icon: 'success' })
    },

    onEdit(inv) {
      uni.showToast({ title: `编辑 ${inv.title}`, icon: 'none' })
    },

    onDelete(inv) {
      uni.showModal({
        title: '删除发票？',
        success: (r) => {
          if (r.confirm) {
            this.invoices = this.invoices.filter((i) => i.id !== inv.id)
            uni.showToast({ title: '已删除', icon: 'success' })
          }
        },
      })
    },
  },
}
</script>

<style lang="scss" scoped>
.invoice-manage {
  min-height: 100vh;
  background: var(--color-background);
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
}

.add-btn {
  font-size: var(--font-size-sm);
  color: var(--color-primary);
  font-weight: var(--font-weight-medium);
}

.content {
  padding: 24rpx;
}

.empty {
  padding: 96rpx 24rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
}

.empty-icon {
  font-size: 120rpx;
  opacity: 0.4;
}

.empty-text {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.btn-primary {
  margin-top: 16rpx;
  width: 240rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-primary);
  color: var(--color-text);
  border-radius: 999rpx;
  font-size: var(--font-size-sm);
}

.invoice-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.invoice-card {
  padding: 24rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
}

.invoice-header {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 12rpx;
}

.invoice-type {
  padding: 4rpx 12rpx;
  background: var(--color-primary-light);
  color: var(--color-primary);
  border-radius: 999rpx;
  font-size: var(--font-size-xs);
}

.default-tag {
  padding: 4rpx 12rpx;
  background: var(--color-success);
  color: #fff;
  border-radius: 999rpx;
  font-size: var(--font-size-xs);
}

.invoice-name {
  display: block;
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
  margin-bottom: 4rpx;
}

.invoice-tax {
  display: block;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin-bottom: 16rpx;
}

.invoice-actions {
  display: flex;
  gap: 24rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid var(--color-divider);
}

.action-text {
  font-size: var(--font-size-sm);
  color: var(--color-primary);
}

.action-text.danger {
  color: var(--color-danger);
}
</style>