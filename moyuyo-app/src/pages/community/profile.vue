<template>
  <view class="profile-page">
    <!-- 顶部用户卡：头像 + 昵称 + 简介 -->
    <view class="user-card">
      <image :src="avatarSrc" class="avatar" mode="aspectFill" />
      <view class="user-meta">
        <text class="nickname">
          {{
            viewedProfile?.nickname ||
              userStore.userInfo?.nickname ||
              userStore.userInfo?.email ||
              t('userCenter.defaultNickname')
          }}
        </text>
        <!-- 简介：自己 profile 时点击触发编辑/清空；他人 profile 纯展示 -->
        <text class="bio" :class="{ 'bio--editable': isOwnProfile }" @tap="onTapBio">
          {{ bioText }}
        </text>
      </view>
    </view>

    <!-- 数据条：4 段式（关注/粉丝/帖子/获赞） -->
    <view class="stat-bar">
      <view class="stat-cell">
        <text class="stat-num">{{ statFollowing }}</text>
        <text class="stat-label">{{ t('userCenter.profileStatFollowing') }}</text>
      </view>
      <view class="stat-divider" />
      <view class="stat-cell">
        <text class="stat-num">{{ statFollowers }}</text>
        <text class="stat-label">{{ t('userCenter.profileStatFollowers') }}</text>
      </view>
      <view class="stat-divider" />
      <view class="stat-cell">
        <text class="stat-num">{{ statPosts }}</text>
        <text class="stat-label">{{ t('userCenter.profileStatPosts') }}</text>
      </view>
      <view class="stat-divider" />
      <view class="stat-cell">
        <text class="stat-num">{{ statLikes }}</text>
        <text class="stat-label">{{ t('userCenter.profileStatLikes') }}</text>
      </view>
    </view>

    <!-- 三 Tab 切换：帖子 / 点赞 / 收藏 -->
    <view class="tab-bar">
      <view
        v-for="tab in tabs"
        :key="tab.key"
        class="tab"
        :class="{ 'tab--active': activeTab === tab.key }"
        @tap="onSwitchTab(tab.key)"
      >
        <text class="tab-label">{{ tab.label }}</text>
        <view v-if="activeTab === tab.key" class="tab-indicator" />
      </view>
    </view>

    <!-- 编辑简介弹窗：自定义 IOS 风格弹层 -->
    <view v-if="showBioEditor" class="bio-modal-mask" @tap="closeBioEditor">
      <view class="bio-modal" @tap.stop>
        <text class="bio-modal-title">{{ t('userCenter.profileBioEdit') }}</text>
        <textarea
          v-model="bioDraft"
          class="bio-modal-textarea"
          :maxlength="bioMaxLength"
          :placeholder="t('userCenter.profileBioPlaceholder')"
          :show-confirm-bar="false"
          :adjust-position="false"
          auto-height
          focus
          @input="onBioInput"
        />
        <view class="bio-modal-footer">
          <text class="bio-modal-counter">{{ bioDraft.length }}/{{ bioMaxLength }}</text>
        </view>
        <view class="bio-modal-actions">
          <view class="bio-modal-btn bio-modal-btn--cancel" @tap="closeBioEditor">
            <text>{{ t('common.cancel') || '取消' }}</text>
          </view>
          <view class="bio-modal-btn bio-modal-btn--confirm" @tap="onBioConfirm">
            <text>{{ t('common.confirm') || '确定' }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 单列列表 + 加载状态 -->
    <view class="list-wrap">
      <!-- 加载中：仅在首屏 loading 时展示 -->
      <view v-if="activeLoading" class="status-line">{{ t('common.loading') || '加载中…' }}</view>
      <!-- 空态：三种文案区分 -->
      <view v-else-if="!activeList.length" class="empty">
        <text class="empty-icon luc luc-file-text" />
        <text class="empty-text">{{ activeEmptyText }}</text>
      </view>
      <!-- 列表 -->
      <view v-else class="post-list">
        <view
          v-for="post in activeList"
          :key="post.id"
          class="post-item"
          @tap="goDetail(post.id)"
          @longpress="onLongPressPost(post)"
        >
          <view class="post-header">
            <image
              :src="post.avatar ? toAbs(post.avatar) : defaultAvatar"
              class="post-avatar"
              mode="aspectFill"
            />
            <view class="post-user">
              <text class="post-username">
                {{ post.username || t('userCenter.defaultNickname') }}
              </text>
              <text class="post-time">{{ formatTime(post.createTime) }}</text>
            </view>
          </view>
          <view v-if="post.topic" class="post-topic">#{{ post.topic }}</view>
          <view class="post-content">{{ post.content }}</view>
          <!-- 多图九宫格：复用详情页规则 1=单图 / 2~4=两列 / 5+=三列 -->
          <view
            v-if="post.images && post.images.length"
            class="post-image-grid"
            :class="gridClassFor(post.images.length)"
          >
            <view
              v-for="(img, idx) in post.images"
              :key="idx"
              class="post-image-cell"
              @tap.stop="previewPostImages(post.images, idx)"
            >
              <image
                :src="img"
                class="post-image"
                :mode="
                  gridClassFor(post.images.length) === 'grid-single' ? 'widthFix' : 'aspectFill'
                "
              />
            </view>
          </view>
          <!-- 底部数据条 -->
          <view class="post-meta">
            <text class="meta-item">
              <text class="luc luc-heart" />
              <text class="meta-num">{{ post.likes || 0 }}</text>
            </text>
            <text class="meta-item">
              <text class="luc luc-message-circle" />
              <text class="meta-num">{{ post.comments || 0 }}</text>
            </text>
          </view>
        </view>
        <!-- 加载更多 / 到底提示 -->
        <view v-if="activeList.length" class="footer-line">
          {{ activeFinished ? t('userCenter.profileLoadDone') : t('userCenter.profileLoadMore') }}
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { communityApi, userApi } from '@/api'
import followApi from '@/api/follow'
import { useUserStore } from '@/store'
import { i18n } from '@/i18n'
import { toAbsoluteImageUrl } from '@/utils/imageUrl'
import { setStorage, STORAGE_KEYS } from '@/utils/storage'

// 单页大小：与其它分页列表一致
const PAGE_SIZE = 20

// 三个 tab 对应的数据源加载器（自己 profile）
// 三个分支参数完全一致(只需要 page/size),用 config 数组减少模板里重复代码
const TAB_LOADERS = {
  posts: (page, size) => communityApi.getMyPosts({ page, size }),
  liked: (page, size) => communityApi.getLikedPosts({ page, size }),
  collected: (page, size) => communityApi.getCollectedPosts({ page, size }),
}

// 他人 profile：只展示该用户的"帖子" tab（liked/collected 是私有的）
// 用 viewerId 关闭(避免 401 时静默失败)，showError:true 让用户看到错误提示
const OTHER_TAB_LOADERS = {
  posts: (viewerId, page, size) => communityApi.getUserPosts(viewerId, { page, size }),
}

export default {
  // 页面级 i18n 标题:navbar 标题文案来源
  pageTitleKey: 'pageTitle.communityProfile',

  data() {
    return {
      defaultAvatar: 'https://i.pravatar.cc/100?img=20',
      // locale 切换时自增,触发依赖文案的 computed 重算
      localeVersion: 0,
      // 当前激活的 tab key
      activeTab: 'posts',
      // 各 tab 独立维护 list/page/finished/loading,避免切 tab 时相互覆盖
      tabsData: {
        posts: { list: [], page: 0, finished: false, loading: false },
        liked: { list: [], page: 0, finished: false, loading: false },
        collected: { list: [], page: 0, finished: false, loading: false },
      },
      // 用户数据条
      statFollowing: 0,
      statFollowers: 0,
      statPosts: 0,
      statLikes: 0,
      // 用户简介：当前 UserVO 没有 bio 字段,展示默认文案
      bioText: '',
      // 他人 profile：从 query.id 解析出的被查看者 id（未传则是自己）
      viewedUserId: 0,
      // 拉取到的被查看者公开资料（id/nickname/avatar/bio/following/followers/isFollowing）
      viewedProfile: null,
      // loadOtherProfile 防抖锁：onShow 反复触发时避免重复请求 + 闪烁
      otherProfileLoading: false,
      // bio 保存中：避免用户连点触发多次 PUT
      bioSaving: false,
      // 自定义简介编辑弹窗状态
      showBioEditor: false,
      // 弹窗内编辑草稿（受控）
      bioDraft: '',
      // bio 字符上限：与后端 ProfileUpdateRequest @Size(max=200) 对齐
      bioMaxLength: 200,
    }
  },

  computed: {
    userStore() {
      return useUserStore()
    },
    /**
     * 模板用的 t(key):内部访问 localeVersion 建立响应式依赖,
     * locale 切换后下一次模板渲染会拿到新文案
     */
    t() {
      return (key) => {
        void this.localeVersion
        return i18n.t(key)
      }
    },
    /**
     * tab 配置:key 用于数据索引,label 取 i18n 文案
     * locale 变化时 i18n.t 重新求值,这里 read localeVersion 建立依赖
     * 自己 profile 显示全部 3 个 tab；他人 profile 只显示"帖子"（liked/collected 是私有的）
     */
    tabs() {
      void this.localeVersion
      if (!this.isOwnProfile) {
        return [{ key: 'posts', label: i18n.t('userCenter.profileTabPosts') }]
      }
      return [
        { key: 'posts', label: i18n.t('userCenter.profileTabPosts') },
        { key: 'liked', label: i18n.t('userCenter.profileTabLiked') },
        { key: 'collected', label: i18n.t('userCenter.profileTabCollected') },
      ]
    },
    /** 当前激活 tab 的列表数据 */
    activeList() {
      return this.tabsData[this.activeTab].list
    },
    /** 当前激活 tab 是否正在首次加载 */
    activeLoading() {
      return this.tabsData[this.activeTab].loading
    },
    /** 当前激活 tab 是否已无更多 */
    activeFinished() {
      return this.tabsData[this.activeTab].finished
    },
    /** 当前激活 tab 的空态文案（按 tab 区分） */
    activeEmptyText() {
      const map = {
        posts: 'userCenter.profileEmptyPosts',
        liked: 'userCenter.profileEmptyLiked',
        collected: 'userCenter.profileEmptyCollected',
      }
      return i18n.t(map[this.activeTab])
    },
    /**
     * 头像 src:复用 user.vue 的 cache busting 思路,
     * 但本页面 onShow 时再 +1,避免从 user.vue 跳转过来仍展示旧头像
     */
    avatarSrc() {
      // 他人 profile：优先用 viewedProfile.avatar，自己 profile 退回登录用户头像
      const raw =
        this.viewedProfile?.avatar || this.userStore.userInfo?.avatar || this.defaultAvatar
      return toAbsoluteImageUrl(raw)
    },
    /**
     * 是否为查看自己：只看 query 是否传了 id。
     * 不要依赖 userStore.userInfo.id 判断,onLoad 时 userInfo 可能还没就绪(异步),
     * 此时会把"他人 profile"误判为"自己" → 走 getMyPosts 拉到错误数据。
     * 判定规则保持简单：query 有 id 即"他人"。
     */
    isOwnProfile() {
      return !this.viewedUserId
    },
  },

  onLoad(query = {}) {
    // 默认 bio 文案：用户无 bio 时展示
    this.bioText = this.resolveBioText()
    // 订阅 locale 变化,触发依赖文案的 computed 重算
    this._unsubLocale = i18n.subscribe(() => {
      this.localeVersion += 1
      // locale 切换后默认文案可能变化,优先保留真实 bio,空时回退到默认
      this.bioText = this.resolveBioText()
    })
    // 解析 query.id：有传 id 即"查看他人 profile"
    const rawId = query && (query.id ?? query.userId)
    const parsedId = Number(rawId)
    this.viewedUserId = Number.isFinite(parsedId) && parsedId > 0 ? parsedId : 0
    // 每次进入页面都清空 viewedProfile,避免上一次进入的他人数据残留
    this.viewedProfile = null
    // 他人 profile 只能看"帖子" tab,防止上一次的 activeTab 残留为 liked/collected 导致空态
    if (!this.isOwnProfile) {
      this.activeTab = 'posts'
      // 重置 tabs 数据,避免上一次进入的他人 A 的帖子列表残留到他人 B
      this.tabsData.posts = { list: [], page: 0, finished: false, loading: false }
      this.tabsData.liked = { list: [], page: 0, finished: true, loading: false }
      this.tabsData.collected = { list: [], page: 0, finished: true, loading: false }
    }
    // 首屏加载：自己走旧逻辑；他人走公开 profile + 用户帖子
    if (this.isOwnProfile) {
      this.loadStatBar()
      this.loadActiveTab(true)
    } else {
      this.loadOtherProfile()
    }
  },

  onShow() {
    // 从详情页返回时,如果点赞/收藏状态可能变化,刷新当前 tab 列表
    // 用户卡(statBar)也重拉,确保关注/粉丝/帖子数最新
    if (this.isOwnProfile) {
      // bio 可能在他处被改,onShow 时按 store 当前值刷新一次
      this.bioText = this.resolveBioText()
      this.loadStatBar()
      this.refreshActiveTab()
    } else {
      // 他人 profile：刷新被查看者的关注/粉丝数和帖子（关注状态可能变化）
      this.loadOtherProfile()
    }
  },

  onUnload() {
    if (this._unsubLocale) {
      this._unsubLocale()
      this._unsubLocale = null
    }
  },

  /**
   * 触底加载：scroll-view 触底时调用,加载当前 tab 下一页
   */
  onReachBottom() {
    const tab = this.tabsData[this.activeTab]
    if (tab.loading || tab.finished) return
    this.loadActiveTab(false)
  },

  /**
   * 下拉刷新：APP 端需要在 pages.json 配置 enablePullDownRefresh:true,
   * 但本页面未启用,先留接口备用
   */
  onPullDownRefresh() {
    this.refreshActiveTab().finally(() => uni.stopPullDownRefresh())
  },

  methods: {
    /**
     * 切换 tab：点击 tab 触发
     * - 首次切到新 tab 时自动触发首次加载
     * - 已加载过的 tab 直接展示缓存,避免重复请求
     */
    onSwitchTab(key) {
      if (this.activeTab === key) return
      this.activeTab = key
      const tab = this.tabsData[key]
      // 首次切到该 tab 且无数据时,触发首次加载
      if (!tab.list.length && !tab.finished && !tab.loading) {
        this.loadActiveTab(true)
      }
    },

    /**
     * 实际加载当前 tab 的下一页
     * @param reset true 表示重置(用于首次加载或下拉刷新)
     */
    async loadActiveTab(reset) {
      const tabKey = this.activeTab
      const tab = this.tabsData[tabKey]
      if (tab.loading) return
      tab.loading = true
      const nextPage = reset ? 1 : tab.page + 1
      try {
        // 自己 profile 用默认三 tab 加载器；他人 profile 走带 userId 的加载器
        const loaders = this.isOwnProfile ? TAB_LOADERS : OTHER_TAB_LOADERS
        const loader = loaders[tabKey]
        if (!loader) {
          // 当前 tab 在该 profile 模式下不可用(例如他人模式下切到 liked/collected),直接置 finished
          tab.list = []
          tab.finished = true
          tab.loading = false
          return
        }
        const res = this.isOwnProfile
          ? await loader(nextPage, PAGE_SIZE)
          : await loader(this.viewedUserId, nextPage, PAGE_SIZE)
        // 后端 IPage 结构：{ records, total, current, size }
        // 老版本可能直接返回数组;做一次降级处理
        const records = Array.isArray(res) ? res : res?.records || []
        const total = Number(res?.total ?? records.length)
        // 图片相对路径转绝对路径,APP 端 image 才能正常加载
        const normalized = records.map((p) => ({
          ...p,
          images: Array.isArray(p.images) ? p.images.map(toAbsoluteImageUrl) : [],
          cover: p.cover ? toAbsoluteImageUrl(p.cover) : null,
          avatar: p.avatar ? toAbsoluteImageUrl(p.avatar) : null,
        }))
        if (reset) {
          tab.list = normalized
          tab.page = 1
        } else {
          tab.list = tab.list.concat(normalized)
          tab.page = nextPage
        }
        // 全部加载完判定：列表长度 >= total 或本页为空
        tab.finished = tab.list.length >= total || normalized.length === 0
      } catch (e) {
        uni.showToast({
          title: this.t('userCenter.profileLoadFailed'),
          icon: 'none',
        })
      } finally {
        tab.loading = false
      }
    },

    /** 刷新当前 tab（重置为首页） */
    async refreshActiveTab() {
      await this.loadActiveTab(true)
    },

    /**
     * 加载数据条：帖子数用当前激活 tab 列表的 total 兜底,
     * 其它两个 tab 也分别在 listCollectedPosts/listLikedPosts 时填充
     * 关注/粉丝：复用 followApi 的 listFollowing/listFollowers
     */
    async loadStatBar() {
      if (!this.userStore.isLoggedIn) return
      try {
        const tasks = [
          communityApi
            .getMyPosts({ page: 1, size: 1 })
            .then((r) => Number(r?.total ?? 0))
            .catch(() => 0),
          followApi
            .listFollowing({ page: 1, size: 1 })
            .then((r) => Number(r?.total ?? (Array.isArray(r) ? r.length : 0)))
            .catch(() => 0),
          followApi
            .listFollowers({ page: 1, size: 1 })
            .then((r) => Number(r?.total ?? (Array.isArray(r) ? r.length : 0)))
            .catch(() => 0),
          communityApi
            .getLikedPosts({ page: 1, size: 1 })
            .then((r) => Number(r?.total ?? 0))
            .catch(() => 0),
        ]
        const [posts, following, followers, liked] = await Promise.all(tasks)
        this.statPosts = posts
        this.statFollowing = following
        this.statFollowers = followers
        // 获赞 = 当前用户所有帖子的 likes 之和
        // 单列表的 likes 不便累加,这里用 liked 总数作为兜底展示(后端暂无"我获赞总数"接口)
        this.statLikes = liked
      } catch (e) {
        console.warn('[profile] load stat bar failed', e)
      }
    },

    /**
     * 加载"他人 profile"：公开 profile 数据 + 该用户的帖子列表
     * - 头像/昵称/简介/关注粉丝数 来自 GET /api/v1/users/{id}/profile
     * - 帖子 tab 数据来自 GET /api/v1/community/posts?userId=xxx（已加 userId 过滤）
     * - liked/collected tab 是私有的,他人 profile 不可用 → 重置为空并 finished
     */
    async loadOtherProfile() {
      if (!this.viewedUserId) return
      // 防抖锁：onShow 频繁触发时只发一次,避免闪烁 + 重复请求
      if (this.otherProfileLoading) return
      this.otherProfileLoading = true
      try {
        const profile = await userApi.getUserProfile(this.viewedUserId)
        this.viewedProfile = profile || null
        // 把关注/粉丝/帖子数刷到 statBar
        this.statFollowing = Number(profile?.following ?? 0)
        this.statFollowers = Number(profile?.followers ?? 0)
        this.statPosts = Number(profile?.posts ?? 0)
        this.statLikes = 0 // 后端未提供"他人获赞总数"接口,留 0 占位
        // 简介：有就用，没有走默认文案
        if (profile?.bio) {
          this.bioText = profile.bio
        } else {
          this.bioText = i18n.t('userCenter.profileBioDefault')
        }
        // 首次进入时（帖子 tab 还是空）才加载帖子;onShow 重入不再重置列表避免闪烁
        // tabs 重置已在 onLoad 完成,这里只需要按需触发首次加载
        if (!this.tabsData.posts.list.length && !this.tabsData.posts.finished) {
          await this.loadActiveTab(true)
        }
      } catch (e) {
        console.warn('[profile] load other profile failed', e)
        uni.showToast({ title: this.t('userCenter.profileLoadFailed'), icon: 'none' })
      } finally {
        this.otherProfileLoading = false
      }
    },

    /**
     * 跳转帖子详情：复用现有 /pages/community/detail?id=xxx
     */
    goDetail(id) {
      if (!id) return
      uni.navigateTo({ url: `/pages/community/detail?id=${id}` })
    },

    /** 相对路径转绝对路径 */
    toAbs(url) {
      return toAbsoluteImageUrl(url)
    },

    /**
     * 网格列数：与社区详情页保持一致
     * - 1 张：单图
     * - 2~4 张：两列
     * - 5+ 张：三列九宫格
     */
    gridClassFor(n) {
      if (n <= 1) return 'grid-single'
      if (n <= 4) return 'grid-cols-2'
      return 'grid-cols-3'
    },

    /**
     * 点击九宫格中的图片:阻止冒泡到卡片跳转,弹出原生预览,
     * 复用的 URL 已是绝对地址,可直接交给 uni.previewImage
     */
    previewPostImages(images, idx) {
      uni.previewImage({ urls: images, current: images[idx] })
    },

    /**
     * 解析当前 bio 文本：自己 profile 取 store.userInfo.bio（已登录场景）,
     * 否则用默认 i18n 文案。统一走这一处避免多处重复逻辑。
     */
    resolveBioText() {
      // 自己 profile:store.userInfo.bio 由登录/更新接口回填,直接读
      const bio = this.userStore?.userInfo?.bio
      if (bio && String(bio).trim()) return String(bio)
      return i18n.t('userCenter.profileBioDefault')
    },

    /** 时间格式化为相对时间或日期 */
    formatTime(t) {
      if (!t) return ''
      const d = new Date(t)
      const now = Date.now()
      const diff = now - d.getTime()
      // 1 分钟内:刚刚
      if (diff < 60_000) return '刚刚'
      // 1 小时内:X 分钟前
      if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} 分钟前`
      // 24 小时内:X 小时前
      if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)} 小时前`
      // 其它:显示 yyyy-mm-dd
      const y = d.getFullYear()
      const m = String(d.getMonth() + 1).padStart(2, '0')
      const day = String(d.getDate()).padStart(2, '0')
      return `${y}-${m}-${day}`
    },

    /**
     * 点击 bio 触发编辑：
     * - 自己 profile：弹出 ActionSheet(编辑 / 清空)
     * - 他人 profile：不响应（CSS 也已禁用可点击样式）
     */
    onTapBio() {
      if (!this.isOwnProfile) return
      // 已有真实 bio 时,允许"清空"入口；否则只显示"编辑"
      const hasBio = !!(this.userStore.userInfo?.bio && this.userStore.userInfo.bio.trim())
      const itemList = [this.t('userCenter.profileBioEdit')]
      if (hasBio) itemList.push(this.t('userCenter.profileBioClear'))
      uni.showActionSheet({
        itemList,
        success: (res) => {
          // tapIndex 在取消场景下不会进入 success,正常回调里它总是 number
          const idx = Number(res?.tapIndex)
          if (!Number.isFinite(idx)) return
          if (idx === 0) {
            this.openBioEditor()
          } else if (idx === 1 && hasBio) {
            this.saveBio('')
          }
        },
        fail: () => {
          // 用户取消或系统弹层失败,不做任何处理（默认行为）
        },
      })
    },

    /**
     * 打开简介编辑弹窗：弹出自定义 IOS 风格弹层，
     * 用 v-model 双向绑定草稿，textarea 自带 auto-height，
     * 比原生 showModal 输入体验更好（更大的输入框 + 字数实时统计）。
     */
    openBioEditor() {
      const current = (this.userStore.userInfo?.bio || '').toString()
      // 草稿保留原值（不去 trim，避免用户编辑中途删除后再恢复）
      this.bioDraft = current
      this.showBioEditor = true
    },

    /** 关闭简介编辑弹窗 */
    closeBioEditor() {
      this.showBioEditor = false
    },

    /**
     * 输入事件：textarea 的 @input 在每次内容变化时触发，
     * v-model 已自动同步到 bioDraft，这里仅占位（保留扩展点，例如去抖保存草稿）。
     */
    onBioInput(e) {
      // v-model 已处理同步；此处保留以便后续扩展
      void e
    },

    /**
     * 确认编辑：取出草稿（trim），做长度校验，最后走 saveBio。
     * 与旧逻辑保持一致的后端契约：超长时提示失败，不发起请求。
     */
    onBioConfirm() {
      const value = (this.bioDraft || '').trim()
      if (value.length > this.bioMaxLength) {
        uni.showToast({ title: this.t('userCenter.profileBioSaveFailed'), icon: 'none' })
        return
      }
      this.closeBioEditor()
      this.saveBio(value)
    },

    /**
     * 保存 bio：调用 PUT /me，刷新本地 store 与页面显示。
     */
    async saveBio(value) {
      if (this.bioSaving) return
      this.bioSaving = true
      try {
        // 仅传一个字段,其它字段维持不变,与后端白名单 DTO 兼容
        const updated = await userApi.updateUser({ bio: value })
        // userApi.updateUser 返回结构与 GET /me 一致（UserController.toProfileMap）
        if (updated && typeof updated === 'object') {
          // 同步到 store:仅覆盖 bio,避免冲掉其它已变更字段
          this.userStore.userInfo = { ...(this.userStore.userInfo || {}), ...updated }
          // 持久化到本地存储:store 没有自动 watch,这里手动 setStorage,
          // 否则 APP 重启后 bio 会回退到上一次登录时的值
          setStorage(STORAGE_KEYS.USER_INFO, this.userStore.userInfo)
        }
        // 优先用后端 sanitize 后的 bio（与数据库一致），无值时回退到默认文案
        const finalBio = updated && typeof updated.bio === 'string' ? updated.bio : value
        this.bioText = finalBio || this.t('userCenter.profileBioDefault')
        uni.showToast({ title: this.t('userCenter.profileBioSaveSuccess'), icon: 'success' })
      } catch (e) {
        uni.showToast({ title: this.t('userCenter.profileBioSaveFailed'), icon: 'none' })
      } finally {
        this.bioSaving = false
      }
    },

    /**
     * 长按帖子：自己 profile 时弹出删除确认；他人 profile 或 liked/collected
     * tab 中的他人帖子(后端会 403)直接提示无法操作，避免误导用户。
     */
    onLongPressPost(post) {
      const postId = post?.id
      if (!postId) return
      if (!this.isOwnProfile) {
        // 他人 profile 列表理论上不会出现"删除"入口,但兜底防误触
        uni.showToast({
          title: this.t('community.actions.deleteFailed') || '无权操作',
          icon: 'none',
        })
        return
      }
      // liked / collected tab 中可能含他人帖子,userId != 自己时不能删
      // (后端 deletePost 仅作者本人可删,非作者会 403)。
      const myId = Number(this.userStore.userId)
      const ownerId = Number(post.userId)
      const canDelete = this.activeTab === 'posts' || (!Number.isNaN(ownerId) && ownerId === myId)
      if (!canDelete) {
        uni.showToast({
          title: this.t('community.actions.deleteFailed') || '该帖子无法删除',
          icon: 'none',
        })
        return
      }
      uni.showModal({
        title: this.t('community.actions.delete'),
        content: this.t('community.actions.deleteConfirm'),
        confirmText: this.t('community.actions.delete'),
        cancelText: this.t('community.actions.cancel'),
        confirmColor: '#ff4d4f',
        success: async (res) => {
          if (!res.confirm) return
          try {
            await communityApi.deletePost(postId)
            // 从当前激活 tab 列表中移除,数据条 -1
            const tab = this.tabsData[this.activeTab]
            tab.list = tab.list.filter((p) => p.id !== postId)
            if (this.statPosts > 0) this.statPosts -= 1
            uni.showToast({ title: this.t('community.actions.deleteSuccess'), icon: 'success' })
          } catch (e) {
            uni.showToast({
              title: this.t('community.actions.deleteFailed') || '删除失败',
              icon: 'none',
            })
          }
        },
      })
    },
  },
}
</script>

