<template>
  <view class="terms-doc">
    <view class="header">
      <view class="header-btn" @click="goBack">
        <text class="back-icon"><text class="luc luc-arrow-left" /></text>
      </view>
      <text class="header-title">{{ document.title }}</text>
      <view class="header-btn" />
    </view>

    <scroll-view class="content" scroll-y>
      <view class="meta">
        <text class="meta-text">最近更新：{{ document.updatedAt }}</text>
      </view>
      <view class="body">
        <text v-for="(line, idx) in document.body" :key="idx" class="body-line">
          {{ line }}
        </text>
      </view>
      <view class="footer">
        <text class="footer-text">本协议由 MOYUYO ATELIER 制定并保留最终解释权</text>
      </view>
    </scroll-view>
  </view>
</template>

<script>
/**
 * 静态协议正文页。
 * 当后端 / 配置文件中尚未提供正式协议 URL 时作为兜底展示。
 * 上线前请法务/产品替换为真实条款，或在 .env 中注入 VITE_TERMS_URL 等链接。
 */
export default {
  data() {
    return {
      type: '',
      document: {
        title: '用户协议',
        updatedAt: '2026-01-01',
        body: [],
      },
    }
  },

  onLoad(options) {
    this.type = options.type || 'terms'
    this.document = this.buildDocument(this.type)
    // 动态标题设置
    uni.setNavigationBarTitle({ title: this.document.title })
  },

  methods: {
    goBack() {
      uni.navigateBack()
    },

    buildDocument(type) {
      // 文档字典,后续替换为后端接口或外链 URL
      const map = {
        terms: {
          title: '用户协议',
          updatedAt: '2026-01-01',
          body: [
            '欢迎使用 MOYUYO ATELIER(以下简称"本平台")。在完成注册程序或以任何方式使用本平台服务前,请您务必仔细阅读并透彻理解本协议全部条款。',
            '一、账户与注册',
            '1. 您应保证注册时填写的资料真实、准确、完整,否则本平台有权拒绝您的注册或终止您的使用。',
            '2. 您应妥善保管账户密码,因您主动泄露或保管不善造成的损失由您自行承担。',
            '二、商品与订单',
            '1. 商品价格、数量、规格等信息以页面展示为准;本平台对商品信息的错误不承担责任,除非法律另有规定。',
            '2. 订单提交后请于支付时限内完成付款,逾期订单自动关闭。',
            '三、用户行为规范',
            '1. 您不得利用本平台从事任何违反法律法规的活动,包括但不限于发布违法信息、侵害他人权益等。',
            '2. 您应遵守本平台社区规范,不得发布辱骂、骚扰、广告等不当内容。',
            '四、协议的变更与终止',
            '本平台有权根据需要不时修改本协议条款,变更后的协议将在生效日前 7 天以公告方式告知。',
            '五、争议解决',
            '本协议适用中华人民共和国法律。因本协议产生的争议,双方应友好协商解决;协商不成的,任一方有权向本平台所在地有管辖权的人民法院提起诉讼。',
          ],
        },
        privacy: {
          title: '隐私政策',
          updatedAt: '2026-01-01',
          body: [
            '本平台非常重视您的个人信息保护。本政策将帮助您了解我们收集、使用、存储和共享个人信息的方式。',
            '一、我们收集的信息',
            '1. 账户信息:昵称、邮箱、手机号、收货地址等。',
            '2. 订单信息:商品、金额、收货地址、支付方式等。',
            '3. 设备与日志:IP 地址、设备型号、操作系统版本、浏览记录等。',
            '二、信息的使用',
            '1. 用于为您提供下单、支付、物流、客服等核心服务。',
            '2. 用于安全风控、违规排查与合规审计。',
            '三、信息的共享',
            '除法律法规规定或您的明确同意外,我们不会与第三方共享您的个人信息。',
            '四、您的权利',
            '您有权访问、更正、删除您的个人信息,可通过 [设置 - 隐私设置] 或联系客服行使。',
            '五、联系我们',
            '如有疑问,请通过客服邮箱 support@moyuyo.com 与我们联系。',
          ],
        },
        qualification: {
          title: '商户资质',
          updatedAt: '2026-01-01',
          body: [
            'MOYUYO ATELIER 由具备合法经营资质的公司运营,相关资质文件如下:',
            '1. 营业执照(统一社会信用代码 XXXXXXXX)',
            '2. 增值电信业务经营许可证',
            '3. 食品/宠物用品经营许可证(根据业务范围)',
            '如需查看原件,请联系客服邮箱 support@moyuyo.com 申请。',
          ],
        },
        license: {
          title: '开源许可',
          updatedAt: '2026-01-01',
          body: [
            '本应用使用了以下开源软件,在此向原作者表示感谢:',
            '1. Vue.js - MIT License',
            '2. uni-app - Apache License 2.0',
            '3. ECharts - Apache License 2.0',
            '4. Lucide Icons - ISC License',
            '完整许可文本请参见项目仓库的 THIRD_PARTY_LICENSES 文件。',
          ],
        },
      }
      return map[type] || map.terms
    },
  },
}
</script>

<style lang="scss" scoped>
.terms-doc {
  min-height: 100vh;
  background: var(--color-background);
  display: flex;
  flex-direction: column;
}

.header {
  position: sticky;
  top: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 88rpx;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
}

.header-btn {
  position: absolute;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 72rpx;
  height: 72rpx;
}

.header-btn:first-child {
  left: 16rpx;
}

.header-btn:last-child {
  right: 16rpx;
}

.back-icon {
  font-size: 48rpx;
  color: var(--color-text);
  line-height: 1;
}

.header-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  letter-spacing: -0.02em;
}

.content {
  flex: 1;
  height: calc(100vh - 88rpx);
  padding: 32rpx 40rpx 80rpx;
}

.meta {
  margin-bottom: 24rpx;
}

.meta-text {
  font-size: 24rpx;
  color: var(--color-text-tertiary);
}

.body {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  padding: 32rpx;
  background: var(--color-surface);
  border-radius: var(--radius-md);
  border: 1rpx solid var(--color-divider);
}

.body-line {
  font-size: 28rpx;
  line-height: 1.7;
  color: var(--color-text);
  white-space: pre-wrap;
}

.footer {
  margin-top: 40rpx;
  text-align: center;
}

.footer-text {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}
</style>
