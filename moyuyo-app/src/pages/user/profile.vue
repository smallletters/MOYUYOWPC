<template>
  <view class="profile">
    <view class="avatar-section">
      <image :src="userStore.userInfo?.avatar || defaultAvatar" class="avatar" />
      <text class="name">{{ userStore.userInfo?.nickname || 'User' }}</text>
      <text class="email">{{ userStore.userInfo?.email }}</text>
      <view
        class="btn btn-outline change-avatar"
        :class="{ disabled: avatarUploading }"
        @click="onChangeAvatar"
      >
        {{ avatarUploading ? $t('profile.uploading') : $t('profile.changeAvatarBtn') }}
      </view>
    </view>

    <view class="card">
      <view class="form-row">
        <text class="label">{{ $t('profile.nickname') }}</text>
        <input v-model="form.nickname" class="input">
      </view>
      <view class="form-row">
        <text class="label">{{ $t('profile.email') }}</text>
        <text class="readonly">{{ userStore.userInfo?.email }}</text>
      </view>
      <view class="form-row">
        <text class="label">{{ $t('profile.phone') }}</text>
        <input v-model="form.phone" class="input" type="number">
      </view>
      <view class="form-row">
        <text class="label">{{ $t('profile.birthday') }}</text>
        <picker
          mode="date"
          :value="form.birthday"
          :end="today"
          @change="form.birthday = $event.detail.value"
        >
          <view class="input picker">{{ form.birthday || $t('profile.select') }}</view>
        </picker>
      </view>
    </view>

    <view class="btn btn-primary save-btn" :class="{ disabled: saving }" @click="onSave">
      {{ saving ? $t('profile.saving') : $t('profile.save') }}
    </view>

    <view class="btn btn-outline logout-btn" @click="onLogout">{{ $t('settings.logout') }}</view>
  </view>
</template>

<script>
import { useUserStore } from '@/store'
import { uploadImage } from '@/api/upload'
import { i18n } from '@/i18n'

export default {
  pageTitleKey: 'pageTitle.userProfile',

  data() {
    return {
      defaultAvatar: 'https://i.pravatar.cc/200?img=20',
      // 保存中标记:防止双击重复提交
      saving: false,
      // 头像上传中标记:和 saving 独立,允许分别显示状态
      avatarUploading: false,
      form: {
        nickname: '',
        phone: '',
        birthday: '',
      },
      // locale 版本号:locale 切换时自增,触发模板重渲染
      localeVersion: 0,
    }
  },

  computed: {
    today() {
      return new Date().toISOString().split('T')[0]
    },
    userStore() {
      return useUserStore()
    },
  },

  onLoad(query) {
    // 守卫:如果 query.id 与当前登录用户不一致,说明是从 search 等地方点别人头像进来的
    // 当前 profile.vue 是编辑自己的页面,不支持查看他人;直接弹窗提示后返回
    const myId = this.userStore.userInfo && this.userStore.userInfo.id
    if (query && query.id && String(query.id) !== String(myId)) {
      uni.showModal({
        title: '暂不支持查看他人主页',
        content: '可在搜索页的用户 Tab 中查看',
        showCancel: false,
        confirmText: '我知道了',
        success: () => uni.navigateBack(),
      })
      return
    }
    if (this.userStore.userInfo) {
      this.form.nickname = this.userStore.userInfo.nickname || ''
      this.form.phone = this.userStore.userInfo.phone || ''
      this.form.birthday = this.userStore.userInfo.birthday || ''
    }
    // 订阅 locale 变化
    this._unsubLocale = i18n.subscribe(() => {
      this.localeVersion += 1
    })
  },

  onUnload() {
    if (this._unsubLocale) this._unsubLocale()
  },

  methods: {
    async onSave() {
      if (this.saving) return
      this.saving = true
      uni.showLoading({ title: i18n.t('profile.saving'), mask: true })
      try {
        await this.userStore.updateProfile(this.form)
        uni.hideLoading()
        uni.showToast({ title: i18n.t('profile.saved'), icon: 'success' })
      } catch (e) {
        uni.hideLoading()
        uni.showToast({ title: e?.message || i18n.t('profile.saveFailed'), icon: 'none' })
      } finally {
        this.saving = false
      }
    },

    /**
     * 头像上传闭环:选择 -> 上传到 file/upload -> 把返回 URL 一并提交到 updateProfile
     * 修复前仅展示选择提示,头像未真正保存到后端
     *
     * 后端已支持从 Content-Type 兜底推断扩展名(uni-app H5 选出的文件名通常不带后缀)
     * 因此此处只需用 filePath 走 uni.uploadFile 即可
     */
    async onChangeAvatar() {
      if (this.avatarUploading) return
      let chose = false
      try {
        const chooseRes = await uni.chooseImage({
          count: 1,
          sizeType: ['compressed'],
          // 显式 sourceType=['album']:
          // 1) 头像从相册选最自然,不需要现场拍照(拍照用相机插件/camera.js)
          // 2) Android 上 sourceType 同时含 'album'/'camera' 时 uni-app 会先弹系统
          //    ActionSheet 让用户选,但部分 uni-app 版本会把这个弹层 itemList 渲染成
          //    "uni.chooseImage.sourceType.camera / .album / .cancel" 字符串(渲染 bug),
          //    显式 ['album'] 绕过该弹层,直接进入系统相册选择器
          sourceType: ['album'],
        })
        chose = true
        const filePath = chooseRes.tempFilePaths?.[0]
        if (!filePath) return
        this.avatarUploading = true
        uni.showLoading({ title: i18n.t('profile.uploading'), mask: true })
        const uploadRes = await uploadImage(filePath)
        const avatarUrl = uploadRes?.url
        if (!avatarUrl) throw new Error('Upload returned no URL')
        await this.userStore.updateProfile({ avatar: avatarUrl })
        uni.hideLoading()
        uni.showToast({ title: i18n.t('profile.avatarUpdated'), icon: 'success' })
      } catch (e) {
        uni.hideLoading()
        // 用户在选择器里"取消"时,uni.chooseImage 抛 errMsg 含 "cancel",
        // 不要弹错误 toast 干扰体验。
        const msg = String(e?.errMsg || e?.message || '')
        const canceled = /cancel/i.test(msg)
        if (!chose || canceled) return
        // 给用户友好提示:不再透传后端技术文案(例如 "不支持的文件类型,仅允许 PNG/JPG/JPEG/GIF/WebP")
        uni.showToast({ title: i18n.t('profile.avatarUploadFailed'), icon: 'none' })
      } finally {
        this.avatarUploading = false
      }
    },

    onLogout() {
      uni.showModal({
        title: i18n.t('settings.logoutConfirm'),
        success: async (res) => {
          if (res.confirm) {
            await this.userStore.logout()
            uni.reLaunch({ url: '/pages/tabbar/user' })
          }
        },
      })
    },
  },
}
</script>

