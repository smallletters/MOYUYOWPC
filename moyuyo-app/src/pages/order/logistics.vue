<template>
  <view class="logistics">
    <view v-if="loading" class="loading">{{ $t('common.loading') }}</view>

    <!-- 加载失败：给出提示与重试入口，避免页面永久停在 Loading -->
    <view v-else-if="loadError" class="card error-card">
      <text class="error-text">{{ $t('orderLogistics.loadFailed') }}</text>
      <view class="retry-btn" @click="loadLogistics">{{ $t('common.retry') }}</view>
    </view>

    <template v-else-if="logistics">
      <view class="card status-card">
        <text class="status-title">{{ statusLabel }}</text>
        <text v-if="logistics.carrier" class="status-desc">
          {{ logistics.carrier }} · {{ logistics.trackingNumber }}
        </text>
      </view>

      <view v-if="logistics.traces && logistics.traces.length" class="timeline">
        <view
          v-for="(t, i) in logistics.traces"
          :key="i"
          class="timeline-item"
          :class="{ active: i === 0, completed: i > 0 }"
        >
          <view class="timeline-dot" />
          <view class="timeline-content">
            <text class="timeline-title">{{ t.desc }}</text>
            <text v-if="t.location" class="timeline-desc">{{ t.location }}</text>
            <text class="timeline-time">{{ formatTraceTime(t.time) }}</text>
          </view>
        </view>
      </view>

      <!-- 无轨迹空态：单号未注册或承运商尚未推送事件 -->
      <view v-else class="card empty-card">
        <text class="empty-text">{{ $t('orderLogistics.noLogistics') }}</text>
      </view>

      <view v-if="logistics.carrier" class="card info-card">
        <text class="info-title">Tracking Number</text>
        <text class="info-value">{{ logistics.trackingNumber }}</text>
        <text class="info-title">Carrier</text>
        <text class="info-value">{{ logistics.carrier }}</text>
      </view>
    </template>
  </view>
</template>

<script>
import { orderApi } from '@/api'
import { i18n } from '@/i18n'
import { formatTraceTime as formatTraceTimeUtil } from '@/utils/traceTime'

export default {
  pageTitleKey: 'pageTitle.orderLogistics',

  data() {
    return {
      orderId: null,
      logistics: null,
      loading: true,
      loadError: false,
      localeVersion: 0,
    }
  },

  computed: {
    statusLabel() {
      void this.localeVersion
      if (!this.logistics) return ''
      const key = `orderLogistics.status.${this.logistics.currentStatus}`
      const v = i18n.t(key)
      return v === key ? i18n.t('orderLogistics.status.IN_TRANSIT') : v
    },
  },

  onLoad(query) {
    this._unsubLocale = i18n.subscribe(() => {
      this.localeVersion += 1
    })
    this.orderId = query.id
    this.loadLogistics()
  },

  onUnload() {
    if (this._unsubLocale) this._unsubLocale()
  },

  methods: {
    async loadLogistics() {
      this.loading = true
      this.loadError = false
      try {
        this.logistics = await orderApi.getLogistics(this.orderId)
      } catch (e) {
        console.error('[logistics] load error', e)
        this.loadError = true
      } finally {
        this.loading = false
      }
    },

    /**
     * 轨迹时间格式化（复用 utils/traceTime）
     */
    formatTraceTime(value) {
      return formatTraceTimeUtil(value)
    },
  },
}
</script>

<style lang="scss" scoped>
.logistics {
  min-height: 100vh;
  background: var(--color-background);
  padding: 16rpx;
}

.loading {
  text-align: center;
  padding: 64rpx;
  color: var(--color-text-tertiary);
}

.status-card {
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: 32rpx 24rpx;
  margin-bottom: 16rpx;
  text-align: center;
}

.status-title {
  display: block;
  font-size: 36rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-primary-dark);
}

.status-desc {
  display: block;
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
  margin-top: 8rpx;
}

.error-card,
.empty-card {
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: 48rpx 24rpx;
  margin-bottom: 16rpx;
  text-align: center;
}

.error-text,
.empty-text {
  display: block;
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.retry-btn {
  margin-top: 24rpx;
  padding: 16rpx 0;
  font-size: var(--font-size-sm);
  color: var(--color-primary-dark);
  text-decoration: underline;
}

.timeline {
  padding: 0 16rpx;
  margin-bottom: 16rpx;
}

.timeline-item {
  display: flex;
  gap: 16rpx;
  padding-bottom: 32rpx;
  position: relative;

  &::before {
    content: '';
    position: absolute;
    left: 14rpx;
    top: 32rpx;
    bottom: 0;
    width: 2rpx;
    background: var(--color-divider);
  }

  &:last-child::before {
    display: none;
  }
}

.timeline-dot {
  width: 30rpx;
  height: 30rpx;
  border-radius: 50%;
  background: var(--color-divider);
  flex-shrink: 0;
  margin-top: 4rpx;
}

.timeline-item.active .timeline-dot {
  background: var(--color-primary);
  box-shadow: 0 0 0 6rpx rgba(219, 201, 138, 0.2);
}

.timeline-item.completed .timeline-dot {
  background: var(--color-primary);
}

.timeline-content {
  flex: 1;
}

.timeline-title {
  display: block;
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
  margin-bottom: 4rpx;
}

.timeline-desc {
  display: block;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin-bottom: 4rpx;
}

.timeline-time {
  display: block;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.info-card {
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: 24rpx;
  margin: 0 0 16rpx;
}

.info-title {
  display: block;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin-top: 16rpx;
  &:first-child {
    margin-top: 0;
  }
}

.info-value {
  display: block;
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
  margin-top: 4rpx;
}
</style>
