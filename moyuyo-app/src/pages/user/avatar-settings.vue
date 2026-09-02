<template>
  <view class="avatar-settings">
    <!-- 顶部导航栏 -->
    <view class="header">
      <view class="back-btn" @click="goBack">
        <text class="back-icon luc-arrow-left" />
      </view>
      <text class="header-title">编辑资料</text>
      <view class="placeholder" />
    </view>

    <view class="content">
      <!-- 头像编辑区域 -->
      <view class="avatar-section">
        <view class="avatar-container">
          <view class="avatar-placeholder">
            <text class="avatar-letter">M</text>
          </view>
          <!-- 相机图标 -->
          <view class="camera-btn" @click="onShowActionSheet">
            <text class="camera-icon luc-camera" />
          </view>
        </view>
        <text class="avatar-hint">点击更换头像</text>
      </view>

      <!-- 昵称编辑 -->
      <view class="form-card">
        <view class="form-card-inner">
          <view class="field-header">
            <text class="field-label">昵称</text>
            <text class="field-limit">2-20 个字符</text>
          </view>
          <!-- 昵称显示模式 -->
          <view v-if="!isEditingNickname" class="nickname-display">
            <text class="nickname-value">{{ nickname }}</text>
            <view class="edit-nickname-btn" @click="startEditNickname">
              <text class="edit-nickname-icon luc-pencil" />
              <text class="edit-nickname-text">编辑</text>
            </view>
          </view>
          <!-- 昵称编辑模式 -->
          <view v-else class="nickname-edit">
            <input
              v-model="nickname"
              class="nickname-input"
              placeholder="请输入昵称"
              maxlength="20"
              focus
              @input="onNicknameInput"
            >
            <view class="nickname-footer">
              <view class="hint-row">
                <text class="hint-icon luc-check-circle" />
                <text class="hint-text" :class="hintType">昵称可用</text>
              </view>
              <text class="char-count">{{ nickname.length }}/20</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 个人简介编辑 -->
      <view class="form-card">
        <view class="form-card-inner">
          <view class="field-header">
            <text class="field-label">个人简介</text>
            <text class="field-limit">最多 100 字</text>
          </view>
          <textarea
            v-model="bio"
            class="bio-textarea"
            placeholder="介绍一下你和你的宠物吧..."
            maxlength="100"
            @input="onBioInput"
          />
          <view class="bio-footer">
            <text class="char-count">{{ bio.length }}/100</text>
          </view>
        </view>
      </view>

      <!-- 性别选择 -->
      <view class="form-card">
        <view class="form-card-inner">
          <text class="field-label block-label">性别</text>
          <view class="gender-options">
            <view
              v-for="option in genderOptions"
              :key="option.value"
              class="option-chip"
              :class="{ active: gender === option.value }"
              @click="selectGender(option.value)"
            >
              <text class="option-text">{{ option.label }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 生日设置 -->
      <view class="form-card">
        <view class="form-item" @click="onSelectBirthday">
          <text class="item-label">生日</text>
          <view class="item-right">
            <text class="item-value">{{ birthday }}</text>
            <text class="chevron luc-chevron-right" />
          </view>
        </view>
      </view>

      <!-- 地区设置 -->
      <view class="form-card">
        <view class="form-item" @click="onSelectRegion">
          <text class="item-label">地区</text>
          <view class="item-right">
            <text class="item-value">{{ region }}</text>
            <text class="chevron luc-chevron-right" />
          </view>
        </view>
      </view>

      <!-- IP 偏好设置 -->
      <view class="form-card">
        <view class="form-card-inner">
          <view class="field-header">
            <text class="field-label">IP 偏好</text>
            <text class="field-limit">可多选</text>
          </view>
          <view class="ip-grid">
            <view
              v-for="ip in ipOptions"
              :key="ip.id"
              class="ip-card"
              :class="{ active: selectedIps.includes(ip.id) }"
              @click="toggleIp(ip.id)"
            >
              <view class="ip-circle" :class="'ip-' + ip.id">
                <text class="ip-letter">{{ ip.letter }}</text>
              </view>
              <text class="ip-name" :class="{ active: selectedIps.includes(ip.id) }">
                {{ ip.name }}
              </text>
            </view>
          </view>
        </view>
      </view>
    </view>

    <!-- 底部固定保存按钮 -->
    <view class="save-bar safe-area-bottom">
      <view class="save-btn" @click="onSave">
        <text class="save-btn-text">保存</text>
      </view>
    </view>

    <!-- 头像更换操作面板 -->
    <view v-if="showActionSheet" class="action-sheet-overlay" @click="onCloseActionSheet">
      <view class="action-sheet" @click.stop>
        <view class="action-option" @click="onTakePhoto">
          <text class="action-icon luc-camera" />
          <text class="action-text">拍照</text>
        </view>
        <view class="action-divider" />
        <view class="action-option" @click="onFromGallery">
          <text class="action-icon luc-image" />
          <text class="action-text">从相册选择</text>
        </view>
        <view class="action-gap" />
        <view class="action-option cancel" @click="onCloseActionSheet">
          <text class="action-cancel-text">取消</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { usePageTitle } from '@/utils/i18nPageMixin'
usePageTitle('pageTitle.userAvatarSettings')


const goBack = () => uni.navigateBack()



// 昵称
const nickname = ref('宠物爱好者')
const isEditingNickname = ref(false)
const hintType = ref('success')

const startEditNickname = () => {
  isEditingNickname.value = true
}

const onNicknameInput = () => {
  // 昵称输入时的简单校验
  hintType.value = nickname.value.trim().length >= 2 ? 'success' : 'error'
}

// 个人简介
const bio = ref('热爱生活的铲屎官，有一只叫小橘的猫咪，喜欢记录日常。')
const onBioInput = () => {}

// 性别
const gender = ref('unset')
const genderOptions = [
  { label: '男', value: 'male' },
  { label: '女', value: 'female' },
  { label: '不设置', value: 'unset' },
]

const selectGender = (value) => {
  gender.value = value
}

// 生日
const birthday = ref('1995-06-15')
const onSelectBirthday = () => uni.showToast({ title: '选择生日', icon: 'none' })

// 地区
const region = ref('上海市 浦东新区')
const onSelectRegion = () => uni.showToast({ title: '选择地区', icon: 'none' })

// IP 偏好
const ipOptions = [
  { id: 'milo', name: 'MILO', letter: 'M' },
  { id: 'luna', name: 'LUNA', letter: 'L' },
  { id: 'atlas', name: 'ATLAS', letter: 'A' },
  { id: 'olive', name: 'OLIVE', letter: 'O' },
]
const selectedIps = ref(['milo', 'olive'])

const toggleIp = (id) => {
  const idx = selectedIps.value.indexOf(id)
  if (idx >= 0) {
    selectedIps.value.splice(idx, 1)
  } else {
    selectedIps.value.push(id)
  }
}

// 操作面板
const showActionSheet = ref(false)
const onShowActionSheet = () => {
  showActionSheet.value = true
}
const onCloseActionSheet = () => {
  showActionSheet.value = false
}
const onTakePhoto = () => {
  showActionSheet.value = false
  uni.showToast({ title: '打开相机', icon: 'none' })
}
const onFromGallery = () => {
  showActionSheet.value = false
  uni.showToast({ title: '打开相册', icon: 'none' })
}

// 保存
const onSave = () => uni.showToast({ title: '资料已保存', icon: 'success' })
</script>

<style lang="scss" scoped>
.avatar-settings {
  min-height: 100vh;
  background: var(--color-background);
  padding-bottom: 180rpx;
}

/* 顶部导航栏 */
.header {
  position: sticky;
  top: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 88rpx;
  padding: 0 24rpx;
  background: var(--color-card);
  border-bottom: 1rpx solid var(--color-border);
}

.back-btn {
  position: absolute;
  left: 24rpx;
  width: 72rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 16rpx;
}

.back-icon {
  font-size: 48rpx;
  color: var(--color-primary);
  line-height: 1;
}
.header-title {
  font-size: 32rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}
.placeholder {
  width: 72rpx;
}

/* 内容区 */
.content {
  padding: 40rpx 32rpx 0;
  display: flex;
  flex-direction: column;
  gap: 32rpx;
}

/* 头像区域 */
.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20rpx;
}
.avatar-container {
  position: relative;
}
.avatar-placeholder {
  width: 240rpx;
  height: 240rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #cfe5ff, #66abff);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4rpx 24rpx rgba(0, 0, 0, 0.08);
}

