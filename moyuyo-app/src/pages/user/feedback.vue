<template>
  <view class="feedback">
    <!-- 顶部导航栏 -->
    <view class="header">
      <view class="back-btn" @click="goBack">
        <text class="back-icon luc-arrow-left" />
      </view>
      <text class="header-title">{{ $t('feedback.title') }}</text>
      <view class="header-placeholder" />
    </view>

    <scroll-view class="content" scroll-y>
      <!-- 反馈类型选择 -->
      <view class="section">
        <text class="section-title">{{ $t('feedback.typeLabel') }}</text>
        <view class="type-list">
          <view
            v-for="item in typeOptions"
            :key="item.value"
            class="type-item"
            :class="{ active: feedbackType === item.value }"
            @click="feedbackType = item.value"
          >
            <text class="type-text">{{ $t(item.labelKey) }}</text>
          </view>
        </view>
      </view>

      <!-- 问题描述 -->
      <view class="section">
        <text class="section-title">{{ $t('feedback.descLabel') }}</text>
        <view class="textarea-wrapper">
          <textarea
            v-model="description"
            class="textarea"
            :placeholder="$t('feedback.descPlaceholder')"
            maxlength="500"
            :show-count="false"
          />
          <view class="textarea-counter">
            <text class="counter-text">{{ description.length }}/500</text>
          </view>
        </view>
      </view>

      <!-- 上传截图 -->
      <view class="section">
        <text class="section-title">{{ $t('feedback.uploadHint') }}</text>
        <view class="upload-list">
          <view v-for="(img, index) in uploadedImages" :key="index" class="upload-item">
            <image :src="$img(img)" class="upload-preview" mode="aspectFill" />
            <view class="upload-remove" @click="removeImage(index)">
              <text class="remove-icon luc-x" />
            </view>
          </view>
          <view v-if="uploadedImages.length < 3" class="upload-btn" @click="onUploadImage">
            <text class="upload-btn-icon">+</text>
            <text class="upload-btn-label">{{ $t('feedback.addImage') }}</text>
          </view>
        </view>
      </view>

      <!-- 联系方式 -->
      <view class="section">
        <text class="section-title">{{ $t('feedback.contactLabel') }}</text>
        <view class="input-wrapper">
          <input
            v-model="contact"
            class="input"
            type="text"
            :placeholder="$t('feedback.contactPlaceholder')"
          >
        </view>
      </view>

      <!-- 提交按钮 -->
      <view class="submit-area">
        <view class="submit-btn" @click="onSubmit">
          <text class="submit-btn-text">{{ $t('feedback.submit') }}</text>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import { feedbackApi } from '@/api'
import { i18n } from '@/i18n'

export default {
  pageTitleKey: 'pageTitle.userFeedback',

  data() {
    return {
      feedbackType: '',
      description: '',
      contact: '',
      uploadedImages: [],
      typeOptions: [
        { labelKey: 'feedback.types.feature', value: 'feature' },
        { labelKey: 'feedback.types.ux', value: 'ux' },
        { labelKey: 'feedback.types.content', value: 'content' },
        { labelKey: 'feedback.types.other', value: 'other' },
      ],
      submitting: false,
    }
  },

  methods: {
    goBack() {
      uni.navigateBack()
    },

    onUploadImage() {
      // 反馈图从相册选;显式 ['album'] 绕过 Android 上 uni-app sourceType
      // 弹层文案渲染 bug(显示成 "uni.chooseImage.sourceType.xxx")。
      uni.chooseImage({
        count: 3 - this.uploadedImages.length,
        sizeType: ['compressed'],
        sourceType: ['album'],
        success: (res) => {
          const tempPaths = res.tempFilePaths || []
          this.uploadedImages = [...this.uploadedImages, ...tempPaths].slice(0, 3)
        },
      })
    },

    removeImage(index) {
      this.uploadedImages.splice(index, 1)
    },

    async onSubmit() {
      if (this.submitting) return

      if (!this.feedbackType) {
        uni.showToast({ title: i18n.t('feedback.typeRequired'), icon: 'none' })
        return
      }

      if (!this.description.trim()) {
        uni.showToast({ title: i18n.t('feedback.descRequired'), icon: 'none' })
        return
      }

      this.submitting = true
      try {
        await feedbackApi.submitFeedback({
          type: this.feedbackType,
          description: this.description.trim(),
          contact: this.contact,
          images: this.uploadedImages,
        })
        uni.showToast({ title: i18n.t('feedback.submitted'), icon: 'success' })
        setTimeout(() => {
          uni.navigateBack()
        }, 1500)
      } catch {
        uni.showToast({ title: i18n.t('feedback.failed'), icon: 'none' })
      } finally {
        this.submitting = false
      }
    },
  },
}
</script>

<style lang="scss" scoped>
.feedback {
  /* 视口锁定外壳：整页锁定视口高度 + 裁剪溢出，唯一滚动权威是内部 scroll-view，
     避免页面级滚动与内部 scroll-view 双层抢手势（约定见 goods/detail.vue）。
     100vh 写在 100dvh 之前做兜底。 */
  display: flex;
  flex-direction: column;
  /* 视口高度：三层渐进增强。
     1) 100vh  —— 兜底，所有环境合法
     2) 100dvh —— H5 移动浏览器动态视口（地址栏）
     3) var(--window-top) —— uni-app 顶部窗口偏移：非 custom 页 = 导航栏高 + 状态栏高，custom 页 = 0。
        必须扣除，否则顶部内容会顶到导航栏/状态栏底下。
     只减 --window-top 不减 --window-bottom：后者在 H5 被归一化为 env(safe-area-inset-bottom)，
     会与页面自身底栏的 safe-area padding 重复预留。本批无 tabBar 页，故安全。 */
  height: 100vh;
  height: 100dvh;
  height: calc(100dvh - var(--window-top, 0px));
  overflow: hidden;
  background: var(--color-background);
}

