<template>
  <view class="debug">
    <view class="header">
      <text class="title">原生插件通信测试</text>
      <text class="sub">验证 uni-app 与 Native 端双向数据传递</text>
    </view>

    <view class="section">
      <text class="section-title">PetHub 3D 插件</text>
      <view class="btn-row">
        <button class="btn" @click="testPetHub('loadPet')">loadPet</button>
        <button class="btn" @click="testPetHub('switchScene')">switchScene</button>
        <button class="btn" @click="testPetHub('playAnimation')">playAnimation</button>
      </view>
    </view>

    <view class="section">
      <text class="section-title">Payment 支付插件</text>
      <view class="btn-row">
        <button class="btn" @click="testPayment('isAvailable')">isAvailable</button>
        <button class="btn" @click="testPayment('pay')">pay</button>
      </view>
    </view>

    <view class="section">
      <text class="section-title">Auth 登录插件</text>
      <view class="btn-row">
        <button class="btn" @click="testAuth('isAuthorized')">isAuthorized</button>
        <button class="btn" @click="testAuth('login')">login</button>
      </view>
    </view>

    <view class="log-section">
      <text class="section-title">调试日志</text>
      <scroll-view class="log-box" scroll-y>
        <text v-for="(line, i) in logs" :key="i" class="log-line">{{ line }}</text>
      </scroll-view>
    </view>

    <view class="footer">
      <button class="btn btn-clear" @click="logs = []">清空日志</button>
    </view>
  </view>
</template>

<script>
import { usePetHubPlugin } from '@/plugins/pethub'
import { usePaymentPlugin } from '@/plugins/payment'
import { useAuthPlugin } from '@/plugins/auth'

export default {
  data() {
    return { logs: ['[初始化] 调试页面已加载'] }
  },
  methods: {
    log(msg) {
      this.logs.push(`[${new Date().toLocaleTimeString()}] ${msg}`)
      console.log(msg)
    },

    async testPetHub(method) {
      this.log(`[PetHub] 调用 ${method}...`)
      const plugin = usePetHubPlugin()
      const params = { loadPet: 'milo-001', switchScene: 'grassland', playAnimation: 'run' }
      const start = Date.now()
      const res = await plugin[method](params[method] || '')
      const cost = Date.now() - start
      this.log(`[PetHub] ${method} → ${JSON.stringify(res)} (${cost}ms)`)
    },

    async testPayment(method) {
      this.log(`[Payment] 调用 ${method}...`)
      const plugin = usePaymentPlugin()
      const start = Date.now()
      const res =
        method === 'pay'
          ? await plugin.pay('apple_pay', { amount: 19.99, currency: 'USD' })
          : await plugin.isAvailable('apple_pay')
      const cost = Date.now() - start
      this.log(`[Payment] ${method} → ${JSON.stringify(res)} (${cost}ms)`)
    },

    async testAuth(method) {
      this.log(`[Auth] 调用 ${method}...`)
      const plugin = useAuthPlugin()
      const start = Date.now()
      const res = await plugin[method]('apple')
      const cost = Date.now() - start
      this.log(`[Auth] ${method} → ${JSON.stringify(res)} (${cost}ms)`)
    },
  },
}
</script>

<style lang="scss" scoped>
.debug {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: var(--color-background);
  padding: 0 var(--space-md);
}

.header {
  padding: var(--space-lg) 0;
}
.title {
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-bold);
}
.sub {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  margin-top: var(--space-xs);
}

.section {
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: var(--space-md);
  margin-bottom: var(--space-sm);
}

.section-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  margin-bottom: var(--space-sm);
  display: block;
}

.btn-row {
  display: flex;
  gap: var(--space-sm);
  flex-wrap: wrap;
}

.btn {
  flex: 1;
  min-width: 140rpx;
  padding: var(--space-sm) var(--space-md);
  background: var(--color-primary);
  border: none;
  border-radius: var(--radius-pill);
  font-size: var(--font-size-sm);
  text-align: center;
  color: var(--color-text);
}

.btn-clear {
  background: var(--color-divider);
  width: 100%;
}

.log-section {
  flex: 1;
  margin-top: var(--space-sm);
}

.log-box {
  background: #1a1a1a;
  color: #00ff88;
  font-size: 20rpx;
  padding: var(--space-sm);
  border-radius: var(--radius-sm);
  height: 300rpx;
}

.log-line {
  display: block;
  line-height: 1.6;
  word-break: break-all;
}

.footer {
  padding: var(--space-md) 0;
}
</style>
