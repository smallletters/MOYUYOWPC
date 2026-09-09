<template>
  <view class="pet-profile">
    <scroll-view scroll-y class="content">
      <!-- 空档案引导：无宠物档案时（我的中心进入）显示新增入口 -->
      <template v-if="standby">
        <view class="empty-box">
          <view class="empty-icon"><text class="luc luc-paw-print" /></view>
          <text class="empty-title">{{ $t('petProfile.empty.title') }}</text>
          <text class="empty-desc">{{ $t('petProfile.empty.desc') }}</text>
          <view class="empty-btn" @click="onAddNew">
            <text class="luc luc-plus empty-btn-icon" />
            {{ $t('petProfile.empty.addBtn') }}
          </view>
        </view>
      </template>

      <!-- 宠物档案表单（新增 / 编辑回显共用） -->
      <template v-else>
        <!-- 宠物切换器：我的中心进入且账号有多只宠物时，支持切换查看/编辑 -->
        <scroll-view
          v-if="pets.length > 1 && isEdit && !standby"
          scroll-x
          class="pet-switcher-scroll"
        >
          <view class="pet-switcher">
            <view
              v-for="(p, i) in pets"
              :key="p.id"
              class="pet-switch-item"
              :class="{ active: i === currentIndex }"
              @click="switchPet(i)"
            >
              <image :src="p.avatar || defaultAvatar" class="pet-switch-avatar" />
              <text class="pet-switch-name">{{ p.name }}</text>
            </view>
          </view>
        </scroll-view>

        <!-- 头像 -->
        <view class="avatar-card">
          <view class="avatar-wrap" @click="onChangeAvatar">
            <image :src="form.avatar || defaultAvatar" class="avatar" />
            <view class="avatar-edit"><text class="luc luc-camera" /></view>
          </view>
          <text class="avatar-tip">{{ $t('petProfile.avatarTip') }}</text>
        </view>

        <!-- 基本信息 -->
        <view class="form-card">
          <view class="form-item">
            <text class="form-label">{{ $t('petProfile.fields.name') }}</text>
            <input
              v-model="form.name"
              class="form-input"
              :placeholder="$t('petProfile.fields.namePlaceholder')"
            >
          </view>
          <view class="form-item">
            <text class="form-label">{{ $t('petProfile.fields.species') }}</text>
            <picker mode="selector" :range="speciesOptions" @change="onSpeciesChange">
              <view class="form-picker">
                {{ form.species || $t('petProfile.fields.selectPlaceholder') }}
                <text class="luc luc-chevron-right" />
              </view>
            </picker>
          </view>
          <view class="form-item">
            <text class="form-label">{{ $t('petProfile.fields.breed') }}</text>
            <input
              v-model="form.breed"
              class="form-input"
              :placeholder="$t('petProfile.fields.breedPlaceholder')"
            >
          </view>
          <view class="form-item">
            <text class="form-label">{{ $t('petProfile.fields.gender') }}</text>
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
            <text class="form-label">{{ $t('petProfile.fields.birthday') }}</text>
            <picker mode="date" :value="form.birthday" @change="onBirthdayChange">
              <view class="form-picker">
                {{ form.birthday || $t('petProfile.fields.selectPlaceholder') }}
                <text class="luc luc-chevron-right" />
              </view>
            </picker>
          </view>
          <view class="form-item">
            <text class="form-label">{{ $t('petProfile.fields.weight') }}</text>
            <input
              v-model="form.weight"
              type="digit"
              class="form-input"
              :placeholder="$t('petProfile.fields.weightPlaceholder')"
            >
          </view>
          <view class="form-item">
            <text class="form-label">{{ $t('petProfile.fields.adoptedAt') }}</text>
            <picker mode="date" :value="form.adoptedAt" @change="onAdoptedAtChange">
              <view class="form-picker">
                {{ form.adoptedAt || $t('petProfile.fields.selectPlaceholder') }}
                <text class="luc luc-chevron-right" />
              </view>
            </picker>
          </view>
        </view>

        <!-- 标签 -->
        <view class="form-card">
          <text class="card-title">{{ $t('petProfile.tagsTitle') }}</text>
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

        <!-- 宠物状态操作区：编辑回显态下提供「去世/意外/删除」 -->
        <view v-if="isEdit && form.id" class="profile-actions">
          <view class="status-row">
            <text class="status-label">{{ $t('petProfile.actions.status') }}</text>
            <text class="status-badge" :class="'status-' + (form.status || 'ACTIVE')">
              {{ statusText }}
            </text>
          </view>
          <view class="action-row">
            <view class="action-btn medical" @click="markMedical">
              <text class="luc luc-plus" />
              {{ $t('petProfile.actions.medical') }}
            </view>
            <view class="action-btn died" @click="markDied">
              <text class="luc luc-x-circle" />
              {{ $t('petProfile.actions.died') }}
            </view>
            <view class="action-btn accident" @click="markAccident">
              <text class="luc luc-alert-triangle" />
              {{ $t('petProfile.actions.accident') }}
            </view>
            <view class="action-btn delete" @click="removePet">
              <text class="luc luc-trash-2" />
              {{ $t('petProfile.actions.delete') }}
            </view>
          </view>
        </view>

        <!-- 档案回显态下追加新增入口：我的中心进入时也可再建一只 -->
        <view v-if="showAddAnother" class="add-another" @click="onAddNew">
          <text class="luc luc-plus" />
          {{ $t('petProfile.addAnother') }}
        </view>

        <view class="bottom-spacer" />
      </template>
    </scroll-view>

    <!-- 底部保存按钮：移除自研顶部导航后，保存入口移至表单底部，避免功能丢失 -->
    <view v-if="!standby" class="footer-save">
      <view class="footer-save-btn" @click="onSave">{{ $t('petProfile.save') }}</view>
    </view>
  </view>