.avatar-letter {
  font-size: 48rpx;
  font-weight: 800;
  color: #fff;
}
.camera-btn {
  position: absolute;
  bottom: 0;
  right: 0;
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4rpx 16rpx rgba(219, 201, 138, 0.3);
  border: 5rpx solid var(--color-card);
}

.camera-icon {
  font-size: 28rpx;
}
.avatar-hint {
  font-size: 24rpx;
  color: var(--color-text-secondary);
}

/* 通用表单卡片 */
.form-card {
  background: var(--color-card);
  border-radius: 20rpx;
  overflow: hidden;
}

.form-card-inner {
  padding: 28rpx 32rpx;
}

.field-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16rpx;
}

.field-label {
  font-size: 28rpx;
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
}
.field-limit {
  font-size: 24rpx;
  color: var(--color-text-secondary);
}
.block-label {
  display: block;
  margin-bottom: 24rpx;
}

/* 昵称区域 */
.nickname-display {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 0;
}
.nickname-value {
  font-size: 30rpx;
  color: var(--color-text-secondary);
}
.edit-nickname-btn {
  display: flex;
  align-items: center;
  gap: 8rpx;
}
.edit-nickname-icon {
  font-size: 24rpx;
}
.edit-nickname-text {
  font-size: 28rpx;
  font-weight: var(--font-weight-medium);
  color: var(--color-primary);
}

