/**
 * 商品 / 分类状态管理
 */
import { defineStore } from 'pinia'
import { productApi } from '@/api'

export const useProductStore = defineStore('product', {
  state: () => ({
    categoryTree: [],        // 分类树
    homeBanners: [],         // 首页 Banner
    homeRecommend: [],       // 首页推荐流
    searchHistory: []        // 搜索历史
  }),

  actions: {
    /**
     * 加载分类树
     */
    async loadCategoryTree(force = false) {
      if (!force && this.categoryTree.length > 0) return this.categoryTree
      const list = await productApi.getCategoryList({ per_page: 100 })
      this.categoryTree = list
      return list
    },

    /**
     * 加载首页数据
     */
    async loadHomeData() {
      const [recommend, banners] = await Promise.all([
        productApi.getProductList({ per_page: 10, orderby: 'popularity' }),
        // Banner 数据由 WP 后台文章/CPT 管理，此处预留扩展
        Promise.resolve([])
      ])
      this.homeRecommend = recommend
      this.homeBanners = banners
      return { recommend, banners }
    },

    addSearchHistory(keyword) {
      if (!keyword) return
      const list = this.searchHistory.filter((k) => k !== keyword)
      list.unshift(keyword)
      this.searchHistory = list.slice(0, 10)
    },

    clearSearchHistory() {
      this.searchHistory = []
    }
  }
})
