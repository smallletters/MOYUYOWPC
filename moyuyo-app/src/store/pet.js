import { defineStore } from 'pinia'
import { petApi } from '@/api'
import { useUserStore } from '@/store/user'

// 护理摘要请求自增序号：loadCareSummary 竞态防护，仅最后一次请求的结果会写入 store
let careSummarySeq = 0

export const usePetStore = defineStore('pet', {
  state: () => ({
    pets: [],
    currentPet: null,
    growthRecords: [],
    reminders: [],
    careSummary: [],
    achievements: [],
    loading: false,
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
  },
})
