<template>
  <view class="pet-profile">
    <view class="page-header">
      <view class="back" aria-label="返回" @click="goBack">
        <text class="luc luc-arrow-left" />
      </view>
      <text class="title">{{ isEdit ? '编辑宠物' : '新增宠物' }}</text>
      <view class="save-btn" @click="onSave">保存</view>
    </view>

    <scroll-view scroll-y class="content">
      <!-- 头像 -->
      <view class="avatar-card">
        <view class="avatar-wrap" @click="onChangeAvatar">
          <image :src="form.avatar || defaultAvatar" class="avatar" />
          <view class="avatar-edit"><text class="luc luc-camera" /></view>
        </view>
        <text class="avatar-tip">点击更换头像</text>
      </view>

      <!-- 基本信息 -->
      <view class="form-card">
        <view class="form-item">
          <text class="form-label">宠物名字</text>
          <input v-model="form.name" class="form-input" placeholder="请输入宠物名字">
        </view>
        <view class="form-item">
          <text class="form-label">种类</text>
          <picker mode="selector" :range="speciesOptions" @change="onSpeciesChange">
            <view class="form-picker">
              {{ form.species || '请选择' }}
              <text class="luc luc-chevron-right" />
            </view>
          </picker>
        </view>
        <view class="form-item">
          <text class="form-label">品种</text>
          <input v-model="form.breed" class="form-input" placeholder="如金毛寻回犬">
        </view>
        <view class="form-item">
          <text class="form-label">性别</text>
          <view class="gender-row">
            <view
              v-for="g in genderOptions"
              :key="g.value"
              class="gender-chip"
              :class="{ selected: form.gender === g.value }"
              @click="form.gender = g.value"
            >
              {{ g.label }}
            </view>
          </view>
        </view>
        <view class="form-item">
          <text class="form-label">生日</text>
          <picker mode="date" :value="form.birthday" @change="onBirthdayChange">
            <view class="form-picker">
              {{ form.birthday || '请选择' }}
              <text class="luc luc-chevron-right" />
            </view>
          </picker>
        </view>
        <view class="form-item">
          <text class="form-label">体重</text>
          <input
            v-model="form.weight"
            type="digit"
            class="form-input"
            placeholder="kg">
        </view>
      </view>

      <!-- 标签 -->
      <view class="form-card">
        <text class="card-title">性格标签</text>
        <view class="tag-list">
          <view
            v-for="tag in allTags"
            :key="tag"
            class="tag-chip"
            :class="{ selected: form.tags.includes(tag) }"
            @click="onTagToggle(tag)"
          >
            {{ tag }}
          </view>
        </view>
      </view>

      <view class="bottom-spacer" />
    </scroll-view>
  </view>
</template>

<script>
import { petApi } from '@/api'

