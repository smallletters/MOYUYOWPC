<template>
  <view class="home">
    <!-- 顶部自定义导航栏 -->
    <view class="navbar">
      <view class="navbar-search" @click="goSearch">
        <u-icon name="search" color="#9A948C" size="18" />
        <text class="navbar-search-placeholder">搜索宠物好物、品牌、攻略</text>
      </view>
      <view class="navbar-icon" @click="onScan">
        <u-icon name="camera-fill" color="#2E2B29" size="22" />
      </view>
      <view class="navbar-icon" @click="goMessages">
        <u-icon name="bell-fill" color="#2E2B29" size="22" />
        <view v-if="unreadCount > 0" class="navbar-badge">{{ unreadCount }}</view>
      </view>
    </view>

    <!-- 滚动内容
         关键：不再用 <scroll-view>，改用普通 <view>，由 uni-app page 原生接管滚动。
         原因：Android 端 scroll-view 与 swiper 嵌套时，touch-slop(≈20px) 会被
         WebView 用来判定"切断惯性滚动"，体感就是"先下滑一点才能上滑"。 -->
    <view class="home-scroll">
      <!-- ① 金刚区：4 大产品线 + 4 个运营入口 -->
      <view class="kingkong">
        <view
          v-for="item in kingkongList"
          :key="item.id"
          class="kingkong-item"
          @click="onKingkongClick(item)"
        >
          <view class="kingkong-icon" :style="{ background: item.bg }">
            <text class="kingkong-emoji luc" :class="$luc(item.icon)" />
          </view>
          <text class="kingkong-label">{{ $t(item.labelKey) }}</text>
        </view>
      </view>

      <!-- ② CMS Banner 轮播（从 /api/v1/cms/banners 拉取，支持管理后台配置图片） -->
      <!-- bannerLoaded 用于在请求未返回前隐藏 swiper，避免初始 swiper 在数据替换时
           触发 uview-plus 的 getBoundingClientRect null 崩溃 -->
      <view v-if="bannerLoaded && banners.length" class="banner">
        <swiper
          :key="bannerVersion"
          :autoplay="true"
          :interval="3000"
          :duration="500"
          :circular="true"
          :disable-touch="true"
          :indicator-dots="true"
          indicator-active-color="#2E2B29"
          indicator-color="rgba(46,43,41,0.3)"
          @click="onBannerClick"
        >
          <swiper-item v-for="(b, idx) in banners" :key="b.id || idx">
            <view class="banner-item">
              <image :src="b.image" class="banner-img" mode="aspectFill" />
              <view v-if="b.title" class="banner-title-overlay">
                <text v-if="b.tag" class="banner-tag">{{ b.tag }}</text>
                <text class="banner-title-text">{{ b.title }}</text>
              </view>
            </view>
          </swiper-item>
        </swiper>
      </view>

      <!-- ③ 推荐流 / 猜你喜欢 + 今日爆款 + 口碑好评（3-tab 切换） -->
      <view class="recommend">
        <view class="recommend-tabs">
          <view
            v-for="tab in recommendTabs"
            :key="tab.key"
            class="recommend-tab"
            :class="{ 'recommend-tab-active': activeTab === tab.key }"
            @click="switchTab(tab.key)"
          >
            <text class="recommend-tab-label">{{ $t(tab.labelKey) }}</text>
            <view v-if="activeTab === tab.key" class="recommend-tab-indicator" />
          </view>
        </view>

        <view v-if="recommendLoading && recommend.length === 0" class="recommend-empty">
          加载中...
        </view>
        <view v-else-if="recommend.length === 0" class="recommend-empty">暂无商品</view>
        <view v-else class="recommend-grid">
          <view
            v-for="p in recommend"
            :key="p.id"
            class="recommend-card"
            @click="goDetail(p.id)">
            <image :src="resolveImage(p)" class="recommend-image" mode="aspectFill" />
            <view class="recommend-body">
              <text class="recommend-name">{{ p.name }}</text>
              <text class="recommend-desc">
                {{ truncate(p.shortDetail || p.detail || '', 30) }}
              </text>
              <view class="recommend-price-row">
                <text class="recommend-price">${{ p.price }}</text>
                <text
                  v-if="p.originalPrice && Number(p.originalPrice) > Number(p.price)"
                  class="recommend-original"
                >
                  ${{ p.originalPrice }}
                </text>
              </view>
              <view v-if="p.rating && p.rating > 0" class="recommend-rating">
                <text class="recommend-star luc-star" />
                <text class="recommend-rating-num">{{ p.rating.toFixed(1) }}</text>
                <text class="recommend-rating-sep">·</text>
                <text class="recommend-rating-count">{{ formatCount(p.reviewCount) }} 好评</text>
              </view>
            </view>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>
