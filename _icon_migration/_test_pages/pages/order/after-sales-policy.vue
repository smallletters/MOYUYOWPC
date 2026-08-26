<template>
  <view class="policy-page">
    <!-- 顶部导航栏 -->
    <view class="nav-bar">
      <view class="nav-back" @tap="goBack">
        <text class="back-icon"><text class="luc luc-arrow-left"></text></text>
      </view>
      <text class="nav-title">售后政策</text>
      <view class="nav-share" @tap="sharePolicy">
        <text class="share-icon">↗</text>
      </view>
    </view>

    <!-- 内容区域 -->
    <scroll-view class="policy-body" scroll-y>
      <!-- 更新时间提示 -->
      <view class="update-hint">
        <text class="hint-icon"><text class="luc luc-file-text"></text></text>
        <text class="hint-text">政策更新于 2026-07-01</text>
      </view>

      <!-- FAQ 折叠列表 -->
      <view class="faq-list">
        <view v-for="(section, index) in sections" :key="index" class="faq-item">
          <view class="faq-header" @tap="toggleSection(index)">
            <text class="faq-title">{{ section.title }}</text>
            <text class="faq-chevron" :class="{ rotated: section.open }"><text class="luc luc-arrow-left"></text></text>
          </view>
          <view class="faq-body" :class="{ collapsed: !section.open }">
            <view class="faq-content">
              <!-- 内容块 -->
              <view v-for="(block, bIndex) in section.blocks" :key="bIndex" class="content-block">
                <text class="block-title" :class="{ 'block-title-error': block.isError }">
                  {{ block.title }}
                </text>

                <!-- 段落内容 -->
                <view v-if="block.paragraphs">
                  <text
                    v-for="(p, pIndex) in block.paragraphs"
                    :key="pIndex"
                    class="block-paragraph"
                  >
                    {{ p }}
                  </text>
                </view>

                <!-- 列表内容 -->
                <view v-if="block.items" class="block-list">
                  <view v-for="(item, iIndex) in block.items" :key="iIndex" class="block-list-item">
                    <text class="list-icon luc" :class="$luc(block.isError  ?  'alert-triangle'  :  block.isMinus ? 'minus' : 'check-circle')"></text>
                    <text class="list-text">{{ item }}</text>
                  </view>
                </view>

                <!-- 步骤内容 -->
                <view v-if="block.steps" class="block-steps">
                  <view v-for="(step, sIndex) in block.steps" :key="sIndex" class="step-item">
                    <view class="step-num">
                      <text class="step-num-text">{{ sIndex + 1 }}</text>
                    </view>
                    <text class="step-text">{{ step }}</text>
                  </view>
                </view>
              </view>
            </view>
          </view>
        </view>
      </view>

      <!-- 底部留白 -->
      <view class="bottom-space" />
    </scroll-view>

    <!-- 底部联系客服按钮 -->
    <view class="bottom-bar">
      <view class="contact-btn" @tap="contactService">
        <text class="contact-icon"><text class="luc luc-headphones"></text></text>
        <text class="contact-text">联系客服</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'

// FAQ 各分区数据
const sections = ref([
  {
    title: '退货政策',
    open: false,
    blocks: [
      {
        title: '7天无理由退货',
        paragraphs: [
          '自签收之日起 7 个自然日内，商品完好且不影响二次销售的情况下，可申请无理由退货。',
        ],
      },
      {
        title: '退货条件',
        items: [
          '商品未使用、未清洗、未拆封（密封类商品）',
          '原包装、吊牌、说明书、赠品等配件齐全',
          '保留原始购买凭证或电子订单记录',
        ],
      },
      {
        title: '不适用情况',
        isError: true,
        items: ['超过 7 天退货时效', '商品已被使用或洗涤'],
      },
    ],
  },
  {
    title: '退款说明',
    open: false,
    blocks: [
      {
        title: '退款时效',
        paragraphs: [
          'Stripe 支付：退款将在审核通过后 3-5 个工作日内原路退回。',
          'PayPal 支付：退款将在审核通过后 1-3 个工作日内原路退回。',
        ],
      },
      {
        title: '退款方式',
        paragraphs: [
          '所有退款均通过原支付方式原路退回，不支持更换退款渠道。退款到账时间以各支付机构实际处理速度为准。',
        ],
      },
      {
        title: '价格保护',
        paragraphs: [
          '下单后 7 个自然日内如商品降价，可申请价格保护补差价。差价将退回至原支付账户，具体退款时效同上。',
        ],
      },
    ],
  },
  {
    title: '换货说明',
    open: false,
    blocks: [
      {
        title: '同款不同规格换货',
        paragraphs: [
          '签收后 7 个自然日内，可申请同款商品不同颜色或尺寸的换货。需确保商品完好、未使用、原包装齐全。',
        ],
      },
      {
        title: '换货流程',
        steps: [
          '在订单详情页提交换货申请，选择目标规格',
          '客服审核通过后，寄回原商品',
          '仓库签收并确认商品完好后，发出新规格商品',
        ],
      },
    ],
  },
  {
    title: '运费说明',
    open: false,
    blocks: [
      {
        title: '商家原因',
        paragraphs: ['因商品质量问题、发错货等商家原因产生的退换货，往返运费由 MOYUYO 全额承担。'],
      },
      {
        title: '用户原因',
        paragraphs: [
          '因个人偏好、尺寸不合适等非商家原因的退换货，寄回运费由用户自行承担。换货的新商品发货运费由商家承担。',
        ],
      },
      {
        title: '运费险',
        paragraphs: [
          '部分商品附赠运费险，可在退货时使用运费险抵扣寄回运费。具体以商品详情页标注及保险公司规则为准。',
        ],
      },
    ],
  },
  {
    title: '不支持退换的情况',
    open: false,
    blocks: [
      {
        title: '',
        isMinus: true,
        items: [
          '已洗涤、使用或拆封的洗护类商品',
          '定制商品、刻字/绣花等个性化定制商品',
          '内衣、窝垫等贴身或卫生类商品（拆封后）',
          '食品、零食、保健品等消耗类商品（拆封后）',
          '促销赠品、试用装等非售卖商品',
          '超过退换货时效的商品',
        ],
      },
    ],
  },
])

