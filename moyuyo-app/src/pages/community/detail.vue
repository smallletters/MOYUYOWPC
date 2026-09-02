<template>
  <view class="post-detail">
    <view v-if="!post" class="loading">Loading...</view>
    <template v-else>
      <view class="post-card">
        <view class="post-header">
          <image :src="post.avatar || defaultAvatar" class="avatar" />
          <view class="user-info" @tap="onTapAuthor">
            <text class="username">{{ post.username || 'Pet Lover' }}</text>
            <text class="time">{{ formatTime(post.createTime) }}</text>
          </view>
          <!-- 关注作者按钮:仅作者 != 登录用户时显示,匿名/自己时不显示 -->
          <view
            v-if="canShowFollowBtn"
            class="follow-btn"
            :class="{ 'follow-btn--on': isFollowingAuthor }"
            @tap.stop="onToggleFollow"
          >
            {{ isFollowingAuthor ? t('community.postActions.followDone') : t('community.postActions.followAdd') }}
          </view>
        </view>
        <!-- 帖子正文:把 #话题 和 @用户 解析为可点击的富文本段 -->
        <view class="content">
          <template v-for="(seg, i) in contentSegments" :key="i">
            <text v-if="seg.type === 'text'" class="content__text">{{ seg.text }}</text>
            <text
              v-else-if="seg.type === 'topic'"
              class="content__topic"
              @tap="onTapTopic(seg.text)"
            >{{ seg.text }}</text>
            <text
              v-else-if="seg.type === 'mention'"
              class="content__mention"
              @tap="onTapMention(seg.text)"
            >{{ seg.text }}</text>
          </template>
        </view>
        <!-- 单独的话题 tag(由发布时选择,展示为可点击徽章) -->
        <view v-if="post.topic" class="post-topic" @tap="onTapTopic('#' + post.topic)">
          # {{ post.topic }}
        </view>
        <!-- 多图渲染：按 3 列九宫格展示；超过 9 张仍能滚动看（容器 wrap 即可） -->
        <view v-if="post.images && post.images.length" class="image-grid" :class="gridClass">
          <view
            v-for="(img, idx) in post.images"
            :key="idx"
            class="image-cell"
            @tap="previewImages(idx)"
          >
            <image :src="img" class="post-image" mode="aspectFill" />
          </view>
        </view>
        <view class="stats">
          <!-- 点赞按钮：liked=true 时高亮 + 心形变红；点击触发 onToggleLike -->
          <view class="stat-item" :class="{ liked: post.liked }" @tap="onToggleLike">
            <text class="luc luc-heart" />
            <text class="stat-num">{{ post.likes || 0 }}</text>
          </view>
          <!-- 收藏按钮:collected=true 时高亮 + 书签填充 -->
          <view class="stat-item" :class="{ collected: post.collected }" @tap="onToggleCollect">
            <text class="luc luc-bookmark" />
            <text class="stat-num">{{ t('community.postActions.favorite') }}</text>
          </view>
          <view class="stat-item static">
            <text class="luc luc-message-circle" />
            <text class="stat-num">{{ post.comments || 0 }}</text>
          </view>
        </view>
      </view>

      <view class="comments-section">
        <text class="section-title">Comments ({{ (post.commentList || []).length }})</text>
        <view v-if="!(post.commentList && post.commentList.length)" class="no-comments">
          No comments yet
        </view>
        <view v-for="c in post.commentList || []" :key="c.id" class="comment-item">
          <text class="comment-user">{{ c.username }}</text>
          <text class="comment-content">{{ c.content }}</text>
          <text class="comment-time">{{ formatTime(c.createTime) }}</text>
        </view>
      </view>
    </template>

    <view class="comment-bar safe-area-bottom">
      <input
        v-model="commentText"
        class="comment-input"
        placeholder="Write a comment..."
        confirm-type="send"
        @confirm="onSendComment"
      >
      <view
        class="btn btn-primary send-btn"
        :class="{ disabled: !commentText }"
        @click="onSendComment"
      >
        Send
      </view>
    </view>
  </view>
</template>

