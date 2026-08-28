<template>
  <view class="edit-profile">
    <!-- 顶部导航栏 -->
    <view class="header">
      <view class="back-btn" @click="goBack">
        <text class="back-icon"><text class="luc luc-arrow-left" /></text>
      </view>
      <text class="header-title">{{ t('editProfile.title') }}</text>
      <view class="save-link" :class="{ disabled: saving }" @click="onSave">
        <text class="save-text">
          {{ saving ? t('editProfile.saving') : t('editProfile.save') }}
        </text>
      </view>
    </view>

    <view class="content">
      <!-- 基本信息 -->
      <view class="form-group">
        <text class="group-label">{{ t('editProfile.basicInfo') }}</text>
        <view class="form-card">
          <!-- 昵称 -->
          <view class="form-item" @click="onEditNickname">
            <text class="item-label">{{ t('editProfile.nickname') }}</text>
            <view class="item-right">
              <text class="item-value">{{ profile.nickname || t('editProfile.notSet') }}</text>
              <text class="chevron"><text class="luc luc-chevron-right" /></text>
            </view>
          </view>
          <view class="divider indent" />
          <!-- 性别 -->
          <view class="form-item" @click="onPickGender">
            <text class="item-label">{{ t('editProfile.gender') }}</text>
            <view class="item-right">
              <text class="item-value">{{ genderLabel(profile.gender) }}</text>
              <text class="chevron"><text class="luc luc-chevron-right" /></text>
            </view>
          </view>
          <view class="divider indent" />
          <!-- 生日 -->
          <view class="form-item" @click="onPickBirthday">
            <text class="item-label">{{ t('editProfile.birthday') }}</text>
            <view class="item-right">
              <text class="item-value">{{ profile.birthday || t('editProfile.notSet') }}</text>
              <text class="chevron"><text class="luc luc-chevron-right" /></text>
            </view>
          </view>
        </view>
      </view>

      <!-- 联系方式（仅展示，不在本页编辑） -->
      <view class="form-group">
        <text class="group-label">{{ t('editProfile.contact') }}</text>
        <view class="form-card">
          <view class="form-item">
            <text class="item-label">{{ t('editProfile.email') }}</text>
            <view class="item-right">
              <text class="item-value">{{ profile.email || t('editProfile.notBound') }}</text>
            </view>
          </view>
          <view class="divider indent" />
          <view class="form-item">
            <text class="item-label">{{ t('editProfile.phone') }}</text>
            <view class="item-right">
              <text class="item-value">{{ profile.phone || t('editProfile.notBound') }}</text>
            </view>
          </view>
        </view>
      </view>
    </view>

    <!-- 性别选择 Picker（4 选 1） -->
    <uni-popup ref="genderPopup" type="bottom">
      <view class="picker-sheet">
        <view class="picker-sheet__head">
          <text class="picker-sheet__title">{{ t('editProfile.editGender') }}</text>
          <text class="picker-sheet__close" @click="closeGenderPopup">
            {{ t('common.cancel') }}
          </text>
        </view>
        <view class="picker-sheet__list">
          <view
            v-for="opt in genderOptions"
            :key="opt.value"
            class="picker-sheet__item"
            :class="{ active: profile.gender === opt.value }"
            @click="confirmGender(opt.value)"
          >
            <text>{{ opt.label }}</text>
            <text v-if="profile.gender === opt.value" class="picker-sheet__check">
              <text class="luc luc-check" />
            </text>
          </view>
        </view>
      </view>
    </uni-popup>
  </view>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getUserInfo, updateUser as updateUserApi } from '@/api/user'
import i18n from '@/i18n'

// 轻量翻译函数（响应 localeVersion 变化，刷新依赖本地化的 computed）
const localeVersion = ref(0)
let _unsubLocale = null
function t(key, params) {
  void localeVersion.value // 触发依赖追踪
  return i18n.t(key, params)
}

// ===== 性别枚举（与后端 ProfileUpdateRequest 对齐） =====
// MALE 男 / FEMALE 女 / OTHER 中性 / UNDISCLOSED 不透露
const genderOptions = computed(() => [
  { value: 'MALE', label: t('editProfile.genderMale') },
  { value: 'FEMALE', label: t('editProfile.genderFemale') },
  { value: 'OTHER', label: t('editProfile.genderNeutral') },
  { value: 'UNDISCLOSED', label: t('editProfile.genderUndisclosed') },
])

// 性别值 -> 展示文案
function genderLabel(code) {
  if (!code) return t('editProfile.notSet')
  const hit = genderOptions.value.find((o) => o.value === code)
  return hit ? hit.label : code
}

// ===== 表单状态（与服务端字段对齐） =====
const profile = reactive({
  nickname: '',
  // MALE/FEMALE/OTHER/UNDISCLOSED；空表示未设置
  gender: '',
  // ISO-8601 日期字符串：1990-01-01；空表示未设置
  birthday: '',
  email: '',
  phone: '',
})

const saving = ref(false)
const genderPopup = ref(null)

// ===== 加载当前用户信息 =====
async function loadProfile() {
  try {
    const res = await getUserInfo()
    profile.nickname = res?.nickname || ''
    profile.gender = res?.gender || ''
    // 后端 birthday 为 LocalDate，Jackson 默认序列化为 "yyyy-MM-dd" 字符串
    profile.birthday = res?.birthday || ''
    profile.email = res?.email || ''
    profile.phone = res?.phone || ''
  } catch (err) {
    uni.showToast({ title: t('editProfile.loadFailed'), icon: 'none' })
  }
}

const goBack = () => uni.navigateBack()

