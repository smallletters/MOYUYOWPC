// 帮助中心 API
import { get, post } from '@/utils/request'

export function listCategories() {
  return get('/api/v1/help/categories')
}

export function listArticles(params = {}) {
  return get('/api/v1/help/articles', params)
}

export function articleDetail(id) {
  return get(`/api/v1/help/articles/${id}`)
}

export function helpful(id, helpful) {
  return post(`/api/v1/help/articles/${id}/helpful`, { helpful })
}

export default { listCategories, listArticles, articleDetail, helpful }