<script>
import { communityApi } from '@/api'
import followApi from '@/api/follow'
import { useUserStore } from '@/store'
import { i18n } from '@/i18n'
import { applyPageTitle, resolvePageTitle } from '@/utils/i18nPageMixin'

export default {
  // 页面级 i18n 标题:navbar 标题文案来源
  pageTitleKey: 'pageTitle.communityDetail',

  data() {
    return {
      postId: null,
      post: null,
      commentText: '',
      defaultAvatar: 'https://i.pravatar.cc/100?img=1',
      // 点赞请求中标记：避免用户连续点击产生重复请求
      liking: false,
      // 收藏请求中标记
      collecting: false,
      // 关注请求中标记
      followingLoading: false,
      // 是否已关注作者(默认 false;登录后由 detail 接口或独立查询填充)
      isFollowingAuthor: false,
      // locale 切换时自增,用于让模板里的 $t()/t() 重新求值
      // (i18n.t 内部读 _localeRef.value,但通过函数间接读取 Vue 不会自动追踪)
      localeVersion: 0,
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
        // 显式读取以建立 Vue 响应式追踪
        void this.localeVersion
        return i18n.t(key)
      }
    },
    /**
     * 是否展示"关注作者"按钮:
     * - 未登录:不显示(直接去登录页即可)
     * - 作者 == 自己:不显示(自己关注自己没意义)
     * - 其余:显示
     */
    canShowFollowBtn() {
      if (!this.post || !this.userStore.isLoggedIn) return false
      const me = this.userStore.userInfo && this.userStore.userInfo.id
      return !!me && String(this.post.userId) !== String(me)
    },
    // 图片数量决定网格列数与排布：
    // - 1 张：单图大图模式
    // - 2~4 张：两列
    // - 5~9 张：三列九宫格
    gridClass() {
      const n = (this.post?.images || []).length
      if (n <= 1) return 'grid-single'
      if (n <= 4) return 'grid-cols-2'
      return 'grid-cols-3'
    },
    /**
     * 把 post.content 解析为分段数组,识别 #话题 和 @用户 标记。
     * 规则:
     *   - # 必须是段首或前面是空白/换行,后跟 1~30 个非空白字符
     *   - @ 必须是段首或前面是空白/换行,后跟 1~20 个非空白字符
     * 返回: [{ type: 'text'|'topic'|'mention', text: string }]
     */
    contentSegments() {
      const content = this.post?.content || ''
      if (!content) return []
      const re = /(^|[\s\n])(#[\u4e00-\u9fa5\w]{1,30}|@[^\s@]{1,20})/g
      const segs = []
      let lastIdx = 0
      let m
      while ((m = re.exec(content)) !== null) {
        const leadStart = m.index
        const leadLen = m[1].length
        const token = m[2]
        // 推进到 token 开始位置(包含前导空白)
        const tokenStart = leadStart + leadLen
        // 把前一段(到 leadStart)作为文本
        if (tokenStart > lastIdx) {
          segs.push({ type: 'text', text: content.slice(lastIdx, tokenStart) })
        }
        if (token.startsWith('#')) {
          segs.push({ type: 'topic', text: token })
        } else if (token.startsWith('@')) {
          segs.push({ type: 'mention', text: token })
        }
        lastIdx = tokenStart + token.length
      }
      if (lastIdx < content.length) {
        segs.push({ type: 'text', text: content.slice(lastIdx) })
      }
      return segs
    },
  },

  onLoad(query) {
    this.postId = query.id
    // 立即按当前 locale 设置 navbar 标题(覆盖 pages.json 字面量)
    applyPageTitle(resolvePageTitle(this.pageTitleKey, this.pageTitleFallback))
    // 订阅 locale 变化:同时驱动模板里的 i18n 文案和 navbar 标题
    this._unsubLocale = i18n.subscribe(() => {
      this.localeVersion += 1
      applyPageTitle(resolvePageTitle(this.pageTitleKey, this.pageTitleFallback))
    })
    this.loadDetail()
  },

  onUnload() {
    if (this._unsubLocale) this._unsubLocale()
  },

  methods: {
    async loadDetail() {
      try {
        const data = await communityApi.getPostDetail(this.postId)
        // 后端 VO 用 List<String> 存图片 URL；缺字段兜底为空数组，保证模板渲染安全
        this.post = {
          ...data,
          images: Array.isArray(data?.images) ? data.images : [],
        }
        // 登录态下补查关注状态(后端没在 detail 里直接返回 isFollowingAuthor)
        this.loadFollowStatus()
      } catch (e) {
        uni.showToast({ title: this.t('community.postActions.loadFailed'), icon: 'none' })
      }
    },

    /** 加载"是否已关注作者"状态 */
    async loadFollowStatus() {
      if (!this.post || !this.userStore.isLoggedIn) return
      const me = this.userStore.userInfo && this.userStore.userInfo.id
      if (!me || String(this.post.userId) === String(me)) return
      try {
        const r = await followApi.followStatus(this.post.userId)
        // 接口返回 {following: boolean},降级兜底直接当 false
        this.isFollowingAuthor = !!(r && r.following)
      } catch (e) {
        // 静默失败,按钮仍可点,错误在 onToggleFollow 里提示
        this.isFollowingAuthor = false
      }
    },

    /**
     * 点赞 / 取消点赞切换。
     * - 先乐观更新 UI（liked 取反 + likes ±1），再异步调接口；失败时回滚并提示
     * - liking 标记防止用户连点
     */
    async onToggleLike() {
      if (!this.post || this.liking) return
      // 未登录:弹窗引导去登录(与社区页一致)
      if (!this.userStore.isLoggedIn) {
        uni.showModal({
          title: this.t('community.postActions.loginRequiredTitleLike'),
          content: this.t('community.postActions.loginPrompt'),
          confirmText: this.t('community.postActions.goLogin'),
          cancelText: this.t('community.postActions.browseMore'),
          success: (res) => {
            if (res.confirm) uni.reLaunch({ url: '/pages/user/login' })
          },
        })
        return
      }
      const before = {
        liked: !!this.post.liked,
        likes: Number(this.post.likes) || 0,
      }
      const next = {
        liked: !before.liked,
        likes: before.likes + (before.liked ? -1 : 1),
      }
      // 乐观更新
      this.post.liked = next.liked
      this.post.likes = Math.max(0, next.likes)
      this.liking = true
      try {
        if (next.liked) {
          await communityApi.likePost(this.postId)
        } else {
          await communityApi.unlikePost(this.postId)
        }
      } catch (e) {
        // 失败回滚
        this.post.liked = before.liked
        this.post.likes = before.likes
        uni.showToast({ title: this.t('community.postActions.likeFailed'), icon: 'none' })
      } finally {
        this.liking = false
      }
    },

    /**
     * 收藏 / 取消收藏切换。
     * - 乐观更新 collected 字段,失败回滚
     * - 注意:后端 mo_community_collect 没有 +collectCount 字段(仅记录"谁收藏了"),
     *   所以收藏数只能在"我的收藏"列表里取,详情页只关心 collected 状态
     */
    async onToggleCollect() {
      if (!this.post || this.collecting) return
      if (!this.userStore.isLoggedIn) {
        uni.showModal({
          title: this.t('community.postActions.loginRequiredTitleFavorite'),
          content: this.t('community.postActions.loginPrompt'),
          confirmText: this.t('community.postActions.goLogin'),
          cancelText: this.t('community.postActions.browseMore'),
          success: (res) => {
            if (res.confirm) uni.reLaunch({ url: '/pages/user/login' })
          },
        })
        return
      }
      const before = !!this.post.collected
      const next = !before
      // 乐观更新
      this.post.collected = next
      this.collecting = true
      try {
        if (next) {
          await communityApi.collectPost(this.postId)
        } else {
          await communityApi.uncollectPost(this.postId)
        }
        uni.showToast({
          title: this.t(next ? 'community.postActions.collected' : 'community.postActions.uncollected'),
          icon: 'success',
        })
      } catch (e) {
        this.post.collected = before
        uni.showToast({ title: this.t('community.postActions.collectFailed'), icon: 'none' })
      } finally {
        this.collecting = false
      }
    },

    /**
     * 关注 / 取消关注作者。
     * - 乐观更新 isFollowingAuthor,失败回滚
     * - 作者 == 自己的情况在 canShowFollowBtn 已拦截
     */
    async onToggleFollow() {
      if (!this.post || this.followingLoading) return
      if (!this.userStore.isLoggedIn) {
        uni.showModal({
          title: this.t('community.postActions.loginRequiredTitleFollow'),
          content: this.t('community.postActions.loginPrompt'),
          confirmText: this.t('community.postActions.goLogin'),
          cancelText: this.t('community.postActions.browseMore'),
          success: (res) => {
            if (res.confirm) uni.reLaunch({ url: '/pages/user/login' })
          },
        })
        return
      }
      const before = this.isFollowingAuthor
      const next = !before
      this.isFollowingAuthor = next
      this.followingLoading = true
      try {
        if (next) {
          await followApi.follow(this.post.userId)
          uni.showToast({ title: this.t('community.postActions.followDone'), icon: 'success' })
        } else {
          await followApi.unfollow(this.post.userId)
          uni.showToast({ title: this.t('community.postActions.unfollowDone'), icon: 'none' })
        }
      } catch (e) {
        this.isFollowingAuthor = before
        uni.showToast({ title: this.t('community.postActions.followFailed'), icon: 'none' })
      } finally {
        this.followingLoading = false
      }
    },

    /**
     * 预览图片：使用 uni.previewImage 全屏查看当前帖子的全部图片，
     * 点击的索引 idx 作为 current，从该图开始展示。
     */
    previewImages(idx) {
      const urls = this.post?.images || []
      if (!urls.length) return
      uni.previewImage({ current: urls[idx], urls })
    },

    async onSendComment() {
      if (!this.commentText) return
      try {
        await communityApi.addComment(this.postId, this.commentText)
        this.commentText = ''
        uni.showToast({ title: this.t('community.postActions.commentPosted'), icon: 'success' })
        this.loadDetail()
      } catch (e) {
        uni.showToast({ title: this.t('community.postActions.commentFailed'), icon: 'none' })
      }
    },

    formatTime(time) {
      if (!time) return ''
      return new Date(time).toLocaleString()
    },

    /**
     * 点击作者区域(头像/昵称):进入作者主页
     * 当前后端没有"按 id 查用户"接口,所以暂跳到社区搜索页用昵称搜(用户 Tab)
     * 后续有了 /users/{id} 详情接口再换成 user-profile-page
     */
    onTapAuthor() {
      if (!this.post) return
      const name = this.post.username || ''
      if (!name) return
      uni.navigateTo({
        url: `/pages/community/search?keyword=${encodeURIComponent(name)}`,
      })
    },

    /**
     * 点击 #话题 段:跳转搜索页并以话题为关键词搜索
     * (统一走搜索页的好处:复用前端列表 / 分页 / 多 tab 视图,避免再建话题详情页)
     */
    onTapTopic(token) {
      // token 形如 "#宠物日常",剥掉 # 作为关键词
      const name = (token || '').replace(/^#/, '').trim()
      if (!name) return
      uni.navigateTo({
        url: `/pages/community/search?keyword=${encodeURIComponent(name)}`,
      })
    },

    /**
     * 点击 @用户 段:目前后端未提供根据昵称查 userId 的轻量接口,
     * 先跳到搜索页让用户在"用户"Tab 下找到该用户(主流 APP 也是这样兜底)
     */
    onTapMention(token) {
      const name = (token || '').replace(/^@/, '').trim()
      if (!name) return
      uni.navigateTo({
        url: `/pages/community/search?keyword=${encodeURIComponent(name)}`,
      })
    },
  },
}
</script>

<style lang="scss" scoped>
.post-detail {
  min-height: 100vh;
  background: var(--color-background);
  padding-bottom: 120rpx;
}
.loading {
  text-align: center;
  padding: 64rpx;
  color: var(--color-text-tertiary);
}
.post-card {
  background: var(--color-surface);
  padding: 24rpx;
  margin-bottom: 16rpx;
}
.post-header {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 16rpx;
}
.avatar {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  flex-shrink: 0;
}
.user-info {
  flex: 1;
  min-width: 0;
}
.username {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
  display: block;
}
.time {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}
/* 关注按钮:头部右侧,椭圆形小按钮 */
.follow-btn {
  flex-shrink: 0;
  padding: 8rpx 20rpx;
  font-size: 24rpx;
  border-radius: 999rpx;
  background: var(--color-primary, #18b367);
  color: #ffffff;
  border: 1rpx solid var(--color-primary, #18b367);
  transition: all 0.2s ease;
}
.follow-btn:active {
  opacity: 0.7;
}
.follow-btn--on {
  background: transparent;
  color: var(--color-primary, #18b367);
}
.content {
  font-size: var(--font-size-base);
  line-height: 1.6;
  margin-bottom: 16rpx;
}
/* 帖子正文中普通文本段(无样式,只是包一层方便定位) */
.content__text {
  color: var(--color-text-primary);
}
/* #话题 段:主色 + 下划线,可点击 */
.content__topic {
  color: var(--color-primary, #18b367);
  font-weight: 500;
}
/* @用户 段:蓝紫色 + 下划线,可点击 */
.content__mention {
  color: #5b6cff;
  font-weight: 500;
}
/* 单独的话题 tag 徽章 */
.post-topic {
  display: inline-block;
  padding: 4rpx 16rpx;
  margin-bottom: 16rpx;
  background: rgba(24, 179, 103, 0.1);
  color: var(--color-primary, #18b367);
  font-size: var(--font-size-sm);
  border-radius: 999rpx;
  font-weight: 500;
}
.post-topic:active {
  opacity: 0.7;
}
.post-image {
  width: 100%;
  border-radius: var(--radius-sm);
  margin-bottom: 16rpx;
}

/* 多图九宫格：根据图片数量自动切换列数；单图占满宽度 */
.image-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx;
  margin-bottom: 16rpx;
}

.image-cell {
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: var(--color-background);
}

/* 三列（5~9 张）：每格约为容器宽度 1/3 减间距 */
.grid-cols-3 .image-cell {
  width: calc((100% - 16rpx) / 3);
  aspect-ratio: 1 / 1;
}

/* 两列（2~4 张）：每格约为容器宽度 1/2 减间距 */
.grid-cols-2 .image-cell {
  width: calc((100% - 8rpx) / 2);
  aspect-ratio: 1 / 1;
}

/* 单图：占满宽度，按原图比例展示 */
.grid-single .image-cell {
  width: 100%;
}

.image-cell .post-image {
  width: 100%;
  height: 100%;
  margin-bottom: 0;
  display: block;
}

.stats {
  display: flex;
  gap: 24rpx;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  align-items: center;
}

/* stat-item 兼容旧版 <text> 文本节点：保留 inline-flex 让心形+数字水平排列 */
.stat-item {
  display: inline-flex;
  align-items: center;
  gap: 6rpx;
  padding: 4rpx 0;
}

/* 点赞高亮态：心形变红，加粗，让用户明确感知已点赞 */
.stat-item.liked {
  color: #ff4d4f;
}

/* 收藏高亮态:书签变金色,加粗 */
.stat-item.collected {
  color: #fa8c16;
  font-weight: 600;
}

.stat-item.static {
  cursor: default;
}

.stat-num {
  font-size: var(--font-size-sm);
}
.comments-section {
  padding: 0 24rpx;
}
.section-title {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  margin-bottom: 16rpx;
  display: block;
}
.no-comments {
  text-align: center;
  padding: 32rpx;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}
.comment-item {
  padding: 16rpx 0;
  border-bottom: 1rpx solid var(--color-divider);
}
.comment-user {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  display: block;
  margin-bottom: 4rpx;
}
.comment-content {
  font-size: var(--font-size-base);
  display: block;
  margin-bottom: 4rpx;
}
.comment-time {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}
.comment-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  gap: 16rpx;
  padding: 16rpx 24rpx;
  background: var(--color-surface);
  border-top: 1rpx solid var(--color-divider);
}
.comment-input {
  flex: 1;
  padding: 16rpx 20rpx;
  background: var(--color-background);
  border-radius: var(--radius-pill);
  font-size: var(--font-size-sm);
}
.send-btn {
  padding: 16rpx 32rpx;
  font-size: var(--font-size-sm);
  flex-shrink: 0;
}
.send-btn.disabled {
  opacity: 0.5;
}
</style>
