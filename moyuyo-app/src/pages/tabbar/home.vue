<template>
  <view class="home">
    <!-- 顶部自定义导航栏 -->
    <view class="navbar">
      <view class="navbar-search" @click="goSearch">
        <u-icon name="search" color="#9A948C" size="18" />
        <text class="navbar-search-placeholder">{{ $t('home.searchPlaceholder') }}</text>
      </view>
      <view class="navbar-icon" @click="onScan">
        <u-icon name="camera-fill" color="#2E2B29" size="22" />
      </view>
      <view class="navbar-icon" @click="goNotifications">
        <u-icon name="bell-fill" color="#2E2B29" size="22" />
        <view v-if="unreadCount > 0" class="navbar-badge">{{ unreadCount }}</view>
      </view>
    </view>

    <!-- 滚动内容
         关键：不再用 <scroll-view>，改用普通 <view>，由 uni-app page 原生接管滚动。
         原因：Android 端 scroll-view 与 swiper 嵌套时，touch-slop(≈20px) 会被
         WebView 用来判定"切断惯性滚动"，体感就是"先下滑一点才能上滑"。 -->
    <view class="home-scroll">
      <!-- ① 金刚区：4 个运营入口（分类 / 会员 / 领券 / 限时） -->
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
      <view v-if="bannerLoaded && displayBanners.length" class="banner">
        <swiper
          :key="bannerVersion"
          :autoplay="true"
          :interval="3000"
          :duration="500"
          :circular="true"
          :indicator-dots="true"
          indicator-active-color="#2E2B29"
          indicator-color="rgba(46,43,41,0.3)"
        >
          <swiper-item v-for="(b, idx) in displayBanners" :key="b.id || idx">
            <!-- 点击事件必须绑在 swiper-item 内部：swiper 本身的 @click 在 H5 下拿到的是
                 原生 MouseEvent（无 detail.current），APP 下事件结构也不一致，
                 直接用 v-for 闭包里的 idx 最可靠 -->
            <view class="banner-item" @click="onBannerClick(idx)">
              <image
                v-if="b.image"
                :src="$img(b.image)"
                class="banner-img"
                mode="aspectFill" />
              <!-- 无图兜底（接口失败时的本地降级 banner）：纯色块，不依赖任何外链 -->
              <view v-else class="banner-placeholder" />
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
          {{ $t('home.loading') }}
        </view>
        <view v-else-if="recommend.length === 0" class="recommend-empty">
          {{ $t('category.empty.product') }}
        </view>
        <view v-else class="recommend-grid">
          <view
            v-for="p in recommend"
            :key="p.id"
            class="recommend-card"
            @click="goDetail(p.id)">
            <!-- 图片容器负责撑出 1:1 高度：uni-image 自带固定 240px 高度，
                 直接给它 aspect-ratio 无效，宽屏（折叠屏展开/平板）下会只拉宽不拉高 -->
            <view class="recommend-image-wrap">
              <image :src="resolveImage(p)" class="recommend-image" mode="aspectFill" />
            </view>
            <view class="recommend-body">
              <text class="recommend-name">{{ p.name }}</text>
              <!-- 只展示纯文本简短描述，避免回退到富文本 detail 露出 HTML 标签 -->
              <text v-if="p.shortDetail" class="recommend-desc">
                {{ truncate(p.shortDetail, 30) }}
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
                <text class="recommend-rating-count">
                  {{ $t('home.recommendRating', { count: formatCount(p.reviewCount) }) }}
                </text>
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
import { reportActiveHeartbeat } from '@/utils/heartbeat'
import { toAbsoluteImageUrl } from '@/utils/imageUrl'
import { useThemeStore } from '@/store'

/**
 * 站内 tabBar 页：只能用 uni.switchTab 打开，navigateTo 会直接失败。
 * 与 pages.json 的 tabBar.list 保持一致。
 */
