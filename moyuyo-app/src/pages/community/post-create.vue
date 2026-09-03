<template>
  <view class="post-create">
    <!-- 顶部导航(u-navbar 自带回退按钮,统一标题栏样式) -->
    <u-navbar
      title="发布"
      :auto-back="true"
      :background="themeColorBg"
      :border-bottom="true">
      <template #right>
        <view class="nav-right-slot">
          <!-- 使用 uview-plus 标准按钮:type="primary" 自动应用品牌主题色,
            plain 镂空样式与 navbar 协调;loading 内置显示 -->
          <u-button
            type="primary"
            plain
            size="small"
            shape="circle"
            :disabled="!canPublish"
            :loading="submitting"
            @click="onPublish"
          >
            发布
          </u-button>
        </view>
      </template>
    </u-navbar>

    <scroll-view scroll-y class="content">
      <!-- 内容编辑区:用 u-textarea 统一风格 -->
      <u--textarea
        v-model="content"
        class="content-input"
        placeholder="说点什么吧..."
        :maxlength="500"
        :count="true"
        :auto-height="true"
        :height="240"
        border="none"
      />

      <!-- 上传图片:用 u-upload 替换手写上传 UI -->
      <view class="upload-section">
        <u-upload
          :file-list="imageFileList"
          :max-count="9"
          :max-size="10 * 1024 * 1024"
          :deletable="true"
          accept="image"
          multiple
          :auto-upload="false"
          :capture="['album']"
          @after-read="onAfterRead"
          @delete="onDeleteImage"
        />
      </view>

      <!-- 发布选项:用 u-cell-group + u-cell 标准 cell 风格(value-style 由组件库默认控制) -->
      <u-cell-group :border="false" class="option-group">
        <u-cell
          title="话题"
          :value="topic || '选择话题'"
          is-link
          @click="onPickTopic" />
        <u-cell
          title="位置"
          :value="location || '不显示位置'"
          is-link
          @click="onPickLocation" />
        <u-cell
          title="谁可以看"
          value="公开"
          is-link
          @click="onPickVisibility" />
      </u-cell-group>
    </scroll-view>
  </view>
</template>

<script>
import { communityApi } from '@/api'
import { useUserStore } from '@/store'

export default {
  data() {
    return {
      content: '',
      // u-upload 需要 {url, ...} 结构
      imageFileList: [],
      topic: '',
      location: '',
      visibility: 'public',
      submitting: false,
    }
  },

  computed: {
    userStore() {
      return useUserStore()
    },
    /** 是否可发布:有内容或图片,且未提交中 */
    canPublish() {
      return !this.submitting && (!!this.content.trim() || this.imageFileList.length > 0)
    },
    /** 兼容旧字段 */
    images() {
      return this.imageFileList.map((f) => f.url || f.thumb)
    },
  },

  onLoad(query) {
    // 支持从外部传入预选话题
    if (query && query.topic) {
      this.topic = decodeURIComponent(query.topic)
    }
  },

  methods: {
    goBack() {
      uni.navigateBack()
    },

    /**
     * u-upload 读取图片后的回调:
     * - 当 auto-upload=false 时,after-read 触发,文件对象已 push 到 file-list
     * - 兼容 uni.chooseImage 返回的 {path, ...} 结构
     */
    onAfterRead(event) {
      // event.file 可能为单个或数组(多选模式)
      const list = Array.isArray(event.file) ? event.file : [event.file]
      list.forEach((file) => {
        // 仅展示本地路径,不实际上传
        this.imageFileList.push({
          url: file.url || file.path || file.thumb,
          status: 'success',
          message: '',
        })
      })
    },

    /** 删除某张图片(u-upload @delete) */
    onDeleteImage(event) {
      const index = event.index
      if (index >= 0 && index < this.imageFileList.length) {
        this.imageFileList.splice(index, 1)
      }
    },

    /** 选择话题(占位,实际项目中应跳转到话题选择页) */
    onPickTopic() {
      uni.showToast({ title: '话题选择功能开发中', icon: 'none' })
    },

    /** 选择位置(预留位置权限流程) */
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

    /** 选择可见性(预留) */
    onPickVisibility() {
      uni.showActionSheet({
        itemList: ['公开', '仅好友', '仅自己'],
        success: (res) => {
          const map = ['public', 'friends', 'private']
          this.visibility = map[res.tapIndex]
          uni.showToast({
            title: ['公开', '仅好友', '仅自己'][res.tapIndex],
            icon: 'none',
          })
        },
      })
    },

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
      this.submitting = true
      uni.showLoading({ title: '发布中...' })
      try {
        // 后端接口 /api/v1/community/posts(POST)
        await communityApi.createPost(this.content, this.images, this.topic || null)
        uni.hideLoading()
        uni.showToast({ title: '发布成功', icon: 'success' })
        setTimeout(() => uni.navigateBack(), 800)
      } catch (e) {
        uni.hideLoading()
        uni.showToast({ title: e.message || '发布失败', icon: 'none' })
      } finally {
        this.submitting = false
      }
    },
  },
}
</script>

<style lang="scss" scoped>
.post-create {
  min-height: 100vh;
  width: 100%;
  box-sizing: border-box;
  background: var(--color-background);
}

.nav-right-slot {
  display: flex;
  align-items: center;
  height: 88rpx;
  padding-right: 8rpx;
}

.content {
  width: 100%;
  padding: 24rpx;
  box-sizing: border-box;
}

/* 内容编辑区:去掉默认边框,适配设计稿 */
.content-input {
  width: 100%;
  background: transparent;
}

.content-input ::v-deep .u-textarea {
  background: transparent !important;
}

/* 上传图片区与输入区分隔 */
.upload-section {
  margin: 24rpx 0;
}

/* 选项 cell 组:加圆角,与设计稿一致 */
.option-group {
  border-radius: var(--radius-md);
  overflow: hidden;
}

.option-group ::v-deep .u-cell {
  font-size: 28rpx;
}
</style>
