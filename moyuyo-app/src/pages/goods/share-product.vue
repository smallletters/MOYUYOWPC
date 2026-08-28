<template>
  <view class="share-product">
    <!-- 顶部导航栏 -->
    <view class="header">
      <view class="header-btn" @tap="goBack">
        <text class="back-icon"><text class="luc luc-arrow-left" /></text>
      </view>
      <text class="header-title">分享商品</text>
      <view class="header-btn" @tap="handleClose">
        <text class="close-icon"><text class="luc luc-x" /></text>
      </view>
    </view>

    <scroll-view scroll-y class="scroll">
      <!-- 分享预览卡片 -->
      <view class="preview-section">
        <view class="share-card">
          <view class="product-image-area">
            <image
              v-if="productImage"
              :src="productImage"
              class="product-main-image"
              mode="aspectFill"
            />
            <view v-else class="product-main-image product-main-image--placeholder">
              <text class="luc luc-image" />
            </view>
            <view class="brand-watermark">MOYUYO</view>
          </view>
          <view class="product-body">
            <text class="share-product-name">{{ product.name }}</text>
            <text v-if="product.shortDetail" class="share-product-desc">
              {{ product.shortDetail }}
            </text>
            <view class="share-price-row">
              <text class="share-price">${{ formatPrice(product.price) }}</text>
              <text
                v-if="
                  product.originalPrice && Number(product.originalPrice) > Number(product.price)
                "
                class="share-original-price"
              >
                ${{ formatPrice(product.originalPrice) }}
              </text>
            </view>
            <view class="share-divider" />
            <view class="share-qr-row">
              <image
                v-if="qrUrl"
                :src="qrUrl"
                class="qr-code-img"
                mode="aspectFit"
                @tap="previewQr"
              />
              <view v-else class="qr-code qr-code--loading">
                <text class="loading-text">二维码加载中...</text>
              </view>
              <view class="qr-info">
                <text class="qr-label">扫码查看详情</text>
                <view class="qr-recommender">
                  <view class="recommender-avatar">
                    <text><text class="luc luc-user" /></text>
                  </view>
                  <text class="recommender-name">推荐人：{{ recommender }}</text>
                </view>
                <text class="qr-source">来自 MOYUYO 宠物商城</text>
              </view>
            </view>
          </view>
        </view>
      </view>

      <!-- 分享链接（可复制） -->
      <view class="section">
        <text class="section-title">分享链接</text>
        <view class="link-card">
          <text class="link-text" :selectable="true">{{ shareLink }}</text>
          <view class="link-copy" @tap="onCopyLink">
            <text class="luc luc-clipboard-list" />
            <text class="link-copy-text">复制</text>
          </view>
        </view>
      </view>

      <!-- 分享渠道 -->
      <view class="section">
        <text class="section-title">分享到</text>
        <view class="share-channels">
          <view
            v-for="channel in channels"
            :key="channel.id"
            class="channel-item"
            @tap="handleShare(channel)"
          >
            <view class="channel-icon" :style="{ background: channel.bg }">
              <text v-if="channel.brand" class="channel-emoji channel-brand">
                {{ channel.brand }}
              </text>
              <text v-else class="channel-emoji luc" :class="$luc(channel.icon)" />
            </view>
            <text class="channel-name">{{ channel.name }}</text>
          </view>
        </view>
      </view>

      <!-- 分享设置 -->
      <view class="section">
        <view class="settings-card">
          <view
            v-for="setting in shareSettings"
            :key="setting.id"
            class="setting-row"
            :style="{ borderBottom: !setting.last ? '2rpx solid var(--border)' : 'none' }"
          >
            <text class="setting-label">{{ setting.label }}</text>
            <view
              class="toggle-track"
              :class="{ active: setting.value }"
              @tap="toggleSetting(setting.id)"
            >
              <view
                class="toggle-thumb"
                :style="{ transform: setting.value ? 'translateX(40rpx)' : 'translateX(0)' }"
              />
            </view>
          </view>
        </view>
      </view>

      <!-- 自定义消息 -->
      <view class="section">
        <text class="section-title">添加分享文案（可选）</text>
        <view class="message-input-wrapper">
          <textarea
            v-model="customMessage"
            class="message-input"
            placeholder="写一段推荐语..."
            maxlength="200"
          />
          <text class="char-count">{{ customMessage.length }}/200</text>
        </view>
      </view>

      <!-- 生成分享图片按钮 -->
      <view class="generate-btn" @tap="handleGenerateImage">
        <text>
          <text class="luc luc-arrow-up" />
          生成分享图片
        </text>
      </view>

      <view class="bottom-spacer" />
    </scroll-view>

    <!-- 隐藏的画布，用于生成分享图（不展示在页面上） -->
    <canvas
      id="sharePosterCanvas"
      canvas-id="sharePosterCanvas"
      class="share-canvas"
      :style="{ width: canvasWidth + 'px', height: canvasHeight + 'px' }"
    />
  </view>