<script>
import { cmsApi } from '@/api'

export default {
  pageTitleKey: 'pageTitle.tabbarHome',

  data() {
    return {
      unreadCount: 0,
      // 控制 swiper 是否挂载；首次 onLoad 前为 false，请求完成（无论成功失败）置为 true，
      // 避免初始 banners 渲染 swiper 后被异步数据替换，触发 uview-plus swiper-item.remove
      // 阶段的 getBoundingClientRect null 崩溃
      bannerLoaded: false,
      // swiper 的 :key；数据来源变化时递增，绕开 uview-plus 在 swiper-item 卸载时的 DOM 引用 bug
      bannerVersion: 0,
      // 从 CMS Banner 接口拉取（管理后台「CMS管理 → Banner 管理」配置的图片）。
      // 兜底数据用于接口失败时降级显示，避免页面空白。
      banners: [
        { image: 'https://picsum.photos/750/360?random=1', title: 'MILO 探险家' },
        { image: 'https://picsum.photos/750/360?random=2', title: 'LUNA 策展家' },
      ],
      kingkongList: [
        {
          id: 'care',
          labelKey: 'home.kingkong.care',
          icon: 'spray-can',
          bg: 'var(--background-200)',
        },
        {
          id: 'gear',
          labelKey: 'home.kingkong.gear',
          icon: 'shopping-bag',
          bg: 'var(--background-200)',
        },
        {
          id: 'health',
          labelKey: 'home.kingkong.health',
          icon: 'heart',
          bg: 'var(--background-200)',
        },
        { id: 'play', labelKey: 'home.kingkong.play', icon: 'star', bg: 'var(--background-200)' },
        { id: 'home', labelKey: 'home.kingkong.living', icon: 'home', bg: 'var(--background-200)' },
        { id: 'vip', labelKey: 'home.kingkong.vip', icon: 'star', bg: 'var(--background-200)' },
        {
          id: 'coupon',
          labelKey: 'home.kingkong.coupon',
          icon: 'tag',
          bg: 'var(--background-200)',
        },
        { id: 'flash', labelKey: 'home.kingkong.flash', icon: 'zap', bg: 'var(--background-200)' },
      ],
      // 推荐区 3-tab 数据
      recommendTabs: [
        { key: 'guess', labelKey: 'home.recommendTabs.guess' },
        { key: 'hot', labelKey: 'home.recommendTabs.hot' },
        { key: 'rating', labelKey: 'home.recommendTabs.rating' },
      ],
      activeTab: 'guess',
      recommend: [],
      recommendLoading: false,
    }
  },

  onLoad() {
    this.loadBanners()
    this.loadRecommend()
  },

  onShow() {
    // 拉取未读消息数（预留）
  },

  /**
   * page 级 onPullDownRefresh：与 pages.json 的 enablePullDownRefresh 配对。
   * 用法：触发原生下拉刷新 → 拉数据 → uni.stopPullDownRefresh 收起。
   */
  onPullDownRefresh() {
    Promise.all([this.loadBanners(), this.loadRecommend()])
      .catch(() => {})
      .finally(() => {
        uni.stopPullDownRefresh()
      })
  },

  /**
   * page 级 onReachBottom：原生滚动触底回调，配合下方 onLoadMore 即可。
   */
  onReachBottom() {
    this.onLoadMore()
  },

  methods: {
    async loadBanners() {
      try {
        const list = await cmsApi.getBannerList()
        if (Array.isArray(list) && list.length > 0) {
          // 映射后端字段 → u-swiper 期望的 image/title
          this.banners = list.map((b) => ({
            id: b.id,
            image: b.imageUrl,
            title: b.title,
            link: b.linkUrl,
            tag: b.tag,
            description: b.description,
          }))
        }
      } catch (e) {
        console.warn('[home] load banners failed, fallback to default', e)
      } finally {
        // 无论成功失败都打开 swiper；bannerVersion 自增让 swiper 整体重建，
        // 彻底绕开 uview-plus swiper-item 卸载阶段的 DOM 引用 null 崩溃
        this.bannerVersion += 1
        this.bannerLoaded = true
      }
    },

    async loadRecommend() {
      this.recommendLoading = true
      try {
        const list = await cmsApi.getRecommendProducts(this.activeTab, 10)
        this.recommend = Array.isArray(list) ? list : []
      } catch (e) {
        console.warn('[home] load recommend failed', e)
        this.recommend = []
      } finally {
        this.recommendLoading = false
      }
    },

    async switchTab(key) {
      if (this.activeTab === key) return
      this.activeTab = key
      await this.loadRecommend()
    },

    /**
     * 解析商品主图：优先 mainImage，没有则取 images[0].src，否则空字符串
     * 若是 /uploads/ 相对路径则拼接后端 base（Vite proxy 会代理同源 /uploads）
     */
    resolveImage(p) {
      if (!p) return ''
      const raw = p.mainImage || (Array.isArray(p.images) && p.images[0] && p.images[0].src) || ''
      if (!raw) return ''
      if (raw.startsWith('http')) return raw
      // 相对路径(/uploads/...) APP 端没有 dev server，必须拼上后端 base
      // dev 端 vite proxy 会代理 /uploads/* 同源访问，所以也兼容
      if (raw.startsWith('/')) {
        const base = process.env.VITE_ADMIN_API_BASE
        return base ? `${base}${raw}` : raw
      }
      return raw
    },

    truncate(s, n) {
      if (!s) return ''
      return s.length > n ? s.slice(0, n) + '…' : s
    },

    formatCount(n) {
      if (!n || n < 0) return '0'
      if (n >= 1000) {
        return (n / 1000).toFixed(1).replace(/\.0$/, '') + 'k+'
      }
      return String(n)
    },

    // 兼容保留：现在页面级下拉刷新由 onPullDownRefresh 接管；
    // 保留此方法以防其它位置有引用。其内不再操作已删除的 refreshing。
    async onRefresh() {
      await Promise.all([this.loadBanners(), this.loadRecommend()])
    },

    // 触底加载更多：append 到现有 recommend 列表。受当前单页设计限制，
    // 这里只把"还有更多数据"的占位行为接入，保持行为简单不破坏 UI。
    onLoadMore() {
      // 单页（首页）当前不做分页；显式空实现，保持 uni-app page onReachBottom 调用不报错。
    },

    onKingkongClick(item) {
      // 根据金刚区类型跳转
      if (['care', 'gear', 'health', 'play', 'home'].includes(item.id)) {
        uni.switchTab({ url: '/pages/tabbar/category' })
        // 通过 globalData 传递选中分类
        getApp().globalData.categoryTab = item.id
      } else if (item.id === 'coupon') {
        uni.navigateTo({ url: '/pages/user/coupon-center' })
      } else if (item.id === 'vip') {
        uni.navigateTo({ url: '/pages/user/membership' })
      } else if (item.id === 'subscribe') {
        uni.navigateTo({ url: '/pages/user/subscribe' })
      } else if (item.id === 'flash') {
        uni.navigateTo({ url: '/pages/goods/flash-sale' })
      } else {
        uni.showToast({ title: '敬请期待', icon: 'none' })
      }
    },

    onBannerClick(idx) {
      uni.showToast({ title: `Banner ${idx + 1}`, icon: 'none' })
    },

    goSearch() {
      uni.navigateTo({ url: '/pages/goods/search' })
    },

    goMessages() {
      uni.navigateTo({ url: '/pages/user/messages' })
    },

    goDetail(id) {
      uni.navigateTo({ url: `/pages/goods/detail?id=${id}` })
    },

    goCategory() {
      uni.switchTab({ url: '/pages/tabbar/category' })
    },

    onScan() {
      // #ifdef APP-PLUS
      uni.scanCode({
        success: (res) => {
          if (res.result) {
            uni.showToast({ title: `识别: ${res.result.slice(0, 20)}`, icon: 'none' })
            // 实际项目中根据 URL 解析跳转
          }
        },
        fail: () => {
          uni.showToast({ title: '未识别到有效二维码', icon: 'none' })
        },
      })
      // #endif
      // #ifdef H5
      uni.showToast({ title: '请使用 APP 扫码', icon: 'none' })
      // #endif
    },
  },
}
</script>

