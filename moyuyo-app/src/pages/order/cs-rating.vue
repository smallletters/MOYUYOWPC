<template>
  <view class="rating-page">
    <!-- 导航栏 -->
    <view class="nav-bar">
      <view class="nav-back" @tap="goBack">
        <text class="back-icon"><text class="luc luc-arrow-left" /></text>
      </view>
      <text class="nav-title">服务评价</text>
    </view>

    <scroll-view class="rating-body" scroll-y>
      <!-- 客服信息卡片 -->
      <view class="cs-info-card">
        <view class="cs-avatar">
          <text class="cs-emoji"><text class="luc luc-user" /></text>
        </view>
        <view class="cs-detail">
          <text class="cs-name">客服小 MO</text>
          <text class="cs-date">2026-07-08 14:30</text>
          <view class="cs-topic-tag">
            <text class="cs-topic-text">物流配送咨询</text>
          </view>
        </view>
      </view>

      <!-- 评分区域 -->
      <view class="rating-section">
        <text class="section-title">请对本次服务进行评价</text>

        <view class="rating-dimensions">
          <view v-for="(dim, index) in dimensions" :key="index" class="dimension-row">
            <text class="dim-label">{{ dim.label }}</text>
            <view class="star-row">
              <view
                v-for="star in 5"
                :key="star"
                class="star-btn"
                @tap="setRating(index, star)">
                <text class="star-icon" :class="{ 'star-active': star <= dim.rating }">
                  <text class="luc luc-star" />
                </text>
              </view>
            </view>
          </view>
        </view>
      </view>

      <!-- 分隔线 -->
      <view class="divider" />

      <!-- 文字评价区 -->
      <view class="textarea-section">
        <textarea
          v-model="feedbackText"
          class="feedback-textarea"
          placeholder="分享更多您的感受（选填）"
          placeholder-class="textarea-placeholder"
          maxlength="500"
          @input="updateCharCount"
        />
        <view class="char-count-row">
          <text class="char-count" :class="{ 'char-count-warn': charCount >= 450 }">
            {{ charCount }}/500
          </text>
        </view>
      </view>

      <!-- 上传截图区 -->
      <view class="upload-section">
        <view class="upload-row">
          <!-- 已上传的图片 -->
          <view v-for="(img, index) in uploadedImages" :key="index" class="upload-thumb">
            <text class="thumb-emoji">{{ img }}</text>
            <view class="thumb-delete" @tap="removeImage(index)">
              <text class="delete-icon"><text class="luc luc-x" /></text>
            </view>
          </view>
          <!-- 添加按钮 -->
          <view v-if="uploadedImages.length < 3" class="upload-add" @tap="addImage">
            <text class="add-circle-icon">⊕</text>
            <text class="add-count">{{ uploadedImages.length }}/3</text>
          </view>
        </view>
      </view>

      <!-- 提交按钮 -->
      <view class="submit-section">
        <view class="submit-btn" @tap="submitRating">
          <text class="submit-text">提交评价</text>
        </view>
      </view>

      <!-- 积分提示 -->
      <view class="reward-hint">
        <text class="reward-icon"><text class="luc luc-tag" /></text>
        <text class="reward-text">完成评价可获得 10 积分奖励</text>
      </view>

      <!-- 分隔线 -->
      <view class="divider" />

      <!-- 意见反馈入口 -->
      <view class="feedback-entry">
        <text class="feedback-question">对服务不满意？</text>
        <view class="feedback-btn" @tap="goFeedback">
          <text class="feedback-btn-icon"><text class="luc luc-message-circle" /></text>
          <text class="feedback-btn-text">提交意见反馈</text>
        </view>
      </view>

      <view class="bottom-space" />
    </scroll-view>
  </view>
</template>
<script setup>
import { ref } from 'vue'

// 评分维度
const dimensions = ref([
  { label: '解决问题程度', rating: 5 },
  { label: '响应速度', rating: 5 },
  { label: '服务态度', rating: 5 },
])

// 文字评价
const feedbackText = ref('')
const charCount = ref(0)

// 上传图片
const uploadedImages = ref(['camera'])

// 设置评分
const setRating = (dimIndex, star) => {
  dimensions.value[dimIndex].rating = star
}

// 更新字数
const updateCharCount = () => {
  charCount.value = feedbackText.value.length
}

// 添加图片
const addImage = () => {
  uni.chooseImage({
    count: 3 - uploadedImages.value.length,
    success: () => {
      uploadedImages.value.push('camera')
      uni.showToast({ title: '图片上传功能开发中', icon: 'none' })
    },
  })
}

// 删除图片
const removeImage = (index) => {
  uploadedImages.value.splice(index, 1)
}

// 提交评价
const submitRating = () => {
  const allRated = dimensions.value.every((d) => d.rating > 0)
  if (!allRated) {
    uni.showToast({ title: '请完成所有评分', icon: 'none' })
    return
  }
  uni.showLoading({ title: '提交中...' })
  setTimeout(() => {
    uni.hideLoading()
    uni.showToast({ title: '评价提交成功', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 1500)
  }, 1000)
}

// 前往反馈页
const goFeedback = () => {
  uni.navigateTo({ url: '/pages/user/feedback' })
}

// 返回
const goBack = () => {
  uni.navigateBack()
}
</script>

<style lang="scss" scoped>
.rating-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--color-background);
}