</template>

<script>
import { productApi } from '@/api'
import { shareApi } from '@/api'
import { getStorage, STORAGE_KEYS } from '@/utils/storage'

export default {
  data() {
    return {
      productId: null,
      product: {
        name: '宠物商品',
        shortDetail: '',
        price: 0,
        originalPrice: 0,
      },
      productImage: '',
      recommender: 'MOYUYO 用户',
      customMessage: '',
      qrUrl: '',
      shareLink: '',
      // 画布尺寸（rpx 概念此处省略，统一用 px）
      canvasWidth: 0,
      canvasHeight: 0,
      channels: [
        { id: 'email', name: 'Email', icon: 'mail', bg: 'var(--background-200)' },
        {
          id: 'messages',
          name: '短信',
          icon: 'message-circle',
          bg: 'var(--state-success-surface)',
        },
        { id: 'whatsapp', name: 'WhatsApp', icon: 'send', bg: 'var(--state-success-surface)' },
        { id: 'twitter', name: 'X', brand: '𝕏', bg: 'var(--background-200)' },
        { id: 'copy', name: '复制链接', icon: 'link', bg: 'var(--brand-50)' },
        { id: 'facebook', name: 'Facebook', brand: 'f', bg: 'var(--background-200)' },
      ],
      shareSettings: [
        { id: 'caption', label: '添加分享文案', value: false, last: false },
        { id: 'price', label: '显示价格', value: true, last: false },
        { id: 'watermark', label: '添加水印', value: true, last: true },
      ],
    }
  },

  onLoad(query) {
    // 从 query.id 读取商品 id；未传则保持占位
    this.productId = query && query.id ? query.id : null
    // 推荐人：取本地用户昵称，未登录时显示 MOYUYO 用户
    const userInfo = getStorage(STORAGE_KEYS.USER_INFO)
    this.recommender = (userInfo && userInfo.nickname) || 'MOYUYO 用户'
    // 构造分享链接（用前端绝对域名；H5 与 App 都能识别）
    this.shareLink = this.buildShareLink()
    // 二维码图片 URL：直接指向后端 QR 接口
    this.qrUrl = shareApi.getShareQrUrl(this.shareLink, 200)
    // 加载真实商品
    if (this.productId) {
      this.loadProduct()
    } else {
      uni.showToast({ title: '缺少商品 id', icon: 'none' })
    }
  },

  methods: {
    goBack() {
      uni.navigateBack()
    },
    handleClose() {
      uni.navigateBack()
    },

    /**
     * 构造分享链接：H5 用当前 origin，App/小程序用 VITE_PAY_RETURN_BASE 兜底
     */
    buildShareLink() {
      // #ifdef H5
      if (typeof window !== 'undefined' && window.location) {
        return `${window.location.origin}/pages/goods/detail?id=${this.productId || ''}`
      }
      // #endif
      // 原生端：必须显式配置 payReturnBase；未配置时回退到 #/detail?id= 前缀的 SPA 路由
      return `/pages/goods/detail?id=${this.productId || ''}`
    },

    async loadProduct() {
      try {
        const data = await productApi.getProductDetail(this.productId)
        if (!data) return
        this.product = {
          name: data.name || '宠物商品',
          shortDetail: data.shortDetail || '',
          price: parseFloat(data.price) || 0,
          originalPrice: parseFloat(data.originalPrice) || 0,
        }
        this.productImage =
          (data.images && data.images[0] && (data.images[0].url || data.images[0])) ||
          data.mainImage ||
          ''
      } catch (e) {
        console.warn('[share-product] load product failed', e)
      }
    },

    formatPrice(v) {
      const n = Number(v) || 0
      return n.toFixed(2)
    },

    toggleSetting(id) {
      const setting = this.shareSettings.find((s) => s.id === id)
      if (setting) setting.value = !setting.value
    },

    /**
     * 通用入口：触发后端埋点 + 按渠道执行真实分享动作
     */
    handleShare(channel) {
      // 1. 触发任务埋点（分享 1 个商品每日任务 +1）；失败不阻塞分享
      shareApi.shareProduct().catch(() => {})

      // 2. 按渠道分发
      const text =
        this.customMessage && this.shareSettings.find((s) => s.id === 'caption')?.value
          ? this.customMessage
          : `${this.product.name} - ${this.shareLink}`
      switch (channel.id) {
        case 'copy':
          this.onCopyLink()
          break
        case 'email':
          this.shareByEmail(text)
          break
        case 'messages':
          this.shareBySms(text)
          break
        case 'whatsapp':
          this.shareByWhatsApp(text)
          break
        case 'twitter':
          this.shareByTwitter(text)
          break
        case 'facebook':
          this.shareByFacebook()
          break
        default:
          uni.showToast({ title: `已分享到 ${channel.name}`, icon: 'success' })
      }
    },

    onCopyLink() {
      if (!this.shareLink) return
      uni.setClipboardData({
        data: this.shareLink,
        success: () => uni.showToast({ title: '链接已复制', icon: 'success' }),
        fail: () => uni.showToast({ title: '复制失败', icon: 'none' }),
      })
    },

    previewQr() {
      if (!this.qrUrl) return
      // #ifdef MP-WEIXIN
      uni.previewImage({ urls: [this.qrUrl] })
      // #endif
      // #ifdef H5
      if (typeof window !== 'undefined') window.open(this.qrUrl, '_blank')
      // #endif
      // #ifdef APP-PLUS
      uni.previewImage({ urls: [this.qrUrl] })
      // #endif
    },

    shareByEmail(text) {
      const subject = encodeURIComponent(`推荐：${this.product.name}`)
      const body = encodeURIComponent(`${text}\n\n${this.shareLink}`)
      const href = `mailto:?subject=${subject}&body=${body}`
      this.openExternal(href)
    },

    shareBySms(text) {
      const body = encodeURIComponent(`${text} ${this.shareLink}`)
      const href = `sms:?body=${body}`
      this.openExternal(href)
    },

    shareByWhatsApp(text) {
      const waUrl = 'https://wa.me/?text=' + encodeURIComponent(text)
      this.openExternal(waUrl)
    },

    shareByTwitter(text) {
      const url = 'https://twitter.com/intent/tweet?text=' + encodeURIComponent(text)
      this.openExternal(url)
    },

    shareByFacebook() {
      const url =
        'https://www.facebook.com/sharer/sharer.php?u=' + encodeURIComponent(this.shareLink)
      this.openExternal(url)
    },

    /**
     * 平台感知打开：
     *  - H5：window.open 打开新标签（避免拦截）
     *  - App：plus.runtime.openURL 系统浏览器
     *  - 小程序：copyAndHint 兜底（小程序无法打开外链）
     */
    openExternal(href) {
      // #ifdef H5
      if (typeof window !== 'undefined') {
        window.open(href, '_blank', 'noopener,noreferrer')
        return
      }
      // #endif
      // #ifdef APP-PLUS
      try {
        const plus = (typeof globalThis !== 'undefined' && globalThis.plus) || undefined
        if (plus && plus.runtime && plus.runtime.openURL) {
          plus.runtime.openURL(href, (err) => {
            uni.showToast({ title: '打开失败：' + ((err && err.message) || ''), icon: 'none' })
          })
          return
        }
      } catch (e) {
        /* fallthrough */
      }
      // #endif
      // 小程序：fallback 复制链接
      uni.setClipboardData({
        data: this.shareLink,
        success: () =>
          uni.showToast({ title: '已复制链接，请手动打开对应 App 粘贴', icon: 'none' }),
      })
    },

    /**
     * 生成分享图：用 canvas 绘制 share-card 视图（H5 走 2D canvas，App/小程序走 uni.canvasToTempFilePath）
     * 这里统一使用 uni.createCanvasContext 抽象，避免逐平台适配。
     */
    handleGenerateImage() {
      if (!this.productId) {
        uni.showToast({ title: '缺少商品 id', icon: 'none' })
        return
      }
      // 初始化画布尺寸（H5 用 750x1000，App/小程序按系统 dpi 缩放）
      const width = 750
      const height = 1000
      this.canvasWidth = width
      this.canvasHeight = height
      uni.showLoading({ title: '生成中...', mask: true })
      // 等下一帧 canvas style 生效后，先预加载图片（H5 canvas 不允许直接画跨域图）再绘制
      this.$nextTick(() => this.prepareImages(width, height))
    },

    /**
     * 把商品图和二维码通过 uni.getImageInfo 转为本地临时路径，避免 H5 canvas 跨域污染。
     * 任一加载失败时该图片降级为空白占位，整体流程仍可完成（避免阻塞分享）。
     */
    async prepareImages(w, h) {
      const tasks = []
      if (this.productImage) {
        tasks.push(
          this.resolveLocalPath(this.productImage)
            .then((p) => ({ key: 'product', path: p }))
            .catch(() => ({ key: 'product', path: '' })),
        )
      } else {
        tasks.push(Promise.resolve({ key: 'product', path: '' }))
      }
      if (this.qrUrl) {
        tasks.push(
          this.resolveLocalPath(this.qrUrl)
            .then((p) => ({ key: 'qr', path: p }))
            .catch(() => ({ key: 'qr', path: '' })),
        )
      } else {
        tasks.push(Promise.resolve({ key: 'qr', path: '' }))
      }
      const results = await Promise.all(tasks)
      const localMap = {}
      for (const r of results) localMap[r.key] = r.path
      this.drawPoster(w, h, localMap)
    },

    /**
     * uni.getImageInfo 包装：成功返回本地路径；H5 端把跨域 URL 转 dataURL 以绕过 canvas tainted。
     */
    resolveLocalPath(src) {
      return new Promise((resolve, reject) => {
        uni.getImageInfo({
          src,
          success: (res) => resolve(res.path),
          fail: (err) => {
            // #ifdef H5
            // 兜底：用 fetch + canvas.toDataURL 强行转 dataURL，绕过跨域污染
            if (typeof window !== 'undefined' && window.fetch) {
              window
                .fetch(src)
                .then((r) => r.blob())
                .then((blob) => {
                  const reader = new FileReader()
                  reader.onload = () => resolve(reader.result)
                  reader.onerror = () => reject(reader.error)
                  reader.readAsDataURL(blob)
                })
                .catch(reject)
              return
            }
            // #endif
            reject(err)
          },
        })
      })
    },

    drawPoster(w, h, localMap = {}) {
      let ctx
      // #ifdef MP-WEIXIN
      ctx = uni.createCanvasContext('sharePosterCanvas', this)
      // #endif
      // #ifdef H5 || APP-PLUS
      ctx = uni.createCanvasContext('sharePosterCanvas')
      // #endif

      // 背景
      ctx.setFillStyle('#FFFFFF')
      ctx.fillRect(0, 0, w, h)

      // 顶部商品图区域（占位框，必要时绘制图片）
      const imgX = 40,
        imgY = 40,
        imgW = w - 80,
        imgH = 360
      if (localMap.product) {
        // 居中裁剪绘制：drawImage 支持 sx/sy/sWidth/sHeight 实现图片自适应
        ctx.drawImage(localMap.product, imgX, imgY, imgW, imgH)
      } else {
        ctx.setFillStyle('#F5F5F5')
        ctx.fillRect(imgX, imgY, imgW, imgH)
        ctx.setFillStyle('#BBBBBB')
        ctx.setFontSize(22)
        ctx.fillText('商品图片加载失败', imgX + 220, imgY + 180)
      }

      // 商品名称
      ctx.setFillStyle('#111111')
      ctx.setFontSize(28)
      ctx.fillText(this.product.name || '', 40, 450)

      // 描述（截断）
      if (this.product.shortDetail) {
        ctx.setFillStyle('#666666')
        ctx.setFontSize(20)
        ctx.fillText(this.product.shortDetail.slice(0, 30), 40, 490)
      }

      // 价格
      ctx.setFillStyle('#E53935')
      ctx.setFontSize(36)
      ctx.fillText(`$${this.formatPrice(this.product.price)}`, 40, 560)

      // 分割线
      ctx.setStrokeStyle('#EEEEEE')
      ctx.beginPath()
      ctx.moveTo(40, 610)
      ctx.lineTo(w - 40, 610)
      ctx.stroke()

      // 二维码区域
      const qrSize = 180
      const qrX = 40
      const qrY = 650
      if (localMap.qr) {
        ctx.drawImage(localMap.qr, qrX, qrY, qrSize, qrSize)
      } else {
        ctx.setFillStyle('#F5F5F5')
        ctx.fillRect(qrX, qrY, qrSize, qrSize)
        ctx.setStrokeStyle('#DDDDDD')
        ctx.strokeRect(qrX, qrY, qrSize, qrSize)
        ctx.setFillStyle('#999999')
        ctx.setFontSize(20)
        ctx.fillText('二维码加载失败', qrX + 30, qrY + 100)
      }

      // "扫码查看商品详情" + 链接（放在二维码右侧）
      const textX = qrX + qrSize + 24
      ctx.setFillStyle('#111111')
      ctx.setFontSize(24)
      ctx.fillText('扫码查看商品详情', textX, qrY + 36)
      ctx.setFillStyle('#666666')
      ctx.setFontSize(16)
      // 链接最多 60 字符；超出截断
      const linkText =
        this.shareLink.length > 60 ? this.shareLink.slice(0, 60) + '…' : this.shareLink
      ctx.fillText(linkText, textX, qrY + 70)

      // 品牌水印
      ctx.setFillStyle('#999999')
      ctx.setFontSize(18)
      ctx.fillText('— MOYUYO 宠物商城 —', 40, h - 30)

      ctx.draw(false, () => {
        uni.hideLoading()
        // 绘制完成后导出图片
        uni.canvasToTempFilePath(
          {
            canvasId: 'sharePosterCanvas',
            success: (res) => {
              this.saveOrShareImage(res.tempFilePath)
            },
            fail: (err) => {
              console.warn('[share] canvasToTempFilePath failed', err)
              uni.showToast({ title: '生成失败', icon: 'none' })
            },
          },
          this,
        )
      })
    },

    /**
     * 保存到相册（App/小程序）；H5 走下载
     */
    saveOrShareImage(filePath) {
      // #ifdef H5
      // H5：通过 <a download> 触发下载
      try {
        const a = document.createElement('a')
        a.href = filePath
        a.download = `moyuyo-share-${this.productId || 'product'}.png`
        document.body.appendChild(a)
        a.click()
        document.body.removeChild(a)
        uni.showToast({ title: '图片已下载', icon: 'success' })
      } catch (e) {
        uni.showToast({ title: '下载失败', icon: 'none' })
      }
      // #endif
      // #ifdef APP-PLUS || MP-WEIXIN
      uni.saveImageToPhotosAlbum({
        filePath,
        success: () => uni.showToast({ title: '已保存到相册', icon: 'success' }),
        fail: () => uni.showToast({ title: '保存失败，请检查权限', icon: 'none' }),
      })
      // #endif
    },
  },
}
</script>

