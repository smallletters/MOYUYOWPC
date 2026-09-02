import { defineStore } from 'pinia'
import { petApi } from '@/api'
import { useUserStore } from '@/store/user'

export const usePetStore = defineStore('pet', {
  state: () => ({
    pets: [],
    currentPet: null,
    growthRecords: [],
    reminders: [],
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
        if (!this.currentPet && this.pets.length > 0) {
          this.currentPet = this.pets[0]
        }
      } catch (e) {
        console.warn('[pet] loadPets failed', e)
        this.pets = []
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

    async loadReminders(petId) {
      try {
        this.reminders = await petApi.getReminders(petId)
      } catch (e) {
        this.reminders = []
      }
    },

    async updateReminder(petId, reminderId, data) {
      const updated = await petApi.updateReminder(petId, reminderId, data)
      const idx = this.reminders.findIndex((r) => r.id === reminderId)
      if (idx >= 0) this.reminders[idx] = updated
      return updated
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
        this.loadAchievements(petId),
      ])
    },
  },
})