<style lang="scss" scoped>
.home {
  /* 不再用 flex 嵌套结构，外层去掉固定高度，由 uni-app page 自身的 overflow 接管滚动 */
  background: var(--color-background);
  /* 给页面内容预留底部 tabbar 高度，避免最后一项被遮挡 */
  padding-bottom: 0;
}

.navbar {
  display: flex;
  align-items: center;
  padding: 16rpx 24rpx;
  /* 状态栏安全区：uni-app 编译后 --status-bar-height 是真机状态栏高度（如 44px），env 是 iOS safe area */
  /* H5 调试下两个值都是 0，真机/PWA/WebView 嵌入时自动撑开 */
  padding-top: calc(env(safe-area-inset-top, 0px) + var(--status-bar-height, 0px));
  min-height: calc(env(safe-area-inset-top, 0px) + var(--status-bar-height, 0px) + 44px);
  background: var(--color-surface);
  box-sizing: border-box;
  /* 让 navbar 在 page 文档流中作为正常块级元素，不参与 flex 拉伸 */
  position: sticky;
  top: 0;
  z-index: 10;
}

.navbar-search {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12rpx;
  background: var(--color-background);
  border-radius: var(--radius-pill);
  padding: 16rpx 24rpx;
}

.navbar-search-placeholder {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.navbar-icon {
  position: relative;
  margin-left: 24rpx;
}

.navbar-badge {
  position: absolute;
  top: -8rpx;
  right: -8rpx;
  min-width: 28rpx;
  height: 28rpx;
  padding: 0 6rpx;
  background: var(--color-danger);
  color: #fff;
  font-size: 20rpx;
  border-radius: var(--radius-pill);
  text-align: center;
  line-height: 28rpx;
}

.home-scroll {
  /* 由 uni-app page 接管滚动，这里只是普通内容容器 */
}

.banner {
  margin: 24rpx 32rpx 0;
  border-radius: 24rpx;
  overflow: hidden;
  box-shadow: 0 6rpx 20rpx rgba(0, 0, 0, 0.04);
}
.banner :deep(.uni-swiper-wrapper),
.banner swiper {
  width: 100%;
  height: 320rpx;
}
.banner swiper-item,
.banner :deep(.uni-swiper-item) {
  width: 100%;
  height: 320rpx;
}
.banner-item {
  position: relative;
  width: 100%;
  height: 320rpx;
}
.banner-img {
  width: 100%;
  height: 320rpx;
  display: block;
}
.banner-title-overlay {
  position: absolute;
  left: 24rpx;
  bottom: 24rpx;
  right: 24rpx;
  background: rgba(0, 0, 0, 0.35);
  padding: 12rpx 16rpx;
  border-radius: 12rpx;
  display: flex;
  align-items: center;
  gap: 12rpx;
}
.banner-tag {
  background: var(--color-primary);
  color: #2e2b29;
  font-size: 20rpx;
  font-weight: 700;
  padding: 4rpx 12rpx;
  border-radius: 6rpx;
}
.banner-title-text {
  color: #fff;
  font-size: 26rpx;
  font-weight: 600;
}

.kingkong {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16rpx;
  padding: 24rpx;
  background: var(--color-surface);
  margin: 0 24rpx;
  border-radius: var(--radius-lg);
}

.kingkong-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
}