.nickname-input {
  width: 100%;
  padding: 24rpx 28rpx;
  border: 3rpx solid var(--color-border);
  border-radius: var(--radius-md, 24rpx);
  background: var(--color-card);
  color: var(--color-text);
  font-size: 30rpx;
}

.nickname-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12rpx;
}
.hint-row {
  display: flex;
  align-items: center;
  gap: 8rpx;
}
.hint-icon {
  font-size: 24rpx;
}
.hint-text {
  font-size: 24rpx;
}
.hint-text.success {
  color: var(--color-success);
}
.hint-text.error {
  color: var(--color-error);
}
.char-count {
  font-size: 24rpx;
  color: var(--color-text-secondary);
}

/* 简介文本域 */
.bio-textarea {
  width: 100%;
  padding: 24rpx 28rpx;
  border: 3rpx solid var(--color-border);
  border-radius: var(--radius-md, 24rpx);
  background: var(--color-card);
  color: var(--color-text);
  font-size: 30rpx;
  line-height: 1.5;
  height: 200rpx;
}

.bio-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 8rpx;
}

/* 性别选项 */
.gender-options {
  display: flex;
  align-items: center;
  gap: 24rpx;
}
.option-chip {
  padding: 16rpx 40rpx;
  border-radius: 999rpx;
  border: 3rpx solid var(--color-border);
  background: var(--color-card);
}

.option-text {
  font-size: 28rpx;
  font-weight: var(--font-weight-medium);
  color: var(--color-text-secondary);
}

.option-chip.active {
  border-color: var(--color-primary);
  background: rgba(219, 201, 138, 0.1);
}

.option-chip.active .option-text {
  color: var(--color-primary);
  font-weight: 600;
}

/* 表单行 */
.form-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 32rpx;
  height: 104rpx;
}

.item-label {
  font-size: 28rpx;
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
}
.item-right {
  display: flex;
  align-items: center;
  gap: 8rpx;
}
.item-value {
  font-size: 28rpx;
  color: var(--color-text-secondary);
}
.chevron {
  font-size: 36rpx;
  color: #aeaeb2;
  line-height: 1;
}

/* IP 偏好 */
.ip-grid {
  display: flex;
  gap: 24rpx;
}
.ip-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
  padding: 28rpx 16rpx;
  border-radius: var(--radius-md, 24rpx);
  border: 3rpx solid var(--color-border);
  background: var(--color-card);
}

.ip-card.active {
  border-color: var(--color-primary);
  background: rgba(219, 201, 138, 0.1);
  box-shadow: 0 0 0 2rpx var(--color-primary);
}

.ip-circle {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ip-letter {
  font-size: 36rpx;
  font-weight: 800;
  color: #fff;
}

.ip-milo .ip-circle {
  background: linear-gradient(135deg, #a8e6cf, #56c596);
}
.ip-luna .ip-circle {
  background: linear-gradient(135deg, #dcd6f7, #a29bfe);
}
.ip-atlas .ip-circle {
  background: linear-gradient(135deg, #ffd3b6, #ff9a56);
}
.ip-olive .ip-circle {
  background: linear-gradient(135deg, #c4e5a3, #7bc86c);
}

.ip-name {
  font-size: 26rpx;
  font-weight: 700;
  color: var(--color-text);
  letter-spacing: 1rpx;
}
.ip-name.active {
  color: var(--color-primary);
}

/* 底部保存栏 */
.save-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 40;
  padding: 24rpx 40rpx;
  padding-bottom: calc(24rpx + env(safe-area-inset-bottom));
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(20px);
  border-top: 1rpx solid var(--color-border);
}

.save-btn {
  width: 100%;
  height: 100rpx;
  border-radius: var(--radius-md, 24rpx);
  background: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
}

.save-btn-text {
  font-size: 32rpx;
  font-weight: 600;
  color: #fff;
}

/* 操作面板 */
.action-sheet-overlay {
  position: fixed;
  inset: 0;
  z-index: 50;
  background: rgba(0, 0, 0, 0.35);
  display: flex;
  align-items: flex-end;
  justify-content: center;
}

.action-sheet {
  width: 100%;
  background: var(--color-border);
  border-radius: 32rpx 32rpx 0 0;
  padding-bottom: env(safe-area-inset-bottom);
}

.action-option {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  height: 112rpx;
  background: var(--color-card);
  color: var(--color-primary);
  font-size: 34rpx;
  font-weight: 600;
}

.action-option:first-child {
  border-radius: 32rpx 32rpx 0 0;
}
.action-icon {
  font-size: 32rpx;
}
.action-text {
  font-size: 34rpx;
}
.action-divider {
  height: 2rpx;
  background: var(--color-border);
}
.action-gap {
  height: 16rpx;
  background: var(--color-border);
}
.action-option.cancel {
  color: var(--color-text-secondary);
  border-radius: 0;
}
.action-cancel-text {
  font-size: 34rpx;
  font-weight: 600;
  color: var(--color-text-secondary);
}
</style>
