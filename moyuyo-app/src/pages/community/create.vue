<template>
  <view class="create-post">
    <!-- 顶部导航:左侧返回 + 标题 + 右侧主操作按钮(圆角强调色,符合主流社交 APP 模式) -->
    <view class="navbar">
      <view class="navbar__back" @click="onBack">
        <text class="navbar__back-icon">‹</text>
      </view>
      <text class="navbar__title">发布帖子</text>
      <!-- 主操作按钮:未填内容时置灰但不消失,持续可见引导用户 -->
      <view class="navbar__publish" :class="{ 'is-disabled': !canPublish }" @click="onPublish">
        <text v-if="submitting" class="navbar__publish-loading">发布中</text>
        <text v-else>发布</text>
      </view>
    </view>

    <scroll-view scroll-y class="content">
      <!-- 内容输入:大尺寸文本框 + 字数计数(右下角) -->
      <view class="composer">
        <textarea
          v-model="content"
          class="composer__textarea"
          placeholder="说点什么吧…分享你和宠物的日常"
          :maxlength="2000"
          placeholder-class="composer__placeholder"
          :auto-height="true"
          @input="onContentInput"
        />
        <text class="composer__counter">{{ content.length }}/2000</text>
      </view>

      <!-- 图片九宫格:本地路径预览(后端无 user 端上传端点时占位) -->
      <view class="media-section">
        <view v-for="(img, idx) in images" :key="idx" class="media-thumb">
          <image :src="img" class="media-thumb__img" mode="aspectFill" />
          <view class="media-thumb__remove" @click="removeImage(idx)">×</view>
        </view>
        <view v-if="images.length < 9" class="media-add" @click="onPickImage">
          <text class="media-add__icon">+</text>
          <text class="media-add__text">{{ images.length || '' }} / 9</text>
        </view>
      </view>

      <!-- 设置项:Cell 列表统一风格 -->
      <view class="option-card">
        <view class="option-cell" @click="onPickTopic">
          <text class="option-cell__icon">🐾</text>
          <text class="option-cell__label">话题</text>
          <text class="option-cell__value" :class="{ 'is-placeholder': !topic }">
            {{ topic || '选择话题' }}
          </text>
          <text class="option-cell__arrow">›</text>
        </view>
        <view class="option-cell" @click="onPickLocation">
          <text class="option-cell__icon">📍</text>
          <text class="option-cell__label">位置</text>
          <text class="option-cell__value" :class="{ 'is-placeholder': !location }">
            {{ location || '不显示位置' }}
          </text>
          <text class="option-cell__arrow">›</text>
        </view>
        <view class="option-cell" @click="onPickVisibility">
          <text class="option-cell__icon">👁</text>
          <text class="option-cell__label">谁可以看</text>
          <text class="option-cell__value">{{ visibilityLabel }}</text>
          <text class="option-cell__arrow">›</text>
        </view>
      </view>
    </scroll-view>

    <!-- 底部工具栏:快捷输入入口 -->
    <view class="toolbar">
      <view class="toolbar__btn" @click="onInsertEmoji">
        <text class="toolbar__icon">😊</text>
      </view>
      <view class="toolbar__btn" @click="onMention">
        <text class="toolbar__icon">@</text>
      </view>
      <view class="toolbar__btn" @click="onInsertHash">
        <text class="toolbar__icon">#</text>
      </view>
      <view class="toolbar__btn" @click="onPickImage">
        <text class="toolbar__icon">🖼</text>
      </view>
    </view>

    <!-- 话题选择面板:从真实后端 /api/v1/community/topics 拉取 -->
    <view v-if="topicPickerVisible" class="sheet-mask" @click="closeTopicPicker">
      <view class="sheet" @click.stop>
        <view class="sheet__header">
          <text class="sheet__title">选择话题</text>
          <view class="sheet__close" @click="closeTopicPicker">×</view>
        </view>
        <view v-if="topicsLoading" class="sheet__loading">
          <text>加载中...</text>
        </view>
        <view v-else-if="!topics.length" class="sheet__empty">
          <text>暂无可用话题</text>
        </view>
        <scroll-view v-else scroll-y class="sheet__list">
          <view
            v-for="t in topics"
            :key="t.id"
            class="topic-item"
            :class="{ 'is-active': topic === t.name }"
            @click="selectTopic(t)"
          >
            <view class="topic-item__main">
              <text class="topic-item__name"># {{ t.name }}</text>
              <text v-if="t.description" class="topic-item__desc">{{ t.description }}</text>
            </view>
            <text class="topic-item__count">{{ formatCount(t.postCount) }} 帖</text>
          </view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script>