.kingkong-icon {
  width: 96rpx;
  height: 96rpx;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
}

.kingkong-emoji {
  font-size: 44rpx;
}

.kingkong-label {
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}

/* 推荐区（猜你喜欢 / 今日爆款 / 口碑好评） */
.recommend {
  padding: 16rpx 24rpx 32rpx;
  background: var(--color-background);
}

.recommend-tabs {
  display: flex;
  position: relative;
  border-bottom: 1rpx solid var(--color-divider);
  margin-bottom: 20rpx;
}

.recommend-tab {
  flex: 1;
  position: relative;
  text-align: center;
  padding: 24rpx 0 16rpx;
}

.recommend-tab-label {
  font-size: var(--font-size-base);
  color: var(--color-text-tertiary);
  font-weight: var(--font-weight-medium);
}

.recommend-tab-active .recommend-tab-label {
  color: var(--color-text);
  font-weight: var(--font-weight-semibold);
}

.recommend-tab-indicator {
  position: absolute;
  bottom: -1rpx;
  left: 50%;
  transform: translateX(-50%);
  width: 64rpx;
  height: 4rpx;
  background: var(--color-primary);
  border-radius: 2rpx;
}

.recommend-empty {
  text-align: center;
  padding: 48rpx 0;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}

.recommend-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16rpx;
}

.recommend-card {
  background: var(--color-surface);
  border-radius: var(--radius-md);
  overflow: hidden;
  border: 1rpx solid var(--color-divider);
}

.recommend-image {
  width: 100%;
  aspect-ratio: 1;
  background: var(--color-background);
  display: block;
}

.recommend-body {
  padding: 16rpx;
}

.recommend-name {
  display: block;
  font-size: var(--font-size-sm);
  color: var(--color-text);
  font-weight: var(--font-weight-medium);
  line-height: 1.4;
  /* 单行省略 */
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recommend-desc {
  display: block;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin: 6rpx 0 12rpx;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recommend-price-row {
  display: flex;
  align-items: baseline;
  gap: 8rpx;
  margin-bottom: 6rpx;
}

.recommend-price {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-primary);
}

.recommend-original {
  font-size: var(--font-size-xs);
  color: var(--color-text-300);
  text-decoration: line-through;
}

.recommend-rating {
  display: flex;
  align-items: center;
  gap: 4rpx;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.recommend-star {
  color: var(--color-warning, #f6a609);
  font-size: var(--font-size-sm);
}

.recommend-rating-num {
  color: var(--color-text);
  font-weight: var(--font-weight-medium);
}

.recommend-rating-sep {
  color: var(--color-divider);
  margin: 0 2rpx;
}

.recommend-rating-count {
  color: var(--color-text-tertiary);
}
</style>