</template>

<script>
import { petApi } from '@/api'
import { uploadImage } from '@/api/upload'

export default {
  pageTitleKey: 'pageTitle.petProfile',

  data() {
    return {
      isEdit: false,
      // 无宠物档案时的空态引导（我的中心进入且账号暂无宠物）
      standby: false,
      // 当前编辑的宠物 id：仅 URL 带 ?id= 时存在；我的中心默认回显需传空
      petId: null,
      // 我的中心进入时拉到的全量宠物列表（仅该入口使用），供顶部切换器切换
      pets: [],
      // 当前展示的宠物在 pets 中的索引（切换器当前选中项）
      currentIndex: 0,
      defaultAvatar: 'https://picsum.photos/200/200?random=50',
      saving: false,
      avatarUploading: false,
      form: {
        id: null,
        name: '',
        species: '',
        breed: '',
        gender: '',
        status: 'ACTIVE',
        birthday: '',
        weight: '',
        adoptedAt: '',
        avatar: '',
        tags: [],
      },
    }
  },

  computed: {
    today() {
      return new Date().toISOString().split('T')[0]
    },
    // 选项数据随语言切换动态更新($t 在 computed 中建立响应式依赖)
    speciesOptions() {
      return this.$t('petProfile.speciesOptions')
    },
    genderOptions() {
      const g = this.$t('petProfile.gender') || {}
      return [
        { value: 'male', label: g.male },
        { value: 'female', label: g.female },
        { value: 'other', label: g.other },
      ]
    },
    allTags() {
      return this.$t('petProfile.tags')
    },
    // 从我的中心回显已有档案时，允许在同一页再新增一只宠物
    showAddAnother() {
      return this.isEdit && !this.petId && !this.standby
    },
    // 当前宠物的状态显示文案（回显状态为 null 时按正常处理）
    statusText() {
      const label = this.$t('petProfile.statusLabel') || {}
      const status = this.form.status || 'ACTIVE'
      return label[status] || status
    },
  },

  onLoad(query) {
    // 三种进入方式：
    //  ?id=xx        → 编辑指定宠物（pet-hub 等）
    //  ?mode=new     → 直接新建（pet 主页「添加宠物档案」）
    //  无参数        → 我的中心「我的宠物」：已有宠物则回显档案，否则空态引导新增
    this.petId = query?.id ? Number(query.id) : null
    const modeNew = query?.mode === 'new'
    if (this.petId || modeNew) {
      this.initDirect(modeNew)
    } else {
      this.initArchive()
    }
  },

  methods: {
    // 直接进入表单态：指定编辑或强制新增
    async initDirect(modeNew) {
      if (this.petId) {
        this.isEdit = true
        await this.loadPet(this.petId)
      } else {
        this.isEdit = false
      }
    },

    // 我的中心进入：优先回显已有的宠物档案（多只时用顶部切换器切换），无档案则显示空态引导
    async initArchive() {
      let pets = []
      try {
        pets = await petApi.getPets()
      } catch (e) {
        console.warn('[pet-profile] getPets failed', e)
      }
      // 后端按创建时间倒序，grep 到的最新在前。
      // 为保证「档案页默认展示最早创建那只」，改为升序（最初的宠物在前）。
      const list = Array.isArray(pets) ? [...pets].reverse() : []
      this.pets = list
      const first = list[0]
      if (first) {
        this.isEdit = true
        this.fillPet(first)
      } else {
        // 无档案：展示空态 + 「新增宠物」主按钮
        this.standby = true
        this.form = this.emptyForm()
      }
    },

    // 切换宠物：更新当前回显表单并同步编辑态/当前索引
    switchPet(index) {
      const pet = this.pets[index]
      if (!pet) return
      this.isEdit = true
      this.currentIndex = index
      this.fillPet(pet)
    },

    // 切换到新增模式（清空表单、退出空态）
    onAddNew() {
      this.standby = false
      this.isEdit = false
      this.petId = null
      this.form = this.emptyForm()
    },

    // 空白表单模板
    emptyForm() {
      return {
        id: null,
        name: '',
        species: '',
        breed: '',
        gender: '',
        status: 'ACTIVE',
        birthday: '',
        weight: '',
        adoptedAt: '',
        avatar: '',
        tags: [],
      }
    },

    async loadPet(id) {
      try {
        const pet = await petApi.getPetDetail(id)
        this.fillPet(pet)
      } catch (e) {
        console.warn('[pet-profile] load failed', e)
      }
    },

    // 宠物数据回填到表单（兼容 tags 为 JSON 字符串或数组、体重字段名差异）
    fillPet(pet) {
      if (!pet) return
      let tags = pet.tags
      if (typeof tags === 'string') {
        tags = tags
          .split(',')
          .map((s) => s.trim())
          .filter(Boolean)
      }
      if (!Array.isArray(tags)) tags = []
      this.form = {
        ...this.emptyForm(),
        ...pet,
        // weight 兼容 PetVO.weight 与后端 weight_kg 两种命名
        weight: pet.weight ?? pet.weightKg ?? '',
        // adoptedAt 兼容字段缺失（老后端/历史数据），缺省为空字符串
        adoptedAt: pet.adoptedAt || '',
        tags,
      }
    },

    /**
     * 头像选择 + 上传闭环:之前只存本地临时路径,后端拿到的是 _tmp 后缀路径无法显示
     */
    async onChangeAvatar() {
      if (this.avatarUploading) return
      try {
        const chooseRes = await uni.chooseImage({
          count: 1,
          sizeType: ['compressed'],
          // 头像从相册选最自然;显式 ['album'] 绕过 Android 上 uni-app
          // sourceType 弹层文案渲染 bug(显示成 "uni.chooseImage.sourceType.xxx")。
          sourceType: ['album'],
        })
        const filePath = chooseRes.tempFilePaths?.[0]
        if (!filePath) return
        this.avatarUploading = true
        uni.showLoading({ title: this.$t('petProfile.toast.uploadingAvatar'), mask: true })
        const uploadRes = await uploadImage(filePath)
        const url = uploadRes?.url
        if (!url) throw new Error('Upload returned no URL')
        this.form.avatar = url
        uni.hideLoading()
      } catch (e) {
        uni.hideLoading()
        uni.showToast({
          title: e?.message || this.$t('petProfile.toast.avatarUploadFailed'),
          icon: 'none',
        })
      } finally {
        this.avatarUploading = false
      }
    },

    onSpeciesChange(e) {
      this.form.species = this.speciesOptions[e.detail.value]
    },

    onBirthdayChange(e) {
      // 防止用户选择未来日期(逻辑校验,保存接口仍以后端为准)
      if (e.detail.value > this.today) {
        uni.showToast({ title: this.$t('petProfile.toast.birthdayFuture'), icon: 'none' })
        return
      }
      this.form.birthday = e.detail.value
    },

    onAdoptedAtChange(e) {
      // 加入家庭时间同样不允许晚于今天
      if (e.detail.value > this.today) {
        uni.showToast({ title: this.$t('petProfile.toast.adoptedAtFuture'), icon: 'none' })
        return
      }
      this.form.adoptedAt = e.detail.value
    },

    onTagToggle(tag) {
      const idx = this.form.tags.indexOf(tag)
      if (idx >= 0) {
        this.form.tags.splice(idx, 1)
      } else {
        this.form.tags.push(tag)
      }
    },

    // 通用确认弹窗：返回 Promise<boolean> 是否确认
    confirmModal(content, confirmText) {
      return new Promise((resolve) => {
        uni.showModal({
          title: this.$t('petProfile.actions.confirmTitle'),
          content,
          confirmText,
          cancelText: this.$t('petProfile.actions.cancel'),
          success: (res) => resolve(!!res.confirm),
        })
      })
    },

    // 标记「去世」：状态标记（保留档案可查看）
    async markDied() {
      const ok = await this.confirmModal(
        this.$t('petProfile.actions.diedConfirm'),
        this.$t('petProfile.actions.died'),
      )
      if (ok) await this.doUpdateStatus('DIED')
    },

    // 标记「意外」：状态标记（保留档案可查看）
    async markAccident() {
      const ok = await this.confirmModal(
        this.$t('petProfile.actions.accidentConfirm'),
        this.$t('petProfile.actions.accident'),
      )
      if (ok) await this.doUpdateStatus('ACCIDENT')
    },

    // 标记「医疗」：状态标记（保留档案可查看）
    async markMedical() {
      const ok = await this.confirmModal(
        this.$t('petProfile.actions.medicalConfirm'),
        this.$t('petProfile.actions.medical'),
      )
      if (ok) await this.doUpdateStatus('MEDICAL')
    },

    // 调用后端状态更新接口并同步本地回显
    async doUpdateStatus(status) {
      if (!this.form.id) return
      try {
        uni.showLoading({ title: this.$t('petProfile.toast.saving'), mask: true })
        await petApi.updatePetStatus(this.form.id, status)
        this.form.status = status
        uni.hideLoading()
        uni.showToast({ title: this.$t('petProfile.actions.updated'), icon: 'success' })
      } catch (e) {
        uni.hideLoading()
        uni.showToast({
          title: e?.message || this.$t('petProfile.actions.updateFailed'),
          icon: 'none',
        })
      }
    },

    // 删除宠物：确认后调用删除接口，并更新本地列表/空态
    async removePet() {
      if (!this.form.id) return
      const ok = await this.confirmModal(
        this.$t('petProfile.actions.deleteConfirm'),
        this.$t('petProfile.actions.delete'),
      )
      if (!ok) return
      try {
        uni.showLoading({ title: this.$t('petProfile.toast.saving'), mask: true })
        await petApi.deletePet(this.form.id)
        // 从本地列表移除该宠物（若来自我的中心多宠物列表）
        const idx = this.pets.findIndex((p) => p.id === this.form.id)
        if (idx >= 0) this.pets.splice(idx, 1)
        uni.hideLoading()
        uni.showToast({ title: this.$t('petProfile.actions.deleted'), icon: 'success' })
        setTimeout(() => {
          // 由 pet-hub 等以 ?id= 直接进入：删除后返回上一页
          if (this.petId) {
            uni.navigateBack()
            return
          }
          // 我的中心入口：剩余宠物则切到第一只，否则回到空态引导
          if (this.pets.length) {
            this.switchPet(0)
          } else {
            this.standby = true
            this.isEdit = false
            this.petId = null
            this.form = this.emptyForm()
          }
        }, 600)
      } catch (e) {
        uni.hideLoading()
        uni.showToast({
          title: e?.message || this.$t('petProfile.actions.updateFailed'),
          icon: 'none',
        })
      }
    },

    async onSave() {
      if (this.saving) return
      if (!this.form.name) {
        uni.showToast({ title: this.$t('petProfile.toast.nameRequired'), icon: 'none' })
        return
      }
      this.saving = true
      uni.showLoading({ title: this.$t('petProfile.toast.saving'), mask: true })
      try {
        // 只发送后端 PetEntity 实际字段,过滤掉 VO 里的 achievements/scenes/createdAt/updatedAt 等
        // 空字符串统一置空,避免后端 LocalDate/Double 反序列化报错或 NULL 触发 409
        const payload = {
          name: this.form.name || null,
          species: this.form.species || null,
          breed: this.form.breed || null,
          gender: this.form.gender || null,
          birthday: this.form.birthday || null,
          // 加入家庭时间：LocalDate(yyyy-MM-dd)，空值置空避免 409
          adoptedAt: this.form.adoptedAt || null,
          weight:
            this.form.weight === '' || this.form.weight === undefined || this.form.weight === null
              ? null
              : Number(this.form.weight),
          avatar: this.form.avatar || null,
          notes: this.form.notes || null,
          // tags 对应后端 mo_pet.tags(JSON 数组列)，实体字段为 List<String>，直接传数组
          tags: Array.isArray(this.form.tags) ? this.form.tags : [],
        }
        if (this.isEdit && this.form.id) {
          await petApi.updatePet(this.form.id, payload)
        } else {
          await petApi.createPet(payload)
        }
        uni.hideLoading()
        uni.showToast({ title: this.$t('petProfile.toast.saved'), icon: 'success' })
        setTimeout(() => uni.navigateBack(), 800)
      } catch (e) {
        uni.hideLoading()
        uni.showToast({ title: e?.message || this.$t('petProfile.toast.saveFailed'), icon: 'none' })
      } finally {
        this.saving = false
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

.footer-save {
  /* 底部固定保存条：取代被移除的自研顶栏「保存」入口 */
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 10;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background: var(--color-surface);
  border-top: 1rpx solid var(--color-divider);
  box-sizing: border-box;
}

.footer-save-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 88rpx;
  border-radius: var(--radius-pill);
  background: var(--color-primary);
  color: var(--color-text);
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
}

.content {
  /* 宽度自适应屏幕：uni-scroll-view 默认为 content-box，
     直接 padding 会被加在 width:100% 之外导致横向溢出，
     故用 box-sizing 把 padding 收进盒内，并禁止横向溢出 */
  width: 100%;
  box-sizing: border-box;
  padding: 24rpx;
  overflow-x: hidden;
}

/* ===== 宠物切换器 ===== */
.pet-switcher-scroll {
  white-space: nowrap;
  margin-bottom: 24rpx;
}
.pet-switcher {
  display: inline-flex;
  gap: 16rpx;
}
.pet-switch-item {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  padding: 16rpx 20rpx;
  background: var(--color-surface);
  border: 2rpx solid var(--color-divider);
  border-radius: var(--radius-md);
}
.pet-switch-item.active {
  border-color: var(--color-primary);
  background: var(--color-primary-light);
}
.pet-switch-avatar {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background: var(--color-background);
}
.pet-switch-name {
  max-width: 160rpx;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}
.pet-switch-item.active .pet-switch-name {
  color: var(--color-primary);
  font-weight: var(--font-weight-semibold);
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

/* ===== 宠物状态操作区（去世/意外/删除） ===== */
.profile-actions {
  margin-top: 8rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
  padding: 24rpx;
}
.status-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20rpx;
}
.status-label {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}
.status-badge {
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
  padding: 4rpx 20rpx;
  border-radius: 999rpx;
}
.status-badge.status-ACTIVE {
  color: var(--color-primary);
  background: var(--color-primary-light);
}
.status-badge.status-DIED {
  color: #8c8c8c;
  background: #f0f0f0;
}
.status-badge.status-ACCIDENT {
  color: #d46b08;
  background: #fff7e6;
}
.status-badge.status-MEDICAL {
  color: #096dd9;
  background: #e6f7ff;
}
.action-row {
  display: flex;
  gap: 16rpx;
}
.action-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  height: 80rpx;
  border-radius: var(--radius-pill);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
}
.action-btn.medical {
  color: #096dd9;
  border: 1rpx solid #91d5ff;
  background: #e6f7ff;
}
.action-btn.died {
  color: #8c8c8c;
  border: 1rpx solid #d9d9d9;
  background: #fafafa;
}
.action-btn.accident {
  color: #d46b08;
  border: 1rpx solid #ffd591;
  background: #fff7e6;
}
.action-btn.delete {
  color: #ffffff;
  background: var(--color-danger, #ff4d4f);
}

.bottom-spacer {
  /* 为底部固定保存条(16px上+88rpx按钮+16px下+安全区)预留空间,避免最后一项表单被遮挡 */
  height: calc(120rpx + env(safe-area-inset-bottom));
}

/* ===== 空档案引导 ===== */
.empty-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 200rpx;
}
.empty-icon {
  width: 152rpx;
  height: 152rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--color-primary-light);
  color: var(--color-primary);
  font-size: 72rpx;
}
.empty-title {
  margin-top: 36rpx;
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}
.empty-desc {
  margin-top: 12rpx;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}
.empty-btn {
  margin-top: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  padding: 22rpx 88rpx;
  border-radius: var(--radius-pill);
  background: var(--color-primary);
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: #ffffff;
}
.empty-btn-icon {
  font-size: var(--font-size-sm);
}

/* ===== 档案页「新增宠物」入口 ===== */
.add-another {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  margin-top: 8rpx;
  padding: 22rpx 0;
  border: 1rpx solid var(--color-primary);
  border-radius: var(--radius-pill);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-primary);
  background: var(--color-surface);
}
</style>