import { communityApi, uploadApi } from '@/api'
import { useUserStore } from '@/store'

export default {
  data() {
    return {
      // 表单数据
      content: '',
      // images: 本地预览路径,发布时逐张上传转 URL
      images: [],
      // uploadedImageUrls: 已上传的图片 URL,最终传给 createPost
      uploadedImageUrls: [],
      topic: '',
      location: '',
      visibility: 'public',
      submitting: false,
      uploadingImages: false,
      // 话题选择器
      topicPickerVisible: false,
      topics: [],
      topicsLoading: false,
    }
  },

  computed: {
    userStore() {
      return useUserStore()
    },
    /** 是否可发布:有内容或图片,且未提交中 */
    canPublish() {
      return !this.submitting && (!!this.content.trim() || this.images.length > 0)
    },
    /** 可见性文本映射 */
    visibilityLabel() {
      return { public: '公开', friends: '仅好友', private: '仅自己' }[this.visibility] || '公开'
    },
  },

  onLoad() {
    // 登录态校验:未登录直接拦截并跳登录页
    if (!this.userStore.isLoggedIn) {
      uni.showToast({ title: '请先登录', icon: 'none' })
      setTimeout(() => uni.reLaunch({ url: '/pages/user/login' }), 800)
      return
    }
    // 预加载话题列表,用户进入即可点选
    this.loadTopics()
  },

  methods: {
    onBack() {
      uni.navigateBack()
    },

    /** 文本输入:截断到 2000 字符,与后端 @Size(max=2000) 对齐 */
    onContentInput(e) {
      const val = e.detail.value || ''
      if (val.length > 2000) {
        this.content = val.slice(0, 2000)
      }
    },

    /** 加载话题列表(真实后端 /api/v1/community/topics) */
    async loadTopics() {
      this.topicsLoading = true
      try {
        const list = await communityApi.getCommunityTopics()
        this.topics = Array.isArray(list) ? list : []
      } catch (e) {
        // 接口失败不阻塞用户,允许不选话题直接发布
        this.topics = []
        console.warn('[create] loadTopics failed:', e.message)
      } finally {
        this.topicsLoading = false
      }
    },

    /** 选择图片(最多 9 张)。选完后立即上传,得到真实 URL。
     *  本地路径保留用于即时预览,uploadedImageUrls 用于最终提交。
     */
    async onPickImage() {
      const remain = 9 - this.images.length
      if (remain <= 0) {
        uni.showToast({ title: '最多 9 张图片', icon: 'none' })
        return
      }
      const res = await new Promise((resolve) => {
        uni.chooseImage({
          count: remain,
          success: (r) => resolve(r),
          fail: () => resolve(null),
        })
      })
      if (!res || !res.tempFilePaths || !res.tempFilePaths.length) return
      const localPaths = res.tempFilePaths.slice(0, remain)
      // 1. 先把本地路径塞进去,马上能预览
      this.images = this.images.concat(localPaths).slice(0, 9)
      // 2. 异步上传得到真实 URL
      this.uploadingImages = true
      uni.showLoading({ title: `上传中 0/${localPaths.length}...`, mask: true })
      let successCount = 0
      for (let i = 0; i < localPaths.length; i++) {
        try {
          const result = await uploadApi.uploadImage(localPaths[i])
          if (result?.url) {
            this.uploadedImageUrls.push(result.url)
            successCount++
          }
        } catch (e) {
          console.error('[create] upload failed:', e.message)
          uni.showToast({ title: `第 ${i + 1} 张上传失败`, icon: 'none' })
        }
        uni.showLoading({ title: `上传中 ${i + 1}/${localPaths.length}...`, mask: true })
      }
      uni.hideLoading()
      this.uploadingImages = false
      if (successCount > 0) {
        uni.showToast({ title: `已上传 ${successCount} 张`, icon: 'success' })
      }
    },

    /** 删除某张图片(本地预览 + 已上传 URL 同步移除) */
    removeImage(index) {
      this.images.splice(index, 1)
      // 已上传 URL 数组与本地预览数组下标保持一一对应
      if (index < this.uploadedImageUrls.length) {
        this.uploadedImageUrls.splice(index, 1)
      }
    },

    /** 打开话题选择器 */
    onPickTopic() {
      // 列表为空且未在加载,触发一次兜底请求
      if (!this.topics.length && !this.topicsLoading) {
        this.loadTopics()
      }
      this.topicPickerVisible = true
    },

    /** 关闭话题选择器 */
    closeTopicPicker() {
      this.topicPickerVisible = false
    },

    /** 选中话题 */
    selectTopic(t) {
      // 后端约束 topic ≤32 字符
      if (t.name && t.name.length > 32) {
        this.topic = t.name.slice(0, 32)
      } else {
        this.topic = t.name || ''
      }
      this.closeTopicPicker()
    },

    /** 选择位置(使用设备位置能力,后端未存储 location 字段,仅前端展示) */
    onPickLocation() {
      uni.authorize({
        scope: 'scope.userLocation',
        success: () => {
          uni.getLocation({
            type: 'wgs84',
            success: (res) => {
              this.location = `${res.latitude.toFixed(4)}, ${res.longitude.toFixed(4)}`
              uni.showToast({ title: '位置已填入', icon: 'success' })
            },
            fail: () => uni.showToast({ title: '获取位置失败', icon: 'none' }),
          })
        },
        fail: () => uni.showToast({ title: '请授予定位权限', icon: 'none' }),
      })
    },

    /** 选择可见性:当前后端未支持,仅前端占位 */
    onPickVisibility() {
      uni.showActionSheet({
        itemList: ['公开', '仅好友', '仅自己'],
        success: (res) => {
          const map = ['public', 'friends', 'private']
          this.visibility = map[res.tapIndex]
        },
      })
    },

    /** 工具栏:表情插入 */
    onInsertEmoji() {
      this.content = (this.content || '') + '😊'
    },

    /** 工具栏:@好友(占位) */
    onMention() {
      uni.showToast({ title: '@好友功能开发中', icon: 'none' })
    },

    /** 工具栏:#话题(占位,实际通过右侧 cell 选话题) */
    onInsertHash() {
      this.onPickTopic()
    },

    /** 数字格式化(1200 -> 1.2k) */
    formatCount(n) {
      if (n == null) return '0'
      if (n >= 10000) return `${(n / 10000).toFixed(1)}w`
      if (n >= 1000) return `${(n / 1000).toFixed(1)}k`
      return String(n)
    },

    /** 发布:调用真实后端 /api/v1/community/posts */
    async onPublish() {
      if (!this.userStore.isLoggedIn) {
        uni.showToast({ title: '请先登录', icon: 'none' })
        return
      }
      if (!this.canPublish) {
        uni.showToast({ title: '请输入内容或上传图片', icon: 'none' })
        return
      }
      if (this.submitting) return
      if (this.uploadingImages) {
        uni.showToast({ title: '图片正在上传,请稍候', icon: 'none' })
        return
      }

      // 防御性校验:与后端 DTO 约束保持一致
      const trimmed = (this.content || '').trim()
      if (!trimmed) {
        uni.showToast({ title: '帖子内容不能为空', icon: 'none' })
        return
      }
      if (trimmed.length > 2000) {
        uni.showToast({ title: '内容不能超过 2000 字', icon: 'none' })
        return
      }
      if (this.topic && this.topic.length > 32) {
        uni.showToast({ title: '话题不能超过 32 字符', icon: 'none' })
        return
      }
      if (this.images.length > 9) {
        uni.showToast({ title: '图片不能超过 9 张', icon: 'none' })
        return
      }

      // 真实图片 URL(已上传过的)。如果用户选了图但上传失败,这里不会包含失败的那张,
      // 这种情况下要求用户删除该图重新选择,避免给后端传本地路径
      const imageUrls = this.uploadedImageUrls
      if (this.images.length > 0 && imageUrls.length === 0) {
        uni.showToast({ title: '图片上传失败,请重新选择', icon: 'none' })
        return
      }

      this.submitting = true
      uni.showLoading({ title: '发布中...', mask: true })
      try {
        const result = await communityApi.createPost(trimmed, imageUrls, this.topic || null)
        uni.hideLoading()
        uni.showToast({ title: '发布成功', icon: 'success' })
        // 发布成功后通知上一页刷新列表
        const pages = getCurrentPages()
        if (pages.length >= 2) {
          const prevPage = pages[pages.length - 2]
          if (prevPage && typeof prevPage.$vm?.loadPosts === 'function') {
            prevPage.$vm.loadPosts()
          }
        }
        setTimeout(() => uni.navigateBack(), 800)
        return result
      } catch (e) {
        uni.hideLoading()
        console.error('[create] onPublish error:', e)
      } finally {
        this.submitting = false
      }
    },
  },
}
</script>

