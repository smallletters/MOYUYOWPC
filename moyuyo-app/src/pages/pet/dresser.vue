<template>
  <view class="pet-dresser">
    <view class="page-header">
      <view class="back" aria-label="返回" @click="goBack">
        <text class="luc luc-arrow-left" />
      </view>
      <text class="title">宠物装扮</text>
      <!-- 上传自定义形象入口 -->
      <view class="upload-btn" @click="onUpload">
        <text class="luc luc-plus" />
        <text class="upload-btn-text">上传</text>
      </view>
    </view>

    <scroll-view scroll-y class="content">
      <!-- 宠物预览 -->
      <view class="preview-card">
        <image :src="currentPet?.avatar || ''" class="pet-avatar" />
        <text class="pet-name">{{ currentPet?.name || '宠物' }}</text>
        <text class="preview-tip">点击右侧装扮为宠物添加配饰</text>
      </view>

      <!-- 装扮分类 -->
      <view class="cat-tabs">
        <view
          v-for="cat in categories"
          :key="cat.id"
          class="cat-tab"
          :class="{ active: activeCat === cat.id }"
          @click="activeCat = cat.id"
        >
          {{ cat.label }}
        </view>
      </view>

      <!-- 装扮列表 -->
      <view class="dresser-grid">
        <view
          v-for="d in filteredItems"
          :key="d.id"
          class="dresser-card"
          :class="{ equipped: d.equipped }"
          @click="onEquip(d)"
        >
          <image :src="d.imageUrl" class="dresser-image" mode="aspectFill" />
          <text class="dresser-name">{{ d.name }}</text>
          <text v-if="d.equipped" class="equipped-tag">已穿戴</text>
          <!-- 仅用户自定义装扮(userId 非 NULL)显示删除按钮，系统装扮不可删 -->
          <view v-if="d.userId" class="delete-btn" @click.stop="onDelete(d)">
            <text class="luc luc-trash-2" />
          </view>
        </view>

        <!-- 空状态 -->
        <view v-if="!loading && filteredItems.length === 0" class="empty-state">
          <text class="empty-text">暂无装扮，点击右上角上传自定义形象</text>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import { petApi, petDresserApi, uploadApi } from '@/api'

export default {
  pageTitleKey: 'pageTitle.petDresser',

  data() {
    return {
      currentPet: null,
      activeCat: 'all',
      // 分类与后端 category 白名单保持一致: hat/scarf/clothes/toy/custom
      categories: [
        { id: 'all', label: '全部' },
        { id: 'hat', label: '帽子' },
        { id: 'scarf', label: '围巾' },
        { id: 'clothes', label: '衣服' },
        { id: 'toy', label: '玩具' },
        { id: 'custom', label: '自定义' },
      ],
      items: [],
      loading: false,
    }
  },

  computed: {
    filteredItems() {
      if (this.activeCat === 'all') return this.items
      return this.items.filter((i) => i.category === this.activeCat)
    },
  },

  onShow() {
    this.init()
  },

  methods: {
    async init() {
      await this.loadPet()
      if (this.currentPet?.id) {
        this.loadOutfits()
      }
    },

    // 加载当前用户宠物列表，取第一只作为当前宠物
    async loadPet() {
      try {
        const pets = await petApi.getPets()
        this.currentPet = (pets && pets[0]) || null
      } catch (e) {
        this.currentPet = { name: '宠物' }
      }
    },

    // 从后端加载装扮列表：系统装扮 + 当前用户自定义装扮
    async loadOutfits() {
      if (!this.currentPet?.id) return
      this.loading = true
      try {
        const list = await petDresserApi.getPetOutfits(this.currentPet.id)
        this.items = Array.isArray(list) ? list : []
      } catch (e) {
        console.warn('[dresser] load outfits failed', e)
        uni.showToast({ title: this.$t('petDresser.loadFailed'), icon: 'none' })
      } finally {
        this.loading = false
      }
    },

    goBack() {
      uni.navigateBack()
    },

    // 装备装扮：调用后端接口，成功后刷新列表(后端会更新 equipped 字段)
    async onEquip(item) {
      if (!this.currentPet?.id) return
      try {
        await petDresserApi.equipOutfit(this.currentPet.id, item.id)
        await this.loadOutfits()
        uni.showToast({
          title: this.$t('petDresser.equipped', { name: item.name }),
          icon: 'success',
        })
      } catch (e) {
        uni.showToast({ title: e?.message || this.$t('petDresser.equipFailed'), icon: 'none' })
      }
    },

    // 上传自定义装扮形象：选图 → 上传图片 → 提交装扮记录
    onUpload() {
      if (!this.currentPet?.id) {
        uni.showToast({ title: this.$t('petDresser.createPetFirst'), icon: 'none' })
        return
      }
      uni.chooseImage({
        count: 1,
        sourceType: ['album', 'camera'],
        success: async (res) => {
          const filePath = res.tempFilePaths && res.tempFilePaths[0]
          if (!filePath) return
          // 1. 先上传图片拿到 imageUrl
          uni.showLoading({ title: this.$t('petDresser.uploading'), mask: true })
          try {
            const uploadRes = await uploadApi.uploadImage(filePath)
            const imageUrl = uploadRes?.url
            if (!imageUrl) throw new Error('图片上传失败')
            // 2. 提交装扮记录到后端（默认归类为 custom）
            const created = await petDresserApi.uploadOutfit(this.currentPet.id, {
              category: 'custom',
              name: '自定义形象',
              imageUrl,
            })
            uni.hideLoading()
            uni.showToast({ title: this.$t('petDresser.uploaded'), icon: 'success' })
            // 上传完成后自动切到"自定义"分类并刷新列表
            this.activeCat = 'custom'
            await this.loadOutfits()
            // 可选：提示用户可重命名
            if (created?.id) {
              console.log('[dresser] outfit created:', created.id)
            }
          } catch (e) {
            uni.hideLoading()
            uni.showToast({ title: e?.message || this.$t('petDresser.uploadFailed'), icon: 'none' })
          }
        },
        fail: () => {
          // 用户取消选择，静默处理
        },
      })
    },

    // 删除装扮：仅用户自定义装扮可删，调用前二次确认
    onDelete(item) {
      if (!item.userId) {
        uni.showToast({ title: this.$t('petDresser.systemOutfitUnremovable'), icon: 'none' })
        return
      }
      uni.showModal({
        title: this.$t('petDresser.deleteTitle'),
        content: this.$t('petDresser.deleteContent', { name: item.name }),
        confirmColor: '#ff4d4f',
        success: async (modalRes) => {
          if (!modalRes.confirm) return
          try {
            await petDresserApi.deleteOutfit(this.currentPet.id, item.id)
            uni.showToast({ title: this.$t('petDresser.deleted'), icon: 'success' })
            await this.loadOutfits()
          } catch (e) {
            uni.showToast({ title: e?.message || this.$t('petDresser.deleteFailed'), icon: 'none' })
          }
        },
      })
    },
  },
}
</script>