// ===== 昵称：弹系统输入框 =====
function onEditNickname() {
  uni.showModal({
    title: t('editProfile.editNickname'),
    editable: true,
    placeholderText: t('editProfile.newNicknameHint', { max: t('editProfile.nicknameMaxLen') }),
    content: profile.nickname,
    success: ({ confirm, content }) => {
      if (!confirm) return
      const max = Number(t('editProfile.nicknameMaxLen')) || 64
      const trimmed = (content || '').trim().slice(0, max)
      if (!trimmed) {
        uni.showToast({ title: t('editProfile.nicknameRequired'), icon: 'none' })
        return
      }
      profile.nickname = trimmed
    },
  })
}

// ===== 性别 Picker =====
function onPickGender() {
  genderPopup.value?.open?.()
}
function closeGenderPopup() {
  genderPopup.value?.close?.()
}
function confirmGender(value) {
  profile.gender = value
  closeGenderPopup()
}

// ===== 生日 Picker =====
// uni-app 各端原生日期选择器 API 不一致（H5 / 小程序 / APP 行为差异大），
// 这里用 actionSheet 给出"设置 / 清除"二选一，跨端一致体验。
function onPickBirthday() {
  const actions = [t('editProfile.birthdayActionSet')]
  if (profile.birthday) actions.push(t('editProfile.birthdayActionClear'))
  uni.showActionSheet({
    itemList: actions,
    success: ({ tapIndex }) => {
      if (tapIndex === 0) {
        promptBirthday()
      } else if (tapIndex === 1) {
        profile.birthday = ''
      }
    },
  })
}

// 弹输入框让用户填写 YYYY-MM-DD，做基本格式 + 不晚于今天的校验
function promptBirthday() {
  uni.showModal({
    title: t('editProfile.pickBirthdayTitle'),
    editable: true,
    placeholderText: t('editProfile.birthdayFormatHint'),
    content: profile.birthday,
    success: ({ confirm, content }) => {
      if (!confirm) return
      const trimmed = (content || '').trim()
      // 格式校验：YYYY-MM-DD；防止用户输入 1990-1-1 / 1990.01.01 等不规范形式
      if (!/^\d{4}-\d{2}-\d{2}$/.test(trimmed)) {
        uni.showToast({ title: t('editProfile.birthdayFormatError'), icon: 'none' })
        return
      }
      const d = new Date(trimmed)
      if (Number.isNaN(d.getTime())) {
        uni.showToast({ title: t('editProfile.birthdayInvalid'), icon: 'none' })
        return
      }
      if (d > new Date()) {
        uni.showToast({ title: t('editProfile.birthdayFuture'), icon: 'none' })
        return
      }
      profile.birthday = trimmed
    },
  })
}

// ===== 保存：仅提交变更字段（白名单由后端 DTO 控制） =====
async function onSave() {
  if (saving.value) return
  saving.value = true
  try {
    // 仅提交页面真实可编辑的 3 个字段；后端只更新传入的非空字段
    const payload = {
      nickname: profile.nickname,
      gender: profile.gender || null,
      birthday: profile.birthday || null,
    }
    await updateUserApi(payload)
    uni.showToast({ title: t('editProfile.saved'), icon: 'success' })
    setTimeout(() => uni.navigateBack(), 800)
  } catch (err) {
    const msg = err?.message || t('editProfile.failed')
    uni.showToast({ title: msg, icon: 'none' })
  } finally {
    saving.value = false
  }
}

// 进入页面时拉取真实数据；订阅语言切换以刷新本地化文案
onMounted(() => {
  _unsubLocale = i18n.subscribe(() => {
    localeVersion.value += 1
  })
  loadProfile()
})
onBeforeUnmount(() => {
  if (_unsubLocale) _unsubLocale()
})
// onShow 保留以便后续扩展（如刷新未保存提示）
onShow(() => {})
</script>

<style lang="scss" scoped>
.edit-profile {
  min-height: 100vh;
  background: var(--color-background);
}

/* 顶部导航栏 */
.header {
  position: sticky;
  top: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 88rpx;
  padding: 0 24rpx;
  background: var(--color-card);
  border-bottom: 1rpx solid var(--color-border);
}
.back-btn {
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
.save-link {
  padding: 8rpx;
}
.save-link.disabled {
  opacity: 0.5;
}
.save-text {
  font-size: 28rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-primary);
}

/* 内容区 */
.content {
  padding: 40rpx 32rpx 64rpx;
  display: flex;
  flex-direction: column;
  gap: 40rpx;
}

/* 表单分组 */
.form-group {
  display: flex;
  flex-direction: column;
}
.group-label {
  font-size: 24rpx;
  font-weight: var(--font-weight-medium);
  color: var(--color-text-secondary);
  letter-spacing: 2rpx;
  padding: 0 8rpx 16rpx;
}
.form-card {
  background: var(--color-card);
  border-radius: 20rpx;
  overflow: hidden;
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

/* 分割线 */
.divider {
  height: 1rpx;
  background: var(--color-border);
}
.divider.indent {
  margin-left: 64rpx;
}

/* 底部 Picker 弹层 */
.picker-sheet {
  background: var(--color-card);
  border-radius: 24rpx 24rpx 0 0;
  padding-bottom: env(safe-area-inset-bottom);
}
.picker-sheet__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 32rpx;
  border-bottom: 1rpx solid var(--color-border);
}
.picker-sheet__title {
  font-size: 30rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}
.picker-sheet__close {
  font-size: 28rpx;
  color: var(--color-text-secondary);
}
.picker-sheet__list {
  padding: 8rpx 0 24rpx;
}
.picker-sheet__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 32rpx;
  font-size: 30rpx;
  color: var(--color-text);
}
.picker-sheet__item.active {
  color: var(--color-primary);
  font-weight: var(--font-weight-semibold);
}
.picker-sheet__check {
  font-size: 32rpx;
  color: var(--color-primary);
}
</style>
