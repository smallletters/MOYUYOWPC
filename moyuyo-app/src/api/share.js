import { post, get } from '@/utils/request'

// 分享商品埋点：用于任务中心"分享 1 个商品"每日任务
export function shareProduct() {
  return post('/api/v1/shares/product')
}

// 分享链接二维码：后端透传公共 QR API 返回 PNG（公开接口，匿名可访问）
// text: 要编码的字符串（商品详情 URL + 推荐人 ID）
// size: 像素边长（默认 240，范围 120~600）
export function getShareQrUrl(text, size = 240) {
  // 通过 query 拼接构造 URL；uni-app 在 H5 用 <img src>、在小程序用 wx.previewImage/App 用 uni.getImageInfo 都能直接加载
  return `/api/v1/shares/qr?text=${encodeURIComponent(text)}&size=${size}`
}

export default {
  shareProduct,
  getShareQrUrl,
}