<style lang="scss" scoped>
.pet-dresser {
  min-height: 100vh;
  background: var(--color-background);
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
  margin-right: 60rpx;
}

.upload-btn {
  display: flex;
  align-items: center;
  gap: 6rpx;
  padding: 8rpx 16rpx;
  background: var(--color-primary);
  border-radius: 999rpx;
  color: #fff;
  font-size: var(--font-size-xs);
}

.upload-btn .luc {
  font-size: 28rpx;
}

.upload-btn-text {
  color: #fff;
}

.content {
  padding: 24rpx;
}

.preview-card {
  padding: 32rpx 24rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
  text-align: center;
  margin-bottom: 24rpx;
}

.pet-avatar {
  width: 200rpx;
  height: 200rpx;
  border-radius: 50%;
  background: var(--color-background);
  margin-bottom: 16rpx;
}

.pet-name {
  display: block;
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  margin-bottom: 8rpx;
}

.preview-tip {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.cat-tabs {
  display: flex;
  background: var(--color-surface);
  border-radius: var(--radius-md);
  margin-bottom: 16rpx;
  overflow-x: auto;
}

.cat-tab {
  flex: 1;
  min-width: 110rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  white-space: nowrap;
}

.cat-tab.active {
  color: var(--color-primary);
  font-weight: var(--font-weight-semibold);
}

.dresser-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16rpx;
}

.dresser-card {
  position: relative;
  padding: 16rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}

.dresser-card.equipped {
  border-color: var(--color-primary);
  background: var(--color-primary-light);
}

.dresser-image {
  width: 100%;
  aspect-ratio: 1;
  border-radius: var(--radius-sm);
  background: var(--color-background);
}

.dresser-name {
  font-size: var(--font-size-xs);
  color: var(--color-text);
  text-align: center;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.equipped-tag {
  position: absolute;
  top: 8rpx;
  right: 8rpx;
  padding: 2rpx 8rpx;
  background: var(--color-primary);
  color: var(--color-text);
  border-radius: 999rpx;
  font-size: 18rpx;
}

.delete-btn {
  position: absolute;
  top: 8rpx;
  left: 8rpx;
  width: 44rpx;
  height: 44rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.45);
  border-radius: 50%;
  color: #fff;
  font-size: 24rpx;
}

.empty-state {
  grid-column: 1 / -1;
  padding: 80rpx 0;
  text-align: center;
}

.empty-text {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}
</style>