// 切换折叠
const toggleSection = (index) => {
  sections.value[index].open = !sections.value[index].open
}

// 返回
const goBack = () => {
  uni.navigateBack()
}

// 分享政策
const sharePolicy = () => {
  uni.showShareMenu()
}

// 联系客服
const contactService = () => {
  uni.navigateTo({ url: '/pages/user/customer-service' })
}
</script>

<style lang="scss" scoped>
.policy-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--color-background);
}

/* 导航栏 */
.nav-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 88rpx;
  padding: 0 32rpx;
  position: relative;
  background: var(--color-background);
}

.nav-back {
  position: absolute;
  left: 32rpx;
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #f2f2f7;
}

.back-icon {
  font-size: 40rpx;
  color: var(--color-text);
}

.nav-title {
  font-size: 34rpx;
  font-weight: 600;
  color: var(--color-text);
}

.nav-share {
  position: absolute;
  right: 32rpx;
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #f2f2f7;
}

.share-icon {
  font-size: 32rpx;
  color: var(--color-text);
}

/* 内容区域 */
.policy-body {
  flex: 1;
  padding: 24rpx 32rpx;
}

.update-hint {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 16rpx 0;
}

.hint-icon {
  font-size: 28rpx;
}

.hint-text {
  font-size: 24rpx;
  color: var(--color-text-secondary);
}

/* FAQ 列表 */
.faq-list {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.faq-item {
  border-radius: 32rpx;
  overflow: hidden;
  background: var(--color-card);
  border: 1rpx solid var(--color-border);
}

.faq-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 32rpx;
}

.faq-title {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--color-text);
}

.faq-chevron {
  font-size: 28rpx;
  color: var(--color-text-secondary);
  transition: transform 0.3s ease;
  transform: rotate(-90deg);
}

.faq-chevron.rotated {
  transform: rotate(-270deg);
}

.faq-body {
  max-height: 2000rpx;
  overflow: hidden;
  transition:
    max-height 0.3s ease,
    opacity 0.25s ease;
  opacity: 1;
}

.faq-body.collapsed {
  max-height: 0;
  opacity: 0;
}

.faq-content {
  padding: 0 32rpx 32rpx;
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.content-block {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.block-title {
  font-size: 26rpx;
  font-weight: 600;
  color: var(--color-text);
}

.block-title-error {
  color: var(--color-error);
}

.block-paragraph {
  font-size: 26rpx;
  line-height: 1.6;
  color: var(--color-text-secondary);
}

.block-list {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  padding-left: 8rpx;
}

.block-list-item {
  display: flex;
  align-items: flex-start;
  gap: 12rpx;
}

.list-icon {
  font-size: 22rpx;
  flex-shrink: 0;
  margin-top: 4rpx;
}

.list-text {
  font-size: 26rpx;
  line-height: 1.6;
  color: var(--color-text-secondary);
}

/* 步骤 */
.block-steps {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.step-item {
  display: flex;
  align-items: flex-start;
  gap: 20rpx;
}

.step-num {
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  background: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.step-num-text {
  font-size: 22rpx;
  font-weight: 700;
  color: #ffffff;
}

.step-text {
  font-size: 26rpx;
  line-height: 1.6;
  color: var(--color-text-secondary);
}

.bottom-space {
  height: 160rpx;
}

/* 底部按钮 */
.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 24rpx 32rpx;
  padding-bottom: calc(24rpx + env(safe-area-inset-bottom));
  background: linear-gradient(to top, var(--color-background) 70%, transparent);
}

.contact-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
  height: 96rpx;
  border-radius: 48rpx;
  background: var(--color-primary);
}

.contact-icon {
  font-size: 32rpx;
}

.contact-text {
  font-size: 32rpx;
  font-weight: 600;
  color: #ffffff;
}
</style>