/* 导航栏 */
.nav-bar {
  display: flex;
  align-items: center;
  height: 88rpx;
  padding: 0 32rpx;
  background: var(--color-background);
  border-bottom: 1rpx solid var(--color-border);
  position: relative;
  justify-content: center;
}

.nav-back {
  position: absolute;
  left: 32rpx;
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.back-icon {
  font-size: 44rpx;
  color: var(--color-primary);
}

.nav-title {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--color-text);
}

/* 内容 */
.rating-body {
  flex: 1;
}

/* 客服信息卡片 */
.cs-info-card {
  margin: 32rpx;
  padding: 32rpx;
  border-radius: 32rpx;
  background: var(--color-card);
  border: 1rpx solid var(--color-border);
  display: flex;
  align-items: center;
  gap: 28rpx;
}

.cs-avatar {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background: #f2f2f7;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border: 3rpx solid #f2f2f7;
}

.cs-emoji {
  font-size: 44rpx;
}

.cs-detail {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.cs-name {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text);
}

.cs-date {
  font-size: 24rpx;
  color: var(--color-text-secondary);
}

.cs-topic-tag {
  align-self: flex-start;
  padding: 4rpx 16rpx;
  border-radius: 16rpx;
  background: #f2f2f7;
}

.cs-topic-text {
  font-size: 22rpx;
  color: var(--color-text-secondary);
}

/* 评分区域 */
.rating-section {
  margin: 0 32rpx;
}

.section-title {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--color-text);
  margin-bottom: 32rpx;
}

.rating-dimensions {
  display: flex;
  flex-direction: column;
  gap: 40rpx;
}

.dimension-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.dim-label {
  font-size: 28rpx;
  color: #48484a;
}

.star-row {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.star-btn {
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.star-icon {
  font-size: 40rpx;
  color: #c7c7cc;
}

.star-active {
  color: #ff9500;
}

/* 分隔线 */
.divider {
  height: 1rpx;
  background: var(--color-border);
  margin: 40rpx 32rpx;
}

/* 文字评价 */
.textarea-section {
  margin: 0 32rpx;
}

.feedback-textarea {
  width: 100%;
  min-height: 200rpx;
  padding: 28rpx;
  border-radius: 24rpx;
  background: #f2f2f7;
  border: 1rpx solid var(--color-border);
  font-size: 28rpx;
  line-height: 1.6;
  color: var(--color-text);
}

.textarea-placeholder {
  color: var(--color-text-secondary);
}

.char-count-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 12rpx;
}

.char-count {
  font-size: 24rpx;
  color: var(--color-text-secondary);
}

.char-count-warn {
  color: var(--color-error);
}

/* 上传截图 */
.upload-section {
  margin: 40rpx 32rpx 0;
}

.upload-row {
  display: flex;
  align-items: center;
  gap: 24rpx;
}

.upload-thumb {
  position: relative;
  width: 160rpx;
  height: 160rpx;
  border-radius: 24rpx;
  overflow: hidden;
  background: #f2f2f7;
  border: 1rpx solid var(--color-border);
  display: flex;
  align-items: center;
  justify-content: center;
}

.thumb-emoji {
  font-size: 56rpx;
}

.thumb-delete {
  position: absolute;
  top: 8rpx;
  right: 8rpx;
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  background: rgba(28, 28, 30, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
}

.delete-icon {
  font-size: 24rpx;
  color: #ffffff;
}

.upload-add {
  width: 160rpx;
  height: 160rpx;
  border-radius: 24rpx;
  border: 3rpx dashed #d1d1d6;
  background: #f2f2f7;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
}

.add-circle-icon {
  font-size: 44rpx;
  color: var(--color-text-secondary);
}

.add-count {
  font-size: 22rpx;
  color: var(--color-text-secondary);
}

/* 提交按钮 */
.submit-section {
  margin: 56rpx 32rpx 0;
}

.submit-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 96rpx;
  border-radius: 32rpx;
  background: var(--color-primary);
}

.submit-text {
  font-size: 32rpx;
  font-weight: 600;
  color: #ffffff;
}

/* 积分提示 */
.reward-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  margin-top: 24rpx;
}

.reward-icon {
  font-size: 28rpx;
}

.reward-text {
  font-size: 24rpx;
  color: var(--color-text-secondary);
}

/* 意见反馈入口 */
.feedback-entry {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 24rpx;
  padding: 0 32rpx 64rpx;
}

.feedback-question {
  font-size: 28rpx;
  color: var(--color-text-secondary);
}

.feedback-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  height: 96rpx;
  padding: 0 48rpx;
  border-radius: 32rpx;
  background: #f2f2f7;
}

.feedback-btn-icon {
  font-size: 28rpx;
}

.feedback-btn-text {
  font-size: 28rpx;
  font-weight: 500;
  color: var(--color-text);
}

.bottom-space {
  height: 64rpx;
}
</style>
