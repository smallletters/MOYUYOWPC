import { post } from '@/utils/request'

// 分享商品埋点：用于任务中心"分享 1 个商品"每日任务
export function shareProduct() {
  return post('/api/v1/shares/product')
}

export default {
  shareProduct,
}
