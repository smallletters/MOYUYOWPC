<template>
  <view class="points-mall">
    <view class="page-header">
      <view class="back" :aria-label="$t('common.back')" @click="goBack">
        <text class="luc luc-arrow-left" />
      </view>
      <text class="title">{{ $t('pointsMall.title') }}</text>
    </view>

    <scroll-view scroll-y class="content">
      <!-- 积分余额 -->
      <view class="points-card" :aria-label="$t('pointsMall.pointsCardLabel')">
        <text class="points-label">{{ $t('pointsMall.myPoints') }}</text>
        <text class="points-value">{{ points.toLocaleString() }}</text>
        <text class="points-tip">{{ $t('pointsMall.pointsTip') }}</text>
      </view>

      <!-- 礼品分类 -->
      <view class="cat-tabs" :aria-label="$t('pointsMall.catLabel')">
        <view
          v-for="cat in categories"
          :key="cat.id"
          class="cat-tab"
          :class="{ active: activeCat === cat.id }"
          @click="activeCat = cat.id"
        >
          {{ $t(cat.labelKey) }}
        </view>
      </view>

      <!-- 礼品列表 -->
      <view class="goods-list" :aria-label="$t('pointsMall.goodsListLabel')">
        <view v-for="g in filteredGoods" :key="g.id" class="goods-card">
          <image :src="g.image" class="goods-image" />
          <view class="goods-info">
            <text class="goods-name">{{ g.name }}</text>
            <view class="goods-bottom">
              <view class="points-cost">
                <text class="points-num">{{ g.points }}</text>
                <text class="points-unit">{{ $t('pointsMall.pointsUnit') }}</text>
              </view>
              <view class="btn-exchange" @click="onExchange(g)">
                {{ $t('pointsMall.exchange') }}
              </view>
            </view>
          </view>
        </view>
      </view>

      <!-- 积分说明 -->
      <view class="points-rules" :aria-label="$t('pointsMall.rulesLabel')">
        <text class="rules-title">{{ $t('pointsMall.rulesTitle') }}</text>
        <text class="rules-line">{{ $t('pointsMall.rule1') }}</text>
        <text class="rules-line">{{ $t('pointsMall.rule2') }}</text>
        <text class="rules-line">{{ $t('pointsMall.rule3') }}</text>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import { pointsApi } from '@/api'
import { i18n } from '@/i18n'

export default {
  data() {
    return {
      points: 0,
      activeCat: 'all',
      categories: [
        { id: 'all', labelKey: 'pointsMall.cat.all' },
        { id: 'digital', labelKey: 'pointsMall.cat.digital' },
        { id: 'daily', labelKey: 'pointsMall.cat.daily' },
        { id: 'coupon', labelKey: 'pointsMall.cat.coupon' },
      ],
      goods: [],
    }
  },

  computed: {
    filteredGoods() {
      if (this.activeCat === 'all') return this.goods
      return this.goods.filter((g) => g.category === this.activeCat)
    },
  },

  onShow() {
    this.loadData()
  },

  methods: {
    async loadData() {
      // 章节 3.2：查询余额 + 积分礼品列表
      try {
        const balance = await pointsApi.getPointsBalance()
        this.points = balance || 0
      } catch (e) {
        console.warn('[points-mall] load points failed', e)
      }
      try {
        const list = await pointsApi.getPointsGoods()
        const arr = (list && list.data) || list || []
        this.goods = Array.isArray(arr) ? arr : []
      } catch (e) {
        console.warn('[points-mall] load goods failed', e)
        this.goods = []
      }
    },

    goBack() {
      uni.navigateBack()
    },

    onExchange(g) {
      // 章节 3.2：兑换礼品
      if (this.points < g.points) {
        uni.showToast({ title: i18n.t('pointsMall.insufficient'), icon: 'none' })
        return
      }
      const doExchange = async (receiver) => {
        try {
          await pointsApi.exchangePointsGoods(g.id, receiver)
          uni.showToast({ title: i18n.t('pointsMall.exchangeSuccess'), icon: 'success' })
          this.points -= g.points
          this.loadData()
        } catch (e) {
          uni.showToast({
            title: (e && e.message) || i18n.t('pointsMall.exchangeFailed'),
            icon: 'none',
          })
        }
      }

      // 实物礼品需要地址
      if (g.needAddress) {
        uni.showModal({
          title: i18n.t('pointsMall.confirmTitle'),
          content: i18n.t('pointsMall.confirmContentWithAddress', {
            points: g.points,
            name: g.name,
          }),
          confirmText: i18n.t('pointsMall.fillAddress'),
          success: (r) => {
            if (!r.confirm) return
            uni.navigateTo({ url: `/pages/user/address-list?purpose=exchange&goodsId=${g.id}` })
          },
        })
        return
      }
      uni.showModal({
        title: i18n.t('pointsMall.confirmTitle'),
        content: i18n.t('pointsMall.confirmContent', { points: g.points, name: g.name }),
        success: (r) => {
          if (r.confirm) doExchange(null)
        },
      })
    },
  },
}
</script>

<style lang="scss" scoped>
.points-mall {
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
  margin-right: 60rpx;
}

.content {
  padding: 24rpx;
}

.points-card {
  padding: 40rpx 24rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
  text-align: center;
}

.points-label {
  display: block;
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.points-value {
  display: block;
  font-size: 64rpx;
  font-weight: var(--font-weight-bold);
  color: var(--color-primary);
  margin: 8rpx 0;
}

.points-tip {
  display: block;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.cat-tabs {
  display: flex;
  margin: 24rpx 0 16rpx;
  border-bottom: 1rpx solid var(--color-divider);
}

.cat-tab {
  flex: 1;
  height: 80rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--font-size-base);
  color: var(--color-text-tertiary);
  position: relative;
}

.cat-tab.active {
  color: var(--color-text);
  font-weight: var(--font-weight-semibold);
}

.cat-tab.active::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: -1rpx;
  transform: translateX(-50%);
  width: 48rpx;
  height: 4rpx;
  background: var(--color-primary);
  border-radius: 2rpx;
}

.goods-list {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16rpx;
}

.goods-card {
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.goods-image {
  width: 100%;
  aspect-ratio: 1;
  background: var(--color-background);
  display: block;
}

.goods-info {
  padding: 16rpx;
}

.goods-name {
  display: block;
  font-size: var(--font-size-sm);
  color: var(--color-text);
  font-weight: var(--font-weight-medium);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.goods-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12rpx;
}

.points-cost {
  display: flex;
  align-items: baseline;
  gap: 4rpx;
}

.points-num {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-primary);
}

.points-unit {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.btn-exchange {
  padding: 6rpx 16rpx;
  background: var(--color-primary);
  color: var(--color-text);
  border-radius: 999rpx;
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
}

.points-rules {
  margin-top: 32rpx;
  padding: 24rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
}

.rules-title {
  display: block;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  margin-bottom: 12rpx;
}

.rules-line {
  display: block;
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
  line-height: 1.8;
}
</style>
