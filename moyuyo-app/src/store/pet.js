import { defineStore } from 'pinia'
import { petApi, petDresserApi } from '@/api'
import { useUserStore } from '@/store/user'

// 护理摘要请求自增序号：loadCareSummary 竞态防护，仅最后一次请求的结果会写入 store
let careSummarySeq = 0

// 装扮持久化 key(按账号隔离;未登录时不持久化)
const DRESS_STORAGE_KEY = 'moyuyo:dress:'

// 动态引入 IDB 删除工具,避免小程序端 import 报错
function safeIdbDeleteWithURL(key) {
  try {
    // idb-storage 内部用 ES Module 动态导入;此处用 require 在 build 时不生效,改用动态 import
    // Vue2 + uni-app 编译时,这里会保持为 dynamic import 不被 tree-shake
    // eslint-disable-next-line
    return import('@/utils/idb-storage').then(({ idbDeleteWithURL }) => idbDeleteWithURL(key))
  } catch (e) {
    // 非 H5 环境(小程序)直接忽略
    return Promise.resolve(false)
  }
}

export const usePetStore = defineStore('pet', {
  state: () => ({
    pets: [],
    currentPet: null,
    growthRecords: [],
    reminders: [],
    careSummary: [],
    achievements: [],
    loading: false,
    // 装扮数据:自定义上传的形象 + 选中的形象/背景
    // customAvatars: [{ id, name, src(base64/路径), type: 'image'|'model3d', format? }]
    customAvatars: [],
    selectedAvatarId: '',
    selectedBgId: '',
  }),

  getters: {
    hasPets: (state) => state.pets.length > 0,
    activePet: (state) => state.currentPet || state.pets[0] || null,
  },

  actions: {
    async loadPets() {
      // 未登录直接返回,避免每个用到 petStore.loadPets 的页面都打 401 噪音
      const userStore = useUserStore()
      if (!userStore.isLoggedIn) {
        this.pets = []
        this.currentPet = null
        return
      }
      this.loading = true
      try {
        this.pets = await petApi.getPets()
        // 刷新后若当前选中宠物已不在列表中（如在别处被删除），回退到第一只
        if (this.currentPet && !this.pets.some((p) => p.id === this.currentPet.id)) {
          this.currentPet = this.pets[0] || null
        }
        if (!this.currentPet && this.pets.length > 0) {
          this.currentPet = this.pets[0]
        }
      } catch (e) {
        console.warn('[pet] loadPets failed', e)
        this.pets = []
        // 拉取失败时同时清空当前选中与护理摘要，避免列表为空但场景/护理卡仍指向旧宠物（幽灵宠物）
        this.currentPet = null
        this.careSummary = []
      } finally {
        this.loading = false
      }
    },

    async loadPetDetail(id) {
      try {
        this.currentPet = await petApi.getPetDetail(id)
        return this.currentPet
      } catch (e) {
        console.warn('[pet] loadPetDetail failed', e)
        return null
      }
    },

    async createPet(data) {
      const pet = await petApi.createPet(data)
      this.pets.push(pet)
      if (this.pets.length === 1) this.currentPet = pet
      return pet
    },

    async updatePet(id, data) {
      const updated = await petApi.updatePet(id, data)
      const idx = this.pets.findIndex((p) => p.id === id)
      if (idx >= 0) this.pets[idx] = updated
      if (this.currentPet?.id === id) this.currentPet = updated
      return updated
    },

    async deletePet(id) {
      await petApi.deletePet(id)
      this.pets = this.pets.filter((p) => p.id !== id)
      if (this.currentPet?.id === id) {
        this.currentPet = this.pets[0] || null
      }
    },

    async loadGrowthRecords(petId) {
      try {
        this.growthRecords = await petApi.getGrowthRecords(petId)
      } catch (e) {
        this.growthRecords = []
      }
    },

    async createGrowthRecord(petId, data) {
      const record = await petApi.createGrowthRecord(petId, data)
      this.growthRecords.unshift(record)
      return record
    },

    async deleteGrowthRecord(petId, recordId) {
      await petApi.deleteGrowthRecord(petId, recordId)
      // 本地同步移除，避免整页刷新
      this.growthRecords = this.growthRecords.filter((r) => r.id !== recordId)
    },

    async loadReminders(petId) {
      try {
        this.reminders = await petApi.getReminders(petId)
      } catch (e) {
        this.reminders = []
      }
    },

    async updateReminder(petId, reminderId, data) {
      // reminderId 为空时传 0，走后端按 petId+type 的新增/upsert 契约
      const updated = await petApi.updateReminder(petId, reminderId || 0, data)
      const idx = this.reminders.findIndex((r) => r.id === updated.id)
      if (idx >= 0) {
        this.reminders[idx] = updated
        return updated
      }
      // 新增返回：按类型合并，避免同类型出现重复条目
      const typeIdx = this.reminders.findIndex((r) => r.reminderType === updated.reminderType)
      if (typeIdx >= 0) {
        this.reminders[typeIdx] = updated
      } else {
        this.reminders.push(updated)
      }
      return updated
    },

    async loadCareSummary(petId) {
      const seq = ++careSummarySeq
      try {
        const data = (await petApi.getCareSummary(petId)) || []
        // 仅当本次请求仍是最新一次时才写入，避免快速切换宠物时旧响应覆盖新数据
        if (seq === careSummarySeq) this.careSummary = data
      } catch (e) {
        // 仅最后一次请求失败才清空，避免旧请求的错误清掉新宠物已加载的数据
        if (seq === careSummarySeq) this.careSummary = []
      }
    },

    async loadAchievements(petId) {
      try {
        this.achievements = await petApi.getAchievements(petId)
      } catch (e) {
        this.achievements = []
      }
    },

    async loadAllPetData(petId) {
      await Promise.all([
        this.loadGrowthRecords(petId),
        this.loadReminders(petId),
        this.loadCareSummary(petId),
        this.loadAchievements(petId),
      ])
    },

    // ============== 装扮数据持久化 ==============
    // 按 userId 隔离;未登录时使用 'local' 段,避免切换账号时装扮数据串台
    _dressKey() {
      const u = useUserStore()
      const uid = u?.userInfo?.id || 'local'
      return DRESS_STORAGE_KEY + uid
    },

    // 从本地存储加载装扮数据,失败时静默忽略
    // 同时从后端拉取用户自定义形象(持久化在 mo_pet_outfit,APP 更新后本地数据丢失可从此恢复)
    async loadDress() {
      // 1. 先加载本地缓存(localStorage + IDB 中的 3D 模型)
      try {
        const raw = uni.getStorageSync(this._dressKey())
        if (raw) {
          const data = typeof raw === 'string' ? JSON.parse(raw) : raw
          this.customAvatars = Array.isArray(data.customAvatars) ? data.customAvatars : []
          this.selectedAvatarId = data.selectedAvatarId || ''
          this.selectedBgId = data.selectedBgId || ''
        }
      } catch (e) {
        console.warn('[pet] loadDress local failed', e)
      }
      // 2. 从后端拉取所有宠物的自定义形象并合并
      //    (customAvatars 在前端是全局共享的,但后端按 pet_id 隔离,需遍历所有宠物拉取)
      const pets = this.pets.length > 0 ? this.pets : this.currentPet ? [this.currentPet] : []
      if (pets.length === 0) return
      try {
        const allServer = []
        for (const p of pets) {
          if (!p?.id) continue
          const list = (await petDresserApi.getPetOutfits(p.id)) || []
          // 仅用户自定义形象(userId 非空)参与合并,系统装扮由弹窗内置常量展示
          list.filter((o) => o.userId != null).forEach((o) => allServer.push(o))
        }
        // 去重:已存在 outfitId 或相同 src(图片 URL)的不再重复加入
        //   - outfitId:正常流程本地已记录后端 id
        //   - src:兜底崩溃场景(本地已存但 outfitId 未及回写)
        const localOutfitIds = new Set(
          this.customAvatars.map((a) => a.outfitId).filter((x) => x != null),
        )
        const localSrcs = new Set(this.customAvatars.map((a) => a.src).filter((x) => x != null))
        allServer.forEach((o) => {
          if (localOutfitIds.has(o.id)) return
          if (o.imageUrl && localSrcs.has(o.imageUrl)) return
          this.customAvatars.push({
            id: 'cu-svr-' + o.id,
            outfitId: o.id, // 后端 id,删除时用
            outfitPetId: o.petId, // 装扮所属宠物 id,删除时用(与后端 pet_id 对齐)
            name: o.name || '自定义形象',
            src: o.imageUrl,
            type: 'image',
          })
        })
        this._saveDress()
      } catch (e) {
        // 后端拉取失败不影响本地数据展示
        console.warn('[pet] loadDress from server failed', e)
      }
    },

    // 持久化到本地(同步)
    _saveDress() {
      try {
        uni.setStorageSync(this._dressKey(), {
          customAvatars: this.customAvatars,
          selectedAvatarId: this.selectedAvatarId,
          selectedBgId: this.selectedBgId,
        })
      } catch (e) {
        // 配额超限等场景;提示用户清理
        console.warn('[pet] saveDress failed', e)
        uni.showToast({ title: '存储空间不足,请删除部分装扮', icon: 'none' })
      }
    },

    // 添加自定义形象(由装扮弹窗 emit 调用)
    // 图片类型会同步写入后端 mo_pet_outfit(user_id=当前用户),确保 APP 更新不丢失
    addCustomAvatar(item) {
      this.customAvatars.push(item)
      this._saveDress()
      // 图片类型:把装扮记录持久化到后端
      if (item.type === 'image') {
        const pet = this.currentPet || this.pets[0]
        if (pet?.id) {
          // 记录装扮所属宠物 id,后续删除时用(避免切换宠物后因 petId 不匹配被后端拒绝)
          item.outfitPetId = pet.id
          petDresserApi
            .uploadOutfit(pet.id, {
              category: 'custom',
              name: item.name || '自定义形象',
              imageUrl: item.src,
            })
            .then((created) => {
              // 记录后端返回的 id,便于后续删除
              if (created?.id) {
                item.outfitId = created.id
                this._saveDress()
              }
            })
            .catch((e) => {
              console.warn('[pet] 上传装扮到后端失败', e)
              // 后端写入失败:本地仍可临时使用,但 APP 更新后会丢失,需提示用户
              uni.showToast({
                title: '上传到服务器失败,更新后可能丢失',
                icon: 'none',
                duration: 3000,
              })
            })
        }
      }
      return true
    },

    // 删除自定义形象(同步清理 IDB 中的 3D 模型 blob + 后端装扮记录)
    removeCustomAvatar(id) {
      const target = this.customAvatars.find((a) => a.id === id)
      this.customAvatars = this.customAvatars.filter((a) => a.id !== id)
      if (this.selectedAvatarId === id) this.selectedAvatarId = ''
      this._saveDress()
      // 同步删除后端装扮记录:使用上传时记录的 outfitPetId(而非当前宠物),
      // 避免用户切换宠物后因 petId 不匹配被后端拒绝
      if (target?.outfitId && target.outfitPetId) {
        petDresserApi.deleteOutfit(target.outfitPetId, target.outfitId).catch((e) => {
          console.warn('[pet] 删除后端装扮失败', e)
        })
      }
      // 如果是 3D 模型且 storeKey 存在,异步清理 IDB 数据(失败不影响主流程)
      if (target && target.type === 'model3d' && target.storeKey) {
        safeIdbDeleteWithURL(target.storeKey).catch((e) =>
          console.warn('[pet] 删除 IDB 模型失败', e),
        )
      }
    },

    // 设置选中的形象
    setSelectedAvatar(id) {
      this.selectedAvatarId = id || ''
      this._saveDress()
    },

    // 设置选中的背景
    setSelectedBg(id) {
      this.selectedBgId = id || ''
      this._saveDress()
    },
  },
})