const TAB_BAR_PAGES = [
  '/pages/tabbar/home',
  '/pages/tabbar/pet',
  '/pages/tabbar/community',
  '/pages/tabbar/messages',
  '/pages/tabbar/user',
]

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
      // 兜底数据用于接口失败时降级显示，避免页面空白（见 computed fallbackBanners）。
      banners: [],
      kingkongList: [
        // 分类入口：进入分类页（不预设产品线，由用户在分类页内自行切换）
        {
          id: 'category',
          labelKey: 'home.kingkong.category',
          icon: 'grid',
          bg: 'var(--background-200)',
        },
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
    // 原生 tabBar 不吃 CSS 变量，深色模式下必须在这里重新上色。
    // 切 tab / 从设置页（非 tabBar 页）返回都会走到这里；
    // 且 setTabBarStyle 要求当前页是 tabBar 页，这里是唯一能可靠落色的时机。
    useThemeStore().syncTabBar()
    // 心跳改由 @/utils/heartbeat 提供；此处调用以保持"切到首页 tab"也算活跃
    reportActiveHeartbeat()
  },

  computed: {
    /**
     * Banner 兜底数据（接口失败 / 数据未返回时降级展示）。
     * 用 computed 而非 data：标题走 i18n，locale 切换时跟随刷新。
     *
     * 不配置 image：原来用的是 picsum.photos 外链，真机常常只连局域网、没有公网出口，
     * 会渲染成空白条。这里退化成品牌渐变色块 + 标题，离线也稳定。
     */
    fallbackBanners() {
      return [{ title: this.$t('home.bannerMilo') }, { title: this.$t('home.bannerLuna') }]
    },
    /**
     * 实际渲染的 banner 列表：接口数据优先，空则回落兜底。
     */
    displayBanners() {
      return this.banners.length ? this.banners : this.fallbackBanners
    },
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
          // 图片空间返回的是 /uploads/** 相对路径，APP 端必须补成绝对地址，否则原生 <image> 会空白
          this.banners = list.map((b) => ({
            id: b.id,
            image: toAbsoluteImageUrl(b.imageUrl),
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
      if (item.id === 'category') {
        this.goCategory()
      } else if (item.id === 'coupon') {
        uni.navigateTo({ url: '/pages/user/coupon-center' })
      } else if (item.id === 'vip') {
        uni.navigateTo({ url: '/pages/user/membership' })
      } else if (item.id === 'flash') {
        uni.navigateTo({ url: '/pages/goods/flash-sale' })
      } else {
        uni.showToast({ title: this.$t('common.comingSoon'), icon: 'none' })
      }
    },

    /**
     * 点击海报：跳到 CMS 里配置的 linkUrl。
     *
     * 入参是 v-for 闭包里的索引（swiper-item 内部绑定）。
     * 旧实现把 swiper 的 @click 事件对象当索引用（`idx + 1`），
     * 于是 toast 文案变成 "Banner [object Object]1" —— 保留一点防御，
     * 万一将来又被改回绑在 swiper 上也不会再拼出脏字符串。
     */
    onBannerClick(idx) {
      let i = Number(idx)
      if (!Number.isFinite(i)) {
        // 兜底：误传事件对象时尝试从 detail.current 取，再不行就取第 1 张
        const fromDetail = Number(idx && idx.detail && idx.detail.current)
        i = Number.isFinite(fromDetail) ? fromDetail : 0
      }
      const banner = this.displayBanners[i]
      if (!banner) return
      const link = String(banner.link || '').trim()
      // 兜底 banner（接口失败时的降级数据）没有链接，静默忽略
      if (!link) return
      this.openBannerLink(link)
    },

    /**
     * 解析并打开 banner 链接。
     * CMS 里运营通常填三种形态（见 mo_cms_banner.link_url 现有数据）：
     *  1) 带域名的站内页：http://192.168.64.103:5174/pages/goods/detail?id=xxx
     *  2) 站内相对路径：/pages/goods/detail?id=xxx
     *  3) 外部网页：https://example.com/activity
     */
    openBannerLink(link) {
      // 只把「路径从 /pages/ 开始」的链接当站内页：
      // 允许无域名（/pages/x）或带域名（http://host/pages/x），
      // 但 https://example.com/blog/pages/x 这种外站路径不会被误判。
      // 用纯字符串 + 正则实现，不依赖 URL 构造函数（APP 端不保证可用）。
      const internal = link.match(/^(?:https?:\/\/[^/]+)?(\/pages\/[^?#]*(?:\?[^#]*)?)$/i)
      if (internal) {
        const path = internal[1]
        const base = path.split('?')[0]
        if (TAB_BAR_PAGES.includes(base)) {
          // tabBar 页在 uni 里只能用 switchTab，且不能带 query
          uni.switchTab({ url: base })
        } else {
          uni.navigateTo({
            url: path,
            fail: () => {
              // 页面未注册 / 已下线时给明确提示，避免点了没反应
              uni.showToast({ title: this.$t('home.bannerUnavailable'), icon: 'none' })
            },
          })
        }
        return
      }

      if (/^https?:\/\//i.test(link)) {
        // 纯外链：交给系统浏览器 / 新标签页，避免内嵌 webview 无法返回
        // #ifdef APP-PLUS
        plus.runtime.openURL(link)
        // #endif
        // #ifdef H5
        window.open(link, '_blank')
        // #endif
        return
      }

      uni.showToast({ title: this.$t('home.bannerUnavailable'), icon: 'none' })
    },

    goSearch() {
      uni.navigateTo({ url: '/pages/goods/search' })
    },

    goNotifications() {
      uni.navigateTo({ url: '/pages/user/notifications' })
    },

    goDetail(id) {
      uni.navigateTo({ url: `/pages/goods/detail?id=${id}` })
    },

    goCategory() {
      // 分类页不再是 tabBar 页，必须用 navigateTo
      uni.navigateTo({ url: '/pages/tabbar/category' })
    },

    onScan() {
      // #ifdef APP-PLUS
      uni.scanCode({
        success: (res) => {
          if (res.result) {
            uni.showToast({
              title: this.$t('home.scanResult', { value: res.result.slice(0, 20) }),
              icon: 'none',
            })
            // 实际项目中根据 URL 解析跳转
          }
        },
        fail: () => {
          uni.showToast({ title: this.$t('home.scanInvalid'), icon: 'none' })
        },
      })
      // #endif
      // #ifdef H5
      uni.showToast({ title: this.$t('home.scanAppOnly'), icon: 'none' })
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
  /* 状态栏安全区：uni-app 编译后 --status-bar-height 是真机状态栏高度（如 44px），env 是 iOS safe area
     H5 调试下两个值都是 0，真机/PWA/WebView 嵌入时自动撑开。
     取二者较大值（而非相加）：iOS 上 env 与 status-bar-height 同为状态栏高度，相加会翻倍 */
  padding-top: var(--status-bar-height, 0px);
  padding-top: max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px));
  min-height: calc(44px + var(--status-bar-height, 0px));
  min-height: calc(44px + max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px)));
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

.banner {
  margin: 24rpx 32rpx 0;
  border-radius: 24rpx;
  overflow: hidden;
  box-shadow: var(--shadow-pop);
}
.banner :deep(.uni-swiper-wrapper),
.banner swiper {
  width: 100%;
  /* 海报源图为 16:9，高度按容器宽度（屏宽 - 左右各 32rpx 外边距）等比计算：
     固定 320rpx 在折叠屏展开/平板等宽屏下只拉宽不拉高，图片会被裁切 */
  height: calc((100vw - 64rpx) * 0.5625);
}
.banner swiper-item,
.banner :deep(.uni-swiper-item) {
  width: 100%;
  height: calc((100vw - 64rpx) * 0.5625);
}
.banner-item {
  position: relative;
  width: 100%;
  height: calc((100vw - 64rpx) * 0.5625);
}
.banner-img {
  width: 100%;
  height: calc((100vw - 64rpx) * 0.5625);
  display: block;
}
/* 无图兜底：本地纯色块，不依赖公网图片 */
.banner-placeholder {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  background: var(--background-200, #ede9e4);
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
  /* 金刚区现有 4 个入口，按 4 列均分（原 5 列布局预留的产品线入口已移除） */
  grid-template-columns: repeat(4, 1fr);
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

/* 图片容器：padding-top 百分比相对卡片宽度计算，高度始终等于宽度（1:1 方形）。
   这样折叠屏展开、平板等宽屏下卡片变宽时，图片高度同步变高，不会被 uni-image 的 240px 固定高度裁切。 */
.recommend-image-wrap {
  position: relative;
  width: 100%;
  padding-top: 100%;
  background: var(--color-background);
}

.recommend-image {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
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
  color: var(--color-text-tertiary);
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