<style scoped lang="scss">
.share-product {
  min-height: 100vh;
  background: var(--background);
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 88rpx;
  padding: 0 30rpx;
  background: var(--background);
  border-bottom: 2rpx solid var(--border);
  position: sticky;
  top: 0;
  z-index: 30;
}
.header-btn {
  width: 72rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.back-icon,
.close-icon {
  font-size: 40rpx;
  color: var(--primary);
}
.header-title {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--foreground);
}
.scroll {
  padding-bottom: 40rpx;
}
.preview-section {
  display: flex;
  justify-content: center;
  padding: 40rpx 30rpx 30rpx;
}
.share-card {
  width: 640rpx;
  border-radius: 24rpx;
  overflow: hidden;
  background: var(--background-50);
  box-shadow: var(--shadow-lg);
}
.product-image-area {
  position: relative;
  overflow: hidden;
  height: 420rpx;
  background: linear-gradient(135deg, var(--brand-50), var(--brand-100));
}
.product-main-image {
  width: 100%;
  height: 420rpx;
}
.product-main-image--placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 80rpx;
  color: var(--text-300);
}
.brand-watermark {
  position: absolute;
  bottom: 24rpx;
  right: 24rpx;
  font-size: 20rpx;
  font-weight: 700;
  letter-spacing: 0.1em;
  color: var(--text-800);
  opacity: 0.12;
  pointer-events: none;
}
.product-body {
  padding: 30rpx;
  border-top: 2rpx solid var(--border);
}
.share-product-name {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--text-800);
  display: block;
}
.share-product-desc {
  font-size: 24rpx;
  color: var(--text-400);
  display: block;
  margin-top: 8rpx;
}
.share-price-row {
  display: flex;
  align-items: baseline;
  gap: 12rpx;
  margin-top: 16rpx;
}
.share-price {
  font-size: 36rpx;
  font-weight: 700;
  color: var(--primary);
}
.share-original-price {
  font-size: 24rpx;
  text-decoration: line-through;
  color: var(--text-300);
}
.share-divider {
  height: 2rpx;
  background: var(--border);
  margin: 24rpx 0;
}
.share-qr-row {
  display: flex;
  align-items: center;
  gap: 24rpx;
}
.qr-code-img,
.qr-code {
  width: 160rpx;
  height: 160rpx;
  border-radius: 8rpx;
  background: var(--background-50);
  flex-shrink: 0;
}
.qr-code--loading {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20rpx;
  color: var(--text-300);
  border: 2rpx dashed var(--border);
}
.loading-text {
  font-size: 18rpx;
  color: var(--text-400);
}
.qr-info {
  flex: 1;
  min-width: 0;
}
.qr-label {
  font-size: 24rpx;
  font-weight: 500;
  color: var(--text-600);
  display: block;
}
.qr-recommender {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-top: 12rpx;
}
.recommender-avatar {
  width: 32rpx;
  height: 32rpx;
  border-radius: 50%;
  background: var(--background-200);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20rpx;
}
.recommender-name {
  font-size: 24rpx;
  color: var(--text-400);
}
.qr-source {
  font-size: 20rpx;
  color: var(--text-400);
  display: block;
  margin-top: 8rpx;
}