export default {
  data() {
    return {
      isEdit: false,
      defaultAvatar: 'https://picsum.photos/200/200?random=50',
      form: {
        id: null,
        name: '',
        species: '',
        breed: '',
        gender: '',
        birthday: '',
        weight: '',
        avatar: '',
        tags: [],
      },
      speciesOptions: ['狗狗', '猫咪', '兔子', '鸟类', '其他'],
      genderOptions: [
        { value: 'male', label: '♂ 公' },
        { value: 'female', label: '♀ 母' },
      ],
      allTags: ['粘人', '活泼', '安静', '聪明', '贪吃', '爱玩', '胆大', '怕生'],
    }
  },

  onLoad(query) {
    if (query.id) {
      this.isEdit = true
      this.loadPet(query.id)
    }
  },

  methods: {
    async loadPet(id) {
      try {
        const pet = await petApi.getPetDetail(id)
        this.form = { ...this.form, ...pet }
      } catch (e) {
        console.warn('[pet-profile] load failed', e)
      }
    },

    goBack() {
      uni.navigateBack()
    },

    onChangeAvatar() {
      uni.chooseImage({
        count: 1,
        success: (res) => {
          this.form.avatar = res.tempFilePaths[0]
        },
      })
    },

    onSpeciesChange(e) {
      this.form.species = this.speciesOptions[e.detail.value]
    },

    onBirthdayChange(e) {
      this.form.birthday = e.detail.value
    },

    onTagToggle(tag) {
      const idx = this.form.tags.indexOf(tag)
      if (idx >= 0) {
        this.form.tags.splice(idx, 1)
      } else {
        this.form.tags.push(tag)
      }
    },

    async onSave() {
      if (!this.form.name) {
        uni.showToast({ title: '请输入宠物名字', icon: 'none' })
        return
      }
      uni.showLoading({ title: '保存中...' })
      try {
        // 编辑模式调用 updatePet,新增模式调用 createPet
        if (this.isEdit && this.form.id) {
          await petApi.updatePet(this.form.id, this.form)
        } else {
          await petApi.createPet(this.form)
        }
        uni.hideLoading()
        uni.showToast({ title: '已保存', icon: 'success' })
        setTimeout(() => uni.navigateBack(), 800)
      } catch (e) {
        uni.hideLoading()
        uni.showToast({ title: e?.message || '保存失败', icon: 'none' })
      }
    },
  },
}
</script>

<style lang="scss" scoped>
.pet-profile {
  min-height: 100vh;
  background: var(--color-background);
  padding-bottom: 80rpx;
}

.page-header {
  display: flex;
  align-items: center;
  height: 88rpx;
  padding: 0 24rpx;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
}

.back {
  width: 60rpx;
  height: 60rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 44rpx;
  color: var(--color-text);
}

.title {
  flex: 1;
  text-align: center;
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.save-btn {
  font-size: var(--font-size-sm);
  color: var(--color-primary);
  font-weight: var(--font-weight-semibold);
}

.content {
  padding: 24rpx;
}

.avatar-card {
  padding: 32rpx 24rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
  text-align: center;
  margin-bottom: 24rpx;
}

.avatar-wrap {
  position: relative;
  display: inline-block;
  margin-bottom: 12rpx;
}

.avatar {
  width: 160rpx;
  height: 160rpx;
  border-radius: 50%;
  background: var(--color-background);
}

.avatar-edit {
  position: absolute;
  right: -8rpx;
  bottom: -8rpx;
  width: 48rpx;
  height: 48rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-primary);
  color: var(--color-text);
  border-radius: 50%;
  font-size: 24rpx;
  border: 4rpx solid var(--color-surface);
}

.avatar-tip {
  display: block;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.form-card {
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
  margin-bottom: 24rpx;
}

.card-title {
  display: block;
  padding: 24rpx 24rpx 0;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  margin-bottom: 12rpx;
}

.form-item {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 24rpx;
  border-bottom: 1rpx solid var(--color-divider);
}

.form-item:last-child {
  border-bottom: none;
}

.form-label {
  width: 160rpx;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  flex-shrink: 0;
}

.form-input {
  flex: 1;
  font-size: var(--font-size-sm);
  color: var(--color-text);
}

.form-picker {
  flex: 1;
  font-size: var(--font-size-sm);
  color: var(--color-text);
}

.gender-row {
  flex: 1;
  display: flex;
  gap: 12rpx;
}

.gender-chip {
  flex: 1;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-background);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-pill);
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.gender-chip.selected {
  background: var(--color-primary-light);
  color: var(--color-primary);
  border-color: var(--color-primary);
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  padding: 0 24rpx 24rpx;
}

.tag-chip {
  padding: 8rpx 20rpx;
  background: var(--color-background);
  border: 1rpx solid var(--color-divider);
  border-radius: 999rpx;
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}

.tag-chip.selected {
  background: var(--color-primary);
  color: var(--color-text);
  border-color: var(--color-primary);
}

.bottom-spacer {
  height: 80rpx;
}
</style>