<style lang="scss" scoped>
/* ========== 整体容器 ========== */
.create-post {
  min-height: 100vh;
  width: 100%;
  box-sizing: border-box;
  background: var(--color-background, #f5f6f8);
  display: flex;
  flex-direction: column;
}

/* ========== 顶部导航 ========== */
.navbar {
  display: flex;
  align-items: center;
  height: 88rpx;
  padding: 0 24rpx;
  background: var(--color-surface, #ffffff);
  border-bottom: 1rpx solid var(--color-divider, #ececec);
  flex-shrink: 0;
}
.navbar__back {
  width: 60rpx;
  height: 60rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 8rpx;
}
.navbar__back-icon {
  font-size: 56rpx;
  color: var(--color-text-primary, #1a1a1a);
  font-weight: 300;
  line-height: 1;
}
.navbar__title {
  flex: 1;
  text-align: center;
  font-size: 34rpx;
  font-weight: 600;
  color: var(--color-text-primary, #1a1a1a);
}
.navbar__publish {
  min-width: 112rpx;
  height: 60rpx;
  padding: 0 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-primary, #18b367);
  border-radius: 30rpx;
  font-size: 28rpx;
  color: #ffffff;
  font-weight: 500;
  transition: opacity 0.2s ease;
}
.navbar__publish.is-disabled {
  opacity: 0.4;
}
.navbar__publish-loading {
  font-size: 24rpx;
}

/* ========== 内容滚动区 ========== */
.content {
  flex: 1;
  width: 100%;
  padding: 24rpx;
  box-sizing: border-box;
}

/* ========== 文本输入区 ========== */
.composer {
  background: var(--color-surface, #ffffff);
  border-radius: var(--radius-md, 16rpx);
  padding: 24rpx;
  margin-bottom: 24rpx;
  position: relative;
}
.composer__textarea {
  width: 100%;
  min-height: 200rpx;
  font-size: 32rpx;
  line-height: 1.6;
  color: var(--color-text-primary, #1a1a1a);
}
.composer__placeholder {
  color: var(--color-text-tertiary, #999);
}
.composer__counter {
  display: block;
  text-align: right;
  margin-top: 12rpx;
  font-size: 24rpx;
  color: var(--color-text-tertiary, #999);
}

/* ========== 图片九宫格 ========== */
.media-section {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-bottom: 24rpx;
}
.media-thumb {
  position: relative;
  width: 200rpx;
  height: 200rpx;
  border-radius: var(--radius-md, 16rpx);
  overflow: hidden;
  background: var(--color-surface, #ffffff);
}
.media-thumb__img {
  width: 100%;
  height: 100%;
}
.media-thumb__remove {
  position: absolute;
  top: 6rpx;
  right: 6rpx;
  width: 40rpx;
  height: 40rpx;
  line-height: 36rpx;
  text-align: center;
  background: rgba(0, 0, 0, 0.55);
  color: #ffffff;
  border-radius: 50%;
  font-size: 28rpx;
}
.media-add {
  width: 200rpx;
  height: 200rpx;
  border-radius: var(--radius-md, 16rpx);
  background: var(--color-surface, #ffffff);
  border: 2rpx dashed var(--color-divider, #d0d0d0);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--color-text-secondary, #666);
}
.media-add__icon {
  font-size: 60rpx;
  font-weight: 200;
  line-height: 1;
}
.media-add__text {
  margin-top: 8rpx;
  font-size: 22rpx;
}

/* ========== 设置项 Cell 卡 ========== */
.option-card {
  background: var(--color-surface, #ffffff);
  border-radius: var(--radius-md, 16rpx);
  overflow: hidden;
}
.option-cell {
  display: flex;
  align-items: center;
  height: 96rpx;
  padding: 0 24rpx;
  border-bottom: 1rpx solid var(--color-divider, #f0f0f0);
}
.option-cell:last-child {
  border-bottom: none;
}
.option-cell__icon {
  font-size: 36rpx;
  margin-right: 16rpx;
  width: 44rpx;
  text-align: center;
}
.option-cell__label {
  font-size: 30rpx;
  color: var(--color-text-primary, #1a1a1a);
  flex-shrink: 0;
}
.option-cell__value {
  flex: 1;
  text-align: right;
  font-size: 28rpx;
  color: var(--color-text-secondary, #666);
  margin-right: 12rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.option-cell__value.is-placeholder {
  color: var(--color-text-tertiary, #999);
}
.option-cell__arrow {
  font-size: 36rpx;
  color: var(--color-text-tertiary, #ccc);
  line-height: 1;
}

/* ========== 底部工具栏 ========== */
.toolbar {
  display: flex;
  align-items: center;
  height: 96rpx;
  padding: 0 16rpx;
  background: var(--color-surface, #ffffff);
  border-top: 1rpx solid var(--color-divider, #ececec);
  flex-shrink: 0;
}
.toolbar__btn {
  width: 88rpx;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.toolbar__icon {
  font-size: 40rpx;
}

/* ========== 话题选择底部弹层 ========== */
.sheet-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 999;
  display: flex;
  align-items: flex-end;
}
.sheet {
  width: 100%;
  max-height: 70vh;
  background: var(--color-surface, #ffffff);
  border-radius: 24rpx 24rpx 0 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.sheet__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx;
  border-bottom: 1rpx solid var(--color-divider, #ececec);
  flex-shrink: 0;
}
.sheet__title {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--color-text-primary, #1a1a1a);
}
.sheet__close {
  width: 48rpx;
  height: 48rpx;
  line-height: 44rpx;
  text-align: center;
  font-size: 40rpx;
  color: var(--color-text-secondary, #666);
}
.sheet__loading,
.sheet__empty {
  padding: 80rpx 24rpx;
  text-align: center;
  font-size: 28rpx;
  color: var(--color-text-tertiary, #999);
}
.sheet__list {
  flex: 1;
  width: 100%;
}
.topic-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx;
  border-bottom: 1rpx solid var(--color-divider, #f0f0f0);
}
.topic-item.is-active {
  background: var(--color-background, #f5f6f8);
}
.topic-item__main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.topic-item__name {
  font-size: 30rpx;
  color: var(--color-primary, #18b367);
  font-weight: 500;
}
.topic-item__desc {
  margin-top: 6rpx;
  font-size: 24rpx;
  color: var(--color-text-tertiary, #999);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.topic-item__count {
  flex-shrink: 0;
  font-size: 24rpx;
  color: var(--color-text-tertiary, #999);
  margin-left: 16rpx;
}
</style>