<style lang="scss" scoped>
.profile {
  min-height: 100vh;
  background: var(--color-background);
  padding-bottom: 64rpx;
}

.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 64rpx 24rpx;
  background: var(--color-surface);
}

.avatar {
  width: 160rpx;
  height: 160rpx;
  border-radius: 50%;
  background: var(--color-background);
  margin-bottom: 16rpx;
}

.name {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
}

.email {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
  margin-top: 4rpx;
}

.change-avatar {
  margin-top: 24rpx;
  padding: 12rpx 32rpx;
  font-size: var(--font-size-sm);
}

.card {
  background: var(--color-surface);
  border-radius: var(--radius-md);
  margin: 16rpx;
  padding: 0 24rpx;
}

.form-row {
  display: flex;
  align-items: center;
  padding: 24rpx 0;
  border-bottom: 1rpx solid var(--color-divider);
}

.form-row:last-child {
  border-bottom: none;
}

.label {
  width: 200rpx;
  font-size: var(--font-size-base);
  color: var(--color-text-secondary);
}

.input {
  flex: 1;
  font-size: var(--font-size-base);
  text-align: right;
}

.readonly {
  flex: 1;
  text-align: right;
  font-size: var(--font-size-base);
  color: var(--color-text-tertiary);
}

.picker {
  min-height: 44rpx;
}

.save-btn,
.logout-btn {
  margin: 16rpx 24rpx;
  padding: 24rpx 0;
  font-size: var(--font-size-md);
  display: flex;
  justify-content: center;
  align-items: center;
  border-radius: 999rpx;
  transition: all 0.2s ease;
}

/* 主操作按钮:品牌主色渐变 + 阴影抬起 + 按压反馈 */
.save-btn {
  /* 改用页面品牌主色 Sand Gold 渐变,与全站一致 */
  background: linear-gradient(135deg, #e8ddb5 0%, #dbc98a 50%);
  color: #2e2b29;
  border: none;
  font-weight: 600;
  letter-spacing: 2rpx;
  box-shadow:
    0 8rpx 20rpx rgba(219, 201, 138, 0.45),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.5);
}

.save-btn:active:not(.disabled) {
  transform: scale(0.98);
  box-shadow:
    0 4rpx 10rpx rgba(219, 201, 138, 0.3),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.4);
}

.save-btn.disabled {
  opacity: 0.45;
  box-shadow: none;
  cursor: not-allowed;
}

/* 退出按钮:危险操作使用浅红底 + 红字,比整圈红描边更柔和 */
.logout-btn {
  background: rgba(255, 59, 48, 0.08);
  color: var(--color-danger);
  border: 2rpx solid rgba(255, 59, 48, 0.2);
}

.logout-btn:active {
  background: rgba(255, 59, 48, 0.15);
  transform: scale(0.98);
}
</style>