<style lang="scss" scoped>
.profile-page {
  min-height: 100vh;
  background: var(--color-background);
  padding-bottom: 64rpx;
}

/* 用户卡 */
.user-card {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 32rpx 24rpx;
  background: var(--color-surface);
}
.avatar {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  background: var(--color-background);
  flex-shrink: 0;
}
.user-meta {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  min-width: 0;
}
.nickname {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}
.bio {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
  line-height: 1.5;
}
/* bio 可点击：自己 profile 时给虚线下划线,提示可编辑 */
.bio--editable {
  text-decoration: underline dashed var(--color-divider);
  text-underline-offset: 6rpx;
}

/* 数据条 */
.stat-bar {
  display: flex;
  align-items: center;
  background: var(--color-surface);
  margin: 16rpx 24rpx;
  padding: 24rpx 0;
  border-radius: 24rpx;
  box-shadow: 0 4rpx 24rpx rgba(0, 0, 0, 0.06);
}
.stat-cell {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6rpx;
}
.stat-num {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-bold);
  color: var(--color-text);
}
.stat-label {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}
.stat-divider {
  width: 1rpx;
  height: 40rpx;
  background: var(--color-divider);
}

/* Tab 切换 */
.tab-bar {
  display: flex;
  background: var(--color-surface);
  margin: 0 24rpx 16rpx;
  border-radius: 24rpx 24rpx 0 0;
  border-bottom: 1rpx solid var(--color-divider);
  position: sticky;
  top: 0;
  z-index: 10;
}
.tab {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  padding: 24rpx 0;
  position: relative;
}
.tab-label {
  font-size: var(--font-size-base);
  color: var(--color-text-tertiary);
}
.tab--active .tab-label {
  color: var(--color-text);
  font-weight: var(--font-weight-semibold);
}
.tab-indicator {
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 56rpx;
  height: 4rpx;
  border-radius: 4rpx;
  background: var(--color-primary, #18b367);
}

/* 列表区 */
.list-wrap {
  margin: 0 24rpx;
}
.status-line {
  text-align: center;
  padding: 64rpx 0;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}
.empty {
  text-align: center;
  padding: 96rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
}
.empty-icon {
  font-size: 96rpx;
  color: var(--color-text-tertiary);
  opacity: 0.4;
}
.empty-text {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.post-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}
.post-item {
  background: var(--color-surface);
  border-radius: 24rpx;
  padding: 24rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.04);
}
.post-header {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 12rpx;
}
.post-avatar {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  flex-shrink: 0;
}
.post-user {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
  min-width: 0;
}
.post-username {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
}
.post-time {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.post-topic {
  display: inline-block;
  padding: 4rpx 16rpx;
  margin-bottom: 12rpx;
  background: rgba(24, 179, 103, 0.1);
  color: var(--color-primary, #18b367);
  font-size: var(--font-size-sm);
  border-radius: 999rpx;
  font-weight: 500;
}

.post-content {
  font-size: var(--font-size-base);
  color: var(--color-text);
  line-height: 1.6;
  /* 单列列表单卡片下文本截断 4 行,展开按钮后续可加 */
  display: -webkit-box;
  -webkit-line-clamp: 4;
  line-clamp: 4;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* 多图九宫格：与详情页共用一套列数规则 */
.post-image-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx;
  margin-top: 16rpx;
}
.post-image-cell {
  border-radius: 12rpx;
  overflow: hidden;
  background: var(--color-background);
}
.post-image-cell .post-image {
  width: 100%;
  height: 100%;
  display: block;
}
/* 三列九宫格：5~9 张及更多 */
.grid-cols-3 .post-image-cell {
  width: calc((100% - 16rpx) / 3);
  aspect-ratio: 1 / 1;
}
/* 两列：2~4 张 */
.grid-cols-2 .post-image-cell {
  width: calc((100% - 8rpx) / 2);
  aspect-ratio: 1 / 1;
}
/* 单图：占满宽度，高度交给 widthFix */
.grid-single .post-image-cell {
  width: 100%;
}
.grid-single .post-image-cell .post-image {
  height: auto;
}

.post-meta {
  display: flex;
  gap: 32rpx;
  margin-top: 16rpx;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}
.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 6rpx;
}
.meta-num {
  font-size: var(--font-size-sm);
}

.footer-line {
  text-align: center;
  padding: 32rpx 0;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

/* ===== IOS 风格简介编辑弹窗 ===== */
/* 遮罩：半透明黑色 + 背景模糊，模拟 iOS 弹层视觉 */
.bio-modal-mask {
  position: fixed;
  inset: 0;
  z-index: 999;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.45);
  backdrop-filter: blur(20rpx);
  -webkit-backdrop-filter: blur(20rpx);
  animation: bioModalMaskFade 0.2s ease-out;
}
@keyframes bioModalMaskFade {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

/* 卡片：圆角大、阴影柔和，仿 iOS Alert */
.bio-modal {
  width: 600rpx;
  background: var(--color-surface);
  border-radius: 28rpx;
  padding: 40rpx 32rpx 0;
  box-shadow: 0 20rpx 60rpx rgba(0, 0, 0, 0.18);
  overflow: hidden;
  animation: bioModalPop 0.22s cubic-bezier(0.32, 0.72, 0, 1);
}
@keyframes bioModalPop {
  from {
    transform: scale(0.92);
    opacity: 0;
  }
  to {
    transform: scale(1);
    opacity: 1;
  }
}

.bio-modal-title {
  display: block;
  font-size: 32rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  text-align: center;
  margin-bottom: 28rpx;
}

/* 输入框：高度明显增大，留足编辑空间 */
.bio-modal-textarea {
  display: block;
  width: 100%;
  min-height: 240rpx;
  /* iOS 风格：浅灰底 + 内边距，避免与卡片底色冲突 */
  padding: 24rpx;
  background: var(--color-background);
  border-radius: 16rpx;
  font-size: 28rpx;
  line-height: 1.6;
  color: var(--color-text);
  box-sizing: border-box;
}

/* 页脚：右下角字数统计 */
.bio-modal-footer {
  display: flex;
  justify-content: flex-end;
  padding: 12rpx 4rpx 0;
}
.bio-modal-counter {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}

/* 底部按钮：iOS 风格分隔线 + 主次按钮 */
.bio-modal-actions {
  display: flex;
  border-top: 1rpx solid var(--color-divider);
  margin: 28rpx -32rpx 0;
}
.bio-modal-btn {
  flex: 1;
  height: 96rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30rpx;
  color: var(--color-text);
  position: relative;
}
.bio-modal-btn:active {
  background: rgba(0, 0, 0, 0.05);
}
/* 左右按钮中间加竖向分隔线 */
.bio-modal-btn--cancel::after {
  content: '';
  position: absolute;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 1rpx;
  height: 56rpx;
  background: var(--color-divider);
}
/* 主按钮加粗 + 主题色 */
.bio-modal-btn--confirm text {
  font-weight: var(--font-weight-semibold);
  color: var(--color-primary, #18b367);
}
</style>