/* 分享链接卡片 */
.link-card {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 20rpx 24rpx;
  border-radius: 16rpx;
  background: var(--card);
  border: 2rpx solid var(--border);
}
.link-text {
  flex: 1;
  font-size: 22rpx;
  color: var(--text-600);
  word-break: break-all;
}
.link-copy {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 8rpx 16rpx;
  border-radius: 999rpx;
  background: var(--brand-50);
  color: var(--primary);
  font-size: 22rpx;
  flex-shrink: 0;
}
.link-copy-text {
  font-size: 22rpx;
}

.section {
  padding: 0 30rpx 30rpx;
}
.section-title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--foreground);
  display: block;
  margin-bottom: 20rpx;
}
.share-channels {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24rpx;
}
.channel-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
}
.channel-icon {
  width: 112rpx;
  height: 112rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}
.channel-emoji {
  font-size: 48rpx;
}
.channel-brand {
  font-size: 40rpx;
  font-weight: 700;
  color: var(--text-900);
}
.channel-name {
  font-size: 22rpx;
  font-weight: 500;
  color: var(--text-500);
}
.settings-card {
  border-radius: 24rpx;
  overflow: hidden;
  background: var(--card);
  border: 2rpx solid var(--border);
}
.setting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 30rpx;
  height: 100rpx;
}
.setting-label {
  font-size: 28rpx;
  color: var(--foreground);
}
.toggle-track {
  position: relative;
  width: 102rpx;
  height: 62rpx;
  border-radius: 999rpx;
  background: var(--background-300);
  transition: background-color 0.2s;
  flex-shrink: 0;
}
.toggle-track.active {
  background: var(--primary);
}
.toggle-thumb {
  position: absolute;
  top: 4rpx;
  left: 4rpx;
  width: 54rpx;
  height: 54rpx;
  border-radius: 50%;
  background: var(--background-50);
  box-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.15);
  transition: transform 0.2s;
}
.message-input-wrapper {
  position: relative;
}
.message-input {
  width: 100%;
  height: 160rpx;
  border-radius: 24rpx;
  padding: 24rpx;
  background: var(--background-200);
  font-size: 28rpx;
  color: var(--foreground);
  border: none;
  outline: none;
  box-sizing: border-box;
  resize: none;
}
.char-count {
  position: absolute;
  bottom: 16rpx;
  right: 24rpx;
  font-size: 20rpx;
  color: var(--text-400);
}
.generate-btn {
  margin: 0 30rpx;
  height: 104rpx;
  border-radius: 999rpx;
  background: var(--primary);
  color: var(--primary-foreground);
  font-size: 32rpx;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
  box-shadow: var(--shadow-sm);
}
.bottom-spacer {
  height: 40rpx;
}

/* 隐藏画布：定位到屏幕外，避免占用可视空间 */
.share-canvas {
  position: fixed;
  top: -9999rpx;
  left: -9999rpx;
  pointer-events: none;
}
</style>