/* 顶部导航栏：常流 flex 子项，天然吸顶（父级 overflow:hidden 后 sticky 无依附，已删除）。
   flex-shrink:0 防止列向 flex 中被压缩塌陷；position:relative 为
   .back-btn/.header-placeholder 的绝对定位保留包含块（原先由 sticky 提供）。 */
.header {
  position: relative;
  flex-shrink: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: center;
  /* 顶部状态栏安全区：取 --status-bar-height 与 env(safe-area-inset-top) 较大值，避免 iOS 双重计算；第二条为老 WebView 兜底 */
  height: calc(88rpx + var(--status-bar-height, 0px));
  height: calc(88rpx + max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px)));
  padding-top: var(--status-bar-height, 0px);
  padding-top: max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px));
  box-sizing: border-box;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
}

.back-btn {
  position: absolute;
  left: 16rpx;
  /* 绝对定位元素吃不到父级 padding，需自行下移状态栏高度 */
  top: var(--status-bar-height, 0px);
  top: max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px));
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 72rpx;
  height: 72rpx;
  border-radius: var(--radius-sm);
}

.back-icon {
  font-size: 48rpx;
  color: var(--color-primary);
  line-height: 1;
}

.header-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  letter-spacing: -0.02em;
}

.header-placeholder {
  position: absolute;
  right: 16rpx;
  /* 绝对定位元素吃不到父级 padding，需自行下移状态栏高度 */
  top: var(--status-bar-height, 0px);
  top: max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px));
  width: 72rpx;
  height: 72rpx;
}

/* 内容区 */
.content {
  /* 唯一滚动权威：flex:1 占满外壳剩余空间，不再硬算高度（约定见 goods/detail.vue）。
     min-height:0 覆盖 flex 项默认 min-height:auto，允许收缩到小于内容高度以产生内部滚动。
     uni-scroll-view 在 H5 下为自定义元素,默认 display:inline 会收缩宽度,
     需显式 block + width:100% + border-box 才能铺满屏幕宽度 */
  display: block;
  flex: 1;
  min-height: 0;
  width: 100%;
  padding: 32rpx;
  box-sizing: border-box;
}

/* 区块 */
.section {
  margin-bottom: 40rpx;
}

.section-title {
  display: block;
  margin-bottom: 20rpx;
  font-size: 30rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

/* 反馈类型 */
.type-list {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.type-item {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 64rpx;
  padding: 0 28rpx;
  border-radius: 16rpx;
  border: 1rpx solid var(--color-divider);
  background: var(--color-surface);
}

.type-item.active {
  background: var(--color-primary);
  border-color: var(--color-primary);
}

.type-text {
  font-size: 26rpx;
  color: var(--color-text);
  white-space: nowrap;
}

.type-item.active .type-text {
  color: #ffffff;
  font-weight: var(--font-weight-medium);
}

/* 文本域 */
.textarea-wrapper {
  border-radius: var(--radius-md);
  border: 1rpx solid var(--color-divider);
  background: var(--color-surface);
  overflow: hidden;
}

.textarea {
  width: 100%;
  min-height: 240rpx;
  padding: 24rpx;
  font-size: 28rpx;
  line-height: 1.6;
  color: var(--color-text);
  background: transparent;
  border: none;
  outline: none;
  box-sizing: border-box;
}

.textarea-counter {
  display: flex;
  justify-content: flex-end;
  padding: 12rpx 24rpx;
  border-top: 1rpx solid var(--color-divider);
}

.counter-text {
  font-size: 24rpx;
  color: var(--color-text-tertiary);
}

/* 上传截图 */
.upload-list {
  display: flex;
  gap: 20rpx;
  flex-wrap: wrap;
}

.upload-item {
  position: relative;
  width: 160rpx;
  height: 160rpx;
  border-radius: var(--radius-md);
  overflow: hidden;
}

.upload-preview {
  width: 100%;
  height: 100%;
}

.upload-remove {
  position: absolute;
  top: 8rpx;
  right: 8rpx;
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
}

.remove-icon {
  font-size: 28rpx;
  color: #ffffff;
  line-height: 1;
}

.upload-btn {
  width: 160rpx;
  height: 160rpx;
  border-radius: var(--radius-md);
  border: 2rpx dashed var(--color-divider);
  background: var(--color-surface);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
}

.upload-btn-icon {
  font-size: 48rpx;
  color: var(--color-text-tertiary);
  line-height: 1;
}

.upload-btn-label {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}

/* 输入框 */
.input-wrapper {
  display: flex;
  align-items: center;
  height: 88rpx;
  padding: 0 24rpx;
  border-radius: var(--radius-md);
  border: 1rpx solid var(--color-divider);
  background: var(--color-surface);
}

.input {
  flex: 1;
  font-size: 28rpx;
  color: var(--color-text);
  background: transparent;
  border: none;
  outline: none;
}

.input::placeholder {
  color: var(--color-text-tertiary);
}

/* 提交区域 */
.submit-area {
  padding-top: 16rpx;
  padding-bottom: 64rpx;
}

.submit-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 88rpx;
  border-radius: 999rpx;
  background: var(--color-primary);
}

.submit-btn-text {
  font-size: 30rpx;
  font-weight: var(--font-weight-semibold);
  color: #ffffff;
}
</style>
